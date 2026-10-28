package nodomain.freeyourgadget.gadgetbridge.devices.cards

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import org.slf4j.LoggerFactory

/**
 * A device-specific control in the device card, below the icon row.
 */
sealed interface QuickControl : DeviceCardEntry {
    @get:StringRes
    val title: Int

    @get:DrawableRes
    val icon: Int
}

data class QuickControlOption(
    val value: String,
    @param:StringRes val label: Int = 0,
    @param:DrawableRes val icon: Int = 0,
    /**
     * overrides [label] when set.
     */
    val labelText: String? = null,
) {
    fun label(context: Context): String = labelText ?: context.getString(label)
}

/**
 * An icon button at the end of a quick control row. It is displayed only for the values that
 * [visible] accepts.
 */
data class QuickControlTrailingAction<T>(
    @param:DrawableRes val icon: Int,
    @param:StringRes val description: Int,
    val visible: (GBDevice, T) -> Boolean,
    val onClick: (GBDevice, Context) -> Unit,
)

/**
 * A row of buttons. Each button selects one of the [options].
 * Rendered as a MaterialButtonToggleGroup.
 */
data class QuickControlToggleGroup(
    override val id: String,
    @param:StringRes override val title: Int,
    @param:DrawableRes override val icon: Int,
    val options: List<QuickControlOption>,
    val value: (GBDevice) -> String,
    val onChange: (GBDevice, Context, String) -> Unit,
    val visible: (GBDevice) -> Boolean = { true },
    val trailingAction: QuickControlTrailingAction<String>? = null,
    override val optional: Boolean = false,
) : QuickControl {
    override fun isVisible(device: GBDevice) = visible(device)

    companion object {
        /**
         * Creates a control for a string device preference. A change stores the value and sends
         * the configuration to the device.
         */
        @JvmStatic
        fun forPreference(
            key: String,
            @StringRes title: Int,
            @DrawableRes icon: Int,
            defaultValue: String,
            options: List<QuickControlOption>,
            visible: (GBDevice) -> Boolean = { true },
            trailingAction: QuickControlTrailingAction<String>? = null,
            optional: Boolean = false,
        ) = QuickControlToggleGroup(
            id = key,
            title = title,
            icon = icon,
            options = options,
            value = { device -> GBApplication.getDevicePrefs(device).getString(key, defaultValue) },
            onChange = { device, _, value -> QuickControlPreferences.putString(device, key, value) },
            visible = visible,
            trailingAction = trailingAction,
            optional = optional,
        )
    }
}

/**
 * A button that displays the current value. It opens a dialog to select one of the [options].
 */
data class QuickControlDialog(
    override val id: String,
    @param:DrawableRes override val icon: Int,
    @param:StringRes override val title: Int,
    val options: List<QuickControlOption>,
    val value: (GBDevice) -> String,
    val onChange: (GBDevice, Context, String) -> Unit,
    val visible: (GBDevice) -> Boolean = { true },
    val trailingAction: QuickControlTrailingAction<String>? = null,
    override val optional: Boolean = false,
) : QuickControl {
    override fun isVisible(device: GBDevice) = visible(device)

    companion object {
        /**
         * Creates a control for a string device preference. A change stores the value and sends
         * the configuration to the device.
         */
        @JvmStatic
        fun forPreference(
            key: String,
            @DrawableRes icon: Int,
            @StringRes title: Int,
            defaultValue: String,
            options: List<QuickControlOption>,
            visible: (GBDevice) -> Boolean = { true },
            trailingAction: QuickControlTrailingAction<String>? = null,
            optional: Boolean = false,
        ) = QuickControlDialog(
            id = key,
            icon = icon,
            title = title,
            options = options,
            value = { device -> GBApplication.getDevicePrefs(device).getString(key, defaultValue) },
            onChange = { device, _, value -> QuickControlPreferences.putString(device, key, value) },
            visible = visible,
            trailingAction = trailingAction,
            optional = optional,
        )
    }
}

/**
 * A slider for an integer value between [min] and [max].
 *
 * @param continuous call [onChange] at each step while the user drags the slider. If false,
 * [onChange] is called only when the user releases the slider.
 */
