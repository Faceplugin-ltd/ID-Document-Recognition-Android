package com.faceplugin.documentreader

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.FileNotFoundException
import java.io.InputStream

/** Bitmap helpers for the gallery path. */
object Utils {

    fun getBitmap(
        resolver: ContentResolver,
        selectedImage: Uri?,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        if (selectedImage == null) return null
        var input: InputStream? = null
        try {
            input = resolver.openInputStream(selectedImage)
        } catch (_: FileNotFoundException) {
            return null
        }
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(input, null, options)
        try {
            input?.close()
        } catch (_: Exception) {
        }

        try {
            input = resolver.openInputStream(selectedImage)
        } catch (_: FileNotFoundException) {
            return null
        }
        options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        options.inJustDecodeBounds = false
        val bmp = BitmapFactory.decodeStream(input, null, options)
        try {
            input?.close()
        } catch (_: Exception) {
        }
        return bmp
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        bitmapWidth: Int,
        bitmapHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > bitmapHeight || width > bitmapWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize > bitmapHeight &&
                halfWidth / inSampleSize > bitmapWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
