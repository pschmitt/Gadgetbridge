/*  Copyright (C) 2024-2026 Daniele Gobbetti, José Rebelo, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.ChecksumCalculator;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminByteBufferReader;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.status.GFDIStatusMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.status.GenericStatusMessage;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public abstract class GFDIMessage {
    protected static final Logger LOG = LoggerFactory.getLogger(GFDIMessage.class);
    protected final ByteBuffer response = ByteBuffer.allocate(10 * 1024); // FIXME we should allocate the minimum necessary for each message
    protected GFDIStatusMessage statusMessage;
    protected GarminMessage garminMessage;

    public static GFDIMessage parseIncoming(byte[] message) {
        final MessageReader messageReader = new MessageReader(message);

        boolean supportedType = false;
        int messageType = messageReader.readShort();
        try {
            if ((messageType & 0x8000) != 0) {
                // final int sequenceNumber = (messageType >> 8) & 0x7f;
                messageType = (messageType & 0xff) + 5000;
            }
            final GarminMessage garminMessage = GarminMessage.fromId(messageType);
            if (garminMessage == null) {
                return new UnhandledMessage(messageType);
            }
            supportedType = true;
            final GFDIMessage gfdiMessage = switch(garminMessage){
                case RESPONSE -> GFDIStatusMessage.parseIncoming(messageReader, garminMessage);
                case FILE_TRANSFER_DATA -> FileTransferDataMessage.parseIncoming(messageReader, garminMessage);
                case CREATE_FILE -> CreateFileMessage.parseIncoming(messageReader, garminMessage);
                case FILE_AVAILABLE -> FileAvailableMessage.parseIncoming(messageReader, garminMessage);
                case FIT_DEFINITION -> FitDefinitionMessage.parseIncoming(messageReader, garminMessage);
                case FIT_DATA ->  FitDataMessage.parseIncoming(messageReader, garminMessage);
                case WEATHER_REQUEST -> WeatherRequestMessage.parseIncoming(messageReader, garminMessage);
                case BATTERY_STATUS -> BatteryStatusMessage.parseIncoming(messageReader, garminMessage);
                case DEVICE_INFORMATION -> DeviceInformationMessage.parseIncoming(messageReader, garminMessage);
                case NOTIFICATION_CONTROL -> NotificationControlMessage.parseIncoming(messageReader, garminMessage);
                case NOTIFICATION_SUBSCRIPTION -> NotificationSubscriptionMessage.parseIncoming(messageReader, garminMessage);
                case SYNCHRONIZATION -> SynchronizationMessage.parseIncoming(messageReader, garminMessage);
                case FIND_MY_PHONE_REQUEST -> FindMyPhoneRequestMessage.parseIncoming(messageReader, garminMessage);
                case FIND_MY_PHONE_CANCEL -> FindMyPhoneCancelMessage.parseIncoming(messageReader, garminMessage);
                case MUSIC_CONTROL -> MusicControlMessage.parseIncoming(messageReader, garminMessage);
                case MUSIC_CONTROL_CAPABILITIES -> MusicControlCapabilitiesMessage.parseIncoming(messageReader, garminMessage);
                case PROTOBUF_REQUEST -> ProtobufMessage.parseIncoming(messageReader, garminMessage);
                case PROTOBUF_RESPONSE -> ProtobufMessage.parseIncoming(messageReader, garminMessage);
                case MUSIC_CONTROL_ENTITY_UPDATE -> MusicControlEntityUpdateMessage.parseIncoming(messageReader, garminMessage);
                case CONFIGURATION -> ConfigurationMessage.parseIncoming(messageReader, garminMessage);
                case CURRENT_TIME_REQUEST -> CurrentTimeRequestMessage.parseIncoming(messageReader, garminMessage);
                case AUTH_NEGOTIATION -> AuthNegotiationMessage.parseIncoming(messageReader, garminMessage);
                default -> {
                    supportedType = false;
                    yield new UnhandledMessage(garminMessage);
                }
            };

            return gfdiMessage;
        } catch (final Exception e) {
            LOG.error("UNHANDLED GFDI MESSAGE TYPE {}, MESSAGE {}", messageType, message, e);
            return new UnhandledMessage(messageType);
        } finally {
            messageReader.warnIfLeftover(messageType, supportedType);
        }
    }

    public GarminMessage getGarminMessage() {
        return garminMessage;
    }

    protected abstract boolean generateOutgoing();

    public byte[] getOutgoingMessage() {
        response.clear();
        boolean toSend = generateOutgoing();
        response.order(ByteOrder.LITTLE_ENDIAN);
        if (!toSend)
            return null;
        addLengthAndChecksum();
        response.flip();

        final byte[] packet = new byte[response.limit()];
        response.get(packet);
        return packet;
    }

    protected GFDIStatusMessage getStatusMessage() {
        return new GenericStatusMessage(garminMessage, Status.ACK);
    }

    public List<GBDeviceEvent> getGBDeviceEvent() {
        return Collections.emptyList();
    }

    public byte[] getAckBytestream() {
        if (null == this.statusMessage) {
            return null;
        }
        return this.statusMessage.getOutgoingMessage();
    }

    private void addLengthAndChecksum() {
        response.putShort(0, (short) (response.position() + 2));
        response.putShort((short) ChecksumCalculator.computeCrc(response.asReadOnlyBuffer(), 0, response.position()));
    }

    public enum GarminMessage {
        RESPONSE(5000), //TODO: STATUS is a better name?
        DOWNLOAD_REQUEST(5002),
        UPLOAD_REQUEST(5003),
        FILE_TRANSFER_DATA(5004),
        CREATE_FILE(5005),
        DELETE_FILE(5006),
        FILTER(5007),
        SET_FILE_FLAG(5008),
        FILE_AVAILABLE(5009),
        FIT_DEFINITION(5011),
        FIT_DATA(5012),
        WEATHER_REQUEST(5014),
        WEATHER_ALERT(5015),
        LIVE_TRACK_TRACKING_REQUEST(5016),
        EPHEMERIS_DATA_REQUEST(5019),
        EPHEMERIS_DATA(5020),
        CANCEL_FILE_TRANSFER(5022),
        BATTERY_STATUS(5023),
        DEVICE_INFORMATION(5024),
        LIVE_TRACK_STOP_TRACKING(5025),
        DEVICE_SETTINGS(5026),
        QUEUED_DOWNLOAD(5027),
        EPHEMERIS_EPO_REQUEST(5028),
        EPHEMERIS_EPO_DATA(5029),
        SYSTEM_EVENT(5030),
        SUPPORTED_FILE_TYPES_REQUEST(5031),
        NOTIFICATION_UPDATE(5033),
        NOTIFICATION_CONTROL(5034),
        NOTIFICATION_DATA(5035),
        NOTIFICATION_SUBSCRIPTION(5036),
        SYNCHRONIZATION(5037),
        FIND_MY_PHONE_REQUEST(5039),
        FIND_MY_PHONE_CANCEL(5040),
        MUSIC_CONTROL(5041),
        MUSIC_CONTROL_CAPABILITIES(5042),
        PROTOBUF_REQUEST(5043),
        PROTOBUF_RESPONSE(5044),
        CANCEL_PROTOBUF(5045),
        LIVE_TRACK_AUTO_START(5046),
        LIVE_TRACK_AUTO_START_CANCEL(5047),
        LIVE_TRACK_AUTO_START_FAILURE(5048),
        MUSIC_CONTROL_ENTITY_UPDATE(5049),
        CONFIGURATION(5050),
        CURRENT_TIME_REQUEST(5052),
        COMPRESSED_FILE_DATA(5054),
        VECTOR_PEDALS_PAIRING(5055),
        VECTOR_SENSOR_PAIRING(5056),
        COIN_REQUEST(5060),
        CHORE_REQUEST(5061),
        TIMER_REQUEST(5062),
        ACTIVE_TIME_REQUEST(5063),
        GRAPHIC_SET(5064),
        GRAPHIC_QUERY(5065),
        SET_GAME_LEVEL(5066),
        AUTH_NEGOTIATION(5101),
        LTK_RECONNECT(5102),
        STK_BEGIN_GENERATION(5103),
        CONFIRM_NUMBER(5104),
        STK_RANDOM_NUMBER(5105),
        STK_GENERATION_STATUS(5106),
        LTK_KEY_DISTRIBUTION(5107),
        SESSION_SKD_DISTRIBUTION(5108),
        SESSION_KEY_VERIFICATION(5109),
        PASSKEY_REDISPLAY(5110),
        SECURE_SESSION(5111),
        OUT_OF_BAND_PASSKEY_DATA(5112),
        ;

        private final int id;

        GarminMessage(int id) {
            this.id = id;
        }

        @Nullable
        public static GarminMessage fromId(final int id) {
            for (final GarminMessage garminMessage : GarminMessage.values()) {
                if (garminMessage.getId() == id) {
                    return garminMessage;
                }
            }
            return null;
        }

        public int getId() {
            return id;
        }
    }

    public enum Status {
        ACK,
        NAK,
        UNSUPPORTED,
        DECODE_ERROR,
        CRC_ERROR,
        LENGTH_ERROR;

        public static Status fromCode(final int code) {
            for (final Status status : Status.values()) {
                if (status.ordinal() == code) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown status code " + code);
        }

    }

    public static class MessageReader extends GarminByteBufferReader {

        private final int payloadSize;

        public MessageReader(byte[] data) {
            super(data);
            this.byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            this.payloadSize = readShort(); //includes CRC
            checkSize();
            checkCRC();
            this.byteBuffer.limit(payloadSize - 2); //remove CRC
        }

        public void skip(int offset) {
            if (remaining() < offset) throw new IllegalStateException();
            byteBuffer.position(byteBuffer.position() + offset);
        }

        private void checkSize() {
            if (payloadSize != byteBuffer.capacity()) {
                LOG.error("Received GFDI packet with invalid length: {} vs {}", payloadSize, byteBuffer.capacity());
                throw new IllegalArgumentException("Received GFDI packet with invalid length");
            }
        }

        private void checkCRC() {
            final int crc = Short.toUnsignedInt(byteBuffer.getShort(payloadSize - 2));
            final int correctCrc = ChecksumCalculator.computeCrc(byteBuffer.asReadOnlyBuffer(), 0, payloadSize - 2);
            if (crc != correctCrc) {
                LOG.error("Received GFDI packet with invalid CRC: {} vs {}", crc, correctCrc);
                throw new IllegalArgumentException("Received GFDI packet with invalid CRC");
            }
        }

        public void warnIfLeftover(int messageType, boolean supportedType) {
            if (byteBuffer.hasRemaining() && byteBuffer.position() < (byteBuffer.limit())) {
                int pos = byteBuffer.position();
                int numBytes = (byteBuffer.limit()) - byteBuffer.position();
                byte[] leftover = new byte[numBytes];
                byteBuffer.get(leftover);
                byteBuffer.position(pos);
                if (supportedType){
                    LOG.warn("Leftover bytes when parsing message type {}. Bytes: {}, complete message: {}", messageType, GB.hexdump(leftover), GB.hexdump(byteBuffer.array()));
                } else {
                    LOG.warn("Unknown message type {}. Bytes: {}", messageType, GB.hexdump(leftover));
                }
            }
        }
    }
}
