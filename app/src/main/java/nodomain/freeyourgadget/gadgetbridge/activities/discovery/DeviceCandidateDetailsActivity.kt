package nodomain.freeyourgadget.gadgetbridge.activities.discovery

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.core.view.MenuProvider
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityDeviceCandidateDetailsBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemDeviceCandidateDetailsRowBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemDeviceCandidateDetailsSectionBinding
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate
import nodomain.freeyourgadget.gadgetbridge.service.btle.BleNamesResolver
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import org.slf4j.LoggerFactory
import java.util.Locale
import androidx.core.util.isNotEmpty
import androidx.core.util.size

@SuppressLint("MissingPermission")
class DeviceCandidateDetailsActivity : AbstractGBActivity(), MenuProvider {
    private lateinit var binding: ActivityDeviceCandidateDetailsBinding
    private lateinit var candidate: GBDeviceCandidate
    private lateinit var scanSections: List<Section>

    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var gattStatus: String? = null
    private var gattServices: List<BluetoothGattService> = emptyList()

    private val gattTimeout = Runnable {
        LOG.warn("GATT service discovery timed out for {}", candidate.macAddress)
        finishGatt("Timeout")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceCandidateDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        addMenuProvider(this)

        val extraCandidate = intent.getParcelableCompat<GBDeviceCandidate>(DeviceCoordinator.EXTRA_DEVICE_CANDIDATE)
        if (extraCandidate == null) {
            GB.toast("Missing device candidate", Toast.LENGTH_LONG, GB.ERROR)
            finish()
            return
        }
        candidate = extraCandidate

        binding.deviceCandidateDetailsIcon.setImageResource(
            DeviceHelper.getInstance().resolveDeviceType(candidate).deviceCoordinator.defaultIconResource
        )
        binding.deviceCandidateDetailsName.text = candidate.name
        binding.deviceCandidateDetailsAddress.text = candidate.macAddress
        binding.deviceCandidateDetailsReadGatt.visibility = if (canConnectGatt()) View.VISIBLE else View.GONE
        binding.deviceCandidateDetailsReadGatt.setOnClickListener { connectGatt() }
        scanSections = buildScanSections()
        render()
    }

    override fun onDestroy() {
        handler.removeCallbacks(gattTimeout)
        closeGatt()
        super.onDestroy()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_device_candidate_details, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.device_candidate_details_copy -> {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(candidate.name, buildReport()))
                GB.toast(getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT, GB.INFO)
                return true
            }

