package nodomain.freeyourgadget.gadgetbridge.activities.debug

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.GB

@AndroidEntryPoint
class DatabaseDebugFragment : AbstractDebugFragment() {
    private val viewModel: DatabaseDebugViewModel by viewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.debug_preferences_database, rootKey)

        onClick(PREF_DEBUG_EMPTY_DATABASE) { emptyDatabase() }
        onClick(PREF_DEBUG_DELETE_OLD_DATABASE) { deleteOldActivityDbFile() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect { render(it) } }
                launch { viewModel.events.collect { onEvent(it) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun render(state: DatabaseDebugUiState) {
        state.version?.let {
            findPreference<Preference>(PREF_DEBUG_DATABASE_VERSION)?.summary = it.toString()
        }
        findPreference<Preference>(PREF_DEBUG_DELETE_OLD_DATABASE)!!.apply {
            isVisible = state.showDeleteOldDatabase
            isEnabled = !state.deleteInProgress
        }
        findPreference<Preference>(PREF_DEBUG_EMPTY_DATABASE)!!.isEnabled = !state.deleteInProgress

        val tablesHeader: PreferenceCategory = findPreference(PREF_HEADER_DATABASE_TABLES)!!
        removeDynamicPrefs(tablesHeader)
        for (tableName in state.tables) {
            addDynamicPref(tablesHeader, tableName) {
                goTo(
                    DatabaseTableDebugFragment().apply {
                        arguments = Bundle().apply { putString("tableName", tableName) }
                    }
                )
            }
        }
    }

    private fun onEvent(event: DatabaseDebugEvent) {
        when (event) {
            is DatabaseDebugEvent.DatabaseDeleted -> {
                if (event.success) {
                    GB.toast(
                        requireContext(),
                        getString(R.string.dbmanagementactivity_database_successfully_deleted),
                        Toast.LENGTH_SHORT,
                        GB.INFO
                    )
                    requireActivity().finishAffinity()
                    GBApplication.restart()
                } else {
                    GB.toast(
                        requireContext(),
                        getString(R.string.dbmanagementactivity_db_deletion_failed),
                        Toast.LENGTH_SHORT,
                        GB.INFO
                    )
                }
            }

            is DatabaseDebugEvent.OldDatabaseDeleted -> {
                GB.toast(
                    requireContext(),
                    getString(
                        if (event.success) {
                            R.string.dbmanagementactivity_old_activity_db_successfully_deleted
                        } else {
                            R.string.dbmanagementactivity_old_activity_db_deletion_failed
                        }
                    ),
                    Toast.LENGTH_SHORT,
                    GB.INFO
                )
            }
        }
    }

    private fun emptyDatabase() {
        MaterialAlertDialogBuilder(requireContext())
            .setCancelable(true)
            .setIcon(R.drawable.ic_warning)
            .setTitle(R.string.dbmanagementactivity_delete_activity_data_title)
            .setMessage(R.string.dbmanagementactivity_really_delete_entire_db)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteDatabase() }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
    }

    private fun deleteOldActivityDbFile() {
        MaterialAlertDialogBuilder(requireContext())
            .setCancelable(true)
            .setTitle(R.string.dbmanagementactivity_delete_old_activity_db)
            .setIcon(R.drawable.ic_warning)
            .setMessage(R.string.dbmanagementactivity_delete_old_activitydb_confirmation)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.deleteOldDatabase() }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
    }

    companion object {
        private const val PREF_DEBUG_DATABASE_VERSION = "pref_debug_database_version"
        private const val DANGEROUS_ACTIONS = "dangerous_actions"
        private const val PREF_DEBUG_DELETE_OLD_DATABASE = "pref_debug_delete_old_database"
        private const val PREF_DEBUG_EMPTY_DATABASE = "pref_debug_empty_database"
        private const val PREF_HEADER_DATABASE_TABLES = "pref_header_database_tables"
    }
}
