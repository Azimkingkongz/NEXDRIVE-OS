package my.cardeck.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private val mediaPoller = object : Runnable {
        override fun run() {
            MediaTrackerService.pollActiveSessions(this@MainActivity)
            val title = MediaStateHolder.title
            if (title.isNotEmpty()) {
                val safeTitle = JSONObject.quote(title)
                val safeArtist = JSONObject.quote(MediaStateHolder.artist)
                val posMs = (MediaStateHolder.currentEstimatedPositionSec() * 1000).toLong()
                val durMs = MediaStateHolder.durationMs
                val playing = MediaStateHolder.isPlaying
                webView.evaluateJavascript(
                    "if(window.onAndroidMediaUpdate) window.onAndroidMediaUpdate($safeTitle, $safeArtist, $posMs, $durMs, $playing);",
                    null
                )
            }
            handler.postDelayed(this, 1000L)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        requestLocationPermissionIfNeeded()

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setGeolocationEnabled(true)
            settings.mediaPlaybackRequiresUserGesture = false
            settings.textZoom = 100

            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, true, false)
                }
            }

            addJavascriptInterface(AndroidBridge(), "AndroidBridge")
            loadUrl("file:///android_asset/dashboard/index.html")
        }

        setContentView(webView)
    }

    private fun hideSystemBars() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        handler.post(mediaPoller)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(mediaPoller)
    }

    private fun requestLocationPermissionIfNeeded() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1001
            )
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun openNotificationAccessSettings() {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        @JavascriptInterface
        fun launchApp(packageName: String, fallbackUrl: String) {
            // 1. Try exact package name first (e.g. com.spotify.music, com.waze)
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    return
                }
            } catch (_: Exception) {}

            // 2. Smart Search: Match any installed app by app name or package keyword
            //    (Handles BitChord variants, Spotify Lite/Mod, YouTube ReVanced, TikTok Asia, etc.)
            try {
                val keyword = when {
                    packageName.contains("bitchord", true) -> "bitchord"
                    packageName.contains("spotify", true) -> "spotify"
                    packageName.contains("waze", true) -> "waze"
                    packageName.contains("youtube.music", true) -> "youtube music"
                    packageName.contains("youtube", true) -> "youtube"
                    packageName.contains("tiktok", true) || packageName.contains("trill", true) -> "tiktok"
                    packageName.contains("netflix", true) -> "netflix"
                    packageName.contains("whatsapp", true) -> "whatsapp"
                    packageName.contains("maps", true) -> "maps"
                    else -> packageName.substringAfterLast(".")
                }

                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
                for (info in resolveInfos) {
                    val pkg = info.activityInfo.packageName
                    val label = info.loadLabel(packageManager).toString()
                    if (label.contains(keyword, ignoreCase = true) || pkg.contains(keyword, ignoreCase = true)) {
                        val foundIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (foundIntent != null) {
                            foundIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(foundIntent)
                            return
                        }
                    }
                }
            } catch (_: Exception) {}

            // 3. Fallback if the app is not installed on the device
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "Aplikasi belum di-install dalam peranti ini",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }

        /**
         * Tunes the internal Android Car Player hardware FM tuner (connected to the car's physical aerial/antenna)
         * via MCU broadcast intents used by Topway (TS10/TS18), FYT/SYU (UIS7862/8581), XYAuto (T3L), and Microntek head units.
         * No internet connection is required.
         */
        @JavascriptInterface
        fun tuneHardwareFm(freqMhz: String) {
            try {
                val mhz = freqMhz.toFloatOrNull() ?: return
                val khz = (mhz * 1000).toInt() // e.g., 92.9 -> 92900 kHz
                val hundredKhz = (mhz * 100).toInt() // e.g., 92.9 -> 9290

                // 1. Microntek / MTC / Rockchip Car Head Units
                sendBroadcast(Intent("com.microntek.radio.tune").apply {
                    putExtra("freq", khz)
                    putExtra("band", 0) // FM1
                })

                // 2. FYT / SYU (UIS7862 / SC9853i / UIS8581) Car Head Units
                sendBroadcast(Intent("com.syu.ms.action.RADIO_FREQ").apply {
                    putExtra("freq", hundredKhz)
                })

                // 3. Topway (TS7 / TS9 / TS10 / TS18) & XYAuto (T3L / YT9216) Head Units
                sendBroadcast(Intent("com.tw.radio.ACTION_TUNE_FREQ").apply {
                    putExtra("freq", hundredKhz)
                    putExtra("freq_khz", khz)
                })
                sendBroadcast(Intent("cn.xyauto.action.RADIO_FREQ").apply {
                    putExtra("freq", hundredKhz)
                })
            } catch (_: Exception) {}
        }

        /**
         * Opens the built-in Hardware Aerial FM Radio app pre-installed on the Android Car Player.
         */
        @JavascriptInterface
        fun openHardwareRadioApp(): Boolean {
            val commonCarRadioPackages = listOf(
                "com.syu.radio",          // FYT / SYU (7862 / 8581)
                "com.ts.MainUI",          // Topway / TS10 / TS18 / XYAuto
                "com.tw.radio",           // Topway Radio
                "com.microntek.radio",    // MTC / PX5 / PX6
                "com.xyauto.radio",       // XYAuto T3L
                "com.forfan.carassist",   // Junsun / Forfan
                "com.android.fmradio",    // Standard Android Hardware FM
                "com.caf.fmradio",        // Qualcomm Hardware FM
                "com.mediatek.fmradio"    // MediaTek Hardware FM
            )
            for (pkg in commonCarRadioPackages) {
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                        return true
                    }
                } catch (_: Exception) {}
            }
            return false
        }
    }
}
