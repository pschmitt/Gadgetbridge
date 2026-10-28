/*  Copyright (C) 2025 Arjan Schrijver

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.appmanager

import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.graphics.Insets
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.InstallHandler
import nodomain.freeyourgadget.gadgetbridge.devices.pebble.PebbleCoordinator
import nodomain.freeyourgadget.gadgetbridge.entities.PebbleAppstoreIdEntry
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.internet.InternetRequestType
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.InternetUtils
import nodomain.freeyourgadget.gadgetbridge.util.PebbleUtils
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import nodomain.freeyourgadget.gadgetbridge.webview.GBChromeClient
import nodomain.freeyourgadget.gadgetbridge.webview.GBWebClient
import nodomain.freeyourgadget.gadgetbridge.webview.RequestInterceptorInterface
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

class PebbleAppStoreActivity : AbstractGBActivity()  {
    val LOG: Logger = LoggerFactory.getLogger(PebbleAppStoreActivity::class.java)
    private lateinit var mGBDevice: GBDevice
    private var webView: WebView? = null
    private lateinit var appStoreDomain: String
    private lateinit var url: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_banglejs_apps_management)
        applyContentInsets()

        val gbDevice: GBDevice? = intent?.extras?.getParcelableCompat(GBDevice.EXTRA_DEVICE)
        requireNotNull(gbDevice) { "Must provide a device when invoking this activity" }
        mGBDevice = gbDevice
        appStoreDomain = (mGBDevice.deviceCoordinator as PebbleCoordinator).getAppStoreDomain(mGBDevice)
        val defaultAppStoreUrl = "https://apps.$appStoreDomain/en_US/watchfaces"
        url = intent?.extras?.getString(
            DeviceService.EXTRA_URI,
            defaultAppStoreUrl
        ) ?: defaultAppStoreUrl
        initViews()

        // Let the back gesture/button walk the app loader WebView history first
        // (which closes in-app dialogs and navigates back within the SPA) and
        // only leave the activity once there is no WebView history left.
        // Without this the system back finishes the activity immediately,
        // closing the whole app instead of the WebView's current view.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView?.canGoBack() == true) {
                    webView?.goBack()
                    return
                }

                // Nothing to go back to in the WebView—use the default
                // behavior (finish the activity).
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (webView != null) return  // already set up
        val commandFilter = IntentFilter()
        commandFilter.addAction(GBDevice.ACTION_DEVICE_CHANGED)
        initViews()
    }

    override fun onDestroy() {
        webView!!.destroy()
        webView = null
        super.onDestroy()
        finish()
    }

    private fun isDownloadableWatchapp(url: String): Boolean {
        val downloadExtensions = listOf(".pbw", ".zip")
        return downloadExtensions.any { url.endsWith(it, ignoreCase = true) }
    }

    private fun downloadInstallWatchapp(url: Uri) {
        val filename = url.lastPathSegment ?: "downloaded_file.bin"
        val cacheDir = applicationContext.externalCacheDir
        val targetFile = File(cacheDir, filename)

        InternetUtils.downloadBinaryFile(url, targetFile) { file ->
            installFile(file)
        }
    }

    private fun downloadInstallWatchappById(storeId: String) {
        val appUrl = "https://appstore-api.$appStoreDomain/api/v1/apps/id/$storeId"
        val response: JSONObject? = InternetUtils.doJsonRequest(appUrl.toUri())
        if (response != null) {
            val dataArray = response.getJSONArray("data")
            val firstAppObject = dataArray.getJSONObject(0)
            val appUUID = firstAppObject.getString("uuid")
            // Cache appstore ID for app update checks
            DBHelper.store(PebbleAppstoreIdEntry(appUUID, storeId, System.currentTimeMillis(), false))
            // Find and install pbw file
            val latestRelease = firstAppObject.getJSONObject("latest_release")
            val pbwFile = latestRelease.getString("pbw_file")
            downloadInstallWatchapp(pbwFile.toUri())
            // Download and cache preview image
            val previewUrl = firstAppObject.getJSONObject("list_image").getString("144x144")
            val cacheDir: File? = PebbleUtils.getPbwCacheDir()
            val previewImgFile = File(cacheDir, appUUID + "_preview.png")
            InternetUtils.downloadBinaryFile(previewUrl.toUri(), previewImgFile) { }
        }
    }

    fun installFile(file: File) {
        @Suppress("DEPRECATION")
        val installHandler: InstallHandler? = mGBDevice.deviceCoordinator.findInstallHandler(file.toUri(), Bundle.EMPTY, applicationContext)
        if (installHandler == null) {
            GB.toast(getString(R.string.fwinstaller_file_not_compatible_to_device), Toast.LENGTH_LONG, GB.ERROR)
            LOG.error("Installable file not compatible with device")
            return
        }
        val startIntent = Intent(applicationContext, installHandler.getInstallActivity())
        startIntent.putExtra(GBDevice.EXTRA_DEVICE, mGBDevice)
        startIntent.action = Intent.ACTION_VIEW
        startIntent.setDataAndType(file.toUri(), null)
        startActivity(startIntent)
    }

    /**
     * {@link AbstractGBActivity} pads the content view by the system-bar insets, but the
     * WebView still reports them to the page, so a page that respects
     * env(safe-area-inset-*) pads a second time. Zero those two types before they reach
     * the WebView.
     *
     * <p>{@code ime()} is deliberately left alone. The WebView makes room for the
     * keyboard itself, by shrinking the page's visual viewport, and that is the only
     * place the keyboard should be accounted for.
     */
    private fun applyWebViewInsets() {
        val webView = webView ?: return
        val container = webView.parent as View

        ViewCompat.setOnApplyWindowInsetsListener(container) { _, windowInsets ->
            WindowInsetsCompat.Builder(windowInsets)
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.NONE)
                .build()
        }
    }

    /**
     * {@link AbstractGBActivity} pads the content view by the system-bar insets, but the
     * bottom edge needs one more correction. While the keyboard is up,
     * {@code navigationBars()} reports the keyboard's height (so {@code systemBars()}
     * grows from the navigation bar's height to the keyboard's), and padding by that would
     * shorten the content view by the keyboard - while the WebView already makes room for
     * it by shrinking the page's visual viewport. Pad by the part of the bottom the
     * keyboard does not cover.
     */
    private fun applyContentInsets() {
        val content = findViewById<View>(android.R.id.content)

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout()
            )
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                maxOf(0, bars.bottom - ime.bottom)
            )

            windowInsets
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initViews() {
        webView = findViewById(R.id.webview)
        applyWebViewInsets()
        val settings = webView!!.settings
        settings.javaScriptEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        val gbWebClient = object : GBWebClient(InternetRequestType.PEBBLE_APP_STORE, mGBDevice) {
            override fun shouldOverrideUrlLoading(
                wv: WebView,
                request: WebResourceRequest
            ): Boolean {
                val requestUrl = request.url
                // Handle pebble:// urls
                if (requestUrl.toString().startsWith("pebble://appstore/")) {
                    val appId = requestUrl.lastPathSegment
                    if (appId != null) {
                        downloadInstallWatchappById(appId)
                    }
                    return true
                }
                // Check if the URL is a downloadable watchface/watchapp
                if (isDownloadableWatchapp(requestUrl.toString())) {
                    downloadInstallWatchapp(requestUrl)
                    return true  // Indicate that the URL loading is handled
                }

                return super.shouldOverrideUrlLoading(wv, request)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                LOG.error("Got webview error for {}: {}", request.url, error.description)
                if (request.isForMainFrame) {
                    view.loadUrl("about:blank")
                }
            }
        }
        webView!!.webViewClient = gbWebClient

        webView!!.webChromeClient = object : GBChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.grant(request.resources)
            }
        }

        webView!!.addJavascriptInterface(RequestInterceptorInterface(gbWebClient), "GBReqInt")
        webView!!.loadUrl(url)
    }
}
