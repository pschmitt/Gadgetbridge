package nodomain.freeyourgadget.gadgetbridge.activities.debug.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import nodomain.freeyourgadget.gadgetbridge.R
import org.slf4j.LoggerFactory

class DebugPreferenceEditDialog(
    private val context: Context,
    private val sharedPreferences: SharedPreferences,
    private val preference: DebugPreference,
    private val onChanged: Runnable
) {
    @SuppressLint("InflateParams")  // dialog view, no parent
    private val view = LayoutInflater.from(context).inflate(R.layout.dialog_debug_preference_edit, null)
    private val valueSwitch: MaterialSwitch = view.findViewById(R.id.debug_preference_switch)
    private val valueLayout: TextInputLayout = view.findViewById(R.id.debug_preference_value_layout)
    private val valueEdit: TextInputEditText = view.findViewById(R.id.debug_preference_value)

    fun show() {
        val value = preference.value
        view.findViewById<TextView>(R.id.debug_preference_type).text =
            if (value is Set<*>) "Set<String>" else value.javaClass.simpleName

        if (value is Boolean) {
            valueSwitch.visibility = View.VISIBLE
            valueSwitch.isChecked = value
        } else {
            valueLayout.visibility = View.VISIBLE
            valueEdit.inputType = when (value) {
                is Int, is Long -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
                is Float -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED or InputType.TYPE_NUMBER_FLAG_DECIMAL
                else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            }
            valueEdit.setText(if (value is Set<*>) value.joinToString("\n") else value.toString())
            valueEdit.doAfterTextChanged { valueLayout.error = null }
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(preference.key)
            .setView(view)
            .setPositiveButton(R.string.save, null)
            .setNeutralButton(R.string.reset) { _, _ ->
                LOG.debug("Removing preference {}", preference.key)
                sharedPreferences.edit { remove(preference.key) }
                onChanged.run()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        // Replace the default listener, so that the dialog stays open when the value is not valid
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (save()) {
                    dialog.dismiss()
                    onChanged.run()
                } else {
                    valueLayout.error = context.getString(R.string.hydration_add_invalid_value)
                }
            }
        }
        dialog.show()
    }

    private fun save(): Boolean {
        val key = preference.key
        val text = valueEdit.text?.toString().orEmpty()
        LOG.debug("Setting preference {} to {}", key, if (preference.value is Boolean) valueSwitch.isChecked else text)
        when (preference.value) {
            is Boolean -> sharedPreferences.edit { putBoolean(key, valueSwitch.isChecked) }
            is Int -> {
                val newValue = text.trim().toIntOrNull() ?: return false
                sharedPreferences.edit { putInt(key, newValue) }
            }

            is Long -> {
                val newValue = text.trim().toLongOrNull() ?: return false
                sharedPreferences.edit { putLong(key, newValue) }
            }

            is Float -> {
                val newValue = text.trim().toFloatOrNull() ?: return false
                sharedPreferences.edit { putFloat(key, newValue) }
            }

            is Set<*> -> sharedPreferences.edit { putStringSet(key, text.lines().filter { it.isNotEmpty() }.toSet()) }
            else -> sharedPreferences.edit { putString(key, text) }
        }
        return true
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DebugPreferenceEditDialog::class.java)
    }
}
