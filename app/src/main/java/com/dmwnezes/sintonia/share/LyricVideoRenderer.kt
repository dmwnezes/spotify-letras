package com.dmwnezes.sintonia.share

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Surface
import com.dmwnezes.sintonia.data.Track
import com.dmwnezes.sintonia.lyrics.LrcParser
import com.dmwnezes.sintonia.lyrics.LyricLine
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class VideoFormat(val width: Int, val height: Int, val label: String) {
    STORY(1080, 1920, "Stories 9:16"),
    SQUARE(1080, 1080, "Quadrado 1:1"),
}

/** Cores em ARGB para desenhar fora do Compose. */
data class VideoColors(val deep: Int, val base: Int, val glow1: Int, val glow2: Int, val glow3: Int)

data class VideoSpec(
    val track: Track,
    val cover: Bitmap?,
    val colors: VideoColors,
    val lines: List<LyricLine>,   // letra inteira sincronizada (pode estar vazia)
    val startMs: Long,
    val endMs: Long,
    val format: VideoFormat,
)

/**
 * Gera um MP4 (H.264, 30 fps, sem áudio) com a capa, o nome da música e a letra
 * aparecendo no tempo certo. Desenha cada quadro num Canvas e codifica com MediaCodec.
 */
object LyricVideoRenderer {
    private const val FPS = 30
    private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC

    suspend fun render(spec: VideoSpec, out: File, onProgress: (Float) -> Unit) {
        val w = spec.format.width
        val h = spec.format.height
        val durationMs = (spec.endMs - spec.startMs).coerceIn(1000, 31_000)
        val frames = (durationMs * FPS / 1000).toInt()
        val frameUs = 1_000_000L / FPS

        val mf = MediaFormat.createVideoFormat(MIME, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, if (spec.format == VideoFormat.STORY) 9_000_000 else 6_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        codec.configure(mf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()
        out.parentFile?.mkdirs()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val drawer = FrameDrawer(spec)
        val info = MediaCodec.BufferInfo()
        var trackIndex = -1
        var written = 0L
        var muxing = false

        fun drain(endOfStream: Boolean) {
            var idleTries = 0
            while (true) {
                val idx = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream || ++idleTries > 300) return
                    }
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxing = true
                    }
                    idx >= 0 -> {
                        val buf = codec.getOutputBuffer(idx)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && muxing && buf != null) {
                            // Tempo de cada quadro definido por nós (o Canvas não carrega carimbo de tempo).
                            info.presentationTimeUs = written * frameUs
                            written++
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, buf, info)
                        }
                        codec.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        try {
            for (i in 0 until frames) {
                coroutineContext.ensureActive()
                val t = i * 1000L / FPS
                val canvas = lockCanvas(surface)
                try { drawer.draw(canvas, t) } finally { surface.unlockCanvasAndPost(canvas) }
                drain(false)
                if (i % 6 == 0) onProgress(i / frames.toFloat())
            }
            codec.signalEndOfInputStream()
            drain(true)
            onProgress(1f)
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { surface.release() }
            runCatching { if (muxing) muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun lockCanvas(surface: Surface): Canvas =
        if (Build.VERSION.SDK_INT >= 23) runCatching { surface.lockHardwareCanvas() }.getOrElse { surface.lockCanvas(null) }
        else surface.lockCanvas(null)
}

/** Desenha um quadro do vídeo no tempo [tMs] desde o começo do trecho. */
internal class FrameDrawer(private val spec: VideoSpec) {
    private val w = spec.format.width.toFloat()
    private val h = spec.format.height.toFloat()
    private val story = spec.format == VideoFormat.STORY
    private val hasLyrics = spec.lines.any { it.timeMs in (spec.startMs - 15_000)..spec.endMs && it.text.isNotBlank() }

    private val bold: Typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 800, false) else Typeface.DEFAULT_BOLD
    private val medium: Typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 500, false) else Typeface.DEFAULT

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val blurredCover: Bitmap? = spec.cover?.let {
        // Reduz muito e amplia de novo: dá um desfoque suave e barato.
        val tiny = Bitmap.createScaledBitmap(it, 12, 12, true)
        Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(tiny, 48, 48, true), w.toInt(), h.toInt(), true)
    }

