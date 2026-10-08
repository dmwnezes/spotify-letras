package com.dmwnezes.sintonia.notebook

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
import androidx.palette.graphics.Palette
import com.dmwnezes.sintonia.share.ShareHelper
import java.io.File
import kotlin.math.max

/** Dados de um trecho para virar imagem. */
data class Quote(val text: String, val translation: String?, val track: String, val artist: String, val imageUrl: String?)

/** Desenha o trecho como imagem 1080×1350 (formato de post), com a capa desfocada ao fundo. */
object QuoteImage {
    const val W = 1080
    const val H = 1350

    fun render(q: Quote, cover: Bitmap?): Bitmap {
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val pal = cover?.let { Palette.from(it).maximumColorCount(12).generate() }
        val vibrant = pal?.getVibrantColor(pal.getDominantColor(Color.rgb(224, 69, 123))) ?: Color.rgb(224, 69, 123)
        val deep = pal?.getDarkMutedColor(pal.getDarkVibrantColor(Color.rgb(18, 10, 36))) ?: Color.rgb(18, 10, 36)

        // Fundo: cor escura + capa bem desfocada + brilho
        c.drawColor(deep)
        cover?.let {
            val tiny = Bitmap.createScaledBitmap(it, 10, 10, true)
            val big = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(tiny, 40, 40, true), W, H, true)
            paint.alpha = 160
            c.drawBitmap(big, 0f, 0f, paint)
            paint.alpha = 255
        }
        paint.shader = RadialGradient(W * 0.2f, H * 0.18f, W * 0.9f,
            Color.argb(110, Color.red(vibrant), Color.green(vibrant), Color.blue(vibrant)), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), Color.argb(60, 0, 0, 0), Color.argb(190, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        paint.shader = null

        val bold = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 800, false) else Typeface.DEFAULT_BOLD
        val regular = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 400, true) else Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)

        // Aspas grandes
        val quoteMark = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = vibrant.withMinLightness(); typeface = bold; textSize = 260f
        }
        c.drawText("“", 70f, 330f, quoteMark)

        // Texto do trecho: diminui a fonte até caber
        val maxW = W - 180
        var size = 76f
        var layout: StaticLayout
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = bold }
        while (true) {
            textPaint.textSize = size
            layout = StaticLayout.Builder.obtain(q.text, 0, q.text.length, textPaint, maxW)
                .setLineSpacing(0f, 1.1f).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            if (layout.height <= 560 || size <= 40f) break
            size -= 4f
        }
        var y = 360f
        c.save(); c.translate(90f, y); layout.draw(c); c.restore()
        y += layout.height + 34

        q.translation?.takeIf { it.isNotBlank() }?.let { tr ->
            val trPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(185, 255, 255, 255); typeface = regular; textSize = max(30f, size * 0.52f) }
            val trLayout = StaticLayout.Builder.obtain(tr, 0, tr.length, trPaint, maxW)
                .setLineSpacing(0f, 1.1f).setMaxLines(4).setEllipsize(TextUtils.TruncateAt.END).build()
            c.save(); c.translate(90f, y); trLayout.draw(c); c.restore()
        }

        // Rodapé: capa pequena + música
        val thumb = RectF(90f, H - 250f, 90f + 150, H - 100f)
        if (cover != null) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                    val m = Matrix(); val s = max(thumb.width() / cover.width, thumb.height() / cover.height)
                    m.setScale(s, s); m.postTranslate(thumb.left, thumb.top); setLocalMatrix(m)
                }
            }
            c.drawRoundRect(thumb, 22f, 22f, p)
        }
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = bold; textSize = 44f }
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 255, 255, 255); textSize = 34f }
        val tx = thumb.right + 34
        val tw = W - tx - 90
        c.drawText(TextUtils.ellipsize(q.track, title, tw, TextUtils.TruncateAt.END).toString(), tx, thumb.top + 70, title)
        c.drawText(TextUtils.ellipsize(q.artist, sub, tw, TextUtils.TruncateAt.END).toString(), tx, thumb.top + 120, sub)
        sub.textSize = 26f; sub.color = Color.argb(120, 255, 255, 255)
        val brand = "Sintonia"
        c.drawText(brand, W - 90 - sub.measureText(brand), 120f, sub)
        return bmp
    }

    private fun Int.withMinLightness(): Int {
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(this, hsl)
        hsl[2] = hsl[2].coerceIn(0.55f, 0.75f)
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl)
    }

    /** Gera a imagem e abre o compartilhar do Android. */
    suspend fun share(context: Context, q: Quote) {
        val cover = ShareHelper.loadCover(context, q.imageUrl)
        val bmp = render(q, cover)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "trecho.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.arquivos", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "🎵 ${q.track} — ${q.artist}")
            clipData = ClipData.newRawUri("trecho", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartilhar trecho").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
