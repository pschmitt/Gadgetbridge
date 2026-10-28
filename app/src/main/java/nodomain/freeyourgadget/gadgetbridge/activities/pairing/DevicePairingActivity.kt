package nodomain.freeyourgadget.gadgetbridge.activities.pairing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.AuthKeyActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityDevicePairingBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemPairingStepBinding
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate
import nodomain.freeyourgadget.gadgetbridge.util.CompanionDeviceUtil
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import org.slf4j.LoggerFactory

@SuppressLint("MissingPermission")
class DevicePairingActivity : AbstractGBActivity() {
    private lateinit var binding: ActivityDevicePairingBinding
    private val stepViews = mutableMapOf<PairingStep, ItemPairingStepBinding>()

    private val viewModel: DevicePairingViewModel by viewModels {
        viewModelFactory {
            initializer {
                DevicePairingViewModel(
                    application,
                    intent.getParcelableCompat<GBDeviceCandidate>(DeviceCoordinator.EXTRA_DEVICE_CANDIDATE)!!
                )
            }
        }
    }

    private val authKeyLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                viewModel.start()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getParcelableCompat<GBDeviceCandidate>(DeviceCoordinator.EXTRA_DEVICE_CANDIDATE) == null) {
            LOG.error("No device candidate provided")
            finish()
            return
        }

        binding = ActivityDevicePairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val candidate = viewModel.candidate
        binding.pairingTitle.text = getString(R.string.pairing, candidate.name)
        binding.pairingAskBondTitle.text = getString(R.string.discovery_pair_title, candidate.name)

        val hint = viewModel.coordinator.getPairingHint(this, candidate)
        if (hint != null) {
            binding.pairingHint.text = hint
            binding.pairingHintCard.visibility = View.VISIBLE
        }

        binding.pairingBond.setOnClickListener {
            viewModel.onBondDecision(true)
        }
        binding.pairingDontBond.setOnClickListener {
            viewModel.onBondDecision(false)
        }
        binding.pairingRetry.setOnClickListener {
            viewModel.start()
        }
        binding.pairingChangeAuthKey.setOnClickListener {
            authKeyLauncher.launch(AuthKeyActivity.newIntent(this, candidate))
        }
        binding.pairingCancel.setOnClickListener {
            finish()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.associationRequested.collect { associate() } }
            }
        }
    }

    private fun associate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            viewModel.onAssociationFinished()
            return
        }
        val candidate = viewModel.candidate
        val bluetoothDevice = candidate.device
        // A bonded device is found in the bonded devices, a device that is not bonded only by a scan
        val useLe = bluetoothDevice?.bondState != BluetoothDevice.BOND_BONDED &&
            when (viewModel.coordinator.connectionType) {
                DeviceCoordinator.ConnectionType.BLE -> true
                DeviceCoordinator.ConnectionType.BT_CLASSIC -> false
                else -> bluetoothDevice?.type == BluetoothDevice.DEVICE_TYPE_LE ||
                    bluetoothDevice?.type == BluetoothDevice.DEVICE_TYPE_DUAL
            }
        CompanionDeviceUtil.associateCompanion(
            this,
            candidate.macAddress,
            useLe,
            REQUEST_CODE_ASSOCIATE,
            { viewModel.onAssociationFinished() },
            { viewModel.onAssociationFinished() }
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ASSOCIATE) {
            viewModel.onAssociationFinished()
        }
    }

    private fun render(state: PairingState) {
        if (stepViews.keys != state.steps.keys) {
            binding.pairingSteps.removeAllViews()
            stepViews.clear()
            for (step in state.steps.keys) {
                stepViews[step] = ItemPairingStepBinding.inflate(layoutInflater, binding.pairingSteps, true)
            }
        }
        for ((step, status) in state.steps) {
            renderStep(stepViews.getValue(step), step, status, state)
        }

        val phase = state.phase
        binding.pairingAskBondCard.visibility = if (phase == PairingPhase.AskBond) View.VISIBLE else View.GONE

        if (phase is PairingPhase.Failed) {
            binding.pairingError.text = phase.message
                ?: getString(R.string.pairing_failed_connect, viewModel.candidate.name)
            binding.pairingChangeAuthKey.visibility = if (phase.isAuthKeyError) View.VISIBLE else View.GONE
            binding.pairingErrorCard.visibility = View.VISIBLE
        } else {
            binding.pairingErrorCard.visibility = View.GONE
        }

        if (phase == PairingPhase.Initialized) {
            LOG.info("Pairing completed for {}", viewModel.candidate.macAddress)
            GB.toast(this, getString(R.string.pairing_success, viewModel.candidate.name), Toast.LENGTH_SHORT, GB.INFO)
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun renderStep(row: ItemPairingStepBinding, step: PairingStep, status: StepStatus, state: PairingState) {
        row.pairingStepTitle.setText(
            when (step) {
                PairingStep.ASSOCIATE -> R.string.companion_pairing_request_title
                PairingStep.BOND -> R.string.pairing_step_bond
                PairingStep.CONNECT -> R.string.connecting
                PairingStep.INITIALIZE -> R.string.initializing
            }
        )

        val detail = when {
            status == StepStatus.SKIPPED -> getString(R.string.install_item_skipped)
            status != StepStatus.ACTIVE -> null
            step != PairingStep.CONNECT && step != PairingStep.INITIALIZE -> null
            state.deviceBonding -> getString(
                R.string.pairing_in_progress,
                viewModel.candidate.name,
                viewModel.candidate.macAddress
            )

            else -> state.deviceState?.let { getString(it.stringId) }
        }
        row.pairingStepDetail.text = detail
        row.pairingStepDetail.visibility = if (detail != null) View.VISIBLE else View.GONE

        row.pairingStepProgress.visibility = if (status == StepStatus.ACTIVE) View.VISIBLE else View.GONE
        row.pairingStepIcon.visibility = if (status == StepStatus.ACTIVE) View.GONE else View.VISIBLE
        row.pairingStepIcon.alpha = if (status == StepStatus.PENDING) PENDING_ALPHA else 1f
        when (status) {
            StepStatus.PENDING -> row.pairingStepIcon.setImageResource(R.drawable.ic_circle)
            StepStatus.DONE -> row.pairingStepIcon.setImageResource(R.drawable.ic_done)
            StepStatus.SKIPPED -> row.pairingStepIcon.setImageResource(R.drawable.ic_remove)
            StepStatus.FAILED -> row.pairingStepIcon.setImageResource(R.drawable.ic_cancel)
            StepStatus.ACTIVE -> Unit
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DevicePairingActivity::class.java)

        private const val REQUEST_CODE_ASSOCIATE = 1426
        private const val PENDING_ALPHA = 0.38f
    }
}
