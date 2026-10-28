package nodomain.freeyourgadget.gadgetbridge.activities.dashboard.data

/**
 * The dashboard-wide preferences that affect how the widgets are rendered.
 */
data class DashboardSettings(
    val cardsEnabled: Boolean = true,
    val showAllDevices: Boolean = true,
    val deviceAddresses: Set<String> = emptySet(),
) {
    fun includesDevice(address: String): Boolean = showAllDevices || address in deviceAddresses
}
