/*  Copyright (C) 2026 Davide Gessa

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.casio.ggb100;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.devices.casio.CasioConstants;
import nodomain.freeyourgadget.gadgetbridge.externalevents.opentracks.OpenTracksController;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEOperation;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.casio.Casio2C2DSupport;
import nodomain.freeyourgadget.gadgetbridge.webview.CurrentPosition;

/**
 * GG-B100 sync handshake, run after the APP_INFO / BLE_FEAT exchange (handled in
 * CasioGGB100DeviceSupport, which passes the BLE_FEAT connection reason here)
 *
 * Reasons 00 (pairing) / 01 (app session) / 08 (mission log):
 *   1. request_echo BLE_SETTINGS (0x11)
 *   2. status block (0x05 param 0x1c) - fetched on CONVOY/h0011, not ALL_REQ;
 *      skipped for now (TODO)
 *   3. request NEW_DATA_STATUS (0x37) → log pending-data flags
 *   4. mission-log (0x19) + LIFE LOG (0x11) block fetches on h0011/CONVOY,
 *      each completed with an ACK - this is what confirms a mission START/GOAL
 *      on the watch side
 * Reasons 00 / 01 / 03 / 08:
 *   5. version_round: VER_INFO / WATCH_COND / VER_INFO / WATCH_COND
 *   6. city block: DST_WATCH_STATE (0x1d) echo → DST_SETTING (0x1e) slots
 *      (save 0, echo both) + GPS chunks (0x24) → WORLD_CITY (0x1f) slots
 *      (save 0, echo both) → SENSOR/DISPLAY config (0x2f) echo
 * Reason 03 only:
 *   7. TX power read on h0009 (TODO) + request 0x36
 * Reasons 00 / 01 only:
 *   8. request MODE_CUST (0x38)
 * All:
 *   9. write CURRENT_TIME (0x09) - always the last step; no 47 01 confirmation
 *      follows on the GG-B100
 *  10. (except reason 08) after initialization, fetch the settings features
 *      0x15/0x16 so the Casio2C2DSupport alarm/chime cache is populated
 *
 * Reason 04 (manual sync) runs only steps 5, 6 and 9
 */
public class InitOperation extends AbstractBTLEOperation<CasioGGB100DeviceSupport> {
    private static final Logger LOG = LoggerFactory.getLogger(InitOperation.class);

    // Unknown feature requested at the end of scheduled-sync (reason 03) flows
    private static final byte FEATURE_STILL_UNKNOWN_36 = 0x36;

    // Linear init state machine
    private static final int S_BLE_SETTINGS   = 0;
    private static final int S_NEW_DATA       = 1;
    private static final int S_VER_1          = 2;
    private static final int S_COND_1         = 3;
    private static final int S_VER_2          = 4;
    private static final int S_COND_2         = 5;
    private static final int S_DST_WATCH      = 6;
    private static final int S_DST_SETTING_0  = 7;
    private static final int S_DST_SETTING_1  = 8;
    private static final int S_WORLD_CITY_0   = 9;
    private static final int S_WORLD_CITY_1   = 10;
    private static final int S_FEAT_2F        = 11;
    private static final int S_UNKNOWN_36     = 12;
    private static final int S_MODE_CUST      = 13;
    private static final int S_DONE           = 14;
    private static final int S_FETCH_MISSION  = 15;
    private static final int S_FETCH_LIFELOG  = 16;

    private static final int FETCH_TIMEOUT_MS = 15_000;

    private int mState;
    private final byte mConnectionReason;
    private byte[] mDstSetting0 = null;
    private byte[] mWorldCity0 = null;

    // Block fetch (h0011/CONVOY) state
    private byte mFetchFeature = 0;
    private int mNewDataFlags = FetchMissionLogDataOperation.NEW_DATA_UNKNOWN;
    private int mExpectedLength = -1;
    private final ByteArrayOutputStream mBuffer = new ByteArrayOutputStream();
    private final Handler mFetchTimeoutHandler = new Handler(Looper.getMainLooper());

    private final CasioGGB100DeviceSupport mSupport;

    public InitOperation(CasioGGB100DeviceSupport support, byte connectionReason) {
        super(support);
        this.mSupport = support;
        this.mConnectionReason = connectionReason;
    }

    // ── Write helpers ─────────────────────────────────────────────────────────

    private void req(byte feat) {
        req(new byte[]{feat});
    }

    private void req(byte feat, byte slot) {
        req(new byte[]{feat, slot});
    }

    private void req(byte[] request) {
        TransactionBuilder b = createTransactionBuilder("req_" + String.format("%02x", request[0]));
        mSupport.writeAllFeaturesRequest(b, request);
        b.queue();
    }

