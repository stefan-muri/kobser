package com.kobser.app.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

/**
 * Loads media artwork via Coil so Android Auto and the media notification use the
 * same image pipeline (and the same authenticated cover-art URLs) that already
 * work in-app. Media3's default loader sometimes fails to fetch these, leaving
 * Android Auto without thumbnails.
 */
@UnstableApi
class CoilBitmapLoader(private val context: Context) : BitmapLoader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val imageLoader = ImageLoader(context)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = scope.future {
        BitmapFactory.decodeByteArray(data, 0, data.size)
            ?: throw IllegalArgumentException("Could not decode bitmap")
    }

    override fun loadBitmap(uri: Uri, options: BitmapFactory.Options?): ListenableFuture<Bitmap> = scope.future {
        val request = ImageRequest.Builder(context)
            .data(uri.toString())
            .allowHardware(false)
            .build()
        val result = imageLoader.execute(request)
        (result as? SuccessResult)?.drawable?.toBitmap()
            ?: throw IllegalStateException("Failed to load bitmap from $uri")
    }
}
