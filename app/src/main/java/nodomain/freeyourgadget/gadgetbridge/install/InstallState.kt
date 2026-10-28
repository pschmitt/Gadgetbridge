package nodomain.freeyourgadget.gadgetbridge.install

import kotlin.math.roundToInt

sealed interface InstallState {
    data object Preparing : InstallState

    data class WaitingForDevice(val parsedFiles: ParsedFiles.Supported, val reason: String) : InstallState

    data class Blocked(val reason: String, val items: List<InstallItem>) : InstallState

    data class Ready(val parsedFiles: ParsedFiles.Supported, val warnings: List<String>) : InstallState

    data class Installing(val items: List<ItemProgress>) : InstallState

    data class Finished(val items: List<ItemProgress>) : InstallState
}

sealed interface ItemStatus {
    data object Pending : ItemStatus

    data class Transferring(val percent: Int) : ItemStatus

    data object Installed : ItemStatus

    data class Failed(val reason: String?) : ItemStatus

    data object Skipped : ItemStatus
}

val ItemStatus.isOutcome: Boolean
    get() = this is ItemStatus.Installed || this is ItemStatus.Failed || this is ItemStatus.Skipped

data class ItemProgress(val item: InstallItem, val status: ItemStatus)

/**
 * The progress of all items, from 0 to 100. Each item is weighted by its size when all sizes are
 * known.
 */
fun List<ItemProgress>.overallPercent(): Int {
    if (isEmpty()) {
        return 0
    }
    val sizes = map { it.item.sizeBytes }
    val weights = if (sizes.all { it != null && it > 0 }) sizes.map { it!!.toDouble() } else map { 1.0 }
    val done = zip(weights).sumOf { (progress, weight) ->
        val fraction = when (val status = progress.status) {
            ItemStatus.Pending -> 0.0
            is ItemStatus.Transferring -> status.percent / 100.0
            else -> 1.0
        }
        fraction * weight
    }
    return (100 * done / weights.sum()).roundToInt()
}

/**
 * The 1-based position of the first item without an outcome, or the number of items when all
 * items have an outcome.
 */
fun List<ItemProgress>.currentPosition(): Int {
    val index = indexOfFirst { !it.status.isOutcome }
    return if (index < 0) size else index + 1
}

/**
 * The state of the device, as seen by the manager.
 */
sealed interface DeviceCheck {
    data class NotReady(val reason: String) : DeviceCheck

    data class Checked(val compatibility: Compatibility) : DeviceCheck
}
