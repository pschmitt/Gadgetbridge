/*  Copyright (C) 2023-2024 Daniel Dakhno, José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.devices.test;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.appmanager.AppManagerActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec;
import nodomain.freeyourgadget.gadgetbridge.capabilities.HeartRateCapability;
import nodomain.freeyourgadget.gadgetbridge.capabilities.loyaltycards.BarcodeFormat;
import nodomain.freeyourgadget.gadgetbridge.capabilities.password.PasswordCapabilityImpl;
import nodomain.freeyourgadget.gadgetbridge.capabilities.widgets.WidgetManager;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.HydrationSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.InstallHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.Vo2MaxSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControl;
import nodomain.freeyourgadget.gadgetbridge.devices.roidmi.RoidmiConst;
import nodomain.freeyourgadget.gadgetbridge.devices.test.activity.TestActivitySummaryParser;
import nodomain.freeyourgadget.gadgetbridge.devices.test.activity.TestActivityTrackProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestBloodPressureSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestBodyEnergySampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestCyclingSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestGenericMetricSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestHeartRateRestingSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestHrvSummarySampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestHrvValueSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestPaiSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestRespiratoryRateSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestSleepScoreSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestSolarChargeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestSpo2SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestStressSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestTemperatureSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestTrainingLoadAcuteSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestTrainingLoadChronicSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestVo2MaxSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestWeightSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.samples.TestWorkoutLoadSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.CyclingSample;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadAcuteSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadChronicSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate;
import nodomain.freeyourgadget.gadgetbridge.model.AbstractNotificationPattern;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryParser;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrackProvider;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig;
import nodomain.freeyourgadget.gadgetbridge.model.BloodPressureSample;
import nodomain.freeyourgadget.gadgetbridge.model.BodyEnergySample;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.model.HeartRateSample;
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample;
import nodomain.freeyourgadget.gadgetbridge.model.HrvValueSample;
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample;
import nodomain.freeyourgadget.gadgetbridge.model.PaiSample;
import nodomain.freeyourgadget.gadgetbridge.model.RespiratoryRateSample;
import nodomain.freeyourgadget.gadgetbridge.model.SleepScoreSample;
import nodomain.freeyourgadget.gadgetbridge.model.SolarChargeSample;
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample;
import nodomain.freeyourgadget.gadgetbridge.model.StressSample;
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureSample;
import nodomain.freeyourgadget.gadgetbridge.model.TrainingLoadStatus;
import nodomain.freeyourgadget.gadgetbridge.model.Vo2MaxSample;
import nodomain.freeyourgadget.gadgetbridge.model.WeightSample;
import nodomain.freeyourgadget.gadgetbridge.model.WorkoutLoadSample;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.ServiceDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.test.TestDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class TestDeviceCoordinator extends AbstractDeviceCoordinator {
    @Override
    public boolean supports(@NonNull final GBDeviceCandidate candidate) {
        return false;
    }

    @Override
    public String getManufacturer() {
        return "Generic";
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(@NonNull final GBDevice device) {
        return TestDeviceSupport.class;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_test;
    }

    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsActivityTracking(device) ? new TestSampleProvider(device, session) : super.getSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends StressSample> getStressSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsStressMeasurement(device) ? new TestStressSampleProvider() : super.getStressSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends BodyEnergySample> getBodyEnergySampleProvider(final GBDevice device, final DaoSession session) {
        return supportsBodyEnergy(device) ? new TestBodyEnergySampleProvider() : super.getBodyEnergySampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends HrvSummarySample> getHrvSummarySampleProvider(final GBDevice device, final DaoSession session) {
        return supportsHrvMeasurement(device) ? new TestHrvSummarySampleProvider() : super.getHrvSummarySampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends HrvValueSample> getHrvValueSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsHrvMeasurement(device) ? new TestHrvValueSampleProvider() : super.getHrvValueSampleProvider(device, session);
    }

    @Override
    public int[] getStressRanges() {
        // TODO getStressRanges
        return super.getStressRanges();
    }

    @Override
    public TimeSampleProvider<? extends TemperatureSample> getTemperatureSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsTemperatureMeasurement(device) ? new TestTemperatureSampleProvider(device) : super.getTemperatureSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends Spo2Sample> getSpo2SampleProvider(final GBDevice device, final DaoSession session) {
        return supportsSpo2(device) ? new TestSpo2SampleProvider() : super.getSpo2SampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends HeartRateSample> getHeartRateMaxSampleProvider(final GBDevice device, final DaoSession session) {
        // TODO getHeartRateMaxSampleProvider
        return super.getHeartRateMaxSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends HeartRateSample> getHeartRateRestingSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsHeartRateRestingMeasurement(device) ? new TestHeartRateRestingSampleProvider() : super.getHeartRateRestingSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends HeartRateSample> getHeartRateManualSampleProvider(final GBDevice device, final DaoSession session) {
        // TODO getHeartRateManualSampleProvider
        return super.getHeartRateManualSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends BloodPressureSample> getBloodPressureSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsBloodPressureMeasurement(device) ? new TestBloodPressureSampleProvider() : super.getBloodPressureSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends SolarChargeSample> getSolarChargeSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsSolarCharging(device) ? new TestSolarChargeSampleProvider() : super.getSolarChargeSampleProvider(device, session);
    }

    @Override
    public Vo2MaxSampleProvider<? extends Vo2MaxSample> getVo2MaxSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsVO2Max(device) ? new TestVo2MaxSampleProvider(device) : super.getVo2MaxSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends WeightSample> getWeightSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsWeightMeasurement(device) ? new TestWeightSampleProvider() : super.getWeightSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends SleepScoreSample> getSleepScoreProvider(final GBDevice device, final DaoSession session) {
        return supportsSleepScore(device) ? new TestSleepScoreSampleProvider() : super.getSleepScoreProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends WorkoutLoadSample> getWorkoutLoadSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsTrainingLoad(device) ? new TestWorkoutLoadSampleProvider() : super.getWorkoutLoadSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends GenericTrainingLoadAcuteSample> getTrainingAcuteLoadSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsTrainingLoad(device) ? new TestTrainingLoadAcuteSampleProvider() : super.getTrainingAcuteLoadSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends GenericTrainingLoadChronicSample> getTrainingChronicLoadSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsTrainingLoadChronic(device) ? new TestTrainingLoadChronicSampleProvider() : super.getTrainingChronicLoadSampleProvider(device, session);
    }

    @Override
    public TrainingLoadStatus getTrainingLoadStatus(@NonNull final GBDevice device, @NonNull final DaoSession session, final long untilTs) {
        if (!supportsTrainingLoad(device)) {
            return super.getTrainingLoadStatus(device, session, untilTs);
        }
        final GenericTrainingLoadAcuteSample acute = new TestTrainingLoadAcuteSampleProvider().getLatestSample(untilTs);
        final GenericTrainingLoadChronicSample chronic = new TestTrainingLoadChronicSampleProvider().getLatestSample(untilTs);
        if (acute == null || chronic == null) {
            return null;
        }
        final float ratio = (float) acute.getValue() / chronic.getValue();
        if (ratio < 0.8f) {
            return TrainingLoadStatus.LOW;
        } else if (ratio <= 1.3f) {
            return TrainingLoadStatus.OPTIMAL;
        } else if (ratio <= 1.5f) {
            return TrainingLoadStatus.HIGH;
        }
        return TrainingLoadStatus.VERY_HIGH;
    }

    @Override
    public GenericMetricSampleProvider getMetricsSampleProvider(@NonNull final GBDevice device, @NonNull final DaoSession session) {
        return new TestGenericMetricSampleProvider(device, session);
    }

    @NonNull
    @Override
    public Set<MetricSample.Metric> supportsMetrics(@NonNull final GBDevice device) {
        final Set<MetricSample.Metric> metrics = EnumSet.noneOf(MetricSample.Metric.class);
        if (supports(device, TestFeature.GENERIC_METRICS)) {
            metrics.addAll(EnumSet.complementOf(EnumSet.of(MetricSample.Metric.UNKNOWN)));
            return metrics;
        }
        if (supportsTrainingLoad(device)) {
            metrics.add(MetricSample.Metric.GENERIC_TRAINING_LOAD_ACUTE);
        }
        if (supportsTrainingLoadChronic(device)) {
            metrics.add(MetricSample.Metric.GENERIC_TRAINING_LOAD_CHRONIC);
        }
        if (supportsRacePrediction(device)) {
            metrics.add(MetricSample.Metric.GENERIC_RACE_PREDICTOR_5K);
            metrics.add(MetricSample.Metric.GENERIC_RACE_PREDICTOR_10K);
            metrics.add(MetricSample.Metric.GENERIC_RACE_PREDICTOR_HALF_MARATHON);
            metrics.add(MetricSample.Metric.GENERIC_RACE_PREDICTOR_FULL_MARATHON);
        }
        if (supportsTrainingReadiness(device)) {
            metrics.add(MetricSample.Metric.GARMIN_TRAINING_READINESS);
            metrics.add(MetricSample.Metric.GARMIN_RECOVERY_TIME);
        }
        return metrics;
    }

    @Override
    public TimeSampleProvider<CyclingSample> getCyclingSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsCyclingData(device) ? new TestCyclingSampleProvider() : super.getCyclingSampleProvider(device, session);
    }

    @Override
    public HydrationSampleProvider getHydrationSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsHydration(device) ? new HydrationSampleProvider(device, session) : super.getHydrationSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends PaiSample> getPaiSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsPai(device) ? new TestPaiSampleProvider() : super.getPaiSampleProvider(device, session);
    }

    @Override
    public TimeSampleProvider<? extends RespiratoryRateSample> getRespiratoryRateSampleProvider(final GBDevice device, final DaoSession session) {
        return supportsRespiratoryRate(device) ? new TestRespiratoryRateSampleProvider() : super.getRespiratoryRateSampleProvider(device, session);
    }

    @Nullable
    @Override
    public ActivitySummaryParser getActivitySummaryParser(final GBDevice device, final Context context) {
        return this.supportsRecordedActivities(device) ? new TestActivitySummaryParser() : super.getActivitySummaryParser(device, context);
    }

    @Override
    public ActivityTrackProvider getActivityTrackProvider(@NonNull final GBDevice device, @NonNull final Context context) {
        return this.supportsRecordedActivities(device) ? new TestActivityTrackProvider() : super.getActivityTrackProvider(device, context);
    }

    @Override
    public File getAppCacheDir() throws IOException {
        return new File(FileUtils.getExternalFilesDir(), "test-app-cache");
    }

    @Override
    public String getAppCacheSortFilename() {
        return "test-app-cache-order.txt";
    }

    @Override
    public String getAppFileExtension() {
        return ".txt";
    }

    @Override
    public boolean supportsAppListFetching(@NonNull final GBDevice device) {
        return supports(device, TestFeature.APP_LIST_FETCHING);
    }

    @Override
    public boolean supportsFlashing(@NonNull GBDevice device) {
        return supports(device, TestFeature.FLASHING);
    }

    @Nullable
    @Override
    public InstallHandler findInstallHandler(final Uri uri, final Bundle options, final Context context) {
        // TODO findInstallHandler?
        return super.findInstallHandler(uri, options, context);
    }

    @Override
    public boolean supportsScreenshots(@NonNull final GBDevice device) {
        return supports(device, TestFeature.SCREENSHOTS);
    }

    @Override
    public boolean supportsGallery(@NonNull final GBDevice device) {
        return supports(device, TestFeature.GALLERY);
    }

    @Override
    public int getAlarmSlotCount(final GBDevice device) {
        /*
          0: Forced smart, no interval
          1: Forced smart, with interval
          2: Unforced smart, no interval
          3: Unforced smart, with interval
          4: Not smart
         */
        return 5;
    }

    @Override
    public boolean supportsSmartWakeup(@NonNull final GBDevice device, int position) {
        return supports(getTestDevice(), TestFeature.SMART_WAKEUP) && position <= 3;
    }

    @Override
    public boolean supportsSmartWakeupInterval(@NonNull GBDevice device, int alarmPosition) {
        return supports(getTestDevice(), TestFeature.SMART_WAKEUP_INTERVAL) && (alarmPosition == 1 || alarmPosition == 3);
    }

    @Override
    public String getSmartWakeupDescription(@NonNull final GBDevice device) {
        return supports(device, TestFeature.SMART_WAKEUP_DESCRIPTION)
            ? "Test device smart wake up description."
            : super.getSmartWakeupDescription(device);
    }

    @Override
    public boolean forcedSmartWakeup(final GBDevice device, final int alarmPosition) {
        return supports(getTestDevice(), TestFeature.SMART_WAKEUP_FORCED_SLOT) && alarmPosition <= 1;
    }

    @Override
    public boolean supportsAppReordering(@NonNull final GBDevice device) {
        return supports(device, TestFeature.APP_REORDERING);
    }

    @Override
    public boolean supportsAppsManagement(@NonNull final GBDevice device) {
        return supports(device, TestFeature.APPS_MANAGEMENT);
    }

    @Override
    public boolean supportsCachedAppManagement(@NonNull final GBDevice device) {
        return supports(device, TestFeature.CACHED_APP_MANAGEMENT);
    }

    @Override
    public boolean supportsInstalledAppManagement(@NonNull final GBDevice device) {
        return supports(device, TestFeature.INSTALLED_APP_MANAGEMENT);
    }

    @Override
    public boolean supportsWatchfaceManagement(@NonNull final GBDevice device) {
        return supports(device, TestFeature.WATCHFACE_MANAGEMENT);
    }

    @Nullable
    @Override
    public Class<? extends Activity> getAppsManagementActivity(final GBDevice device) {
        return AppManagerActivity.class;
    }

    @Nullable
    @Override
    public Class<? extends Activity> getWatchfaceDesignerActivity(final GBDevice device) {
        // TODO getWatchfaceDesignerActivity
        return super.getWatchfaceDesignerActivity(device);
    }

    @Override
    public boolean supportsCalendarEvents(final GBDevice device) {
        return supports(device, TestFeature.CALENDAR_EVENTS);
    }

    @Override
    public boolean supportsDataFetching(@NonNull final GBDevice device) {
        return supports(device, TestFeature.ACTIVITY_DATA_FETCHING);
    }

    @Override
    public boolean supportsActivityTracking(@NonNull GBDevice device) {
        return supports(device, TestFeature.ACTIVITY_TRACKING);
    }

    @Override
    public boolean supportsSleepMeasurement(@NonNull GBDevice device) {
        return supports(device, TestFeature.SLEEP_MEASUREMENT);
    }

    @Override
    public boolean supportsStepCounter(@NonNull GBDevice device) {
        return supports(device, TestFeature.STEP_COUNTER);
    }

    @Override
    public boolean supportsSpeedzones(@NonNull GBDevice device) {
        return supports(device, TestFeature.SPEEDZONES);
    }

    @Override
    public boolean supportsCharts(@NonNull GBDevice device) {
        return supports(device, TestFeature.CHARTS);
    }

    @Override
    public boolean supportsTemperatureMeasurement(@NonNull final GBDevice device) {
        return supports(device, TestFeature.TEMPERATURE_MEASUREMENT);
    }

    @Override
    public boolean supportsContinuousTemperature(@NonNull final GBDevice device) {
        return supports(device, TestFeature.CONTINUOUS_TEMPERATURE);
    }

    @Override
    public boolean supportsRecordedActivities(@NonNull final GBDevice device) {
        return supports(device, TestFeature.RECORDED_ACTIVITIES);
    }

    @Override
    public boolean supportsStressMeasurement(@NonNull GBDevice device) {
        return supports(device, TestFeature.STRESS_MEASUREMENT);
    }

    @Override
    public boolean supportsBodyEnergy(@NonNull GBDevice device) {
        return supports(device, TestFeature.BODY_ENERGY);
    }

    @Override
    public boolean supportsHrvMeasurement(@NonNull final GBDevice device) {
        return supports(device, TestFeature.HRV_MEASUREMENT);
    }

    @Override
    public boolean supportsSpo2(@NonNull GBDevice device) {
        return supports(device, TestFeature.SPO2);
    }

    @Override
    public boolean supportsHeartRateStats(@NonNull GBDevice device) {
        return supports(device, TestFeature.HEART_RATE_STATS);
    }

    @Override
    public boolean supportsHeartRateRestingMeasurement(@NonNull GBDevice device) {
        return supports(device, TestFeature.HEART_RATE_RESTING);
    }

    @Override
    public boolean supportsBloodPressureMeasurement(@NonNull GBDevice device) {
        return supports(device, TestFeature.BLOOD_PRESSURE);
    }

    @Override
    public boolean supportsWeightMeasurement(@NonNull GBDevice device) {
        return supports(device, TestFeature.WEIGHT);
    }

    @Override
    public boolean supportsSleepScore(@NonNull GBDevice device) {
        return supports(device, TestFeature.SLEEP_SCORE);
    }

    @Override
    public boolean supportsAwakeSleep(@NonNull GBDevice device) {
        return supports(device, TestFeature.AWAKE_SLEEP);
    }

    @Override
    public boolean supportsCyclingData(@NonNull GBDevice device) {
        return supports(device, TestFeature.CYCLING_DATA);
    }

    @Override
    public boolean supportsSolarCharging(@NonNull GBDevice device) {
        return supports(device, TestFeature.SOLAR_CHARGING);
    }

    @Override
    public boolean supportsTrainingLoad(@NonNull GBDevice device) {
        return supports(device, TestFeature.TRAINING_LOAD);
    }

    @Override
    public boolean supportsTrainingLoadChronic(@NonNull GBDevice device) {
        return supports(device, TestFeature.TRAINING_LOAD_CHRONIC);
    }

    @Override
    public boolean supportsRacePrediction(@NonNull GBDevice device) {
        return supports(device, TestFeature.RACE_PREDICTION);
    }

    @Override
    public boolean supportsTrainingReadiness(@NonNull GBDevice device) {
        return supports(device, TestFeature.TRAINING_READINESS);
    }

    @Override
    public boolean supportsHydration(@NonNull GBDevice device) {
        return supports(device, TestFeature.HYDRATION);
    }

    @Override
    public boolean supportsVO2Max(@NonNull GBDevice device) {
        return supports(device, TestFeature.VO2_MAX);
    }

    @Override
    public boolean supportsVO2MultiSport(@NonNull GBDevice device) {
        return supports(device, TestFeature.VO2_MULTISPORT);
    }

    @Override
    public boolean supportsActiveCalories(@NonNull GBDevice device) {
        return supports(device, TestFeature.ACTIVE_CALORIES);
    }

    @Override
    public boolean supportsActivityDistance(@NonNull GBDevice device) {
        return supports(device, TestFeature.ACTIVITY_DISTANCE);
    }

    @Override
    public boolean supportsPai(@NonNull GBDevice device) {
        return supports(device, TestFeature.PAI);
    }

    @Override
    public int getPaiName() {
        return R.string.menuitem_pai;
    }

    @Override
    public boolean supportsPaiLow(@NonNull GBDevice device) {
        return supports(device, TestFeature.PAI_LOW);
    }

    @Override
    public boolean supportsPaiTime(@NonNull GBDevice device) {
        return supports(device, TestFeature.PAI_TIME);
    }

    @Override
    public boolean supportsRespiratoryRate(@NonNull GBDevice device) {
        return supports(device, TestFeature.RESPIRATORY_RATE);
    }

    @Override
    public boolean supportsSleepRespiratoryRate(@NonNull GBDevice device) {
        return supports(device, TestFeature.SLEEP_RESPIRATORY_RATE);
    }

    @Override
    public boolean supportsDayRespiratoryRate(@NonNull GBDevice device) {
        return supports(device, TestFeature.DAY_RESPIRATORY_RATE);
    }

    @Override
    public boolean supportsAlarmSnoozing(@NonNull GBDevice device) {
        return supports(device, TestFeature.ALARM_SNOOZING);
    }

    @Override
    public boolean supportsAlarmTitle(@NonNull final GBDevice device) {
        return supports(device, TestFeature.ALARM_TITLE);
    }

    @Override
    public int getAlarmTitleLimit(final GBDevice device) {
        return supports(device, TestFeature.ALARM_TITLE_LIMIT) ? 10 : -1;
    }

    @Override
    public boolean supportsAlarmDescription(@NonNull final GBDevice device) {
        return supports(device, TestFeature.ALARM_DESCRIPTION);
    }

    @Override
    public boolean supportsAlarmSounds(@NonNull final GBDevice device) {
        return supports(device, TestFeature.ALARM_SOUNDS);
    }

    @Override
    public boolean supportsAlarmBacklight(@NonNull final GBDevice device) {
        return supports(device, TestFeature.ALARM_BACKLIGHT);
    }

    @Override
    public boolean supportsAlarmTitlePresets(@NonNull final GBDevice device) {
        return supports(device, TestFeature.ALARM_TITLE_PRESETS);
    }

    @Override
    public List<Alarm.ALARM_LABEL> getAlarmTitlePresets(@NonNull final GBDevice device) {
        return Arrays.asList(Alarm.ALARM_LABEL.values());
    }

    @Override
    public boolean supportsMusicInfo(@NonNull GBDevice device) {
        return supports(device, TestFeature.MUSIC_INFO);
    }

    @Override
    public boolean supportsLedColor(@NonNull GBDevice device) {
        return supports(device, TestFeature.LED_COLOR);
    }

    @Override
    public int getMaximumReminderMessageLength() {
        // TODO getMaximumReminderMessageLength
        return 16;
    }

    @Override
    public int getReminderSlotCount(final GBDevice device) {
        return supports(device, TestFeature.REMINDERS) ? 3 : 0;
    }

    @Override
    public int getCannedRepliesSlotCount(final GBDevice device) {
        return supports(device, TestFeature.CANNED_REPLIES) ? 3 : 0;
    }

    @Override
    public int getWorldClocksSlotCount() {
        return supports(getTestDevice(), TestFeature.WORLD_CLOCKS) ? 3 : 0;
    }

    @Override
    public int getWorldClocksLabelLength() {
        // TODO getWorldClocksLabelLength
        return 10;
    }

    @Override
    public boolean supportsDisabledWorldClocks(@NonNull GBDevice device) {
        return supports(device, TestFeature.DISABLED_WORLD_CLOCKS);
    }

    @Override
    public int getContactsSlotCount(final GBDevice device) {
        return supports(device, TestFeature.CONTACTS) ? 3 : 0;
    }

    @Override
    public boolean supportsRgbLedColor(@NonNull GBDevice device) {
        return supports(device, TestFeature.RGB_LED_COLOR);
    }

    @NonNull
    @Override
    public int[] getColorPresets() {
        return RoidmiConst.COLOR_PRESETS;
    }

    @Override
    public boolean supportsHeartRateMeasurement(@NonNull final GBDevice device) {
        return supports(device, TestFeature.HEART_RATE_MEASUREMENT);
    }

    @Override
    public boolean supportsManualHeartRateMeasurement(@NonNull final GBDevice device) {
        return supports(device, TestFeature.MANUAL_HEART_RATE_MEASUREMENT);
    }

    @Override
    public boolean supportsRealtimeData(@NonNull GBDevice device) {
        return supports(device, TestFeature.REALTIME_DATA);
    }

    @Override
    public boolean supportsRemSleep(@NonNull GBDevice device) {
        return supports(device, TestFeature.REM_SLEEP);
    }

    @Override
    public boolean supportsWeather(@NonNull final GBDevice device) {
        return supports(device, TestFeature.WEATHER);
    }

    @Override
    public boolean supportsFindDevice(@NonNull GBDevice device) {
        return supports(device, TestFeature.FIND_DEVICE);
    }

    @Override
    public boolean supportsUnicodeEmojis(@NonNull GBDevice device) {
        return supports(device, TestFeature.UNICODE_EMOJIS);
    }

    @Override
    public int[] getSupportedDeviceSpecificConnectionSettings() {
        return super.getSupportedDeviceSpecificConnectionSettings();
    }

    @Nullable
    @Override
    public DeviceSettingsSpec getDeviceSettings(@NonNull final GBDevice device) {
        return TestDeviceSettingsKt.testDeviceSettings(device, this);
    }

    @Override
    public int[] getSupportedDeviceSpecificAuthenticationSettings() {
        return new int[]{R.xml.devicesettings_pairingkey};
    }

    @Override
    public String[] getSupportedLanguageSettings(final GBDevice device) {
        return new String[]{
            "auto",
            "en_US",
        };
    }

    @Override
    public Set<BarcodeFormat> getSupportedBarcodeFormats(@NonNull final GBDevice device) {
        return Set.of(
            BarcodeFormat.CODABAR,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.ITF,
            BarcodeFormat.PDF_417,
            BarcodeFormat.QR_CODE,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E
        );
    }

    @Nullable
    @Override
    public Class<? extends Activity> getCalibrationActivity() {
        // TODO getCalibrationActivity
        return super.getCalibrationActivity();
    }

    @Override
    public int getBatteryCount(final GBDevice device) {
        return supports(device, TestFeature.BATTERIES_MULTIPLE) ? 3 : 1;
    }

    @Override
    public BatteryConfig[] getBatteryConfig(final GBDevice device) {
        if (getBatteryCount(device) == 1) {
            return super.getBatteryConfig(device);
        }

        final BatteryConfig[] ret = new BatteryConfig[getBatteryCount(device)];

        for (int i = 0; i < getBatteryCount(device); i++) {
            ret[i] = new BatteryConfig(i, R.drawable.ic_battery_full, GBDevice.BATTERY_LABEL_DEFAULT);
        }

        return ret;
    }

    @Override
    public boolean supportsPowerOff(@NonNull final GBDevice device) {
        return supports(device, TestFeature.POWER_OFF);
    }

    @Override
    public PasswordCapabilityImpl.Mode getPasswordCapability() {
        return super.getPasswordCapability();
    }

    @Override
    public List<HeartRateCapability.MeasurementInterval> getHeartRateMeasurementIntervals() {
        return super.getHeartRateMeasurementIntervals();
    }

    @Override
    public boolean supportsWidgets(@NonNull final GBDevice device) {
        return supports(device, TestFeature.WIDGETS);
    }

    @Nullable
    @Override
    public WidgetManager getWidgetManager(final GBDevice device) {
        return super.getWidgetManager(device);
    }

    @Override
    public boolean supportsAppNotifications(@NonNull final GBDevice device) {
        return supports(device, TestFeature.APP_NOTIFICATIONS);
    }

    @Override
    public boolean supportsPhoneCalls(@NonNull final GBDevice device) {
        return supports(device, TestFeature.PHONE_CALLS);
    }

    @Override
    public boolean supportsCallControl(@NonNull final GBDevice device) {
        return supports(device, TestFeature.CALL_CONTROL);
    }

    @Override
    public boolean supportsInternetAccess(@NonNull final GBDevice device) {
        return supports(device, TestFeature.INTERNET_ACCESS);
    }

    @Override
    public boolean supportsSmsReply(@NonNull final GBDevice device) {
        return supports(device, TestFeature.SMS_REPLY);
    }

    @Override
    public boolean supportsPhoneSilentMode(@NonNull final GBDevice device) {
        return supports(device, TestFeature.PHONE_SILENT_MODE);
    }

    @Override
    public boolean supportsGpsSharing(@NonNull final GBDevice device) {
        return supports(device, TestFeature.GPS_SHARING);
    }

    @Override
    public boolean supportsNavigation(@NonNull final GBDevice device) {
        return supports(device, TestFeature.NAVIGATION);
    }

    @Override
    public EnumSet<ServiceDeviceSupport.Flags> getInitialFlags() {
        return EnumSet.noneOf(ServiceDeviceSupport.Flags.class);
    }

    @Override
    public int getDefaultIconResource() {
        // TODO getDefaultIconResource
        return super.getDefaultIconResource();
    }

    @Override
    public boolean supportsNotificationVibrationPatterns(@NonNull GBDevice device) {
        return supports(device, TestFeature.NOTIFICATION_VIBRATION_PATTERNS);
    }

    @Override
    public boolean supportsNotificationVibrationRepetitionPatterns(@NonNull GBDevice device) {
        return supports(device, TestFeature.NOTIFICATION_VIBRATION_REPETITION_PATTERNS);
    }

    @Override
    public boolean supportsNotificationLedPatterns(@NonNull GBDevice device) {
        return supports(device, TestFeature.NOTIFICATION_LED_PATTERNS);
    }

    @Override
    public boolean addBatteryPollingSettings() {
        return supports(getTestDevice(), TestFeature.BATTERY_POLLING_SETTINGS);
    }

    @Override
    public AbstractNotificationPattern[] getNotificationVibrationPatterns() {
        // TODO getNotificationVibrationPatterns
        return new AbstractNotificationPattern[0];
    }

    @Override
    public AbstractNotificationPattern[] getNotificationVibrationRepetitionPatterns() {
        // TODO getNotificationVibrationRepetitionPatterns
        return new AbstractNotificationPattern[0];
    }

    @Override
    public AbstractNotificationPattern[] getNotificationLedPatterns() {
        // TODO getNotificationLedPatterns
        return new AbstractNotificationPattern[0];
    }

    @Override
    public boolean validateAuthKey(final String authKey) {
        return authKey.equals("hunter2");
    }

    public boolean supports(final GBDevice device, final TestFeature feature) {
        if (device == null) {
            return false;
        }

        final Set<String> features = new HashSet<>(getPrefs(device).getStringSet(TestDeviceConst.PREF_TEST_FEATURES, Collections.emptySet()));
        // if nothing enable, enable everything
        return features.isEmpty() || features.contains(feature.name());
    }

    @Nullable
    @Deprecated
    private GBDevice getTestDevice() {
        // HACK: Avoid the refactor of adding a device as a parameter on all functions
        // This means that it's only possible to have a single test device

        if (GBApplication.app() == null || GBApplication.app().getDeviceManager() == null) {
            return null;
        }

        final List<GBDevice> devices = GBApplication.app().getDeviceManager().getDevices();
        for (GBDevice device : devices) {
            if (DeviceType.TEST == device.getType()) {
                return device;
            }
        }

        return null;
    }

    protected static Prefs getPrefs(final GBDevice device) {
        return new Prefs(GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()));
    }

    @Override
    public List<QuickControl> getQuickControls(@NonNull final GBDevice device) {
        if (!supports(device, TestFeature.QUICK_CONTROLS)) {
            return Collections.emptyList();
        }
        return TestDeviceQuickControlsKt.testDeviceQuickControls();
    }

    @Override
    public DeviceCoordinator.DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceCoordinator.DeviceKind.UNKNOWN;
    }
}
