package com.faceplugin.documentreader

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.faceplugin.documentreadersdk.DocumentReaderSDK

/**
 * Sample host for [com.faceplugin.documentreadersdk.DocumentReaderSDK].
 *
 * Init (background thread): [DocumentReaderSDK.getMachineCode] → [DocumentReaderSDK.setActivation] → [DocumentReaderSDK.init].
 * Replace [LICENSE_KEY] with an `FP1.…` key issued for **your** `applicationId`.
 *
 * Home tiles: Camera | Gallery | About
 */
class MainActivity : AppCompatActivity() {

    companion object {
        /** `FP1.…` from FacePlugin for this app's `applicationId`. */
        private const val LICENSE_KEY =
            "FP1.RlBMMQMAAQBqeoZzR7nWysw9CnwbAgAAZXy0SxBsbbNapp8RqrQ1suumyJ34/izXc5/D6jIsrMZdhiykNFPxoigRtaT5jg2JOsqtY9eEhiwksRHZ0vWWiHRj9IlE5xOqJxra7Zo+g0QOBkmtX4he6WVfFax8m2g87alvb674nBbYYkw0flYJn4kg6AJHKdHUVa7zb6rhqHnR/QpxSz4ksNE8L93e9vQuksZjeE/6xz95Z3jPuZk4APt47iSD6eiAcswth5IO3rEEKEz0UrrTUuHuTjxgpNsYWBAkqZBhGeXg0WxZhm002aS5iFwvldmlmfkXf+ffd8M0OVoPlMzUwyaNNejLjcb7zWlFzSDM/JR8jSK6K3wtA4K0JUq0u1Z0TWBAZIXZUFVRtoGW5Y3+smHwdx4nWwVebm+rEfgSV21TxVA8sG47nVccYqXQ3L0mlNB4od9Wlr825HGanzsSbUKc1nqErs/6N4Uc9XM2vHWbOH9vjHBqcMeDA5fxGqKERGBd1TqrRHxoJRn9gWedlwWg3H2kSFK/Xied2z8ln0Fl6tNv53ZGszT3dDrou29JprjBiZIdBpFL5HJqTCxMAp+pT0IRv6akJNazpTzHRJ6u9Cmbw//N4zDoGdpWbYZB1485LfQz7YHHZc1vlkcQ5vfpAgLg2rx5ubZMJPPI4ZwxLL2gPVE0csUyM7XHFLxvRzQF4o0sMhFuuDBhzDyf+XyHk5gN/gsqb3XlNn4BnPkMUrqLADCBiAJCAWWeMlxk4h7ucchMgn1AG1sSshKOFL67UBMwfrmtDjZEE9pOUEgpskKr6yqNb4IHIHONMJwkjpLf8MxLhfli+7dmAkIAy7OjXrpXhGcNOmJYmilYpy6G2y7rpPus9hxedqlGaK5gx7FeCTGUGCUrf33s/9rCmcNXA8h2utx+btSAMlClwMg="
    }

    private lateinit var txtStatusNotification: TextView
    private var loadingDialog: AlertDialog? = null
    private var sdkReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        txtStatusNotification = findViewById(R.id.txtStatusNotification)

