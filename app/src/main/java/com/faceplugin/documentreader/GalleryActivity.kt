package com.faceplugin.documentreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gallery: Front required, Back optional — same shape as Windows/Linux Gradio.
 */
class GalleryActivity : AppCompatActivity() {

    private lateinit var imgFront: ImageView
    private lateinit var imgBack: ImageView
    private lateinit var txtFrontPlaceholder: TextView
    private lateinit var txtBackPlaceholder: TextView
    private lateinit var btnClearFront: View
    private lateinit var btnClearBack: View
    private lateinit var btnRecognize: Button

    private var frontBmp: Bitmap? = null
    private var backBmp: Bitmap? = null
    private var pickingBack = false
    private var loadingDialog: AlertDialog? = null
    private val processing = AtomicBoolean(false)

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        bindPicked(uri, pickingBack)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        imgFront = findViewById(R.id.imgFront)
        imgBack = findViewById(R.id.imgBack)
        txtFrontPlaceholder = findViewById(R.id.txtFrontPlaceholder)
        txtBackPlaceholder = findViewById(R.id.txtBackPlaceholder)
        btnClearFront = findViewById(R.id.btnClearFront)
        btnClearBack = findViewById(R.id.btnClearBack)
        btnRecognize = findViewById(R.id.btnRecognize)

        savedInstanceState?.let { state ->
            pickingBack = state.getBoolean(STATE_PICKING_BACK, false)
        }
        loadCached(back = false)
        loadCached(back = true)

        findViewById<View>(R.id.cardFront).setOnClickListener { pick(back = false) }
        findViewById<View>(R.id.cardBack).setOnClickListener { pick(back = true) }
        btnClearFront.setOnClickListener { clear(back = false) }
        btnClearBack.setOnClickListener { clear(back = true) }
        btnRecognize.setOnClickListener { recognize() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PICKING_BACK, pickingBack)
    }

    private fun pick(back: Boolean) {
        pickingBack = back
        pickImage.launch("image/*")
    }

    private fun cacheFile(back: Boolean): File {
        return File(cacheDir, if (back) CACHE_BACK else CACHE_FRONT)
    }

    private fun loadCached(back: Boolean) {
        val file = cacheFile(back)
        if (!file.isFile || file.length() == 0L) return
        val bmp = decodeFile(file) ?: return
        applyBitmap(bmp, back)
    }

    private fun bindPicked(uri: Uri, back: Boolean) {
        val dest = cacheFile(back)
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                Toast.makeText(this, "Could not open image", Toast.LENGTH_LONG).show()
                return
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open image", Toast.LENGTH_LONG).show()
            return
        }
        val bmp = decodeFile(dest)
        if (bmp == null) {
            Toast.makeText(this, "Could not load image", Toast.LENGTH_LONG).show()
            return
        }
        applyBitmap(bmp, back)
    }

    private fun decodeFile(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        val h = bounds.outHeight
        val w = bounds.outWidth
        if (h > 1080 || w > 1920) {
            val halfH = h / 2
            val halfW = w / 2
            while (halfH / sample > 1080 && halfW / sample > 1920) {
                sample *= 2
            }
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun applyBitmap(bmp: Bitmap, back: Boolean) {
        if (back) {
            backBmp?.recycle()
            backBmp = bmp
            imgBack.setImageBitmap(bmp)
            txtBackPlaceholder.visibility = View.GONE
            btnClearBack.visibility = View.VISIBLE
        } else {
            frontBmp?.recycle()
            frontBmp = bmp
            imgFront.setImageBitmap(bmp)
            txtFrontPlaceholder.visibility = View.GONE
            btnClearFront.visibility = View.VISIBLE
        }
        btnRecognize.isEnabled = frontBmp != null && !processing.get()
    }

    private fun clear(back: Boolean) {
        if (processing.get()) return
        if (back) {
            imgBack.setImageDrawable(null)
            backBmp?.recycle()
            backBmp = null
            cacheFile(back = true).delete()
            txtBackPlaceholder.visibility = View.VISIBLE
            btnClearBack.visibility = View.GONE
        } else {
            imgFront.setImageDrawable(null)
            frontBmp?.recycle()
            frontBmp = null
            cacheFile(back = false).delete()
            txtFrontPlaceholder.visibility = View.VISIBLE
            btnClearFront.visibility = View.GONE
        }
        btnRecognize.isEnabled = frontBmp != null
    }

    private fun recognize() {
        val front = frontBmp
        if (front == null) {
            Toast.makeText(this, R.string.gallery_front_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (!processing.compareAndSet(false, true)) return
        btnRecognize.isEnabled = false
        val back = backBmp
        showDialog(if (back != null) "Processing front and back" else "Processing image")
        Thread {
            val (json, deny) = try {
                DocSdkSession.recognize(this, front, back)
            } catch (t: Throwable) {
                "{\"msg\":\"${t.message?.replace("\"", "'")}\"}" to null
            }
            runOnUiThread {
                processing.set(false)
                btnRecognize.isEnabled = frontBmp != null
                dismissDialog()
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!deny.isNullOrBlank()) {
                    Toast.makeText(this, deny, Toast.LENGTH_LONG).show()
                }
                ResultActivity.open(this, json)
            }
        }.start()
    }

    private fun showDialog(msg: String) {
        if (loadingDialog?.isShowing == true) return
        loadingDialog = AlertDialog.Builder(this)
            .setMessage(msg)
            .setCancelable(false)
            .show()
    }

    private fun dismissDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !processing.get()) {
            frontBmp?.recycle()
            backBmp?.recycle()
        }
    }

    companion object {
        private const val STATE_PICKING_BACK = "picking_back"
        private const val CACHE_FRONT = "gallery_front.bin"
        private const val CACHE_BACK = "gallery_back.bin"
    }
}
