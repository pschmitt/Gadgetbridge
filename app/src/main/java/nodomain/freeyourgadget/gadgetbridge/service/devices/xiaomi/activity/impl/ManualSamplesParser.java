/*  Copyright (C) 2024 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.impl;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.XiaomiManualSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiManualSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.XiaomiActivityFileId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.XiaomiActivityParser;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class ManualSamplesParser extends XiaomiActivityParser {
    private static final Logger LOG = LoggerFactory.getLogger(ManualSamplesParser.class);

    @Override
    public boolean parse(final Context context, final GBDevice gbDevice, final XiaomiActivityFileId fileId, final byte[] bytes) {
        if (fileId.getVersion() == 1) {
            final List<XiaomiManualSample> samples = decodeVersion1(fileId, bytes);
            if (samples == null) {
                return false;
            }
            return persistSamples(context, gbDevice, samples);
        }

        if (fileId.getVersion() != 2) {
            LOG.warn("Unknown manual samples version {}", fileId.getVersion());
            return false;
        }

        return persistSamples(context, gbDevice, decodeVersion2(bytes));
    }

    static List<XiaomiManualSample> decodeVersion2(final byte[] bytes) {
        final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.limit(buf.limit() - 4); // discard crc at the end
        buf.get(new byte[7]); // skip fileId bytes
        final byte fileIdPadding = buf.get();
        if (fileIdPadding != 0) {
            LOG.warn("Expected 0 padding after fileId, got {} - parsing might fail", fileIdPadding);
        }

        // Looks like there is no header, it starts right away with samples:
        // 8A90A965 12 63 <- spo2
        // ... multiple 13 00
        // C793A965 13 00
        // 9698A965 13 1C <- stress
        // E79CA965 44 5A0E0000 <- body temperature
        // 729FA965 44 590E0000 <- body temperature

        final List<XiaomiManualSample> samples = new ArrayList<>();

        // 4-byte timestamp + type byte
        while (buf.remaining() >= 5) {
            final int timestamp = buf.getInt();
            // High nibble: value length in bytes. Low nibble: measurement (1 HR, 2 SpO2,
            // 3 stress, 4 temperature, 5 blood pressure), see
            // https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/3517#issuecomment-1516353
            final int typeAndLength = buf.get() & 0xff;
            final int length = typeAndLength >> 4;
            final int measurement = typeAndLength & 0x0f;
            if (length > buf.remaining()) {
                LOG.warn("Manual sample {} claims {} bytes, {} remain", typeAndLength, length, buf.remaining());
                break;
            }

            final int type;
            final int value;
            switch (measurement) {
                case 1:
                case 2:
                case 3:
                    // Stored under the 1-byte type constant, whatever length the band sent.
                    type = measurement == 1 ? XiaomiManualSampleProvider.TYPE_HR
                            : measurement == 2 ? XiaomiManualSampleProvider.TYPE_SPO2
                            : XiaomiManualSampleProvider.TYPE_STRESS;
                    if (length == 1) {
                        value = buf.get() & 0xff;
                    } else if (length == 2) {
                        value = buf.getShort() & 0xffff;
                    } else {
                        LOG.warn("Unexpected length {} for manual sample {}", length, typeAndLength);
                        buf.position(buf.position() + length);
                        continue;
                    }
                    break;
                case 4:
                    if (length != 4) {
                        LOG.warn("Unexpected length {} for manual sample {}", length, typeAndLength);
                        buf.position(buf.position() + length);
                        continue;
                    }
                    type = XiaomiManualSampleProvider.TYPE_TEMPERATURE;
                    // FIXME: This is actually 2 2-byte values, see the comment linked above
                    value = buf.getInt();
                    break;
                // TODO blood pressure, see the comment linked above
                default:
                    LOG.warn("Unknown manual sample type {}, skipping {} bytes", typeAndLength, length);
                    buf.position(buf.position() + length);
                    continue;
            }

            if (value == 0) {
                continue;
            }

            LOG.debug("Got manual sample: ts={} type={} value={}", timestamp, type, value);

            final XiaomiManualSample sample = new XiaomiManualSample();
            sample.setTimestamp(timestamp * 1000L);
            sample.setType(type);
            sample.setValue(value);

            samples.add(sample);
        }
        if (buf.hasRemaining()) {
            LOG.warn("Ignoring {} trailing manual sample bytes", buf.remaining());
        }

        return samples;
    }

    @Nullable
    static List<XiaomiManualSample> decodeVersion1(final XiaomiActivityFileId fileId, final byte[] bytes) {
        if (fileId.getVersion() != 1) {
            LOG.warn("Expected manual samples version 1, got {}", fileId.getVersion());
            return null;
        }

        // Mi Band 9 Active files contain 7 file-id bytes, one padding byte, a 0xff
        // header, one or more 6-byte samples, and a final 4-byte CRC. Each sample is
        // a little-endian timestamp followed by a type and an unsigned byte value.
        if (bytes.length < 19) {
            LOG.warn("Manual samples v1 packet too short: {}", bytes.length);
            return null;
        }

        final ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.limit(buf.limit() - 4); // discard crc at the end
        buf.get(new byte[7]); // skip fileId bytes

        final byte fileIdPadding = buf.get();
        if (fileIdPadding != 0) {
            LOG.warn("Expected 0 padding after v1 fileId, got {}", fileIdPadding);
            return null;
        }

        final int header = buf.get() & 0xff;
        if (header != 0xff) {
            LOG.warn("Unexpected manual samples v1 header {}", header);
            return null;
        }

        if (buf.remaining() == 0 || buf.remaining() % 6 != 0) {
            LOG.warn("Unexpected manual samples v1 payload length {}", buf.remaining());
            return null;
        }

        final List<XiaomiManualSample> samples = new ArrayList<>();

        while (buf.hasRemaining()) {
            final int timestamp = buf.getInt();
            final int typeV1 = buf.get() & 0xff;
            final int value = buf.get() & 0xff;

            final int type;
            switch (typeV1) {
                case 0x02:
                    type = XiaomiManualSampleProvider.TYPE_SPO2;
                    break;
                case 0x03:
                    type = XiaomiManualSampleProvider.TYPE_STRESS;
                    break;
                default:
                    LOG.warn("Unknown manual samples v1 type {}", typeV1);
                    return null;
            }

            if (value == 0 || value > 100) {
                LOG.warn("Invalid manual samples v1 value {} for type {}", value, typeV1);
                return null;
            }

            LOG.debug("Got manual v1 sample: ts={} type={} value={}", timestamp, type, value);

            final XiaomiManualSample sample = new XiaomiManualSample();
            sample.setTimestamp(timestamp * 1000L);
            sample.setType(type);
            sample.setValue(value);
            samples.add(sample);
        }

        return samples;
    }

    private boolean persistSamples(
            final Context context,
            final GBDevice gbDevice,
            final List<XiaomiManualSample> samples) {

        try (DBHandler handler = GBApplication.acquireDB()) {
            final DaoSession session = handler.getDaoSession();
            final XiaomiManualSampleProvider sampleProvider = new XiaomiManualSampleProvider(gbDevice, session);
            sampleProvider.persistSamples(samples, context);
        } catch (final Exception e) {
            GB.toast(context, "Error saving manual samples", Toast.LENGTH_LONG, GB.ERROR);
            LOG.error("Error saving manual samples", e);
            return false;
        }

        return true;
    }
}
