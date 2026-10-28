package nodomain.freeyourgadget.gadgetbridge.devices.cards

import androidx.annotation.DrawableRes
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.CategorySetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.GroupSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.QuickSettings
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ScreenSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.SeekBarSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.SwitchSetting
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import org.slf4j.LoggerFactory

object QuickControls {
    private val LOG = LoggerFactory.getLogger(QuickControls::class.java)

    /**
     * Returns a quick control for each of the preference keys in [keys] / [optionalKeys].
     * The preference order is kept, and optional preferences are placed after the default ones.
     * Each key must be a switch, a list or a seek bar from the device settings DSL.
     * The control visibility follows the same logic as the device settings.
     */
    @JvmStatic
    @JvmOverloads
    fun fromDeviceSettings(
        device: GBDevice,
        keys: List<String>,
        optionalKeys: List<String> = emptyList(),
    ): List<QuickControl> {
        val spec = device.deviceCoordinator.getDeviceSettings(device) ?: return emptyList()

        val switches = mutableMapOf<String, SwitchSetting>()
        collectSwitches(spec.items, switches)

        val prefs = Prefs(GBApplication.getDeviceSpecificSharedPrefs(device.address))
        val controls = mutableMapOf<String, QuickControl>()
        val allKeys = keys + optionalKeys
        collect(spec.items, allKeys.toSet(), optionalKeys.toSet(), emptyList(), R.drawable.ic_settings, prefs, switches, controls)

        return allKeys.mapNotNull { key ->
            controls[key] ?: run {
                LOG.warn("No quick control for device setting {}", key)
                null
            }
        }
    }

    private fun collectSwitches(items: List<DeviceSetting>, out: MutableMap<String, SwitchSetting>) {
        for (setting in items) {
            when (setting) {
                is SwitchSetting -> out[setting.key] = setting
                is GroupSetting -> collectSwitches(setting.children, out)
                else -> {}
            }
        }
    }

    private fun collect(
        items: List<DeviceSetting>,
        keys: Set<String>,
        optionalKeys: Set<String>,
        conditions: List<(Prefs) -> Boolean>,
        @DrawableRes parentIcon: Int,
        prefs: Prefs,
        switches: Map<String, SwitchSetting>,
        out: MutableMap<String, QuickControl>,
    ) {
        for (setting in items) {
            if (setting !is GroupSetting && (setting.key !in keys || setting.key in out)) {
                continue
            }
            val settingConditions = conditions + listOfNotNull(
                setting.visibleWhen,
                dependencyOf(setting)?.let { key -> { p: Prefs -> isDependencyEnabled(p, key, switches) } },
            )
            val visible = { device: GBDevice ->
                val devicePrefs = Prefs(GBApplication.getDeviceSpecificSharedPrefs(device.address))
                settingConditions.all { it(devicePrefs) }
            }
            val icon = iconOf(setting).takeIf { it != 0 } ?: parentIcon
            when (setting) {
                is ScreenSetting -> collect(
                    setting.children,
                    keys,
                    optionalKeys,
                    settingConditions + listOfNotNull(setting.enabled),
                    icon,
                    prefs,
                    switches,
                    out,
                )

                is CategorySetting -> collect(
                    setting.children,
                    keys,
                    optionalKeys,
                    settingConditions,
                    icon,
                    prefs,
                    switches,
                    out
                )

                is SwitchSetting -> out[setting.key] = QuickControlSwitch.forPreference(
                    key = setting.key,
                    icon = icon,
                    title = setting.title,
                    defaultValue = setting.defaultValue,
                    visible = visible,
                    optional = setting.key in optionalKeys,
                )

                is ListSetting -> out[setting.key] = QuickControlDialog.forPreference(
                    key = setting.key,
                    icon = icon,
                    title = setting.title,
                    defaultValue = setting.defaultValue,
                    options = QuickSettings.resolveEntries(GBApplication.getContext(), setting, prefs).map {
                        when (it) {
                            is ListEntry.Res -> QuickControlOption(it.value, label = it.label)
                            is ListEntry.Text -> QuickControlOption(it.value, labelText = it.label)
                        }
                    },
                    visible = visible,
                    optional = setting.key in optionalKeys,
                )

                is SeekBarSetting -> out[setting.key] = QuickControlSlider.forPreference(
                    key = setting.key,
                    icon = icon,
                    title = setting.title,
                    min = setting.min,
                    max = setting.max,
                    defaultValue = setting.defaultValue,
                    step = setting.step,
                    formatValue = if (setting.valueFormat != 0) {
                        { context, value -> context.getString(setting.valueFormat, value * setting.scale) }
                    } else {
                        { _, value -> value.toString() }
                    },
                    visible = visible,
                    optional = setting.key in optionalKeys,
                )

                else -> {}
            }
        }
    }

    @DrawableRes
    private fun iconOf(setting: DeviceSetting): Int = when (setting) {
        is ScreenSetting -> setting.icon
        is CategorySetting -> setting.icon
        is SwitchSetting -> setting.icon
        is ListSetting -> setting.icon
        is SeekBarSetting -> setting.icon
        else -> 0
    }

    private fun dependencyOf(setting: DeviceSetting): String? = when (setting) {
        is SwitchSetting -> setting.dependency
        is ListSetting -> setting.dependency
        is SeekBarSetting -> setting.dependency
        else -> null
    }

    /**
     * Whether the switch [key] enables its dependents. A dependency that is not a switch of the
     * device settings is always enabled.
     */
    private fun isDependencyEnabled(prefs: Prefs, key: String?, switches: Map<String, SwitchSetting>): Boolean {
        val switch = key?.let { switches[it] } ?: return true
        return prefs.getBoolean(switch.key, switch.defaultValue) != switch.disableDependentsState &&
            isDependencyEnabled(prefs, switch.dependency, switches)
    }
}
