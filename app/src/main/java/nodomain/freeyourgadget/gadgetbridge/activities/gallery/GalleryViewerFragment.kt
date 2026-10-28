package nodomain.freeyourgadget.gadgetbridge.activities.gallery

import android.app.Dialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.viewpager2.widget.ViewPager2
import coil3.size.Size
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.gallery.GalleryViewModel.DownloadStatus
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentGalleryViewerBinding
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryCapability
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryPreviewCaches
import nodomain.freeyourgadget.gadgetbridge.service.gallery.MediaFile
import nodomain.freeyourgadget.gadgetbridge.service.gallery.MediaType
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Displays the loaded [MediaFile]s of the [GalleryActivity] in full screen, one page for each file.
 */
@OptIn(UnstableApi::class)
class GalleryViewerFragment : DialogFragment() {
    private var binding: FragmentGalleryViewerBinding? = null
    private lateinit var viewModel: GalleryViewModel
    private lateinit var adapter: GalleryViewerAdapter

    private var player: ExoPlayer? = null

    // The file of the current page, and the file that the player has prepared
    private var activeId: String? = null
    private var preparedId: String? = null

    // The player plays the local original of preparedId, not its preview
    private var preparedLocal = false

    // Finds the local original of the active video before the player prepares it
    private var prepareJob: Job? = null

    // The downloaded files whose local original failed to open or to play, and that display the preview
    private val failedOriginals = HashSet<String>()

    // The toolbar, the action bar and the system bars are hidden
    private var immersive = false

    // The actions are in the toolbar menu, and the action bar is not displayed
    private var landscape = false

    // Immersive in the landscape orientation, from the fullscreen button of a video
    private var videoFullscreen = false
    private var orientationBeforeFullscreen = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    // The controls of the active video are visible
    private var controlsVisible = false

