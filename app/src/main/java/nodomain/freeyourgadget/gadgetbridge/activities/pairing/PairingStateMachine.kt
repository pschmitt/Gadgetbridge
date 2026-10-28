package nodomain.freeyourgadget.gadgetbridge.activities.pairing

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

enum class PairingStep {
    ASSOCIATE,
    BOND,
    CONNECT,
    INITIALIZE,
}

enum class StepStatus {
    PENDING,
    ACTIVE,
    DONE,
    SKIPPED,
    FAILED,
}

enum class BondMode {
    NONE,
    ASK,
    BOND,
}

sealed interface PairingPhase {
    data object Idle : PairingPhase
    data object Associating : PairingPhase
    data object AskBond : PairingPhase
    data object Bonding : PairingPhase
    data object Connecting : PairingPhase
    data object WaitingForDevice : PairingPhase
    data object Initializing : PairingPhase
    data object Initialized : PairingPhase
    data class Failed(val message: String?, val isAuthKeyError: Boolean) : PairingPhase

    val isActive: Boolean
        get() = this !is Idle && this !is Initialized && this !is Failed
}

data class PairingState(
    val phase: PairingPhase = PairingPhase.Idle,
    val steps: Map<PairingStep, StepStatus> = emptyMap(),
    val bondMode: BondMode = BondMode.NONE,
    /**
     * True after the device reported a connection attempt.
     */
    val connectionStarted: Boolean = false,
    /**
     * True while the device bonds during the connection.
     */
    val deviceBonding: Boolean = false,
    val deviceState: GBDevice.State? = null,
)

sealed interface PairingEvent {
    data class Start(val associate: Boolean, val bondMode: BondMode) : PairingEvent
    data class AssociationFinished(val associated: Boolean) : PairingEvent
    data class BondDecision(val bond: Boolean) : PairingEvent
    data class BondStateChanged(val bonded: Boolean?) : PairingEvent
    data class DeviceStateChanged(val state: GBDevice.State) : PairingEvent
    data class ErrorReported(val message: String, val isAuthKeyError: Boolean) : PairingEvent
}

sealed interface PairingEffect {
    data object Associate : PairingEffect
    data object Bond : PairingEffect
    data class Connect(val afterBond: Boolean) : PairingEffect
    data object Disconnect : PairingEffect
}

data class PairingTransition(val state: PairingState, val effects: List<PairingEffect> = emptyList())

/**
 * The pairing of a device candidate, as a function from a state and an event to the next state and the
 * effects to run.
 */
object PairingStateMachine {
    fun reduce(state: PairingState, event: PairingEvent): PairingTransition {
        return when (event) {
            is PairingEvent.Start -> start(event)

            is PairingEvent.AssociationFinished -> {
                if (state.phase != PairingPhase.Associating) {
                    return PairingTransition(state)
                }
                val status = if (event.associated) StepStatus.DONE else StepStatus.SKIPPED
                startBond(state.withStep(PairingStep.ASSOCIATE, status))
            }

            is PairingEvent.BondDecision -> {
                if (state.phase != PairingPhase.AskBond) {
                    return PairingTransition(state)
                }
                if (event.bond) {
                    PairingTransition(state.copy(phase = PairingPhase.Bonding), listOf(PairingEffect.Bond))
                } else {
                    startConnect(state.withStep(PairingStep.BOND, StepStatus.SKIPPED), afterBond = false)
                }
            }

            is PairingEvent.BondStateChanged -> onBondStateChanged(state, event.bonded)

            is PairingEvent.DeviceStateChanged -> onDeviceStateChanged(state, event.state)

            is PairingEvent.ErrorReported -> {
                if (!state.phase.isActive) {
                    return PairingTransition(state)
                }
                PairingTransition(
                    state.failActiveStep().copy(phase = PairingPhase.Failed(event.message, event.isAuthKeyError)),
                    listOf(PairingEffect.Disconnect)
                )
            }
        }
    }

    private fun start(event: PairingEvent.Start): PairingTransition {
        val steps = buildMap {
            if (event.associate) put(PairingStep.ASSOCIATE, StepStatus.PENDING)
            if (event.bondMode != BondMode.NONE) put(PairingStep.BOND, StepStatus.PENDING)
            put(PairingStep.CONNECT, StepStatus.PENDING)
            put(PairingStep.INITIALIZE, StepStatus.PENDING)
        }
        val state = PairingState(steps = steps, bondMode = event.bondMode)
        if (event.associate) {
            return PairingTransition(
                state.withStep(PairingStep.ASSOCIATE, StepStatus.ACTIVE).copy(phase = PairingPhase.Associating),
                listOf(PairingEffect.Associate)
            )
        }
        return startBond(state)
    }

