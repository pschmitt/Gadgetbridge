package nodomain.freeyourgadget.gadgetbridge.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallStateMachineTest {
    private val first = InstallItem("first.prg", 0, 100)
    private val second = InstallItem("second.prg", 0, 300)
    private val files = ParsedFiles.Supported(listOf(first, second), "info")

    private val compatible = DeviceCheck.Checked(Compatibility.Compatible())
    private val notReady = DeviceCheck.NotReady("not ready")

    private fun installing(): InstallStateMachine = InstallStateMachine().apply {
        supported(files, compatible)
        assertTrue(start())
    }

    private fun InstallStateMachine.statuses(): List<ItemStatus> = when (val state = state.value) {
        is InstallState.Installing -> state.items.map { it.status }
        is InstallState.Finished -> state.items.map { it.status }
        else -> throw AssertionError("No item statuses in $state")
    }

    @Test
    fun unsupportedIsBlocked() {
        val machine = InstallStateMachine()
        machine.unsupported("not a prg")
        assertEquals(InstallState.Blocked("not a prg", emptyList()), machine.state.value)
    }

    @Test
    fun unsupportedIgnoresDeviceChecks() {
        val machine = InstallStateMachine()
        machine.unsupported("not a prg")
        machine.deviceChecked(compatible)
        assertEquals(InstallState.Blocked("not a prg", emptyList()), machine.state.value)
    }

    @Test
    fun supportedAndCompatibleIsReady() {
        val machine = InstallStateMachine()
        machine.supported(files, DeviceCheck.Checked(Compatibility.Compatible(listOf("warning"))))
        assertEquals(InstallState.Ready(files, listOf("warning")), machine.state.value)
    }

    @Test
    fun waitsForDeviceUntilItIsReady() {
        val machine = InstallStateMachine()
        machine.supported(files, notReady)
        assertEquals(InstallState.WaitingForDevice(files, "not ready"), machine.state.value)

        machine.deviceChecked(compatible)
        assertEquals(InstallState.Ready(files, emptyList()), machine.state.value)

        machine.deviceChecked(notReady)
        assertEquals(InstallState.WaitingForDevice(files, "not ready"), machine.state.value)
    }

    @Test
    fun blockedByTheDeviceCanBecomeReady() {
        val machine = InstallStateMachine()
        machine.supported(files, DeviceCheck.Checked(Compatibility.Incompatible("not supported")))
        assertEquals(InstallState.Blocked("not supported", files.items), machine.state.value)

        machine.deviceChecked(compatible)
        assertEquals(InstallState.Ready(files, emptyList()), machine.state.value)
    }

    @Test
    fun startOnlyFromReady() {
        val machine = InstallStateMachine()
        assertFalse(machine.start())
        machine.supported(files, notReady)
        assertFalse(machine.start())
        assertEquals(InstallState.WaitingForDevice(files, "not ready"), machine.state.value)
    }

    @Test
    fun startSetsAllItemsPending() {
        val machine = installing()
        assertEquals(listOf(ItemStatus.Pending, ItemStatus.Pending), machine.statuses())
        assertFalse(machine.start())
    }

    @Test
    fun finishesWhenEachItemHasAnOutcome() {
        val machine = installing()
        machine.progress(first, 50)
        assertEquals(listOf(ItemStatus.Transferring(50), ItemStatus.Pending), machine.statuses())

        machine.outcome(first, ItemStatus.Installed)
        assertTrue(machine.state.value is InstallState.Installing)

        machine.outcome(second, ItemStatus.Failed("error"))
        assertTrue(machine.state.value is InstallState.Finished)
        assertEquals(listOf(ItemStatus.Installed, ItemStatus.Failed("error")), machine.statuses())
    }

    @Test
    fun skippedIsAnOutcome() {
        val machine = installing()
        machine.outcome(first, ItemStatus.Failed(null))
        machine.outcome(second, ItemStatus.Skipped)
        assertTrue(machine.state.value is InstallState.Finished)
    }

    @Test
    fun lateProgressIsIgnored() {
        val machine = installing()
        machine.outcome(first, ItemStatus.Installed)
        machine.progress(first, 80)
        assertEquals(listOf(ItemStatus.Installed, ItemStatus.Pending), machine.statuses())
    }

    @Test
    fun secondOutcomeIsIgnored() {
        val machine = installing()
        machine.outcome(first, ItemStatus.Installed)
        machine.outcome(first, ItemStatus.Failed("late"))
        assertEquals(listOf(ItemStatus.Installed, ItemStatus.Pending), machine.statuses())
    }

    @Test
    fun reportsAfterFinishedAreIgnored() {
        val machine = installing()
        machine.outcome(first, ItemStatus.Installed)
        machine.outcome(second, ItemStatus.Installed)
        val finished = machine.state.value

        machine.progress(second, 10)
        machine.outcome(second, ItemStatus.Failed("late"))
        assertEquals(finished, machine.state.value)
    }

    @Test
    fun reportsBeforeStartAreIgnored() {
        val machine = InstallStateMachine()
        machine.supported(files, compatible)
        machine.progress(first, 10)
        machine.outcome(first, ItemStatus.Installed)
        assertEquals(InstallState.Ready(files, emptyList()), machine.state.value)
    }

    @Test
    fun unknownItemIsIgnored() {
        val machine = installing()
        machine.outcome(InstallItem("first.prg", 0, 100), ItemStatus.Installed)
        assertEquals(listOf(ItemStatus.Pending, ItemStatus.Pending), machine.statuses())
    }

    @Test
    fun deviceLostFailsTheItemsWithoutOutcome() {
        val machine = installing()
        machine.outcome(first, ItemStatus.Installed)
        machine.progress(second, 30)
        machine.deviceLost("disconnected")

        assertTrue(machine.state.value is InstallState.Finished)
        assertEquals(listOf(ItemStatus.Installed, ItemStatus.Failed("disconnected")), machine.statuses())
    }

    @Test
    fun deviceChecksDoNotChangeInstalling() {
        val machine = installing()
        machine.deviceChecked(notReady)
        machine.deviceChecked(DeviceCheck.Checked(Compatibility.Incompatible("not supported")))
        assertTrue(machine.state.value is InstallState.Installing)
    }

    @Test
    fun restartUsesTheLastDeviceCheck() {
        val machine = installing()
        machine.deviceChecked(notReady)
        machine.outcome(first, ItemStatus.Installed)
        machine.outcome(second, ItemStatus.Installed)

        machine.restart()
        assertEquals(InstallState.WaitingForDevice(files, "not ready"), machine.state.value)

        machine.deviceChecked(compatible)
        assertEquals(InstallState.Ready(files, emptyList()), machine.state.value)
        assertTrue(machine.start())
    }

    @Test
    fun restartOnlyFromFinished() {
        val machine = installing()
        machine.restart()
        assertTrue(machine.state.value is InstallState.Installing)
    }

    @Test
    fun overallPercentIsWeightedBySize() {
        val items = listOf(
            ItemProgress(first, ItemStatus.Installed),
            ItemProgress(second, ItemStatus.Transferring(50)),
        )
        // (100 + 150) / 400
        assertEquals(63, items.overallPercent())
        assertEquals(2, items.currentPosition())
    }

    @Test
    fun overallPercentWithoutSizesWeightsItemsEqually() {
        val items = listOf(
            ItemProgress(InstallItem("a", 0, null), ItemStatus.Skipped),
            ItemProgress(InstallItem("b", 0, 300), ItemStatus.Pending),
        )
        assertEquals(50, items.overallPercent())
    }

    @Test
    fun currentPositionWhenAllItemsHaveAnOutcome() {
        val items = listOf(
            ItemProgress(first, ItemStatus.Installed),
            ItemProgress(second, ItemStatus.Failed(null)),
        )
        assertEquals(2, items.currentPosition())
        assertEquals(100, items.overallPercent())
    }
}
