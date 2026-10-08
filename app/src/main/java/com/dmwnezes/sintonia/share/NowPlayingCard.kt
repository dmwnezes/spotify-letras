package com.dmwnezes.sintonia.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.dmwnezes.sintonia.data.Track
import java.io.File
import kotlin.math.max

/** O que vai no cartão "tocando agora". */
data class NowPlayingInfo(
    val track: Track,
    val positionMs: Long,
    val line: String?,
    val translation: String?,
)

/** Imagem 1080×1920 para Stories: capa grande, música, a linha que está tocando e o progresso. */
object NowPlayingCard {
    private const val W = 1080f
    private const val H = 1920f

    fun render(info: NowPlayingInfo, cover: Bitmap?): Bitmap {
        val bmp = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val pal = cover?.let { Palette.from(it).maximumColorCount(12).generate() }
        val vibrant = pal?.getVibrantColor(pal.getDominantColor(Color.rgb(224, 69, 123))) ?: Color.rgb(224, 69, 123)
        val deep = pal?.getDarkMutedColor(pal.getDarkVibrantColor(Color.rgb(18, 10, 36))) ?: Color.rgb(18, 10, 36)

        // Fundo: capa desfocada + cor da capa
        c.drawColor(deep)
        cover?.let {
            val tiny = Bitmap.createScaledBitmap(it, 10, 10, true)
            val big = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(tiny, 40, 40, true), W.toInt(), H.toInt(), true)
            p.alpha = 170; c.drawBitmap(big, 0f, 0f, p); p.alpha = 255
        }
        p.shader = RadialGradient(W * 0.5f, H * 0.3f, W, ColorUtils.setAlphaComponent(vibrant, 120), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p)
        p.shader = LinearGradient(0f, 0f, 0f, H, Color.argb(40, 0, 0, 0), Color.argb(200, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, H, p)
        p.shader = null

        val bold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 800, false) else Typeface.DEFAULT_BOLD
        val medium = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 500, false) else Typeface.DEFAULT
        val italic = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 500, true) else Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)

        // Selo "tocando agora"
        val tag = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = bold; textSize = 34f; letterSpacing = 0.12f }
        val tagText = "TOCANDO AGORA"
        val tw = tag.measureText(tagText)
        p.color = Color.argb(60, 255, 255, 255)
        c.drawRoundRect(RectF((W - tw) / 2 - 34, 150f, (W + tw) / 2 + 34, 222f), 36f, 36f, p)
        p.color = ColorUtils.setAlphaComponent(vibrant, 255).let { v -> lighten(v) }
        c.drawCircle((W - tw) / 2 - 8, 186f, 9f, p)
        c.drawText(tagText, (W - tw) / 2 + 12, 199f, tag)

        // Capa com sombra suave
        val rect = RectF((W - 800) / 2, 290f, (W + 800) / 2, 1090f)
        for (k in 1..12) {
            p.color = Color.argb(9, 0, 0, 0)
            val g = k * 5f
            c.drawRoundRect(RectF(rect).apply { inset(-g, -g); offset(0f, 26f) }, 46f + g, 46f + g, p)
        }
        if (cover != null) {
            val cp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    val m = Matrix(); val s = max(rect.width() / cover.width, rect.height() / cover.height)
                    m.setScale(s, s); m.postTranslate(rect.left, rect.top); setLocalMatrix(m)
                }
            }
            c.drawRoundRect(rect, 46f, 46f, cp)
        } else {
            p.color = Color.argb(50, 255, 255, 255); c.drawRoundRect(rect, 46f, 46f, p)
        }

        // Música e artista
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = bold; textSize = 66f }
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255); typeface = medium; textSize = 42f }
        val maxW = W - 280
        c.drawText(TextUtils.ellipsize(info.track.name, title, maxW, TextUtils.TruncateAt.END).toString(), 140f, 1200f, title)
        c.drawText(TextUtils.ellipsize(info.track.artistLine, sub, maxW, TextUtils.TruncateAt.END).toString(), 140f, 1262f, sub)

        // Linha que está tocando
        var y = 1350f
        info.line?.takeIf { it.isNotBlank() }?.let { line ->
            p.color = lighten(vibrant)
            c.drawRoundRect(RectF(140f, y, 148f, y + 120), 4f, 4f, p)
            val lp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = italic; textSize = 46f }
            val l = StaticLayout.Builder.obtain(line, 0, line.length, lp, (W - 340).toInt())
                .setLineSpacing(0f, 1.1f).setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            c.save(); c.translate(178f, y); l.draw(c); c.restore()
            y += l.height + 14
            info.translation?.let { tr ->
                val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 255, 255, 255); typeface = medium; textSize = 34f }
                c.drawText(TextUtils.ellipsize(tr, tp, W - 340, TextUtils.TruncateAt.END).toString(), 178f, y + 30, tp)
            }
        }

        // Progresso da música
        val by = 1660f
        val dur = info.track.durationMs.coerceAtLeast(1)
        val frac = (info.positionMs.toFloat() / dur).coerceIn(0f, 1f)
        p.color = Color.argb(70, 255, 255, 255); c.drawRoundRect(140f, by, W - 140, by + 10, 5f, 5f, p)
        p.color = Color.WHITE; c.drawRoundRect(140f, by, 140 + (W - 280) * frac, by + 10, 5f, 5f, p)
        c.drawCircle(140 + (W - 280) * frac, by + 5, 14f, p)
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 255, 255, 255); textSize = 30f; typeface = medium }
        c.drawText(fmt(info.positionMs), 140f, by + 60, small)
        val total = fmt(dur)
        c.drawText(total, W - 140 - small.measureText(total), by + 60, small)

        small.color = Color.argb(150, 255, 255, 255)
        val brand = "Ouça no Spotify · Sintonia"
        c.drawText(brand, (W - small.measureText(brand)) / 2, H - 110, small)
        return bmp
    }

    private fun lighten(color: Int): Int {
        val hsl = FloatArray(3); ColorUtils.colorToHSL(color, hsl)
        hsl[2] = hsl[2].coerceIn(0.6f, 0.78f); return ColorUtils.HSLToColor(hsl)
    }

    private fun fmt(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

    suspend fun share(context: Context, info: NowPlayingInfo) {
        val cover = ShareHelper.loadCover(context, info.track.imageUrl)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "tocando-agora.png")
        file.outputStream().use { render(info, cover).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.arquivos", file)
        val link = "https://open.spotify.com/track/${info.track.id}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "🎵 ${info.track.name} — ${info.track.artistLine}\n$link")
            clipData = ClipData.newRawUri("tocando agora", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartilhar o que está tocando").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
