package nodomain.freeyourgadget.gadgetbridge.service.ftp

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.wifi.WifiSession
import nodomain.freeyourgadget.gadgetbridge.util.internethelper.InternetHelperFtpClient

/**
 * A reference-counted FTP connection to a device, over a Wi-Fi network of the device.
 */
abstract class WifiFtpSession(
    context: Context,
    device: GBDevice
) : WifiSession<InternetHelperFtpClient, WifiFtpSession.State>(
    context,
    device,
    State.STOPPED,
    State.CONNECTED
) {
    enum class State {
        STOPPED,
        STARTING_HOTSPOT,
        CONNECTING_WIFI,
        STARTING_FTP_SERVER,
        CONNECTED,
    }

    /**
     * The start directory on the device that the FTP server makes available.
     */
    abstract val rootDir: String

    override fun stateText(state: State): Int {
        return when (state) {
            State.STOPPED -> R.string.wifi_ftp_state_stopped
            State.STARTING_HOTSPOT -> R.string.wifi_ftp_state_starting_hotspot
            State.CONNECTING_WIFI -> R.string.wifi_ftp_state_connecting_wifi
            State.STARTING_FTP_SERVER -> R.string.wifi_ftp_state_starting_ftp_server
            State.CONNECTED -> R.string.wifi_ftp_state_connected
        }
    }
}
