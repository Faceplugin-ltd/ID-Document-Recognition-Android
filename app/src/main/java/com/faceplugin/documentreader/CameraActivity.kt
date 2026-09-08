package com.faceplugin.documentreader

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.util.Size
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.faceplugin.documentreadersdk.DocumentReaderSDK
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Live preview using [DocumentReaderSDK.locateDocument] (bounds + type `score`, no OCR).
 * User taps Capture when ready; then [DocSdkSession.startGallery] + [DocumentReaderSDK.recognize].
 */
class CameraActivity : AppCompatActivity() {

    companion object {
        /** Draw overlay when locate `score` ≥ this (percent). */
        private const val SHOW_THRESHOLD = 50
        /** Strong lock / still refresh when locate `score` ≥ this. */
        private const val HIGH_THRESHOLD = 85
        /** Capture enabled at this locate `score`. */
        private const val KEEP_CAPTURE_MIN = 50
        /** Max edge for locate bitmaps (keeps overlay latency down). */
        private const val LOCATE_MAX_EDGE = 480
    }

    private lateinit var previewView: PreviewView
    private lateinit var guideView: DocumentGuideView
    private lateinit var txtPercent: TextView
    private lateinit var txtHint: TextView
    private lateinit var btnCapture: Button
    private lateinit var cameraExecutor: ExecutorService

    private val locating = AtomicBoolean(false)
    private val captured = AtomicBoolean(false)
    private var lastStillUpdateMs = 0L
    private var latestStill: Bitmap? = null
    private val stillLock = Any()
    private var cameraProvider: ProcessCameraProvider? = null

    private val permission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        previewView = findViewById(R.id.previewView)
        previewView.implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        guideView = findViewById(R.id.guideView)
        txtPercent = findViewById(R.id.txtPercent)
        txtHint = findViewById(R.id.txtCameraHint)
        btnCapture = findViewById(R.id.btnCapture)
        btnCapture.visibility = View.VISIBLE
        btnCapture.isEnabled = false
        btnCapture.setOnClickListener { onCaptureClicked() }
        findViewById<ImageButton>(R.id.btnCloseCamera).setOnClickListener { finish() }
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            cameraProvider = provider
            
            // Preview at standard high res
            val previewSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build()
            val preview = Preview.Builder()
                .setResolutionSelector(previewSelector)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // Analysis at lower resolution for speed
            val analysisSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(analysisSelector)
                .build()
            analysis.setAnalyzer(cameraExecutor, this::analyzeFrame)
            
            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onCaptureClicked() {
        if (captured.get()) return
        val still = synchronized(stillLock) { latestStill }
        if (still == null) {
            Toast.makeText(this, R.string.camera_hint, Toast.LENGTH_SHORT).show()
            return
        }
        beginRecognize(still)
    }

