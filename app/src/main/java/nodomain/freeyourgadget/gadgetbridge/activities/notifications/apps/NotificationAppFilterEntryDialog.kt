package nodomain.freeyourgadget.gadgetbridge.activities.notifications.apps

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Typeface
import android.os.Bundle
import android.view.WindowManager
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.databinding.DialogNotificationAppFilterEntryBinding
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppFilterSyntax
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppRules

class NotificationAppFilterEntryDialog : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val arguments = requireArguments()
        val packageName = requireNotNull(arguments.getString(KEY_PACKAGE_NAME))
        val syntax = NotificationAppFilterSyntax.valueOf(requireNotNull(arguments.getString(KEY_SYNTAX)))
        val index = arguments.getInt(KEY_INDEX, NEW)
        val binding = DialogNotificationAppFilterEntryBinding.inflate(layoutInflater)
        binding.notificationAppFilterEntryLayout.setHint(syntax.entryHint)
        if (syntax == NotificationAppFilterSyntax.REGEX) {
            binding.notificationAppFilterEntry.typeface = Typeface.MONOSPACE
        }
        if (savedInstanceState == null) {
            val entry = arguments.getString(KEY_ENTRY).orEmpty()
            binding.notificationAppFilterEntry.setText(entry)
            binding.notificationAppFilterEntry.setSelection(entry.length)
        }
        binding.notificationAppFilterEntry.requestFocus()

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(syntax.entriesTitle)
            .setView(binding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                // We trim spaces in words-mode, but not regex since spaces are meaningful there
                val text = binding.notificationAppFilterEntry.text?.toString().orEmpty()
                val entry = if (syntax == NotificationAppFilterSyntax.WORDS) text.trim() else text
                NotificationAppEditor.update(packageName) { config ->
                    val entries = config.filter.entries
                    val edited = if (index == NEW || index !in entries.indices) {
                        entries + entry
                    } else {
                        entries.mapIndexed { i, e -> if (i == index) entry else e }
                    }
                    config.copy(filter = config.filter.copy(entries = edited))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        fun refresh() {
            val text = binding.notificationAppFilterEntry.text?.toString().orEmpty()
            val error = if (syntax == NotificationAppFilterSyntax.REGEX) NotificationAppRules.regexError(text) else null
            binding.notificationAppFilterEntryLayout.error = error
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = text.isNotBlank() && error == null
        }
        binding.notificationAppFilterEntry.doAfterTextChanged { refresh() }
        dialog.setOnShowListener { refresh() }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        return dialog
    }

    companion object {
        const val TAG = "NotificationAppFilterEntryDialog"

        /**
         * The index for a new entry.
         */
        const val NEW = -1

        private const val KEY_PACKAGE_NAME = "packageName"
        private const val KEY_SYNTAX = "syntax"
        private const val KEY_INDEX = "index"
        private const val KEY_ENTRY = "entry"

        fun newInstance(
            packageName: String,
            syntax: NotificationAppFilterSyntax,
            index: Int,
            entry: String
        ): NotificationAppFilterEntryDialog {
            val dialog = NotificationAppFilterEntryDialog()
            dialog.arguments = Bundle().apply {
                putString(KEY_PACKAGE_NAME, packageName)
                putString(KEY_SYNTAX, syntax.name)
                putInt(KEY_INDEX, index)
                putString(KEY_ENTRY, entry)
            }
            return dialog
        }
    }
}
