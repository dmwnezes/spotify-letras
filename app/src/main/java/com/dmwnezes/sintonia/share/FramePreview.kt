package com.dmwnezes.sintonia.share

import android.graphics.Bitmap
import android.graphics.Canvas

/** Desenha um único quadro do vídeo num Bitmap (usado em pré-visualização e testes). */
object FramePreview {
    fun frame(spec: VideoSpec, tMs: Long): Bitmap {
        val bmp = Bitmap.createBitmap(spec.format.width, spec.format.height, Bitmap.Config.ARGB_8888)
        FrameDrawer(spec).draw(Canvas(bmp), tMs)
        return bmp
    }
}