    // Posição e tamanho da capa
    private val coverRect: RectF = when {
        story && hasLyrics -> RectF((w - 640) / 2, 250f, (w + 640) / 2, 250f + 640)
        story -> RectF((w - 820) / 2, 330f, (w + 820) / 2, 330f + 820)
        hasLyrics -> RectF(80f, 90f, 80f + 260, 90f + 260)
        else -> RectF((w - 600) / 2, 110f, (w + 600) / 2, 110f + 600)
    }
    private val coverPaint: Paint? = spec.cover?.let { bmp ->
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                val m = android.graphics.Matrix()
                val s = max(coverRect.width() / bmp.width, coverRect.height() / bmp.height)
                m.setScale(s, s)
                m.postTranslate(coverRect.left, coverRect.top)
                setLocalMatrix(m)
            }
        }
    }

    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = bold }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 255, 255, 255); typeface = medium }
    private val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = bold }
    private val layoutCache = HashMap<String, StaticLayout>()

    fun draw(c: Canvas, tMs: Long) {
        val abs = spec.startMs + tMs
        val tSec = tMs / 1000f
        drawBackground(c, tSec)
        drawCover(c, tSec)
        drawTitle(c)
        if (hasLyrics) drawLyrics(c, abs) else drawBars(c, tSec)
        drawFooter(c, tMs)
    }

    private fun drawBackground(c: Canvas, t: Float) {
        c.drawColor(spec.colors.deep)
        blurredCover?.let {
            paint.alpha = 150
            c.drawBitmap(it, 0f, 0f, paint)
            paint.alpha = 255
        }
        glow(c, spec.colors.glow1, 0.25f + 0.08f * cos(t * 0.6f), 0.28f + 0.05f * sin(t * 0.5f), 0.75f, 120)
        glow(c, spec.colors.glow2, 0.78f + 0.07f * sin(t * 0.45f), 0.55f + 0.06f * cos(t * 0.4f), 0.65f, 100)
        glow(c, spec.colors.glow3, 0.45f + 0.09f * sin(t * 0.35f + 1f), 0.85f, 0.6f, 90)
        paint.shader = LinearGradient(0f, 0f, 0f, h, Color.argb(70, 0, 0, 0), Color.argb(170, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    private fun glow(c: Canvas, color: Int, cx: Float, cy: Float, rK: Float, alpha: Int) {
        val r = min(w, h) * rK
        val x = w * cx
        val y = h * cy
        val col = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        paint.shader = RadialGradient(x, y, r, col, Color.argb(0, Color.red(color), Color.green(color), Color.blue(color)), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, paint)
        paint.shader = null
    }

    private fun drawCover(c: Canvas, t: Float) {
        val breathe = 1f + 0.012f * sin(t * 2f * PI.toFloat() * 0.5f)
        val r = if (story) 44f else 28f
        c.save()
        c.scale(breathe, breathe, coverRect.centerX(), coverRect.centerY())
        // sombra suave: camadas translúcidas crescendo
        for (k in 1..10) {
            paint.color = Color.argb(10, 0, 0, 0)
            val grow = k * 4.5f
            val sr = RectF(coverRect).apply { inset(-grow, -grow); offset(0f, 22f) }
            c.drawRoundRect(sr, r + grow, r + grow, paint)
        }
        paint.color = Color.WHITE
        if (coverPaint != null) c.drawRoundRect(coverRect, r, r, coverPaint)
        else {
            paint.color = Color.argb(60, 255, 255, 255)
            c.drawRoundRect(coverRect, r, r, paint)
            paint.color = Color.WHITE
        }
        c.restore()
    }

    private fun drawTitle(c: Canvas) {
        val track = spec.track
        if (!story && hasLyrics) {
            val x = coverRect.right + 40
            val maxW = w - x - 70
            titlePaint.textSize = 54f
            subPaint.textSize = 38f
            c.drawText(ellipsize(track.name, titlePaint, maxW), x, coverRect.top + 110, titlePaint)
            c.drawText(ellipsize(track.artistLine, subPaint, maxW), x, coverRect.top + 170, subPaint)
            return
        }
        val y = coverRect.bottom + if (story) 110 else 90
        titlePaint.textSize = if (story) 62f else 54f
        subPaint.textSize = if (story) 42f else 36f
        val maxW = w - 160
        c.drawText(ellipsize(track.name, titlePaint, maxW), 80f, y, titlePaint)
        c.drawText(ellipsize(track.artistLine, subPaint, maxW), 80f, y + (if (story) 62 else 54), subPaint)
    }

    private fun drawLyrics(c: Canvas, abs: Long) {
        val lines = spec.lines
        val idx = LrcParser.indexAt(lines, abs + 150).coerceAtLeast(0)
        val since = abs + 150 - lines[idx].timeMs
        val fadeIn = (since / 280f).coerceIn(0f, 1f)

        val top = if (story) coverRect.bottom + 270 else coverRect.bottom + 90
        val maxW = (w - 160).toInt()
        val centerY = if (story) top + 230 else top + 230

        // Linha atual
        val cur = lines[idx].text.ifBlank { "•  •  •" }
        val curL = layout(cur, if (story) 70f else 60f, maxW)
        val curY = centerY - curL.height / 2f
        // anterior
        if (idx > 0) {
            val prevL = layout(lines[idx - 1].text.ifBlank { "•  •  •" }, if (story) 46f else 40f, maxW, maxLines = 2)
            drawLayout(c, prevL, 80f, curY - prevL.height - 36, (0.32f * 255).toInt())
        }
        drawLayout(c, curL, 80f, curY + (1f - fadeIn) * 24f, (255 * (0.35f + 0.65f * fadeIn)).toInt())
        // próxima
        if (idx + 1 < lines.size) {
            val nextL = layout(lines[idx + 1].text.ifBlank { "•  •  •" }, if (story) 46f else 40f, maxW, maxLines = 1)
            drawLayout(c, nextL, 80f, curY + curL.height + 36, (0.45f * 255).toInt())
        }
    }

    private fun drawBars(c: Canvas, t: Float) {
        val n = 28
        val baseY = if (story) coverRect.bottom + 400 else h - 230
        val maxH = if (story) 170f else 150f
        val gap = (w - 160) / n
        val bw = gap * 0.55f
        val beat = (sin(t * 2f * PI.toFloat() * (100f / 60f)) + 1f) / 2f
        for (i in 0 until n) {
            val x = i / n.toFloat()
            val env = 0.25f + 0.75f * exp(-x * 1.8f)
            val v = env * (0.5f + 0.5f * sin(t * (2.1f + i * 0.17f) + i * 0.9f)) * (if (i < 7) 0.7f + 0.3f * beat else 1f)
            val bh = max(bw, v * maxH)
            paint.color = Color.argb(200, 255, 255, 255)
            c.drawRoundRect(80 + i * gap + (gap - bw) / 2, baseY - bh, 80 + i * gap + (gap + bw) / 2, baseY, bw / 2, bw / 2, paint)
        }
    }

    private fun drawFooter(c: Canvas, tMs: Long) {
        val total = (spec.endMs - spec.startMs).coerceAtLeast(1)
        val y = h - if (story) 210 else 90
        paint.color = Color.argb(60, 255, 255, 255)
        c.drawRoundRect(80f, y, w - 80, y + 8, 4f, 4f, paint)
        paint.color = Color.WHITE
        c.drawRoundRect(80f, y, 80 + (w - 160) * (tMs.toFloat() / total), y + 8, 4f, 4f, paint)
        subPaint.textSize = 30f
        c.drawText(fmt(spec.startMs + tMs), 80f, y + 52, subPaint)
        val brand = "Ouça no Spotify · Sintonia"
        c.drawText(brand, w - 80 - subPaint.measureText(brand), y + 52, subPaint)
    }

    private fun layout(text: String, size: Float, width: Int, maxLines: Int = 3): StaticLayout =
        layoutCache.getOrPut("$size|$maxLines|$text") {
            val p = TextPaint(lyricPaint).apply { textSize = size }
            StaticLayout.Builder.obtain(text, 0, text.length, p, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.08f)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        }

    private fun drawLayout(c: Canvas, l: StaticLayout, x: Float, y: Float, alpha: Int) {
        c.save()
        c.translate(x, y)
        l.paint.alpha = alpha.coerceIn(0, 255)
        l.draw(c)
        l.paint.alpha = 255
        c.restore()
    }

    private fun ellipsize(s: String, p: TextPaint, maxW: Float) = TextUtils.ellipsize(s, p, maxW, TextUtils.TruncateAt.END).toString()

    private fun fmt(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}
