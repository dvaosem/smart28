package com.dvaosem.smart28.ui

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.dvaosem.smart28.R
import com.dvaosem.smart28.widget.S28Prefs
import com.dvaosem.smart28.widget.SmartWidget
import org.json.JSONObject

/**
 * Thin WebView shell around https://dvaosem.com/smart28/smart28.html.
 * The web app is updated through the hosting; the APK changes only when this shell or the widget changes.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val START_URL = "https://dvaosem.com/smart28/smart28.html"
        private val OWN_HOSTS = setOf("dvaosem.com", "www.dvaosem.com")
        private const val OFFLINE_HTML = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
            "<body style='background:#22201d;color:#ece6dd;font-family:sans-serif;text-align:center;padding-top:30vh'>" +
            "<h2>Bez pripojenia</h2><p><a style='color:#b8a994;font-size:1.1em' href='$START_URL'>Skúsiť znova</a></p></body></html>"
    }

    private lateinit var webView: WebView
    @Volatile private var pendingUpdateUrl: String? = null

    private fun js(code: String) {
        runOnUiThread { if (::webView.isInitialized) webView.evaluateJavascript(code, null) }
    }

    private fun call(fn: String, argJson: String) = js("window.$fn&&window.$fn($argJson)")

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webview)
        val bg = Color.parseColor("#22201d")
        window.setBackgroundDrawable(ColorDrawable(bg))
        webView.setBackgroundColor(bg)
        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "https" && OWN_HOSTS.contains(u.host ?: "")) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: Exception) { }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) view.loadDataWithBaseURL(START_URL, OFFLINE_HTML, "text/html", "utf-8", null)
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) webView.loadUrl(START_URL)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::webView.isInitialized) webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    inner class AndroidBridge {

        /** The web app hands over what the home-screen widget needs to talk to the API on its own. */
        @JavascriptInterface
        fun saveSession(apiKey: String, refreshToken: String, apiUrl: String) {
            if (apiKey.length > 200 || refreshToken.length > 4000 || !apiUrl.startsWith("https://")) return
            S28Prefs.saveSession(this@MainActivity, apiKey, refreshToken, apiUrl)
        }

        /** Raw device list [{id,name,category,online,status:[{code,value}]}] for the widget. */
        @JavascriptInterface
        fun saveDevices(json: String) {
            if (json.length > 1_000_000) return
            S28Prefs.saveDevices(this@MainActivity, json)
            SmartWidget.refreshAll(this@MainActivity)
        }

        /** Favourite device ids (JSON array) shown in the widget, in this order. */
        @JavascriptInterface
        fun saveFavorites(json: String) {
            if (json.length > 20_000) return
            S28Prefs.saveFavorites(this@MainActivity, json)
            SmartWidget.refreshAll(this@MainActivity)
        }

        @JavascriptInterface
        fun hasWidget(): Boolean = SmartWidget.hasWidgets(this@MainActivity)

        @JavascriptInterface
        fun pinWidget(): Boolean {
            val mgr = AppWidgetManager.getInstance(this@MainActivity)
            if (!mgr.isRequestPinAppWidgetSupported) return false
            runOnUiThread {
                mgr.requestPinAppWidget(ComponentName(this@MainActivity, SmartWidget::class.java), null, null)
            }
            return true
        }

        @JavascriptInterface
        fun getAppBuild(): Int = UpdateManager.currentBuild(this@MainActivity)

        @JavascriptInterface
        fun getAppVersion(): String = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        } catch (e: Exception) { "" }

        /** Checks GitHub Releases; answers via window.s28OnUpdate({current, latest, notes, manual, error}). */
        @JavascriptInterface
        fun checkUpdate(manual: Boolean) {
            Thread {
                val cur = UpdateManager.currentBuild(this@MainActivity)
                val rel = UpdateManager.latest()
                pendingUpdateUrl = rel?.apkUrl
                val json = JSONObject().apply {
                    put("current", cur); put("latest", rel?.build ?: 0)
                    put("notes", rel?.notes ?: ""); put("manual", manual); put("error", rel == null); put("detail", UpdateManager.lastError)
                }
                call("s28OnUpdate", json.toString())
            }.start()
        }

        /** Progress via window.s28OnUpdateProgress(p): 0..100 download, 101 installer opened,
         *  -1 failed, -2 needs "install unknown apps" permission. */
        @JavascriptInterface
        fun installUpdate() {
            val url = pendingUpdateUrl ?: return call("s28OnUpdateProgress", "-1")
            if (!UpdateManager.canInstall(this@MainActivity)) {
                runOnUiThread { UpdateManager.openInstallPermissionSettings(this@MainActivity) }
                return call("s28OnUpdateProgress", "-2")
            }
            Thread {
                val apk = UpdateManager.download(this@MainActivity, url) { p -> call("s28OnUpdateProgress", p.toString()) }
                if (apk == null) { call("s28OnUpdateProgress", "-1"); return@Thread }
                runOnUiThread { UpdateManager.installSession(this@MainActivity, apk) }
                call("s28OnUpdateProgress", "101")
            }.start()
        }

        @JavascriptInterface
        fun openUpdatePage() {
            runOnUiThread {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dvaosem/smart28/releases/latest")))
            }
        }
    }
}