    private void write(byte[] data) {
        TransactionBuilder b = createTransactionBuilder("write_" + String.format("%02x", data[0]));
        mSupport.writeAllFeatures(b, data);
        b.queue();
    }

    // ── Time write ────────────────────────────────────────────────────────────

    private void writeCurrentTime() {
        write(Casio2C2DSupport.buildCurrentTimePacket(ZonedDateTime.now()));
    }

    // ── Block fetch (mission log 0x19 / LIFE LOG 0x11 on DATA_REQ_SP + CONVOY) ──

    // Reasons 00/01/03/08 fetch mission and life log after NEW_DATA; send back an ACK
    private void beginBlockFetch(byte feat) {
        mFetchFeature = feat;
        mBuffer.reset();
        mExpectedLength = -1;
        mFetchTimeoutHandler.postDelayed(this::onFetchTimeout, FETCH_TIMEOUT_MS);
        TransactionBuilder b = createTransactionBuilder("fetch_" + String.format("%02x", feat));
        // the official app toggles the DATA_REQ/CONVOY CCCDs off and on around
        // every fetch
        b.notify(CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID, false);
        b.notify(CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID, false);
        b.notify(CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID, true);
        b.notify(CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID, true);
        b.writeLegacy(getCharacteristic(CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID),
                new byte[]{0x00, feat, 0x00, 0x00, 0x00});
        b.queue();
    }

    private void ackBlockFetch() {
        TransactionBuilder b = createTransactionBuilder("fetch_ack_" + String.format("%02x", mFetchFeature));
        b.writeLegacy(getCharacteristic(CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID),
                new byte[]{0x04, mFetchFeature, 0x00, 0x00, 0x00});
        b.queue();
    }

    private void endBlockFetch() {
        mFetchTimeoutHandler.removeCallbacksAndMessages(null);
        TransactionBuilder b = createTransactionBuilder("fetch_end");
        b.notify(CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID, false);
        b.notify(CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID, false);
        b.queue();
    }

    private void onFetchTimeout() {
        mFetchTimeoutHandler.removeCallbacksAndMessages(null);
        LOG.warn("Init[fetch 0x{}] timed out — continuing", String.format("%02x", mFetchFeature));
        advanceFetch();
    }

    private void advanceFetch() {
        mFetchTimeoutHandler.removeCallbacksAndMessages(null);
        if (mState == S_FETCH_MISSION) {
            mState = S_FETCH_LIFELOG;
            beginBlockFetch(Casio2C2DSupport.FEATURE_LIFE_LOG);
        } else {
            endBlockFetch();
            startVersionRound();
        }
    }

    private void handleBlockFetchCharacteristicChanged(UUID uuid, byte[] data) {
        if (uuid.equals(CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID)) {
            // echo with the total payload length
            if (data.length >= 4 && data[0] == 0x00 && data[1] == mFetchFeature) {
                mExpectedLength = (data[2] & 0xff) | ((data[3] & 0xff) << 8);
                LOG.debug("Init[fetch 0x{}] incoming: {} bytes",
                        String.format("%02x", mFetchFeature), mExpectedLength);
            }
        } else if (uuid.equals(CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID)) {
            mBuffer.write(data, 0, data.length);
            final int expected = mExpectedLength > 0 ? mExpectedLength
                    : (mFetchFeature == Casio2C2DSupport.FEATURE_MISSION_LOG_DATA ? 294 : 160);
            if (mBuffer.size() >= expected) {
                final byte[] payload = mBuffer.toByteArray();
                if (mFetchFeature == Casio2C2DSupport.FEATURE_MISSION_LOG_DATA) {
                    FetchMissionLogDataOperation.parseMissionLog(mSupport, payload, mNewDataFlags);
                } else {
                    FetchStepCountDataOperation.parseLifeLog(mSupport, payload);
                }
                ackBlockFetch();
                advanceFetch();
            }
        }
    }

    // ── Flow entry points ────────────────────────────────────────────────────

    private void startFlow() {
        if (mConnectionReason == 0x00 || mConnectionReason == 0x01 || mConnectionReason == 0x08) {
            mState = S_BLE_SETTINGS;
            req(Casio2C2DSupport.FEATURE_SETTING_FOR_BLE);
        } else if (mConnectionReason == 0x03) {
            // TODO: scheduled syncs start with the status block fetch (0x05,
            // param 0x1c) on CONVOY/h0011 - skipped until the CONVOY channel
            // is implemented
            mState = S_NEW_DATA;
            req(Casio2C2DSupport.FEATURE_NEW_DATA_STATUS);
        } else {
            // 0x04 (manual sync) and anything else: time-only flow
            startVersionRound();
        }
    }