        findViewById<ImageView>(R.id.imgHomeLogo).setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.company_website_url)))
            )
        }

        findViewById<View>(R.id.cardCamera).setOnClickListener {
            if (!ensureReady()) return@setOnClickListener
            startActivity(Intent(this, CameraActivity::class.java))
        }
        findViewById<View>(R.id.cardGallery).setOnClickListener {
            if (!ensureReady()) return@setOnClickListener
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        findViewById<View>(R.id.cardAbout).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        updateStatus(getString(R.string.sdk_loading), R.color.status_info)

        Thread {
            try {
                // License is bound to applicationId. Init loads the engine — keep off the UI thread.
                val machine = DocumentReaderSDK.getMachineCode(this) ?: ""
                val act = DocumentReaderSDK.setActivation(this, LICENSE_KEY)
                val init = if (act == DocumentReaderSDK.SDK_SUCCESS) DocumentReaderSDK.init(this) else act
                sdkReady = init == DocumentReaderSDK.SDK_SUCCESS
                android.util.Log.i("DocumentReaderSDKDemo", "machine=$machine init=$init ready=$sdkReady")
                runOnUiThread {
                    if (sdkReady) {
                        val label = try {
                            LicenseStatus.current().label
                        } catch (_: Throwable) {
                            ""
                        }
                        if (label.isNotBlank()) {
                            updateStatus(
                                getString(R.string.sdk_ready_with_license, label),
                                R.color.status_ok,
                            )
                        } else {
                            updateStatus(getString(R.string.sdk_ready), R.color.status_ok)
                        }
                    } else {
                        val msg = when (init) {
                            1 -> getString(R.string.sdk_license_invalid)
                            2 -> getString(R.string.sdk_license_expired)
                            3 -> getString(R.string.sdk_not_activated)
                            4 -> getString(R.string.sdk_init_failed)
                            5 -> getString(R.string.sdk_no_database)
                            6 -> getString(R.string.sdk_database_load_error)
                            else -> getString(R.string.sdk_failed) + ": " + init
                        }
                        updateStatus(msg, R.color.status_error)
                    }
                    maybeSelfTest()
                }
            } catch (t: Throwable) {
                android.util.Log.e("DocumentReaderSDKDemo", "init", t)
                runOnUiThread {
                    updateStatus(getString(R.string.sdk_failed) + ": " + (t.message ?: "Init failed"), R.color.status_error)
                }
            }
        }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        maybeSelfTest()
    }

    private fun updateStatus(message: String, colorResId: Int) {
        txtStatusNotification.text = message
        val color = ContextCompat.getColor(this, colorResId)
        txtStatusNotification.background?.setTint(color)
    }

    private fun ensureReady(): Boolean {
        if (sdkReady) return true
        Toast.makeText(this, R.string.sdk_failed, Toast.LENGTH_SHORT).show()
        return false
    }

    /** Hidden: adb … --es process_path /sdcard/id.jpg
     *  Two-page: --es process_front … --es process_back …
     *  Locate only: --es locate_path /sdcard/id.jpg */
    private fun maybeSelfTest() {
        if (!sdkReady) return
        val quiet = intent?.getStringExtra("selftest_quiet") == "1"
        val locatePath = intent?.getStringExtra("locate_path")
        if (locatePath != null) {
            intent?.removeExtra("locate_path")
            val bmp = BitmapFactory.decodeFile(locatePath)
            if (bmp == null) {
                android.util.Log.e("DocumentReaderSDKDemo", "locate could not decode $locatePath")
                return
            }
            Thread {
                val json = try {
                    DocumentReaderSDK.locateDocument(bmp)
                } catch (t: Throwable) {
                    "{\"msg\":\"${t.message}\"}"
                }
                val pct = ResultParser.documentPercent(json)
                val side = ResultParser.documentSide(json)
                val name = try {
                    org.json.JSONObject(json.trim()).optString("documentName", "")
                } catch (_: Exception) {
                    ""
                }
                val corners = ResultParser.documentCorners(json) != null
                android.util.Log.i(
                    "DocumentReaderSDKDemo",
                    "locate path=$locatePath score=$pct side=$side name=$name corners=$corners head=${json.take(280)}"
                )
            }.start()
            return
        }
        val frontPath = intent?.getStringExtra("process_front")
            ?: intent?.getStringExtra("process_path")
            ?: return
        val backPath = intent?.getStringExtra("process_back")
        intent?.removeExtra("process_path")
        intent?.removeExtra("process_front")
        intent?.removeExtra("process_back")
        val front = BitmapFactory.decodeFile(frontPath)
        if (front == null) {
            android.util.Log.e("DocumentReaderSDKDemo", "self-test could not decode $frontPath")
            return
        }
        val back = backPath?.let { BitmapFactory.decodeFile(it) }
        if (!quiet) showDialog("Processing image")
        Thread {
            val (ocr, deny) = try {
                DocSdkSession.recognize(this, front, back)
            } catch (t: Throwable) {
                "{\"msg\":\"${t.message}\"}" to null
            }
            val name = try {
                org.json.JSONObject(ocr.trim()).optString("documentName", "")
            } catch (_: Exception) {
                ""
            }
            android.util.Log.i(
                "DocumentReaderSDKDemo",
                "recognize path=$frontPath back=${backPath ?: "-"} len=${ocr.length} " +
                    "ocr=${ocr.contains("\"ocr\"")} mrz=${ocr.contains("\"mrz\"")} " +
                    "barcode=${ocr.contains("\"barcode\"")} name=$name deny=${deny ?: "-"} head=${ocr.take(240)}"
            )
            runOnUiThread {
                if (!quiet) {
                    dismissDialog()
                    if (!deny.isNullOrBlank()) {
                        Toast.makeText(this, deny, Toast.LENGTH_LONG).show()
                    }
                    ResultActivity.open(this, ocr)
                }
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
        if (isFinishing) {
            try {
                // Unload native engine when this activity is actually finishing.
                DocumentReaderSDK.deinit()
            } catch (_: Throwable) {
            }
        }
    }
}
