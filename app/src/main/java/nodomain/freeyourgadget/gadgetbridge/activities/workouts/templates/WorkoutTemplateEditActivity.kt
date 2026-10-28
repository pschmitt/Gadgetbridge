package nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.ScalarValueEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.TextValueEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.buildChoiceEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.buildScalarEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.buildTextEditor
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.dp
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.formatPairValue
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.formatScalarValue
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.formatTypedValue
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.templates.editors.hideKeyboard
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityWorkoutTemplateEditBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.WorkoutTemplateEditHeaderBinding
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutEffort
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutNodeType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutPoolLengthUnit
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepNode
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.exercises.WorkoutExerciseCatalog
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.SportSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.ValueSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutTemplateSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutValidationIssue
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat

/**
 * Creates or edits one [nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate]. A
 * top-level step is a card. A repeat group is one card that contains all its step as cards. A STEP
 * row opens [WorkoutStepEditActivity]. Every row can be dragged in or out of a repeat group, see
 * [StepDragCallback].
 *
 * A multisport template lists its legs, one row each. A leg opens this same screen with
 * [EXTRA_LEG], and the edited leg is returned in the result.
 *
 * A row with a validation error shows a warning icon and its first issue.
 */
class WorkoutTemplateEditActivity : AbstractGBActivity() {
    private lateinit var device: GBDevice
    private lateinit var spec: WorkoutTemplateSpec
    private lateinit var adapter: StepAdapter
    private val viewModel: WorkoutTemplateEditViewModel by viewModels()

    private lateinit var binding: ActivityWorkoutTemplateEditBinding
    private lateinit var header: WorkoutTemplateEditHeaderBinding
    private var nameEditor: TextValueEditor? = null
    private var noteEditor: TextValueEditor? = null
    private var templateReady = false
    private var actionBarShowsTemplateName = false

    /**
     * The exercise catalog, set once it is read. Reading it is slow, so a row shows
     * no exercise name until then.
     */
    private var exerciseCatalog: WorkoutExerciseCatalog? = null

    /**
     * Whether this screen is editting the steps of one multisport leg.
     */
    private val legMode: Boolean
        get() = viewModel.leg != null

    /**
     * The custom pool length field, while [WorkoutPoolLengthUnit.CUSTOM] is selected.
     */
    private var poolLengthCustomEditor: ScalarValueEditor? = null

    /**
     * What to do with the result of [WorkoutStepEditActivity], set right before it is launched.
     */
    private sealed class PendingStep {
        data object AddTopLevel : PendingStep()
        data class AddChild(val parent: WorkoutStepNode) : PendingStep()
        data object EditExisting : PendingStep()
    }

    private var pendingStep: PendingStep? = null