    private val immersiveBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            setImmersive(immersive = false, videoFullscreen = false)
        }
    }

    // The video position to restore when the screen starts again
    private var resumeId: String? = null
    private var resumePosition = 0L

    // The pager is at the start file
    private var positioned = false
    private var acquired = false

    // The states that the actions display
    private val actionStates = HashMap<ViewerAction, ActionState>()

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val id = preparedId
            if (!preparedLocal || id == null) {
                LOG.error("Failed to play the preview of {}", id, error)
                return
            }
            LOG.warn("Failed to play the original of {} ({}), playing the preview", id, error.errorCodeName, error)
            val missing = error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION
            // The player must leave the original before the state update renders again
            failedOriginals.add(id)
            preparePlayer()
            viewModel.state.value.files.firstOrNull { it.id == id }?.let { onOriginalFailed(it, missing) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.GalleryViewerTheme)
        activeId = savedInstanceState?.getString(KEY_FILE_ID) ?: requireArguments().getString(KEY_FILE_ID)
        resumeId = savedInstanceState?.getString(KEY_RESUME_ID)
        resumePosition = savedInstanceState?.getLong(KEY_RESUME_POSITION) ?: 0L
        immersive = savedInstanceState?.getBoolean(KEY_IMMERSIVE) ?: false
        videoFullscreen = savedInstanceState?.getBoolean(KEY_VIDEO_FULLSCREEN) ?: false
        orientationBeforeFullscreen = savedInstanceState?.getInt(KEY_ORIENTATION_BEFORE_FULLSCREEN)
            ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as ComponentDialog
        dialog.onBackPressedDispatcher.addCallback(this, immersiveBackCallback)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentGalleryViewerBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = binding!!
        viewModel = (requireActivity() as GalleryActivity).viewModel
        val metrics = resources.displayMetrics
        adapter = GalleryViewerAdapter(
            viewModel.imageLoader,
            viewLifecycleOwner.lifecycleScope,
            Size(metrics.widthPixels * ORIGINAL_SCALE, metrics.heightPixels * ORIGINAL_SCALE),
            findOriginal = { viewModel.findOriginal(it) },
            onOriginalFailed = { file, missing -> onOriginalFailed(file, missing) },
            onFullscreenButtonClick = { enabled ->
                // The button state of a page also changes when the adapter sets it
                if (enabled != videoFullscreen) {
                    setImmersive(enabled, enabled)
                }
            },
            onPhotoClick = { setImmersive(!immersive, false) },
            onControlsVisibilityChange = { visible ->
                controlsVisible = visible
                updateBarsVisibility(true)
            }
        )

        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            root.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            if (landscape) {
                adapter.setControlsBottomPadding(bars.bottom)
            } else {
                // The action bar background extends below the navigation bar
                binding.galleryViewerActions.updatePadding(bottom = bars.bottom)
            }
            WindowInsetsCompat.CONSUMED
        }
        binding.galleryViewerToolbar.setNavigationOnClickListener { dismiss() }
        if (landscape) {
            binding.galleryViewerActions.isVisible = false
            binding.galleryViewerToolbar.inflateMenu(R.menu.menu_gallery_viewer)
            binding.galleryViewerToolbar.setOnMenuItemClickListener { item ->
                val action = ViewerAction.entries.firstOrNull { it.menuId == item.itemId }
                    ?: return@setOnMenuItemClickListener false
                onAction(action)
                true
            }
        } else {
            ViewerAction.entries.forEach { action ->
                actionButton(binding, action).setOnClickListener { onAction(action) }
            }
            binding.galleryViewerActions.addOnLayoutChangeListener { actions, _, top, _, bottom, _, oldTop, _, oldBottom ->
                if (bottom - top != oldBottom - oldTop) {
                    // The adapter must not change the layout of the pages during this layout pass
                    actions.post { adapter.setControlsBottomPadding(actions.height) }
                }
            }
        }
        binding.galleryViewerPager.adapter = adapter
        binding.galleryViewerPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                onPageChanged()
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        applyImmersive(false)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val newPlayer = ExoPlayer.Builder(requireContext())
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        newPlayer.addListener(playerListener)
        player = newPlayer
        adapter.setActive(activeId, newPlayer)
        preparePlayer()
    }

    override fun onStop() {
        prepareJob?.cancel()
        prepareJob = null
        adapter.setActive(activeId, null)
        player?.let {
            if (preparedId != null) {
                resumeId = preparedId
                resumePosition = it.currentPosition
            }
            it.release()
        }
        player = null
        preparedId = null
        preparedLocal = false
        acquired = false
        super.onStop()
    }

    override fun onDestroyView() {
        binding = null
        positioned = false
        actionStates.clear()
        super.onDestroyView()
    }

    override fun onDestroy() {
        val activity = activity
        if (videoFullscreen && activity != null && !activity.isChangingConfigurations) {
            activity.requestedOrientation = orientationBeforeFullscreen
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Before API 28, this occurs before onStop
        val player = player
        if (player != null && preparedId != null) {
            resumeId = preparedId
            resumePosition = player.currentPosition
        }
        outState.putString(KEY_FILE_ID, activeId)
        outState.putString(KEY_RESUME_ID, resumeId)
        outState.putLong(KEY_RESUME_POSITION, resumePosition)
        outState.putBoolean(KEY_IMMERSIVE, immersive)
        outState.putBoolean(KEY_VIDEO_FULLSCREEN, videoFullscreen)
        outState.putInt(KEY_ORIENTATION_BEFORE_FULLSCREEN, orientationBeforeFullscreen)
    }

    private fun render(state: GalleryViewModel.State) {
        adapter.setLocal(state.downloaded - failedOriginals)
        // The active video changes to the local original when its download completes or is found
        val player = player
        val prepared = preparedId
        val active = activeId
        val switchSource = when {
            player == null || active == null || prepareJob != null -> false
            prepared == null -> isLocal(active) && state.files.any { it.id == active && it.type == MediaType.VIDEO }
            else -> prepared == active && preparedLocal != isLocal(active)
        }
        if (player != null && switchSource) {
            if (prepared != null) {
                resumeId = prepared
                resumePosition = player.currentPosition
            }
            preparePlayer()
        }
        adapter.submitList(state.files) {
            val binding = binding ?: return@submitList
            if (!positioned) {
                val index = adapter.currentList.indexOfFirst { it.id == activeId }
                if (index < 0) {
                    LOG.debug("File {} is not in the gallery", activeId)
                    dismissAllowingStateLoss()
                    return@submitList
                }
                binding.galleryViewerPager.setCurrentItem(index, false)
                positioned = true
            } else if (adapter.itemCount == 0) {
                LOG.debug("No more files in the gallery")
                dismissAllowingStateLoss()
                return@submitList
            } else if (binding.galleryViewerPager.currentItem >= adapter.itemCount) {
                // After a delete of the last file, the viewer displays the file before it
                binding.galleryViewerPager.setCurrentItem(adapter.itemCount - 1, false)
            }
            onPageChanged()
        }

        // A Preview fetch fails while the GallerySource is not acquired
        if (state.acquired && !acquired) {
            adapter.reloadImages()
            if (preparedId == null || player?.playerError != null) {
                preparePlayer()
            }
        }
        acquired = state.acquired
    }

    private fun onPageChanged() {
        val binding = binding ?: return
        if (!positioned) {
            return
        }
        val position = binding.galleryViewerPager.currentItem
        val file = adapter.currentList.getOrNull(position) ?: return
        updateToolbar(file)
        if (position >= adapter.itemCount - LOAD_AHEAD_PAGES) {
            viewModel.loadMore()
        }
        if (file.id != activeId) {
            activeId = file.id
            adapter.setActive(file.id, player)
            controlsVisible = false
            updateBarsVisibility(true)
            preparePlayer()
        }
    }

    private fun onAction(action: ViewerAction) {
        val file = viewModel.state.value.files.firstOrNull { it.id == activeId } ?: return
        when (action) {
            ViewerAction.DOWNLOAD -> (requireActivity() as GalleryActivity).download(listOf(file))
            ViewerAction.FAVORITE -> viewModel.setFavorite(listOf(file), !file.favorite)
            ViewerAction.DELETE -> (requireActivity() as GalleryActivity).confirmThenDelete(listOf(file))
        }
    }

    private fun updateToolbar(file: MediaFile) {
        val binding = binding ?: return
        val toolbar = binding.galleryViewerToolbar
        val time = DateUtils.formatDateTime(
            requireContext(),
            file.time,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR
        )
        toolbar.title = file.name
        ViewerAction.entries.forEach { action ->
            val state = actionState(action, file)
            if (actionStates.put(action, state) == state) {
                return@forEach
            }
            if (landscape) {
                toolbar.menu.findItem(action.menuId)?.let { applyActionState(it, state) }
            } else {
                applyActionState(actionButton(binding, action), state)
            }
        }
        toolbar.subtitle = if (file.type == MediaType.VIDEO) {
            getString(R.string.date_placeholders__date__time, time, GalleryAdapter.formatDuration(file.duration))
        } else {
            time
        }
    }

    private fun actionState(action: ViewerAction, file: MediaFile): ActionState {
        val capabilities = viewModel.source.capabilities
        return when (action) {
            ViewerAction.DOWNLOAD -> when (viewModel.state.value.downloadStatus(file.id)) {
                DownloadStatus.NONE ->
                    ActionState(R.drawable.ic_download, R.string.ftp_file_browser_download)

                DownloadStatus.QUEUED ->
                    ActionState(R.drawable.ic_access_time, R.string.gallery_download_queued, enabled = false)

                DownloadStatus.DOWNLOADING ->
                    ActionState(R.drawable.ic_downloading, R.string.busy_task_downloading, enabled = false)

                DownloadStatus.DOWNLOADED ->
                    ActionState(R.drawable.ic_download_done, R.string.gallery_downloaded, enabled = false)
            }

            ViewerAction.FAVORITE -> if (file.favorite) {
                ActionState(
                    R.drawable.ic_star_gold,
                    R.string.gallery_unfavorite,
                    visible = GalleryCapability.FAVORITE in capabilities,
                    tinted = false
                )
            } else {
                ActionState(
                    R.drawable.ic_star_gray,
                    R.string.gallery_favorite,
                    visible = GalleryCapability.FAVORITE in capabilities
                )
            }

            ViewerAction.DELETE ->
                ActionState(R.drawable.ic_delete, R.string.delete, visible = GalleryCapability.DELETE in capabilities)
        }
    }

    private fun applyActionState(item: MenuItem, state: ActionState) {
        item.setIcon(state.icon)
        item.setTitle(state.label)
        item.isEnabled = state.enabled
        item.isVisible = state.visible
    }

    private fun applyActionState(button: MaterialButton, state: ActionState) {
        button.iconTint =
            if (state.tinted) ContextCompat.getColorStateList(button.context, R.color.gallery_viewer_action) else null
        button.setIconResource(state.icon)
        button.setText(state.label)
        button.isEnabled = state.enabled
        button.isVisible = state.visible
    }

    private fun actionButton(binding: FragmentGalleryViewerBinding, action: ViewerAction) = when (action) {
        ViewerAction.DOWNLOAD -> binding.galleryViewerDownload
        ViewerAction.FAVORITE -> binding.galleryViewerFavorite
        ViewerAction.DELETE -> binding.galleryViewerDelete
    }

    /**
     * Stops the player, and plays the active file when it is a video: the local original when the file is
     * downloaded, else the preview.
     */
    private fun preparePlayer() {
        val player = player ?: return
        prepareJob?.cancel()
        prepareJob = null
        player.stop()
        player.clearMediaItems()
        preparedId = null
        preparedLocal = false

        val state = viewModel.state.value
        val file = state.files.firstOrNull { it.id == activeId }
        if (file == null || file.type != MediaType.VIDEO) {
            return
        }

        if (isLocal(file.id)) {
            prepareJob = viewLifecycleOwner.lifecycleScope.launch {
                val uri = viewModel.findOriginal(file)
                prepareJob = null
                if (uri == null) {
                    LOG.warn("The original of {} is not in the download folder, playing the preview", file.id)
                    onOriginalFailed(file, true)
                    preparePlayer()
                    return@launch
                }
                val startPosition = getStartPosition(file)
                LOG.debug("Preparing the original of {} from {} at {} ms", file.id, uri, startPosition)
                player.setMediaItem(MediaItem.fromUri(uri), startPosition)
                player.playWhenReady = true
                player.prepare()
                preparedId = file.id
                preparedLocal = true
            }
            return
        }

        // The player must not open the Preview before the GallerySource is acquired
        if (!state.acquired) {
            return
        }
        val startPosition = getStartPosition(file)
        LOG.debug("Preparing the preview of {} at {} ms", file.id, startPosition)
        val mediaSource = ProgressiveMediaSource.Factory(
            GalleryPreviewCaches.dataSourceFactory(viewModel.device, viewModel.source, file)
        ).createMediaSource(MediaItem.fromUri(Uri.Builder().scheme(URI_SCHEME).appendPath(file.id).build()))
        player.setMediaSource(mediaSource, startPosition)
        player.playWhenReady = true
        player.prepare()
        preparedId = file.id
    }

    private fun getStartPosition(file: MediaFile): Long {
        val position = if (resumeId == file.id) resumePosition else 0L
        resumeId = null
        return position
    }

    private fun isLocal(id: String) = id in viewModel.state.value.downloaded && id !in failedOriginals

    private fun onOriginalFailed(file: MediaFile, missing: Boolean) {
        failedOriginals.add(file.id)
        if (missing) {
            viewModel.setNotDownloaded(file)
        }
    }

    private fun setImmersive(immersive: Boolean, videoFullscreen: Boolean) {
        if (immersive == this.immersive && videoFullscreen == this.videoFullscreen) {
            return
        }
        LOG.debug("Immersive: {}, video fullscreen: {}", immersive, videoFullscreen)
        val activity = requireActivity()
        if (videoFullscreen && !this.videoFullscreen) {
            orientationBeforeFullscreen = activity.requestedOrientation
        } else if (!videoFullscreen && this.videoFullscreen) {
            activity.requestedOrientation = orientationBeforeFullscreen
        }
        this.immersive = immersive
        this.videoFullscreen = videoFullscreen
        applyImmersive(true)
    }

    /**
     * When immersive, hides the system bars and the bars of the viewer. In video fullscreen, also sets the
     * landscape orientation. Else, displays the system bars and the bars of the viewer.
     */
    private fun applyImmersive(animate: Boolean) {
        if (videoFullscreen) {
            // The orientation request is lost when the process is restored
            requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        immersiveBackCallback.isEnabled = immersive
        adapter.setFullscreen(videoFullscreen)
        updateBarsVisibility(animate)
        val window = dialog?.window ?: return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (immersive) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * Displays the toolbar, and the action bar in portrait, when not immersive or together with the controls
     * of the active video.
     */
    private fun updateBarsVisibility(animate: Boolean) {
        val binding = binding ?: return
        val visible = !immersive || controlsVisible
        fade(binding.galleryViewerToolbar, visible, animate)
        if (!landscape) {
            fade(binding.galleryViewerActions, visible, animate)
        }
    }

    private fun fade(view: View, visible: Boolean, animate: Boolean) {
        view.animate().cancel()
        if (!animate) {
            view.alpha = 1f
            view.isInvisible = !visible
        } else if (visible) {
            if (view.isInvisible) {
                view.alpha = 0f
                view.isInvisible = false
            }
            view.animate().alpha(1f).setDuration(BARS_FADE_MS)
        } else if (!view.isInvisible) {
            view.animate().alpha(0f).setDuration(BARS_FADE_MS).withEndAction { view.isInvisible = true }
        }
    }

    private enum class ViewerAction(@IdRes val menuId: Int) {
        DOWNLOAD(R.id.gallery_download),
        FAVORITE(R.id.gallery_favorite),
        DELETE(R.id.gallery_delete),
    }

    /**
     * The state of a [ViewerAction] for the active file. When not tinted, the icon keeps its own colors in the
     * action bar.
     */
    private data class ActionState(
        @DrawableRes val icon: Int,
        @StringRes val label: Int,
        val enabled: Boolean = true,
        val visible: Boolean = true,
        val tinted: Boolean = true,
    )

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(GalleryViewerFragment::class.java)

        const val TAG = "gallery_viewer"

        private const val KEY_FILE_ID = "file_id"
        private const val KEY_RESUME_ID = "resume_id"
        private const val KEY_RESUME_POSITION = "resume_position"
        private const val KEY_IMMERSIVE = "immersive"
        private const val KEY_VIDEO_FULLSCREEN = "video_fullscreen"
        private const val KEY_ORIENTATION_BEFORE_FULLSCREEN = "orientation_before_fullscreen"
        private const val URI_SCHEME = "gallery"
        private const val LOAD_AHEAD_PAGES = 5
        private const val BARS_FADE_MS = 200L

        // The local original of a photo is decoded at most at this multiple of the screen size
        private const val ORIGINAL_SCALE = 2

        fun newInstance(fileId: String) = GalleryViewerFragment().apply {
            arguments = Bundle().apply { putString(KEY_FILE_ID, fileId) }
        }
    }
}
