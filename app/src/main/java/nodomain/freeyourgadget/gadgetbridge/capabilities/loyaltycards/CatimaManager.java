/*  Copyright (C) 2023-2024 José Rebelo

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.capabilities.loyaltycards;

import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_CATIMA_PACKAGE;
import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_LAST_SYNC_HASH;
import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_PENDING_SYNC_HASH;
import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_SYNC_ARCHIVED;
import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_SYNC_GROUPS;
import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_SYNC_GROUPS_ONLY;
import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_SYNC_ON_CHANGE;
import static nodomain.freeyourgadget.gadgetbridge.activities.loyaltycards.LoyaltyCardsSettingsConst.LOYALTY_CARDS_SYNC_STARRED;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils;
import nodomain.freeyourgadget.gadgetbridge.util.CheckSums;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class CatimaManager {
    private static final Logger LOG = LoggerFactory.getLogger(CatimaManager.class);

    public static final String ACTION_LOYALTY_CARDS_SYNCED = "nodomain.freeyourgadget.gadgetbridge.loyaltycards.action.synced";
    public static final String ACTION_LOYALTY_CARDS_REFRESH_OBSERVER = "nodomain.freeyourgadget.gadgetbridge.loyaltycards.action.refresh_observer";
    public static final String EXTRA_SUCCESS = "success";

    private static final ExecutorService AUTO_SYNC_EXECUTOR = Executors.newSingleThreadExecutor();

    private final Context context;

    public CatimaManager(final Context context) {
        this.context = context;
    }

    /**
     * Syncs the loyalty cards to the device on a background thread, if automatic sync is enabled
     * and the cards changed since the last successful sync.
     */
    public void autoSync(final GBDevice gbDevice) {
        if (!gbDevice.getDeviceCoordinator().supportsLoyaltyCards(gbDevice)) {
            return;
        }
        final Prefs prefs = new Prefs(GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()));
        if (!prefs.getBoolean(LOYALTY_CARDS_SYNC_ON_CHANGE, false)) {
            return;
        }
        AUTO_SYNC_EXECUTOR.execute(() -> {
            final ArrayList<LoyaltyCard> cardsToSync = getCardsToSync(gbDevice);
            if (cardsToSync == null) {
                return;
            }
            final String hash = hash(cardsToSync);
            if (hash.equals(prefs.getString(LOYALTY_CARDS_LAST_SYNC_HASH, ""))) {
                LOG.debug("Loyalty cards did not change since the last sync");
                return;
            }
            send(gbDevice, cardsToSync, hash);
        });
    }

    /**
     * Sends the loyalty cards to the device.
     *
     * @return the number of cards to be sent, or -1 if the sync was not triggered.
     */
    public int sync(final GBDevice gbDevice) {
        final ArrayList<LoyaltyCard> cardsToSync = getCardsToSync(gbDevice);
        if (cardsToSync == null) {
            return -1;
        }

        send(gbDevice, cardsToSync, hash(cardsToSync));

        return cardsToSync.size();
    }

    /**
     * Returns the cards that match the sync options for the device, or null if the cards cannot be read.
     */
    @Nullable
    public ArrayList<LoyaltyCard> getCardsToSync(final GBDevice gbDevice) {
        final String catimaPackage = getCatimaPackage(gbDevice);
        if (catimaPackage == null) {
            LOG.warn("Catima is not installed");
            return null;
        }

        final Prefs prefs = new Prefs(GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()));

        final boolean syncGroupsOnly = prefs.getBoolean(LOYALTY_CARDS_SYNC_GROUPS_ONLY, false);
        final Set<String> syncGroups = prefs.getStringSet(LOYALTY_CARDS_SYNC_GROUPS, Collections.emptySet());
        final boolean syncArchived = prefs.getBoolean(LOYALTY_CARDS_SYNC_ARCHIVED, false);
        final boolean syncStarred = prefs.getBoolean(LOYALTY_CARDS_SYNC_STARRED, false);

        final CatimaContentProvider catima = new CatimaContentProvider(context, catimaPackage);

        LOG.debug("Syncing loyalty cards from {}", catimaPackage);

        if (!catima.isCatimaCompatible()) {
            LOG.warn("Catima is not compatible");
            return null;
        }

        // FossWallet does not support starred sync
        final boolean starredSupported = catimaPackage.contains("catima");

        final List<LoyaltyCard> cards = catima.getCards();
        if (cards == null) {
            return null;
        }

        final Set<Integer> cardsInGroupsToSync = new HashSet<>();
        if (syncGroupsOnly) {
            final Map<String, List<Integer>> groupCards = catima.getGroupCards();
            if (groupCards == null) {
                return null;
            }
            for (final Map.Entry<String, List<Integer>> groupCardsEntry : groupCards.entrySet()) {
                if (syncGroups.contains(groupCardsEntry.getKey())) {
                    cardsInGroupsToSync.addAll(groupCardsEntry.getValue());
                }
            }
        }

        final Set<BarcodeFormat> supportedBarcodeFormats = gbDevice.getDeviceCoordinator().getSupportedBarcodeFormats(gbDevice);

        final ArrayList<LoyaltyCard> cardsToSync = new ArrayList<>();
        for (final LoyaltyCard card : cards) {
            if (!supportedBarcodeFormats.contains(card.getBarcodeFormat())) {
                LOG.debug("Ignoring card {} - unsupported barcode format {}", card.getId(), card.getBarcodeFormat());
                continue;
            }
            if (syncGroupsOnly && !cardsInGroupsToSync.contains(card.getId())) {
                continue;
            }
            if (!syncArchived && card.isArchived()) {
                continue;
            }
            if (starredSupported && syncStarred && !card.isStarred()) {
                continue;
            }
            cardsToSync.add(card);
        }

        Collections.sort(cardsToSync);

        LOG.debug("Will sync cards: {}", cardsToSync);

        return cardsToSync;
    }

    private void send(final GBDevice gbDevice, final ArrayList<LoyaltyCard> cards, final String hash) {
        LOG.debug("Storing pending loyalty cards sync hash {}", hash);
        GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()).edit()
            .remove(LOYALTY_CARDS_LAST_SYNC_HASH)
            .putString(LOYALTY_CARDS_PENDING_SYNC_HASH, hash)
            .apply();

        GBApplication.deviceService(gbDevice).onSetLoyaltyCards(cards);
    }

    public static void notifySynced(final Context context, final GBDevice device, final boolean success) {
        LOG.debug("Loyalty cards sync to {} finished, success = {}", device.getAddress(), success);

        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
        final String pendingHash = prefs.getString(LOYALTY_CARDS_PENDING_SYNC_HASH, null);
        final SharedPreferences.Editor editor = prefs.edit().remove(LOYALTY_CARDS_PENDING_SYNC_HASH);
        if (!success) {
            editor.remove(LOYALTY_CARDS_LAST_SYNC_HASH);
        } else if (pendingHash != null) {
            LOG.debug("Storing loyalty cards sync hash {}", pendingHash);
            editor.putString(LOYALTY_CARDS_LAST_SYNC_HASH, pendingHash);
        }
        editor.apply();

        final Intent intent = new Intent(ACTION_LOYALTY_CARDS_SYNCED);
        intent.putExtra(GBDevice.EXTRA_DEVICE, device);
        intent.putExtra(EXTRA_SUCCESS, success);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    @Nullable
    public String getCatimaPackage(final GBDevice gbDevice) {
        final List<CharSequence> installedCatimaPackages = findInstalledCatimaPackages();
        if (installedCatimaPackages.isEmpty()) {
            return null;
        }

        final Prefs prefs = new Prefs(GBApplication.getDeviceSpecificSharedPrefs(gbDevice.getAddress()));
        return prefs.getString(LOYALTY_CARDS_CATIMA_PACKAGE, installedCatimaPackages.get(0).toString());
    }

    private static String hash(final List<LoyaltyCard> cards) {
        final StringBuilder sb = new StringBuilder();
        for (final LoyaltyCard card : cards) {
            sb.append(card.getId()).append('\0')
                .append(card.getName()).append('\0')
                .append(card.getNote()).append('\0')
                .append(card.getExpiry() != null ? card.getExpiry().getTime() : null).append('\0')
                .append(card.getBalance()).append('\0')
                .append(card.getBalanceType()).append('\0')
                .append(card.getCardId()).append('\0')
                .append(card.getBarcodeId()).append('\0')
                .append(card.getBarcodeFormat()).append('\0')
                .append(card.getBarcodeEncodingName()).append('\0')
                .append(card.getColor()).append('\0')
                .append(card.isStarred()).append('\0')
                .append(card.isArchived()).append('\n');
        }
        return Objects.requireNonNull(CheckSums.md5(sb.toString()));
    }

    public List<CharSequence> findInstalledCatimaPackages() {
        final List<CharSequence> installedCatimaPackages = new ArrayList<>();
        for (final String knownPackage : CatimaContentProvider.KNOWN_PACKAGES) {
            if (AndroidUtils.isPackageInstalled(knownPackage)) {
                installedCatimaPackages.add(knownPackage);
            }
        }
        return installedCatimaPackages;
    }
}
