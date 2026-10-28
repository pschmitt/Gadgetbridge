package nodomain.freeyourgadget.gadgetbridge.service.devices.cycling_sensor.support;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.devices.CyclingSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.CyclingSample;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfoProfile;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class CyclingSensorSupport extends CyclingSensorBaseSupport {
    static class CyclingSpeedCadenceMeasurement {
        private static final int FLAG_REVOLUTION_DATA_PRESENT = 1 << 0;
        private static final int FLAG_CADENCE_DATA_PRESENT    = 1 << 1;
        public boolean revolutionDataPresent = false;
        public boolean cadenceDataPresent = false;
        public int revolutionCount;
        public int lastRevolutionTimeTicks;
        public int crankRevolutionCount;
        public int lastCrankRevolutionTimeTicks;

        public static CyclingSpeedCadenceMeasurement fromPayload(byte[] payload) throws RuntimeException {
            if (payload == null || payload.length < 1) {
                throw new IllegalArgumentException("empty payload");
            }

            ByteBuffer buffer = ByteBuffer
                    .wrap(payload)
                    .order(ByteOrder.LITTLE_ENDIAN);

            int flags = buffer.get() & 0xFF;

            // A sensor that measures both wheel and crank sets both bits, so test each bit on its own
            boolean revolutionDataPresent = (flags & FLAG_REVOLUTION_DATA_PRESENT) != 0;
            boolean cadenceDataPresent = (flags & FLAG_CADENCE_DATA_PRESENT) != 0;

            // wheel: uint32 revolutions + uint16 event time; crank: uint16 revolutions + uint16 event time
            int expectedLength = 1 + (revolutionDataPresent ? 6 : 0) + (cadenceDataPresent ? 4 : 0);
            if (payload.length < expectedLength) {
                throw new IllegalArgumentException("payload length " + payload.length
                        + " too short for flags " + flags + ", expected " + expectedLength);
            }

            CyclingSpeedCadenceMeasurement result = new CyclingSpeedCadenceMeasurement();

            if(revolutionDataPresent){
                result.revolutionDataPresent = true;
                result.revolutionCount = buffer.getInt() & 0xFFFFFFFF; // remove sign
                result.lastRevolutionTimeTicks = buffer.getShort() & 0xFFFF;
            }

            if(cadenceDataPresent){
                result.cadenceDataPresent = true;
                result.crankRevolutionCount = buffer.getShort() & 0xFFFF;
                result.lastCrankRevolutionTimeTicks = buffer.getShort() & 0xFFFF;
            }

            return result;
        }

        /**
         * Speed in m/s since the previous wheel measurement, or null if no wheel event time passed.
         * Wheel event times count 1/1024 s in 16 bits, so they roll over every 64 s.
         */
        Float speedSince(CyclingSpeedCadenceMeasurement previous, float wheelCircumferenceMeters) {
            int ticksPassed = (lastRevolutionTimeTicks - previous.lastRevolutionTimeTicks) & 0xFFFF;
            // every second is subdivided in 1024 ticks
            int millisDelta = (int)(ticksPassed * (1000f / 1024f));

            if (millisDelta <= 0) {
                return null;
            }

            int revolutionsDelta = revolutionCount - previous.revolutionCount;
            float revolutionsPerSecond = revolutionsDelta * (1000f / millisDelta);
            return revolutionsPerSecond * wheelCircumferenceMeters;
        }

        /**
         * Cadence in rpm since the previous crank measurement, or null if no crank event time passed.
         * Crank revolutions and event times (1/1024 s) are 16-bit counters that roll over.
         */
        Integer cadenceSince(CyclingSpeedCadenceMeasurement previous) {
            int ticksPassed = (lastCrankRevolutionTimeTicks - previous.lastCrankRevolutionTimeTicks) & 0xFFFF;
            if (ticksPassed == 0) {
                return null;
            }

            int revolutionsDelta = (crankRevolutionCount - previous.crankRevolutionCount) & 0xFFFF;
            return Math.round(revolutionsDelta * 60f * 1024f / ticksPassed);
        }

        @NonNull
        @Override
        public String toString() {
            return String.format("Measurement revolutions: %d, time ticks %d", revolutionCount, lastRevolutionTimeTicks);
        }
    }

    /**
     * Turns successive crank measurements into a cadence. Reports 0 once no new crank event
     * arrived for {@link #IDLE_MILLIS}, and null while there is nothing new to report, so a
     * slow cadence does not flicker to 0 between two crank events.
     */
    static class CrankCadenceTracker {
        // Without a new crank event for this long, the rider has stopped pedaling
        static final long IDLE_MILLIS = 3_000;
        // Event times roll over every 64 s, so an older measurement cannot be compared
        static final long MAX_GAP_MILLIS = 30_000;

        private CyclingSpeedCadenceMeasurement last = null;
        private long lastMillis = 0;
        private long lastEventMillis = 0;

        Integer update(CyclingSpeedCadenceMeasurement current, long nowMillis) {
            boolean comparable = last != null && nowMillis - lastMillis <= MAX_GAP_MILLIS;
            Integer cadence = comparable ? current.cadenceSince(last) : null;
            last = current;
            lastMillis = nowMillis;

            if (cadence != null || !comparable) {
                lastEventMillis = nowMillis;
            } else if (nowMillis - lastEventMillis > IDLE_MILLIS) {
                cadence = 0;
            }
            return cadence;
        }
    }

    public final static UUID UUID_CYCLING_SENSOR_SERVICE =
            UUID.fromString("00001816-0000-1000-8000-00805f9b34fb");
    public final static UUID UUID_CYCLING_SENSOR_CSC_MEASUREMENT =
            UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb");

    private static final Logger logger = LoggerFactory.getLogger(CyclingSensorSupport.class);

    private long persistenceInterval;
    private long nextPersistenceTimestamp = 0;

    private float wheelCircumferenceMeters;

    private CyclingSpeedCadenceMeasurement lastWheelMeasurement = null;
    private long lastWheelMeasurementTime = 0;
    private final CrankCadenceTracker crankCadenceTracker = new CrankCadenceTracker();

    private BluetoothGattCharacteristic batteryCharacteristic = null;

    public CyclingSensorSupport() {
        super(logger);

        addSupportedService(UUID_CYCLING_SENSOR_SERVICE);
        addSupportedService(BatteryInfoProfile.SERVICE_UUID);
    }

    @Override
    public void onSendConfiguration(String config) {
        switch (config){
            case DeviceSettingsPreferenceConst.PREF_CYCLING_SENSOR_PERSISTENCE_INTERVAL:
            case DeviceSettingsPreferenceConst.PREF_CYCLING_SENSOR_WHEEL_DIAMETER:
                loadConfiguration();
                break;
        }
    }

    private void loadConfiguration(){
        Prefs deviceSpecificPrefs = new Prefs(
                GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress())
        );
        persistenceInterval = deviceSpecificPrefs.getInt(DeviceSettingsPreferenceConst.PREF_CYCLING_SENSOR_PERSISTENCE_INTERVAL, 60) * 1000;
        nextPersistenceTimestamp = 0;
        
        float wheelDiameter = deviceSpecificPrefs.getFloat(DeviceSettingsPreferenceConst.PREF_CYCLING_SENSOR_WHEEL_DIAMETER, 29);
        wheelCircumferenceMeters = (float)(wheelDiameter * 2.54 * Math.PI) / 100;
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        builder.setDeviceState(GBDevice.State.INITIALIZING);

        builder.notify(UUID_CYCLING_SENSOR_CSC_MEASUREMENT, true);

        builder.setDeviceState(GBDevice.State.INITIALIZED);
        batteryCharacteristic = getCharacteristic(BatteryInfoProfile.UUID_CHARACTERISTIC_BATTERY_LEVEL);

        if(batteryCharacteristic != null){
            builder.read(batteryCharacteristic);
        }

        loadConfiguration();

        gbDevice.setFirmwareVersion("1.0.0");

        return builder;
    }

    private void handleMeasurementCharacteristic(byte[] value){
        CyclingSpeedCadenceMeasurement measurement;

        try {
            measurement = CyclingSpeedCadenceMeasurement.fromPayload(value);
        } catch (RuntimeException e) {
            logger.warn("Ignoring invalid CSC measurement {}: {}", GB.hexdump(value), e.getMessage());
            return;
        }

        if(!measurement.revolutionDataPresent && !measurement.cadenceDataPresent){
            return;
        }

        handleCyclingMeasurement(measurement);
    }

    private void handleCyclingMeasurement(CyclingSpeedCadenceMeasurement currentMeasurement) {
        logger.debug("Measurement " + currentMeasurement);

        long now = System.currentTimeMillis();

        CyclingSample sample = new CyclingSample();

        // Revolution count, speed and distance stay null when the packet holds no wheel data,
        // e.g. from a cadence-only sensor
        if (currentMeasurement.revolutionDataPresent) {
            Float speed = null;

            long lastMeasurementDelta = (now - lastWheelMeasurementTime);

            if(lastMeasurementDelta <= 30_000){
                speed = currentMeasurement.speedSince(lastWheelMeasurement, wheelCircumferenceMeters);
            }

            lastWheelMeasurement = currentMeasurement;
            lastWheelMeasurementTime = now;

            sample.setRevolutionCount(currentMeasurement.revolutionCount);
            sample.setSpeed(speed);
            sample.setDistance(currentMeasurement.revolutionCount * wheelCircumferenceMeters);
        }

        if (currentMeasurement.cadenceDataPresent) {
            sample.setCadence(crankCadenceTracker.update(currentMeasurement, now));
        }

        sample.setTimestamp(now);

        Intent liveIntent = new Intent(DeviceService.ACTION_REALTIME_SAMPLES);
        liveIntent.putExtra(GBDevice.EXTRA_DEVICE, getDevice());
        liveIntent.putExtra(DeviceService.EXTRA_REALTIME_SAMPLE, sample);
        liveIntent.putExtra("EXTRA_DEVICE_ADDRESS", getDevice().getAddress());
        LocalBroadcastManager.getInstance(getContext())
                .sendBroadcast(liveIntent);


        if(now < nextPersistenceTimestamp){
            // too early
            return;
        }

        nextPersistenceTimestamp = now + persistenceInterval;

        try(DBHandler handler = GBApplication.acquireDB()) {
            DaoSession session = handler.getDaoSession();

            CyclingSampleProvider sampleProvider =
                    new CyclingSampleProvider(getDevice(), session);

            Device databaseDevice = DBHelper.getDevice(getDevice(), session);
            User databaseUser = DBHelper.getUser(session);
            sample.setDevice(databaseDevice);
            sample.setUser(databaseUser);

            sampleProvider.addSample(sample);
        } catch (Exception e) {
            // throw new RuntimeException(e);
            logger.error("failed adding DB cycling sample");
        }
    }

    @Override
    public boolean onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
        if(characteristic.equals(batteryCharacteristic) && value != null && value.length == 1){
            GBDeviceEventBatteryInfo info = new GBDeviceEventBatteryInfo();
            info.level = value[0];
            handleGBDeviceEvent(info);
        }

        return true;
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
        if(characteristic.getUuid().equals(UUID_CYCLING_SENSOR_CSC_MEASUREMENT)){
            handleMeasurementCharacteristic(value);
            return true;
        }
        return false;
    }
}
