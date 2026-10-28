package nodomain.freeyourgadget.gadgetbridge.widgets

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.ActiveCaloriesWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.ActiveTimeWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BloodPressureWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BodyEnergyWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.CaloriesWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.DistanceWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.GoalsWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.HeartRateWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.HrvWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.HydrationWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.LastActivityWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.PaiWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.RespiratoryRateWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.SleepScoreWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.SleepWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Spo2Widget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StepsWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StressWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.TodayWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.TrainingLoadWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.TrainingReadinessWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Vo2MaxWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BmiWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BasalMetabolicRateWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BodyFatWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BodyWaterWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.MuscleMassWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.WeightWidget

/**
 * Resolves [GBWidget] implementations by their IDs ([GBWidget.id]) - the common ones
 * plus the ones that coordinators of currently paired devices declare via
 * [nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator.getWidgetsProvider].
 */
object WidgetRegistry {
    private val common: List<GBWidget<*>> = listOf(
        ActiveCaloriesWidget,
        ActiveTimeWidget,
        BasalMetabolicRateWidget,
        BloodPressureWidget,
        BmiWidget,
        BodyEnergyWidget,
        BodyFatWidget,
        BodyWaterWidget,
        CaloriesWidget,
        DistanceWidget,
        GoalsWidget,
        HeartRateWidget,
        HrvWidget,
        HydrationWidget,
        LastActivityWidget,
        MuscleMassWidget,
        PaiWidget,
        RespiratoryRateWidget,
        SleepScoreWidget,
        SleepWidget,
        Spo2Widget,
        StepsWidget,
        StressWidget,
        TodayWidget,
        TrainingLoadWidget,
        TrainingReadinessWidget,
        Vo2MaxWidget,
        WeightWidget,
    )

    /**
     * All known widget kinds: common + device-specific.
     */
    fun all(): List<GBWidget<*>> {
        val deviceSpecificWidgets = GBApplication.app().deviceManager.devices
            .map { it.deviceCoordinator }
            .distinct()
            .flatMap { it.widgetsProvider.getWidgets() }

        val byId = LinkedHashMap<String, GBWidget<*>>()
        for (widget in common) byId.putIfAbsent(widget.id, widget)
        for (widget in deviceSpecificWidgets) byId.putIfAbsent(widget.id, widget)
        return byId.values.toList()
    }

    fun byId(id: String): GBWidget<*>? = all().find { it.id == id }

    /**
     * The devices that are enabled for the dashboard.
     */
    fun dashboardDevices(): List<GBDevice> {
        val prefs = GBApplication.getPrefs()
        val showAllDevices = prefs.getBoolean("dashboard_devices_all", true)
        val selectedDevices = prefs.getStringSet("dashboard_devices_multiselect", emptySet())
        return GBApplication.app().deviceManager.devices
            .filter { showAllDevices || it.address in selectedDevices }
    }

    /**
     * Widget kinds supported by at least one device that is enabled for the dashboard.
     */
    fun available(): List<GBWidget<*>> {
        val devices = dashboardDevices()
        return all().filter { widget -> devices.any { widget.isSupportedBy(it) } }
    }

    /**
     * Whether at least one widget supports the device.
     */
    @JvmStatic
    fun supportsAnyWidget(device: GBDevice): Boolean = all().any { it.isSupportedBy(device) }
}
