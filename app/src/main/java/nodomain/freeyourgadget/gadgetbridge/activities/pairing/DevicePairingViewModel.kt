package nodomain.freeyourgadget.gadgetbridge.activities.pairing

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate
import nodomain.freeyourgadget.gadgetbridge.service.AbstractDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.BleNamesResolver
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils
import nodomain.freeyourgadget.gadgetbridge.util.CompanionDeviceUtil
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getDevice
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

@SuppressLint("MissingPermission")
class DevicePairingViewModel(
    application: Application,
    val candidate: GBDeviceCandidate,
) : AndroidViewModel(application) {
    val coordinator: DeviceCoordinator = DeviceHelper.getInstance().resolveCoordinator(candidate)
    private val device: GBDevice = GBApplication.app().deviceManager.getDeviceByAddress(candidate.macAddress)
        ?.takeIf { it.deviceCoordinator == coordinator }
        ?: DeviceHelper.getInstance().toSupportedDevice(candidate)

    private val _state = MutableStateFlow(PairingState())
    val state: StateFlow<PairingState> = _state.asStateFlow()

    private val associationRequests = Channel<Unit>(Channel.CONFLATED)
    val associationRequested = associationRequests.receiveAsFlow()

    private var connectionRequested = false

    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                GBDevice.ACTION_DEVICE_CHANGED -> {
                    val changedDevice = intent.getDevice() ?: return
                    if (!changedDevice.address.equals(candidate.macAddress, ignoreCase = true)) {
                        return
                    }
                    LOG.debug("Device {} changed to {}", changedDevice.address, changedDevice.state)
                    dispatch(PairingEvent.DeviceStateChanged(changedDevice.state))
                }

                AbstractDeviceSupport.ACTION_PAIRING_ERROR -> {
                    val address = intent.getStringExtra(AbstractDeviceSupport.EXTRA_PAIRING_ERROR_ADDRESS)
                    if (!candidate.macAddress.equals(address, ignoreCase = true)) {
                        return
                    }
                    val message = intent.getStringExtra(AbstractDeviceSupport.EXTRA_PAIRING_ERROR_MESSAGE).orEmpty()
                    val isAuthKeyError = intent.getBooleanExtra(
                        AbstractDeviceSupport.EXTRA_PAIRING_ERROR_AUTH_KEY,
                        false
                    )
                    LOG.debug("Pairing error for {} (auth key error: {}): {}", address, isAuthKeyError, message)
                    dispatch(PairingEvent.ErrorReported(message, isAuthKeyError))
                }
            }
        }
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val bondDevice = intent.getParcelableCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (!bondDevice.address.equals(candidate.macAddress, ignoreCase = true)) {
                return
            }
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            LOG.debug(
                "Bond state of {} changed to {}",
                bondDevice.address,
                BleNamesResolver.getBondStateString(bondState)
            )
            val bonded = when (bondState) {
                BluetoothDevice.BOND_BONDED -> true
                BluetoothDevice.BOND_BONDING -> null
                BluetoothDevice.BOND_NONE -> false
                else -> return
            }
            dispatch(PairingEvent.BondStateChanged(bonded))
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(GBDevice.ACTION_DEVICE_CHANGED)
            addAction(AbstractDeviceSupport.ACTION_PAIRING_ERROR)
        }
        LocalBroadcastManager.getInstance(application).registerReceiver(deviceReceiver, filter)
        ContextCompat.registerReceiver(
            application,
            bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        start()
    }

    /**
     * Starts the pairing from the first step. The steps that are already done are skipped.
     */
    fun start() {
        val context = getApplication<Application>()
        val address = candidate.macAddress
        val needsAssociation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            coordinator.connectionType != DeviceCoordinator.ConnectionType.USB &&
            BluetoothAdapter.checkBluetoothAddress(address) &&
            CompanionDeviceUtil.getCompanionDeviceManager(context) != null &&
            !CompanionDeviceUtil.isCompanionAssociated(context, address)

        val bondMode = if (candidate.device?.bondState == BluetoothDevice.BOND_BONDED) {
            BondMode.NONE
        } else {
            when (coordinator.bondingStyle) {
                DeviceCoordinator.BONDING_STYLE_NONE, DeviceCoordinator.BONDING_STYLE_LAZY -> BondMode.NONE
                DeviceCoordinator.BONDING_STYLE_ASK -> BondMode.ASK
                else -> BondMode.BOND
            }
        }

        LOG.info("Starting pairing of {}, needsAssociation={}, bondMode={}", address, needsAssociation, bondMode)
        dispatch(PairingEvent.Start(needsAssociation, bondMode))
    }

    fun onBondDecision(bond: Boolean) {
        dispatch(PairingEvent.BondDecision(bond))
    }

    fun onAssociationFinished() {
        val context = getApplication<Application>()
        val associated = CompanionDeviceUtil.isCompanionAssociated(context, candidate.macAddress)
        LOG.debug("Association of {} finished, associated={}", candidate.macAddress, associated)
        if (associated) {
            CompanionDeviceUtil.StartObserving(context, candidate.macAddress)
        }
        dispatch(PairingEvent.AssociationFinished(associated))
    }

    private fun dispatch(event: PairingEvent) {
        val transition = PairingStateMachine.reduce(_state.value, event)
        if (transition.state != _state.value) {
            LOG.debug("Pairing state after {}: {}", event, transition.state)
        }
        _state.value = transition.state
        transition.effects.forEach(::run)
    }

    private fun run(effect: PairingEffect) {
        when (effect) {
            PairingEffect.Associate -> associationRequests.trySend(Unit)
            PairingEffect.Bond -> bond()
            is PairingEffect.Connect -> connect(effect.afterBond)
            PairingEffect.Disconnect -> GBApplication.deviceService(device).disconnect()
        }
    }

    private fun bond() {
        val bluetoothDevice = candidate.device
        if (bluetoothDevice == null) {
            LOG.warn("No Bluetooth device for {}, skipping bond", candidate.macAddress)
            dispatch(PairingEvent.BondStateChanged(false))
            return
        }
        if (bluetoothDevice.createBond()) {
            LOG.info("Bonding with {} in progress", bluetoothDevice.address)
            return
        }
        val bondState = bluetoothDevice.bondState
        LOG.warn(
            "Bonding with {} failed immediately, bond state {}",
            bluetoothDevice.address,
            BleNamesResolver.getBondStateString(bondState)
        )
        if (bondState != BluetoothDevice.BOND_BONDING) {
            dispatch(PairingEvent.BondStateChanged(bondState == BluetoothDevice.BOND_BONDED))
        }
    }

    private fun connect(afterBond: Boolean) {
        connectionRequested = true
        viewModelScope.launch {
            if (afterBond) {
                delay(DELAY_AFTER_BOND_MS.milliseconds)
            }
            LOG.info("Connecting to {}", device.address)
            GBApplication.deviceService(device).disconnect()
            GBApplication.deviceService(device).connect(true)
        }
    }

    override fun onCleared() {
        val context = getApplication<Application>()
        AndroidUtils.safeUnregisterBroadcastReceiver(LocalBroadcastManager.getInstance(context), deviceReceiver)
        AndroidUtils.safeUnregisterBroadcastReceiver(context, bondReceiver)
        if (connectionRequested && _state.value.phase != PairingPhase.Initialized) {
            LOG.info("Pairing of {} cancelled, disconnecting", device.address)
            GBApplication.deviceService(device).disconnect()
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DevicePairingViewModel::class.java)

        private const val DELAY_AFTER_BOND_MS = 1000L
    }
}
