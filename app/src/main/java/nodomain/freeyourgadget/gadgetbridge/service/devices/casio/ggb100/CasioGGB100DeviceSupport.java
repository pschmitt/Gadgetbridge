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
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventFindPhone;
import nodomain.freeyourgadget.gadgetbridge.devices.casio.CasioConstants;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.casio.Casio2C2DSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.casio.CasioLocationIndicatorLibrary;
import nodomain.freeyourgadget.gadgetbridge.service.devices.casio.CasioLocationPoint;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.webview.CurrentPosition;

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_FIND_PHONE;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_FIND_PHONE_DURATION;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_CASIO_COUNTDOWN_TIMER;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_CASIO_PRESSURE_ENERGY_CALC;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_CASIO_COMPASS_AUTO_CORRECTION;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_CASIO_USER_PROFILE;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_CASIO_ALTITUDE_MEASUREMENT_INTERVAL;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_CASIO_ALTIMETER_DISPLAY;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_CASIO_PRESSURE_GRAPH;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_CASIO_MODE_CAROUSEL;
import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_CASIO_DISPLAY_SCREENS;

public class CasioGGB100DeviceSupport extends Casio2C2DSupport
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(CasioGGB100DeviceSupport.class);
    private final Handler mFindPhoneHandler = new Handler(Looper.getMainLooper());

    public CasioGGB100DeviceSupport() {
        super(LOG);
    }

    @Override
    public DevicePreference[] supportedDevicePreferences() {
        return new DevicePreference[] {
            new TimeFormatPreference(),
            new OperatingSoundPreference(),
            new AutoLightPreference(),
            new LongerLightDurationPreference(),
            new PressureEnergyCalcPreference(),
            new CompassAutoCorrectionPreference(),
            new AltitudeMeasurementIntervalPreference(),
            new AltimeterDisplayPreference(),
            new PressureGraphPreference(),
            new ModeCarouselPreference(),
            new DisplayScreensPreference(),
            new HourlyChimePreference(),
            new CountdownTimerPreference(),
            new TimeSyncPreference(),
            new ConnectionDurationPreference(),
            new UserProfilePreference(),
        };
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }


    // ── Initialization ────────────────────────────────────────────────────────

    private void resetState() {
        mFindPhoneHandler.removeCallbacksAndMessages(null);
    }

    @Override
    public void dispose() {
        resetState();
        super.dispose();
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        resetState();

        builder.setDeviceState(GBDevice.State.INITIALIZING);
        builder.notify(CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID, true);
        builder.writeLegacy(
                getCharacteristic(CasioConstants.CASIO_READ_REQUEST_FOR_ALL_FEATURES_CHARACTERISTIC_UUID),
                new byte[]{Casio2C2DSupport.FEATURE_APP_INFORMATION});

        getDevice().setFirmwareVersion("N/A");
        getDevice().setFirmwareVersion2("N/A");

        GBApplication.getPrefs().getPreferences().registerOnSharedPreferenceChangeListener(this);

        return builder;
    }

    // ── Write helpers ─────────────────────────────────────────────────────────

    private void req(byte feat) {
        TransactionBuilder b = createTransactionBuilder("req_" + String.format("%02x", feat));
        b.setCallback(this);
        writeAllFeaturesRequest(b, new byte[]{feat});
        b.queue();
    }

    private void req(byte feat, byte slot) {
        TransactionBuilder b = createTransactionBuilder("req_" + String.format("%02x", feat));
        b.setCallback(this);
        writeAllFeaturesRequest(b, new byte[]{feat, slot});
        b.queue();
    }

    private void write(byte[] data) {
        TransactionBuilder b = createTransactionBuilder("write_" + String.format("%02x", data[0]));
        b.setCallback(this);
        writeAllFeatures(b, data);
        b.queue();
    }

    // ── App information ──────────────────────────────────────────────────────

    // What the official app writes to APP_INFO at pairing time. The watch keeps it
    // across connections. Last byte = capabilities, 2 = BT time adjustment supported
    private static final byte[] APP_INFO_TOKEN = {
            0x22, 0x55, (byte) 0xf6, 0x55, 0x69,
            0x26, (byte) 0x2c, (byte) 0x6b, (byte) 0xb9, (byte) 0x7b, 0x02
    };

    /** Leave the stored token alone: the official app writes it once at pairing */
    private void checkAppInformation(byte[] data) {
        if (data.length >= 12 && data[11] == 0x02) {
            return;
        }
        LOG.info("APP_INFO on watch is {}, writing ours", GB.hexdump(data));
        byte[] arr = new byte[1 + APP_INFO_TOKEN.length];
        arr[0] = Casio2C2DSupport.FEATURE_APP_INFORMATION;
        System.arraycopy(APP_INFO_TOKEN, 0, arr, 1, APP_INFO_TOKEN.length);
        write(arr);
    }


    // ── Data fetch ────────────────────────────────────────────────────────────

    @Override
    public void onFetchRecordedData(int dataTypes) {
        try {
            new FetchStepCountDataOperation(this).perform();
        } catch (IOException e) {
            GB.toast(getContext(), "Error fetching step data",
                    Toast.LENGTH_SHORT, GB.ERROR, e);
            startFetchMissionLogData();
        }
    }

    public void onStepCountFetchFinished() {
        startFetchMissionLogData();
    }

    private void startFetchMissionLogData() {
        try {
            new FetchMissionLogDataOperation(this).perform();
        } catch (IOException e) {
            GB.toast(getContext(), "Error fetching sport data",
                    Toast.LENGTH_SHORT, GB.ERROR, e);
        }
    }

    // ── Phone finder ──────────────────────────────────────────────────────────

    private void onReverseFindDevice(boolean start) {
        if (start) {
            String findPhone = getDevicePrefs().getString(PREF_FIND_PHONE,
                    getContext().getString(R.string.p_off));
            if (findPhone.equals(getContext().getString(R.string.p_off))) return;

            GBDeviceEventFindPhone ev = new GBDeviceEventFindPhone();
            ev.event = GBDeviceEventFindPhone.Event.START;
            evaluateGBDeviceEvent(ev);

            if (findPhone.equals(getContext().getString(R.string.p_on))) {
                int duration = getDevicePrefs().getInt(PREF_FIND_PHONE_DURATION, 0);
                if (duration > 0) {
                    mFindPhoneHandler.postDelayed(() -> {
                        GBDeviceEventFindPhone stop = new GBDeviceEventFindPhone();
                        stop.event = GBDeviceEventFindPhone.Event.STOP;
                        evaluateGBDeviceEvent(stop);
                    }, duration * 1000L);
                }
            }
        } else {
            GBDeviceEventFindPhone ev = new GBDeviceEventFindPhone();
            ev.event = GBDeviceEventFindPhone.Event.STOP;
            evaluateGBDeviceEvent(ev);
        }
    }

    // ── Location Indicator ──────────────────────────────────────────────────────────

    private void onLocationIndicator(boolean indicate, byte[] data) {
        if (indicate) {
            LOG.debug("Watch asked us to indicate the active location point");

            final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
            final CasioLocationIndicatorLibrary lib = CasioLocationIndicatorLibrary.fromJson(
                    prefs.getString(CasioLocationIndicatorLibrary.PREF_LOCATION_INDICATOR_LIBRARY, null));
            final CasioLocationPoint active = lib.getActive();

            byte[] data_reply = new byte[9];
            data_reply[0] = Casio2C2DSupport.FEATURE_LOCATION_INDICATOR;
            data_reply[1] = 0x02;

            final Location l = new CurrentPosition().getLastKnownLocation();
            if (active == null || l == null) {
                data_reply[2] = 0x01; // status: 0 -> ok, 1 -> nothing to give
                write(data_reply);
                return;
            }

            // phone → ALL_FEAT  35 <mode> <st> <dist LE32> <bearing LE16>
            final GPSCoordinate current = new GPSCoordinate(l.getLongitude(), l.getLatitude());
            final long dist = Math.round(GPSCoordinate.distanceHaversine(current, active.waypoint));
            final int bearing = (int) Math.round(GPSCoordinate.bearing(current, active.waypoint)) % 360;

            data_reply[2] = 0x00; // status: ok
            data_reply[3] = (byte) (dist & 0xff);
            data_reply[4] = (byte) ((dist >> 8) & 0xff);
            data_reply[5] = (byte) ((dist >> 16) & 0xff);
            data_reply[6] = (byte) ((dist >> 24) & 0xff);
            data_reply[7] = (byte) (bearing & 0xff);
            data_reply[8] = (byte) ((bearing >> 8) & 0xff);
            write(data_reply);
        } else {
            LOG.debug("Watch asked us to memorize the current location.");

            Location l = new CurrentPosition().getLastKnownLocation();

            byte[] data_reply = new byte[9];
            data_reply[0] = Casio2C2DSupport.FEATURE_LOCATION_INDICATOR;

            if (l == null) {
                LOG.warn("No GPS fix, cannot memorize location");
                data_reply[2] = 0x01; // status: 0 -> ok, 1 -> no fix
                write(data_reply);
                return;
            }

            final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
            final CasioLocationIndicatorLibrary lib = CasioLocationIndicatorLibrary.fromJson(
                    prefs.getString(CasioLocationIndicatorLibrary.PREF_LOCATION_INDICATOR_LIBRARY, null));

            CasioLocationPoint point = new CasioLocationPoint();
            point.label = getContext().getString(
                    R.string.casio_location_indicator_default_label, lib.locations.size() + 1);
            point.timestamp = System.currentTimeMillis();
            point.waypoint = new GPSCoordinate(l.getLongitude(), l.getLatitude(),
                    l.hasAltitude() ? l.getAltitude() : GPSCoordinate.UNKNOWN_ALTITUDE);

            lib.add(point);
            prefs.edit()
                    .putString(CasioLocationIndicatorLibrary.PREF_LOCATION_INDICATOR_LIBRARY, lib.toJson())
                    .apply();

            data_reply[2] = 0x00; // status: ok
            write(data_reply);
        }
    }

    // ── Settings ───────────────────

    public void fetchSettings() {
        if (!isConnected() || !isInitialized()) return;
        try {
            TransactionBuilder b = performInitialized("fetchSettings");
            initializeDeviceSettings(b);
            b.queue();
        } catch (IOException e) {
            LOG.error("fetchSettings failed: {}", e.getMessage());
        }
    }

    // ── Countdown timer (0x18) ────────────────────────────────────────────────

    public class CountdownTimerPreference extends DevicePreference {
        { name = PREF_CASIO_COUNTDOWN_TIMER; }
        { feature = FEATURE_COUNTDOWN_TIMER; }

        @Override
        public void updateValue(byte[] data) {
            if (data.length < 4) {
                LOG.warn("countdown timer data too small: {}", Arrays.toString(data));
                return;
            }
            final int totalSeconds = parseCountdownTimer(getDevicePrefs().getString(name, "0:00:00"));
            data[1] = (byte) (totalSeconds / 3600);
            data[2] = (byte) ((totalSeconds / 60) % 60);
            data[3] = (byte) (totalSeconds % 60);
        }

        @Override
        public boolean readValue(byte[] data, SharedPreferences.Editor editor) {
            if (data.length < 4) {
                LOG.warn("countdown timer data too small: {}", Arrays.toString(data));
                return false;
            }
            final String value = String.format(Locale.ROOT, "%d:%02d:%02d",
                    data[1] & 0xff, data[2] & 0xff, data[3] & 0xff);
            if (!value.equals(getDevicePrefs().getString(name, "0:00:00"))) {
                editor.putString(name, value);
            }
            return false;
        }
    }

    /** Parses a {@code HH:MM:SS} duration */
    private static int parseCountdownTimer(final String value) {
        int hours = 0, minutes = 0, seconds = 0;
        final String[] parts = value.trim().split(":");
        try {
            if (parts.length > 0) hours = Integer.parseInt(parts[0]);
            if (parts.length > 1) minutes = Integer.parseInt(parts[1]);
            if (parts.length > 2) seconds = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            LOG.warn("Invalid countdown timer value '{}'", value);
        }
        final int total = hours * 3600 + minutes * 60 + seconds;
        return Math.max(0, Math.min(total, 24 * 3600));
    }

    // ── User profile (0x2d) ───────────────────────────────────────────────────

    /**
     * Step goal, height and weight
     * byte[2] = step goal × 16 / 1000 (0x80 = 8000 steps)
     * byte[6] = 313 − height in cm (0x8f = 170 cm)
     * byte[8] = 219 − weight in kg (0x9a = 65 kg)
     */
    public class UserProfilePreference extends DevicePreference {
        { name = PREF_CASIO_USER_PROFILE; }
        { feature = FEATURE_USER_PROFILE; }

        @Override
        public void updateValue(byte[] data) {
            if (data.length < 9) {
                LOG.warn("user profile data too small: {}", Arrays.toString(data));
                return;
            }
            final ActivityUser user = new ActivityUser();
            data[2] = encodeStepGoal(user.getStepsGoal());
            data[6] = encodeHeight(user.getHeightCm());
            data[8] = encodeWeight(user.getWeightKg());
        }

        @Override
        public boolean readValue(byte[] data, SharedPreferences.Editor editor) {
            if (data.length < 9) {
                LOG.warn("user profile data too small: {}", Arrays.toString(data));
                return false;
            }
            LOG.debug("user profile on watch: {} steps, {} cm, {} kg",
                    (data[2] & 0xff) * 1000 / 16, 313 - (data[6] & 0xff), 219 - (data[8] & 0xff));
            final ActivityUser user = new ActivityUser();
            return data[2] != encodeStepGoal(user.getStepsGoal())
                || data[6] != encodeHeight(user.getHeightCm())
                || data[8] != encodeWeight(user.getWeightKg());
        }

        private static byte encodeStepGoal(final int stepsGoal) {
            return (byte) Math.min(0xff, Math.max(0, stepsGoal) * 16 / 1000);
        }

        private static byte encodeHeight(final int heightCm) {
            return (byte) (313 - Math.max(58, Math.min(heightCm, 313)));
        }

        private static byte encodeWeight(final int weightKg) {
            return (byte) (219 - Math.max(-36, Math.min(weightKg, 219)));
        }
    }

    // ── Basic settings (0x13) ─────────────────────────────────────────────────

    /** Pressure sensor for energy calc: byte[8] bit 0x04, clear = on */
    public class PressureEnergyCalcPreference extends BoolDevicePreference {
        { name = PREF_CASIO_PRESSURE_ENERGY_CALC; }
        { feature = FEATURE_SETTING_FOR_BASIC; }
        { index = 8; mask = 0x04; }
    }

    /** Compass auto correction: byte[8] bit 0x08, clear = on */
    public class CompassAutoCorrectionPreference extends BoolDevicePreference {
        { name = PREF_CASIO_COMPASS_AUTO_CORRECTION; }
        { feature = FEATURE_SETTING_FOR_BASIC; }
        { index = 8; mask = 0x08; }
    }

    // ── Sensor/display config (0x2f) ──────────────────────────────────────────

    /** Altitude auto-measurement interval: byte[1] bit 0x04, set = 2 min, clear = 5 s */
    public class AltitudeMeasurementIntervalPreference extends DevicePreference {
        { name = PREF_CASIO_ALTITUDE_MEASUREMENT_INTERVAL; }
        { feature = FEATURE_FEAT_2F; }

        @Override
        public void updateValue(byte[] data) {
            if (data.length < 2) {
                LOG.warn("sensor config data too small: {}", Arrays.toString(data));
                return;
            }
            if (getMode() == 1) {
                data[1] |= 0x04;
            } else {
                data[1] &= ~0x04;
            }
        }

        @Override
        public boolean readValue(byte[] data, SharedPreferences.Editor editor) {
            if (data.length < 2) {
                LOG.warn("sensor config data too small: {}", Arrays.toString(data));
                return false;
            }
            final String value = (data[1] & 0x04) != 0 ? "1" : "0";
            if (!value.equals(getDevicePrefs().getString(name, "1"))) {
                editor.putString(name, value);
            }
            return false;
        }

        private int getMode() {
            return "1".equals(getDevicePrefs().getString(name, "1")) ? 1 : 0;
        }
    }

    /** Altimeter display: byte[1] bits 0x08|0x10, 0x10 = plain, 0x08 = ±100 m, 0x18 = ±1000 m, 0x00 = plain */
    public class AltimeterDisplayPreference extends DevicePreference {
        { name = PREF_CASIO_ALTIMETER_DISPLAY; }
        { feature = FEATURE_FEAT_2F; }

        private static final int[] MODE_BITS = { 0x10, 0x08, 0x18 };

        @Override
        public void updateValue(byte[] data) {
            if (data.length < 2) {
                LOG.warn("sensor config data too small: {}", Arrays.toString(data));
                return;
            }
            data[1] = (byte) ((data[1] & ~0x18) | MODE_BITS[getMode()]);
        }

        @Override
        public boolean readValue(byte[] data, SharedPreferences.Editor editor) {
            if (data.length < 2) {
                LOG.warn("sensor config data too small: {}", Arrays.toString(data));
                return false;
            }
            final int bits = data[1] & 0x18;
            final int mode = bits == 0x08 ? 1 : (bits == 0x18 ? 2 : 0);
            final String value = Integer.toString(mode);
            if (!value.equals(getDevicePrefs().getString(name, "0"))) {
                editor.putString(name, value);
            }
            return false;
        }

        private int getMode() {
            try {
                return Math.max(0, Math.min(Integer.parseInt(getDevicePrefs().getString(name, "0")), 2));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    /** Pressure graph: byte[2] bit 0x04, set = pressure-change graph */
    public class PressureGraphPreference extends InvertedBoolDevicePreference {
        { name = PREF_CASIO_PRESSURE_GRAPH; }
        { feature = FEATURE_FEAT_2F; }
        { index = 2; mask = 0x04; }
    }

    // ── Mode & display customization (0x38) ───────────────────────────────────

    private static final String DEFAULT_MODE_CAROUSEL = "1,2,3,4,5,6,7,8";
    private static final String DEFAULT_DISPLAY_SCREENS = "1,3,5,7";

    /**
     * Mode carousel: bytes[1:9], enabled mode ids in carousel order
     * 1=BAROMETER 2=TEMPERATURE 3=RECALL 4=SUNRISE 5=STOPWATCH
     * 6=TIMER 7=ALARM 8=WORLD TIME
     */
    public class ModeCarouselPreference extends DevicePreference {
        { name = PREF_CASIO_MODE_CAROUSEL; }
        { feature = FEATURE_MODE_DISPLAY_CUSTOMIZATION; }

        @Override
        public void updateValue(byte[] data) {
            if (data.length < 17) {
                LOG.warn("mode display data too small: {}", Arrays.toString(data));
                return;
            }
            writeIds(data, 1, getDevicePrefs().getString(name, DEFAULT_MODE_CAROUSEL));
        }

        @Override
        public boolean readValue(byte[] data, SharedPreferences.Editor editor) {
            if (data.length < 17) {
                LOG.warn("mode display data too small: {}", Arrays.toString(data));
                return false;
            }
            final String value = readIds(data, 1);
            if (!value.equals(getDevicePrefs().getString(name, DEFAULT_MODE_CAROUSEL))) {
                editor.putString(name, value);
            }
            return false;
        }
    }

    /**
     * Timekeeping display screens: bytes[9:17], enabled screen ids in order
     */
    public class DisplayScreensPreference extends DevicePreference {
        { name = PREF_CASIO_DISPLAY_SCREENS; }
        { feature = FEATURE_MODE_DISPLAY_CUSTOMIZATION; }

        @Override
        public void updateValue(byte[] data) {
            if (data.length < 17) {
                LOG.warn("mode display data too small: {}", Arrays.toString(data));
                return;
            }
            writeIds(data, 9, getDevicePrefs().getString(name, DEFAULT_DISPLAY_SCREENS));
        }

        @Override
        public boolean readValue(byte[] data, SharedPreferences.Editor editor) {
            if (data.length < 17) {
                LOG.warn("mode display data too small: {}", Arrays.toString(data));
                return false;
            }
            final String value = readIds(data, 9);
            if (!value.equals(getDevicePrefs().getString(name, DEFAULT_DISPLAY_SCREENS))) {
                editor.putString(name, value);
            }
            return false;
        }
    }

    /** Writes up to 8 ids from a comma-separated string at offset, ff-padding the rest */
    private static void writeIds(final byte[] data, final int offset, final String csv) {
        for (int i = 0; i < 8; i++) {
            data[offset + i] = (byte) 0xff;
        }
        if (csv == null) {
            return;
        }
        int i = 0;
        for (final String part : csv.split(",")) {
            if (i >= 8) {
                break;
            }
            try {
                final int id = Integer.parseInt(part.trim());
                if (id >= 1 && id <= 8) {
                    data[offset + i++] = (byte) id;
                }
            } catch (NumberFormatException e) {
                LOG.warn("Invalid id '{}' in '{}'", part, csv);
            }
        }
    }

    /** Reads the non-ff ids at offset as a comma-separated string, in order */
    private static String readIds(final byte[] data, final int offset) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            final int id = data[offset + i] & 0xff;
            if (id >= 1 && id <= 8) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(id);
            }
        }
        return sb.toString();
    }

    // ── Characteristic change handler ─────────────────────────────────────────────────────────

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic,
                                           byte[] data) {
        UUID uuid = characteristic.getUuid();

        if (data == null || data.length == 0) return true;

        if (!uuid.equals(CasioConstants.CASIO_ALL_FEATURES_CHARACTERISTIC_UUID)) {
            return super.onCharacteristicChanged(gatt, characteristic, data);
        }

        byte feat = data[0];

        if(feat == Casio2C2DSupport.FEATURE_APP_INFORMATION) {
            LOG.debug("Init[APP_INFO] → requesting BLE_FEAT");
            checkAppInformation(data);
            req(Casio2C2DSupport.FEATURE_BLE_FEATURES);
            return true;
        } else if (feat == Casio2C2DSupport.FEATURE_BLE_FEATURES) {
            LOG.debug("Init[BLE_FEAT] → writing WATCH_NAME");
            // Identity confirm: write device name to ALL_FEAT
            byte[] nameBytes = "CASIO GG-B100\0\0\0\0\0\0".getBytes(StandardCharsets.US_ASCII);
            byte[] namePacket = new byte[1 + nameBytes.length];
            namePacket[0] = Casio2C2DSupport.FEATURE_WATCH_NAME;
            System.arraycopy(nameBytes, 0, namePacket, 1, nameBytes.length);
            write(namePacket);

            byte connectionReason = data[8];
            LOG.debug("Connection reason is: " + Byte.toString(connectionReason));

            if (connectionReason == 0x02) {
                LOG.debug("Watch requests phone finder");
                setInitialized();
                return true;
            } else if (connectionReason == 0x07) {
                LOG.debug("Watch requests a location indicator");
                setInitialized();
                req(Casio2C2DSupport.FEATURE_LOCATION_INDICATOR);
                return true;
            }

            // Long sync flows for pairing (0x00), connect (0x01), scheduled sync (0x03),
            // manual sync (0x04), mission log (0x08))
            try {
                new InitOperation(this, connectionReason).perform();
            } catch (IOException e) {
                GB.toast(getContext(), "Initializing Casio GG-B100 failed",
                        Toast.LENGTH_SHORT, GB.ERROR, e);
            }

            return true;
        } else if (feat == Casio2C2DSupport.FEATURE_ALERT_LEVEL) {
            // Phone finder: 0x02 = start, other = stop
            LOG.debug("Phone finder");
            onReverseFindDevice(data.length > 1 && data[1] == 0x02);
            return true;
        } else if (feat == Casio2C2DSupport.FEATURE_LOCATION_INDICATOR) {
            LOG.debug("Location Indicator Polling: " + GB.hexdump(data));
            onLocationIndicator(data[1] == 0x02, data);
            return true;
        }

        LOG.info("Unhandled characteristic change: {} feat=0x{}", uuid,
                String.format("%02x", data[0]));
        return super.onCharacteristicChanged(gatt, characteristic, data);
    }

    @Override
    public boolean onCharacteristicRead(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic,
                                        byte[] value, int status) {
        return super.onCharacteristicRead(gatt, characteristic, value, status);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (!isConnected()) return;
        if (key == null) return;

        switch (key) {
            case ActivityUser.PREF_USER_HEIGHT_CM:
            case ActivityUser.PREF_USER_WEIGHT_KG:
            case ActivityUser.PREF_USER_STEPS_GOAL:
                LOG.debug("user profile changed ({}), pushing to watch", key);
                onSendConfiguration(PREF_CASIO_USER_PROFILE);
                break;
        }
    }
}
