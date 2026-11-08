package my.cardeck.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

data class LyricLine(val timeSec: Double, val text: String)

object MediaStateHolder {
    @Volatile var sourceApp: String = ""
    @Volatile var title: String = ""
    @Volatile var artist: String = ""
    @Volatile var positionMs: Long = 0L
    @Volatile var durationMs: Long = 240000L
    @Volatile var isPlaying: Boolean = false
    @Volatile var lastUpdateEpochMs: Long = 0L
    @Volatile var lyrics: List<LyricLine> = emptyList()
    @Volatile var lastFetchedTrackKey: String = ""

    fun currentEstimatedPositionSec(): Double {
        val delta = if (isPlaying && lastUpdateEpochMs > 0) {
            System.currentTimeMillis() - lastUpdateEpochMs
        } else 0L
        return (positionMs + delta).coerceAtLeast(0L) / 1000.0
    }

    fun fetchLyricsIfNeeded() {
        val t = title.trim()
        val a = artist.trim()
        if (t.isEmpty()) return
        val key = "$t|$a"
        if (key == lastFetchedTrackKey) return
        lastFetchedTrackKey = key

        thread {
            try {
                val q = URLEncoder.encode("$t $a", "UTF-8")
                val url = URL("https://lrclib.net/api/search?q=$q")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "NEXDRIVE-OS-Android/2.0")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    var synced: String? = null
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val s = obj.optString("syncedLyrics", "")
                        if (s.isNotEmpty() && s != "null") {
                            synced = s
                            break
                        }
                    }
                    if (!synced.isNullOrEmpty()) {
                        lyrics = parseLrc(synced)
                    } else {
                        val plain = arr.getJSONObject(0).optString("plainLyrics", "")
                        lyrics = plain.lines().filter { it.isNotBlank() }.mapIndexed { idx, line ->
                            LyricLine(idx * 4.0, line.trim())
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        val regex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?](.*)""")
        val out = mutableListOf<LyricLine>()
        for (line in lrc.lines()) {
            val m = regex.find(line) ?: continue
            val min = m.groupValues[1].toIntOrNull() ?: 0
            val sec = m.groupValues[2].toIntOrNull() ?: 0
            val msStr = m.groupValues[3].padEnd(3, '0')
            val ms = msStr.toIntOrNull() ?: 0
            val text = m.groupValues[4].trim()
            if (text.isNotEmpty()) {
                out.add(LyricLine(min * 60.0 + sec + ms / 1000.0, text))
            }
        }
        return out
    }
}

/**
 * Play-Protect-Safe Media Tracker using standard Android & Spotify Media Broadcast Intents
 * (Does NOT use BIND_NOTIFICATION_LISTENER_SERVICE so Malaysia Google Play Protect never blocks installation).
 */
object MediaTrackerService {
    private var receiverRegistered = false

    private val mediaReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val act = intent.action ?: ""
            val pkgExtra = intent.getStringExtra("package") ?: intent.getStringExtra("app") ?: ""
            val track = intent.getStringExtra("track") ?: intent.getStringExtra("title")
            val artist = intent.getStringExtra("artist") ?: ""
            val length = intent.getLongExtra("length", 240000L)
            val pos = intent.getLongExtra("playbackPosition", 0L)
            val playing = intent.getBooleanExtra("playing", true)

            if (!track.isNullOrEmpty()) {
                if (act.contains("spotify", true) || pkgExtra.contains("spotify", true)) {
                    MediaStateHolder.sourceApp = "spotify"
                } else if (act.contains("bitchord", true) || pkgExtra.contains("bitchord", true)) {
                    MediaStateHolder.sourceApp = "bitchord"
                } else if (MediaStateHolder.sourceApp.isEmpty()) {
                    MediaStateHolder.sourceApp = "spotify"
                }
                MediaStateHolder.title = track
                MediaStateHolder.artist = artist
                MediaStateHolder.durationMs = if (length > 0) length else 240000L
                MediaStateHolder.positionMs = pos
                MediaStateHolder.isPlaying = playing
                MediaStateHolder.lastUpdateEpochMs = System.currentTimeMillis()
                MediaStateHolder.fetchLyricsIfNeeded()
            }
        }
    }

    fun pollActiveSessions(context: Context) {
        if (receiverRegistered) return
        try {
            val filter = IntentFilter().apply {
                addAction("com.android.music.metadatachanged")
                addAction("com.android.music.playstatechanged")
                addAction("com.spotify.music.metadatachanged")
                addAction("com.spotify.music.playbackstatechanged")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(mediaReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.applicationContext.registerReceiver(mediaReceiver, filter)
            }
            receiverRegistered = true
        } catch (_: Exception) {
        }
    }
}