            R.id.device_candidate_details_share -> {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, candidate.name)
                shareIntent.putExtra(Intent.EXTRA_TEXT, buildReport())
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
                return true
            }
        }

        return false
    }

    private fun render() {
        binding.deviceCandidateDetailsProgress.visibility = if (gatt != null) View.VISIBLE else View.GONE
        binding.deviceCandidateDetailsReadGatt.isEnabled = gatt == null
        val container = binding.deviceCandidateDetailsSections
        container.removeAllViews()
        for (section in scanSections + gattSections()) {
            val sectionBinding = ItemDeviceCandidateDetailsSectionBinding.inflate(layoutInflater, container, false)
            sectionBinding.deviceCandidateDetailsSectionTitle.text = section.title
            for (row in section.rows) {
                val rowBinding = ItemDeviceCandidateDetailsRowBinding.inflate(
                    layoutInflater,
                    sectionBinding.deviceCandidateDetailsSectionRows,
                    true
                )
                rowBinding.deviceCandidateDetailsRowLabel.text = row.label
                rowBinding.deviceCandidateDetailsRowValue.text = row.value
            }
            container.addView(sectionBinding.root)
        }
    }

    private fun buildReport(): String {
        val sb = StringBuilder()
        sb.append("Name: ").append(candidate.name).append('\n')
        for (section in scanSections + gattSections()) {
            sb.append('\n').append(section.title).append('\n')
            for (row in section.rows) {
                val line = row.reportLine ?: (row.label + ": " + row.value.replace("\n", "\n    "))
                sb.append("  ").append(line).append('\n')
            }
        }
        return sanitize(sb.toString())
    }

    /**
     * Attempts to sanitize the MAC address of the device from the provided text.
     * Matches both byte orders, and with/without separators.
     */
    private fun sanitize(text: String): String {
        val macBytes = candidate.device?.address?.split(':') ?: return text
        val separator = "[:-]?"
        val pattern = Regex(
            macBytes.joinToString(separator) + "|" + macBytes.reversed().joinToString(separator),
            RegexOption.IGNORE_CASE
        )
        return pattern.replace(text) { match -> match.value.replace(Regex("[0-9A-Fa-f]"), "X") }
    }

    private fun buildScanSections(): List<Section> {
        val sections = mutableListOf<Section>()

        val deviceRows = mutableListOf(
            Row("Gadgetbridge device type", DeviceHelper.getInstance().resolveDeviceType(candidate).name)
        )
        val accessory = candidate.accessory
        if (accessory != null) {
            deviceRows.add(Row("USB manufacturer", accessory.manufacturer))
            deviceRows.add(Row("USB model", accessory.model))
            deviceRows.add(Row("USB description", accessory.description.toString()))
            deviceRows.add(Row("USB version", accessory.version.toString()))
            deviceRows.add(Row("USB URI", accessory.uri.toString()))
        }
        val device = candidate.device
        if (device != null) {
            val type = when (device.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                BluetoothDevice.DEVICE_TYPE_LE -> "LE"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                else -> "Unknown (${device.type})"
            }
            deviceRows.add(Row("Bluetooth type", type))
            deviceRows.add(Row("Bond state", BleNamesResolver.getBondStateString(device.bondState)))
            device.bluetoothClass?.let { bluetoothClass ->
                deviceRows.add(Row("Bluetooth class", "0x$bluetoothClass"))
                deviceRows.add(
                    Row(
                        "Major device class", MAJOR_DEVICE_CLASSES[bluetoothClass.majorDeviceClass]
                            ?: String.format(Locale.ROOT, "Unknown (0x%04X)", bluetoothClass.majorDeviceClass)
                    )
                )
                deviceRows.add(
                    Row(
                        "Device class", DEVICE_CLASSES[bluetoothClass.deviceClass]
                            ?: String.format(Locale.ROOT, "Unknown (0x%04X)", bluetoothClass.deviceClass)
                    )
                )
                val services = SERVICE_CLASSES.filterKeys { bluetoothClass.hasService(it) }.values
                deviceRows.add(Row("Service classes", if (services.isEmpty()) "None" else services.joinToString(", ")))
            }
            deviceRows.add(Row("RSSI", candidate.rssi.toString()))
        }
        sections.add(Section("Device", deviceRows))

        if (candidate.serviceUuids.isNotEmpty()) {
            sections.add(Section("Service UUIDs", candidate.serviceUuids.map {
                Row(BleNamesResolver.resolveServiceName(it.uuid), it.uuid.toString())
            }))
        }

        val manufacturerData = candidate.manufacturerSpecificData
        if (manufacturerData.isNotEmpty()) {
            sections.add(Section("Manufacturer data", (0 until manufacturerData.size).map {
                Row(
                    String.format(Locale.ROOT, "0x%04X", manufacturerData.keyAt(it)),
                    formatValue(manufacturerData.valueAt(it))
                )
            }))
        }

        val scanRecord = candidate.scanRecord
        if (scanRecord != null) {
            sections.add(
                Section(
                    "Advertisement",
                    listOf(Row("Raw", GB.hexdump(scanRecord))) + advertisingStructures(scanRecord)
                )
            )
        }

        return sections
    }

    private fun advertisingStructures(scanRecord: ByteArray): List<Row> {
        val rows = mutableListOf<Row>()
        var i = 0
        while (i < scanRecord.size) {
            val length = scanRecord[i].toInt() and 0xff
            // A zero length marks the start of the padding at the end of the record
            if (length == 0 || i + length >= scanRecord.size) {
                break
            }
            val type = scanRecord[i + 1].toInt() and 0xff
            val data = scanRecord.copyOfRange(i + 2, i + 1 + length)
            val typeName = AD_TYPES[type]?.let { " ($it)" } ?: ""
            rows.add(Row(String.format(Locale.ROOT, "0x%02X", type) + typeName, formatValue(data)))
            i += length + 1
        }
        return rows
    }

    private fun gattSections(): List<Section> {
        val status = gattStatus ?: return emptyList()
        val sections = mutableListOf(Section("GATT", listOf(Row("Status", status))))
        for (service in gattServices) {
            val rows = mutableListOf(Row("UUID", service.uuid.toString()))
            for (characteristic in service.characteristics) {
                val name = BleNamesResolver.resolveCharacteristicName(characteristic)
                val properties = BleNamesResolver.getCharacteristicPropertyString(characteristic.properties)
                val reportValue = StringBuilder(characteristic.uuid.toString())
                if (BleNamesResolver.isCharacteristic(characteristic.uuid.toString())) {
                    reportValue.append(" - ").append(name)
                }
                reportValue.append(" (").append(properties).append(')')
                rows.add(
                    Row(
                        "$name · $properties",
                        characteristic.uuid.toString(),
                        "Characteristic: $reportValue"
                    )
                )
            }
            sections.add(Section(BleNamesResolver.resolveServiceName(service), rows))
        }
        return sections
    }

    private fun canConnectGatt(): Boolean {
        val device = candidate.device
        return device != null && device.type != BluetoothDevice.DEVICE_TYPE_CLASSIC
    }

    private fun connectGatt() {
        val device = candidate.device ?: return
        LOG.debug("Connecting GATT to {}", device.address)
        gattStatus = getString(R.string.controlcenter_snackbar_connecting)
        gattServices = emptyList()
        gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        handler.postDelayed(gattTimeout, GATT_TIMEOUT_MS)
        render()
    }

    private fun finishGatt(status: String) {
        handler.removeCallbacks(gattTimeout)
        gattStatus = status
        closeGatt()
        render()
    }

    private fun closeGatt() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            LOG.debug(
                "GATT onConnectionStateChange: status={}, newState={}",
                BleNamesResolver.getStatusString(status),
                BleNamesResolver.getStateString(newState)
            )
            runOnUiThread {
                if (gatt != this@DeviceCandidateDetailsActivity.gatt) {
                    return@runOnUiThread
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gattStatus = getString(R.string.controlcenter_snackbar_discovering_services)
                    render()
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    finishGatt("Disconnected (" + BleNamesResolver.getStatusString(status) + ")")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            LOG.debug("GATT onServicesDiscovered: status={}", BleNamesResolver.getStatusString(status))
            runOnUiThread {
                if (gatt != this@DeviceCandidateDetailsActivity.gatt) {
                    return@runOnUiThread
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    finishGatt("Service discovery failed (" + BleNamesResolver.getStatusString(status) + ")")
                    return@runOnUiThread
                }
                gattServices = gatt.services
                binding.deviceCandidateDetailsReadGatt.visibility = View.GONE
                finishGatt("Done")
            }
        }
    }

    /**
     * A label and a value. The report uses [reportLine] in place of the label and the value, if set.
     */
    private data class Row(val label: String, val value: String, val reportLine: String? = null)

    private data class Section(val title: String, val rows: List<Row>)

    companion object {
        private val LOG = LoggerFactory.getLogger(DeviceCandidateDetailsActivity::class.java)

        private const val GATT_TIMEOUT_MS = 30_000L

        // https://bitbucket.org/bluetooth-SIG/public/src/main/assigned_numbers/core/ad_types.yaml
        private val AD_TYPES = mapOf(
            0x01 to "Flags",
            0x02 to "Incomplete List of 16-bit Service or Service Class UUIDs",
            0x03 to "Complete List of 16-bit Service or Service Class UUIDs",
            0x04 to "Incomplete List of 32-bit Service or Service Class UUIDs",
            0x05 to "Complete List of 32-bit Service or Service Class UUIDs",
            0x06 to "Incomplete List of 128-bit Service or Service Class UUIDs",
            0x07 to "Complete List of 128-bit Service or Service Class UUIDs",
            0x08 to "Shortened Local Name",
            0x09 to "Complete Local Name",
            0x0A to "Tx Power Level",
            0x0D to "Class of Device",
            0x0E to "Simple Pairing Hash C-192",
            0x0F to "Simple Pairing Randomizer R-192",
            0x10 to "Device ID / Security Manager TK Value",
            0x11 to "Security Manager Out of Band Flags",
            0x12 to "Peripheral Connection Interval Range",
            0x14 to "List of 16-bit Service Solicitation UUIDs",
            0x15 to "List of 128-bit Service Solicitation UUIDs",
            0x16 to "Service Data - 16-bit UUID",
            0x17 to "Public Target Address",
            0x18 to "Random Target Address",
            0x19 to "Appearance",
            0x1A to "Advertising Interval",
            0x1B to "LE Bluetooth Device Address",
            0x1C to "LE Role",
            0x1D to "Simple Pairing Hash C-256",
            0x1E to "Simple Pairing Randomizer R-256",
            0x1F to "List of 32-bit Service Solicitation UUIDs",
            0x20 to "Service Data - 32-bit UUID",
            0x21 to "Service Data - 128-bit UUID",
            0x22 to "LE Secure Connections Confirmation Value",
            0x23 to "LE Secure Connections Random Value",
            0x24 to "URI",
            0x25 to "Indoor Positioning",
            0x26 to "Transport Discovery Data",
            0x27 to "LE Supported Features",
            0x28 to "Channel Map Update Indication",
            0x29 to "PB-ADV",
            0x2A to "Mesh Message",
            0x2B to "Mesh Beacon",
            0x2C to "BIGInfo",
            0x2D to "Broadcast_Code",
            0x2E to "Resolvable Set Identifier",
            0x2F to "Advertising Interval - long",
            0x30 to "Broadcast_Name",
            0x31 to "Encrypted Advertising Data",
            0x32 to "Periodic Advertising Response Timing Information",
            0x34 to "Electronic Shelf Label",
            0x3D to "3D Information Data",
            0xFF to "Manufacturer Specific Data",
        )

        private val SERVICE_CLASSES = mapOf(
            BluetoothClass.Service.LIMITED_DISCOVERABILITY to "LIMITED_DISCOVERABILITY",
            0x004000 to "LE_AUDIO", // sdk33
            BluetoothClass.Service.POSITIONING to "POSITIONING",
            BluetoothClass.Service.NETWORKING to "NETWORKING",
            BluetoothClass.Service.RENDER to "RENDER",
            BluetoothClass.Service.CAPTURE to "CAPTURE",
            BluetoothClass.Service.OBJECT_TRANSFER to "OBJECT_TRANSFER",
            BluetoothClass.Service.AUDIO to "AUDIO",
            BluetoothClass.Service.TELEPHONY to "TELEPHONY",
            BluetoothClass.Service.INFORMATION to "INFORMATION",
        )

        private val MAJOR_DEVICE_CLASSES = mapOf(
            BluetoothClass.Device.Major.MISC to "MISC",
            BluetoothClass.Device.Major.COMPUTER to "COMPUTER",
            BluetoothClass.Device.Major.PHONE to "PHONE",
            BluetoothClass.Device.Major.NETWORKING to "NETWORKING",
            BluetoothClass.Device.Major.AUDIO_VIDEO to "AUDIO_VIDEO",
            BluetoothClass.Device.Major.PERIPHERAL to "PERIPHERAL",
            BluetoothClass.Device.Major.IMAGING to "IMAGING",
            BluetoothClass.Device.Major.WEARABLE to "WEARABLE",
            BluetoothClass.Device.Major.TOY to "TOY",
            BluetoothClass.Device.Major.HEALTH to "HEALTH",
            BluetoothClass.Device.Major.UNCATEGORIZED to "UNCATEGORIZED",
        )

        private val DEVICE_CLASSES = mapOf(
            BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED to "AUDIO_VIDEO_UNCATEGORIZED",
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET to "AUDIO_VIDEO_WEARABLE_HEADSET",
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE to "AUDIO_VIDEO_HANDSFREE",
            BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE to "AUDIO_VIDEO_MICROPHONE",
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER to "AUDIO_VIDEO_LOUDSPEAKER",
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES to "AUDIO_VIDEO_HEADPHONES",
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO to "AUDIO_VIDEO_PORTABLE_AUDIO",
            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO to "AUDIO_VIDEO_CAR_AUDIO",
            BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX to "AUDIO_VIDEO_SET_TOP_BOX",
            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO to "AUDIO_VIDEO_HIFI_AUDIO",
            BluetoothClass.Device.AUDIO_VIDEO_VCR to "AUDIO_VIDEO_VCR",
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CAMERA to "AUDIO_VIDEO_VIDEO_CAMERA",
            BluetoothClass.Device.AUDIO_VIDEO_CAMCORDER to "AUDIO_VIDEO_CAMCORDER",
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR to "AUDIO_VIDEO_VIDEO_MONITOR",
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER to "AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER",
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CONFERENCING to "AUDIO_VIDEO_VIDEO_CONFERENCING",
            BluetoothClass.Device.AUDIO_VIDEO_VIDEO_GAMING_TOY to "AUDIO_VIDEO_VIDEO_GAMING_TOY",
            BluetoothClass.Device.COMPUTER_UNCATEGORIZED to "COMPUTER_UNCATEGORIZED",
            BluetoothClass.Device.COMPUTER_DESKTOP to "COMPUTER_DESKTOP",
            BluetoothClass.Device.COMPUTER_SERVER to "COMPUTER_SERVER",
            BluetoothClass.Device.COMPUTER_LAPTOP to "COMPUTER_LAPTOP",
            BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA to "COMPUTER_HANDHELD_PC_PDA",
            BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA to "COMPUTER_PALM_SIZE_PC_PDA",
            BluetoothClass.Device.COMPUTER_WEARABLE to "COMPUTER_WEARABLE",
            BluetoothClass.Device.HEALTH_UNCATEGORIZED to "HEALTH_UNCATEGORIZED",
            BluetoothClass.Device.HEALTH_BLOOD_PRESSURE to "HEALTH_BLOOD_PRESSURE",
            BluetoothClass.Device.HEALTH_THERMOMETER to "HEALTH_THERMOMETER",
            BluetoothClass.Device.HEALTH_WEIGHING to "HEALTH_WEIGHING",
            BluetoothClass.Device.HEALTH_GLUCOSE to "HEALTH_GLUCOSE",
            BluetoothClass.Device.HEALTH_PULSE_OXIMETER to "HEALTH_PULSE_OXIMETER",
            BluetoothClass.Device.HEALTH_PULSE_RATE to "HEALTH_PULSE_RATE",
            BluetoothClass.Device.HEALTH_DATA_DISPLAY to "HEALTH_DATA_DISPLAY",
            0x0500 to "PERIPHERAL_NON_KEYBOARD_NON_POINTING", // sdk33
            0x0540 to "PERIPHERAL_KEYBOARD", // sdk33
            0x0580 to "PERIPHERAL_POINTING", // sdk33
            0x05C0 to "PERIPHERAL_KEYBOARD_POINTING", // sdk33
            BluetoothClass.Device.PHONE_UNCATEGORIZED to "PHONE_UNCATEGORIZED",
            BluetoothClass.Device.PHONE_CELLULAR to "PHONE_CELLULAR",
            BluetoothClass.Device.PHONE_CORDLESS to "PHONE_CORDLESS",
            BluetoothClass.Device.PHONE_SMART to "PHONE_SMART",
            BluetoothClass.Device.PHONE_MODEM_OR_GATEWAY to "PHONE_MODEM_OR_GATEWAY",
            BluetoothClass.Device.PHONE_ISDN to "PHONE_ISDN",
            BluetoothClass.Device.TOY_UNCATEGORIZED to "TOY_UNCATEGORIZED",
            BluetoothClass.Device.TOY_ROBOT to "TOY_ROBOT",
            BluetoothClass.Device.TOY_VEHICLE to "TOY_VEHICLE",
            BluetoothClass.Device.TOY_DOLL_ACTION_FIGURE to "TOY_DOLL_ACTION_FIGURE",
            BluetoothClass.Device.TOY_CONTROLLER to "TOY_CONTROLLER",
            BluetoothClass.Device.TOY_GAME to "TOY_GAME",
            BluetoothClass.Device.WEARABLE_UNCATEGORIZED to "WEARABLE_UNCATEGORIZED",
            BluetoothClass.Device.WEARABLE_WRIST_WATCH to "WEARABLE_WRIST_WATCH",
            BluetoothClass.Device.WEARABLE_PAGER to "WEARABLE_PAGER",
            BluetoothClass.Device.WEARABLE_JACKET to "WEARABLE_JACKET",
            BluetoothClass.Device.WEARABLE_HELMET to "WEARABLE_HELMET",
            BluetoothClass.Device.WEARABLE_GLASSES to "WEARABLE_GLASSES",
        )

        @JvmStatic
        fun newIntent(context: Context, candidate: GBDeviceCandidate): Intent {
            return Intent(context, DeviceCandidateDetailsActivity::class.java)
                .putExtra(DeviceCoordinator.EXTRA_DEVICE_CANDIDATE, candidate)
        }

        /**
         * Formats a value as hex, followed by its value as text when all bytes are printable ASCII.
         */
        private fun formatValue(value: ByteArray): String {
            val text = value.dropLastWhile { it.toInt() == 0 }
            val printable = text.isNotEmpty() && text.all { it in 0x20..0x7e }
            return if (printable) {
                GB.hexdump(value) + " \"" + String(text.toByteArray(), Charsets.US_ASCII) + "\""
            } else {
                GB.hexdump(value)
            }
        }
    }
}
