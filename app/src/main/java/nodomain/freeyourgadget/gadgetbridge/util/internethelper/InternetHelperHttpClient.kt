package nodomain.freeyourgadget.gadgetbridge.util.internethelper

import android.os.ParcelFileDescriptor
import android.os.RemoteException
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.internethelper.aidl.http.HttpRequest
import nodomain.freeyourgadget.internethelper.aidl.http.HttpResponse
import nodomain.freeyourgadget.internethelper.aidl.http.IHttpCallback
import nodomain.freeyourgadget.internethelper.aidl.http.IHttpService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * An HTTP response from [InternetHelperHttpClient]. The header names are in lower case.
 * Close it to release the body and the binding to the Internet Helper.
 */
class InternetHelperHttpResponse internal constructor(
    val status: Int,
    val headers: Map<String, String>,
    val body: InputStream,
    private val binding: InternetHelperBinding<IHttpService>,
) : Closeable {
    override fun close() {
        try {
            body.close()
        } finally {
            binding.unbind()
        }
    }
}

/**
 * A blocking HTTP client of the Internet Helper that can send requests on a given network.
 */
object InternetHelperHttpClient {
    private val LOG: Logger = LoggerFactory.getLogger(InternetHelperHttpClient::class.java)

    private const val ACTION = "nodomain.freeyourgadget.internethelper.HttpService"
    private const val MIN_VERSION_NETWORK = 2

    /**
     * Sends a GET request and blocks until the response headers arrive. The network request id
     * selects the Wi-Fi network from [InternetHelperWifiConnection]. A null network request id
     * selects the default network.
     */
    @JvmStatic
    @Throws(IOException::class, InterruptedException::class)
    fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        networkRequestId: String? = null,
        timeoutMs: Long = 30_000L,
    ): InternetHelperHttpResponse {
        val binding = InternetHelperBinding.bind(ACTION, { IHttpService.Stub.asInterface(it) })
        try {
            return send(binding, HttpRequest(url, HttpRequest.Method.GET, headers), networkRequestId, timeoutMs)
        } catch (e: InterruptedException) {
            binding.unbind()
            throw e
        } catch (e: Exception) {
            binding.unbind()
            throw e as? IOException ?: InternetHelperException(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun send(
        binding: InternetHelperBinding<IHttpService>,
        request: HttpRequest,
        networkRequestId: String?,
        timeoutMs: Long,
    ): InternetHelperHttpResponse {
        val latch = CountDownLatch(1)
        val lock = Any()
        var abandoned = false
        var result: HttpResponse? = null
        var error: String? = null
        val callback = object : IHttpCallback.Stub() {
            override fun onResponse(response: HttpResponse) {
                synchronized(lock) {
                    if (abandoned) {
                        LOG.debug("Closing the late response to GET {}", request.url)
                        response.body?.close()
                        return
                    }
                    result = response
                }
                latch.countDown()
            }

            override fun onException(message: String?) {
                error = message ?: "HTTP request failed"
                latch.countDown()
            }
        }

        try {
            if (networkRequestId == null) {
                binding.service.send(request, callback)
            } else {
                val version = binding.service.version()
                LOG.debug("Internet Helper HTTP service version {}", version)
                if (version < MIN_VERSION_NETWORK) {
                    LOG.error("Internet Helper HTTP service version {} is older than {}", version, MIN_VERSION_NETWORK)
                    throw InternetHelperException(
                        GBApplication.getContext().getString(R.string.internet_helper_error_old_version)
                    )
                }
                binding.service.sendOnNetwork(request, networkRequestId, callback)
            }
        } catch (e: RemoteException) {
            LOG.error("Failed to send GET {}", request.url, e)
            throw InternetHelperException(e.message ?: "Failed to send GET ${request.url}")
        }

        fun abandon() {
            synchronized(lock) {
                abandoned = true
                result?.body?.close()
            }
        }

        // The Internet Helper does not cancel a request - it writes the body into the pipe until the pipe is
        // closed. The body of an abandoned request must be closed, otherwise the Internet Helper will run
        // out of threads.
        val answered = try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            LOG.debug("Interrupted while waiting for GET {}", request.url)
            abandon()
            throw e
        }
        if (!answered) {
            LOG.error("Timeout after {} ms for GET {}", timeoutMs, request.url)
            abandon()
            throw InternetHelperException("Timeout for GET ${request.url}")
        }
        error?.let {
            LOG.error("GET {} failed: {}", request.url, it)
            throw InternetHelperException(it)
        }
        val r = result!!
        LOG.debug("GET {} returned {} {}", request.url, r.status, r.headers)
        val body = r.body?.let { ParcelFileDescriptor.AutoCloseInputStream(it) } ?: ByteArrayInputStream(ByteArray(0))
        return InternetHelperHttpResponse(r.status, r.headers, body, binding)
    }
}