data class QuickControlSlider(
    override val id: String,
    @param:DrawableRes override val icon: Int,
    @param:StringRes override val title: Int,
    val min: Int,
    val max: Int,
    val step: Int = 1,
    val continuous: Boolean = false,
    val formatValue: (Context, Int) -> String = { _, value -> value.toString() },
    val value: (GBDevice) -> Int,
    val onChange: (GBDevice, Context, Int) -> Unit,
    val visible: (GBDevice) -> Boolean = { true },
    val trailingAction: QuickControlTrailingAction<Int>? = null,
    override val optional: Boolean = false,
) : QuickControl {
    override fun isVisible(device: GBDevice) = visible(device)

    companion object {
        /**
         * Creates a control for an integer device preference. A change stores the value and sends
         * the configuration to the device.
         */
        @JvmStatic
        fun forPreference(
            key: String,
            @DrawableRes icon: Int,
            @StringRes title: Int,
            min: Int,
            max: Int,
            defaultValue: Int,
            step: Int = 1,
            continuous: Boolean = false,
            formatValue: (Context, Int) -> String = { _, value -> value.toString() },
            visible: (GBDevice) -> Boolean = { true },
            trailingAction: QuickControlTrailingAction<Int>? = null,
            optional: Boolean = false,
        ) = QuickControlSlider(
            id = key,
            icon = icon,
            title = title,
            min = min,
            max = max,
            step = step,
            continuous = continuous,
            formatValue = formatValue,
            value = { device -> GBApplication.getDevicePrefs(device).getInt(key, defaultValue) },
            onChange = { device, _, value -> QuickControlPreferences.putInt(device, key, value) },
            visible = visible,
            trailingAction = trailingAction,
            optional = optional,
        )
    }
}

/**
 * A switch for a boolean value.
 */
data class QuickControlSwitch(
    override val id: String,
    @param:DrawableRes override val icon: Int,
    @param:StringRes override val title: Int,
    val value: (GBDevice) -> Boolean,
    val onChange: (GBDevice, Context, Boolean) -> Unit,
    val visible: (GBDevice) -> Boolean = { true },
    override val optional: Boolean = false,
) : QuickControl {
    override fun isVisible(device: GBDevice) = visible(device)

    companion object {
        /**
         * Creates a control for a boolean device preference. A change stores the value and sends
         * the configuration to the device.
         */
        @JvmStatic
        fun forPreference(
            key: String,
            @DrawableRes icon: Int,
            @StringRes title: Int,
            defaultValue: Boolean,
            visible: (GBDevice) -> Boolean = { true },
            optional: Boolean = false,
        ) = QuickControlSwitch(
            id = key,
            icon = icon,
            title = title,
            value = { device -> GBApplication.getDevicePrefs(device).getBoolean(key, defaultValue) },
            onChange = { device, _, value -> QuickControlPreferences.putBoolean(device, key, value) },
            visible = visible,
            optional = optional,
        )
    }
}

/**
 * A row with buttons.
 */
data class QuickControlButtons(
    override val id: String,
    @param:DrawableRes override val icon: Int,
    @param:StringRes override val title: Int,
    val buttons: List<QuickControlButton>,
    val visible: (GBDevice) -> Boolean = { true },
    override val optional: Boolean = false,
) : QuickControl {
    override fun isVisible(device: GBDevice) = visible(device)
}

data class QuickControlButton(
    @param:StringRes val description: Int,
    val icon: (GBDevice) -> Int,
    val onClick: (GBDevice, Context) -> Unit,
)

private object QuickControlPreferences {
    private val LOG = LoggerFactory.getLogger(QuickControlPreferences::class.java)

    fun putString(device: GBDevice, key: String, value: String) {
        LOG.debug("Storing string {} = {} for {}", key, value, device.address)
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit { putString(key, value) }
        GBApplication.deviceService(device).onSendConfiguration(key)
    }

    fun putInt(device: GBDevice, key: String, value: Int) {
        LOG.debug("Storing int {} = {} for {}", key, value, device.address)
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit { putInt(key, value) }
        GBApplication.deviceService(device).onSendConfiguration(key)
    }

    fun putBoolean(device: GBDevice, key: String, value: Boolean) {
        LOG.debug("Storing boolean {} = {} for {}", key, value, device.address)
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit { putBoolean(key, value) }
        GBApplication.deviceService(device).onSendConfiguration(key)
    }
}