    private val legEditLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            val edited = result.data?.getParcelableCompat<WorkoutStepNode>(EXTRA_LEG)
                ?: return@registerForActivityResult
            viewModel.replaceNode(edited)
        }

    private val stepEditLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val target = pendingStep
            pendingStep = null
            if (result.resultCode != RESULT_OK || target == null) return@registerForActivityResult
            val edited = result.data?.getParcelableCompat<WorkoutStepNode>(WorkoutStepEditActivity.EXTRA_STEP)
                ?: return@registerForActivityResult
            when (target) {
                is PendingStep.AddTopLevel -> viewModel.addTopLevel(edited)
                is PendingStep.AddChild -> viewModel.addChild(target.parent, edited)
                is PendingStep.EditExisting -> viewModel.replaceNode(edited)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dev = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (dev == null) {
            finish()
            return
        }
        device = dev
        val deviceSpec = dev.deviceCoordinator.getWorkoutTemplateSpec(dev)
        if (deviceSpec == null) {
            finish()
            return
        }
        spec = deviceSpec

        binding = ActivityWorkoutTemplateEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = StepAdapter()
        binding.workoutTemplateStepList.apply {
            layoutManager = LinearLayoutManager(this@WorkoutTemplateEditActivity)
            header = WorkoutTemplateEditHeaderBinding.inflate(layoutInflater, this, false)
            adapter = ConcatAdapter(HeaderAdapter(header.root), this@WorkoutTemplateEditActivity.adapter)
        }
        val touchHelper = ItemTouchHelper(StepDragCallback())
        touchHelper.attachToRecyclerView(binding.workoutTemplateStepList)
        adapter.touchHelper = touchHelper

        binding.workoutTemplateAddStep.setOnClickListener { addStep(parent = autoParentForNewStep()) }
        binding.workoutTemplateAddRepeat.setOnClickListener { editRepeatCount(node = null) }
        binding.workoutTemplateAddLeg.setOnClickListener { addLeg() }
        binding.workoutTemplateSave.setOnClickListener { trySave() }

        onBackPressedDispatcher.addCallback(this) { confirmAndFinish() }

        lifecycleScope.launch {
            viewModel.rows.collect { rows -> adapter.submit(rows, viewModel.validate(spec)) }
        }
        lifecycleScope.launch {
            viewModel.loaded.collect { onTemplateReady() }
        }
        lifecycleScope.launch {
            viewModel.saved.collect {
                setResult(RESULT_TEMPLATE_SAVED)
                finish()
            }
        }

        val catalog = spec.exerciseCatalog
        if (catalog != null) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { catalog.preload() }
                exerciseCatalog = catalog
                adapter.submit(viewModel.rows.value, viewModel.validate(spec))
            }
        }

        lifecycleScope.launch {
            viewModel.saveFailed.collect {
                Snackbar.make(
                    binding.workoutTemplateStepList,
                    R.string.workout_template_save_failed,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        if (viewModel.started) {
            // The activity was re-created and the view model kept the template. A load that is
            // still running emits to WorkoutTemplateEditViewModel.loaded.
            if (viewModel.hasTemplate) onTemplateReady(restored = true)
            return
        }

        val leg = intent.getParcelableCompat<WorkoutStepNode>(EXTRA_LEG)
        val templateId = intent.getLongExtra(EXTRA_TEMPLATE_ID, -1L)
        if (leg != null) {
            viewModel.startLeg(spec.vendorId, leg)
            onTemplateReady()
        } else if (templateId >= 0) {
            viewModel.load(templateId)
        } else {
            val activityKindCode = intent.getIntExtra(EXTRA_ACTIVITY_KIND, -1)
            viewModel.startNew(spec.vendorId, ActivityKind.fromCode(activityKindCode))
            onTemplateReady()
        }
    }

    /**
     * Builds the template-level fields once [WorkoutTemplateEditViewModel.template] is set. A leg
     * has no template-level fields. [restored] keeps the unsaved changes of the view model after
     * the activity is re-created.
     */
    private fun onTemplateReady(restored: Boolean = false) {
        title = viewModel.template.activityKind.getLabel(this)
        val sportSpec = spec.sports[viewModel.template.activityKind]
        val multisport = !legMode && sportSpec?.legs != null
        binding.workoutTemplateAddStep.visibility = if (multisport) View.GONE else View.VISIBLE
        binding.workoutTemplateAddRepeat.visibility = if (multisport) View.GONE else View.VISIBLE
        binding.workoutTemplateAddLeg.visibility = if (multisport) View.VISIBLE else View.GONE

        if (!legMode) {
            val name = buildTextEditor(
                this,
                getString(R.string.workout_template_name_hint),
                viewModel.template.name,
                spec.nameMaxLength
            ) {
                viewModel.template.name = nameEditor?.value.orEmpty()
                refreshNameError()
            }
            nameEditor = name
            header.workoutTemplateHeaderContainer.addView(name.view)
            binding.workoutTemplateStepList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val headerRow = recyclerView.findViewHolderForAdapterPosition(0)?.itemView
                    val hidden = headerRow == null ||
                        headerRow.top + header.workoutTemplateHeaderContainer.top + name.view.bottom <= 0
                    val showName = hidden && viewModel.template.name.isNotBlank()
                    if (showName != actionBarShowsTemplateName) {
                        actionBarShowsTemplateName = showName
                        animateTitle()
                    }
                }
            })
            val note = buildTextEditor(
                this,
                getString(R.string.note),
                viewModel.template.note,
                spec.noteMaxLength
            ) {
                viewModel.template.note = noteEditor?.value?.ifBlank { null }
            }
            noteEditor = note
            header.workoutTemplateHeaderContainer.addView(note.view)
            bindOptionalFields()
        }

        if (!restored) viewModel.markBaseline()
        templateReady = true
    }

    /**
     * Shows the template name in the action bar while the name field is scrolled away, otherwise
     * displays the sport.
     */
    private fun refreshTitle() {
        val sport = viewModel.template.activityKind.getLabel(this)
        if (actionBarShowsTemplateName) {
            title = viewModel.template.name
            supportActionBar?.subtitle = sport
        } else {
            title = sport
            supportActionBar?.subtitle = null
        }
    }

    /**
     * Fades the action bar title out, sets it with [refreshTitle], and fades it in again.
     * The title moves up or down to match the scroll direction.
     */
    private fun animateTitle() {
        val toolbar = findViewById<Toolbar>(androidx.appcompat.R.id.action_bar)
        if (toolbar == null) {
            refreshTitle()
            return
        }
        val shift = toolbar.height / 4f * if (actionBarShowsTemplateName) 1 else -1
        val titleViews = toolbar.children.filterIsInstance<TextView>().toList()
        titleViews.forEach { it.animate().cancel() }
        val fadeOut = titleViews.map {
            it.animate().alpha(0f).translationY(-shift).setDuration(TITLE_FADE_MILLIS)
        }
        val fadeIn = Runnable {
            refreshTitle()
            for (view in toolbar.children.filterIsInstance<TextView>()) {
                view.alpha = 0f
                view.translationY = shift
                view.animate().alpha(1f).translationY(0f).setDuration(TITLE_FADE_MILLIS).start()
            }
        }
        if (fadeOut.isEmpty()) {
            fadeIn.run()
            return
        }
        fadeOut.first().withEndAction(fadeIn)
        fadeOut.forEach { it.start() }
    }

    /**
     * Asks for confirmation before leaving with unsaved changes.
     */
    private fun confirmAndFinish() {
        if (!templateReady || !viewModel.hasUnsavedChanges()) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.workout_template_discard_title)
            .setMessage(R.string.workout_template_discard_message)
            .setPositiveButton(R.string.workout_template_discard) { _, _ -> finish() }
            .setNegativeButton(R.string.workout_template_keep_editing, null)
            .show()
    }

    private fun bindOptionalFields() {
        val sportSpec = spec.sports[viewModel.template.activityKind]

        val poolLengthCustomSpec = sportSpec?.poolLengthCustom
        if (poolLengthCustomSpec != null) {
            val poolLengthContainer = header.workoutTemplatePoolLengthContainer
            val poolLengthCustomContainer = header.workoutTemplatePoolLengthCustomContainer

            if (viewModel.template.poolLengthUnit == null) {
                viewModel.template.poolLengthUnit = WorkoutPoolLengthUnit.UNSPECIFIED
            }

            poolLengthContainer.visibility = View.VISIBLE
            poolLengthContainer.removeAllViews()
            val editor = buildChoiceEditor(
                this,
                WorkoutPoolLengthUnit.entries,
                getString(R.string.poolLength),
                viewModel.template.poolLengthUnit,
                onChanged = { picked ->
                    viewModel.template.poolLengthUnit = picked
                    if (picked != WorkoutPoolLengthUnit.CUSTOM) viewModel.template.poolLength = null
                    bindPoolLengthCustomField(poolLengthCustomContainer, poolLengthCustomSpec)
                },
            )
            poolLengthContainer.addView(editor.view)
            bindPoolLengthCustomField(poolLengthCustomContainer, poolLengthCustomSpec)
        }

        val transitionsBox = header.workoutTemplateTransitionsCheckbox
        if (sportSpec?.transitions == true) {
            transitionsBox.visibility = View.VISIBLE
            transitionsBox.isChecked = viewModel.template.transitions == true
            transitionsBox.setOnCheckedChangeListener { _, checked ->
                hideKeyboard(transitionsBox)
                viewModel.template.transitions = checked
            }
        }
    }

    /**
     * Shows the custom pool length field while [WorkoutPoolLengthUnit.CUSTOM] is selected.
     */
    private fun bindPoolLengthCustomField(container: LinearLayout, spec: ValueSpec) {
        container.removeAllViews()
        poolLengthCustomEditor = null
        if (viewModel.template.poolLengthUnit != WorkoutPoolLengthUnit.CUSTOM) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        val editor = buildScalarEditor(
            this,
            spec,
            getString(R.string.workout_template_pool_length_custom_hint),
            viewModel.template.poolLength?.toLong()
        )
        poolLengthCustomEditor = editor
        container.addView(editor.view)
    }

    /**
     * The last top-level node, when it is an empty repeat group. A new step goes inside it.
     */
    private fun autoParentForNewStep(): WorkoutStepNode? =
        viewModel.template.steps.lastOrNull()
            ?.takeIf { it.type == WorkoutNodeType.REPEAT && it.children.isEmpty() }

    /**
     * Opens a new step in [WorkoutStepEditActivity]. It is added inside [parent], or at the top
     * level when [parent] is null.
     */
    private fun addStep(parent: WorkoutStepNode?) {
        val sportSpec = spec.sports[viewModel.template.activityKind] ?: return
        val stepType = sportSpec.stepTypes.firstOrNull() ?: WorkoutStepType.ACTIVE
        val node = WorkoutStepNode.newStep(stepType)
        pendingStep = if (parent != null) PendingStep.AddChild(parent) else PendingStep.AddTopLevel
        launchStepEditor(node, isNew = true)
    }

    /**
     * Opens an existing STEP node in [WorkoutStepEditActivity]. The returned copy replaces it.
     */
    private fun editStep(node: WorkoutStepNode) {
        pendingStep = PendingStep.EditExisting
        launchStepEditor(node, isNew = false)
    }

    private fun launchStepEditor(node: WorkoutStepNode, isNew: Boolean) {
        val intent = Intent(this, WorkoutStepEditActivity::class.java)
        intent.putExtra(GBDevice.EXTRA_DEVICE, device)
        intent.putExtra(WorkoutStepEditActivity.EXTRA_ACTIVITY_KIND, viewModel.template.activityKind.code)
        intent.putExtra(WorkoutStepEditActivity.EXTRA_STEP, node)
        intent.putExtra(WorkoutStepEditActivity.EXTRA_IS_NEW, isNew)
        stepEditLauncher.launch(intent)
    }

    /**
     * Shows the repeat count dialog. Adds a new top-level repeat group when [node] is null, or
     * updates the count of [node].
     */
    private fun editRepeatCount(node: WorkoutStepNode?) {
        val sportSpec = spec.sports[viewModel.template.activityKind] ?: return
        val repeatSpec = sportSpec.repeat ?: return
        hideKeyboard(binding.workoutTemplateStepList)
        val picker = NumberPicker(this).apply {
            minValue = repeatSpec.min
            maxValue = repeatSpec.max
            value = (node?.repeatCount ?: repeatSpec.min).coerceIn(repeatSpec.min, repeatSpec.max)
            wrapSelectorWheel = false
        }
        val container = FrameLayout(this)
        container.setPadding(dp(this, 24), dp(this, 16), dp(this, 24), dp(this, 0))
        container.addView(
            picker,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.workout_template_add_repeat)
            .setView(container)
            .setPositiveButton(R.string.ok) { _, _ ->
                val count = picker.value
                if (node != null) {
                    node.repeatCount = count
                    viewModel.refreshRows()
                } else {
                    viewModel.addTopLevel(WorkoutStepNode.newRepeat(count))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addLeg() {
        val sportSpec = spec.sports[viewModel.template.activityKind] ?: return
        val kinds = sportSpec.legs?.sports ?: return
        hideKeyboard(binding.workoutTemplateStepList)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.workout_template_multisport_leg_add)
            .setAdapter(SportPickerAdapter(this, kinds)) { _, which ->
                viewModel.addTopLevel(WorkoutStepNode.newLeg(kinds[which]))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Opens the steps of [leg] in a new instance of this screen.
     */
    private fun editLeg(leg: WorkoutStepNode) {
        val intent = Intent(this, WorkoutTemplateEditActivity::class.java)
        intent.putExtra(GBDevice.EXTRA_DEVICE, device)
        intent.putExtra(EXTRA_LEG, leg)
        legEditLauncher.launch(intent)
    }

    private fun trySave() {
        nameEditor?.let { viewModel.template.name = it.value }
        noteEditor?.let { viewModel.template.note = it.value.ifBlank { null } }
        if (viewModel.template.poolLengthUnit == WorkoutPoolLengthUnit.CUSTOM) {
            viewModel.template.poolLength = poolLengthCustomEditor?.value?.toInt()
        }

        val issues = viewModel.validate(spec)
        val nameIssue = refreshNameError(issues)
        if (issues.isNotEmpty()) {
            val firstStepIssue = issues.firstOrNull { it.nodeLocalId != null }
            if (firstStepIssue != null) {
                val index = adapter.indexOf(firstStepIssue.nodeLocalId)
                if (index >= 0) binding.workoutTemplateStepList.smoothScrollToPosition(HEADER_ROWS + index)
                showValidationSnackbar()
            } else if (nameIssue != null) {
                showValidationSnackbar()
            } else {
                // A template-level issue with no field of its own, such as too many steps
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.workout_template_validation_title)
                    .setMessage(getString(issues.first { it.nodeLocalId == null }.message))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            return
        }
        if (legMode) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_LEG, viewModel.legResult()))
            finish()
            return
        }
        viewModel.save(device)
    }

    /**
     * Shows or clears the error of the name field. Returns the name issue, if any.
     */
    private fun refreshNameError(issues: List<WorkoutValidationIssue> = viewModel.validate(spec)): WorkoutValidationIssue? {
        val nameIssue = issues.firstOrNull { it.nodeLocalId == null && isNameIssue(it) }
        nameEditor?.setError(nameIssue?.let { getString(it.message) })
        return nameIssue
    }

    private fun showValidationSnackbar() {
        Snackbar.make(
            binding.workoutTemplateStepList,
            R.string.workout_template_validation_snackbar,
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun isNameIssue(issue: WorkoutValidationIssue) =
        issue.message == R.string.workout_error_name_required || issue.message == R.string.workout_error_name_too_long

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            confirmAndFinish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showRowMenu(anchor: View, row: WorkoutStepRow) {
        hideKeyboard(anchor)
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, MENU_EDIT, 0, R.string.appmanager_app_edit)
        if (row.node.type == WorkoutNodeType.REPEAT) {
            popup.menu.add(0, MENU_ADD_STEP_INSIDE, 1, R.string.workout_template_add_step_inside)
        }
        popup.menu.add(0, MENU_DUPLICATE, 2, R.string.widgets_duplicate)
        val deleteLabel =
            if (row.node.type == WorkoutNodeType.LEG) R.string.workout_template_multisport_leg_delete
            else R.string.workout_template_delete_step
        popup.menu.add(0, MENU_DELETE, 3, deleteLabel)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_EDIT -> {
                    when (row.node.type) {
                        WorkoutNodeType.STEP -> editStep(row.node)
                        WorkoutNodeType.REPEAT -> editRepeatCount(row.node)
                        WorkoutNodeType.LEG -> editLeg(row.node)
                    }
                    true
                }

                MENU_ADD_STEP_INSIDE -> {
                    addStep(parent = row.node)
                    true
                }

                MENU_DUPLICATE -> {
                    viewModel.duplicate(row.node)
                    true
                }

                MENU_DELETE -> {
                    viewModel.remove(row.node)
                    true
                }

                else -> false
            }
        }
        popup.show()
    }

    /**
     * Drag-and-drop reordering:
     * - a STEP can be dragged to any position, in or out of any repeat group
     * - a REPEAT group drags as a single block and may only ever be at the top level
     * - a repeat group can never contain another one
     *
     * The children of a dragged group stay in the list and move with the parent header.
     * The group swaps places with the step or group above or below it.
     * The adapter reorders its own list during the drag.
     * The tree is rebuilt once, in [clearView], from the final order.
     */
    private inner class StepDragCallback : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0,
    ) {
        /**
         * The id of the dragged node.
         */
        private var draggedLocalId: Long? = null

        /**
         * The depth of the dragged row as last previewed by [onChildDraw]. Null for a REPEAT or LEG.
         */
        private var draggedDepth: Int? = null

        private var draggingGroup = false

        override fun isLongPressDragEnabled(): Boolean = true

        override fun getDragDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
            if (viewHolder is StepAdapter.ViewHolder) super.getDragDirs(recyclerView, viewHolder) else 0

        override fun canDropOver(
            recyclerView: RecyclerView,
            current: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = target is StepAdapter.ViewHolder

        override fun onMove(
            rv: RecyclerView,
            holder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = holder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from < 0 || to < 0) return false
            if (draggingGroup) adapter.swapBlocks(from, to) else adapter.moveItem(from, to)
            adapter.restyleAttached(rv)
            return true
        }

        /**
         * For a group, the first row of the step or group next to it, when the dragged block
         * passes the middle of it.
         */
        override fun chooseDropTarget(
            selected: RecyclerView.ViewHolder,
            dropTargets: MutableList<RecyclerView.ViewHolder>,
            curX: Int,
            curY: Int
        ): RecyclerView.ViewHolder? {
            if (!draggingGroup) return super.chooseDropTarget(selected, dropTargets, curX, curY)
            val rv = binding.workoutTemplateStepList
            val position = selected.bindingAdapterPosition
            if (position < 0) return null
            val block = adapter.blockAt(position)
            val movingUp = curY < selected.itemView.top
            val neighbor = (if (movingUp) adapter.blockBefore(block.first) else adapter.blockAfter(block.last))
                ?: return null
            val neighborFirst = rv.findViewHolderForAdapterPosition(HEADER_ROWS + neighbor.first) ?: return null
            val neighborLast = rv.findViewHolderForAdapterPosition(HEADER_ROWS + neighbor.last) ?: return null
            val neighborMiddle = (neighborFirst.itemView.top + neighborLast.itemView.bottom) / 2
            if (movingUp) return neighborFirst.takeIf { curY < neighborMiddle }
            val blockLast = rv.findViewHolderForAdapterPosition(HEADER_ROWS + block.last) ?: return null
            val blockBottom = curY + blockLast.itemView.bottom - selected.itemView.top
            return neighborFirst.takeIf { blockBottom > neighborMiddle }
        }

        /**
         * For a group, keeps the first row of the swapped rows at the old top of these rows, so
         * that the surrounding rows do not move.
         */
        override fun onMoved(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            fromPos: Int,
            target: RecyclerView.ViewHolder,
            toPos: Int,
            x: Int,
            y: Int
        ) {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
            if (!draggingGroup || layoutManager == null) {
                super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y)
                return
            }
            val anchor = (if (toPos < fromPos) target else viewHolder).itemView
            val topMargin = (anchor.layoutParams as ViewGroup.MarginLayoutParams).topMargin
            layoutManager.scrollToPositionWithOffset(
                minOf(fromPos, toPos),
                layoutManager.getDecoratedTop(anchor) - topMargin
            )
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                val position = viewHolder.bindingAdapterPosition
                val row = adapter.itemAt(position)
                draggedLocalId = row?.node?.localId
                draggedDepth = row?.depth
                draggingGroup = row != null && row.node.type != WorkoutNodeType.STEP
            }
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean,
        ) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || viewHolder !is StepAdapter.ViewHolder) return
            val position = viewHolder.bindingAdapterPosition
            if (position < 0) return
            if (draggingGroup) {
                forEachGroupChild(recyclerView, position) { child ->
                    getDefaultUIUtil().onDraw(c, recyclerView, child, dX, dY, actionState, isCurrentlyActive)
                }
                return
            }
            if (!isCurrentlyActive) return
            val depth = adapter.previewDepthAt(position, dY, viewHolder.itemView.height)
            draggedDepth = depth
            if (adapter.setPreviewDepth(position, depth)) adapter.restyleAttached(recyclerView)
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            val position = viewHolder.bindingAdapterPosition
            if (draggingGroup && position >= 0) {
                forEachGroupChild(recyclerView, position) { getDefaultUIUtil().clearView(it) }
            }
            val order = adapter.finishDrag(draggedLocalId, draggedDepth)
            draggedLocalId = null
            draggedDepth = null
            draggingGroup = false
            adapter.restyleAttached(recyclerView)
            if (order.isNotEmpty()) viewModel.applyReorder(order)
        }

        private fun forEachGroupChild(recyclerView: RecyclerView, headerPosition: Int, action: (View) -> Unit) {
            for (childPosition in adapter.blockAt(headerPosition).drop(1)) {
                recyclerView.findViewHolderForAdapterPosition(HEADER_ROWS + childPosition)?.let { action(it.itemView) }
            }
        }
    }

    /**
     * The template-level fields, as the first row of the step list.
     */
    private class HeaderAdapter(private val view: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = object : RecyclerView.ViewHolder(view) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        }
    }

    private enum class Segment { SINGLE, TOP, MIDDLE, BOTTOM }

    private inner class StepAdapter : RecyclerView.Adapter<StepAdapter.ViewHolder>() {
        var touchHelper: ItemTouchHelper? = null

        private var items: MutableList<WorkoutStepRow> = mutableListOf()

        /**
         * The first validation issue of each row, by [WorkoutStepNode.localId].
         */
        private var issuesByNode: Map<Long, WorkoutValidationIssue> = emptyMap()

        @SuppressLint("NotifyDataSetChanged")
        fun submit(newItems: List<WorkoutStepRow>, issues: List<WorkoutValidationIssue>) {
            items = newItems.toMutableList()
            issuesByNode = issues.mapNotNull { issue -> issue.nodeLocalId?.let { it to issue } }.toMap()
            notifyDataSetChanged()
        }

        /**
         * The position of the row of [nodeLocalId], or -1.
         */
        fun indexOf(nodeLocalId: Long?): Int = items.indexOfFirst { it.node.localId == nodeLocalId }

        fun itemAt(position: Int): WorkoutStepRow? = items.getOrNull(position)

        /**
         * Moves the row at [from] to [to].
         */
        fun moveItem(from: Int, to: Int) {
            val row = items.removeAt(from)
            items.add(to, row)
            notifyItemMoved(from, to)
        }

        /**
         * The positions of the row at [position] and of its children.
         */
        fun blockAt(position: Int): IntRange {
            val row = items[position]
            var end = position + 1
            if (row.node.type != WorkoutNodeType.STEP) {
                while (end < items.size && items[end].depth > row.depth) end++
            }
            return position until end
        }

        /**
         * The top-level block that ends right before [position], or null at the start of the list.
         */
        fun blockBefore(position: Int): IntRange? {
            var start = position - 1
            while (start >= 0 && items[start].depth > 0) start--
            return if (start < 0) null else start until position
        }

        /**
         * The top-level block that starts right after [position], or null at the end of the list.
         */
        fun blockAfter(position: Int): IntRange? =
            if (position + 1 < items.size) blockAt(position + 1) else null

        /**
         * Swaps the block at [headerPosition] with the block that starts at [neighborPosition].
         */
        fun swapBlocks(headerPosition: Int, neighborPosition: Int) {
            val block = blockAt(headerPosition)
            if (neighborPosition < headerPosition) {
                repeat(headerPosition - neighborPosition) {
                    items.add(block.last, items.removeAt(neighborPosition))
                    notifyItemMoved(neighborPosition, block.last)
                }
            } else {
                val neighbor = blockAt(neighborPosition)
                for (i in 0 until neighbor.count()) {
                    items.add(block.first + i, items.removeAt(neighbor.first + i))
                    notifyItemMoved(neighbor.first + i, block.first + i)
                }
            }
        }

        /**
         * The depths the STEP at [position] can have, given the rows above and below it. The range
         * has two values right after the last child of a group, where the step can stay in the
         * group or leave it.
         */
        fun depthRangeAt(position: Int): IntRange {
            val prev = items.getOrNull(position - 1) ?: return 0..0
            val max = if (prev.node.type != WorkoutNodeType.STEP) 1 else prev.depth
            if (max == 0) return 0..0
            val next = items.getOrNull(position + 1)
            val stillMidGroup = next != null && next.depth >= max
            if (stillMidGroup) return max..max
            if (prev.node.type != WorkoutNodeType.STEP) return max..max
            return 0..max
        }

        /**
         * The previewed depth of the STEP at [position]. At an ambiguous position (see [depthRangeAt]),
         * the row keeps its current depth. It joins the group when it is dragged up, and leaves it when
         * it is dragged down, by more than [DEPTH_SWITCH_FRACTION] of [rowHeightPx].
         * [dY] is relative to the current adapter position of the row.
         */
        fun previewDepthAt(position: Int, dY: Float, rowHeightPx: Int): Int {
            val range = depthRangeAt(position)
            if (range.first == range.last) return range.first
            val threshold = rowHeightPx * DEPTH_SWITCH_FRACTION
            return when {
                dY > threshold -> range.first
                dY < -threshold -> range.last
                else -> items[position].depth.coerceIn(range)
            }
        }

        /**
         * The positions tinted as the drop target of a drag.
         */
        private var dropTarget: IntRange? = null

        /**
         * Sets the depth of the dragged STEP at [position] and the drop target that matches it.
         * Returns true when the rows must be restyled.
         */
        fun setPreviewDepth(position: Int, depth: Int): Boolean {
            val row = itemAt(position) ?: return false
            val target = groupRangeAt(position, depth)
            if (row.depth == depth && target == dropTarget) return false
            items[position] = row.copy(depth = depth)
            dropTarget = target
            return true
        }

        /**
         * The part of a card that the row at [position] is. The rows of a repeat group make one
         * card, the header at the top.
         */
        private fun segmentAt(position: Int): Segment {
            val row = items[position]
            val nextInGroup = (items.getOrNull(position + 1)?.depth ?: 0) > 0
            return when {
                row.depth > 0 -> if (nextInGroup) Segment.MIDDLE else Segment.BOTTOM
                row.node.type == WorkoutNodeType.STEP || !nextInGroup -> Segment.SINGLE
                else -> Segment.TOP
            }
        }

        /**
         * Applies the style of the row (card, color and margins) at [position]. A top-level step is
         * a card of its own. A repeat group is a card that contains cards for each of its steps.
         *
         * The vertical margins of every row have the same sum, and only the drawn outline changes
         * with the segment. A change of segment during a drag thus does not move the rows, which
         * would change the drag offset that [previewDepthAt] reads.
         */
        fun styleRow(holder: ViewHolder, position: Int) {
            val row = items.getOrNull(position) ?: return
            val context = holder.itemView.context
            val segment = segmentAt(position)
            val roundTop = segment == Segment.SINGLE || segment == Segment.TOP
            val roundBottom = segment == Segment.SINGLE || segment == Segment.BOTTOM
            val radius = dp(context, CARD_RADIUS_DP).toFloat()
            val segmentShape = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(if (roundTop) radius else 0f)
                .setTopRightCornerSize(if (roundTop) radius else 0f)
                .setBottomLeftCornerSize(if (roundBottom) radius else 0f)
                .setBottomRightCornerSize(if (roundBottom) radius else 0f)
                .build()
            val gap = dp(context, CARD_GAP_DP)

            val cardColor = MaterialColors.getColor(holder.itemView, R.attr.device_card_bg)
            val strokeWidth = dp(context, 1)
            val isGroup = row.depth > 0 || row.node.type != WorkoutNodeType.STEP
            holder.frame.background = if (isGroup) {
                val groupColor = if (dropTarget?.contains(position) == true) {
                    val overlay =
                        MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnSurface)
                    MaterialColors.layer(cardColor, overlay, DROP_TARGET_OVERLAY_ALPHA)
                } else {
                    cardColor
                }
                val outline = MaterialShapeDrawable(segmentShape).apply {
                    fillColor = ColorStateList.valueOf(groupColor)
                    setStroke(
                        strokeWidth.toFloat(),
                        MaterialColors.getColor(holder.itemView, R.attr.stat_tile_border)
                    )
                }
                // The negative insets move the stroke of an inner edge out of the row bounds, so
                // the rows of a group show one outline
                InsetDrawable(
                    outline,
                    0,
                    if (roundTop) gap else -strokeWidth,
                    0,
                    if (roundBottom) gap else -strokeWidth
                )
            } else {
                null
            }

            if (row.depth > 0) {
                val inset = dp(context, CHILD_CARD_INSET_DP)
                holder.card.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginStart = inset
                    marginEnd = inset
                    topMargin = 0
                    bottomMargin = 2 * gap
                }
                holder.card.radius = dp(context, CHILD_CARD_RADIUS_DP).toFloat()
                holder.card.setCardBackgroundColor(cardColor)
                holder.card.strokeWidth = strokeWidth
            } else {
                holder.card.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    marginStart = 0
                    marginEnd = 0
                    topMargin = gap
                    bottomMargin = gap
                }
                holder.card.shapeAppearanceModel = segmentShape
                holder.card.setCardBackgroundColor(if (isGroup) Color.TRANSPARENT else cardColor)
                holder.card.strokeWidth = if (isGroup) 0 else strokeWidth
            }
        }

        /**
         * Restyles the attached rows. A move or a depth change during a drag does not bind them
         * again, but it can change the card segment of the rows near it.
         */
        fun restyleAttached(recyclerView: RecyclerView) {
            for (i in 0 until recyclerView.childCount) {
                val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i)) as? ViewHolder ?: continue
                val position = holder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) styleRow(holder, position)
            }
        }

        /**
         * The positions of the group the STEP at [position] joins at [depth]: the header and every
         * row in the group. Null when [depth] is 0.
         *
         * The walk starts from the row above [position], since the depth of the dragged row is not
         * final during the drag.
         */
        fun groupRangeAt(position: Int, depth: Int): IntRange? {
            if (depth <= 0) return null
            var headerIndex = position - 1
            while (headerIndex >= 0 && items[headerIndex].depth >= 1) headerIndex--
            if (headerIndex < 0 || items[headerIndex].node.type == WorkoutNodeType.STEP) return null
            var endIndex = headerIndex + 1
            while (endIndex < items.size && (endIndex == position || items[endIndex].depth >= 1)) endIndex++
            return headerIndex until endIndex
        }

        /**
         * Ends the drag. Sets the row of [draggedLocalId] to [draggedDepth], and returns the
         * (node id, depth) of every row for [WorkoutTemplateEditViewModel.applyReorder].
         */
        fun finishDrag(draggedLocalId: Long?, draggedDepth: Int?): List<Pair<Long, Int>> {
            dropTarget = null
            if (draggedLocalId != null) {
                val index = indexOf(draggedLocalId)
                val row = items.getOrNull(index)
                if (row != null) {
                    val resolvedDepth = if (row.node.type == WorkoutNodeType.STEP) draggedDepth ?: row.depth else 0
                    if (resolvedDepth != row.depth) items[index] = row.copy(depth = resolvedDepth)
                }
            }

            return items.map { it.node.localId to it.depth }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val frame: View = view.findViewById(R.id.workout_step_frame)
            val card: MaterialCardView = view.findViewById(R.id.workout_step_card)
            val content: View = view.findViewById(R.id.workout_step_content)
            val colorBar: View = view.findViewById(R.id.workout_step_color_bar)
            val title: TextView = view.findViewById(R.id.workout_step_title)
            val detail: TextView = view.findViewById(R.id.workout_step_detail)
            val warning: ImageView = view.findViewById(R.id.workout_step_warning)
            val overflow: ImageButton = view.findViewById(R.id.workout_step_overflow)
            val dragHandle: View = view.findViewById(R.id.workout_step_drag_handle)
        }

        override fun getItemCount(): Int = items.size

        override fun onViewRecycled(holder: ViewHolder) {
            // A child of a dragged group can leave the screen
            ItemTouchHelper.Callback.getDefaultUIUtil().clearView(holder.itemView)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_workout_template_step, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val row = items[position]
            val node = row.node
            val context = holder.itemView.context
            styleRow(holder, position)

            val colorRes = when (node.type) {
                WorkoutNodeType.STEP -> {
                    val sportSpec = spec.sports[viewModel.template.activityKind]
                    val stepType = node.stepType
                    holder.title.text = when {
                        stepType == WorkoutStepType.ACTIVE && sportSpec != null -> context.getString(sportSpec.activeLabel)
                        stepType != null -> context.getString(stepType.label)
                        else -> context.getString(R.string.other)
                    }
                    holder.detail.text = stepSummary(context, sportSpec, exerciseCatalog, node)
                    stepType?.colorRes ?: R.color.workout_step_color_other
                }

                WorkoutNodeType.REPEAT -> {
                    holder.title.text = context.getString(R.string.workout_template_repeat_title, node.repeatCount ?: 0)
                    holder.detail.text = ""
                    R.color.workout_step_color_group
                }

                WorkoutNodeType.LEG -> {
                    holder.title.text = node.legActivityKind?.getLabel(context)
                        ?: context.getString(R.string.workout_template_multisport_leg_title)
                    val childCount = node.children.size
                    holder.detail.text =
                        context.resources.getQuantityString(
                            R.plurals.workout_template_step_count,
                            childCount,
                            childCount
                        )
                    R.color.workout_step_color_group
                }
            }

            val issue = issuesByNode[node.localId]
            if (issue != null) {
                holder.warning.visibility = View.VISIBLE
                holder.detail.text = context.getString(issue.message)
                holder.detail.setTextColor(
                    MaterialColors.getColor(
                        context,
                        android.R.attr.colorError,
                        ContextCompat.getColor(context, R.color.workout_step_color_active)
                    )
                )
            } else {
                holder.warning.visibility = View.GONE
                holder.detail.setTextColor(
                    MaterialColors.getColor(
                        context,
                        android.R.attr.textColorSecondary,
                        ContextCompat.getColor(context, R.color.workout_step_color_other)
                    )
                )
            }
            holder.detail.isVisible = holder.detail.text.isNotEmpty()

            // A row with only the title is shorter, with a dot instead of the color bar
            val singleLine = !holder.detail.isVisible
            holder.colorBar.updateLayoutParams<ViewGroup.LayoutParams> {
                width = dp(context, if (singleLine) COLOR_DOT_SIZE_DP else COLOR_BAR_WIDTH_DP)
                height = dp(context, if (singleLine) COLOR_DOT_SIZE_DP else COLOR_BAR_HEIGHT_DP)
            }
            holder.colorBar.background = GradientDrawable().apply {
                shape = if (singleLine) GradientDrawable.OVAL else GradientDrawable.RECTANGLE
                setColor(ContextCompat.getColor(context, colorRes))
            }
            val verticalPadding = dp(context, if (singleLine) 0 else ROW_PADDING_VERTICAL_DP)
            holder.content.setPaddingRelative(
                holder.content.paddingStart,
                verticalPadding,
                holder.content.paddingEnd,
                verticalPadding
            )
            holder.content.minimumHeight =
                dp(context, if (singleLine) ROW_MIN_HEIGHT_SINGLE_LINE_DP else ROW_MIN_HEIGHT_DP)

            holder.card.setOnClickListener {
                when (node.type) {
                    WorkoutNodeType.STEP -> editStep(node)
                    WorkoutNodeType.LEG -> editLeg(node)
                    WorkoutNodeType.REPEAT -> {}
                }
            }
            holder.overflow.setOnClickListener { anchor -> showRowMenu(anchor, row) }
            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper?.startDrag(holder)
                false
            }
        }
    }

    companion object {
        const val EXTRA_TEMPLATE_ID = "workout_template_id"
        const val EXTRA_ACTIVITY_KIND = "workout_template_activity_kind"
        const val EXTRA_LEG = "workout_template_leg"
        const val RESULT_TEMPLATE_SAVED = 1

        private const val HEADER_ROWS = 1
        private const val TITLE_FADE_MILLIS = 120L

        private const val MENU_EDIT = 1
        private const val MENU_ADD_STEP_INSIDE = 2
        private const val MENU_DELETE = 3
        private const val MENU_DUPLICATE = 4

        private const val CARD_RADIUS_DP = 20
        private const val COLOR_BAR_WIDTH_DP = 4
        private const val COLOR_BAR_HEIGHT_DP = 32
        private const val COLOR_DOT_SIZE_DP = 8
        private const val ROW_PADDING_VERTICAL_DP = 8
        private const val ROW_MIN_HEIGHT_DP = 56
        private const val ROW_MIN_HEIGHT_SINGLE_LINE_DP = 40
        private const val CARD_GAP_DP = 6
        private const val CHILD_CARD_RADIUS_DP = 14
        private const val CHILD_CARD_INSET_DP = 8
        private const val DROP_TARGET_OVERLAY_ALPHA = 0.08f
        private const val DEPTH_SWITCH_FRACTION = 0.25f
    }
}

