/*  Copyright (C) 2015-2025 akasaka / Genjitsu Labs, Alicia Hormann, Andreas
    Shimokawa, Arjan Schrijver, Carsten Pfeiffer, Daniel Dakhno, Daniele Gobbetti,
    Davis Mosenkovs, Dmitry Markin, José Rebelo, Matthieu Baerts, Nephiel,
    Petr Vaněk, Taavi Eomäe, Johannes Krude, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.devices;

import static nodomain.freeyourgadget.gadgetbridge.GBApplication.getPrefs;

import android.app.Activity;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.GBException;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.DefaultChartsProvider;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.DeviceChartsProvider;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettings;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.capabilities.HeartRateCapability;
import nodomain.freeyourgadget.gadgetbridge.capabilities.loyaltycards.BarcodeFormat;
import nodomain.freeyourgadget.gadgetbridge.capabilities.password.PasswordCapabilityImpl;
import nodomain.freeyourgadget.gadgetbridge.capabilities.widgets.WidgetManager;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.cards.DefaultDeviceCardItems;
import nodomain.freeyourgadget.gadgetbridge.devices.cards.DeviceCardItem;
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControl;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.CyclingSample;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.DeviceAttributes;
import nodomain.freeyourgadget.gadgetbridge.entities.DeviceDataTables;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadAcuteSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadChronicSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.install.InstallHandlerV2;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate;
import nodomain.freeyourgadget.gadgetbridge.model.AbstractNotificationPattern;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryParser;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrackProvider;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig;
import nodomain.freeyourgadget.gadgetbridge.model.BloodPressureSample;
import nodomain.freeyourgadget.gadgetbridge.model.BodyEnergySample;
import nodomain.freeyourgadget.gadgetbridge.model.SolarChargeSample;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.model.GpxActivityTrackProvider;
import nodomain.freeyourgadget.gadgetbridge.model.HeartRateSample;
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample;
import nodomain.freeyourgadget.gadgetbridge.model.HrvValueSample;
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample;
import nodomain.freeyourgadget.gadgetbridge.model.PaiSample;
import nodomain.freeyourgadget.gadgetbridge.model.RespiratoryRateSample;
import nodomain.freeyourgadget.gadgetbridge.model.RestingMetabolicRateSample;
import nodomain.freeyourgadget.gadgetbridge.model.SleepScoreSample;
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample;
import nodomain.freeyourgadget.gadgetbridge.model.StressSample;
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureSample;
import nodomain.freeyourgadget.gadgetbridge.model.Vo2MaxSample;
import nodomain.freeyourgadget.gadgetbridge.model.WeightSample;
import nodomain.freeyourgadget.gadgetbridge.model.WorkoutLoadSample;
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZonesSpec;
import nodomain.freeyourgadget.gadgetbridge.service.ServiceDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;
import nodomain.freeyourgadget.gadgetbridge.util.preferences.DevicePrefs;
import nodomain.freeyourgadget.gadgetbridge.widgets.DeviceWidgetsProvider;

public abstract class AbstractDeviceCoordinator implements DeviceCoordinator {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractDeviceCoordinator.class);

    protected Pattern supportedDeviceName = null;

    /**
     * This method should return a Regexp pattern that will be matched against a found device
     * to check whether this coordinator supports that device.
     * If more sophisticated logic is needed to determine device support, the supports(GBDeviceCandidate)
     * should be overridden.
     *
     * @return Pattern
     */
    @Nullable
    protected Pattern getSupportedDeviceName() {
        return null;
    }

    @Override
    public boolean supports(@NonNull GBDeviceCandidate candidate) {
        if (supportedDeviceName == null) {
            supportedDeviceName = getSupportedDeviceName();
        }
        if (supportedDeviceName == null) {
            LOG.error("{} should either override getSupportedDeviceName or supports(GBDeviceCandidate)", getClass());
            return false;
        }

        return supportedDeviceName.matcher(candidate.getName()).matches();
    }

    @Override
    public ConnectionType getConnectionType() {
        return ConnectionType.BOTH;
    }

    @Override
    public boolean isConnectable() {
        return true;
    }

    @NonNull
    @Override
    public Collection<? extends ScanFilter> createBLEScanFilters() {
        return Collections.emptyList();
    }

    protected void setBatteryConfigOnDevice(GBDevice gbDevice) {
        for (BatteryConfig batteryConfig : getBatteryConfig(gbDevice)) {
            gbDevice.setBatteryIcon(batteryConfig.getBatteryIcon(), batteryConfig.getBatteryIndex());
            gbDevice.setBatteryLabel(batteryConfig.getBatteryLabel(), batteryConfig.getBatteryIndex());
        }
    }

    @Override
    public GBDevice createDevice(GBDeviceCandidate candidate, DeviceType deviceType) {
        GBDevice gbDevice = new GBDevice(candidate.getDevice().getAddress(), candidate.getName(), null, null, deviceType);
        setBatteryConfigOnDevice(gbDevice);

        final DevicePrefs devicePreferences = GBApplication.getDevicePrefs(gbDevice);
        final SharedPreferences.Editor editor = devicePreferences.getPreferences().edit();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // #5414 - Some old Android versions misbehave
            editor.putBoolean(DeviceSettingsPreferenceConst.PREF_CONNECTION_FORCE_LEGACY_GATT, true);
        }

        applyDefaultPreferences(devicePreferences, editor);

        editor.apply();

        return gbDevice;
    }

    /**
     * Allows for default preferences to be applied on device creation. These do not apply to already-paired devices,
     * for those you should implement a preference migrator in {@link nodomain.freeyourgadget.gadgetbridge.prefs.GBPrefsMigrator}.
     */
    protected void applyDefaultPreferences(final DevicePrefs devicePreferences, final SharedPreferences.Editor editor) {
        // nothing by default
    }

    @Override
    public GBDevice createDevice(Device dbDevice, DeviceType deviceType) {
        GBDevice gbDevice =
            new GBDevice(dbDevice.getIdentifier(), dbDevice.getName(), dbDevice.getAlias(),
                dbDevice.getParentFolder(), deviceType);

        setBatteryConfigOnDevice(gbDevice);

        List<DeviceAttributes> deviceAttributesList = dbDevice.getDeviceAttributesList();
        if (!deviceAttributesList.isEmpty()) {
            gbDevice.setModel(dbDevice.getModel());
            DeviceAttributes attrs = deviceAttributesList.get(0);
            gbDevice.setFirmwareVersion(attrs.getFirmwareVersion1());
            gbDevice.setFirmwareVersion2(attrs.getFirmwareVersion2());
            gbDevice.setVolatileAddress(attrs.getVolatileIdentifier());
        }

        return gbDevice;
    }

    @Override
    public final void deleteDevice(final GBDevice gbDevice,
                                   final boolean deleteFiles,
                                   @Nullable final DeviceDeletionProgress progress) throws GBException {
        LOG.info("Will try to delete device: {}", gbDevice.getName());
        if (gbDevice.isConnected() || gbDevice.isConnecting()) {
            prepareDeviceForDeletion(gbDevice);
            GBApplication.deviceService(gbDevice).disconnect();
        }
        Prefs prefs = getPrefs();

        Set<String> lastDeviceAddresses = prefs.getStringSet(GBPrefs.LAST_DEVICE_ADDRESSES, Collections.emptySet());
        if (lastDeviceAddresses.contains(gbDevice.getAddress())) {
            LOG.debug("#1605 removing last device (one of last devices)");
            lastDeviceAddresses = new HashSet<>(lastDeviceAddresses);
            lastDeviceAddresses.remove(gbDevice.getAddress());
            prefs.getPreferences().edit().putStringSet(GBPrefs.LAST_DEVICE_ADDRESSES, lastDeviceAddresses).apply();
        }

        LOG.debug("Deleting device shared preferences");
        GBApplication.deleteDeviceSpecificSharedPrefs(gbDevice.getAddress());

        LOG.debug("Will delete devices data from database");
        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            final DaoSession session = dbHandler.getDaoSession();
            final Device device = DBHelper.findDevice(gbDevice, session);
            if (device != null) {
                final long deviceId = Objects.requireNonNull(device.getId());
                LOG.debug("Got device ID from the database = {}", deviceId);
                session.callInTx(() -> {
                    LOG.debug("Running device-specific deletion");
                    deleteDevice(gbDevice, device, session);
                    LOG.debug("Deleting device from all device-specific tables");
                    DeviceDataTables.delete(
                        session.getDatabase(),
                        deviceId,
                        progress == null ? null : (done, total) -> progress.onProgress(DeviceDeletionProgress.Stage.DATABASE, done, total)
                    );
                    LOG.debug("Deleting device from the DEVICE table");
                    session.getDeviceDao().delete(device);
                    return null;
                });
            } else {
                LOG.warn("Device to delete not found in db: {}", gbDevice);
            }
        } catch (Exception e) {
            throw new GBException("Error deleting device: " + e.getMessage(), e);
        }

        deleteDeviceFiles(gbDevice, deleteFiles, progress);
        LOG.info("Device has been deleted");
    }

    /**
     * Hook for devices that need to send a final command while still connected.
     */
    protected void prepareDeviceForDeletion(@NonNull final GBDevice gbDevice) throws GBException {
    }

    /**
     * Deletes the cache directory of the device, and the export directory if deleteFiles is true.
     */
    private void deleteDeviceFiles(final GBDevice gbDevice,
                                   final boolean deleteFiles,
                                   @Nullable final DeviceDeletionProgress progress) {
        File export = null;
        if (deleteFiles) {
            try {
                export = getWritableExportDirectory(gbDevice, false);
            } catch (final Exception e) {
                final String message = GBApplication.getContext().getString(
                    R.string.error_deleting_file_exception,
                    "(export)", e.getLocalizedMessage()
                );
                GB.toast(message, Toast.LENGTH_LONG, GB.ERROR, e);
            }
        }
        final File cacheDir = getDeviceCacheDir(gbDevice);

        final int total = (export != null ? FileUtils.countFilesRecursively(export) : 0) +
            FileUtils.countFilesRecursively(cacheDir);
        final AtomicInteger done = new AtomicInteger();
        final Runnable onFileDeleted = progress == null ? null :
            () -> progress.onProgress(DeviceDeletionProgress.Stage.FILES, done.incrementAndGet(), total);
        if (progress != null) {
            progress.onProgress(DeviceDeletionProgress.Stage.FILES, 0, total);
        }

        if (export != null) {
            LOG.debug("Deleting device files for {} in {}", gbDevice, export);
            try {
                if (!FileUtils.deleteRecursively(export, onFileDeleted)) {
                    final String message = GBApplication.getContext().getString(
                        R.string.error_deleting_file,
                        export.getPath()
                    );
                    GB.toast(message, Toast.LENGTH_LONG, GB.ERROR);
                }
            } catch (final Exception e) {
                final String message = GBApplication.getContext().getString(
                    R.string.error_deleting_file_exception,
                    export.getPath(),
                    e.getLocalizedMessage()
                );
                GB.toast(message, Toast.LENGTH_LONG, GB.ERROR, e);
            }
        }

        LOG.debug("Deleting device cache directory {}", cacheDir);
        if (!FileUtils.deleteRecursively(cacheDir, onFileDeleted)) {
            LOG.warn("Failed to delete device cache directory {}", cacheDir);
        }
    }

    /**
     * Hook for subclasses to perform device-specific deletion logic, e.g. db cleanup.
     * By default, it clears all data from all device-specific dao tables.
     *
     * @param gbDevice the GBDevice
     * @param device   the corresponding database Device
     * @param session  the session to use
     * @throws GBException if there was an error deleting device-specific resources
     */
    protected void deleteDevice(@NonNull GBDevice gbDevice, @NonNull Device device, @NonNull DaoSession session) throws GBException {
    }

    @Override
    @Nullable
    public SampleProvider<? extends ActivitySample> getSampleProvider(final GBDevice device, final DaoSession session) {
        return null;
    }

    @Override
    public TimeSampleProvider<? extends BloodPressureSample> getBloodPressureSampleProvider(final GBDevice device, final DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends StressSample> getStressSampleProvider(GBDevice device, DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends BodyEnergySample> getBodyEnergySampleProvider(final GBDevice device, final DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends SolarChargeSample> getSolarChargeSampleProvider(final GBDevice device, final DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends HrvSummarySample> getHrvSummarySampleProvider(GBDevice device, DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends HrvValueSample> getHrvValueSampleProvider(GBDevice device, DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public Vo2MaxSampleProvider<? extends Vo2MaxSample> getVo2MaxSampleProvider(GBDevice device, DaoSession session) {
        return new WorkoutVo2MaxSampleProvider(device, session);
    }

    @Override
    public int[] getStressRanges() {
        // 0-39 = relaxed
        // 40-59 = mild
        // 60-79 = moderate
        // 80-100 = high
        return new int[]{0, 40, 60, 80};
    }

    @Override
    public boolean showStressLevelInPercents() {
        return false;
    }

    @Override
    public int[] getStressChartParameters() {
        // by default stress data provided every 60 seconds
        // by default it is not interval data and we don't need to insert deltas
        // if interval and delta provided stress data will be displayed as bars with deltas between.
        return new int[]{60, 0, 0};
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends TemperatureSample> getTemperatureSampleProvider(GBDevice device, DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends Spo2Sample> getSpo2SampleProvider(GBDevice device, DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends WorkoutLoadSample> getWorkoutLoadSampleProvider(final GBDevice device, final DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends GenericTrainingLoadAcuteSample> getTrainingAcuteLoadSampleProvider(final GBDevice device, final DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends GenericTrainingLoadChronicSample> getTrainingChronicLoadSampleProvider(final GBDevice device, final DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<CyclingSample> getCyclingSampleProvider(GBDevice device, DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends HeartRateSample> getHeartRateMaxSampleProvider(GBDevice device, DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends HeartRateSample> getHeartRateRestingSampleProvider(GBDevice device, DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends HeartRateSample> getHeartRateManualSampleProvider(GBDevice device, DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends PaiSample> getPaiSampleProvider(GBDevice device, DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends RespiratoryRateSample> getRespiratoryRateSampleProvider(GBDevice device, DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends WeightSample> getWeightSampleProvider(GBDevice device, DaoSession session) {
        return null;
    }

    @Override
    @Nullable
    public HydrationSampleProvider getHydrationSampleProvider(final GBDevice device, final DaoSession session) {
        return null;
    }

    @Override
    public TimeSampleProvider<? extends RestingMetabolicRateSample> getRestingMetabolicRateProvider(final GBDevice device, final DaoSession session) {
        return new DefaultRestingMetabolicRateProvider(device, session);
    }

    @Override
    @Nullable
    public TimeSampleProvider<? extends SleepScoreSample> getSleepScoreProvider(final GBDevice device, final DaoSession session) {
        return null;
    }

    @Override
    @NonNull
    public SleepSessionProvider getSleepSessionProvider(@NonNull final GBDevice device, @NonNull final DaoSession session) {
        return new DefaultSleepSessionProvider(device, session);
    }

    @Override
    @Nullable
    public ActivitySummaryParser getActivitySummaryParser(final GBDevice device, final Context context) {
        return new ActivitySummaryParser.NoopActivitySummaryParser();
    }

    @Override
    @Nullable
    public ActivityTrackProvider getActivityTrackProvider(@NonNull final GBDevice device, @NonNull final Context context) {
        // By default, most devices write a gpx file when there's an activity track
        return new GpxActivityTrackProvider();
    }

    @Nullable
    @Override
    public File getWorkoutRawDetailsFile(@NonNull final GBDevice device, @NonNull final BaseActivitySummary summary) {
        return FileUtils.tryFixPath(summary.getRawDetailsPath());
    }

    @Nullable
    @Override
    public File getWorkoutRawGpsFile(@NonNull final GBDevice device, @NonNull final BaseActivitySummary summary) {
        return null;
    }

    public boolean isHealthWearable(BluetoothDevice device) {
        BluetoothClass bluetoothClass;
        try {
            bluetoothClass = device.getBluetoothClass();
        } catch (SecurityException se) {
            LOG.warn("missing bluetooth permission: ", se);
            return false;
        }
        if (bluetoothClass == null) {
            LOG.warn("unable to determine bluetooth device class of {}", device);
            return false;
        }
        if (bluetoothClass.getMajorDeviceClass() == BluetoothClass.Device.Major.WEARABLE
            || bluetoothClass.getMajorDeviceClass() == BluetoothClass.Device.Major.UNCATEGORIZED) {
            int deviceClasses =
                BluetoothClass.Device.HEALTH_BLOOD_PRESSURE
                    | BluetoothClass.Device.HEALTH_DATA_DISPLAY
                    | BluetoothClass.Device.HEALTH_PULSE_RATE
                    | BluetoothClass.Device.HEALTH_WEIGHING
                    | BluetoothClass.Device.HEALTH_UNCATEGORIZED
                    | BluetoothClass.Device.HEALTH_PULSE_OXIMETER
                    | BluetoothClass.Device.HEALTH_GLUCOSE;

            return (bluetoothClass.getDeviceClass() & deviceClasses) != 0;
        }
        return false;
    }

    @Override
    @Nullable
    public File getAppCacheDir() throws IOException {
        return null;
    }

    @Override
    public File getWritableExportDirectory(final GBDevice device, boolean createIfRequired) throws IOException {
        File dir = new File(FileUtils.getExternalFilesDir(), device.getAddress());
        if (!dir.isDirectory()) {
            if (createIfRequired && !dir.mkdir()) {
                throw new IOException("Cannot create device specific directory for " + device.getName());
            }
        }
        return dir;
    }

    @Override
    public File getDeviceCacheDir(@NonNull final GBDevice device) {
        final File devicesDir = new File(GBApplication.getContext().getCacheDir(), "devices");
        return new File(devicesDir, FileUtils.makeValidFileName(device.getAddress()));
    }

    @Override
    @Nullable
    public String getAppCacheSortFilename() {
        return null;
    }

    @Override
    @Nullable
    public String getAppFileExtension() {
        return null;
    }

    @Override
    public boolean supportsAppListFetching(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsFlashing(@NonNull GBDevice device) {
        return false;
    }

    @Nullable
    @Override
    public InstallHandler findInstallHandler(final Uri uri, final Bundle options, final Context context) {
        return null;
    }

    @Nullable
    @Override
    public InstallHandlerV2 findInstallHandlerV2(@NonNull final List<Uri> files, @NonNull final Context context) {
        return null;
    }

    @Override
    public boolean supportsScreenshots(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public int getAlarmSlotCount(final GBDevice device) {
        return 0;
    }

    @Override
    public boolean supportsSmartWakeup(@NonNull GBDevice device, int alarmPosition) {
        return false;
    }

    @Override
    public boolean supportsSmartWakeupInterval(@NonNull GBDevice device, int alarmPosition) {
        return false;
    }

    @Override
    public int getSmartWakeupMaxInterval(@NonNull GBDevice device) {
        return 255;
    }

    @Override
    public String getSmartWakeupDescription(@NonNull GBDevice device) {
        return null;
    }

    @Override
    public boolean forcedSmartWakeup(GBDevice device, int alarmPosition) {
        return false;
    }

    @Override
    public boolean supportsAppReordering(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsAppsManagement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsCachedAppManagement(@NonNull final GBDevice device) {
        try {
            return supportsAppsManagement(device) && getAppCacheDir() != null;
        } catch (final Exception e) {
            // we failed, but still tried, so it's supported..
            LOG.error("Failed to get app cache dir", e);
            return true;
        }
    }

    @Override
    public boolean supportsInstalledAppManagement(@NonNull final GBDevice device) {
        return supportsAppsManagement(device);
    }

    @Override
    public boolean supportsWatchfaceManagement(@NonNull final GBDevice device) {
        return supportsAppsManagement(device);
    }

    @Nullable
    @Override
    public Class<? extends Activity> getAppsManagementActivity(final GBDevice device) {
        return null;
    }

    @Nullable
    @Override
    public Class<? extends Activity> getWatchfaceDesignerActivity(final GBDevice device) {
        return null;
    }

    @Nullable
    @Override
    public Class<? extends Activity> getAppStoreActivity(final GBDevice device) {
        return null;
    }

    @Nullable
    @Override
    public Class<? extends Activity> getAppConfigurationActivity(final GBDevice device) {
        return null;
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_ASK;
    }

    @Override
    public boolean requiresAuthKey() {
        return getBondingStyle() == BONDING_STYLE_REQUIRE_KEY;
    }

    @Override
    public int getBlePhyMask() {
        // this is specified as a recommendation ("prefer to use ...") by Google
        // however some roms - e.g. MIUI - treat it as law ("must only use ...") (#6230)
        // -> by default prefer more reliable physical layers (PHYs) over high throughput 2M

        return BluetoothDevice.PHY_LE_1M_MASK | BluetoothDevice.PHY_LE_CODED_MASK;
    }

    @Override
    public boolean suggestUnbindBeforePair() {
        return true;
    }

    @Override
    public boolean isExperimental() {
        return false;
    }

    @Override
    public boolean supportsCalendarEvents(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsDebugLogs(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsDataFetching(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsActivityTracking(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsRecordedActivities(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsBloodPressureMeasurement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsStressMeasurement(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsBodyEnergy(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsHrvMeasurement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsVO2Max(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsVO2MultiSport(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsActiveCalories(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsActivityDistance(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsTrainingLoad(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsTrainingLoadChronic(@NonNull GBDevice device) {
        return supportsTrainingLoad(device);
    }

    @Override
    public boolean supportsRacePrediction(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsTrainingReadiness(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsSolarCharging(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsCharts(@NonNull GBDevice device) {
        // All the default charts (see DefaultChartsProvider)
        return supportsActivityTracking(device) ||
            supportsSleepMeasurement(device) ||
            supportsHrvMeasurement(device) ||
            supportsBodyEnergy(device) ||
            supportsVO2Max(device) ||
            supportsTrainingLoad(device) ||
            supportsHeartRateMeasurement(device) ||
            supportsStepCounter(device) ||
            supportsStressMeasurement(device) ||
            supportsPai(device) ||
            supportsSpeedzones(device) ||
            supportsRealtimeData(device) ||
            supportsSpo2(device) ||
            supportsTemperatureMeasurement(device) ||
            supportsWeightMeasurement(device) ||
            supportsHydration(device) ||
            supportsActiveCalories(device) ||
            supportsCyclingData(device) ||
            supportsRespiratoryRate(device) ||
            supportsBloodPressureMeasurement(device) ||
            supportsRacePrediction(device) ||
            supportsTrainingReadiness(device) ||
            supportsSolarCharging(device);
    }

    @Override
    public boolean supportsSleepMeasurement(@NonNull GBDevice device) {
        return supportsActivityTracking(device);
    }

    @Override
    public boolean supportsStepCounter(@NonNull GBDevice device) {
        return supportsActivityTracking(device);
    }

    @Override
    public boolean supportsSpeedzones(@NonNull GBDevice device) {
        return supportsActivityTracking(device);
    }

    @Override
    public boolean supportsGlucoseMeasurement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public DeviceChartsProvider getChartsProvider() {
        return DefaultChartsProvider.INSTANCE;
    }

    @Override
    public DeviceWidgetsProvider getWidgetsProvider() {
        return DeviceWidgetsProvider.DEFAULT;
    }

    @Override
    public boolean supportsTemperatureMeasurement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsContinuousTemperature(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsSpo2(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsHeartRateStats(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsPai(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public int getPaiName() {
        return R.string.menuitem_pai;
    }

    @Override
    public boolean supportsPaiTime(@NonNull GBDevice device) {
        return supportsPai(device);
    }

    @Override
    public boolean supportsPaiLow(@NonNull GBDevice device) {
        return supportsPai(device);
    }

    @Override
    public int getPaiTarget() {
        return 100;
    }

    @Override
    public boolean supportsRespiratoryRate(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsDayRespiratoryRate(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsSleepRespiratoryRate(@NonNull GBDevice device) {
        return supportsRespiratoryRate(device);
    }

    @Override
    public boolean supportsWeightMeasurement(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsHydration(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsAlarmSnoozing(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsAlarmTitle(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public int getAlarmTitleLimit(GBDevice device) {
        return -1;
    }

    @Override
    public boolean supportsAlarmDescription(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsAlarmSounds(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsAlarmBacklight(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsAlarmTitlePresets(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public List<Alarm.ALARM_LABEL> getAlarmTitlePresets(@NonNull GBDevice device) {
        return Collections.emptyList();
    }

    @Override
    public boolean supportsMusicInfo(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsLedColor(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public int getMaximumReminderMessageLength() {
        return 0;
    }

    @Override
    public int getReminderSlotCount(final GBDevice device) {
        return 0;
    }

    @Override
    public boolean getRemindersHaveTime() {
        return true;
    }

    @Override
    public boolean getReserveReminderSlotsForCalendar() {
        return false;
    }

    @Override
    public int getCannedRepliesSlotCount(final GBDevice device) {
        return 0;
    }

    @Override
    public int getWorldClocksSlotCount() {
        return 0;
    }

    @Override
    public int getWorldClocksLabelLength() {
        return 10;
    }

    @Override
    public boolean supportsDisabledWorldClocks(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsAudioRecordings(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public int getContactsSlotCount(final GBDevice device) {
        return 0;
    }

    @Override
    public boolean supportsRgbLedColor(@NonNull GBDevice device) {
        return false;
    }

    @NonNull
    @Override
    public int[] getColorPresets() {
        return new int[0];
    }

    @Override
    public boolean supportsHeartRateMeasurement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsHeartRateRestingMeasurement(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsManualHeartRateMeasurement(@NonNull final GBDevice device) {
        return supportsHeartRateMeasurement(device);
    }

    @Override
    public boolean supportsLiveOnlyHeartRateDisplay(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsRealtimeData(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsCyclingData(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsRemSleep(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsAwakeSleep(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsSleepScore(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsWeather(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsFindDevice(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsUnicodeEmojis(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsSleepAsAndroid(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public Set<SleepAsAndroidFeature> getSleepAsAndroidFeatures(@NonNull final GBDevice device) {
        return Collections.emptySet();
    }

    @Override
    public int[] getSupportedDeviceSpecificConnectionSettings() {
        int[] settings = new int[0];
        ConnectionType connectionType = getConnectionType();

        if (connectionType.usesBluetoothClassic() || connectionType.usesBluetoothLE()) {
            settings = ArrayUtils.insert(0, settings, R.xml.devicesettings_reconnect_periodic);
            settings = ArrayUtils.insert(0, settings, R.xml.devicesettings_device_connect_back);
            settings = ArrayUtils.insert(0, settings, R.xml.devicesettings_device_connect_by_trigger);
            if (supportsConnectionPriority()) {
                settings = ArrayUtils.add(settings, R.xml.devicesettings_connection_priority_low_power);
            }
        }

        return settings;
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(GBDevice device) {
        return new int[0];
    }

    @Override
    public DeviceSpecificSettings getDeviceSpecificSettings(GBDevice device) {
        final int[] settings = getSupportedDeviceSpecificSettings(device);
        if (settings == null || settings.length == 0) {
            return null;
        }

        return new DeviceSpecificSettings(settings);
    }

    @Override
    public int[] getSupportedDeviceSpecificAuthenticationSettings() {
        return new int[0];
    }

    @Override
    public int[] getSupportedDeviceSpecificExperimentalSettings(final GBDevice device) {
        return new int[0];
    }

    @Override
    public int[] getSupportedDebugSettings(final GBDevice device) {
        return new int[]{
            R.xml.devicesettings_stress_test,
            R.xml.devicesettings_disable_busy_checking,
        };
    }

    public boolean experimentalSettingEnabled(final GBDevice device, final String key) {
        return GBApplication.getPrefs().experimentalSettings() && GBApplication.getDevicePrefs(device).getBoolean(key, false);
    }

    @Nullable
    @Override
    public DeviceSpecificSettingsCustomizer getDeviceSpecificSettingsCustomizer(GBDevice device) {
        return null;
    }

    @Nullable
    @Override
    public String[] getSupportedLanguageSettings(GBDevice device) {
        return null;
    }

    @Nullable
    @Override
    public Class<? extends Activity> getPairingActivity() {
        return null;
    }

    @Nullable
    @Override
    public CharSequence getPairingHint(final Context context, final GBDeviceCandidate candidate) {
        return null;
    }

    @Nullable
    @Override
    public Class<? extends Activity> getCalibrationActivity() {
        return null;
    }

    @Override
    public int getBatteryCount(final GBDevice device) {
        return 1;
    } //multiple battery support, default is 1, maximum is 3, 0 will disable the battery in UI

    @Override
    public BatteryConfig[] getBatteryConfig(final GBDevice device) {
        final BatteryConfig[] batteryConfigs = new BatteryConfig[getBatteryCount(device)];
        for (int i = 0; i < getBatteryCount(device); i++) {
            batteryConfigs[i] = new BatteryConfig(i);
        }
        return batteryConfigs;
    }

    @Override
    public boolean supportsOSBatteryLevel(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean addBatteryPollingSettings() {
        return false;
    }

    @Override
    public boolean supportsPowerOff(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public PasswordCapabilityImpl.Mode getPasswordCapability() {
        return PasswordCapabilityImpl.Mode.NONE;
    }

    @Override
    public List<HeartRateCapability.MeasurementInterval> getHeartRateMeasurementIntervals() {
        return Arrays.asList(
            HeartRateCapability.MeasurementInterval.OFF,
            HeartRateCapability.MeasurementInterval.MINUTES_1,
            HeartRateCapability.MeasurementInterval.MINUTES_5,
            HeartRateCapability.MeasurementInterval.MINUTES_10,
            HeartRateCapability.MeasurementInterval.MINUTES_30,
            HeartRateCapability.MeasurementInterval.HOUR_1
        );
    }

    @Override
    public boolean supportsWidgets(@NonNull final GBDevice device) {
        return false;
    }

    @Nullable
    @Override
    public WidgetManager getWidgetManager(final GBDevice device) {
        return null;
    }

    @Override
    public boolean supportsNavigation(@NonNull final GBDevice device) {
        return supportsAppNotifications(device) &&
            GBApplication.getPrefs().getBoolean(GBPrefs.NAVIGATION_ALLOW_UNSUPPORTED_DEVICES, false);
    }

    @Override
    public boolean supportsAppNotifications(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsPhoneCalls(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsCallControl(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsInternetAccess(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsSmsReply(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsPhoneSilentMode(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsGpsSharing(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public int getOrderPriority() {
        return 0;
    }

    @Override
    public EnumSet<ServiceDeviceSupport.Flags> getInitialFlags() {
        return EnumSet.of(ServiceDeviceSupport.Flags.BUSY_CHECKING);
    }

    @Override
    @DrawableRes
    public int getDefaultIconResource() {
        return R.drawable.ic_device_default;
    }

    @Override
    public boolean supportsNotificationVibrationPatterns(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsNotificationVibrationRepetitionPatterns(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsNotificationLedPatterns(@NonNull GBDevice device) {
        return false;
    }

    @Override
    public boolean supportsCustomVibrationPatterns(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public int getVibrationPresetNameRes(final int presetId) {
        return 0;
    }

    @Override
    public boolean isProtectedVibrationPatternId(final int id) {
        return false;
    }

    @Override
    public AbstractNotificationPattern[] getNotificationVibrationPatterns() {
        return new AbstractNotificationPattern[0];
    }

    @Override
    public AbstractNotificationPattern[] getNotificationVibrationRepetitionPatterns() {
        return new AbstractNotificationPattern[0];
    }

    @Override
    public AbstractNotificationPattern[] getNotificationLedPatterns() {
        return new AbstractNotificationPattern[0];
    }

    @Override
    public int getLiveActivityFragmentPulseInterval() {
        return 1000;
    }

    @Override
    public boolean validateAuthKey(@NonNull final String authKey) {
        return !(authKey.getBytes().length < 34 || !authKey.startsWith("0x"));
    }

    @Override
    @Nullable
    public String getAuthHelp() {
        return "https://gadgetbridge.org/basics/pairing/#authentication-key";
    }

    @Override
    @Nullable
    public String getSecondaryAuthKeyPref() {
        return null;
    }

    @Override
    @StringRes
    public int getSecondaryAuthKeyHint() {
        return 0;
    }

    @Override
    public List<DeviceCardAction> getCustomActions() {
        return Collections.emptyList();
    }

    @Override
    public List<DeviceCardItem> getCardItems(@NonNull final GBDevice device) {
        return DefaultDeviceCardItems.build(this, device);
    }

    @Override
    public List<QuickControl> getQuickControls(@NonNull final GBDevice device) {
        return Collections.emptyList();
    }

    @Override
    public HeartRateZonesSpec getHeartRateZonesSpec(@NonNull GBDevice device) {
        return null;
    }

    @Override
    public Set<BarcodeFormat> getSupportedBarcodeFormats(@NonNull final GBDevice device) {
        return Collections.emptySet();
    }

    @Override
    public boolean supportsLoyaltyCardsSyncRequest(@NonNull final GBDevice device) {
        return false;
    }

    @Override
    public int getReconnectionDelay() {
        // 2 seconds.
        return 2000;
    }

    @Override
    public boolean supportsConnectionPriority() {
        return true;
    }

    @Override
    public GenericMetricSampleProvider getMetricsSampleProvider(@NonNull final GBDevice device, @NonNull final DaoSession session) {
        return new GenericMetricSampleProvider(device, session);
    }

    @Override
    @NonNull
    public Set<MetricSample.Metric> supportsMetrics(@NonNull GBDevice device) {
        return GenericMetricSampleProvider.getSupportedMetrics(device);
    }
}
