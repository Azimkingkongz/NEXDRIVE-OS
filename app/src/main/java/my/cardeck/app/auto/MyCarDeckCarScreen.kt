package my.cardeck.app.auto

import android.annotation.SuppressLint
import android.app.Presentation
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import my.cardeck.app.MediaStateHolder
import my.cardeck.app.MediaTrackerService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Renders the full NEON DRIVE — Tesla Minimal Edition HTML/CSS Dashboard onto
 * the CarLink / Android Auto screen Surface, plays FM Radio & USB/Phone MP3 audio
 * using the phone's own mobile internet (no hotspot required), and supports both
 * direct Surface Touch (Pan Mode) and a 1-tap Native CarLink Control Menu.
 */
class MyCarDeckCarScreen(carContext: CarContext) : Screen(carContext) {

    private val handler = Handler(Looper.getMainLooper())
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: DashboardPresentation? = null
    private var webView: WebView? = null
    private var surfaceWidth: Int = 1280
    private var surfaceHeight: Int = 720
    private var nativeMediaPlayer: MediaPlayer? = null
    private var curFmIdx = 0

    private val fmPresets = listOf(
        Triple("92.3", "ARENA FM", "https://stream.zeno.fm/f3wvbbqmdg8uv"),
        Triple("92.9", "HITZ FM", "https://stream.rcs.revma.com/488bf80298uvv"),
        Triple("103.3", "ERA FM", "https://stream.rcs.revma.com/55tyxs0298uvv"),
        Triple("101.8", "MY FM", "https://stream.rcs.revma.com/79f96r0298uvv"),
        Triple("96.7", "SINAR FM", "https://stream.rcs.revma.com/28429r0298uvv"),
        Triple("94.5", "MIX FM", "https://stream.rcs.revma.com/37829r0298uvv"),
        Triple("99.3", "RAAGA", "https://stream.rcs.revma.com/12w89r0298uvv"),
        Triple("105.7", "LITE FM", "https://stream.rcs.revma.com/61829r0298uvv")
    )

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

            surfaceWidth = width
            surfaceHeight = height

