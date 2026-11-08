package my.cardeck.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var nativeMediaPlayer: MediaPlayer? = null

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

        requestPermissionsIfNeeded()

        webView = WebView(this).apply {
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
                    // Automatically scan USB Pendrive & local audio as soon as dashboard loads
                    Thread { scanAndPushUsbAudio(false) }.start()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    callback?.invoke(origin, true, false)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                    this@MainActivity.filePathCallback = filePathCallback
                    return try {
                        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "audio/*"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                        startActivityForResult(Intent.createChooser(intent, "Select USB Audio Files"), 2002)
                        true
                    } catch (_: Exception) {
                        this@MainActivity.filePathCallback = null
                        false
                    }
                }
            }

            addJavascriptInterface(AndroidBridge(), "AndroidBridge")
            loadUrl("file:///android_asset/dashboard/index.html")
        }

        setContentView(webView)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2002) {
            val callback = filePathCallback
            filePathCallback = null
            if (resultCode == Activity.RESULT_OK && data != null) {
                val clip = data.clipData
                val results = when {
                    clip != null -> Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                    data.data != null -> arrayOf(data.data!!)
                    else -> null
                }
                callback?.onReceiveValue(results)
            } else {
                callback?.onReceiveValue(null)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            Thread { scanAndPushUsbAudio(false) }.start()
        }
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

    override fun onDestroy() {
        super.onDestroy()
        try {
            nativeMediaPlayer?.release()
        } catch (_: Exception) {}
        nativeMediaPlayer = null
    }

    private fun requestPermissionsIfNeeded() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        }
    }

    /**
     * Scans both Android MediaStore and direct USB OTG / Pendrive mount paths
     * (/storage/udisk*, /mnt/media_rw/*, /storage/usb*, etc.) on Android Car Players.
     */
    private fun scanAndPushUsbAudio(showToastOnEmpty: Boolean) {
        val folderMap = linkedMapOf<String, MutableList<JSONObject>>()

        // 1. Query Android MediaStore (covers indexed internal + USB Pendrive storage)
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )
            contentResolver.query(
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
                    val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "USB Audio"
                    val durMs = cursor.getLong(durCol)
                    val path = cursor.getString(dataCol) ?: ""
                    if (durMs in 1..4000L) continue // skip short system notification beeps

                    val folderName = if (path.isNotEmpty()) {
                        File(path).parentFile?.name ?: "USB_Music"
                    } else {
                        "USB_Music"
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

        // 2. Direct filesystem scan of common Android Car Player USB Pendrive mount points
        val usbMountRoots = mutableListOf(
            File("/storage"),
            File("/mnt/media_rw"),
            File("/mnt/usb_storage"),
            File("/storage/udisk"),
            File("/storage/udisk0"),
            File("/storage/udisk1"),
            File("/storage/udisk2"),
            File("/storage/usb"),
            File("/storage/usbotg")
        )
        try {
            ContextCompat.getExternalFilesDirs(this, null).forEach { dir ->
                // Walk up to the root of each external volume (e.g. /storage/XXXX-XXXX)
                var cur = dir
                while (cur != null && cur.parentFile != null && cur.parentFile?.name != "storage") {
                    cur = cur.parentFile
                }
                if (cur != null) usbMountRoots.add(cur)
            }
        } catch (_: Exception) {}

        val audioExts = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg")
        val visitedDirs = mutableSetOf<String>()

        fun scanDirRecursive(dir: File, depth: Int) {
            if (depth > 3 || !dir.exists() || !dir.canRead()) return
            val canon = try { dir.canonicalPath } catch (_: Exception) { dir.absolutePath }
            if (!visitedDirs.add(canon)) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (child.isDirectory) {
                    if (!child.name.startsWith(".") && child.name != "Android") {
                        scanDirRecursive(child, depth + 1)
                    }
                } else {
                    val ext = child.extension.lowercase()
                    if (ext in audioExts && child.length() > 30_000) {
                        val fName = dir.name.ifEmpty { "USB_Pendrive" }
                        val list = folderMap.getOrPut(fName) { mutableListOf() }
                        val cleanTitle = child.nameWithoutExtension
                        if (list.none { it.optString("title") == cleanTitle }) {
                            list.add(JSONObject().apply {
                                put("title", cleanTitle)
                                put("artist", "USB Pendrive · ${ext.uppercase()}")
                                put("dur", "USB")
                                put("uri", child.absolutePath)
                            })
                        }
                    }
                }
            }
        }

        for (root in usbMountRoots) {
            try {
                if (root.name == "storage" || root.name == "media_rw") {
                    root.listFiles()?.forEach { sub ->
                        if (sub.isDirectory && sub.name != "self" && sub.name != "emulated") {
                            scanDirRecursive(sub, 0)
                        }
                    }
                } else {
                    scanDirRecursive(root, 0)
                }
            } catch (_: Exception) {}
        }

        if (folderMap.isNotEmpty()) {
            val arr = JSONArray()
            for ((fName, tracks) in folderMap) {
                if (tracks.isEmpty()) continue
                val fObj = JSONObject().apply {
                    put("name", fName)
                    put("tag", "USB / Local Storage")
                    put("tracks", JSONArray(tracks))
                }
                arr.put(fObj)
            }
            val safeJson = JSONObject.quote(arr.toString())
            runOnUiThread {
                webView.evaluateJavascript("if(window.onRealUsbScanned) window.onRealUsbScanned($safeJson);", null)
            }
        } else if (showToastOnEmpty) {
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "Tiada fail MP3 dijumpai. Sila cucuk USB Pendrive atau pilih folder.",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun openNotificationAccessSettings() {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        @JavascriptInterface
        fun scanUsbDrive() {
            Thread { scanAndPushUsbAudio(true) }.start()
        }

        @JavascriptInterface
        fun playNativeAudio(uriOrPath: String): Boolean {
            return try {
                nativeMediaPlayer?.release()
                nativeMediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    if (uriOrPath.startsWith("http://") || uriOrPath.startsWith("https://") || uriOrPath.startsWith("content://")) {
                        setDataSource(this@MainActivity, Uri.parse(uriOrPath))
                    } else {
                        setDataSource(uriOrPath)
                    }
                    setOnPreparedListener { it.start() }
                    prepareAsync()
                }
                true
            } catch (_: Exception) {
                false
            }
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
        fun launchApp(packageName: String, fallbackUrl: String) {
            // 1. Try exact package name first (e.g. com.spotify.music, com.waze, com.netflix.mediaclient)
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    return
                }
            } catch (_: Exception) {}

            // 2. Smart Search: Match any installed app by app name or package keyword
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
         * Tunes the internal Android Car Player hardware FM tuner via MCU broadcast intents
         * across Topway, FYT/SYU, XYAuto/XYGala, Autochips/ATC, Jancar, and Microntek head units.
         */
        @JavascriptInterface
        fun tuneHardwareFm(freqMhz: String) {
            try {
                val mhz = freqMhz.toFloatOrNull() ?: return
                val khz = (mhz * 1000).toInt()        // e.g., 92.3 -> 92300 kHz
                val hundredKhz = (mhz * 100).toInt()  // e.g., 92.3 -> 9230
                val tenKhz = (mhz * 10).toInt()       // e.g., 92.3 -> 923

                // 1. Microntek / MTC / Rockchip PX5/PX6/PX30
                sendBroadcast(Intent("com.microntek.radio.tune").apply {
                    putExtra("freq", khz)
                    putExtra("band", 0)
                })

                // 2. FYT / SYU (UIS7862 / UIS8581 / SC9853i)
                sendBroadcast(Intent("com.syu.ms.action.RADIO_FREQ").apply {
                    putExtra("freq", hundredKhz)
                })
                sendBroadcast(Intent("com.syu.radio.ACTION_TUNE").apply {
                    putExtra("freq", hundredKhz)
                    putExtra("freq_khz", khz)
                })

                // 3. Topway (TS7 / TS9 / TS10 / TS18)
                sendBroadcast(Intent("com.tw.radio.ACTION_TUNE_FREQ").apply {
                    putExtra("freq", hundredKhz)
                    putExtra("freq_khz", khz)
                })
                sendBroadcast(Intent("com.ts.MainUI.ACTION_RADIO_FREQ").apply {
                    putExtra("freq", tenKhz)
                    putExtra("freq100", hundredKhz)
                })

                // 4. Allwinner / XYAuto / XYGala (T3L / T5 / T8 / YT9216)
                sendBroadcast(Intent("cn.xyauto.action.RADIO_FREQ").apply {
                    putExtra("freq", hundredKhz)
                })
                sendBroadcast(Intent("com.xyauto.broadcast.RADIO_FREQ").apply {
                    putExtra("freq", hundredKhz)
                    putExtra("freq_khz", khz)
                })
                sendBroadcast(Intent("com.xygala.pioneer.radio.TUNE").apply {
                    putExtra("freq", khz)
                })

                // 5. Autochips / ATC (AC8227L / AC8257) & Jancar
                sendBroadcast(Intent("com.autochips.radio.ACTION_TUNE").apply {
                    putExtra("freq", khz)
                    putExtra("freq_100khz", hundredKhz)
                })
                sendBroadcast(Intent("com.jancar.radio.ACTION_TUNE").apply {
                    putExtra("freq", khz)
                })
            } catch (_: Exception) {}
        }

        /**
         * Opens the built-in Hardware Aerial FM Radio app pre-installed on ANY Android Car Player.
         */
        @JavascriptInterface
        fun openHardwareRadioApp(): Boolean {
            val commonCarRadioPackages = listOf(
                "com.syu.radio",
                "com.ts.MainUI",
                "com.tw.radio",
                "com.microntek.radio",
                "com.xyauto.radio",
                "com.xygala.radio",
                "com.autochips.fmradio",
                "com.autochips.radio",
                "com.atc.radio",
                "com.jancar.radio",
                "com.car.radio",
                "com.android.car.radio",
                "com.qf.radio",
                "com.glsx.radio",
                "com.szchoiceway.radio",
                "com.forfan.carassist",
                "com.android.fmradio",
                "com.caf.fmradio",
                "com.mediatek.fmradio"
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

            // Smart scan: find ANY installed launcher activity with "radio" or "fm" in its label or package name
            try {
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = packageManager.queryIntentActivities(mainIntent, 0)
                for (info in resolveInfos) {
                    val pkg = info.activityInfo.packageName
                    val name = info.activityInfo.name
                    val label = info.loadLabel(packageManager).toString()
                    if (pkg != packageName && (
                        label.equals("Radio", ignoreCase = true) ||
                        label.equals("FM Radio", ignoreCase = true) ||
                        label.equals("FM", ignoreCase = true) ||
                        label.contains("radio", ignoreCase = true) ||
                        pkg.contains("radio", ignoreCase = true) ||
                        name.contains("radio", ignoreCase = true)
                    )) {
                        val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_LAUNCHER)
                            setClassName(pkg, name)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(explicitIntent)
                        return true
                    }
                }
            } catch (_: Exception) {}

            runOnUiThread {
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "Tiada aplikasi Hardware FM Radio dijumpai dalam peranti ini",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            return false
        }
    }
}
