/*  Copyright (C) 2023-2026 Arjan Schrijver, José Rebelo

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.DashboardCalendarActivity
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.DashboardEditModeController
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.DashboardEvent
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.DashboardGridBinder
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.DashboardNoticesController
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.DashboardUiState
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.DashboardUndoAction
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.DashboardViewModel
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GridDragController
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.WidgetGridLayout
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.WidgetInstanceSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.WidgetPickerActivity
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.showVariantPreviews
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentDashboardBinding
import nodomain.freeyourgadget.gadgetbridge.util.BarShade
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getDevice
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardQuery
import nodomain.freeyourgadget.gadgetbridge.widgets.PlacedWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid
import java.time.ZoneId
import java.util.Date

/**
 * Hosts the widget grid: date header, calendar/settings menu, and the grid the [DashboardGridBinder] renders the
 * placed widgets into, driven by [DashboardViewModel]. In edit mode, the widgets can be moved, added, changed and
 * deleted.
 */
class DashboardFragment : Fragment(), MenuProvider, HeaderShadePage, DashboardGridBinder.Callbacks {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var binder: DashboardGridBinder? = null
    private lateinit var dragController: GridDragController
    private val viewModel: DashboardViewModel by viewModels { DashboardViewModel.Factory }

    private var noticesController: DashboardNoticesController? = null
    private var editModeController: DashboardEditModeController? = null

    private var receiverRegistered = false
    private var snackbar: Snackbar? = null
    private var lastItems: List<PlacedWidget> = emptyList()

