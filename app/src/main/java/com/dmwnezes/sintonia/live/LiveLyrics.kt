package com.dmwnezes.sintonia.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dmwnezes.sintonia.AppGraph
import com.dmwnezes.sintonia.LyricsState
import com.dmwnezes.sintonia.MainActivity
import com.dmwnezes.sintonia.PlaybackState
import com.dmwnezes.sintonia.R
import com.dmwnezes.sintonia.TranslationState
import com.dmwnezes.sintonia.lyrics.LrcParser
import com.dmwnezes.sintonia.lyrics.Lyrics
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** O que a notificação e o widget mostram num dado instante. */
data class LiveSnapshot(
    val trackId: String?,
    val title: String,
    val artist: String,
    val line: String?,        // linha atual da letra
    val translation: String?, // tradução da linha atual
    val next: String?,        // próxima linha
    val playing: Boolean,
    val cover: Bitmap?,
    val accent: Int,
) {
    companion object {
        fun from(s: PlaybackState, offsetMs: Long, showTranslation: Boolean): LiveSnapshot {
            val np = s.now
            val track = np?.track
            var line: String? = null
            var next: String? = null
            var tr: String? = null
            val lyrics = (s.lyrics as? LyricsState.Ready)?.lyrics
            if (track != null && lyrics is Lyrics.Synced) {
                val pos = np.positionAt(SystemClock.elapsedRealtime()) + offsetMs + 250
                val idx = LrcParser.indexAt(lyrics.lines, pos)
                if (idx >= 0) {
                    line = lyrics.lines[idx].text.ifBlank { "♪" }
                    tr = (s.translation as? TranslationState.Ready)?.lines?.getOrNull(idx)?.takeIf { showTranslation }
                }
                next = lyrics.lines.drop(idx + 1).firstOrNull { it.text.isNotBlank() }?.text
            } else if (track != null && lyrics == Lyrics.Instrumental) {
                line = "♪ Instrumental"
            }
            return LiveSnapshot(
                trackId = track?.id,
                title = track?.name ?: "Nada tocando",
                artist = track?.artistLine.orEmpty(),
                line = line,
                translation = tr,
                next = next,
                playing = np?.isPlaying == true,
                cover = s.cover,
                accent = s.colors.glow1.toArgb(),
            )
        }
    }
}

/**
 * Serviço em primeiro plano que mantém a letra atualizada fora do app:
 * notificação (aparece na tela de bloqueio) e widget da tela inicial.
 * Para sozinho depois de 15 minutos sem música tocando.
 */
class LiveLyricsService : Service() {

    companion object {
        const val ACTION_START = "com.dmwnezes.sintonia.START"
        const val ACTION_STOP = "com.dmwnezes.sintonia.STOP"
        const val ACTION_PREV = "com.dmwnezes.sintonia.PREV"
        const val ACTION_TOGGLE = "com.dmwnezes.sintonia.TOGGLE"
        const val ACTION_NEXT = "com.dmwnezes.sintonia.NEXT"
        private const val CHANNEL = "letra_ao_vivo"
        private const val NOTIF_ID = 42
        private const val IDLE_LIMIT_MS = 15 * 60 * 1000L

        @Volatile var running = false
            private set
        @Volatile var last: LiveSnapshot? = null
            private set

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, LiveLyricsService::class.java).setAction(ACTION_START))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LiveLyricsService::class.java))
        }

        fun actionIntent(ctx: Context, action: String, code: Int): PendingIntent {
            val i = Intent(ctx, LiveLyricsService::class.java).setAction(action)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            return if (Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(ctx, code, i, flags)
            else PendingIntent.getService(ctx, code, i, flags)
        }

        fun openAppIntent(ctx: Context): PendingIntent = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private val scope = MainScope()
    private var loop: Job? = null
    private val playback get() = AppGraph.playback

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AppGraph.prefs.liveLyrics = false
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground(last ?: LiveSnapshot.from(playback.state.value, AppGraph.prefs.lyricsOffsetMs, AppGraph.prefs.showTranslation))
        if (!AppGraph.prefs.isLoggedIn) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PREV -> playback.previous()
            ACTION_TOGGLE -> playback.togglePlay()
            ACTION_NEXT -> playback.next()
        }
        startLoop()
        return START_STICKY
    }

    private fun goForeground(s: LiveSnapshot) {
        ensureChannel()
        val n = buildNotification(s)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
        running = true
    }

    private fun startLoop() {
        if (loop?.isActive == true) return
        playback.acquire("servico")
        loop = scope.launch {
            var lastKey = ""
            var activeAt = SystemClock.elapsedRealtime()
            while (isActive) {
                val prefs = AppGraph.prefs
                val snap = LiveSnapshot.from(playback.state.value, prefs.lyricsOffsetMs, prefs.showTranslation)
                val key = listOf(snap.trackId, snap.line, snap.translation, snap.next, snap.playing, snap.cover?.generationId).joinToString("|")
                if (key != lastKey) {
                    lastKey = key
                    last = snap
                    getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(snap))
                    LyricsWidget.updateAll(this@LiveLyricsService)
                }
                if (snap.playing) activeAt = SystemClock.elapsedRealtime()
                else if (SystemClock.elapsedRealtime() - activeAt > IDLE_LIMIT_MS) { stopSelf(); break }
                delay(250)
            }
        }
    }

    override fun onDestroy() {
        loop?.cancel()
        playback.release("servico")
        scope.cancel()
        running = false
        LyricsWidget.updateAll(this)
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Letra ao vivo", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Mostra a linha da letra que está tocando, inclusive na tela de bloqueio"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    private fun buildNotification(s: LiveSnapshot): Notification {
        val title = s.line ?: s.title
        val detail = listOfNotNull(s.translation, s.next?.let { "→ $it" }).joinToString("\n")
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_lyrics)
            .setContentTitle(title)
            .setContentText(s.translation ?: s.next ?: s.artist)
            .setSubText(if (s.line != null) "${s.title} · ${s.artist}" else s.artist)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail.ifBlank { s.artist }).setBigContentTitle(title))
            .setLargeIcon(s.cover)
            .setColor(s.accent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openAppIntent(this))
            .addAction(R.drawable.ic_w_prev, "Anterior", actionIntent(this, ACTION_PREV, 1))
            .addAction(if (s.playing) R.drawable.ic_w_pause else R.drawable.ic_w_play, if (s.playing) "Pausar" else "Tocar", actionIntent(this, ACTION_TOGGLE, 2))
            .addAction(R.drawable.ic_w_next, "Próxima", actionIntent(this, ACTION_NEXT, 3))
            .addAction(R.drawable.ic_w_close, "Fechar", PendingIntent.getService(this, 4,
                Intent(this, LiveLyricsService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE))
            .build()
    }
}

