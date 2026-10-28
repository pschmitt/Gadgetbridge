package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.view.MenuProvider
import androidx.core.view.get
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ActivitySummariesFilter
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.statistics.WorkoutStatisticsActivity
import nodomain.freeyourgadget.gadgetbridge.adapter.WorkoutSummariesAdapter
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.WorkoutFilterUtils
import nodomain.freeyourgadget.gadgetbridge.util.healthconnect.HealthConnectWorkoutDeletion
import nodomain.freeyourgadget.gadgetbridge.util.healthconnect.HealthConnectWorkoutSync
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.applyAccentColors
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getSerializableCompat
import org.slf4j.LoggerFactory
import java.util.BitSet
import java.util.Calendar
import java.util.Locale

/**
 * The workout list itself: dashboard, summaries, filtering, selection / action mode,
 * deletion, export and track fetching. Hosted by [WorkoutsFragment] and [WorkoutListActivity].
 */
class WorkoutListFragment : Fragment() {
    private val viewModel: WorkoutListViewModel by viewModels()

    private var activityKindMap = HashMap<String, ActivityKind>(0)
    private var activityFilter = 0
    private var dateFromFilter = 0L
    private var dateToFilter = 0L
    private var deviceFilter = 0L
    private var initialDeviceFilter = 0L
    private var itemsFilter: List<Long>? = null
    private var nameContainsFilter: String? = null
    private var gbDevices = mutableListOf<GBDevice>()
    private var selectedItems: BitSet? = null
    private var swipeLayout: SwipeRefreshLayout? = null
    private var summariesAdapter: WorkoutSummariesAdapter? = null
    private var actionMode: ActionMode? = null

