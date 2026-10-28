package nodomain.freeyourgadget.gadgetbridge.service.devices.dji.wifi

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlAck
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlAddress
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlCmdSet
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlCodec
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlPacket
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlPacketType
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.internethelper.InternetHelperUdpChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.random.Random

/**
 * The control channel to a DJI camera: DUML commands over UDP, using [InternetHelperUdpChannel].
 */
class DjiControlChannel private constructor(private val onLost: Runnable) : Closeable {
    fun interface RequestListener {
        fun onRequest(packet: DumlPacket)
    }

    private enum class State {
        IDLE,
        HANDSHAKING,
        OPEN,
        CLOSING,
        CLOSED,
    }

    private class PendingRequest(
        val packet: DumlPacket,
        val continuation: CancellableContinuation<DumlPacket>,
        val retries: Int,
        val attemptTimeoutMs: Long,
    ) {
        var attempts = 0

        @Volatile
        var retry: ScheduledFuture<*>? = null
    }

    private lateinit var udp: InternetHelperUdpChannel

    // Guards the channel state, and keeps the Command sequence numbers in send order.
    private val lock = Any()

    @Volatile
    private var state = State.IDLE

    @Volatile
    private var sessionId = 0
    private var isn = 0
    private var commandSeq = 0
    private var ackedCommandSeq = 0
    private var videoSeq = 0
    private var responseSeq = 0
    private var dumlSeq = Random.nextInt(0x10000)

    @Volatile
    private var handshakeLatch = CountDownLatch(1)

    @Volatile
    private var lastReceiveMs = 0L

    private val pending = ConcurrentHashMap<Int, PendingRequest>()
    private val requestListeners = CopyOnWriteArrayList<RequestListener>()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val receiveThread = Thread({ receiveLoop() }, "DjiControlChannelRx")

