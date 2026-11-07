package my.cardeck.app

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

data class LyricLine(val timeSec: Double, val text: String)

object MediaStateHolder {
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

    fun currentLyricWindow(): Triple<String, String, String> {
        if (lyrics.isEmpty()) {
            return Triple(
                if (title.isNotEmpty()) "🎵 $title - $artist" else "Tiada lagu dikesan",
                "Mainkan lagu di Spotify / YT Music / BitChord",
                "Pastikan Notification Access dibenarkan untuk MyCarDeck"
            )
        }
        val nowSec = currentEstimatedPositionSec()
        var idx = 0
        for (i in lyrics.indices) {
            if (nowSec >= lyrics[i].timeSec) {
                idx = i
            } else {
                break
            }
        }
        val prev = if (idx > 0) lyrics[idx - 1].text else "..."
        val curr = "🎤 " + lyrics[idx].text
        val next = if (idx + 1 < lyrics.size) lyrics[idx + 1].text else "..."
        return Triple(prev, curr, next)
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
                    setRequestProperty("User-Agent", "MyCarDeck-Android/1.0")
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

class MediaTrackerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        pollActiveSessions(this)
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        pollActiveSessions(this)
    }

    companion object {
        fun pollActiveSessions(context: Context) {
            try {
                val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val comp = ComponentName(context, MediaTrackerService::class.java)
                val controllers: List<MediaController> = msm.getActiveSessions(comp)
                val active = controllers.firstOrNull {
                    it.playbackState?.state == PlaybackState.STATE_PLAYING
                } ?: controllers.firstOrNull() ?: return

                val meta = active.metadata
                val state = active.playbackState
                val newTitle = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
                val newArtist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
                val dur = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 240000L

                if (newTitle.isNotEmpty()) {
                    MediaStateHolder.title = newTitle
                    MediaStateHolder.artist = newArtist
                    MediaStateHolder.durationMs = if (dur > 0) dur else 240000L
                    MediaStateHolder.positionMs = state?.position ?: 0L
                    MediaStateHolder.isPlaying = state?.state == PlaybackState.STATE_PLAYING
                    MediaStateHolder.lastUpdateEpochMs = System.currentTimeMillis()
                    MediaStateHolder.fetchLyricsIfNeeded()
                }
            } catch (_: SecurityException) {
                // Notification listener permission not yet enabled by user
            } catch (_: Exception) {
            }
        }
    }
}