            handler.post {
                releaseSurfaceResources()
                val dm = carContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                virtualDisplay = dm.createVirtualDisplay(
                    "NeonDriveTeslaSurface",
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

        override fun onClick(x: Float, y: Float) {
            handler.post {
                // 1. Dispatch synthesized native MotionEvent to Presentation / WebView
                try {
                    val now = SystemClock.uptimeMillis()
                    val downEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
                    val upEvent = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, x, y, 0)
                    presentation?.window?.decorView?.dispatchTouchEvent(downEvent)
                    presentation?.window?.decorView?.dispatchTouchEvent(upEvent)
                    downEvent.recycle()
                    upEvent.recycle()
                } catch (_: Exception) {}

                // 2. Also trigger exact DOM click at normalized (x, y) viewport coordinates
                val normX = (x / surfaceWidth.coerceAtLeast(1)).coerceIn(0f, 1f)
                val normY = (y / surfaceHeight.coerceAtLeast(1)).coerceIn(0f, 1f)
                webView?.evaluateJavascript(
                    "(function(){ var el = document.elementFromPoint($normX * window.innerWidth, $normY * window.innerHeight); if(el) el.click(); })();",
                    null
                )
            }
        }

        override fun onScroll(distanceX: Float, distanceY: Float) {
            handler.post {
                webView?.evaluateJavascript(
                    "(function(){ var lists = document.querySelectorAll('.clean-scroll'); lists.forEach(function(l){ l.scrollTop += ${distanceY.toInt()}; }); })();",
                    null
                )
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

            override fun onDestroy(owner: LifecycleOwner) {
                try {
                    nativeMediaPlayer?.release()
                } catch (_: Exception) {}
                nativeMediaPlayer = null
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

    fun evalJsOnSurface(script: String) {
        handler.post {
            webView?.evaluateJavascript(script, null)
        }
    }

    fun playStreamOrUri(uriOrUrl: String) {
        try {
            nativeMediaPlayer?.release()
            nativeMediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                if (uriOrUrl.startsWith("http://") || uriOrUrl.startsWith("https://") || uriOrUrl.startsWith("content://")) {
                    setDataSource(carContext, Uri.parse(uriOrUrl))
                } else {
                    setDataSource(uriOrUrl)
                }
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (_: Exception) {}
    }

    fun cycleFmStation() {
        val (freq, name, url) = fmPresets[curFmIdx]
        curFmIdx = (curFmIdx + 1) % fmPresets.size
        evalJsOnSurface("tuneFm('$freq', '$name');")
        playStreamOrUri(url)
        CarToast.makeText(carContext, "FM $freq MHz - $name", CarToast.LENGTH_SHORT).show()
    }

    fun tuneSpecificFm(freq: String, name: String, url: String) {
        evalJsOnSurface("tuneFm('$freq', '$name');")
        playStreamOrUri(url)
        CarToast.makeText(carContext, "Playing FM $freq MHz - $name", CarToast.LENGTH_SHORT).show()
    }

    override fun onGetTemplate(): Template {
        // Top Bar: 4 Always-Clickable Native CarLink Buttons (Works even without Pan Mode!)
        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("⏯ Play")
                    .setOnClickListener {
                        evalJsOnSurface("togglePlay();")
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
            .addAction(
                Action.Builder()
                    .setTitle("📻 FM")
                    .setOnClickListener {
                        cycleFmStation()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle("🎛️ Menu")
                    .setOnClickListener {
                        screenManager.push(CarControlMenuScreen(carContext, this@MyCarDeckCarScreen, fmPresets))
                    }
                    .build()
            )
            .build()

        val builder = NavigationTemplate.Builder()
            .setActionStrip(actionStrip)

        // Enable Pan Mode + Surface Touch on Car API Level 2+ (with required PanModeListener!)
        try {
            if (carContext.carAppApiLevel >= 2) {
                builder.setPanModeListener { isInPanMode ->
                    if (isInPanMode) {
                        CarToast.makeText(
                            carContext,
                            "Touch Mode Aktif: Boleh tekan terus pada skrin",
                            CarToast.LENGTH_SHORT
                        ).show()
                    }
                }
                val mapStrip = ActionStrip.Builder()
                    .addAction(Action.PAN)
                    .build()
                builder.setMapActionStrip(mapStrip)
            }
        } catch (_: Exception) {}

        return builder.build()
    }

    private fun scanAndPushAudioToSurface() {
        val folderMap = linkedMapOf<String, MutableList<JSONObject>>()
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )
            carContext.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Audio Track"
                    val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Audio"
                    val durMs = cursor.getLong(durCol)
                    val path = cursor.getString(dataCol) ?: ""
                    if (durMs in 1..4000L) continue

                    val folderName = if (path.isNotEmpty()) {
                        File(path).parentFile?.name ?: "Phone_Music"
                    } else {
                        "Phone_Music"
                    }
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()
                    val totSec = (durMs / 1000).toInt().coerceAtLeast(1)
                    val durFormatted = String.format("%02d:%02d", totSec / 60, totSec % 60)

                    val trackObj = JSONObject().apply {
                        put("title", title)
                        put("artist", artist)
                        put("dur", durFormatted)
                        put("uri", path.ifEmpty { contentUri })
                    }
                    folderMap.getOrPut(folderName) { mutableListOf() }.add(trackObj)
                }
            }
        } catch (_: Exception) {}

        if (folderMap.isNotEmpty()) {
            val arr = JSONArray()
            for ((fName, tracks) in folderMap) {
                if (tracks.isEmpty()) continue
                arr.put(JSONObject().apply {
                    put("name", fName)
                    put("tag", "Phone / USB Storage")
                    put("tracks", JSONArray(tracks))
                })
            }
            val safeJson = JSONObject.quote(arr.toString())
            evalJsOnSurface("if(window.onRealUsbScanned) window.onRealUsbScanned($safeJson);")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    inner class DashboardPresentation(
        outerContext: Context,
        display: android.view.Display
    ) : Presentation(outerContext, display) {

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val wv = WebView(context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.setGeolocationEnabled(true)
                settings.mediaPlaybackRequiresUserGesture = false
                settings.textZoom = 100
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Thread { scanAndPushAudioToSurface() }.start()
                    }
                }
                webChromeClient = WebChromeClient()
                addJavascriptInterface(CarAndroidBridge(), "AndroidBridge")
                loadUrl("file:///android_asset/dashboard/index.html")
            }
            webView = wv
            setContentView(wv)
        }
    }

    inner class CarAndroidBridge {
        @JavascriptInterface
        fun scanUsbDrive() {
            Thread { scanAndPushAudioToSurface() }.start()
        }

        @JavascriptInterface
        fun playNativeAudio(uriOrPath: String): Boolean {
            playStreamOrUri(uriOrPath)
            return true
        }

        @JavascriptInterface
        fun setNativeAudioPlaying(play: Boolean) {
            try {
                nativeMediaPlayer?.let { mp ->
                    if (play && !mp.isPlaying) mp.start()
                    else if (!play && mp.isPlaying) mp.pause()
                }
            } catch (_: Exception) {}
        }

        @JavascriptInterface
        fun tuneHardwareFm(freqMhz: String) {
            try {
                val mhz = freqMhz.toFloatOrNull() ?: return
                val khz = (mhz * 1000).toInt()
                val hundredKhz = (mhz * 100).toInt()
                carContext.sendBroadcast(Intent("com.syu.ms.action.RADIO_FREQ").apply {
                    putExtra("freq", hundredKhz)
                })
                carContext.sendBroadcast(Intent("com.tw.radio.ACTION_TUNE_FREQ").apply {
                    putExtra("freq", hundredKhz)
                    putExtra("freq_khz", khz)
                })
            } catch (_: Exception) {}
        }

        @JavascriptInterface
        fun openHardwareRadioApp(): Boolean {
            cycleFmStation()
            return true
        }

        @JavascriptInterface
        fun launchApp(packageName: String, fallbackUrl: String) {
            launchPackageOnPhone(packageName)
        }
    }

    fun launchPackageOnPhone(packageName: String) {
        val pm = carContext.packageManager
        try {
            val launchIntent = pm.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                carContext.startActivity(launchIntent)
                CarToast.makeText(carContext, "Membuka aplikasi...", CarToast.LENGTH_SHORT).show()
                return
            }
        } catch (_: Exception) {}

        try {
            val keyword = when {
                packageName.contains("bitchord", true) -> "bitchord"
                packageName.contains("spotify", true) -> "spotify"
                packageName.contains("waze", true) -> "waze"
                packageName.contains("youtube", true) -> "youtube"
                packageName.contains("netflix", true) -> "netflix"
                else -> packageName.substringAfterLast(".")
            }
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            for (info in pm.queryIntentActivities(mainIntent, 0)) {
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm).toString()
                if (label.contains(keyword, true) || pkg.contains(keyword, true)) {
                    val found = pm.getLaunchIntentForPackage(pkg)
                    if (found != null) {
                        found.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        carContext.startActivity(found)
                        return
                    }
                }
            }
        } catch (_: Exception) {}
    }
}

