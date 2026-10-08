package com.dmwnezes.sintonia.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.dmwnezes.sintonia.data.Track
import java.io.File

object ShareHelper {

    suspend fun loadCover(context: Context, url: String?): Bitmap? {
        if (url == null) return null
        val req = ImageRequest.Builder(context).data(url).allowHardware(false).size(1080).build()
        val result = ImageLoader(context).execute(req) as? SuccessResult ?: return null
        return result.drawable.toBitmap()
    }

    fun outputFile(context: Context, track: Track): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // guarda só o último vídeo
        val safe = track.name.replace(Regex("[^A-Za-z0-9À-ÿ]+"), "-").trim('-').take(40).ifBlank { "musica" }
        return File(dir, "sintonia-$safe.mp4")
    }

    fun shareVideo(context: Context, file: File, track: Track) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.arquivos", file)
        val link = if (track.id.isNotBlank()) "https://open.spotify.com/track/${track.id}" else ""
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "🎵 ${track.name} — ${track.artistLine}\n$link".trim())
            clipData = ClipData.newRawUri("vídeo", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartilhar vídeo").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
