package com.faceplugin.documentreader

import android.content.Context
import android.graphics.Bitmap
import com.faceplugin.documentreadersdk.DocumentReaderSDK

/**
 * Session helper for still OCR / MRZ / barcode.
 *
 * Camera calls [startGallery] immediately before [DocumentReaderSDK.recognize].
 * Gallery uses [recognize] (front + optional back in one ProcessImage).
 */
object DocSdkSession {
    fun startGallery() = DocumentReaderSDK.startNewSession(
        "{\"scenario\":\"FullProcess\",\"series\":false}"
    )

    /**
     * Recognize with authenticity when the license allows it.
     * @return Pair(json, optional user-facing deny message shown before/after the call)
     */
    fun recognize(context: Context, front: Bitmap, back: Bitmap? = null): Pair<String, String?> {
        val status = LicenseStatus.current()
        val wantAuth = true
        val deny = status.denyMessage(wantRecognition = true, wantAuthenticity = wantAuth)
        val mode = if (wantAuth && status.authenticity) "normal" else "none"
        return try {
            DocumentReaderSDK.recognize(front, back, mode) to deny
        } catch (e: Exception) {
            "{\"msg\":\"${e.message}\"}" to deny
        }
    }
}