/**
 * 100% Native CarLink Touch Menu Screen (ListTemplate) — guarantees that even if a CarLink
 * head unit restricts direct canvas touch, the user can tap any FM Radio station, USB/Phone
 * view, Lyrics, or App with 1-tap native CarLink touch.
 */
class CarControlMenuScreen(
    carContext: CarContext,
    private val mainScreen: MyCarDeckCarScreen,
    private val fmPresets: List<Triple<String, String, String>>
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val itemList = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle("🏠 Tukar Paparan: Home / USB / Lirik")
                    .addText("Tekan untuk tukar skrin pada dashboard Tesla")
                    .setOnClickListener {
                        mainScreen.evalJsOnSurface(
                            "(function(){ var h=document.getElementById('stageHome').classList.contains('active'); var u=document.getElementById('stageUsb').classList.contains('active'); switchStage(h ? 'usb' : (u ? 'lyrics' : 'home')); })();"
                        )
                        screenManager.pop()
                    }
                    .build()
            )
            .addItem(
                Row.Builder()
                    .setTitle("🎤 Buka BitChord / Spotify di Phone")
                    .addText("Buka aplikasi muzik & lirik terus")
                    .setOnClickListener {
                        mainScreen.launchPackageOnPhone("app.bitchord.music")
                        screenManager.pop()
                    }
                    .build()
            )

        for ((freq, name, url) in fmPresets.take(4)) {
            itemList.addItem(
                Row.Builder()
                    .setTitle("📻 $freq MHz — $name")
                    .addText("Mainkan terus guna data phone (Tanpa Hotspot)")
                    .setOnClickListener {
                        mainScreen.tuneSpecificFm(freq, name, url)
                        screenManager.pop()
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("NEON DRIVE — Menu Kawalan CarLink")
            .setHeaderAction(Action.BACK)
            .setSingleList(itemList.build())
            .build()
    }
}
