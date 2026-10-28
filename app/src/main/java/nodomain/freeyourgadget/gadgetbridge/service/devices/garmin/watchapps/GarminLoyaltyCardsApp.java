package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.watchapps;

import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_SYNC_WATCH_REQUEST;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import nodomain.freeyourgadget.gadgetbridge.capabilities.loyaltycards.CatimaManager;
import nodomain.freeyourgadget.gadgetbridge.capabilities.loyaltycards.LoyaltyCard;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.GarminWatchAppCommunicator;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.GarminJson;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.GarminJsonException;

/**
 * The custom Loyalty Cards watch app.
 * <p>
 * Each card has a hash of its content. The watch sends the hashes of the cards it has. The phone replies with the
 * hashes of all cards, in order, followed by the cards that the watch does not have. The lists of hashes are split
 * in messages with an offset and the total count.
 * <p>
 * Each card consists of a header with the hash, the name, the code text, the color, and the size of the barcode bit
 * matrix. The bits are row by row, in 32-bit numbers with the most significant bit first. The header also contains
 * the first set of numbers, and subsequent data messages contain the remaining numbers, if needed.
 */
public class GarminLoyaltyCardsApp extends GarminWatchApp {
    private static final Logger LOG = LoggerFactory.getLogger(GarminLoyaltyCardsApp.class);

    public static final UUID APP_UUID = UUID.fromString("ea44277e-441e-4d2d-987e-5a5a59643016");

    private static final int MAX_NAME_LENGTH = 24;
    private static final int MAX_CODE_LENGTH = 48;

    @Nullable
    private EncodedCards encodedCards;
    @Nullable
    private List<Integer> watchHashes;
    private final List<Integer> requestHashes = new ArrayList<>();

    public GarminLoyaltyCardsApp(final GarminSupport support) {
        super(support, APP_UUID);
    }

    public void onSetLoyaltyCards(final List<LoyaltyCard> cards) {
        encodedCards = encode(cards);
        send();
    }

    @Override
    public void onMessage(final JsonElement message) {
        if (!message.isJsonObject()) {
            return;
        }
        final JsonObject request = message.getAsJsonObject();
        final JsonElement part = request.get("h");
        final JsonElement offsetElement = request.get("o");
        final JsonElement countElement = request.get("n");
        if (part == null || !part.isJsonArray() || offsetElement == null || countElement == null) {
            LOG.warn("Unexpected loyalty cards message: {}", message);
            return;
        }
        final int offset = offsetElement.getAsInt();
        final int count = countElement.getAsInt();
        if (offset == 0) {
            requestHashes.clear();
        } else if (offset != requestHashes.size()) {
            LOG.warn("Unexpected offset {}, expected {}", offset, requestHashes.size());
            return;
        }
        for (final JsonElement hash : part.getAsJsonArray()) {
            requestHashes.add(hash.getAsInt());
        }
        if (requestHashes.size() < count) {
            return;
        }

        watchHashes = new ArrayList<>(requestHashes);
        LOG.debug("Watch has {} loyalty cards: {}", watchHashes.size(), watchHashes);

        if (!getSupport().getDevicePrefs().getBoolean(LOYALTY_CARDS_SYNC_WATCH_REQUEST, false)) {
            LOG.warn("Watch-initiated loyalty card sync is disabled");
            return;
        }

        final List<LoyaltyCard> cards = new CatimaManager(getSupport().getContext()).getCardsToSync(getSupport().getDevice());
        if (cards != null) {
            encodedCards = encode(cards);
        }
        if (encodedCards == null) {
            LOG.debug("No loyalty cards to send to the watch");
            return;
        }
        send();
    }

    private void send() {
        final EncodedCards cards = Objects.requireNonNull(encodedCards);
        if (!isAvailable()) {
            LOG.warn("Loyalty cards app is not installed");
            CatimaManager.notifySynced(getSupport().getContext(), getSupport().getDevice(), false);
            return;
        }
        if (cards.hashes().equals(watchHashes)) {
            LOG.debug("Watch already has the latest loyalty cards");
            CatimaManager.notifySynced(getSupport().getContext(), getSupport().getDevice(), true);
            return;
        }

        final JsonArray hashes = new JsonArray(cards.hashes().size());
        cards.hashes().forEach(hashes::add);
        final List<JsonObject> messages = new ArrayList<>();
        int offset = 0;
        do {
            final JsonObject list = new JsonObject();
            list.addProperty("o", offset);
            list.addProperty("n", hashes.size());
            final int next = fill(list, "l", hashes, offset);
            if (next == offset && !hashes.isEmpty()) {
                LOG.error("Card list does not fit in a message");
                return;
            }
            offset = next;
            messages.add(list);
        } while (offset < hashes.size());

        final Set<Integer> present = watchHashes != null ? new HashSet<>(watchHashes) : Collections.emptySet();
        int sentCards = 0;
        for (final Map.Entry<Integer, List<JsonObject>> card : cards.messages().entrySet()) {
            if (!present.contains(card.getKey())) {
                messages.addAll(card.getValue());
                sentCards++;
            }
        }
        LOG.debug("Sending {} of {} loyalty cards in {} messages", sentCards, cards.messages().size(), messages.size());

        final int last = messages.size() - 1;
        for (int i = 0; i <= last; i++) {
            final Consumer<Boolean> onResult = i == last
                ? success -> {
                if (success) {
                    watchHashes = cards.hashes();
                }
                CatimaManager.notifySynced(getSupport().getContext(), getSupport().getDevice(), success);
            }
                : null;
            sendMessage(messages.get(i), i == 0, onResult);
        }
    }

