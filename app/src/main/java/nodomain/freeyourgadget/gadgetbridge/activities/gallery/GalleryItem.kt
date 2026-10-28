package nodomain.freeyourgadget.gadgetbridge.activities.gallery

import nodomain.freeyourgadget.gadgetbridge.service.gallery.MediaFile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One item of the gallery grid: a day header or a file.
 */
sealed interface GalleryItem {
    /**
     * The header of one day.
     *
     * @property ids the IDs of the loaded files for the day
     */
    data class Header(val day: LocalDate, val ids: List<String>) : GalleryItem

    data class File(val file: MediaFile) : GalleryItem

    companion object {
        /**
         * Returns the grid items of [files], in the same order: a header before each run of files of the same day.
         */
        fun of(files: List<MediaFile>): List<GalleryItem> {
            val zone = ZoneId.systemDefault()
            val items = ArrayList<GalleryItem>(files.size + files.size / 4 + 1)
            var start = 0
            while (start < files.size) {
                val day = dayOf(files[start], zone)
                var end = start + 1
                while (end < files.size && dayOf(files[end], zone) == day) {
                    end++
                }
                val run = files.subList(start, end)
                items.add(Header(day, run.map { it.id }))
                run.mapTo(items) { File(it) }
                start = end
            }
            return items
        }

        private fun dayOf(file: MediaFile, zone: ZoneId): LocalDate =
            Instant.ofEpochMilli(file.time).atZone(zone).toLocalDate()
    }
}