/** Widget da tela inicial com a capa, a linha atual e os controles. */
class LyricsWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, build(context))
    }

    companion object {
        fun updateAll(ctx: Context) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, LyricsWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, build(ctx))
        }

        private fun build(ctx: Context): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_lyrics)
            val s = LiveLyricsService.last
            val live = LiveLyricsService.running

            v.setTextViewText(R.id.w_title, s?.title ?: "Sintonia")
            v.setTextViewText(R.id.w_artist, s?.artist.orEmpty())
            val cover = s?.cover?.let { Bitmap.createScaledBitmap(it, 160, 160, true) }
            if (cover != null) v.setImageViewBitmap(R.id.w_cover, cover) else v.setImageViewResource(R.id.w_cover, R.drawable.ic_stat_lyrics)

            when {
                !live -> {
                    v.setTextViewText(R.id.w_line, "Toque para ver a letra ao vivo")
                    v.setTextViewText(R.id.w_sub, "A letra aparece aqui enquanto a música toca")
                    v.setOnClickPendingIntent(R.id.w_line, LiveLyricsService.actionIntent(ctx, LiveLyricsService.ACTION_START, 10))
                }
                else -> {
                    v.setTextViewText(R.id.w_line, s?.line ?: if (s?.trackId == null) "Nada tocando no Spotify" else "…")
                    v.setTextViewText(R.id.w_sub, s?.translation ?: s?.next?.let { "→ $it" } ?: "")
                    v.setOnClickPendingIntent(R.id.w_line, LiveLyricsService.openAppIntent(ctx))
                }
            }
            v.setTextColor(R.id.w_sub, if (s?.translation != null) (s.accent and 0x00FFFFFF) or (0xE6 shl 24) else 0x99FFFFFF.toInt())
            v.setImageViewResource(R.id.w_play, if (s?.playing == true && live) R.drawable.ic_w_pause else R.drawable.ic_w_play)
            v.setOnClickPendingIntent(R.id.w_prev, LiveLyricsService.actionIntent(ctx, LiveLyricsService.ACTION_PREV, 11))
            v.setOnClickPendingIntent(R.id.w_play, LiveLyricsService.actionIntent(ctx, LiveLyricsService.ACTION_TOGGLE, 12))
            v.setOnClickPendingIntent(R.id.w_next, LiveLyricsService.actionIntent(ctx, LiveLyricsService.ACTION_NEXT, 13))
            v.setOnClickPendingIntent(R.id.w_cover, LiveLyricsService.openAppIntent(ctx))
            v.setOnClickPendingIntent(R.id.w_title, LiveLyricsService.openAppIntent(ctx))
            return v
        }
    }
}
