package nodomain.freeyourgadget.gadgetbridge.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlButtons
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlDialog
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControl
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlTrailingAction
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlToggleGroup
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlSlider
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlSwitch
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import kotlin.math.roundToInt

object QuickControlBinder {
    @JvmStatic
    fun bind(
        container: LinearLayout,
        controls: List<QuickControl>,
        device: GBDevice,
        context: Context,
    ) {
        // The tag is true while the user drags a slider. Avoid a rebind, which would move the slider away.
        if (container.tag == true) {
            return
        }

        val shown = device.isInitialized && GBApplication.getDevicePrefs(device)
            .getBoolean(DeviceSettingsPreferenceConst.PREF_DEVICE_CARD_SHOW_QUICK_CONTROLS, true)
        val visibleControls = if (shown) controls.filter { it.isVisible(device) } else emptyList()

        container.removeAllViews()
        container.isVisible = visibleControls.isNotEmpty()

        // A rebind in a touch callback removes the touch target. The parent then sends a cancel event to the
        // removed slider, and the slider calls its listeners again while the container removes its children.
        // Before the posted rebind runs, the adapter can bind the card to a folder, which hides the container.
        val rebind: () -> Unit = {
            container.post {
                if (container.isVisible) {
                    bind(container, controls, device, context)
                }
            }
        }
        val inflater = LayoutInflater.from(context)
        for (control in visibleControls) {
            val row = when (control) {
                is QuickControlToggleGroup -> bindToggleGroup(inflater, container, control, device, context, rebind)
                is QuickControlDialog -> bindDialog(inflater, container, control, device, context, rebind)
                is QuickControlSlider -> bindSlider(inflater, container, control, device, context, rebind)
                is QuickControlSwitch -> bindSwitch(inflater, container, control, device, context, rebind)
                is QuickControlButtons -> bindButtons(inflater, container, control, device, context, rebind)
            }
            container.addView(row)
        }
    }

    private fun bindToggleGroup(
        inflater: LayoutInflater,
        container: LinearLayout,
        control: QuickControlToggleGroup,
        device: GBDevice,
        context: Context,
        rebind: () -> Unit,
    ): View {
        val row = inflater.inflate(R.layout.quick_control_segmented, container, false)
        val group = row.findViewById<MaterialButtonToggleGroup>(R.id.quick_control_segments)
        val current = control.value(device)
        for (option in control.options) {
            val value = option.value
            val button = inflater.inflate(R.layout.quick_control_segment, group, false) as MaterialButton
            button.id = View.generateViewId()
            button.text = option.label(context)
            if (option.icon != 0) {
                button.setIconResource(option.icon)
            }
            group.addView(button)
            if (value == current) {
                group.check(button.id)
            }
            button.setOnClickListener {
                if (value != current) {
                    control.onChange(device, context, value)
                    rebind()
                }
            }
        }
        bindTrailingAction(row, control.trailingAction, device, context, current)
        return row
    }

