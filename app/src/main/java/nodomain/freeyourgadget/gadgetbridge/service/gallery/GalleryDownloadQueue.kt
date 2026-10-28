package nodomain.freeyourgadget.gadgetbridge.service.gallery

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Downloads the originals of [MediaFile]s into its [GalleryMediaStore] folder, one file at a time,
 * in the order they are queued. It holds a [GallerySource.acquire] call while files are in the queue.
 */
class GalleryDownloadQueue(
    private val context: Context,
    private val address: String,
    deviceName: String,
    private val source: GallerySource,
) {
    private enum class Stop {
        NONE,
        CANCELLED,
        FAILED,
    }

    /**
     * The files of the queue.
     *
     * @property queued the IDs of the files that wait for their download
     * @property current the ID of the file that downloads, or null
     * @property progress the percentage of the current file that is downloaded
     * @property completed the IDs of the files that this queue downloaded or skipped
     */
    data class State(
        val queued: Set<String> = emptySet(),
        val current: String? = null,
        val progress: Int = 0,
        val completed: Set<String> = emptySet(),
    )

    val store = GalleryMediaStore(context, deviceName)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    // Guards all the fields below
    private val lock = Any()
    private val queue = ArrayDeque<MediaFile>()
    private var worker: Job? = null
    private var current: MediaFile? = null
    private var currentStream: GalleryStream? = null
    private var shutDown = false
    private var count = 0
    private var downloaded = 0
    private var skipped = 0
    private var failed = 0
    private var lastUri: Uri? = null
    private var completed: Set<String> = emptySet()

    // The sum of the sizes of the files of the run, and of the files that are downloaded
    private var totalBytes = 0L
    private var doneBytes = 0L

    @Volatile
    private var stopReason = Stop.NONE

    @Volatile
    private var currentRemoved = false

    /**
     * Adds the files that are not in the queue to its end, and starts the queue if it is not running.
     * Returns the number of files that were added.
     */
    fun enqueue(files: List<MediaFile>): Int {
        synchronized(lock) {
            if (shutDown) {
                LOG.warn("The download queue for {} is shut down", address)
                return 0
            }
            val known = queue.mapTo(HashSet()) { it.id }
            current?.let { known.add(it.id) }
            val added = files.filter { known.add(it.id) }
            LOG.debug("Queued {} of {} files for download from {}", added.size, files.size, address)
            if (added.isEmpty()) {
                return 0
            }
            queue.addAll(added)
            // A file that completes again must change the completed set
            completed = completed - added.mapTo(HashSet()) { it.id }
            if (worker == null) {
                start()
            } else {
                count += added.size
                totalBytes += sizeOf(added)
            }
            publish()
            return added.size
        }
    }

    /**
     * Removes the files from the queue. When the current download is one of them, it stops that download,
     * deletes its partial file and continues with the next file.
     */
    fun remove(ids: Set<String>) {
        val stream = synchronized(lock) {
            val removed = queue.filter { it.id in ids }
            queue.removeAll { it.id in ids }
            val stopCurrent = !currentRemoved && current?.let { it.id in ids } == true
            if (removed.isEmpty() && !stopCurrent) {
                return
            }
            LOG.debug("Removed {} files from the download queue of {}, current={}", removed.size, address, stopCurrent)
            count -= removed.size
            totalBytes -= sizeOf(removed)
            publish()
            // The current file leaves the count only when its download stops before the commit
            if (stopCurrent) {
                currentRemoved = true
            }
            if (stopCurrent) currentStream else null
        }
        closeStream(stream)
    }

    /**
     * Stops the queue, and deletes the partial file of the current download.
     */
    fun cancel() {
        stop(Stop.CANCELLED)
    }

    /**
     * Stops the queue. The current download and the files in the queue fail.
     */
    fun shutdown() {
        synchronized(lock) {
            shutDown = true
        }
        stop(Stop.FAILED)
        scope.cancel()
    }

    private fun start() {
        count = queue.size
        totalBytes = sizeOf(queue)
        doneBytes = 0
        downloaded = 0
        skipped = 0
        failed = 0
        lastUri = null
        stopReason = Stop.NONE
        worker = scope.launch { run() }
    }

    private fun stop(reason: Stop) {
        val stream = synchronized(lock) {
            if (worker == null || stopReason != Stop.NONE) {
                return
            }
            LOG.info("Stopping the download queue of {}: {}, {} files in the queue", address, reason, queue.size)
            stopReason = reason
            if (reason == Stop.FAILED) {
                failed += queue.size
            }
            queue.clear()
            publish()
            currentStream
        }
        closeStream(stream)
    }

    private fun closeStream(stream: GalleryStream?) {
        // A read that is blocked on the stream fails when the stream closes
        try {
            stream?.close()
        } catch (e: IOException) {
            LOG.warn("Failed to close the stream of the current download", e)
        }
    }

    private suspend fun run() {
        var acquired = false
        try {
            source.acquire()
            acquired = true
            while (true) {
                val file = next() ?: break
                download(file)
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                LOG.error("The download queue of {} failed", address, e)
            }
            synchronized(lock) {
                failed += queue.size
                queue.clear()
                stopReason = Stop.FAILED
                finish()
            }
            if (e is CancellationException) {
                throw e
            }
        } finally {
            if (acquired) {
                source.release()
            }
        }
    }

    /**
     * Returns the next file, or ends the run when the queue is empty or stopped.
     */
    private fun next(): MediaFile? = synchronized(lock) {
        val file = if (stopReason == Stop.NONE) queue.removeFirstOrNull() else null
        current = file
        currentRemoved = false
        if (file == null) {
            finish()
        } else {
            publish()
        }
        file
    }

    private fun download(file: MediaFile) {
        notifyProgress(file, 0, 0)
        val wakeLock = AndroidUtils.acquirePartialWakeLock(context, WAKE_LOCK_TAG, WAKE_LOCK_TIMEOUT_MS)
        var target: GalleryMediaStore.Target? = null
        try {
            val created = store.create(file)
            target = created
            if (created == null) {
                synchronized(lock) {
                    skipped++
                    totalBytes -= sizeOf(file)
                    completed = completed + file.id
                    publish()
                }
                return
            }
            val (bytes, length) = source.open(file, Variant.ORIGINAL, 0, -1).use { stream ->
                synchronized(lock) {
                    if (stopReason != Stop.NONE || currentRemoved) {
                        throw IOException("The download is stopped")
                    }
                    currentStream = stream
                }
                val expected = if (stream.length >= 0) stream.length else file.size
                LOG.debug("Receiving the original of {}, {} bytes", file.name, expected)
                if (stream.length >= 0 && file.size > 0 && stream.length != file.size) {
                    LOG.warn(
                        "The original of {} has {} bytes, the file list has {}",
                        file.name,
                        stream.length,
                        file.size
                    )
                }
                created.output.use { output -> copy(file, stream, output, expected) } to expected
            }
            if (length > 0 && bytes != length) {
                throw IOException("Received $bytes of $length bytes of ${file.name}")
            }
            val uri = created.commit()
            LOG.info("Downloaded {}, {} bytes, to {}", file.name, bytes, uri)
            synchronized(lock) {
                downloaded++
                doneBytes += sizeOf(file)
                if (uri != null) {
                    lastUri = uri
                }
                completed = completed + file.id
                publish()
            }
        } catch (e: Exception) {
            target?.delete()
            synchronized(lock) {
                if (stopReason == Stop.CANCELLED) {
                    LOG.info("Cancelled the download of {}", file.name)
                } else if (currentRemoved) {
                    LOG.info("Stopped the download of {}, it is removed from the queue", file.name)
                    count--
                    totalBytes -= sizeOf(file)
                } else {
                    LOG.error("Failed to download {}", file.name, e)
                    failed += 1 + queue.size
                    queue.clear()
                    stopReason = Stop.FAILED
                }
            }
        } finally {
            synchronized(lock) { currentStream = null }
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }
    }

    private fun copy(file: MediaFile, input: InputStream, output: OutputStream, length: Long): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytes = 0L
        var loggedBytes = 0L
        var notifiedAt = SystemClock.elapsedRealtime()
        while (true) {
            if (stopReason != Stop.NONE || currentRemoved) {
                throw IOException("The download is stopped")
            }
            val read = input.read(buffer)
            if (read < 0) {
                return bytes
            }
            output.write(buffer, 0, read)
            bytes += read
            if (bytes - loggedBytes >= LOG_INTERVAL_BYTES) {
                LOG.debug("Stored {} of {} bytes of {}", bytes, length, file.name)
                loggedBytes = bytes
            }
            val now = SystemClock.elapsedRealtime()
            if (now - notifiedAt >= NOTIFICATION_INTERVAL_MS) {
                notifiedAt = now
                notifyProgress(file, bytes, length)
            }
        }
    }

    /**
     * Shows the progress of the run, with [bytes] of [length] of the current [file].
     */
    private fun notifyProgress(file: MediaFile, bytes: Long, length: Long) {
        val percent = if (length > 0) (bytes * 100 / length).toInt().coerceIn(0, 100) else 0
        _state.update { if (it.current == file.id) it.copy(progress = percent) else it }
        val index: Int
        val total: Int
        val runBytes: Long
        val runLength: Long
        synchronized(lock) {
            index = downloaded + skipped + failed + 1
            total = count
            // A file with an unknown size is not in totalBytes
            runBytes = doneBytes + bytes.coerceAtMost(sizeOf(file))
            runLength = totalBytes
        }
        GalleryDownloadNotification.progress(context, address, index, total, file.name, runBytes, runLength)
    }

    /**
     * Publishes the [State]. The caller must hold [lock].
     */
    private fun publish() {
        val currentId = current?.id
        val previous = _state.value
        _state.value = State(
            queued = queue.mapTo(LinkedHashSet()) { it.id },
            current = currentId,
            progress = if (currentId == previous.current) previous.progress else 0,
            completed = completed
        )
    }

    private fun sizeOf(file: MediaFile) = file.size.coerceAtLeast(0)

    private fun sizeOf(files: Collection<MediaFile>) = files.sumOf { sizeOf(it) }

    /**
     * Shows the summary, and starts a new run for the files that were queued after the queue stopped.
     */
    private fun finish() {
        LOG.info(
            "Downloads from {} ended with {}: {} downloaded, {} skipped, {} failed",
            address,
            stopReason,
            downloaded,
            skipped,
            failed
        )
        val title = when (stopReason) {
            Stop.NONE -> R.string.ftp_file_browser_download_complete
            Stop.CANCELLED -> R.string.work_info_status_cancelled
            Stop.FAILED -> R.string.gallery_download_failed
        }
        GalleryDownloadNotification.summary(
            context,
            address,
            context.getString(title),
            downloaded,
            skipped,
            failed,
            lastUri
        )
        worker = null
        current = null
        if (queue.isNotEmpty() && !shutDown) {
            start()
        }
        publish()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(GalleryDownloadQueue::class.java)

        private const val BUFFER_SIZE = 64 * 1024
        private const val LOG_INTERVAL_BYTES = 8L * 1024 * 1024
        private const val NOTIFICATION_INTERVAL_MS = 500L
        private const val WAKE_LOCK_TAG = "GalleryDownload"
        private const val WAKE_LOCK_TIMEOUT_MS = 60 * 60_000L
    }
}