    private void startVersionRound() {
        mState = S_VER_1;
        req(Casio2C2DSupport.FEATURE_VERSION_INFORMATION);
    }

    private void startCityBlock() {
        mState = S_DST_WATCH;
        req(Casio2C2DSupport.FEATURE_DST_WATCH_STATE);
    }

    private void startTail() {
        if (mConnectionReason == 0x00 || mConnectionReason == 0x01) {
            mState = S_MODE_CUST;
            req(Casio2C2DSupport.FEATURE_MODE_DISPLAY_CUSTOMIZATION);
        } else if (mConnectionReason == 0x03) {
            // TODO: the app also reads the TX power characteristic on h0009 here
            mState = S_UNKNOWN_36;
            req(FEATURE_STILL_UNKNOWN_36);
        } else {
            finishWithTime();
        }
    }

    private void finishWithTime() {
        writeCurrentTime();
        mSupport.setInitialized();
        mState = S_DONE;

        if (mConnectionReason != 0x08) {
            mSupport.fetchSettings();
        }
    }

    // ── AbstractBTLEOperation ────────────────────────────────────────────────

    @Override
    protected void doPerform() throws IOException {
        startFlow();
    }

    @Override
    protected void operationFinished() throws IOException {
        mFetchTimeoutHandler.removeCallbacksAndMessages(null);
        super.operationFinished();
    }

    @Override
    public TransactionBuilder performInitialized(String taskName) throws IOException {
        throw new UnsupportedOperationException("This IS the initialization class");
    }

