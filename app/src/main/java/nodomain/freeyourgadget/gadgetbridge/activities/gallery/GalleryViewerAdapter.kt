package nodomain.freeyourgadget.gadgetbridge.activities.gallery

import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import coil3.dispose
import coil3.load
import coil3.request.CachePolicy
import coil3.request.error
import coil3.request.placeholder
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.Size
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemGalleryViewerPhotoBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemGalleryViewerVideoBinding
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryImage
import nodomain.freeyourgadget.gadgetbridge.service.gallery.MediaFile
import nodomain.freeyourgadget.gadgetbridge.service.gallery.MediaType
import nodomain.freeyourgadget.gadgetbridge.service.gallery.Variant
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The full-screen pages of the [MediaFile]s: the photo, or the player of a video.
 * A photo page displays the local original when the file is present locally, else the preview.
 * Only the page of the active file has the player.
 *
 * @param originalSize the maximum size to decode a local original
 */
@OptIn(UnstableApi::class)
class GalleryViewerAdapter(
    private val imageLoader: ImageLoader,
    private val scope: CoroutineScope,
    private val originalSize: Size,
    private val findOriginal: suspend (MediaFile) -> Uri?,
    private val onOriginalFailed: (MediaFile, Boolean) -> Unit,
    private val onFullscreenButtonClick: (Boolean) -> Unit,
    private val onPhotoClick: () -> Unit,
    private val onControlsVisibilityChange: (Boolean) -> Unit,
) : ListAdapter<MediaFile, RecyclerView.ViewHolder>(GalleryAdapter.DIFF) {
    class PhotoHolder(val binding: ItemGalleryViewerPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        var fileId: String? = null
        var job: Job? = null
    }

    class VideoHolder(val binding: ItemGalleryViewerVideoBinding) : RecyclerView.ViewHolder(binding.root) {
        var fileId: String? = null
    }

    private var activeId: String? = null
    private var player: Player? = null
    private var fullscreen = false
    private var controlsBottomPadding = 0

    // The IDs of the files whose local originals the photo pages display
    private var local: Set<String> = emptySet()

    // The player must leave a page at once, before the player is released
    private val videoHolders = HashSet<VideoHolder>()

    fun setActive(id: String?, player: Player?) {
        activeId = id
        this.player = player
        videoHolders.forEach { bindPlayer(it) }
    }

    fun setFullscreen(fullscreen: Boolean) {
        this.fullscreen = fullscreen
        videoHolders.forEach {
            it.binding.galleryViewerPlayer.setFullscreenButtonState(fullscreen)
        }
    }

    fun setControlsBottomPadding(padding: Int) {
        controlsBottomPadding = padding
        videoHolders.forEach { updateControlsPadding(it) }
    }

    /**
     * Sets the IDs of the downloaded files, and loads the photo pages again whose source changed.
     */
    fun setLocal(ids: Set<String>) {
        if (ids == local) {
            return
        }
        val changed = (ids - local) + (local - ids)
        local = ids
        currentList.forEachIndexed { position, file ->
            if (file.type != MediaType.VIDEO && file.id in changed) {
                notifyItemChanged(position, PAYLOAD_SOURCE)
            }
        }
    }

    fun reloadImages() {
        notifyItemRangeChanged(0, itemCount)
    }

    override fun getItemViewType(position: Int): Int = getItem(position).type.ordinal

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == MediaType.VIDEO.ordinal) {
            VideoHolder(ItemGalleryViewerVideoBinding.inflate(inflater, parent, false)).also { holder ->
                holder.binding.galleryViewerPlayer.setFullscreenButtonClickListener { fullscreen ->
                    onFullscreenButtonClick(fullscreen)
                }
                holder.binding.galleryViewerPlayer.setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        if (holder.fileId == activeId) {
                            onControlsVisibilityChange(visibility == View.VISIBLE)
                        }
                    }
                )
            }
        } else {
            PhotoHolder(ItemGalleryViewerPhotoBinding.inflate(inflater, parent, false)).also {
                it.binding.galleryViewerImage.setOnClickListener { onPhotoClick() }
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val file = getItem(position)
        when (holder) {
            is PhotoHolder -> bindPhoto(holder, file)

            is VideoHolder -> {
                holder.binding.galleryViewerPlayer.contentDescription = file.name
                loadImage(holder.binding.galleryViewerImage, file, Variant.THUMBNAIL, null) {}
                holder.fileId = file.id
                videoHolders.add(holder)
                bindPlayer(holder)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        // The pages do not display the favorite flag
        if (payloads.isNotEmpty() && payloads.all { it == GalleryAdapter.PAYLOAD_FAVORITE }) {
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        when (holder) {
            is PhotoHolder -> {
                holder.job?.cancel()
                holder.job = null
                holder.fileId = null
                holder.binding.galleryViewerImage.dispose()
                // A pooled page must not keep the decoded original
                holder.binding.galleryViewerImage.setImageDrawable(null)
            }

            is VideoHolder -> {
                holder.binding.galleryViewerImage.dispose()
                holder.binding.galleryViewerPlayer.player = null
                holder.fileId = null
                videoHolders.remove(holder)
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        // A page that leaves the screen displays its photo at fit-center when it comes back
        if (holder is PhotoHolder) {
            holder.binding.galleryViewerImage.resetZoom()
        }
    }

    private fun bindPlayer(holder: VideoHolder) {
        val playerView = holder.binding.galleryViewerPlayer
        playerView.player = if (holder.fileId == activeId) player else null
        // A page that becomes active again starts with hidden controls
        if (playerView.player == null) {
            playerView.hideController()
        }
        playerView.setFullscreenButtonState(fullscreen)
        updateControlsPadding(holder)
    }

    private fun updateControlsPadding(holder: VideoHolder) {
        val controls = holder.binding.galleryViewerPlayer.findViewById<View>(androidx.media3.ui.R.id.exo_controller)
        controls?.updatePadding(bottom = controlsBottomPadding)
    }

    private fun bindPhoto(holder: PhotoHolder, file: MediaFile) {
        val binding = holder.binding
        holder.job?.cancel()
        holder.job = null
        // The page displays its current image until the new image is decoded
        val placeholder = if (holder.fileId == file.id) binding.galleryViewerImage.drawable else null
        if (holder.fileId != file.id) {
            binding.galleryViewerImage.resetZoom()
        }
        holder.fileId = file.id
        binding.galleryViewerImage.contentDescription = file.name
        if (file.id !in local) {
            LOG.debug("Displaying the preview of {}", file.id)
            loadPreview(holder, file, placeholder)
            return
        }

        binding.galleryViewerImage.dispose()
        binding.galleryViewerImage.setImageDrawable(placeholder)
        binding.galleryViewerProgress.isVisible = placeholder == null
        holder.job = scope.launch {
            val uri = findOriginal(file)
            if (uri == null) {
                LOG.warn("The original of {} is not in the download folder, displaying the preview", file.id)
                displayPreviewInstead(holder, file, placeholder, true)
                return@launch
            }
            LOG.debug("Displaying the original of {} from {}", file.id, uri)
            binding.galleryViewerImage.load(uri, imageLoader) {
                size(originalSize)
                precision(Precision.INEXACT)
                // Coil reads the MATRIX scale type of the view as FILL, which decodes a larger bitmap
                scale(Scale.FIT)
                // The original is a local file, and it must not use the caches of the device
                memoryCachePolicy(CachePolicy.DISABLED)
                diskCachePolicy(CachePolicy.DISABLED)
                placeholder(placeholder)
                listener(
                    onSuccess = { _, _ -> binding.galleryViewerProgress.isVisible = false },
                    onError = { _, result ->
                        LOG.warn("Failed to load the original of {}, displaying the preview", file.id, result.throwable)
                        displayPreviewInstead(holder, file, placeholder, result.throwable is FileNotFoundException)
                    }
                )
            }
        }
    }

    private fun displayPreviewInstead(holder: PhotoHolder, file: MediaFile, placeholder: Drawable?, missing: Boolean) {
        local = local - file.id
        onOriginalFailed(file, missing)
        if (holder.fileId == file.id) {
            loadPreview(holder, file, placeholder)
        }
    }

    private fun loadPreview(holder: PhotoHolder, file: MediaFile, placeholder: Drawable?) {
        val binding = holder.binding
        binding.galleryViewerProgress.isVisible = placeholder == null
        loadImage(binding.galleryViewerImage, file, Variant.PREVIEW, placeholder) {
            binding.galleryViewerProgress.isVisible = false
        }
    }

    private fun loadImage(
        view: ImageView,
        file: MediaFile,
        variant: Variant,
        placeholder: Drawable?,
        onDone: () -> Unit
    ) {
        view.load(GalleryImage(file, variant), imageLoader) {
            // Without a size, Coil waits for a pre-draw pass of the page
            size(Size.ORIGINAL)
            placeholder(placeholder)
            error(R.drawable.ic_remove_image)
            listener(
                onSuccess = { _, _ -> onDone() },
                onError = { _, result ->
                    onDone()
                    LOG.error("Failed to load the {} of {}", variant, file.id, result.throwable)
                }
            )
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(GalleryViewerAdapter::class.java)

        private const val PAYLOAD_SOURCE = "source"
    }
}
