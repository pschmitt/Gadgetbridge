package nodomain.freeyourgadget.gadgetbridge.activities.discovery

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator.ConnectionType
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator.DeviceKind
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils

class DeviceTypePickerViewModel : ViewModel() {
    data class Entry(
        val deviceType: DeviceType,
        val name: String,
        val manufacturer: String,
        val kind: DeviceKind,
        @DrawableRes val icon: Int,
        val experimental: Boolean
    )

    data class State(
        val manufacturers: List<String>,
        val kinds: List<DeviceKind>,
        val manufacturer: String?,
        val kind: DeviceKind?,
        val entries: List<Entry>,
        val checked: DeviceType?
    )

    private var allEntries: List<Entry> = emptyList()
    private var sortedManufacturers: List<String> = emptyList()
    private var sortedKinds: List<DeviceKind> = emptyList()
    private var manufacturer: String? = null
    private var kind: DeviceKind? = null
    private var query: String = ""
    private var checked: DeviceType? = null

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var loaded = false

    fun load(context: Context, connectionTypes: Set<ConnectionType>, preselected: DeviceType?) {
        if (loadJob != null) return

        checked = preselected

        val appContext = context.applicationContext
        loadJob = viewModelScope.launch {
            withContext(Dispatchers.Default) { buildEntries(appContext, connectionTypes) }
            loaded = true
            refresh()
        }
    }

    private fun buildEntries(context: Context, connectionTypes: Set<ConnectionType>) {
        val i18nUnknown = context.getString(R.string.unknown)
        val i18nGeneric = context.getString(R.string.pref_header_generic)

        allEntries = DeviceType.entries.mapNotNull { deviceType ->
            val coordinator = deviceType.deviceCoordinator
            if (coordinator.connectionType !in connectionTypes) {
                return@mapNotNull null
            }
            val manufacturer = when (coordinator.manufacturer) {
                "Unknown" -> i18nUnknown
                "Generic" -> i18nGeneric
                else -> coordinator.manufacturer
            }
            val device = GBDevice("", null, null, null, deviceType)
            Entry(
                deviceType = deviceType,
                name = context.getString(coordinator.deviceNameResource),
                manufacturer = manufacturer,
                kind = coordinator.getDeviceKind(device) ?: DeviceKind.UNKNOWN,
                icon = coordinator.defaultIconResource,
                experimental = coordinator.isExperimental
            )
        }.sortedWith(compareBy(StringUtils::naturalCompare) { it.name })

        val manufacturerPriority = listOf("Gadgetbridge", i18nGeneric, i18nUnknown)
        sortedManufacturers = allEntries.map { it.manufacturer }.distinct().sortedWith(
            compareBy<String> { manufacturer ->
                val index = manufacturerPriority.indexOfFirst { it.equals(manufacturer, ignoreCase = true) }
                if (index >= 0) index else Int.MAX_VALUE
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it }
        )

        sortedKinds = allEntries.map { it.kind }.distinct().sortedWith(
            compareBy<DeviceKind> { it == DeviceKind.UNKNOWN }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { context.getString(it.labelRes()) }
        )
    }

    fun setManufacturer(newManufacturer: String?) {
        manufacturer = newManufacturer
        refresh()
    }

    fun setKind(newKind: DeviceKind?) {
        kind = newKind
        refresh()
    }

    fun setQuery(newQuery: String) {
        query = newQuery
        refresh()
    }

    fun setChecked(deviceType: DeviceType) {
        checked = deviceType
        refresh()
    }

    private fun refresh() {
        if (!loaded) return

        val trimmed = query.trim()
        _state.value = State(
            manufacturers = sortedManufacturers.filter { b ->
                b == manufacturer || allEntries.any { it.manufacturer == b && matchesKind(it) }
            },
            kinds = sortedKinds.filter { k ->
                k == kind || allEntries.any { it.kind == k && matchesManufacturer(it) }
            },
            manufacturer = manufacturer,
            kind = kind,
            entries = allEntries.filter { entry ->
                matchesManufacturer(entry) && matchesKind(entry) && (trimmed.isEmpty() ||
                    entry.name.contains(trimmed, ignoreCase = true) ||
                    entry.manufacturer.contains(trimmed, ignoreCase = true))
            },
            checked = checked
        )
    }

    private fun matchesManufacturer(entry: Entry): Boolean = manufacturer == null || entry.manufacturer == manufacturer

    private fun matchesKind(entry: Entry): Boolean = kind == null || entry.kind == kind
}

@StringRes
fun DeviceKind.labelRes(): Int = when (this) {
    DeviceKind.UNKNOWN -> R.string.unknown
    DeviceKind.WATCH -> R.string.device_kind_watch
    DeviceKind.PHONE -> R.string.device_kind_phone
    DeviceKind.SCALE -> R.string.device_kind_scale
    DeviceKind.RING -> R.string.device_kind_ring
    DeviceKind.HEAD_MOUNTED -> R.string.device_kind_head_mounted
    DeviceKind.FITNESS_BAND -> R.string.device_kind_fitness_band
    DeviceKind.CHEST_STRAP -> R.string.device_kind_chest_strap
    DeviceKind.SMART_DISPLAY -> R.string.device_kind_smart_display
    DeviceKind.SPEAKER -> R.string.device_kind_speaker
    DeviceKind.HEADPHONES -> R.string.devicetype_generic_headphones
    DeviceKind.SMART_GLASSES -> R.string.device_kind_smart_glasses
    DeviceKind.EARBUDS -> R.string.device_kind_earbuds
    DeviceKind.BIKE_COMPUTER -> R.string.device_kind_bike_computer
    DeviceKind.SMART_CLOCK -> R.string.device_kind_smart_clock
    DeviceKind.THERMOMETER -> R.string.menuitem_thermometer
    DeviceKind.PV_EQUIPMENT -> R.string.device_kind_pv_equipment
    DeviceKind.GLUCOSE_METER -> R.string.device_kind_glucose_meter
    DeviceKind.BLOOD_PRESSURE_METER -> R.string.device_kind_blood_pressure_meter
    DeviceKind.BATTERY_MONITOR -> R.string.device_kind_battery_monitor
    DeviceKind.SCOOTER -> R.string.device_kind_scooter
    DeviceKind.CAMERA -> R.string.device_kind_camera
    DeviceKind.VACUUM -> R.string.device_kind_vacuum
    DeviceKind.GAMEPAD -> R.string.device_kind_gamepad
    DeviceKind.GYM_EQUIPMENT -> R.string.device_kind_gym_equipment
    DeviceKind.PULSE_OXIMETER -> R.string.device_kind_pulse_oximeter
}