    private val activityDetailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == WorkoutDetailsActivity.RESULT_WORKOUT_CHANGED) {
                // FIXME we could try to only update the workouts that changed
                refresh()
            }
        }

    private val activityFilterLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            result.data?.extras?.let { bundle ->
                activityFilter = bundle.getInt("activityFilter", 0)
                dateFromFilter = bundle.getLong("dateFromFilter", 0)
                dateToFilter = bundle.getLong("dateToFilter", 0)
                deviceFilter = bundle.getLong("deviceFilter", 0)
                nameContainsFilter = bundle.getString("nameContainsFilter")
                itemsFilter = bundle.getSerializableCompat<ArrayList<Long>>("itemsFilter")
                refresh()
            }
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                GBDevice.ACTION_DEVICE_CHANGED -> {
                    val device = BundleCompat.getParcelable(
                        intent.extras ?: Bundle.EMPTY,
                        GBDevice.EXTRA_DEVICE,
                        GBDevice::class.java
                    )
                    if (device == null) {
                        LOG.error("Got device changed without device")
                        return
                    }
                    if (!gbDevices.contains(device)) {
                        return
                    }
                    val swipe = swipeLayout ?: return
                    if (device.isBusy) {
                        swipe.isRefreshing = true
                    } else {
                        val wasBusy = swipe.isRefreshing
                        swipe.isRefreshing = false
                        if (wasBusy) {
                            refresh()
                        }
                    }
                }
                // ExploreSync (and other long-running fetches) hold the
                // device busy for the whole catalog walk, so the
                // busy→idle edge above only fires at session end.
                // Refresh on every per-line flush as well so newly
                // imported activities appear in the list right away.
                // Pass silent=true so the dashboard's loading shimmer
                // and swipe-refresh spinner don't blink on every
                // incremental update.
                GBApplication.ACTION_NEW_DATA -> refresh(silent = true)
                HealthConnectWorkoutSync.ACTION_STATE_CHANGED -> refresh(silent = true)
                else -> LOG.warn("Got unexpected action {}", intent.action)
            }
        }
    }

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.activity_list_menu, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.activity_action_statistics -> {
                    val intent = Intent(requireContext(), WorkoutStatisticsActivity::class.java)
                    if (deviceFilter != ActivitySummariesFilter.ALL_DEVICES) {
                        val device = DeviceHelper.getInstance().findDeviceByDatabaseId(deviceFilter)
                        intent.putExtra(GBDevice.EXTRA_DEVICE, device)
                    }
                    startActivity(intent)
                    true
                }

                R.id.activity_action_manage_timestamp -> {
                    resetFetchTimestampToChosenDate()
                    true
                }

                R.id.activity_action_filter -> {
                    runFilterActivity()
                    true
                }

                else -> false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val singleDevice = arguments?.let { BundleCompat.getParcelable(it, GBDevice.EXTRA_DEVICE, GBDevice::class.java) }
        if (singleDevice != null) {
            gbDevices.add(singleDevice)
            deviceFilter = getDeviceId(singleDevice)
        }
        if (gbDevices.isEmpty()) {
            gbDevices.addAll(GBApplication.app().deviceManager.devices)
            deviceFilter = ActivitySummariesFilter.ALL_DEVICES
        }
        // Store the initial deviceFilter for the "reset filter" action
        initialDeviceFilter = deviceFilter

        // Load and apply saved quick filter
        applySavedQuickFilter()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Same layout the list screen has always used (recycler, swipe layout and FAB).
        return inflater.inflate(R.layout.activity_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val filterLocal = IntentFilter().apply {
            addAction(GBDevice.ACTION_DEVICE_CHANGED)
            addAction(GBApplication.ACTION_NEW_DATA)
            addAction(HealthConnectWorkoutSync.ACTION_STATE_CHANGED)
        }
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, filterLocal)

        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        setupViews(view)
        setupViewModel()
    }

    override fun onDestroyView() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver)
        stopActionMode()
        swipeLayout = null
        summariesAdapter = null
        selectedItems = null
        super.onDestroyView()
    }

    private fun setupViews(root: View) {
        val recycler = root.findViewById<RecyclerView>(R.id.itemListView)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        val workoutSummariesAdapter = WorkoutSummariesAdapter(
            requireContext(),
            activityFilter,
            dateFromFilter,
            dateToFilter,
            nameContainsFilter,
            deviceFilter,
            itemsFilter
        )
        selectedItems = workoutSummariesAdapter.selectedItems

        workoutSummariesAdapter.setOnItemClickListener { position ->
            if (!selectedItems!!.isEmpty) {
                selectedItems!!.set(position, !selectedItems!!.get(position))
                workoutSummariesAdapter.notifyItemChanged(position)
                if (!selectedItems!!.isEmpty) {
                    startActionMode()
                } else {
                    stopActionMode()
                }
                return@setOnItemClickListener
            }
            if (position == 0) return@setOnItemClickListener // item 0 is empty for dashboard
            val summary = workoutSummariesAdapter.getItem(position)
            if (summary != null) {
                try {
                    showActivityDetail(position - 1)
                } catch (e: Exception) {
                    GB.toast(
                        requireContext().applicationContext,
                        "Unable to display Activity Detail: ${e.localizedMessage}",
                        Toast.LENGTH_LONG,
                        GB.ERROR,
                        e
                    )
                }
            }
        }

        workoutSummariesAdapter.setOnItemLongClickListener { position ->
            selectedItems!!.set(position, !selectedItems!!.get(position))
            workoutSummariesAdapter.notifyItemChanged(position)

            if (!selectedItems!!.isEmpty) {
                startActionMode()
            } else {
                stopActionMode()
            }
        }

        recycler.adapter = workoutSummariesAdapter
        summariesAdapter = workoutSummariesAdapter

        // The dashboard row gets a notifyItemChanged on every silent
        // refresh during sync; the default ChangeAnimator cross-fades
        // that, which reads as a distracting opacity blink. Disable
        // just the change animation — add/remove animations still run.
        (recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        val swipe = root.findViewById<SwipeRefreshLayout>(R.id.list_activity_swipe_layout)
        swipeLayout = swipe
        swipe.setOnRefreshListener {
            if (GBApplication.getPrefs().refreshOnSwipe()) {
                fetchTrackData()
            } else {
                swipe.isRefreshing = false
            }
        }
        swipe.isEnabled = GBApplication.getPrefs().refreshOnSwipe()

        val fab: FloatingActionButton = root.findViewById(R.id.fab)
        fab.setOnClickListener { fetchTrackData() }
        // The FAB comes from the layout shared by every list screen, so it is only recolored here.
        fab.applyAccentColors()
    }

    private fun setupViewModel() {
        viewModel.summaries.observe(viewLifecycleOwner) { summaries ->
            // Set before the items, so the first bind of every row already has its indicator.
            summariesAdapter?.uploadStatuses = viewModel.uploadStatuses.value.orEmpty()
            summariesAdapter?.healthConnectFailures = viewModel.healthConnectFailures.value.orEmpty()
            summariesAdapter?.setItems(summaries, true)
            activityKindMap = fillKindMap()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            swipeLayout?.isRefreshing = isLoading
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                GB.toast(requireContext(), it, Toast.LENGTH_LONG, GB.ERROR)
            }
        }

        viewModel.isDashboardLoading.observe(viewLifecycleOwner) { isLoading ->
            summariesAdapter?.isDashboardLoading = isLoading
            summariesAdapter?.notifyItemChanged(0)
        }

        viewModel.dashboardStats.observe(viewLifecycleOwner) { stats ->
            summariesAdapter?.dashboardStats = stats
            summariesAdapter?.notifyItemChanged(0)
        }

        refresh()
    }

    private fun stopActionMode() {
        actionMode?.finish()
        actionMode = null
    }

    private fun startActionMode() {
        val numSelected = selectedItems!!.cardinality()

        if (actionMode != null) {
            // already in action mode
            actionMode?.title = getString(R.string.number_selected_items, numSelected)
            return
        }

        actionMode = requireActivity().startActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.title = getString(R.string.number_selected_items, numSelected)
                requireActivity().menuInflater.inflate(R.menu.activity_list_context_menu, menu)

                // For some reason the icons in the context menu are not tinted
                // by the theme, so we do it manually here
                val typedValue = android.util.TypedValue()
                requireActivity().theme.resolveAttribute(R.attr.actionmenu_icon_color, typedValue, true)
                val iconColor = typedValue.data
                for (i in 0 until menu.size) {
                    menu[i].icon?.setTint(iconColor)
                }

                view?.findViewById<View>(R.id.fab)?.visibility = View.INVISIBLE
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

            override fun onActionItemClicked(mode: ActionMode, menuItem: MenuItem): Boolean {
                val adapter = summariesAdapter
                val selected = selectedItems
                if (adapter == null || selected == null) {
                    mode.finish()
                    actionMode = null
                    return false
                }
                when (menuItem.itemId) {
                    R.id.activity_action_delete -> {
                        val toDelete = ArrayList<BaseActivitySummary>()
                        for (i in 0 until selected.length()) {
                            if (selected.get(i)) {
                                adapter.getItem(i)?.let { toDelete.add(it) }
                            }
                        }

                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.sports_activity_confirm_delete_title, toDelete.size))
                            .setMessage(getString(R.string.sports_activity_confirm_delete_description, toDelete.size))
                            .setIcon(R.drawable.ic_delete_forever)
                            .setPositiveButton(R.string.yes) { _, _ -> deleteItems(toDelete) }
                            .setNegativeButton(R.string.no, null)
                            .show()

                        return true
                    }

                    R.id.activity_action_export -> {
                        val toExport = ArrayList<BaseActivitySummary>()
                        for (i in 0 until selected.length()) {
                            if (selected.get(i)) {
                                adapter.getItem(i)?.let { toExport.add(it) }
                            }
                        }
                        showExportDialog(toExport)
                        return true
                    }

                    R.id.activity_action_select_all -> {
                        for (i in 1 until adapter.itemCount - 1) {
                            if (!selected.get(i)) {
                                selected.set(i, true)
                                adapter.notifyItemChanged(i)
                            }
                        }
                        mode.title = getString(R.string.number_selected_items, selected.cardinality())
                        return true
                    }

                    R.id.activity_action_addto_filter -> {
                        val toFilter = ArrayList<Long>()
                        for (i in 0 until selected.length()) {
                            if (selected.get(i)) {
                                adapter.getItem(i)?.id?.let { id ->
                                    toFilter.add(id)
                                }
                            }
                        }
                        itemsFilter = toFilter
                        refresh()
                        return true
                    }
                }
                mode.finish()
                actionMode = null
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null
                val selected = selectedItems
                if (selected != null) {
                    for (i in 0 until selected.length()) {
                        if (selected.get(i)) {
                            selected.set(i, false)
                            summariesAdapter?.notifyItemChanged(i)
                        }
                    }
                }
                view?.findViewById<View>(R.id.fab)?.visibility = View.VISIBLE
            }
        })
    }

    private fun fillKindMap(): HashMap<String, ActivityKind> {
        val context = requireContext()
        val newMap = HashMap<String, ActivityKind>(0)
        newMap[getString(R.string.activity_summaries_all_activities)] = ActivityKind.UNKNOWN

        summariesAdapter?.items?.forEach { item ->
            val activityName = ActivityKind.fromCode(item.activityKind).getLabel(context)
            if (!newMap.containsKey(activityName) && item.activityKind != 0) {
                newMap[activityName] = ActivityKind.fromCode(item.activityKind)
            }
        }
        return newMap
    }

    private fun resetFetchTimestampToChosenDate() {
        if (deviceFilter == ActivitySummariesFilter.ALL_DEVICES) {
            GB.toast(
                requireContext(),
                getString(R.string.reset_fetch_timestamp_select_device),
                Toast.LENGTH_LONG,
                GB.INFO
            )
            return
        }
        val device = DeviceHelper.getInstance().findDeviceByDatabaseId(deviceFilter)
        if (device == null) {
            GB.toast(
                requireContext(),
                getString(R.string.reset_fetch_timestamp_select_device),
                Toast.LENGTH_LONG,
                GB.INFO
            )
            return
        }
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()
        datePicker.addOnPositiveButtonClickListener { selection ->
            val selectedDate = DateTimeUtils.utcMillisToLocalDate(selection)
            val date = Calendar.getInstance().apply {
                set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
            }
            val timestamp = date.timeInMillis - 1000
            GBApplication.getDeviceSpecificSharedPrefs(device.address).edit().apply {
                remove("lastSportsActivityTimeMillis")
                putLong("lastSportsActivityTimeMillis", timestamp)
                apply()
            }
        }
        datePicker.show(childFragmentManager, "WORKOUT_FETCH_DATE_PICKER")
    }

    private fun deleteItems(items: List<BaseActivitySummary>) {
        val healthConnectSnapshots = mutableListOf<HealthConnectWorkoutDeletion.WorkoutSummarySnapshot>()
        items.forEach { item ->
            val healthConnectSnapshot = HealthConnectWorkoutDeletion.snapshot(item)
            try {
                item.delete()
                healthConnectSnapshot?.let { healthConnectSnapshots.add(it) }
            } catch (_: Exception) {
                // pass delete error
            }
        }
        HealthConnectWorkoutDeletion.deleteWorkoutsFromHealthConnect(requireContext(), healthConnectSnapshots)
        refresh()
    }

    private fun fetchTrackData() {
        gbDevices.forEach { device ->
            if (device.isInitialized && !device.isBusy) {
                swipeLayout?.isRefreshing = true
                GBApplication.deviceService(device).onFetchRecordedData(RecordedDataTypes.TYPE_GPS_TRACKS)
            } else {
                swipeLayout?.isRefreshing = false
                if (!device.isInitialized && deviceFilter != ActivitySummariesFilter.ALL_DEVICES) {
                    GB.toast(
                        requireContext(),
                        getString(R.string.device_not_connected),
                        Toast.LENGTH_SHORT,
                        GB.ERROR
                    )
                }
            }
        }
    }

    private fun showExportDialog(summaries: List<BaseActivitySummary>) {
        if (summaries.isEmpty()) return

        val prefs = GBApplication.getPrefs()
        val view = layoutInflater.inflate(R.layout.dialog_workout_export, null)
        val boxes = mapOf(
            WorkoutBatchExporter.Part.FIT to view.findViewById<CheckBox>(R.id.workout_export_fit),
            WorkoutBatchExporter.Part.GPX to view.findViewById<CheckBox>(R.id.workout_export_gpx),
            WorkoutBatchExporter.Part.RAW to view.findViewById<CheckBox>(R.id.workout_export_raw)
        )

        fun prefKey(part: WorkoutBatchExporter.Part) = "workout_export_" + part.name.lowercase(Locale.ROOT)
        boxes.forEach { (part, box) ->
            box.isChecked = prefs.getBoolean(prefKey(part), part != WorkoutBatchExporter.Part.RAW)
        }
        val shareAs = view.findViewById<RadioGroup>(R.id.workout_export_share_as)
        shareAs.check(
            if (prefs.getBoolean(PREF_EXPORT_AS_ZIP, false)) R.id.workout_export_zip else R.id.workout_export_separate
        )

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.workout_export_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val asZip = shareAs.checkedRadioButtonId == R.id.workout_export_zip
                prefs.preferences.edit().apply {
                    boxes.forEach { (part, box) -> putBoolean(prefKey(part), box.isChecked) }
                    putBoolean(PREF_EXPORT_AS_ZIP, asZip)
                    apply()
                }
                val parts = boxes.filterValues { it.isChecked }.keys
                exportWorkouts(summaries, parts, asZip)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        ok.isEnabled = boxes.values.any { it.isChecked }
        boxes.values.forEach { box ->
            box.setOnCheckedChangeListener { _, _ -> ok.isEnabled = boxes.values.any { it.isChecked } }
        }
    }

    private fun exportWorkouts(
        summaries: List<BaseActivitySummary>,
        parts: Set<WorkoutBatchExporter.Part>,
        asZip: Boolean
    ) {
        val context = requireContext()
        actionMode?.finish()
        GB.toast(context, getString(R.string.workout_export_preparing), Toast.LENGTH_SHORT, GB.INFO)
        lifecycleScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    WorkoutBatchExporter.export(context, summaries, parts, asZip)
                }
            } catch (e: Exception) {
                LOG.error("Failed to export workouts", e)
                GB.toast(
                    context,
                    getString(R.string.workout_export_nothing),
                    Toast.LENGTH_LONG,
                    GB.ERROR
                )
                return@launch
            }
            if (result.files.isEmpty()) {
                GB.toast(
                    context,
                    getString(R.string.workout_export_nothing),
                    Toast.LENGTH_LONG,
                    GB.ERROR
                )
                return@launch
            }
            if (result.skipped > 0) {
                GB.toast(
                    context,
                    context.resources.getQuantityString(R.plurals.workout_export_skipped, result.skipped, result.skipped),
                    Toast.LENGTH_LONG,
                    GB.WARN
                )
            }
            AndroidUtils.shareFiles(
                context,
                result.files,
                WorkoutBatchExporter.mimeTypeFor(result.files)
            )
        }
    }

    private fun showActivityDetail(position: Int) {
        val intent = WorkoutDetailsActivity.createFilteredListIntent(
            requireContext(),
            position,
            activityFilter,
            dateFromFilter,
            dateToFilter,
            nameContainsFilter,
            deviceFilter,
            itemsFilter
        )
        activityDetailLauncher.launch(intent)
    }

    private fun runFilterActivity() {
        val filterIntent = Intent(requireContext(), ActivitySummariesFilter::class.java).apply {
            putExtra("activityKindMap", activityKindMap)
            putExtra("itemsFilter", ArrayList(itemsFilter ?: emptyList()))
            putExtra("activityFilter", activityFilter)
            putExtra("dateFromFilter", dateFromFilter)
            putExtra("dateToFilter", dateToFilter)
            putExtra("deviceFilter", deviceFilter)
            putExtra("initial_deviceFilter", initialDeviceFilter)
            putExtra("nameContainsFilter", nameContainsFilter)
        }
        activityFilterLauncher.launch(filterIntent)
    }

    private fun getDeviceId(device: GBDevice): Long {
        return try {
            GBApplication.acquireDbReadOnly().use { handler ->
                DBHelper.findDevice(device, handler.daoSession)?.id ?: 0L
            }
        } catch (e: Exception) {
            LOG.error("Failed to get device id", e)
            0L
        }
    }

    private fun refresh(silent: Boolean = false) {
        // The adapter only reads the kind and date filters to title the dashboard, so it has to
        // be kept in sync here: the filter screen result only updates this fragment's fields.
        summariesAdapter?.let { adapter ->
            adapter.setActivityKindFilter(activityFilter)
            adapter.setDateFromFilter(dateFromFilter)
            adapter.setDateToFilter(dateToFilter)
            adapter.setNameContainsFilter(nameContainsFilter)
            adapter.setItemsFilter(itemsFilter)
            adapter.setDeviceFilter(deviceFilter)
        }
        viewModel.loadSummaries(
            activityFilter,
            dateFromFilter,
            dateToFilter,
            nameContainsFilter,
            deviceFilter,
            itemsFilter,
            silent
        )
    }

    private fun applySavedQuickFilter() {
        val savedFilter = GBApplication.getPrefs().preferences.getString("workout_list_quick_filter", "noselection")
        val dateRange = WorkoutFilterUtils.getDateRangeForFilter(savedFilter) ?: return

        dateFromFilter = dateRange.first
        dateToFilter = dateRange.second
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(WorkoutListFragment::class.java)
        private const val PREF_EXPORT_AS_ZIP = "workout_export_as_zip"

        @JvmStatic
        fun newInstance(): WorkoutListFragment {
            return WorkoutListFragment()
        }

        @JvmStatic
        fun newInstance(device: GBDevice): WorkoutListFragment {
            return WorkoutListFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(GBDevice.EXTRA_DEVICE, device)
                }
            }
        }
    }
}