    private static EncodedCards encode(final List<LoyaltyCard> cards) {
        final List<Integer> hashes = new ArrayList<>();
        final Map<Integer, List<JsonObject>> messages = new LinkedHashMap<>();
        for (final LoyaltyCard card : cards) {
            final BitMatrix matrix = encodeBarcode(card);
            if (matrix == null) {
                continue;
            }

            final JsonObject header = new JsonObject();
            header.addProperty("name", StringUtils.truncate(card.getName(), MAX_NAME_LENGTH));
            final String value = getBarcodeValue(card);
            if (value.length() <= MAX_CODE_LENGTH) {
                header.addProperty("code", value);
            }
            if (card.getColor() != null) {
                header.addProperty("c", card.getColor() & 0xffffff);
            }
            header.addProperty("w", matrix.getWidth());
            header.addProperty("h", matrix.getHeight());
            final JsonArray data = pack(matrix);

            final int hash = Objects.hash(header, data);
            hashes.add(hash);
            if (!messages.containsKey(hash)) {
                messages.put(hash, getCardMessages(header, hash, data));
            }
        }

        LOG.debug("Encoded {} loyalty cards: {}", hashes.size(), hashes);

        return new EncodedCards(hashes, messages);
    }

    @NonNull
    private static List<JsonObject> getCardMessages(final JsonObject header, final int hash, final JsonArray data) {
        final List<JsonObject> messages = new ArrayList<>();
        header.addProperty("k", hash);
        int offset = fill(header, "d", data, 0);
        messages.add(header);
        while (offset < data.size()) {
            final JsonObject chunk = new JsonObject();
            chunk.addProperty("k", hash);
            chunk.addProperty("o", offset);
            final int next = fill(chunk, "d", data, offset);
            if (next == offset) {
                LOG.error("Data of card {} does not fit in a message", hash);
                break;
            }
            offset = next;
            messages.add(chunk);
        }
        return messages;
    }

    private static int fill(final JsonObject message, final String key, final JsonArray data, final int offset) {
        final JsonArray part = new JsonArray();
        message.add(key, part);
        int next = offset;
        while (next < data.size()) {
            part.add(data.get(next));
            if (!fits(message)) {
                part.remove(part.size() - 1);
                break;
            }
            next++;
        }
        return next;
    }

    private static boolean fits(final JsonObject message) {
        try {
            final byte[] blob = GarminJson.encode(message);
            return GarminWatchAppCommunicator.pad(blob).length <= GarminWatchAppCommunicator.MAX_MESSAGE_SIZE;
        } catch (final GarminJsonException e) {
            LOG.error("Failed to encode message", e);
            return false;
        }
    }

    private static String getBarcodeValue(final LoyaltyCard card) {
        return card.getBarcodeId() != null ? card.getBarcodeId() : card.getCardId();
    }

    @Nullable
    private static BitMatrix encodeBarcode(final LoyaltyCard card) {
        if (card.getBarcodeFormat() == null) {
            LOG.warn("Card {} has no barcode format", card);
            return null;
        }

        try {
            final Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 0);
            hints.put(EncodeHintType.CHARACTER_SET, card.getBarcodeEncoding().name());
            return new MultiFormatWriter().encode(
                getBarcodeValue(card),
                com.google.zxing.BarcodeFormat.valueOf(card.getBarcodeFormat().name()),
                0,
                0,
                hints
            );
        } catch (final WriterException | IllegalArgumentException e) {
            LOG.error("Failed to encode the barcode of card {}", card, e);
            return null;
        }
    }

    private static JsonArray pack(final BitMatrix matrix) {
        final int width = matrix.getWidth();
        final int[] words = new int[(width * matrix.getHeight() + 31) / 32];
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    final int bit = y * width + x;
                    words[bit / 32] |= 1 << (31 - bit % 32);
                }
            }
        }
        final JsonArray array = new JsonArray(words.length);
        for (final int word : words) {
            array.add(word);
        }
        return array;
    }

    /**
     * The hashes of the cards, in order, and the messages of each distinct card.
     */
    private record EncodedCards(List<Integer> hashes, Map<Integer, List<JsonObject>> messages) {
    }
}
