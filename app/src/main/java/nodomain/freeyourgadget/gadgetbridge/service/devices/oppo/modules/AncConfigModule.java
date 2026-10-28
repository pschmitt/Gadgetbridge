package nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.modules;

import android.content.Context;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.devices.oppo.OppoHeadphonesPreferences;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.OppoUtils;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.AncConfigType;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.AncConfigValue;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.OppoCommand;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.OppoMessage;

public class AncConfigModule extends AbstractModule {
    private static final Logger LOG = LoggerFactory.getLogger(AncConfigModule.class);
    private final boolean supportsLevel;

    public AncConfigModule(@NonNull final Context context, final boolean supportsLevel) {
        super(context);
        this.supportsLevel = supportsLevel;
    }

    @NonNull
    public List<OppoMessage> encodeReq(@NonNull final EnumSet<AncConfigType> types) {
        final List<OppoMessage> messages = new ArrayList<>();
        for (AncConfigType type : types) {
            messages.add(encodeReq(type));
        }

        return messages;
    }

    @NonNull
    public OppoMessage encodeReq(@NonNull final AncConfigType type) {
        byte[] payload = new byte[]{
            (byte) type.getCode(),
            (byte) 0x01,
        };

        return new OppoMessage(OppoCommand.ANC_CONFIG_REQ, payload);
    }

    @NonNull
    public OppoMessage encodeSetMode(@NonNull final AncConfigValue value) {
        LOG.debug("Send MODE = {}", value);
        return encodeSet(AncConfigType.MODE, value.getCode());
    }

    @NonNull
    public OppoMessage encodeSetMode(@NonNull final AncConfigValue.Level value) {
        LOG.debug("Send MODE = {}", value);
        return encodeSet(AncConfigType.MODE, value.getCode());
    }

    @NonNull
    public OppoMessage encodeSetTouchCycleModes(@NonNull final EnumSet<AncConfigValue> values) {
        if (values.size() < 2) {
            throw new IllegalArgumentException("ANC cycle must contain at least 2 values");
        }
        LOG.debug("Send TOUCH_CYCLE_MODES = {}", values);
        return encodeSet(AncConfigType.TOUCH_CYCLE_MODES, AncConfigValue.toMask(values));
    }

    @NonNull
    public GBDeviceEventUpdatePreferences decodeRet(@NonNull final byte[] payload) {
        final ByteBuffer buf = ByteBuffer.wrap(payload);
        final GBDeviceEventUpdatePreferences event = new GBDeviceEventUpdatePreferences();

        if (buf.remaining() < 3) {
            LOG.warn("Unexpected anc config ret payload remaining {}, expected 4", buf.remaining());
            return event;
        }

        final int zero = buf.get();
        final Integer typeCode = getTypeCode(buf);
        if (typeCode == null) {
            return event;
        }
        final int valueCode = buf.get() & 0xff;

        final AncConfigType type = AncConfigType.fromCode(typeCode);
        if (type == null) {
            LOG.warn("Unknown anc type code 0x{}", OppoUtils.numberToHex(typeCode, 2));
            return event;
        }

        switch (type) {
            case MODE -> {
                if (supportsLevel) {
                    final AncConfigValue.Level value = AncConfigValue.Level.fromCode(valueCode);
                    if (value != null) {
                        LOG.debug("Got {} = {}", type, value);
                        event.withPreference(OppoHeadphonesPreferences.ANC_SELECTOR, AncConfigValue.ON.getPrefId());
                        event.withPreference(OppoHeadphonesPreferences.ANC_LEVEL_SELECTOR, value.getPrefId());
                        break;
                    }

                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Unknown anc level value code 0x{}", OppoUtils.numberToHex(valueCode, 2));
                    }
                }

                final AncConfigValue value = AncConfigValue.fromCode(valueCode);
                if (value == null) {
                    LOG.warn("Unknown anc value code 0x{}", OppoUtils.numberToHex(valueCode, 2));
                    break;
                }

                LOG.debug("Got {} = {}", type, value);
                event.withPreference(OppoHeadphonesPreferences.ANC_SELECTOR, value.getPrefId());
            }
            case TOUCH_CYCLE_MODES -> {
                final EnumSet<AncConfigValue> values = AncConfigValue.fromMask(valueCode);
                if (values.isEmpty()) {
                    LOG.warn("Unknown anc value mask 0x{}", OppoUtils.numberToHex(valueCode, 2));
                    break;
                }
                LOG.debug("Got {} = {}", type, values);
                final Set<String> valuePrefIds = AncConfigValue.toPrefIds(values);
                event.withPreference(OppoHeadphonesPreferences.ANC_TOUCH_CYCLE_MODES, valuePrefIds);
            }
            case DYNAMIC_LEVEL -> {
                final AncConfigValue.Level value = AncConfigValue.Level.fromCode(valueCode);
                if (value == null) {
                    LOG.warn("Unknown anc level value code 0x{}", OppoUtils.numberToHex(valueCode, 2));
                    break;
                }

                LOG.debug("Got {} = {} (DYNAMIC)", type, value);
                event.withPreference(OppoHeadphonesPreferences.ANC_LEVEL_DYNAMIC, value.getPrefId());
            }
            default -> {
                LOG.debug("Unknown anc type code {}", typeCode);
            }
        }
        return event;
    }

    private OppoMessage encodeSet(@NonNull final AncConfigType type, final int value) {
        final byte[] payload = new byte[]{
            (byte) type.getCode(),
            (byte) 0x01,
            (byte) value
        };

        return new OppoMessage(OppoCommand.ANC_CONFIG_SET, payload);
    }

    private Integer getTypeCode(ByteBuffer buf) {
        switch (buf.remaining()) {
            case 2 -> {
                return buf.get() & 0xff;
            }
            case 3 -> {
                final int typeCode = buf.get() & 0xff;
                final int one = buf.get() & 0xff;
                return typeCode;
            }
            default -> {
                LOG.warn("Unexpected payload length {}, expected {} or {}",
                    buf.position() + buf.remaining(),
                    buf.position() + 2,
                    buf.position() + 3);
                return null;
            }
        }
    }
}