    private val pickWidget = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@registerForActivityResult
        val typeId = data.getStringExtra(WidgetPickerActivity.EXTRA_TYPE_ID) ?: return@registerForActivityResult
        val variantId = data.getStringExtra(WidgetPickerActivity.EXTRA_VARIANT_ID) ?: return@registerForActivityResult
        viewModel.onWidgetPicked(typeId, variantId)
    }

    private val calendarLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val timeMillis = result.data!!.getLongExtra(DashboardCalendarActivity.EXTRA_TIMESTAMP, 0)
            if (timeMillis != 0L) {
                viewModel.onDaySelected(timeMillis)
            }
        }
    }

    private val widgetSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.onSettingsClosed()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                GBApplication.ACTION_NEW_DATA -> intent.getDevice()?.let(viewModel::onNewData)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentDashboardBinding.inflate(inflater, container, false)
        _binding = binding
        lastItems = emptyList()

        editModeController = DashboardEditModeController(
            requireActivity() as AppCompatActivity,
            binding,
            viewModel,
        ) { snackbar?.dismiss() }
        noticesController = DashboardNoticesController(
            requireContext(),
            binding.dashboardNotices,
            onRestoreClassicDesign = viewModel::onRestoreClassicDesign,
            onEdit = ::startEditing,
            onAddWidgets = { address ->
                pickWidget.launch(
                    Intent(requireContext(), WidgetPickerActivity::class.java)
                        .putExtra(WidgetPickerActivity.EXTRA_NEW_DEVICE, address)
                )
            },
            onDismissMigration = viewModel::onDismissMigrationNotice,
            onDismissNewWidgets = viewModel::onDismissNewWidgetsNotice,
        )

        binding.dashboardEditBar.setBackgroundColor(MaterialColors.getColor(binding.dashboardEditBar, R.attr.main_bar_bg))
        ViewCompat.setOnApplyWindowInsetsListener(binding.dashboardEditBar) { bar, insets ->
            bar.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom)
            insets
        }
        binding.dashboardDiscard.setOnClickListener { editModeController?.confirmDiscard() }
        binding.dashboardAddWidget.setOnClickListener { viewModel.onAddWidgetClicked() }
        binding.dashboardDone.setOnClickListener { editModeController?.finish() }

        binder = DashboardGridBinder(binding.dashboardGrid).apply {
            callbacks = this@DashboardFragment
            columns = WidgetGrid.columnsForWidth(resources.configuration.screenWidthDp)
        }
        dragController = GridDragController(
            binding.dashboardGrid,
            binding.dashboardScroll,
            binder!!,
        ) { arrangement ->
            viewModel.onArrange(arrangement)
        }

        binding.arrowLeft.setOnClickListener { viewModel.onPreviousDay() }
        binding.arrowRight.setOnClickListener { viewModel.onNextDay() }

        // The pager resumes only the visible page
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        observeViewModel()
        viewModel.onColumnsChanged(binder!!.columns)

        val filter = IntentFilter()
        filter.addAction(GBApplication.ACTION_NEW_DATA)
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, filter)
        receiverRegistered = true

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        if (viewModel.consumeStartEditingRequest()) {
            startEditing()
        } else {
            editModeController?.startIfEditing()
        }
    }

    override fun onDestroyView() {
        editModeController?.onDestroyView(requireActivity().isChangingConfigurations)
        if (receiverRegistered) {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver)
            receiverRegistered = false
        }
        editModeController = null
        noticesController = null
        binder = null
        _binding = null
        super.onDestroyView()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.dashboard_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.dashboard_show_calendar -> {
                val intent = Intent(requireActivity(), DashboardCalendarActivity::class.java)
                val date = viewModel.uiState.value.date
                intent.putExtra(
                    DashboardCalendarActivity.EXTRA_TIMESTAMP,
                    date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                )
                calendarLauncher.launch(intent)
                true
            }

            R.id.dashboard_edit -> {
                startEditing()
                true
            }

            R.id.dashboard_settings -> {
                startActivity(Intent(requireActivity(), DashboardPreferencesActivity::class.java))
                true
            }

            else -> false
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state -> render(state) }
                }
                launch {
                    viewModel.events.collect { event -> handleEvent(event) }
                }
            }
        }
    }

    private fun render(state: DashboardUiState) {
        val binding = _binding ?: return
        val binder = binder ?: return
        binder.cardsEnabled = state.settings.cardsEnabled
        binder.dashboardShowAllDevices = state.settings.showAllDevices
        binder.dashboardDeviceList = state.settings.deviceAddresses
        binder.timestamp = DashboardQuery.forDay(state.date).timeTo

        val items = state.items.map { it.placed }
        if (items != lastItems) {
            lastItems = items
            binder.submitItems(items)
        }
        binder.submitStates(state.items.associate { it.instanceId to it.state })

        renderDate(state, binding)
        noticesController?.submit(state.notices)
        binding.dashboardDiscard.isEnabled = state.editState?.canDiscard == true
        binder.editing = state.editState != null

        if (state.editState != null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            editModeController?.start()
        }
    }

    private fun renderDate(state: DashboardUiState, binding: FragmentDashboardBinding) {
        if (state.isToday) {
            binding.dashboardDate.text = requireContext().getString(R.string.activity_summary_today)
            binding.arrowRight.isEnabled = false
        } else {
            val date = Date.from(state.date.atStartOfDay(ZoneId.systemDefault()).toInstant())
            binding.dashboardDate.text = DateTimeUtils.formatDate(date, DateUtils.FORMAT_SHOW_WEEKDAY)
            binding.arrowRight.isEnabled = true
        }
    }

    private fun handleEvent(event: DashboardEvent) {
        if (_binding == null) return
        when (event) {
            is DashboardEvent.WidgetAdded -> {
                binding.dashboardGrid.post {
                    if (_binding == null) return@post
                    binder?.cardOf(event.instanceId)?.doOnLayout { card ->
                        binding.dashboardScroll.smoothScrollTo(0, binding.dashboardGrid.top + card.top)
                    }
                    binder?.highlight(event.instanceId)
                }
            }

            DashboardEvent.OpenWidgetPicker -> {
                pickWidget.launch(Intent(requireContext(), WidgetPickerActivity::class.java))
            }

            DashboardEvent.NoWidgetsAvailable -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.widgets_add_widget)
                    .setMessage(R.string.no_supported_devices_found)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }

            is DashboardEvent.ShowUndo -> showUndo(event.action)

            is DashboardEvent.OpenWidgetSettings -> widgetSettingsLauncher.launch(
                WidgetInstanceSettingsActivity.createIntent(requireContext(), event.columns, event.instance)
            )
        }
    }

    private fun showUndo(action: DashboardUndoAction) {
        if (_binding == null) return
        val message = when (action) {
            DashboardUndoAction.DELETE_WIDGET -> R.string.widgets_deleted
            DashboardUndoAction.RESTORE_CLASSIC_DESIGN -> R.string.dashboard_notice_design_restored
        }
        snackbar?.dismiss()
        snackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                when (action) {
                    DashboardUndoAction.DELETE_WIDGET -> viewModel.onUndoDelete()
                    DashboardUndoAction.RESTORE_CLASSIC_DESIGN -> viewModel.onUndoRestoreClassicDesign()
                }
            }
            .apply {
                if (action == DashboardUndoAction.DELETE_WIDGET) {
                    anchorView = binding.dashboardEditBar
                }
                show()
            }
    }

    private fun startEditing() {
        viewModel.onStartEditing()
        editModeController?.start()
    }

    override fun onLongPress(item: PlacedWidget, card: View) {
        startEditing()
        dragController.start(card)
    }

    override fun onEditClick(item: PlacedWidget, button: View) {
        val instanceId = item.instance.instanceId
        val popup = PopupMenu(requireContext(), button)
        popup.inflate(R.menu.dashboard_widget_edit_menu)
        popup.menu.findItem(R.id.dashboard_widget_variant).isVisible = item.widget.variants.size > 1
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.dashboard_widget_variant -> showVariantSheet(item)
                R.id.dashboard_widget_settings -> viewModel.onOpenSettings(instanceId)

                R.id.dashboard_widget_duplicate -> viewModel.onDuplicate(instanceId)

                R.id.dashboard_widget_delete -> viewModel.onDelete(instanceId)
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        popup.show()
    }

    override fun onMove(item: PlacedWidget, offset: Int) {
        viewModel.onMove(item.instance.instanceId, offset)
    }

    /**
     * Shows the variants of [item] in a bottom sheet.
     */
    @SuppressLint("InflateParams")  // The bottom sheet sets the layout params of its content view
    private fun showVariantSheet(item: PlacedWidget) {
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dashboard_widget_edit_sheet, null)

        view.findViewById<TextView>(R.id.dashboard_edit_title).text = item.widget.displayName(requireContext())
        val variants = view.findViewById<WidgetGridLayout>(R.id.dashboard_edit_variants)
        showVariantPreviews(variants, item.widget, item.variant.id) { variant ->
            viewModel.onVariantSelected(item.instance.instanceId, variant.id)
            sheet.dismiss()
        }

        sheet.setContentView(view)
        sheet.behavior.maxWidth = resources.displayMetrics.widthPixels
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.show()
    }

    override fun getHeaderShade(): View? {
        val shade = _binding?.root?.findViewById<View>(R.id.dashboard_date_row_shade) ?: return null
        return if (BarShade.continuesToolbar(shade.context, R.attr.datestep_row_bg)) shade else null
    }
}
