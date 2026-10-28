package nodomain.freeyourgadget.gadgetbridge.activities.notifications.apps

import android.os.Bundle
import android.view.MenuItem
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityNotificationAppReplacementBinding
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppReplacement
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppReplacementTarget
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppRules

/**
 * The editor for the replacement rule at [EXTRA_INDEX] of the app in [EXTRA_PACKAGE_NAME].
 */
class NotificationAppReplacementActivity : AbstractGBActivity() {
    private lateinit var binding: ActivityNotificationAppReplacementBinding
    private lateinit var appPackageName: String
    private var index = NEW

    private val targetButtons = mapOf(
        NotificationAppReplacementTarget.TITLE to R.id.notification_app_replacement_target_title,
        NotificationAppReplacementTarget.BODY to R.id.notification_app_replacement_target_body,
        NotificationAppReplacementTarget.BOTH to R.id.notification_app_replacement_target_both,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extraPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (extraPackageName == null) {
            finish()
            return
        }
        appPackageName = extraPackageName
        index = intent.getIntExtra(EXTRA_INDEX, NEW)

        binding = ActivityNotificationAppReplacementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setTitle(if (index == NEW) R.string.notification_apps_add_text_replacement else R.string.notification_apps_edit_text_replacement)
        supportActionBar?.subtitle = intent.getCharSequenceExtra(EXTRA_TITLE)

        binding.notificationAppReplacementPattern.doAfterTextChanged { refresh() }
        binding.notificationAppReplacementReplacement.doAfterTextChanged { refresh() }
        binding.notificationAppReplacementSample.doAfterTextChanged { refresh() }
        binding.notificationAppReplacementTarget.addOnButtonCheckedListener { _, _, _ -> refresh() }
        binding.notificationAppReplacementIgnoreCase.setOnCheckedChangeListener { _, _ -> refresh() }
        binding.fabSave.setOnClickListener { save() }

        if (savedInstanceState != null) {
            refresh()
        } else if (index == NEW) {
            show(NotificationAppReplacement(pattern = ""))
        } else {
            lifecycleScope.launch {
                val stored = NotificationAppEditor.configs.first()[appPackageName]?.replacements?.getOrNull(index)
                if (stored == null) {
                    finish()
                } else {
                    show(stored)
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun show(replacement: NotificationAppReplacement) {
        binding.notificationAppReplacementPattern.setText(replacement.pattern)
        binding.notificationAppReplacementReplacement.setText(replacement.replacement)
        binding.notificationAppReplacementTarget.check(targetButtons.getValue(replacement.target))
        binding.notificationAppReplacementIgnoreCase.isChecked = replacement.ignoreCase
        binding.notificationAppReplacementEnabled.isChecked = replacement.enabled
        refresh()
    }

    private fun current() = NotificationAppReplacement(
        pattern = binding.notificationAppReplacementPattern.text?.toString().orEmpty(),
        replacement = binding.notificationAppReplacementReplacement.text?.toString().orEmpty(),
        target = targetButtons.entries.firstOrNull { it.value == binding.notificationAppReplacementTarget.checkedButtonId }?.key
            ?: NotificationAppReplacementTarget.BOTH,
        ignoreCase = binding.notificationAppReplacementIgnoreCase.isChecked,
        enabled = binding.notificationAppReplacementEnabled.isChecked,
    )

    private fun refresh() {
        val value = current()
        val sample = binding.notificationAppReplacementSample.text?.toString().orEmpty()
        val patternError = if (value.pattern.isEmpty()) null else NotificationAppRules.regexError(value.pattern)
        val pattern =
            if (patternError == null) NotificationAppRules.compile(value.pattern, value.ignoreCase, 0) else null
        val replacementError = pattern?.let { NotificationAppRules.replacementError(value.pattern, value.replacement) }
        val result = if (pattern != null && replacementError == null) {
            pattern.matcher(sample).replaceAll(value.replacement)
        } else {
            sample
        }
        binding.notificationAppReplacementPatternLayout.error = patternError
        binding.notificationAppReplacementReplacementLayout.error = replacementError
        binding.notificationAppReplacementResult.text = result
        if (value.pattern.isNotEmpty() && patternError == null && replacementError == null) {
            binding.fabSave.show()
        } else {
            binding.fabSave.hide()
        }
    }

    private fun save() {
        val edited = current()
        val editedIndex = index
        NotificationAppEditor.update(appPackageName) { config ->
            if (editedIndex == NEW) {
                config.copy(replacements = config.replacements + edited)
            } else {
                config.copy(replacements = config.replacements.mapIndexed { i, r -> if (i == editedIndex) edited else r })
            }
        }
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "packageName"
        const val EXTRA_TITLE = "title"

        /**
         * The position of the rule app's rule replacement rules list, or [NEW] for a new rule.
         */
        const val EXTRA_INDEX = "index"

        const val NEW = -1
    }
}
