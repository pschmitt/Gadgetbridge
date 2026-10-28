package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.DashboardHost
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentDashboardBinding

/**
 * Owns the action mode, the edit bar, and the page lock of the dashboard edit mode.
 */
class DashboardEditModeController(
    private val activity: AppCompatActivity,
    private val binding: FragmentDashboardBinding,
    private val viewModel: DashboardViewModel,
    private val onFinished: () -> Unit,
) {
    private var actionMode: ActionMode? = null
    private var preserveOnDestroy = false

    private val callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.setTitle(R.string.dashboard_edit)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            setControls(false)
            onFinished()
            if (!preserveOnDestroy) {
                viewModel.onFinishEditing()
            }
            preserveOnDestroy = false
        }
    }

    fun start() {
        if (actionMode != null) return
        actionMode = activity.startSupportActionMode(callback) ?: return
        setControls(true)
    }

    fun startIfEditing() {
        if (viewModel.uiState.value.editState != null) {
            start()
        }
    }

    /**
     * Asks to discard the unsaved changes, and leaves edit mode if the user agrees.
     */
    fun confirmDiscard() {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.workout_template_discard_title)
            .setPositiveButton(R.string.workout_template_discard) { _, _ ->
                viewModel.onDiscardEditing()
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun finish() {
        actionMode?.finish()
    }

    /**
     * Leaves edit mode. Keeps it alive when the activity is recreated, so that it resumes in the new view.
     */
    fun onDestroyView(changingConfigurations: Boolean) {
        preserveOnDestroy = changingConfigurations && viewModel.uiState.value.editState != null
        actionMode?.finish()
    }

    private fun setControls(editing: Boolean) {
        binding.arrowLeft.isInvisible = editing
        binding.arrowRight.isInvisible = editing
        binding.dashboardEditBar.isVisible = editing
        (activity as? DashboardHost)?.onDashboardEditingChanged(editing)
    }
}
