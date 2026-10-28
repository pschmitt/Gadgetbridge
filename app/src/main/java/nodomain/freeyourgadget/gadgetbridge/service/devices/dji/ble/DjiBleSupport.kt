package nodomain.freeyourgadget.gadgetbridge.service.devices.dji.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Bundle
import io.kaitai.struct.KaitaiStruct
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdateDeviceState
import nodomain.freeyourgadget.gadgetbridge.devices.BatteryCurrentSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.BatteryTemperatureSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.BatteryCurrentSample
import nodomain.freeyourgadget.gadgetbridge.entities.BatteryTemperatureSample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.kaitai.DumlBattery
import nodomain.freeyourgadget.gadgetbridge.kaitai.DumlWifi
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.DjiPrefs
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlAck
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlAddress
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlCmdSet
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlCodec
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlFrameReassembler
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlPacket
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlPacketType
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.messages.DumlCommand
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.messages.WifiCmd
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.messages.toPacket
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.gallery.DjiGallerySource
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.wifi.DjiWifiSession
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GallerySourceRegistry
import nodomain.freeyourgadget.gadgetbridge.service.wifi.WifiSessionRegistry
import org.slf4j.LoggerFactory
import java.util.UUID

class DjiBleSupport : AbstractBTLESingleDeviceSupport(LOG) {
    private val reassembler = DumlFrameReassembler()
    private var nextSeq: Int = 0
    private var lastBatteryLevel: Int = -1
    private var lastBatteryState: BatteryState = BatteryState.UNKNOWN
    private var lastBatteryPersistMillis: Long = 0
    private var wifiSession: DjiWifiSession? = null
    private var gallerySource: DjiGallerySource? = null

    init {
        addSupportedService(UUID_SERVICE_DJI)
    }

    override fun setContext(gbDevice: GBDevice, btAdapter: BluetoothAdapter, context: Context) {
        super.setContext(gbDevice, btAdapter, context)
        if (!coordinator.supportsGallery(gbDevice)) {
            return
        }
        val session = DjiWifiSession(context, gbDevice) { cmd, payload ->
            sendPacket(
                "wifi 0x%02x".format(cmd),
                receiver = DumlAddress.WIFI,
                cmdSet = DumlCmdSet.WIFI,
                cmd = cmd,
                payload = payload
            )
        }
        val source = DjiGallerySource(session)
        wifiSession = session
        gallerySource = source
        WifiSessionRegistry.register(gbDevice.address, session)
        GallerySourceRegistry.register(gbDevice.address, source)
    }

    override fun dispose() {
        gallerySource?.let { GallerySourceRegistry.unregister(gbDevice.address, it) }
        gallerySource = null
        wifiSession?.let {
            WifiSessionRegistry.unregister(gbDevice.address, it)
            it.dispose()
        }
        wifiSession = null
        super.dispose()
    }

    override fun useAutoConnect(): Boolean = true

    override fun getDevicePrefs(): DjiPrefs {
        return DjiPrefs(GBApplication.getDeviceSpecificSharedPrefs(gbDevice.address), gbDevice)
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        reassembler.reset()
        lastBatteryLevel = -1
        lastBatteryState = BatteryState.UNKNOWN
        lastBatteryPersistMillis = 0

        builder.setDeviceState(GBDevice.State.INITIALIZING)
        builder.requestMtu(247)
        builder.notify(UUID_CHARACTERISTIC_DJI_FFF4, true)

        sendPacket(
            builder,
            receiver = DumlAddress.WIFI,
            cmdSet = DumlCmdSet.WIFI,
            cmd = WifiCmd.SET_PAIRING_PIN,
            payload = DumlWifi.SetPairingPinRequest().apply {
                val id = devicePrefs.getOrCreatePairingId()
                val pin = devicePrefs.getOrCreatePairingPin()
                setIdLen(id.toByteArray(Charsets.UTF_8).size)
                setId(id)
                setPinLen(pin.toByteArray(Charsets.UTF_8).size)
                setPin(pin)
            }
        )

        builder.setDeviceState(GBDevice.State.AUTHENTICATING)
        return builder
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        if (UUID_CHARACTERISTIC_DJI_FFF4 == characteristic.uuid) {
            for (packet in reassembler.feed(value)) {
                try {
                    handlePacket(packet)
                } catch (e: Exception) {
                    LOG.error("Failed to handle packet {}", packet, e)
                }
            }
        }

        return super.onCharacteristicChanged(gatt, characteristic, value)
    }

    private fun handlePacket(packet: DumlPacket) {
        val cmd = DumlCommand.decode(packet)
        when (val payload = cmd.payload) {
            is DumlWifi.PairingPinApproved -> {
                if (payload.status() == 0x01) {
                    evaluateGBDeviceEvent(GBDeviceEventUpdateDeviceState(GBDevice.State.INITIALIZED))
                    sendPacket(
                        "pairing ack",
                        receiver = DumlAddress.WIFI,
                        seq = packet.seq,
                        packetType = DumlPacketType.RESPONSE,
                        cmdSet = DumlCmdSet.WIFI,
                        cmd = WifiCmd.PAIRING_PIN_APPROVED,
                        payload = DumlWifi.PairingPinApproved().apply { setStatus(0x00) },
                    )
                } else {
                    LOG.warn("Unexpected pairing pin status {}", payload.status())
                    reportPairingError("Unexpected pairing pin status: ${payload.status()}", false)
                }
            }

            is DumlWifi.SetPairingPinResponse -> {
                when (payload.pairingState()) {
                    0x01 -> evaluateGBDeviceEvent(GBDeviceEventUpdateDeviceState(GBDevice.State.INITIALIZED))
                    0x02 -> return // waiting for user to approve
                    else -> LOG.warn("Unexpected pairing state {}", payload.pairingState())
                }
            }

            is DumlWifi.ApSsidGetResponse -> wifiSession?.onApSsidGetResponse(payload)

            is DumlWifi.ApPassphraseGetResponse -> wifiSession?.onApPassphraseGetResponse(payload)

            is DumlBattery.DynamicData -> handleBatteryDynamicData(payload)

            else -> LOG.warn("Unhandled DUML command {}", cmd)
        }
    }

