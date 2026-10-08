package com.dmwnezes.sintonia.wrapped

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** Cartão final da retrospectiva (1080×1920) para postar nos Stories. */
object WrappedCard {

    fun render(s: Slide.Summary): Bitmap {
        val w = 1080f; val h = 1920f
        val bmp = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w, h, Color.rgb(255, 92, 138), Color.rgb(70, 30, 160), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * 0.85f, h * 0.12f, w * 0.8f, Color.argb(150, 255, 210, 90), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w * 0.1f, h * 0.9f, w, Color.argb(160, 30, 200, 190), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null

        val black = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 900, false) else Typeface.DEFAULT_BOLD
        val medium = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 500, false) else Typeface.DEFAULT
        val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = black }
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 255, 255, 255); typeface = medium }

        sub.textSize = 40f
        c.drawText("Minha retrospectiva", 90f, 210f, sub)
        t.textSize = 150f
        c.drawText(s.period, 84f, 360f, t)

        sub.textSize = 38f
        c.drawText("Minutos ouvidos", 90f, 520f, sub)
        t.textSize = 120f
        c.drawText(String.format(Locale("pt", "BR"), "%,d", s.minutes), 84f, 640f, t)

        fun column(title: String, items: List<String>, x: Float, width: Float) {
            sub.textSize = 38f
            c.drawText(title, x, 800f, sub)
            t.textSize = 46f
            items.forEachIndexed { i, name ->
                val y = 880f + i * 82
                c.drawText("${i + 1}", x, y, t)
                c.drawText(TextUtils.ellipsize(name, t, width - 60, TextUtils.TruncateAt.END).toString(), x + 52, y, t)
            }
        }
        column("Artistas", s.topArtists, 90f, 450f)
        column("Músicas", s.topTracks, 560f, 440f)

        if (s.newArtists > 0) {
            sub.textSize = 38f
            c.drawText("Artistas descobertos", 90f, 1380f, sub)
            t.textSize = 110f
            c.drawText(String.format(Locale("pt", "BR"), "%,d", s.newArtists), 84f, 1495f, t)
        }
        sub.textSize = 32f; sub.color = Color.argb(170, 255, 255, 255)
        c.drawText("Feito com o Sintonia", 90f, h - 140f, sub)
        return bmp
    }

    fun share(context: Context, s: Slide.Summary) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "retrospectiva.png")
        file.outputStream().use { render(s).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.arquivos", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("retrospectiva", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartilhar retrospectiva").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
