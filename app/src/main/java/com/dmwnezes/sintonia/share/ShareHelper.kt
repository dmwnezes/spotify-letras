package com.dmwnezes.sintonia.share

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

object ShareHelper {

    suspend fun loadCover(context: Context, url: String?): Bitmap? {
        if (url == null) return null
        val req = ImageRequest.Builder(context).data(url).allowHardware(false).size(1080).build()
        val result = ImageLoader(context).execute(req) as? SuccessResult ?: return null
        return result.drawable.toBitmap()
    }
}