/**
 * The detail line of a step row: the exercise, the duration, the target and the weight, each
 * with its value when [sportSpec] declares a [ValueSpec] for it.
 */
private fun stepSummary(
    context: Context,
    sportSpec: SportSpec?,
    catalog: WorkoutExerciseCatalog?,
    node: WorkoutStepNode,
): String {
    val stepSpec = node.stepType?.let { sportSpec?.stepSpec(it) }
    val parts = mutableListOf<String>()

    catalog?.byId(node.exerciseId)?.let { parts.add(it.name) }

    node.duration?.let { duration ->
        val valueSpec = stepSpec?.durations?.find { it.type == duration.type }?.value
        val valueText = scalarText(context, valueSpec, duration.value)?.let { if (duration.plus) "$it+" else it }
        parts.add(formatTypedValue(context, duration.type, duration.value, valueText))
    }

    node.target?.let { target ->
        if (target.type != WorkoutTargetType.NONE) {
            val valueSpec = stepSpec?.primaryTargets?.find { it.type == target.type }?.value
            val valueText = when {
                target.type.isEnum -> target.enumValue?.let { name -> WorkoutEffort.entries.find { it.name == name } }
                    ?.let { context.getString(it.label) }

                target.type.isZone -> target.zone?.toString()
                target.type.isRange && valueSpec is ValueSpec.PairRange && target.low != null && target.high != null ->
                    formatPairValue(context, valueSpec, target.low, target.high)

                else -> scalarText(context, valueSpec, target.low)
            }
            parts.add(formatTypedValue(context, target.type, target.low, valueText))
        }
    }

    node.weightType?.let { weightType ->
        if (weightType != WorkoutWeightType.NONE) {
            val valueSpec = stepSpec?.fields?.weightSpecs?.get(weightType)
            val value = node.weightValue?.toLong()
            parts.add(formatTypedValue(context, weightType, value, scalarText(context, valueSpec, value)))
        }
    }

    return parts.joinToString(" · ")
}

private fun scalarText(context: Context, valueSpec: ValueSpec?, value: Long?): String? =
    if (valueSpec != null && value != null) formatScalarValue(context, valueSpec, value) else null
