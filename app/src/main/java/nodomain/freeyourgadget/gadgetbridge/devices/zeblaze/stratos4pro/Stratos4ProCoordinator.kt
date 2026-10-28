/*  Copyright (C) 2026 Vladimir Tasic

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
package nodomain.freeyourgadget.gadgetbridge.devices.zeblaze.stratos4pro

import android.content.Context
import android.net.Uri
import android.os.Bundle
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.capabilities.HeartRateCapability
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.GenericHrvValueSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericSpo2SampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericStressSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.InstallHandler
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.cards.ActionCardItem
import nodomain.freeyourgadget.gadgetbridge.devices.cards.DeviceCardItem
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.HrvValueSample
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample
import nodomain.freeyourgadget.gadgetbridge.model.StressSample
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProLights
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProSupport
import java.util.regex.Pattern

/**
 * Zeblaze Stratos 4 Pro, with classic BT call audio and LEDs. Settings verified on this firmware only.
 */
class Stratos4ProCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getManufacturer(): String {
        return "Zeblaze"
    }

    override fun getSupportedDeviceName(): Pattern? {
        // BR/EDR and LE both advertise "Stratos 4 Pro". The non-Pro "Stratos 4" is untested, so not
        // matched.
        return Pattern.compile("^Stratos 4 Pro$")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_zeblaze_stratos_4_pro
    }

    override fun getDefaultIconResource(): Int {
        // Round-faced watch.
        return R.drawable.ic_device_miwatch
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.WATCH
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return Stratos4ProSupport::class.java
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun getBondingStyle(): Int {
        // No LE bond at discovery; an explicit createBond() waits for a broadcast that never comes.
        // LAZY, not NONE: NONE aborts every pairing request, including the classic one for call
        // audio, and hides its consent dialog.
        return BONDING_STYLE_LAZY
    }

    override fun supportsActivityTracking(device: GBDevice): Boolean {
        return true
    }

    // Per-minute distance and kcal, stored with the steps.
    override fun supportsActivityDistance(device: GBDevice): Boolean {
        return true
    }

    override fun supportsActiveCalories(device: GBDevice): Boolean {
        return true
    }

    override fun supportsMusicInfo(device: GBDevice): Boolean {
        return true
    }

    override fun supportsFindDevice(device: GBDevice): Boolean {
        return true
    }

    override fun getContactsSlotCount(device: GBDevice): Int {
        return 20
    }

    override fun getAlarmSlotCount(device: GBDevice): Int {
        return 5
    }

    override fun supportsAlarmSounds(device: GBDevice): Boolean {
        return true
    }

    override fun supportsWeather(device: GBDevice): Boolean {
        return true
    }

    override fun supportsAppNotifications(device: GBDevice): Boolean = true

    override fun supportsPhoneCalls(device: GBDevice): Boolean = true

    override fun supportsCallControl(device: GBDevice): Boolean = true

    override fun supportsSmsReply(device: GBDevice): Boolean = true

    override fun supportsDataFetching(device: GBDevice): Boolean {
        return true
    }

    // Voice recorder files, read over `f9`.
    override fun supportsAudioRecordings(device: GBDevice): Boolean {
        return true
    }

    // GPX or FIT navigation routes, via the device menu's file installer.
    override fun supportsFlashing(device: GBDevice): Boolean {
        return true
    }

    override fun findInstallHandler(uri: Uri, options: Bundle, context: Context): InstallHandler? {
        return Stratos4ProGpxRouteInstallHandler(uri, context).takeIf { it.isValid }
    }

    override fun getSampleProvider(
        device: GBDevice,
        session: DaoSession,
    ): SampleProvider<out AbstractActivitySample> {
        return Stratos4ProActivitySampleProvider(device, session)
    }

    // Health data uses the generic tables, so the stock charts work as is.
    override fun getStressSampleProvider(
        device: GBDevice,
        session: DaoSession,
    ): TimeSampleProvider<out StressSample> {
        return GenericStressSampleProvider(device, session)
    }

    override fun getSpo2SampleProvider(
        device: GBDevice,
        session: DaoSession,
    ): TimeSampleProvider<out Spo2Sample> {
        return GenericSpo2SampleProvider(device, session)
    }

    override fun getHrvValueSampleProvider(
        device: GBDevice,
        session: DaoSession,
    ): TimeSampleProvider<out HrvValueSample> {
        return GenericHrvValueSampleProvider(device, session)
    }

    // HR comes with every `c3` read (field 02 + time in 03).
    override fun supportsHeartRateMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsHeartRateStats(device: GBDevice): Boolean {
        return true
    }

    // Staged sleep: stage 3 = awake, 4 = REM.
    override fun supportsRemSleep(device: GBDevice): Boolean {
        return true
    }

    override fun supportsAwakeSleep(device: GBDevice): Boolean {
        return true
    }

    override fun supportsStressMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsSpo2(device: GBDevice): Boolean {
        return true
    }

    override fun supportsSleepMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsHrvMeasurement(device: GBDevice): Boolean {
        // Frames have an HRV field (bit 26) and it is stored, but a week of history never set it.
        return false
    }

    // Workouts come from `e8` as BaseActivitySummary rows.
    override fun supportsRecordedActivities(device: GBDevice): Boolean {
        return true
    }

    // `e1 ab 02` + `ab 0b`: enable flag plus a 1-byte interval in minutes.
    override fun getHeartRateMeasurementIntervals(): List<HeartRateCapability.MeasurementInterval> {
        return listOf(
            HeartRateCapability.MeasurementInterval.OFF,
            HeartRateCapability.MeasurementInterval.MINUTES_1,
            HeartRateCapability.MeasurementInterval.MINUTES_5,
            HeartRateCapability.MeasurementInterval.MINUTES_10,
            HeartRateCapability.MeasurementInterval.MINUTES_15,
            HeartRateCapability.MeasurementInterval.MINUTES_30,
        )
    }

    override fun supportsManualHeartRateMeasurement(device: GBDevice): Boolean {
        // `e1 ab 12` one-click measurement.
        return true
    }

    override fun getWorldClocksSlotCount(): Int {
        return Stratos4ProSupport.WORLD_CLOCK_SLOTS
    }

    override fun getWorldClocksLabelLength(): Int {
        return Stratos4ProSupport.WORLD_CLOCK_LABEL_LEN
    }

    override fun getCannedRepliesSlotCount(device: GBDevice): Int {
        return Stratos4ProSupport.CANNED_REPLY_SLOTS
    }

    /**
     * LEDs over the debug log service ([Stratos4ProLights]): raw GPIO writes, pins measured on one
     * board, so exact model only.
     */
    fun supportsLights(device: GBDevice): Boolean {
        return device.model == MODEL_STRATOS_4_PRO
    }

    override fun getCardItems(device: GBDevice): List<DeviceCardItem> {
        // Light buttons on the exact model only; each hides itself until the service is present.
        if (!supportsLights(device)) return super.getCardItems(device)
        return super.getCardItems(device) + listOf(
            Stratos4ProLightAction(Stratos4ProLights.Light.WHITE),
            Stratos4ProLightAction(Stratos4ProLights.Light.RED),
        ).map { ActionCardItem(it) }
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec {
        return stratos4ProDeviceSettings(device, this)
    }

    companion object {
        /** Model string (`a4` field 02) of the unit the LED pins were measured on. */
        const val MODEL_STRATOS_4_PRO = "NAL-WB00"
    }
}