    /**
     * Sends the handshake with a new session id, until the camera accepts it. Returns false when the
     * camera does not accept it in time, or when the channel is closed.
     */
    @Throws(InterruptedException::class)
    fun handshake(): Boolean {
        val latch: CountDownLatch
        val packet: ByteArray
        synchronized(lock) {
            if (state == State.CLOSING || state == State.CLOSED) {
                return false
            }
            sessionId = Random.nextInt(0x10000)
            isn = Random.nextInt(0x10000)
            commandSeq = isn
            ackedCommandSeq = isn
            videoSeq = isn
            responseSeq = isn
            latch = CountDownLatch(1)
            handshakeLatch = latch
            state = State.HANDSHAKING
            packet = DjiUdpPacket.handshake(sessionId, isn)
        }
        LOG.info("Handshake for session 0x{}", Integer.toHexString(sessionId))

        val deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (state != State.HANDSHAKING || !transmit(packet)) {
                return false
            }
            if (latch.await(HANDSHAKE_RETRY_MS, TimeUnit.MILLISECONDS)) {
                synchronized(lock) {
                    if (state != State.HANDSHAKING) {
                        return false
                    }
                    state = State.OPEN
                    lastReceiveMs = System.currentTimeMillis()
                }
                LOG.info("Camera accepted the handshake for session 0x{}", Integer.toHexString(sessionId))
                stopLiveView()
                return true
            }
        }
        LOG.warn("Camera did not accept the handshake in {} ms", HANDSHAKE_TIMEOUT_MS)
        synchronized(lock) {
            if (state == State.HANDSHAKING) {
                state = State.IDLE
            }
        }
        return false
    }

    /**
     * Sends a DUML request and suspends until the reply arrives. Retries with the same DUML sequence
     * number up to [retries] times, when no reply arrives in [attemptTimeoutMs].
     * Cancellation stops only the wait.
     */
    @Throws(IOException::class)
    suspend fun request(
        receiver: DumlAddress,
        cmdSet: Int,
        cmd: Int,
        payload: ByteArray,
        retries: Int = RETRIES,
        attemptTimeoutMs: Long = RETRY_MS,
    ): DumlPacket =
        suspendCancellableCoroutine { continuation ->
            val request = PendingRequest(
                DumlPacket(
                    sender = DumlAddress.APP,
                    receiver = receiver,
                    seq = nextDumlSeq(),
                    ack = DumlAck.ACK_AFTER_EXEC,
                    packetType = DumlPacketType.REQUEST,
                    cmdSet = cmdSet,
                    cmd = cmd,
                    payload = payload,
                ),
                continuation,
                retries,
                attemptTimeoutMs
            )
            continuation.invokeOnCancellation { removePending(request) }
            if (state != State.OPEN) {
                continuation.resumeWithException(IOException("The control channel is not open"))
                return@suspendCancellableCoroutine
            }
            pending[request.packet.seq] = request
            attempt(request)
        }

    fun addRequestListener(listener: RequestListener) {
        requestListeners.add(listener)
    }

    fun removeRequestListener(listener: RequestListener) {
        requestListeners.remove(listener)
    }

    /**
     * Sends a Wi-Fi disconnect command to the camera, and closes the channel.
     */
    override fun close() {
        val wasOpen: Boolean
        synchronized(lock) {
            if (state == State.CLOSING || state == State.CLOSED) {
                return
            }
            wasOpen = state == State.OPEN
            state = State.CLOSING
        }
        LOG.info("Closing the control channel, open={}", wasOpen)
        failPending(IOException("The control channel is closed"))
        if (wasOpen) {
            send(DumlAddress.WIFI, DumlCmdSet.WIFI, CMD_WIFI_DISCONNECT, ByteArray(0), DumlAck.ACK_AFTER_EXEC)
            try {
                repeat(TEARDOWN_ACKS) {
                    sendAck()
                    Thread.sleep(TEARDOWN_ACK_INTERVAL_MS)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        state = State.CLOSED
        scheduler.shutdownNow()
        udp.close()
    }

    private fun start() {
        receiveThread.start()
        scheduler.scheduleWithFixedDelay({ tick() }, ACK_INTERVAL_MS, ACK_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

    private fun tick() {
        if (state != State.OPEN) {
            return
        }
        if (System.currentTimeMillis() - lastReceiveMs > LOSS_TIMEOUT_MS) {
            markLost("No packet from the camera for $LOSS_TIMEOUT_MS ms")
            return
        }
        sendAck()
    }

    private fun markLost(reason: String) {
        synchronized(lock) {
            if (state != State.OPEN) {
                return
            }
            state = State.IDLE
        }
        LOG.warn("Control channel lost: {}", reason)
        failPending(IOException(reason))
        onLost.run()
    }

    private fun attempt(request: PendingRequest) {
        val seq = request.packet.seq
        if (pending[seq] !== request) {
            return
        }
        if (request.attempts > request.retries) {
            LOG.warn("No reply after {} attempts to {}", request.attempts, request.packet)
            completeWithError(request, IOException("No reply from the camera"))
            return
        }
        request.attempts++
        LOG.debug("Sending attempt {}: {}", request.attempts, request.packet)
        if (state != State.OPEN || !sendDuml(request.packet)) {
            completeWithError(request, IOException("The control channel is not open"))
            return
        }
        try {
            request.retry = scheduler.schedule({ attempt(request) }, request.attemptTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: RejectedExecutionException) {
            completeWithError(request, IOException("The control channel is closed"))
        }
    }

    private fun removePending(request: PendingRequest): Boolean {
        if (!pending.remove(request.packet.seq, request)) {
            return false
        }
        request.retry?.cancel(false)
        return true
    }

    private fun completeWithError(request: PendingRequest, error: IOException) {
        if (removePending(request)) {
            request.continuation.resumeWithException(error)
        }
    }

    private fun failPending(error: IOException) {
        for (request in pending.values) {
            completeWithError(request, error)
        }
    }

    private fun nextDumlSeq(): Int = synchronized(lock) {
        dumlSeq = (dumlSeq + 1) and 0xffff
        dumlSeq
    }

    private fun send(receiver: DumlAddress, cmdSet: Int, cmd: Int, payload: ByteArray, ack: DumlAck): Boolean {
        return sendDuml(
            DumlPacket(
                sender = DumlAddress.APP,
                receiver = receiver,
                seq = nextDumlSeq(),
                ack = ack,
                packetType = DumlPacketType.REQUEST,
                cmdSet = cmdSet,
                cmd = cmd,
                payload = payload,
            )
        )
    }

    private fun sendDuml(packet: DumlPacket): Boolean {
        val duml = DumlCodec.encode(packet)
        synchronized(lock) {
            if (state != State.OPEN && state != State.CLOSING) {
                return false
            }
            commandSeq = (commandSeq + 8) and 0xffff
            return transmit(DjiUdpPacket.command(sessionId, isn, commandSeq, ackedCommandSeq, duml))
        }
    }

    private fun sendAck() {
        synchronized(lock) {
            transmit(DjiUdpPacket.ack(sessionId, videoSeq, responseSeq, ackedCommandSeq, commandSeq))
        }
    }

    private fun transmit(datagram: ByteArray): Boolean {
        return try {
            udp.send(datagram)
            true
        } catch (e: IOException) {
            LOG.warn("Failed to send a datagram", e)
            false
        }
    }

    /**
     * Sends the live-view stop command 6 times, then the neutral command 22 times, at 20 Hz.
     */
    private fun stopLiveView() {
        val count = LIVE_VIEW_STOP_COUNT + LIVE_VIEW_NEUTRAL_COUNT
        try {
            for (i in 0 until count) {
                val payload = if (i < LIVE_VIEW_STOP_COUNT) LIVE_VIEW_STOP else LIVE_VIEW_NEUTRAL
                scheduler.schedule(
                    { send(DumlAddress.CAMERA, DumlCmdSet.SPECIAL, CMD_SPECIAL_LIVE_VIEW, payload, DumlAck.NONE) },
                    i * LIVE_VIEW_INTERVAL_MS,
                    TimeUnit.MILLISECONDS
                )
            }
        } catch (_: RejectedExecutionException) {
            LOG.debug("Control channel closed before the live view stopped")
        }
    }

    private fun receiveLoop() {
        val buffer = ByteArray(RECEIVE_BUFFER_SIZE)
        try {
            while (true) {
                val size = udp.receive(buffer)
                if (size < 0) {
                    break
                }
                try {
                    onDatagram(buffer.copyOf(size))
                } catch (e: Exception) {
                    LOG.error("Failed to handle datagram {}", GB.hexdump(buffer, 0, size), e)
                }
            }
        } catch (e: IOException) {
            if (state != State.CLOSING && state != State.CLOSED) {
                LOG.error("Failed to receive from the control channel", e)
            }
        }
        LOG.debug("Control channel receive loop ended")
        markLost("The UDP channel is closed")
    }

    private fun onDatagram(datagram: ByteArray) {
        val packet = DjiUdpPacket.decode(datagram) ?: return
        if (packet.sessionId != sessionId) {
            return
        }
        lastReceiveMs = System.currentTimeMillis()
        when (packet.type) {
            DjiUdpPacket.TYPE_HANDSHAKE -> if (packet.handshakeAccepted) {
                handshakeLatch.countDown()
            }

            DjiUdpPacket.TYPE_TELEMETRY -> {
                packet.ackedCommandSeq?.let { seq -> synchronized(lock) { ackedCommandSeq = seq } }
                onDumlMessages(packet)
            }

            DjiUdpPacket.TYPE_VIDEO -> synchronized(lock) {
                if (isNewer(packet.seq, videoSeq)) {
                    videoSeq = packet.seq
                }
            }

            DjiUdpPacket.TYPE_RESPONSE -> {
                synchronized(lock) {
                    if (isNewer(packet.seq, responseSeq)) {
                        responseSeq = packet.seq
                    }
                }
                onDumlMessages(packet)
            }
        }
    }

    private fun onDumlMessages(packet: DjiUdpPacket) {
        for (duml in packet.dumlMessages()) {
            try {
                onDumlMessage(duml)
            } catch (e: Exception) {
                LOG.error("Failed to handle {}", duml, e)
            }
        }
    }

    private fun onDumlMessage(duml: DumlPacket) {
        if (duml.packetType == DumlPacketType.RESPONSE) {
            onResponse(duml)
            return
        }
        val answer = if (duml.cmdSet == DumlCmdSet.GENERAL) CAMERA_REQUEST_ANSWERS[duml.cmd] else null
        if (answer != null) {
            sendDuml(
                DumlPacket(
                    sender = duml.receiver,
                    receiver = duml.sender,
                    seq = duml.seq,
                    ack = answer.ack,
                    packetType = DumlPacketType.RESPONSE,
                    cmdSet = DumlCmdSet.GENERAL,
                    cmd = duml.cmd,
                    payload = answer.payload,
                )
            )
            return
        }
        for (listener in requestListeners) {
            listener.onRequest(duml)
        }
    }

    private fun onResponse(response: DumlPacket) {
        val request = pending[response.seq]
        if (request == null) {
            LOG.debug("Received a reply without a pending request {}", response)
            return
        }
        if (request.packet.cmdSet != response.cmdSet || request.packet.cmd != response.cmd) {
            return
        }
        LOG.debug("Received reply {}", response)
        if (removePending(request)) {
            request.continuation.resume(response)
        }
    }

    private class Answer(val payload: ByteArray, val ack: DumlAck)

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(DjiControlChannel::class.java)

        private const val PORT = 9004
        private const val RECEIVE_BUFFER_SIZE = 65536

        private const val HANDSHAKE_TIMEOUT_MS = 6_000L
        private const val HANDSHAKE_RETRY_MS = 500L
        private const val ACK_INTERVAL_MS = 1_000L
        private const val LOSS_TIMEOUT_MS = 4_000L
        private const val RETRY_MS = 600L
        private const val RETRIES = 3
        private const val TEARDOWN_ACKS = 5
        private const val TEARDOWN_ACK_INTERVAL_MS = 50L

        private const val CMD_SPECIAL_LIVE_VIEW = 0x01
        private const val CMD_WIFI_DISCONNECT = 0x42

        private const val LIVE_VIEW_STOP_COUNT = 6
        private const val LIVE_VIEW_NEUTRAL_COUNT = 22
        private const val LIVE_VIEW_INTERVAL_MS = 50L
        private val LIVE_VIEW_STOP = GB.hexStringToByteArray("0300000000040000000701")
        private val LIVE_VIEW_NEUTRAL = GB.hexStringToByteArray("0000000000040000000401")

        // The camera sends these General requests periodically, and the app must answer them.
        private val CAMERA_REQUEST_ANSWERS = mapOf(
            0x00 to Answer(byteArrayOf(0x00), DumlAck.ACK_AFTER_EXEC),
            0x81 to Answer(
                GB.hexStringToByteArray(
                    "0041505000000000000000000000000000000000000000000000000000000000" +
                        "0000020000000000000208000000000000000000000000000000000000000000"
                ),
                DumlAck.NONE
            ),
            0x82 to Answer(byteArrayOf(0x00), DumlAck.NONE),
            0x88 to Answer(byteArrayOf(0x1a, 0x00, 0x00, 0x00, 0x00), DumlAck.NONE),
        )

        /**
         * Opens the UDP channel to the camera on the Wi-Fi network of [networkRequestId].
         * [onLost] is executed when the camera stops answering, or when the UDP channel closes.
         */
        @Throws(IOException::class)
        fun open(host: String, networkRequestId: String, onLost: Runnable): DjiControlChannel {
            val channel = DjiControlChannel(onLost)
            channel.udp = InternetHelperUdpChannel.open(host, PORT, networkRequestId) { reason ->
                channel.markLost(reason)
            }
            channel.start()
            return channel
        }

        private fun isNewer(seq: Int, current: Int): Boolean = ((seq - current) and 0xffff) in 1 until 0x8000
    }
}
