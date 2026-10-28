package nodomain.freeyourgadget.gadgetbridge.activities.gallery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryActionException
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryDownloadQueue
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryDownloadQueues
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryFilter
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryImageLoaders
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryPreviewCaches
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GallerySource
import nodomain.freeyourgadget.gadgetbridge.service.gallery.MediaFile
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The gallery of one [GallerySource]: the pages loaded for the current filter, and the selection.
 */
class GalleryViewModel(
    val device: GBDevice,
    val source: GallerySource,
    val imageLoader: ImageLoader,
) : ViewModel() {
    enum class Action {
        FAVORITE,
        UNFAVORITE,
        DELETE,
    }

    enum class DownloadStatus {
        NONE,
        QUEUED,
        DOWNLOADING,
        DOWNLOADED,
    }

    /**
     * @property acquired true while the [GallerySource] is acquired
     * @property error the message of the last load failure, or null
     * @property complete true when the last page is loaded
     * @property selected the IDs of the selected [MediaFile]s
     * @property action the [Action] that runs on the device, or null
     * @property downloaded the IDs of the [MediaFile]s whose originals are in the download folder
     * @property downloads the state of the download queue of the device
     */
    data class State(
        val filter: GalleryFilter = GalleryFilter.ALL,
        val files: List<MediaFile> = emptyList(),
        val loading: Boolean = false,
        val acquired: Boolean = false,
        val error: String? = null,
        val complete: Boolean = false,
        val selected: Set<String> = emptySet(),
        val action: Action? = null,
        val downloaded: Set<String> = emptySet(),
        val downloads: GalleryDownloadQueue.State = GalleryDownloadQueue.State(),
    ) {
        fun downloadStatus(id: String): DownloadStatus = when {
            id == downloads.current -> DownloadStatus.DOWNLOADING
            id in downloads.queued -> DownloadStatus.QUEUED
            id in downloaded -> DownloadStatus.DOWNLOADED
            else -> DownloadStatus.NONE
        }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    data class ActionError(val action: Action, val message: String)

    private val _actionErrors = Channel<ActionError>(Channel.BUFFERED)
    val actionErrors: Flow<ActionError> = _actionErrors.receiveAsFlow()

    private var next: String? = null
    private var acquireJob: Job? = null
    private var loadJob: Job? = null

    // A page that was requested before a delete can contain the deleted files
    private val deletedIds = HashSet<String>()

    private val downloadQueue = GalleryDownloadQueues.get(device, source)

    init {
        downloadQueue?.let { queue ->
            viewModelScope.launch {
                var seen = queue.state.value.completed
                queue.state.collect { downloads ->
                    val completed = downloads.completed - seen
                    seen = downloads.completed
                    _state.update {
                        it.copy(
                            downloaded = if (completed.isEmpty()) it.downloaded else it.downloaded + completed,
                            downloads = downloads
                        )
                    }
                }
            }
        }
    }

    /**
     * Checks the downloaded state of the loaded files again. Then, acquires the [GallerySource], and loads the
     * next page if no page is loaded, or if the last load failed.
     */
    fun onVisible() {
        val current = _state.value
        // The user can delete the downloaded files in another app
        checkDownloaded(current.files)
        if (current.acquired || acquireJob?.isActive == true) {
            return
        }
        val load = current.files.isEmpty() || current.error != null
        _state.update { it.copy(loading = true, error = null) }
        acquireJob = viewModelScope.launch {
            try {
                source.acquire()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.error("Failed to acquire the gallery source", e)
                acquireJob = null
                _state.update { it.copy(loading = false, error = errorMessage(e)) }
                return@launch
            }
            acquireJob = null
            _state.update { it.copy(loading = loadJob?.isActive == true, acquired = true) }
            if (load) {
                loadMore()
            }
        }
    }

    fun onHidden() {
        acquireJob?.cancel()
        acquireJob = null
        if (_state.value.acquired) {
            source.release()
        }
        _state.update { it.copy(loading = loadJob?.isActive == true, acquired = false) }
    }

    /**
     * Loads the next page, if no page is loading and the last page is not loaded.
     */
    fun loadMore() {
        val current = _state.value
        if (!current.acquired || loadJob?.isCompleted == false || current.complete || current.error != null) {
            return
        }
        val filter = current.filter
        val after = next
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val page = source.listPage(filter, after)
                LOG.debug("Loaded {} files for {} after {}, next {}", page.files.size, filter, after, page.next)
                next = page.next
                val loaded = page.files.filter { file -> file.id !in deletedIds }
                _state.update {
                    it.copy(
                        files = (it.files + loaded).distinctBy { file -> file.id },
                        loading = false,
                        complete = page.next == null
                    )
                }
                checkDownloaded(loaded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.error("Failed to load the files for {} after {}", filter, after, e)
                _state.update { it.copy(loading = false, error = errorMessage(e)) }
            }
        }
        // The state update runs the collectors at once, and they can call loadMore again
        loadJob = job
        _state.update { it.copy(loading = true) }
        job.start()
    }

    fun retry() {
        _state.update { it.copy(error = null) }
        if (_state.value.acquired) {
            loadMore()
        } else {
            onVisible()
        }
    }

    fun setFilter(filter: GalleryFilter) {
        if (filter == _state.value.filter) {
            return
        }
        LOG.debug("Filter changed to {}", filter)
        loadJob?.cancel()
        loadJob = null
        next = null
        _state.update {
            State(
                filter = filter,
                loading = acquireJob?.isActive == true,
                acquired = it.acquired,
                error = if (it.acquired) null else it.error,
                action = it.action,
                downloaded = it.downloaded,
                downloads = it.downloads
            )
        }
        loadMore()
    }

    fun toggleSelection(file: MediaFile) {
        _state.update {
            val selected = if (file.id in it.selected) it.selected - file.id else it.selected + file.id
            it.copy(selected = selected)
        }
    }

    /**
     * Selects the files in [ids]. When all of them are selected, deselects them.
     */
    fun toggleSelection(ids: Collection<String>) {
        _state.update {
            val selected = if (it.selected.containsAll(ids)) it.selected - ids.toSet() else it.selected + ids
            it.copy(selected = selected)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    /**
     * Sets or clears the favorite flag of the files on the device. The list changes after the device acks.
     */
    fun setFavorite(files: List<MediaFile>, favorite: Boolean) {
        runAction(if (favorite) Action.FAVORITE else Action.UNFAVORITE, files) { source.setFavorite(files, favorite) }
    }

    /**
     * Deletes the files from the device. The list changes after the device acks.
     */
    fun delete(files: List<MediaFile>) {
        runAction(Action.DELETE, files) { source.delete(files) }
    }

    /**
     * Runs [command] when no other [Action] runs. Then, applies the action to the files that the device changed.
     */
    private fun runAction(action: Action, files: List<MediaFile>, command: suspend () -> Unit) {
        val current = _state.value
        if (files.isEmpty() || current.action != null || !current.acquired) {
            LOG.warn(
                "Ignoring {} for {} files, action={}, acquired={}",
                action,
                files.size,
                current.action,
                current.acquired
            )
            return
        }
        LOG.debug("Starting {} for {} files", action, files.size)
        _state.update { it.copy(action = action) }
        viewModelScope.launch {
            val applied = try {
                command()
                LOG.debug("{} done for {} files", action, files.size)
                files
            } catch (e: CancellationException) {
                throw e
            } catch (e: GalleryActionException) {
                LOG.error("{} failed after {} of {} files", action, e.applied.size, files.size, e)
                _actionErrors.trySend(ActionError(action, errorMessage(e)))
                e.applied
            } catch (e: Exception) {
                LOG.error("{} failed for {} files", action, files.size, e)
                _actionErrors.trySend(ActionError(action, errorMessage(e)))
                emptyList()
            }
            val ids = applied.mapTo(HashSet()) { it.id }
            if (action == Action.DELETE) {
                deletedIds.addAll(ids)
            }
            _state.update { it.withApplied(action, ids, applied.size == files.size) }
            if (action == Action.DELETE && applied.isNotEmpty()) {
                removeDeleted(applied)
            }
        }
    }

    /**
     * Returns the state after the device applied [action] to the files in [ids].
     * When [complete] is true, the selection ends.
     */
    private fun State.withApplied(action: Action, ids: Set<String>, complete: Boolean): State {
        val removed = action == Action.DELETE || (action == Action.UNFAVORITE && filter == GalleryFilter.FAVORITES)
        val updated = when {
            ids.isEmpty() -> files
            removed -> files.filter { it.id !in ids }
            else -> files.map { if (it.id in ids) it.copy(favorite = action == Action.FAVORITE) else it }
        }
        val present = updated.mapTo(HashSet()) { it.id }
        return copy(
            files = updated,
            action = null,
            selected = if (complete) emptySet() else selected.filterTo(HashSet()) { it in present }
        )
    }

    /**
     * Finds the files whose originals are in the download folder, with one media store query for each page,
     * and updates [State.downloaded] for them.
     */
    private fun checkDownloaded(files: List<MediaFile>) {
        val queue = downloadQueue ?: return
        if (files.isEmpty()) {
            return
        }
        viewModelScope.launch {
            for (page in files.chunked(PAGE_SIZE)) {
                val completedBefore = queue.state.value.completed
                val found = try {
                    withContext(Dispatchers.IO) { queue.store.findDownloaded(page) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LOG.error("Failed to find the downloaded files", e)
                    return@launch
                }
                LOG.debug(
                    "Found {} of {} files from {} to {} in the download folder",
                    found.size,
                    page.size,
                    page.first().name,
                    page.last().name
                )
                // A file that completes during the query is downloaded, also when the query did not find it
                val completed = queue.state.value.completed - completedBefore
                val notFound = page.mapTo(HashSet()) { it.id } - found - completed
                _state.update { it.copy(downloaded = it.downloaded - notFound + found) }
            }
        }
    }

    /**
     * Returns the URI of the original of [file] in the download folder, or null when it is not there.
     */
    suspend fun findOriginal(file: MediaFile): Uri? {
        val queue = downloadQueue ?: return null
        return try {
            withContext(Dispatchers.IO) { queue.store.findOriginal(file) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.error("Failed to find the original of {}", file.id, e)
            null
        }
    }

    /**
     * Removes [file] from [State.downloaded].
     */
    fun setNotDownloaded(file: MediaFile) {
        LOG.debug("The original of {} is not in the download folder", file.id)
        _state.update { it.copy(downloaded = it.downloaded - file.id) }
    }

    /**
     * Removes the deleted files from the download queue and from the caches of the device.
     */
    private suspend fun removeDeleted(files: List<MediaFile>) {
        GalleryDownloadQueues.remove(device.address, files.mapTo(HashSet()) { it.id })
        withContext(Dispatchers.IO) {
            try {
                GalleryImageLoaders.remove(device.address, files)
            } catch (e: Exception) {
                // The disk cache is closed when the device disconnects
                LOG.warn("Failed to remove the deleted files from the image caches", e)
            }
            try {
                GalleryPreviewCaches.remove(device, source, files)
            } catch (e: Exception) {
                LOG.warn("Failed to remove the deleted files from the preview cache", e)
            }
        }
    }

    /**
     * Clears the gallery cache of the device, from the memory cache and from the disk cache.
     * Includes both the thumbnails and previews.
     */
    suspend fun clearCache() {
        LOG.debug("Clearing the gallery caches of {}", device.address)
        withContext(Dispatchers.IO) {
            try {
                GalleryImageLoaders.clearCache(device.address)
            } catch (e: Exception) {
                // The disk cache is closed when the device disconnects
                LOG.warn("Failed to clear the disk cache", e)
            }
            try {
                GalleryPreviewCaches.clearCache(device)
            } catch (e: Exception) {
                LOG.warn("Failed to clear the preview cache", e)
            }
        }
    }

    override fun onCleared() {
        onHidden()
    }

    private fun errorMessage(e: Exception): String = e.localizedMessage ?: e.javaClass.simpleName

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(GalleryViewModel::class.java)

        private const val PAGE_SIZE = 45
    }
}
