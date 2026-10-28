package nodomain.freeyourgadget.gadgetbridge.activities

/**
 * Implemented by activities that host the [DashboardFragment] and need to react to its edit mode.
 */
interface DashboardHost {
    /**
     * Called when the dashboard enters or leaves edit mode.
     */
    fun onDashboardEditingChanged(editing: Boolean)
}
