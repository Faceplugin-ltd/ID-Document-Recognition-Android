package com.faceplugin.documentreader

import com.faceplugin.documentreadersdk.DocumentReaderSDK
import org.json.JSONObject

/** Parsed [DocumentReaderSDK.getLicenseStatus] for UI and capability checks. */
data class LicenseStatus(
    val licensed: Boolean,
    val level: Int,
    val levelName: String,
    val recognition: Boolean,
    val authenticity: Boolean,
    val label: String,
) {
    companion object {
        fun current(): LicenseStatus {
            return try {
                fromJson(DocumentReaderSDK.getLicenseStatus())
            } catch (_: Throwable) {
                notLicensed()
            }
        }

        fun fromJson(json: String?): LicenseStatus {
            return try {
                val o = JSONObject(json ?: "{}")
                LicenseStatus(
                    licensed = o.optBoolean("licensed", false),
                    level = o.optInt("level", -1),
                    levelName = o.optString("levelName", "None"),
                    recognition = o.optBoolean("recognition", false),
                    authenticity = o.optBoolean("authenticity", false),
                    label = o.optString("label", "Not licensed"),
                )
            } catch (_: Exception) {
                notLicensed()
            }
        }

        private fun notLicensed() = LicenseStatus(
            licensed = false,
            level = -1,
            levelName = "None",
            recognition = false,
            authenticity = false,
            label = "Not licensed",
        )
    }

    /** User-facing note when a requested capability is missing; null if allowed. */
    fun denyMessage(wantRecognition: Boolean, wantAuthenticity: Boolean): String? {
        val parts = mutableListOf<String>()
        if (wantAuthenticity && !authenticity) {
            parts += "Liveness is not available on this license ($label)."
        }
        if (wantRecognition && !recognition) {
            parts += "Recognition is not available on this license ($label)."
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}
