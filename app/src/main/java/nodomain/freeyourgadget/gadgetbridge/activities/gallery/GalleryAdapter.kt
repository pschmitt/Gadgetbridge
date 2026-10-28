package nodomain.freeyourgadget.gadgetbridge.activities.gallery

import android.content.Context
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import coil3.dispose
import coil3.load
import coil3.request.error
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.gallery.GalleryViewModel.DownloadStatus
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemGalleryFileBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemGalleryHeaderBinding
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryImage
import nodomain.freeyourgadget.gadgetbridge.service.gallery.MediaFile
import nodomain.freeyourgadget.gadgetbridge.service.gallery.MediaType
import nodomain.freeyourgadget.gadgetbridge.service.gallery.Variant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * The grid items: the day headers, and the cells of the [MediaFile]s with thumbnail, video duration, favorite flag,
 * selection mark and download state.
 */
class GalleryAdapter(
    private val imageLoader: ImageLoader,
    private val thumbnailSize: Int,
    private val onClick: (MediaFile) -> Unit,
    private val onLongClick: (MediaFile) -> Unit,
    private val onHeaderClick: (GalleryItem.Header) -> Unit,
    private val onHeaderLongClick: (GalleryItem.Header) -> Unit,
) : ListAdapter<GalleryItem, RecyclerView.ViewHolder>(ITEM_DIFF) {
    class ViewHolder(val binding: ItemGalleryFileBinding) : RecyclerView.ViewHolder(binding.root)

    class HeaderViewHolder(val binding: ItemGalleryHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    private var submittedFiles: List<MediaFile>? = null

    private var selected: Set<String> = emptySet()

    private var downloadState = GalleryViewModel.State()

    /**
     * Displays the items of [files]. Runs [onCommitted] when the items are in the adapter.
     */
    fun submitFiles(files: List<MediaFile>, onCommitted: Runnable) {
        if (files === submittedFiles) {
            onCommitted.run()
            return
        }
        submittedFiles = files
        submitList(GalleryItem.of(files), onCommitted)
    }

    fun isHeader(position: Int) = getItemViewType(position) == VIEW_TYPE_HEADER

    fun setSelected(selected: Set<String>) {
        if (selected == this.selected) {
            return
        }
        this.selected = selected
        // The bind reads the selection by file ID, so that the positions of a pending diff do not matter
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
    }

    fun setDownloadState(state: GalleryViewModel.State) {
        val old = downloadState
        if (state.downloaded == old.downloaded && state.downloads == old.downloads) {
            return
        }
        downloadState = state
        val current = state.downloads.current
        val progressChanged = state.downloads.progress != old.downloads.progress
        currentList.forEachIndexed { index, item ->
            val id = (item as? GalleryItem.File)?.file?.id ?: return@forEachIndexed
            if (old.downloadStatus(id) != state.downloadStatus(id) || (progressChanged && id == current)) {
                notifyItemChanged(index, PAYLOAD_DOWNLOAD)
            }
        }
    }

    fun reloadThumbnails() {
        notifyItemRangeChanged(0, itemCount)
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is GalleryItem.Header -> VIEW_TYPE_HEADER
        is GalleryItem.File -> VIEW_TYPE_FILE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        if (viewType == VIEW_TYPE_HEADER) {
            val binding = ItemGalleryHeaderBinding.inflate(inflater, parent, false)
            val holder = HeaderViewHolder(binding)
            binding.root.setOnClickListener {
                itemAt<GalleryItem.Header>(holder)?.let(onHeaderClick)
            }
            binding.root.setOnLongClickListener {
                val header = itemAt<GalleryItem.Header>(holder) ?: return@setOnLongClickListener false
                onHeaderLongClick(header)
                true
            }
            return holder
        }
        val binding = ItemGalleryFileBinding.inflate(inflater, parent, false)
        val holder = ViewHolder(binding)
        binding.root.setOnClickListener {
            itemAt<GalleryItem.File>(holder)?.file?.let(onClick)
        }
        binding.root.setOnLongClickListener {
            val file = itemAt<GalleryItem.File>(holder)?.file ?: return@setOnLongClickListener false
            onLongClick(file)
            true
        }
        return holder
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (holder is ViewHolder && payloads.isNotEmpty() && payloads.all { it in PARTIAL_PAYLOADS }) {
            val file = (getItem(position) as GalleryItem.File).file
            bindSelection(holder.binding, file)
            bindDownload(holder.binding, file)
            holder.binding.galleryFileFavorite.isVisible = file.favorite
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> bindHeader(holder.binding, getItem(position) as GalleryItem.Header)
            is ViewHolder -> bindFile(holder.binding, (getItem(position) as GalleryItem.File).file)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ViewHolder) {
            holder.binding.galleryFileThumbnail.dispose()
        }
    }

    private fun bindHeader(binding: ItemGalleryHeaderBinding, header: GalleryItem.Header) {
        binding.galleryHeaderDate.text = formatDay(binding.root.context, header.day)
        binding.galleryHeaderCheck.isInvisible = selected.isEmpty()
        binding.galleryHeaderCheck.isActivated = selected.containsAll(header.ids)
    }

    private fun bindFile(binding: ItemGalleryFileBinding, file: MediaFile) {
        binding.root.contentDescription = file.name
        bindSelection(binding, file)
        binding.galleryFileThumbnail.load(GalleryImage(file, Variant.THUMBNAIL), imageLoader) {
            // Without a size, Coil waits for a pre-draw pass in which the new cell has a size.
            // This would not display anything until the user tried to scroll.
            size(thumbnailSize)
            error(R.drawable.ic_remove_image)
            listener(onError = { _, result ->
                LOG.error("Failed to load the thumbnail of {}", file.id, result.throwable)
            })
        }
        binding.galleryFileVideo.isVisible = file.type == MediaType.VIDEO
        binding.galleryFileDuration.text = formatDuration(file.duration)
        binding.galleryFileFavorite.isVisible = file.favorite
        bindDownload(binding, file)
    }

    private fun bindSelection(binding: ItemGalleryFileBinding, file: MediaFile) {
        val checked = file.id in selected
        binding.root.isChecked = checked
        binding.galleryFileSelectedOverlay.isInvisible = !checked
        binding.galleryFileCheck.isInvisible = selected.isEmpty()
        binding.galleryFileCheck.isActivated = checked
    }

    private fun bindDownload(binding: ItemGalleryFileBinding, file: MediaFile) {
        val status = downloadState.downloadStatus(file.id)
        binding.galleryFileDownload.isInvisible = status == DownloadStatus.NONE
        when (status) {
            DownloadStatus.NONE -> Unit
            DownloadStatus.QUEUED -> binding.galleryFileDownload.setImageResource(R.drawable.ic_access_time)
            DownloadStatus.DOWNLOADING -> binding.galleryFileDownload.setImageResource(R.drawable.ic_download)
            DownloadStatus.DOWNLOADED -> binding.galleryFileDownload.setImageResource(R.drawable.ic_download_done)
        }
        binding.galleryFileDownloadProgress.isInvisible = status != DownloadStatus.DOWNLOADING
        if (status == DownloadStatus.DOWNLOADING) {
            binding.galleryFileDownloadProgress.setProgressCompat(downloadState.downloads.progress, true)
        }
    }

    private inline fun <reified T : GalleryItem> itemAt(holder: RecyclerView.ViewHolder): T? {
        val position = holder.bindingAdapterPosition
        return if (position == RecyclerView.NO_POSITION) null else getItem(position) as? T
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(GalleryAdapter::class.java)

        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_FILE = 1

        private const val PAYLOAD_SELECTION = "selection"
        private const val PAYLOAD_DOWNLOAD = "download"
        const val PAYLOAD_FAVORITE = "favorite"
        private val PARTIAL_PAYLOADS = setOf(PAYLOAD_SELECTION, PAYLOAD_FAVORITE, PAYLOAD_DOWNLOAD)

        fun formatDuration(seconds: Int): String {
            return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
        }

        private fun formatDay(context: Context, day: LocalDate): String {
            val today = LocalDate.now()
            return when (day) {
                today -> context.getString(R.string.activity_summary_today)
                today.minusDays(1) -> context.getString(R.string.activity_summary_yesterday)
                else -> DateUtils.formatDateTime(
                    context,
                    day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_YEAR
                )
            }
        }

        val DIFF = object : DiffUtil.ItemCallback<MediaFile>() {
            override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile) = oldItem == newItem

            // The images do not change - their cache keys do not contain the favorite flag
            override fun getChangePayload(oldItem: MediaFile, newItem: MediaFile): Any? {
                return if (oldItem.copy(favorite = newItem.favorite) == newItem) PAYLOAD_FAVORITE else null
            }
        }

        private val ITEM_DIFF = object : DiffUtil.ItemCallback<GalleryItem>() {
            override fun areItemsTheSame(oldItem: GalleryItem, newItem: GalleryItem) = when (oldItem) {
                is GalleryItem.Header if newItem is GalleryItem.Header ->
                    // One day can have two runs of files, thus the first ID is also part of the key
                    oldItem.day == newItem.day && oldItem.ids.first() == newItem.ids.first()

                is GalleryItem.File if newItem is GalleryItem.File -> oldItem.file.id == newItem.file.id
                else -> false
            }

            override fun areContentsTheSame(oldItem: GalleryItem, newItem: GalleryItem) = oldItem == newItem

            // Only the IDs of a header can change, and they change only its check
            override fun getChangePayload(oldItem: GalleryItem, newItem: GalleryItem): Any? {
                return if (oldItem is GalleryItem.File && newItem is GalleryItem.File) {
                    DIFF.getChangePayload(oldItem.file, newItem.file)
                } else {
                    PAYLOAD_SELECTION
                }
            }
        }
    }
}

/**
 * The spinner below the grid while a page loads.
 */
class GalleryFooterAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var loading = false
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (value) {
                notifyItemInserted(0)
            } else {
                notifyItemRemoved(0)
            }
        }

    override fun getItemCount(): Int = if (loading) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery_footer, parent, false)
        return object : RecyclerView.ViewHolder(view) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
}