    private fun startBond(state: PairingState): PairingTransition {
        return when (state.bondMode) {
            BondMode.NONE -> startConnect(state, afterBond = false)
            BondMode.ASK -> PairingTransition(
                state.withStep(PairingStep.BOND, StepStatus.ACTIVE).copy(phase = PairingPhase.AskBond)
            )

            BondMode.BOND -> PairingTransition(
                state.withStep(PairingStep.BOND, StepStatus.ACTIVE).copy(phase = PairingPhase.Bonding),
                listOf(PairingEffect.Bond)
            )
        }
    }

    private fun startConnect(state: PairingState, afterBond: Boolean): PairingTransition {
        return PairingTransition(
            state.withStep(PairingStep.CONNECT, StepStatus.ACTIVE).copy(
                phase = PairingPhase.Connecting,
                connectionStarted = false,
                deviceBonding = false
            ),
            listOf(PairingEffect.Connect(afterBond))
        )
    }

    /**
     * @param bonded true when bonded, false when the bond failed, null while the bond is in progress
     */
    private fun onBondStateChanged(state: PairingState, bonded: Boolean?): PairingTransition {
        return when (state.phase) {
            PairingPhase.Bonding -> when (bonded) {
                null -> PairingTransition(state)
                // Do not abort pairing when bond fails - some devices also work without a bond
                else -> startConnect(
                    state.withStep(PairingStep.BOND, if (bonded) StepStatus.DONE else StepStatus.FAILED),
                    afterBond = bonded
                )
            }

            PairingPhase.Connecting,
            PairingPhase.WaitingForDevice,
            PairingPhase.Initializing -> PairingTransition(state.copy(deviceBonding = bonded == null))

            else -> PairingTransition(state)
        }
    }

    private fun onDeviceStateChanged(state: PairingState, deviceState: GBDevice.State): PairingTransition {
        if (state.phase != PairingPhase.Connecting &&
            state.phase != PairingPhase.WaitingForDevice &&
            state.phase != PairingPhase.Initializing
        ) {
            return PairingTransition(state)
        }

        return reduceDeviceState(state.copy(deviceState = deviceState), deviceState)
    }

    private fun reduceDeviceState(state: PairingState, deviceState: GBDevice.State): PairingTransition {
        return when (deviceState) {
            GBDevice.State.INITIALIZED -> PairingTransition(
                state.withStep(PairingStep.CONNECT, StepStatus.DONE)
                    .withStep(PairingStep.INITIALIZE, StepStatus.DONE)
                    .copy(phase = PairingPhase.Initialized, deviceBonding = false)
            )

            // The disconnect before the connection also sends NOT_CONNECTED
            GBDevice.State.NOT_CONNECTED -> if (state.connectionStarted) {
                PairingTransition(state.failActiveStep().copy(phase = PairingPhase.Failed(null, false)))
            } else {
                PairingTransition(state.copy(deviceState = null))
            }

            GBDevice.State.WAITING_FOR_RECONNECT,
            GBDevice.State.WAITING_FOR_SCAN,
            GBDevice.State.SCANNED -> PairingTransition(
                state.withStep(PairingStep.CONNECT, StepStatus.ACTIVE)
                    .withStep(PairingStep.INITIALIZE, StepStatus.PENDING)
                    .copy(phase = PairingPhase.WaitingForDevice)
            )

            GBDevice.State.CONNECTING -> PairingTransition(
                state.withStep(PairingStep.CONNECT, StepStatus.ACTIVE)
                    .withStep(PairingStep.INITIALIZE, StepStatus.PENDING)
                    .copy(phase = PairingPhase.Connecting, connectionStarted = true)
            )

            GBDevice.State.CONNECTED,
            GBDevice.State.INITIALIZING,
            GBDevice.State.AUTHENTICATION_REQUIRED,
            GBDevice.State.AUTHENTICATING -> PairingTransition(
                state.withStep(PairingStep.CONNECT, StepStatus.DONE)
                    .withStep(PairingStep.INITIALIZE, StepStatus.ACTIVE)
                    .copy(phase = PairingPhase.Initializing, connectionStarted = true)
            )
        }
    }

    private fun PairingState.withStep(step: PairingStep, status: StepStatus): PairingState {
        if (step !in steps) {
            return this
        }
        return copy(steps = steps + (step to status))
    }

    private fun PairingState.failActiveStep(): PairingState {
        return copy(
            steps = steps.mapValues { (_, status) -> if (status == StepStatus.ACTIVE) StepStatus.FAILED else status },
            deviceBonding = false
        )
    }
}
