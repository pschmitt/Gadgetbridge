package nodomain.freeyourgadget.gadgetbridge.service.wifi

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * The [WifiSession] of each connected device, by device address.
 */
object WifiSessionRegistry {
    private val LOG: Logger = LoggerFactory.getLogger(WifiSessionRegistry::class.java)

    private val sessions: MutableMap<String, WifiSession<*, *>> = ConcurrentHashMap()

    @JvmStatic
    fun register(address: String, session: WifiSession<*, *>) {
        LOG.debug("Registering session for {}", address)
        sessions[address] = session
    }

    @JvmStatic
    fun unregister(address: String, session: WifiSession<*, *>) {
        LOG.debug("Unregistering session for {}", address)
        sessions.remove(address, session)
    }

    @JvmStatic
    fun get(address: String): WifiSession<*, *>? = sessions[address]
}