    private fun handleBatteryDynamicData(data: DumlBattery.DynamicData) {
        val level = data.stateOfCharge()
        LOG.debug(
            "Battery level={}% voltage={}mV current={}mA temperature={}",
            level,
            data.voltage(),
            data.current(),
            data.temperature() / 10f
        )
        val state = if (data.current() > 0) BatteryState.BATTERY_CHARGING else BatteryState.BATTERY_NORMAL
        // The device pushes these ~ once per second, so we don't store every single sample
        val now = System.currentTimeMillis()
        if (level == lastBatteryLevel && state == lastBatteryState &&
            now - lastBatteryPersistMillis < BATTERY_PERSIST_INTERVAL_MILLIS
        ) {
            return
        }
        lastBatteryLevel = level
        lastBatteryState = state
        lastBatteryPersistMillis = now
        evaluateGBDeviceEvent(GBDeviceEventBatteryInfo().apply {
            this.level = level
            this.state = state
            voltage = data.voltage() / 1000f
        })
        persistBatteryCurrentAndTemperature(now, data.current() / 1000f, data.temperature() / 10f)
    }

    private fun persistBatteryCurrentAndTemperature(timestamp: Long, current: Float, temperature: Float) {
        try {
            GBApplication.acquireDB().use { db ->
                BatteryCurrentSampleProvider(gbDevice, db.daoSession).persistSamples(
                    BatteryCurrentSample().apply {
                        this.timestamp = timestamp
                        batteryIndex = 0
                        this.current = current
                    },
                    context
                )
                BatteryTemperatureSampleProvider(gbDevice, db.daoSession).persistSamples(
                    BatteryTemperatureSample().apply {
                        this.timestamp = timestamp
                        batteryIndex = 0
                        this.temperature = temperature
                    },
                    context
                )
            }
        } catch (e: Exception) {
            LOG.error("Failed to persist battery current and temperature", e)
        }
    }

    @Synchronized
    private fun allocateSeq(): Int {
        val s = nextSeq
        nextSeq = (nextSeq + 1) and 0xFFFF
        return s
    }

    private fun sendPacket(taskName: String, packet: DumlPacket) {
        val builder = createTransactionBuilder(taskName)
        sendPacket(builder, packet)
        builder.queue()
    }

    private fun sendPacket(builder: TransactionBuilder, packet: DumlPacket) {
        builder.write(UUID_CHARACTERISTIC_DJI_FFF5, *DumlCodec.encode(packet))
    }

    private fun sendPacket(
        builder: TransactionBuilder,
        sender: DumlAddress = DumlAddress.APP,
        receiver: DumlAddress,
        seq: Int = allocateSeq(),
        ack: DumlAck = DumlAck.ACK_AFTER_EXEC,
        packetType: DumlPacketType = DumlPacketType.REQUEST,
        cmdSet: Int,
        cmd: Int,
        payload: KaitaiStruct.ReadWrite,
    ) {
        sendPacket(
            builder,
            payload.toPacket(
                cmdSet = cmdSet,
                cmd = cmd,
                sender = sender,
                receiver = receiver,
                seq = seq,
                ack = ack,
                packetType = packetType
            )
        )
    }

    private fun sendPacket(
        taskName: String,
        sender: DumlAddress = DumlAddress.APP,
        receiver: DumlAddress,
        seq: Int = allocateSeq(),
        ack: DumlAck = DumlAck.ACK_AFTER_EXEC,
        packetType: DumlPacketType = DumlPacketType.REQUEST,
        cmdSet: Int,
        cmd: Int,
        payload: KaitaiStruct.ReadWrite,
    ) {
        sendPacket(
            taskName,
            payload.toPacket(
                cmdSet = cmdSet,
                cmd = cmd,
                sender = sender,
                receiver = receiver,
                seq = seq,
                ack = ack,
                packetType = packetType
            )
        )
    }

    var testIdx = 0

    override fun onTestNewFunction(options: Bundle?) {
        when (testIdx++) {
            0 -> {
                sendPacket(
                    "test new function 0",
                    DumlPacket(
                        sender = DumlAddress.APP,
                        receiver = DumlAddress.APP,
                        seq = allocateSeq(),
                        packetType = DumlPacketType.RESPONSE,
                        cmdSet = 0,
                        cmd = 0,
                        payload = byteArrayOf(0x00),
                    )
                )
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DjiBleSupport::class.java)

        private const val BATTERY_PERSIST_INTERVAL_MILLIS = 60_000L

        private val UUID_SERVICE_DJI: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val UUID_CHARACTERISTIC_DJI_FFF4: UUID = UUID.fromString("0000fff4-0000-1000-8000-00805f9b34fb")
        private val UUID_CHARACTERISTIC_DJI_FFF5: UUID = UUID.fromString("0000fff5-0000-1000-8000-00805f9b34fb")
    }
}
