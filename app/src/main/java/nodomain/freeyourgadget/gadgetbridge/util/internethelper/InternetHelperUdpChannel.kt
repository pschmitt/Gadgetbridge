package nodomain.freeyourgadget.gadgetbridge.util.internethelper

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.internethelper.aidl.udp.IUdpCallback
import nodomain.freeyourgadget.internethelper.aidl.udp.IUdpService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InterruptedIOException

/**
 * A UDP socket, over the Internet Helper, connected to one remote host and port.
 * Each [send] and [receive] call is one datagram.
 */
class InternetHelperUdpChannel private constructor(
    private val binding: InternetHelperBinding<IUdpService>,
    private val closedListener: (String) -> Unit,
) {
    @Volatile
    private var socket: ParcelFileDescriptor? = null

    @Volatile
    private var closed = false

    private val callback = object : IUdpCallback.Stub() {
        override fun onClosed(reason: String?) {
            LOG.warn("UDP channel closed by the Internet Helper: {}", reason)
            closedListener(reason ?: "closed")
        }
    }

    @Throws(IOException::class)
    fun send(datagram: ByteArray) {
        val fd = socket?.fileDescriptor ?: throw IOException("UDP channel is closed")
        try {
            Os.write(fd, datagram, 0, datagram.size)
        } catch (e: ErrnoException) {
            throw IOException("Failed to send a datagram", e)
        }
    }

    /**
     * Blocks until a datagram arrives and copies it into [buffer]. Returns its size, or -1 when
     * the channel is closed. [buffer] must be larger than the largest datagram. A datagram that
     * fills [buffer] causes an [IOException], the socket drops the bytes that do not fit.
     */
    @Throws(IOException::class)
    fun receive(buffer: ByteArray): Int {
        val fd = socket?.fileDescriptor ?: return -1
        val size = try {
            Os.read(fd, buffer, 0, buffer.size)
        } catch (e: ErrnoException) {
            if (closed) {
                return -1
            }
            throw IOException("Failed to receive a datagram", e)
        } catch (_: InterruptedIOException) {
            LOG.debug("UDP channel read interrupted, closed={}", closed)
            return -1
        }
        if (size == buffer.size) {
            throw IOException("Datagram is larger than the receive buffer of ${buffer.size} bytes")
        }
        // The Internet Helper does not relay empty datagrams, so an empty record is the end of stream
        return if (size == 0) -1 else size
    }

    fun close() {
        if (closed) {
            return
        }
        closed = true
        val pfd = socket
        socket = null
        LOG.debug("Closing UDP channel")
        if (pfd != null) {
            // The shutdown releases a thread that is blocked in receive() before the fd is closed.
            try {
                Os.shutdown(pfd.fileDescriptor, OsConstants.SHUT_RDWR)
            } catch (e: ErrnoException) {
                LOG.debug("Failed to shut down the UDP channel socket", e)
            }
            try {
                pfd.close()
            } catch (e: IOException) {
                LOG.warn("Failed to close the UDP channel socket", e)
            }
        }
        binding.unbind()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(InternetHelperUdpChannel::class.java)

        private const val ACTION = "nodomain.freeyourgadget.internethelper.UdpService"
        private const val MIN_VERSION = 1

        /**
         * Opens a UDP channel to [host] and [port], on the Wi-Fi network of the network request id
         * from [InternetHelperWifiConnection].
         */
        @JvmStatic
        @Throws(InternetHelperException::class)
        fun open(
            host: String,
            port: Int,
            networkRequestId: String,
            onClosed: (String) -> Unit,
        ): InternetHelperUdpChannel {
            val binding = InternetHelperBinding.bind(ACTION, { IUdpService.Stub.asInterface(it) })
            val channel = InternetHelperUdpChannel(binding, onClosed)
            try {
                val version = binding.service.version()
                LOG.debug("Internet Helper UDP service version {}", version)
                if (version < MIN_VERSION) {
                    LOG.error("Internet Helper UDP service version {} is older than {}", version, MIN_VERSION)
                    throw InternetHelperException(
                        GBApplication.getContext().getString(R.string.internet_helper_error_old_version)
                    )
                }
                channel.socket = binding.service.open(host, port, networkRequestId, channel.callback)
                    ?: throw InternetHelperException("Failed to open a UDP channel to $host:$port")
                LOG.info("Opened UDP channel to {}:{} on network {}", host, port, networkRequestId)
            } catch (e: RemoteException) {
                LOG.error("Failed to open a UDP channel to {}:{}", host, port, e)
                channel.close()
                throw InternetHelperException(e.message ?: "Failed to open a UDP channel to $host:$port")
            } catch (e: InternetHelperException) {
                channel.close()
                throw e
            }
            return channel
        }
    }
}