    private fun bindDialog(
        inflater: LayoutInflater,
        container: LinearLayout,
        control: QuickControlDialog,
        device: GBDevice,
        context: Context,
        rebind: () -> Unit,
    ): View {
        val row = inflater.inflate(R.layout.quick_control_dialog, container, false)
        row.findViewById<ImageView>(R.id.quick_control_icon).setImageResource(control.icon)
        val current = control.value(device)
        val labels = control.options.map { it.label(context) }
        val currentIndex = control.options.indexOfFirst { it.value == current }
        row.findViewById<TextView>(R.id.quick_control_value).text =
            if (currentIndex >= 0) labels[currentIndex] else current
        row.findViewById<View>(R.id.quick_control_dialog_button).setOnClickListener {
            MaterialAlertDialogBuilder(context)
                .setTitle(control.title)
                .setSingleChoiceItems(labels.toTypedArray(), currentIndex) { dialog, which ->
                    dialog.dismiss()
                    val value = control.options[which].value
                    if (value != current) {
                        control.onChange(device, context, value)
                        rebind()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        bindTrailingAction(row, control.trailingAction, device, context, current)
        return row
    }

    private fun bindSlider(
        inflater: LayoutInflater,
        container: LinearLayout,
        control: QuickControlSlider,
        device: GBDevice,
        context: Context,
        rebind: () -> Unit,
    ): View {
        val row = inflater.inflate(R.layout.quick_control_slider, container, false)
        row.findViewById<ImageView>(R.id.quick_control_icon).setImageResource(control.icon)
        val slider = row.findViewById<Slider>(R.id.quick_control_slider)
        val current = control.value(device).coerceIn(control.min, control.max)
        val progress = row.findViewById<LinearProgressIndicator>(R.id.quick_control_progress)
        progress.max = control.max - control.min
        slider.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            marginStart -= slider.trackSidePadding
            marginEnd -= slider.trackSidePadding
        }
        slider.valueFrom = control.min.toFloat()
        slider.valueTo = control.max.toFloat()
        slider.stepSize = control.step.toFloat()
        // Ensure the value falls on a step
        slider.value = (control.min + (current - control.min) / control.step * control.step).toFloat()
        slider.contentDescription = context.getString(control.title)
        slider.setLabelFormatter { control.formatValue(context, it.toInt()) }
        val valueText = row.findViewById<TextView>(R.id.quick_control_value)
        // A constant width keeps the track length constant while the user drags
        valueText.minWidth = maxOf(
            valueText.paint.measureText(control.formatValue(context, control.min)),
            valueText.paint.measureText(control.formatValue(context, control.max)),
        ).roundToInt()
        valueText.text = control.formatValue(context, slider.value.toInt())
        progress.progress = slider.value.toInt() - control.min
        slider.addOnChangeListener { _, value, fromUser ->
            valueText.text = control.formatValue(context, value.toInt())
            progress.progress = value.toInt() - control.min
            // A change from the keyboard or from accessibility services does not start a touch
            if (fromUser && (control.continuous || container.tag != true)) {
                control.onChange(device, context, value.toInt())
            }
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                container.tag = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                if (container.tag != true) {
                    return
                }
                container.tag = null
                if (!control.continuous) {
                    control.onChange(device, context, slider.value.toInt())
                }
                rebind()
            }
        })
        bindTrailingAction(row, control.trailingAction, device, context, current)
        return row
    }

    private fun bindSwitch(
        inflater: LayoutInflater,
        container: LinearLayout,
        control: QuickControlSwitch,
        device: GBDevice,
        context: Context,
        rebind: () -> Unit,
    ): View {
        val row = inflater.inflate(R.layout.quick_control_switch, container, false)
        row.findViewById<ImageView>(R.id.quick_control_icon).setImageResource(control.icon)
        row.findViewById<TextView>(R.id.quick_control_title).setText(control.title)
        val switch = row.findViewById<MaterialSwitch>(R.id.quick_control_switch)
        switch.isChecked = control.value(device)
        switch.setOnCheckedChangeListener { _, isChecked ->
            control.onChange(device, context, isChecked)
            rebind()
        }
        row.findViewById<View>(R.id.quick_control_switch_button).setOnClickListener { switch.toggle() }
        return row
    }

    private fun bindButtons(
        inflater: LayoutInflater,
        container: LinearLayout,
        control: QuickControlButtons,
        device: GBDevice,
        context: Context,
        rebind: () -> Unit,
    ): View {
        val row = inflater.inflate(R.layout.quick_control_buttons, container, false) as LinearLayout
        for (button in control.buttons) {
            val view = inflater.inflate(R.layout.quick_control_button, row, false) as MaterialButton
            view.setIconResource(button.icon(device))
            val description = context.getString(button.description)
            view.contentDescription = description
            TooltipCompat.setTooltipText(view, description)
            view.setOnClickListener {
                button.onClick(device, context)
                rebind()
            }
            row.addView(view)
        }
        return row
    }

    private fun <T> bindTrailingAction(
        row: View,
        action: QuickControlTrailingAction<T>?,
        device: GBDevice,
        context: Context,
        value: T,
    ) {
        val button = row.findViewById<MaterialButton>(R.id.quick_control_trailing_action)
        if (action == null || !action.visible(device, value)) {
            button.isVisible = false
            return
        }
        button.isVisible = true
        button.setIconResource(action.icon)
        val description = context.getString(action.description)
        button.contentDescription = description
        TooltipCompat.setTooltipText(button, description)
        button.setOnClickListener { action.onClick(device, context) }
    }
}
