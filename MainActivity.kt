package com.nexus.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    companion object {
        // ⚠️ This must match your live Hugging Face Space URL exactly.
        const val LIVE_URL = "https://hemel24-massanger.hf.space"
        const val TAG = "NexusApp"
        const val REQ_MIC_PERMISSION = 1001
        const val REQ_NOTIF_PERMISSION = 1002
    }

    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var liveUrlLoaded = false
    private var pendingNotificationSender: String? = null

    private val fileChooserLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val results = if (result.resultCode == Activity.RESULT_OK && data?.data != null) {
                arrayOf(data.data!!)
            } else null
            filePathCallback?.onReceiveValue(results)
            filePathCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setContentView(webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true          // required — the app uses localStorage
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(false)
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        webView.addJavascriptInterface(NativeBridge(), "NexusNative")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (url.startsWith(LIVE_URL)) {
                    liveUrlLoaded = true
                    fetchAndSendFcmToken()
                    pendingNotificationSender?.let { sender ->
                        openRoomFromNotification(sender)
                        pendingNotificationSender = null
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Needed so <input type="file"> (profile photo / posts / cover) actually opens a picker.
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback = callback
                val intent = params.createIntent()
                try {
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    filePathCallback = null
                    return false
                }
                return true
            }

            // Needed so the voice/PTT feature (getUserMedia audio) can access the microphone.
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val wanted = request.resources.filter {
                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    }
                    if (wanted.isNotEmpty() &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        request.grant(wanted.toTypedArray())
                    } else {
                        request.deny()
                    }
                }
            }
        }

        requestRuntimePermissions()
        handleNotificationIntent(intent)

        // Load the local animated splash first; it hands off to the live URL itself.
        webView.loadUrl("file:///android_asset/www/splash.html")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
        if (liveUrlLoaded) {
            pendingNotificationSender?.let { sender ->
                openRoomFromNotification(sender)
                pendingNotificationSender = null
            }
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val sender = intent?.getStringExtra("sender")
        if (!sender.isNullOrEmpty()) {
            pendingNotificationSender = sender
        }
    }

    private fun openRoomFromNotification(sender: String) {
        val escaped = sender.replace("'", "\\'")
        webView.evaluateJavascript(
            "window.openRoomFromNotification && window.openRoomFromNotification('$escaped');",
            null
        )
    }

    /** Called from splash.html once its loading animation has played. */
    inner class NativeBridge {
        @android.webkit.JavascriptInterface
        fun splashDone() {
            runOnUiThread { webView.loadUrl(LIVE_URL) }
        }
    }

    private fun fetchAndSendFcmToken() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "FCM token fetch failed", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result ?: return@addOnCompleteListener
                val escaped = token.replace("'", "\\'")
                runOnUiThread {
                    webView.evaluateJavascript(
                        "window.onFcmToken && window.onFcmToken('$escaped');",
                        null
                    )
                }
            }
        } catch (e: IllegalStateException) {
            // Firebase not configured yet (no google-services.json) — app still works fine,
            // it just won't receive push notifications until Firebase is set up.
            Log.w(TAG, "Firebase not initialized — skipping FCM token fetch", e)
        }
    }

    private fun requestRuntimePermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQ_MIC_PERMISSION)
        }
    }

    override fun onBackPressed() {
        // The web app manages its own screens with the browser History API
        // (pushState/popstate — chat, requests panel, admin panel, etc.).
        // webView.goBack() replays that history correctly and fires the page's
        // popstate handler, so this is the same "step back one screen" behavior
        // as the app's own in-app ← button. Once there's nothing left in the
        // WebView's history (the app's home screen), fall through to the
        // normal Android behavior (minimize/exit).
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