    private fun analyzeFrame(image: ImageProxy) {
        if (captured.get()) {
            image.close()
            return
        }
        if (!locating.compareAndSet(false, true)) {
            image.close()
            return
        }

        val rotation = image.imageInfo.rotationDegrees
        val frame = try {
            // Built-in toBitmap is efficient enough if resolution is not too high
            image.toBitmap()
        } catch (_: Throwable) {
            null
        } finally {
            image.close()
        }

        if (frame == null) {
            locating.set(false)
            return
        }

        var frameUsed = false
        var locateBmp: Bitmap? = null
        try {
            // Use smaller max edge for faster location
            locateBmp = scaleMax(frame, LOCATE_MAX_EDGE)
            val locateJson = try {
                // DocType only — JSON score + position.corners. Scale first for latency.
                DocumentReaderSDK.locateDocument(locateBmp)
            } catch (_: Throwable) {
                "{\"msg\":\"locate failed\"}"
            }
            val scorePct = ResultParser.documentPercent(locateJson)
            val cornersLocate = ResultParser.documentCorners(locateJson)
            val showOverlay = scorePct >= SHOW_THRESHOLD && cornersLocate != null
            val high = scorePct >= HIGH_THRESHOLD
            val now = System.currentTimeMillis()
            if (scorePct >= KEEP_CAPTURE_MIN && (high || now - lastStillUpdateMs > 500)) {
                val still = rotateToUpright(frame, rotation)
                synchronized(stillLock) {
                    if (!captured.get()) {
                        if (latestStill !== still) latestStill?.recycle()
                        latestStill = still
                    } else {
                        if (still !== frame) still.recycle()
                    }
                }
                if (still === frame) frameUsed = true
                lastStillUpdateMs = now
            }

            val cornersFrame = if (showOverlay && cornersLocate != null) {
                val sx = frame.width.toFloat() / locateBmp.width.toFloat().coerceAtLeast(1f)
                val sy = frame.height.toFloat() / locateBmp.height.toFloat().coerceAtLeast(1f)
                cornersLocate.map { (x, y) -> x * sx to y * sy }
            } else {
                null
            }
            val frameW = frame.width
            val frameH = frame.height

            runOnUiThread {
                if (captured.get()) return@runOnUiThread
                txtPercent.text = "$scorePct%"
                val viewCorners = if (cornersFrame != null) {
                    mapFrameCornersToView(cornersFrame, frameW, frameH, rotation)
                } else {
                    null
                }
                if (viewCorners != null) guideView.setDetectedCorners(viewCorners)
                else guideView.clearDetection()
                guideView.locked = high
                btnCapture.visibility = View.VISIBLE
                btnCapture.isEnabled = scorePct >= KEEP_CAPTURE_MIN
                txtHint.setText(if (scorePct >= KEEP_CAPTURE_MIN) R.string.camera_ready else R.string.camera_hint)
            }
        } finally {
            val scaled = locateBmp
            if (scaled != null && scaled !== frame && !scaled.isRecycled) scaled.recycle()
            if (!frameUsed && !frame.isRecycled) frame.recycle()
            locating.set(false)
        }
    }

    private fun beginRecognize(still: Bitmap) {
        if (!captured.compareAndSet(false, true)) return
        runOnUiThread {
            btnCapture.visibility = View.GONE
            txtHint.setText(R.string.camera_capturing)
            // Unbind to freeze the preview in the background
            cameraProvider?.unbindAll()
        }
        cameraExecutor.execute {
            val (json, deny) = try {
                // locateDocument left a DocType session; stills need a new FullProcess session.
                DocSdkSession.startGallery()
                DocSdkSession.recognize(this, still, null)
            } catch (t: Throwable) {
                "{\"msg\":\"${t.message}\"}" to null
            }
            runOnUiThread {
                if (!deny.isNullOrBlank()) {
                    Toast.makeText(this, deny, Toast.LENGTH_LONG).show()
                }
                ResultActivity.open(this, json)
                finish()
            }
        }
    }

    private fun mapFrameCornersToView(
        corners: List<Pair<Float, Float>>,
        frameW: Int,
        frameH: Int,
        rotation: Int
    ): List<PointF>? {
        if (corners.size < 4 || frameW <= 0 || frameH <= 0) return null
        val viewW = previewView.width
        val viewH = previewView.height
        if (viewW <= 0 || viewH <= 0) return null

        val rot = ((rotation % 360) + 360) % 360
        val (dispW, dispH) = if (rot == 90 || rot == 270) frameH to frameW else frameW to frameH
        val scale = max(viewW.toFloat() / dispW, viewH.toFloat() / dispH)
        val dx = (viewW - dispW * scale) / 2f
        val dy = (viewH - dispH * scale) / 2f

        return List(4) { i ->
            val (x, y) = rotateBufferPoint(corners[i].first, corners[i].second, frameW, frameH, rot)
            PointF(x * scale + dx, (dispH - y) * scale + dy)
        }
    }

    private fun rotateBufferPoint(
        x: Float,
        y: Float,
        w: Int,
        h: Int,
        rotation: Int
    ): Pair<Float, Float> {
        return when (rotation) {
            90 -> y to (w - x)
            180 -> (w - x) to (h - y)
            270 -> (h - y) to x
            else -> x to y
        }
    }

    private fun rotateToUpright(src: Bitmap, rotation: Int): Bitmap {
        val rot = ((rotation % 360) + 360) % 360
        if (rot == 0) return src
        val m = Matrix().apply { postRotate(rot.toFloat()) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        return out
    }

    private fun scaleMax(src: Bitmap, maxEdge: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxEdge) return src
        val scale = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).toInt().coerceAtLeast(1),
            (src.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        synchronized(stillLock) {
            latestStill?.recycle()
            latestStill = null
        }
    }
}
