package nodomain.freeyourgadget.gadgetbridge.service.devices.dji.wifi

import android.content.Context
import io.kaitai.struct.KaitaiStruct
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.kaitai.DumlWifi
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.messages.WifiCmd
import nodomain.freeyourgadget.gadgetbridge.service.wifi.WifiSession
import nodomain.freeyourgadget.gadgetbridge.util.internethelper.InternetHelperWifiConnection
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class DjiWifiSession(
    context: Context,
    device: GBDevice,
    private val bleRequest: (Int, KaitaiStruct.ReadWrite) -> Unit,
) : WifiSession<DjiControlChannel, DjiWifiSession.State>(
    context,
    device,
    State.STOPPED,
    State.CONNECTED
) {
    enum class State {
        STOPPED,
        CONNECTING_WIFI,
        OPENING_CONTROL_CHANNEL,
        CONNECTED,
    }

    @Volatile
    private var wifiConnection: InternetHelperWifiConnection? = null

    @Volatile
    private var controlChannel: DjiControlChannel? = null

    private val ssidReplies = LinkedBlockingQueue<DumlWifi.ApSsidGetResponse>()
    private val passphraseReplies = LinkedBlockingQueue<DumlWifi.ApPassphraseGetResponse>()

    /**
     * The Internet Helper network request id of the camera Wi-Fi, while the session is open.
     */
    val networkRequestId: String?
        get() = wifiConnection?.networkRequestId

    fun onApSsidGetResponse(response: DumlWifi.ApSsidGetResponse) {
        LOG.debug("Received AP SSID, status {}: {}", response.status(), response.ssid())
        ssidReplies.offer(response)
    }

    fun onApPassphraseGetResponse(response: DumlWifi.ApPassphraseGetResponse) {
        LOG.debug("Received AP passphrase, status {}, {} characters", response.status(), response.passphrase().length)
        passphraseReplies.offer(response)
    }

    override fun stateText(state: State): Int {
        return when (state) {
            State.STOPPED -> R.string.wifi_ftp_state_stopped
            State.CONNECTING_WIFI -> R.string.wifi_ftp_state_connecting_wifi
            State.OPENING_CONTROL_CHANNEL -> R.string.dji_wifi_state_opening_control_channel
            State.CONNECTED -> R.string.wifi_ftp_state_connected
        }
    }

    override fun open(): DjiControlChannel {
        if (!device.isInitialized) {
            throw IOException(context.getString(R.string.device_not_connected))
        }
        setState(State.CONNECTING_WIFI)
        val ssid = blePoll(
            WifiCmd.AP_SSID_GET,
            DumlWifi.ApSsidGetRequest().apply {
                setUnknown(AP_SSID_GET_UNKNOWN)
            },
            ssidReplies
        ) { it.status() == 0 }.ssid()
        val passphrase = blePoll(
            WifiCmd.AP_PASSPHRASE_GET,
            DumlWifi.ApPassphraseGetRequest().apply {
                setUnknown(AP_PASSPHRASE_GET_UNKNOWN)
            },
            passphraseReplies
        ) { it.status() == 0 }.passphrase()

        val wifi = InternetHelperWifiConnection.open {
            onWifiLost()
        }
        wifiConnection = wifi
        wifi.connect(ssid, passphrase, WIFI_TIMEOUT_MS)

        setState(State.OPENING_CONTROL_CHANNEL)
        val networkRequestId = wifi.networkRequestId
            ?: throw IOException(context.getString(R.string.wifi_ftp_error_closed))
        val channel = DjiControlChannel.open(CAMERA_HOST, networkRequestId) {
            onControlChannelLost()
        }
        controlChannel = channel
        if (!channel.handshake()) {
            throw IOException(context.getString(R.string.dji_wifi_error_control_channel))
        }
        return channel
    }

    override fun shutdown() {
        LOG.debug("Shutting down, disposed={}", disposed)
        controlChannel?.close()
        controlChannel = null
        wifiConnection?.disconnect()
        wifiConnection = null
    }

    /**
     * Sends a BLE request, and waits for a reply that [accept] accepts.
     * Retries up to one time if the reply does not arrive.
     */
    private fun <T> blePoll(
        cmd: Int,
        request: KaitaiStruct.ReadWrite,
        replies: LinkedBlockingQueue<T & Any>,
        accept: (T) -> Boolean,
    ): T {
        replies.clear()
        repeat(BLE_ATTEMPTS) {
            bleRequest(cmd, request)
            val reply = replies.poll(BLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            if (reply != null && accept(reply)) {
                return reply
            }
        }
        LOG.error("No valid reply to Wi-Fi command 0x{} over BLE", Integer.toHexString(cmd))
        throw IOException(context.getString(R.string.dji_wifi_error_credentials))
    }

    private fun onWifiLost() {
        LOG.warn("Camera Wi-Fi lost")
        close()
    }

    private fun onControlChannelLost() {
        val channel = controlChannel ?: return
        thread(name = "DjiControlChannelHandshake") {
            try {
                // Attempt to re-open it
                if (channel.handshake()) {
                    LOG.info("Control Channel re-opened")
                    return@thread
                }
            } catch (_: InterruptedException) {
                LOG.debug("Handshake interrupted")
            }
            if (controlChannel !== channel) {
                LOG.debug("Control Channel replaced, not closing the session")
                return@thread
            }
            LOG.warn("Control Channel not available, closing the session")
            close()
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(DjiWifiSession::class.java)

        const val CAMERA_HOST = "192.168.2.1"

        private const val AP_SSID_GET_UNKNOWN = 0x1c
        private const val AP_PASSPHRASE_GET_UNKNOWN = 0x79
        private const val BLE_ATTEMPTS = 2
        private const val BLE_TIMEOUT_MS = 5_000L
        private const val WIFI_TIMEOUT_MS = 60_000L
    }
}
