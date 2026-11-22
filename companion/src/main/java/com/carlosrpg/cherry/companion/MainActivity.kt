package com.carlosrpg.cherry.companion

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Html
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.concurrent.Executors

/**
 * The car's phone page in a WebView. Finds the car on the Wi-Fi (or through the relay when it
 * isn't there), loads the page from wherever it answered, and re-checks every 10 s and on every
 * network change. The token lives here, not in the page's storage, because the page's address
 * changes between the car's Wi-Fi and the relay; the page reads it through `window.QsApp`.
 */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private val prefs by lazy { getSharedPreferences("car", MODE_PRIVATE) }
    private val finder = CarFinder(BuildConfig.RELAY_URL)
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** The page base now loaded (LAN or relay), or null while searching. Read by the bridge thread. */
    @Volatile private var base: String? = null
    @Volatile private var resolving = false
    private var resumed = false

    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val networkChanged = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { main.post(::resolve) }
        override fun onLost(network: Network) { main.post(::resolve) }
    }

    private val tick = object : Runnable {
        override fun run() {
            check()
            main.postDelayed(this, CHECK_MS)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            setBackgroundColor(getColor(R.color.night))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Bridge(), "QsApp")
            webViewClient = object : WebViewClient() {
                // Only the car's page stays in here; any other link opens in the browser.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url.toString()
                    val current = base
                    if (current != null && url.startsWith(current)) return false
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    return true
                }
            }
        }
        setContentView(web)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, ::back)
        }
        showSearching()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        runCatching { connectivity.registerDefaultNetworkCallback(networkChanged) }
        main.post(tick)
    }

    override fun onPause() {
        super.onPause()
        resumed = false
        main.removeCallbacks(tick)
        runCatching { connectivity.unregisterNetworkCallback(networkChanged) }
    }

    /** Every [CHECK_MS]: on the relay or searching, look for the car again; on the LAN, make sure it still answers. */
    private fun check() {
        val current = base
        if (current == null || current.startsWith("https")) return resolve()
        if (resolving) return
        io.execute {
            if (!finder.stillThere(current, prefs.getString(KEY_CAR, null))) main.post(::resolve)
        }
    }

    private fun resolve() {
        if (resolving || !resumed) return
        resolving = true
        val last = prefs.getString(KEY_HOST, null)
        val car = prefs.getString(KEY_CAR, null)
        io.execute {
            val found = finder.find(last, car)
            main.post {
                resolving = false
                if (found == null) {
                    // Keep a relay page that is up: its own status bar tells the story.
                    if (base == null || base?.startsWith("http:") == true) showSearching()
                    return@post
                }
                val edit = prefs.edit().putString(KEY_CAR, found.carId)
                found.lanHost?.let { edit.putString(KEY_HOST, it) }
                edit.apply()
                if (found.url != base) {
                    base = found.url
                    web.loadUrl(found.url)
                }
            }
        }
    }

    private fun showSearching() {
        base = null
        val title = Html.escapeHtml(getString(R.string.searching_title))
        val text = Html.escapeHtml(getString(R.string.searching_text))
        web.loadDataWithBaseURL(
            null,
            """<html><head><meta name="viewport" content="width=device-width,initial-scale=1"></head>
            <body style="background:#0B1018;color:#F2F5F8;font:16px/1.4 system-ui,sans-serif;padding:32px 20px">
            <h1 style="font-size:22px;font-weight:600">$title</h1><p style="color:#C5CDD7">$text</p></body></html>""",
            "text/html", "utf-8", null,
        )
    }

    private fun back() {
        if (web.canGoBack()) web.goBack() else finish()
    }

    @SuppressLint("GestureBackNavigation") // Android 12 and older; newer ones use the callback in onCreate
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = back()

    /** What the page calls as `window.QsApp`; only the car's page is ever loaded. */
    private inner class Bridge {
        /** Offered as the phone's name when pairing. */
        @JavascriptInterface
        fun deviceName(): String = android.os.Build.MODEL.orEmpty()

        @JavascriptInterface
        fun getToken(): String = if (base != null) prefs.getString(KEY_TOKEN, "").orEmpty() else ""

        @JavascriptInterface
        fun setToken(token: String) {
            if (base == null) return
            prefs.edit().putString(KEY_TOKEN, token).apply()
        }
    }

    companion object {
        private const val CHECK_MS = 10_000L
        private const val KEY_HOST = "lan_host"
        private const val KEY_CAR = "car_id"
        private const val KEY_TOKEN = "token"
    }
}
