package my.cardeck.app.auto

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import my.cardeck.app.MediaStateHolder
import my.cardeck.app.MediaTrackerService
import org.json.JSONObject

/**
 * Renders the full visual MyCarDeck 2.0 HTML/CSS Glass Dashboard directly onto
 * the Android Auto screen Surface using VirtualDisplay + Presentation.
 */
class MyCarDeckCarScreen(carContext: CarContext) : Screen(carContext) {

    private val handler = Handler(Looper.getMainLooper())
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: DashboardPresentation? = null
    private var webView: WebView? = null

    private val mediaPoller = object : Runnable {
        override fun run() {
            MediaTrackerService.pollActiveSessions(carContext)
            val title = MediaStateHolder.title
            if (title.isNotEmpty()) {
                val safeTitle = JSONObject.quote(title)
                val safeArtist = JSONObject.quote(MediaStateHolder.artist)
                val posMs = (MediaStateHolder.currentEstimatedPositionSec() * 1000).toLong()
                val durMs = MediaStateHolder.durationMs
                val playing = MediaStateHolder.isPlaying
                webView?.evaluateJavascript(
                    "if(window.onAndroidMediaUpdate) window.onAndroidMediaUpdate($safeTitle, $safeArtist, $posMs, $durMs, $playing);",
                    null
                )
            }
            handler.postDelayed(this, 1000L)
        }
    }

    private val surfaceCallback = object : SurfaceCallback {
        override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
            val surface = surfaceContainer.surface ?: return
            val width = surfaceContainer.width
            val height = surfaceContainer.height
            val dpi = surfaceContainer.dpi
            if (width <= 0 || height <= 0) return

            handler.post {
                releaseSurfaceResources()
                val dm = carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                virtualDisplay = dm.createVirtualDisplay(
                    "MyCarDeckAutoSurface",
                    width,
                    height,
                    dpi,
                    surface,
                    0
                )
                virtualDisplay?.display?.let { display ->
                    presentation = DashboardPresentation(carContext, display).also {
                        it.show()
                    }
                }
            }
        }

        override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
            handler.post {
                releaseSurfaceResources()
            }
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(surfaceCallback)
                handler.post(mediaPoller)
            }

            override fun onStop(owner: LifecycleOwner) {
                handler.removeCallbacks(mediaPoller)
                releaseSurfaceResources()
            }
        })
    }

    private fun releaseSurfaceResources() {
        try {
            presentation?.dismiss()
        } catch (_: Exception) {}
        presentation = null

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {}
        virtualDisplay = null
        webView = null
    }

    private fun evalJsOnSurface(script: String) {
        handler.post {
            webView?.evaluateJavascript(script, null)
        }
    }

    override fun onGetTemplate(): Template {
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("🏠 Cockpit")
                    .setOnClickListener {
                        evalJsOnSurface("switchStage('home', document.getElementById('railHome'));")
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("💾 USB Music")
                    .setOnClickListener {
                        evalJsOnSurface("switchStage('usb', document.getElementById('railUsb'));")
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("🎤 Lyrics")
                    .setOnClickListener {
                        evalJsOnSurface("switchStage('lyrics', document.getElementById('railLyrics'));")
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("⏭ Next")
                    .setOnClickListener {
                        evalJsOnSurface("nextTrack();")
                    }
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(actionStrip)
            .build()
    }

    @SuppressLint("SetJavaScriptEnabled")
    inner class DashboardPresentation(
        outerContext: Context,
        display: android.view.Display
    ) : Presentation(outerContext, display) {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setGeolocationEnabled(true)
                settings.mediaPlaybackRequiresUserGesture = false
                settings.textZoom = 100
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                loadUrl("file:///android_asset/dashboard/index.html")
            }
            webView = wv
            setContentView(wv)
        }
    }
}