    // ── Main state machine ───────────────────────────────────────────────────

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic,
                                           byte[] data) {
        UUID uuid = characteristic.getUuid();

        if (data == null || data.length == 0) return true;

        if (mState == S_FETCH_MISSION || mState == S_FETCH_LIFELOG) {
            if (uuid.equals(CasioConstants.CASIO_DATA_REQUEST_SP_CHARACTERISTIC_UUID)
                    || uuid.equals(CasioConstants.CASIO_CONVOY_CHARACTERISTIC_UUID)) {
                handleBlockFetchCharacteristicChanged(uuid, data);
                return true;
            }
        }

        if (!uuid.equals(CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID)) {
            return super.onCharacteristicChanged(gatt, characteristic, data);
        }

        byte feat = data[0];

        switch (mState) {
            case S_BLE_SETTINGS:
                if (feat == Casio2C2DSupport.FEATURE_SETTING_FOR_BLE) {
                    LOG.debug("Init[BLE_SETTINGS] → echo + request NEW_DATA");
                    write(data.clone()); // echo back
                    // TODO: status block fetch (0x05, param 0x1c) goes here for
                    // reasons 00/01/08 - on CONVOY/h0011, not ALL_REQ
                    mState = S_NEW_DATA;
                    req(Casio2C2DSupport.FEATURE_NEW_DATA_STATUS);
                }
                break;

            case S_NEW_DATA:
                if (feat == Casio2C2DSupport.FEATURE_NEW_DATA_STATUS) {
                    if (data.length > 1) {
                        int flags = data[1];
                        mNewDataFlags = flags;
                        StringBuilder parts = new StringBuilder();
                        if ((flags & 0x01) != 0) parts.append("mission boundary record(s) ");
                        if ((flags & 0x02) != 0) parts.append("mission-log series/point data ");
                        if (data.length >= 8 && data[2] != (byte) 0xff) {
                            parts.append("GOAL location point saved");
                        }
                        LOG.info("Init[NEW_DATA] flags=0x{}: {}", String.format("%02x", flags),
                                parts.length() > 0 ? parts.toString().trim() : "nothing pending");

                        if (mSupport.getDevicePrefs().getBoolean(
                                DeviceSettingsPreferenceConst.PREF_WORKOUT_START_ON_PHONE, false)) {
                            if (flags == 0x01) {
                                LOG.info("Init[NEW_DATA] mission START — starting OpenTracks recording");
                                OpenTracksController.startRecording(getContext(), ActivityKind.ACTIVITY);
                            } else if (flags == 0x03) {
                                LOG.info("Init[NEW_DATA] mission GOAL — stopping OpenTracks recording");
                                OpenTracksController.stopRecording(getContext());
                            }
                        }
                    }
                    // Reasons 00/01/03/08 fetch the mission log and life log blocks
                    mState = S_FETCH_MISSION;
                    beginBlockFetch(Casio2C2DSupport.FEATURE_MISSION_LOG_DATA);
                }
                break;

            case S_VER_1:
                if (feat == Casio2C2DSupport.FEATURE_VERSION_INFORMATION) {
                    mState = S_COND_1;
                    req(Casio2C2DSupport.FEATURE_WATCH_CONDITION);
                }
                break;

            case S_COND_1:
                if (feat == Casio2C2DSupport.FEATURE_WATCH_CONDITION) {
                    mState = S_VER_2;
                    req(Casio2C2DSupport.FEATURE_VERSION_INFORMATION);
                }
                break;

            case S_VER_2:
                if (feat == Casio2C2DSupport.FEATURE_VERSION_INFORMATION) {
                    mState = S_COND_2;
                    req(Casio2C2DSupport.FEATURE_WATCH_CONDITION);
                }
                break;

            case S_COND_2:
                if (feat == Casio2C2DSupport.FEATURE_WATCH_CONDITION) {
                    LOG.debug("Init[version_round done] → city block");
                    startCityBlock();
                }
                break;

            case S_DST_WATCH:
                if (feat == Casio2C2DSupport.FEATURE_DST_WATCH_STATE) {
                    LOG.debug("Init[DST_WATCH] → echo + request DST_SETTING slot 0");
                    write(data.clone()); // echo back
                    mState = S_DST_SETTING_0;
                    req(Casio2C2DSupport.FEATURE_DST_SETTING, (byte) 0x00);
                }
                break;

            case S_DST_SETTING_0:
                if (feat == Casio2C2DSupport.FEATURE_DST_SETTING) {
                    LOG.debug("Init[DST_SETTING_0] → saved, requesting slot 1");
                    mDstSetting0 = data.clone();
                    mState = S_DST_SETTING_1;
                    req(Casio2C2DSupport.FEATURE_DST_SETTING, (byte) 0x01);
                }
                break;

            case S_DST_SETTING_1:
                if (feat == Casio2C2DSupport.FEATURE_DST_SETTING) {
                    LOG.debug("Init[DST_SETTING_1] → echo both + GPS + request WORLD_CITY slot 0");
                    write(mDstSetting0);      // echo slot 0
                    write(data.clone());       // echo slot 1
                    final Location l = new CurrentPosition().getLastKnownLocation();
                    final double lat = l != null ? l.getLatitude() : 0;
                    final double lon = l != null ? l.getLongitude() : 0;
                    write(Casio2C2DSupport.buildGpsChunk(0, lat, lon));
                    // TODO: chunk slot 01 carries the world-city coordinates, not
                    // altitude; resolve them from the world-city slots (0x1f)
                    write(Casio2C2DSupport.buildGpsChunk(1, 0, 0));
                    mState = S_WORLD_CITY_0;
                    req(Casio2C2DSupport.FEATURE_WORLD_CITY, (byte) 0x00);
                }
                break;

            case S_WORLD_CITY_0:
                if (feat == Casio2C2DSupport.FEATURE_WORLD_CITY) {
                    LOG.debug("Init[WORLD_CITY_0] → saved, requesting slot 1");
                    mWorldCity0 = data.clone();
                    mState = S_WORLD_CITY_1;
                    req(Casio2C2DSupport.FEATURE_WORLD_CITY, (byte) 0x01);
                }
                break;

            case S_WORLD_CITY_1:
                if (feat == Casio2C2DSupport.FEATURE_WORLD_CITY) {
                    LOG.debug("Init[WORLD_CITY_1] → echo both + request SENSOR/DISPLAY config");
                    write(mWorldCity0);   // echo slot 0
                    write(data.clone());  // echo slot 1
                    mState = S_FEAT_2F;
                    req(Casio2C2DSupport.FEATURE_FEAT_2F);
                }
                break;

            case S_FEAT_2F:
                if (feat == Casio2C2DSupport.FEATURE_FEAT_2F) {
                    LOG.debug("Init[SENSOR/DISPLAY] → echo + tail");
                    write(data.clone());
                    startTail();
                }
                break;

            case S_UNKNOWN_36:
                if (feat == FEATURE_STILL_UNKNOWN_36) {
                    LOG.debug("Init[0x36] → write CURRENT_TIME");
                    finishWithTime();
                }
                break;

            case S_MODE_CUST:
                if (feat == Casio2C2DSupport.FEATURE_MODE_DISPLAY_CUSTOMIZATION) {
                    LOG.debug("Init[MODE_CUST] → write CURRENT_TIME");
                    finishWithTime();
                }
                break;

            case S_DONE:
            default:
                break;
        }

        return true;
    }

    @Override
    public boolean onCharacteristicRead(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic,
                                        byte[] value, int status) {
        return super.onCharacteristicRead(gatt, characteristic, value, status);
    }
}
