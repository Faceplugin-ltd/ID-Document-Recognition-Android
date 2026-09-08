package com.faceplugin.documentreader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class FieldRow(val key: String, val value: String, val source: String)

data class SecurityRow(val page: String, val check: String, val status: String)

data class CheckRow(val name: String, val result: Int, val reason: String = "")

data class Verification(val result: Int, val reason: String, val checks: List<CheckRow>)

/** One image from the DocumentReaderSDK `images` array, grouped by category (`name`). */
data class ResultImage(val category: String, val source: String, val bitmap: Bitmap)

enum class DocumentSide { FRONT, BACK, UNKNOWN }

/**
 * Maps [DocumentReaderSDK] JSON into UI fields.
 *
 * Recognize: `errorCode`, `documentName`, `ocr`, `mrz`, `images`.
 * Locate: `score` (type confidence) and `position.corners`.
 */
object ResultParser {

    private const val LONG_VALUE = 300

    private val skipKeys = setOf(
        "checkSums", "contrastPrint", "docFormat", "mrzFormat",
        "mrzFormatCheckdigit", "mrzStringsWithCorrectCheckSums",
        "numberChecksumValidity", "numberValidity", "overallValidity",
        "symbolMatrix", "images"
    )

    private val verifyOrder = listOf(
        "docType", "expiry", "text", "mrz", "security", "imageQA", "portrait"
    )

    private val qaOrder = listOf(
        "focus", "glares", "resolution", "colorness", "perspective",
        "bounds", "portrait", "handwritten", "brightness", "occlusion"
    )

    fun pretty(raw: String): String {
        if (raw.isBlank()) return "(empty response)"
        return try {
            val trimmed = raw.trim()
            when {
                trimmed.startsWith("{") ->
                    (sanitizeValue(JSONObject(trimmed)) as JSONObject).toString(2)
                trimmed.startsWith("[") ->
                    (sanitizeValue(JSONArray(trimmed)) as JSONArray).toString(2)
                else -> summarizeLong(trimmed)
            }
        } catch (_: Exception) {
            summarizeLong(raw)
        }
    }

    /** Replace string values longer than [LONG_VALUE] with type + size. */
    private fun sanitizeValue(value: Any?): Any? {
        return when (value) {
            is JSONObject -> {
                val out = JSONObject()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    out.put(k, sanitizeValue(value.opt(k)))
                }
                out
            }
            is JSONArray -> {
                val out = JSONArray()
                for (i in 0 until value.length()) {
                    out.put(sanitizeValue(value.opt(i)))
                }
                out
            }
            is String -> if (value.length > LONG_VALUE) summarizeLong(value) else value
            else -> value
        }
    }

    fun summarizeLong(value: String): String {
        val type = when {
            value.startsWith("/9j/") || value.startsWith("data:image/jpeg") -> "jpeg"
            value.startsWith("iVBOR") || value.startsWith("data:image/png") -> "png"
            value.startsWith("R0lGOD") || value.startsWith("data:image/gif") -> "gif"
            value.startsWith("Qk") && value.length > 100 -> "bmp"
            value.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' } -> "base64"
            else -> "string"
        }
        return "$type, ${value.length} chars"
    }

    fun summary(raw: String): String {
        return try {
            val obj = JSONObject(raw.trim())
            if (obj.has("msg")) return obj.optString("msg")
            val err = obj.optInt("errorCode", -1)
            val score = obj.opt("score")
            val scoreS = when (score) {
                is Number -> String.format("%.3f", score.toDouble())
                else -> "—"
            }
            val status = obj.optJSONObject("status")?.opt("overallStatus")
            val verify = verificationFrom(obj)
            buildString {
                append("Status: ").append(if (err == 0) "OK" else "Failed")
                append(" (errorCode=").append(err).append(")\n")
                append("Document: ").append(obj.optString("documentName", "—")).append("\n")
                append("Country: ").append(obj.optString("countryName", "—")).append("\n")
                append("Verification: ").append(verify?.let { overallLabel(it.result) } ?: "—").append("\n")
                append("Score: ").append(scoreS)
                if (status != null) append("  ·  overallStatus=").append(status)
                val lic = obj.optString("licenseError", "")
                if (lic.isNotBlank()) append("\nLicense: ").append(lic)
            }
        } catch (_: Exception) {
            raw.take(200)
        }
    }

    fun rows(raw: String): List<FieldRow> {
        return try {
            val obj = JSONObject(raw.trim())
            val out = mutableListOf<FieldRow>()
            out += FieldRow("documentName", obj.optString("documentName", ""), "meta")
            out += FieldRow("countryName", obj.optString("countryName", ""), "meta")
            out += FieldRow("score", scoreCell(obj.opt("score")), "meta")
            out += FieldRow("errorCode", obj.opt("errorCode")?.toString() ?: "", "meta")
            out += rowsFromVerification(verificationFrom(obj))
            out += rowsFromImageQuality(obj.optJSONObject("imageQuality"))
            out += rowsFromMap(obj.optJSONObject("ocr"), "OCR")
            out += rowsFromMap(obj.optJSONObject("mrz"), "MRZ")
            out += rowsFromMap(obj.optJSONObject("barcode"), "Barcode")
            out.filter { it.value.isNotBlank() && it.value != "null" }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun extractName(obj: JSONObject): String {
        for (k in listOf("name", "surnameAndGivenNames", "fullName", "documentName")) {
            val v = obj.optString(k, "")
            if (v.isNotBlank()) return v
        }
        val ocr = obj.optJSONObject("ocr") ?: return ""
        val surname = ocr.optString("surname", "")
        val given = ocr.optString("givenNames", ocr.optString("givenName", ""))
        return listOf(surname, given).filter { it.isNotBlank() }.joinToString(" ")
    }

    fun decodePortrait(obj: JSONObject): Bitmap? {
        return images(obj.toString())
            .firstOrNull { it.category.contains("portrait", ignoreCase = true) }
            ?.bitmap
            ?: images(obj.toString()).firstOrNull()?.bitmap
    }

    /** Images from response `images[]`, labeled like desktop gallery captions. */
    fun images(raw: String): List<ResultImage> {
        return try {
            val obj = JSONObject(raw.trim())
            val arr = obj.optJSONArray("images") ?: return emptyList()
            val order = mutableListOf<String>()
            val best = mutableMapOf<String, Pair<ResultImage, Int>>()
            val seenPayload = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val item = arr.opt(i) ?: continue
                val b64: String
                val name: String
                val source: String
                val type: Int
                val pageIndex: Int?
                when (item) {
                    is String -> {
                        b64 = item
                        name = "image ${i + 1}"
                        source = ""
                        type = 0
                        pageIndex = null
                    }
                    is JSONObject -> {
                        b64 = item.optString(
                            "image",
                            item.optString("value", item.optString("data", ""))
                        )
                        name = item.optString(
                            "name",
                            item.optString("fieldName", item.optString("role", "image ${i + 1}"))
                        ).ifBlank { "image ${i + 1}" }
                        source = item.optString("source", "")
                        type = item.optInt("type", 0)
                        pageIndex = if (item.has("pageIndex") && !item.isNull("pageIndex")) {
                            item.optInt("pageIndex")
                        } else {
                            null
                        }
                    }
                    else -> continue
                }
                if (b64.isBlank() || b64.length < 32) continue
                val payloadKey = imagePayloadKey(b64)
                if (!seenPayload.add(payloadKey)) continue
                val bmp = decodeBase64Bitmap(b64) ?: continue
                val role = canonicalImageKey(name, type)
                val key = if (pageIndex != null) "$role|$pageIndex" else role
                var score = b64.length
                val src = source.lowercase()
                if (src.contains("visual")) score += 2_000_000
                if (src.contains("graphic")) score += 100_000
                if (src.contains("barcode")) score -= 500_000
                if (key !in best) order += key
                val title = displayName(role, name)
                val caption = if (pageIndex != null) "$title (page $pageIndex)" else title
                val current = best[key]
                if (current == null || score > current.second) {
                    best[key] = ResultImage(caption, source, bmp) to score
                }
            }
            order.mapNotNull { best[it]?.first }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun imagePayloadKey(b64: String): String {
        val n = b64.length
        val head = b64.take(96)
        val tail = b64.takeLast(48)
        return "$n:$head:$tail"
    }

    private fun canonicalImageKey(name: String, type: Int): String {
        when (type) {
            201, 206 -> return "portrait"
            202 -> return "fingerprint"
            203 -> return "eye"
            204 -> return "signature"
            205 -> return "barcode"
        }
        var s = name.trim().lowercase().replace('-', ' ').replace('_', ' ')
        while (s.contains("  ")) s = s.replace("  ", " ")
        if (s.contains("portrait") || s.contains("ghost")) return "portrait"
        if (s.contains("signature")) return "signature"
        if (s.contains("fingerprint")) return "fingerprint"
        if (s.contains("barcode")) return "barcode"
        if (s.contains("back") || s.contains("rear") || s.contains("verso")) return "document-back"
        if (s.contains("document") || s.contains("white page") || s.contains("front") || s.contains("recto")) {
            return "document-front"
        }
        if (type != 0) return "t$type"
        return s.ifBlank { "image" }
    }

    private fun displayName(key: String, original: String): String = when (key) {
        "portrait" -> "Portrait"
        "document-front" -> "Document"
        "document-back" -> "Document back"
        "signature" -> "Signature"
        "fingerprint" -> "Fingerprint"
        "barcode" -> "Barcode"
        "eye" -> "Eye"
        else -> original
    }

    private fun scoreCell(value: Any?): String {
        val n = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        } ?: return if (value == null) "—" else value.toString()
        return String.format("%.3f", n).trimEnd('0').trimEnd('.')
    }

    fun decodeBase64Bitmap(b64: String): Bitmap? {
        return try {
            val clean = b64.substringAfter("base64,", b64)
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * ID-type confidence 0–100 from OneCandidate.P (`score`).
     * Does **not** use DocumentPosition.objArea (that is frame fill %, not
     * “how sure this is a real ID card”).
     */
    fun documentPercent(raw: String): Int {
        return try {
            val obj = JSONObject(raw.trim())
            if (!obj.has("score") || obj.isNull("score")) return 0
            val s = obj.optDouble("score", 0.0)
            (if (s <= 1.0) s * 100.0 else s).toInt().coerceIn(0, 100)
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Front vs back from locate/OCR `documentName` (OneCandidate.DocumentName).
     * Tokens are matched on the template title, not the image bytes.
     */
    fun documentSide(raw: String): DocumentSide {
        return try {
            val name = JSONObject(raw.trim()).optString("documentName", "").lowercase()
            if (name.isEmpty()) return DocumentSide.UNKNOWN
            when {
                name.contains("back") || name.contains("rear") || name.contains("verso")
                    || name.contains("reverse") || name.contains("side b")
                    || name.contains("side 2") -> DocumentSide.BACK
                name.contains("front") || name.contains("recto") || name.contains("obverse")
                    || name.contains("side a") || name.contains("side 1") -> DocumentSide.FRONT
                else -> DocumentSide.UNKNOWN
            }
        } catch (_: Exception) {
            DocumentSide.UNKNOWN
        }
    }

    /** Optional framing metric: how much of the image the document occupies (0–100). */
    fun documentFillPercent(raw: String): Int {
        return try {
            val pos = JSONObject(raw.trim()).optJSONObject("position") ?: return 0
            val area = when {
                pos.has("objArea") && !pos.isNull("objArea") -> pos.optDouble("objArea")
                pos.has("ObjArea") && !pos.isNull("ObjArea") -> pos.optDouble("ObjArea")
                else -> return 0
            }
            if (area.isNaN() || area < 0.0) return 0
            (if (area <= 1.0) area * 100.0 else area).toInt().coerceIn(0, 100)
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Document corners in **bitmap / locate-input** pixel space:
     * LeftTop, RightTop, RightBottom, LeftBottom.
     */
    fun documentCorners(raw: String): List<Pair<Float, Float>>? {
        return try {
            val pos = JSONObject(raw.trim()).optJSONObject("position") ?: return null
            val arr = pos.optJSONArray("corners")
            if (arr != null && arr.length() >= 4) {
                val out = ArrayList<Pair<Float, Float>>(4)
                for (i in 0 until 4) {
                    val p = arr.optJSONObject(i) ?: return null
                    out += p.optDouble("x").toFloat() to p.optDouble("y").toFloat()
                }
                return out
            }
            val l = pos.optDouble("left", Double.NaN)
            val t = pos.optDouble("top", Double.NaN)
            val r = pos.optDouble("right", Double.NaN)
            val b = pos.optDouble("bottom", Double.NaN)
            if (l.isNaN() || t.isNaN() || r.isNaN() || b.isNaN()) return null
            if (r <= l || b <= t) return null
            listOf(
                l.toFloat() to t.toFloat(),
                r.toFloat() to t.toFloat(),
                r.toFloat() to b.toFloat(),
                l.toFloat() to b.toFloat()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun overallLabel(result: Int): String = when (result) {
        1 -> "Verified"
        2 -> "Not checked"
        else -> "Not verified"
    }

    private fun checkLabel(result: Int): String = when (result) {
        1 -> "Pass"
        0 -> "Fail"
        else -> "Not checked"
    }

    private fun verificationFrom(obj: JSONObject): Verification? {
        val ready = obj.optJSONObject("verification")
        if (ready != null && ready.has("checks")) {
            val checks = mutableListOf<CheckRow>()
            val mapped = ready.optJSONObject("checks")
            if (mapped != null) {
                val keys = mapped.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val c = mapped.optJSONObject(id)
                    if (c != null) {
                        checks += CheckRow(id, c.optInt("result", 2), c.optString("reason", ""))
                    } else {
                        checks += CheckRow(id, mapped.optInt(id, 2))
                    }
                }
            } else {
                val arr = ready.optJSONArray("checks")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val c = arr.optJSONObject(i) ?: continue
                        checks += CheckRow(
                            c.optString("id", c.optString("name")),
                            c.optInt("result", 2),
                            c.optString("reason", "")
                        )
                    }
                }
            }
            return Verification(ready.optInt("result", 2), ready.optString("reason", ""), checks)
        }
        val status = obj.optJSONObject("status") ?: return null
        val opt = status.optJSONObject("detailsOptical") ?: JSONObject()
        val overall = status.optInt("overallStatus", opt.optInt("overallStatus", 2))
        return Verification(
            overall,
            "",
            listOf(
                CheckRow("docType", opt.optInt("docType", 2)),
                CheckRow("expiry", opt.optInt("expiry", 2)),
                CheckRow("text", opt.optInt("text", 2)),
                CheckRow("mrz", opt.optInt("mrz", 2)),
                CheckRow("security", opt.optInt("security", 2)),
                CheckRow("imageQA", opt.optInt("imageQA", 2)),
                CheckRow("portrait", status.optInt("portrait", opt.optInt("portrait", 2))),
            )
        )
    }

    private fun rowsFromVerification(data: Verification?): List<FieldRow> {
        if (data == null) return emptyList()
        var overall = overallLabel(data.result)
        if (data.result == 0 && data.reason.isNotBlank())
            overall = "$overall — ${data.reason}"
        val out = mutableListOf(FieldRow("overall", overall, "Verify"))
        val ordered = orderedNamed(data.checks.map { it.name to it }, verifyOrder)
        for ((_, check) in ordered) {
            var value = checkLabel(check.result)
            if (check.result == 0 && check.reason.isNotBlank())
                value = "$value — ${check.reason}"
            out += FieldRow(check.name, value, "Verify")
        }
        return out
    }

    private fun orderedNamed(
        items: List<Pair<String, CheckRow>>,
        order: List<String>
    ): List<Pair<String, CheckRow>> {
        val map = items.associateBy { it.first }
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Pair<String, CheckRow>>()
        for (key in order) {
            val item = map[key] ?: continue
            seen += key
            out += item
        }
        for (item in items) {
            if (item.first in seen || item.first == "result") continue
            out += item
        }
        return out
    }

    private fun rowsFromImageQuality(data: JSONObject?): List<FieldRow> {
        if (data == null) return emptyList()
        val pairs = mutableListOf<Pair<String, String>>()
        val mapped = data.optJSONObject("checks")
        if (mapped != null) {
            val keys = mapped.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                pairs += id to qaCell(mapped.opt(id))
            }
        } else {
            val arr = data.optJSONArray("checks") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                val name = c.optString("id", c.optString("name"))
                val value = c.opt("score") ?: c.opt("result") ?: ""
                pairs += name to qaCell(value)
            }
        }
        val seen = mutableSetOf<String>()
        val out = mutableListOf<FieldRow>()
        for (key in qaOrder) {
            val hit = pairs.firstOrNull { it.first == key } ?: continue
            seen += key
            if (hit.second.isNotBlank() && hit.second != "null") {
                out += FieldRow(hit.first, hit.second, "Image QA")
            }
        }
        for ((name, value) in pairs) {
            if (name in seen || name == "result") continue
            if (value.isNotBlank() && value != "null") {
                out += FieldRow(name, value, "Image QA")
            }
        }
        return out
    }

    private fun qaCell(value: Any?): String {
        val raw = when (value) {
            is JSONObject -> value.opt("score") ?: value.opt("result") ?: value
            else -> value
        }
        val n = when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.toDoubleOrNull()
            else -> null
        } ?: return if (raw == null) "" else raw.toString()
        return String.format("%.3f", n).trimEnd('0').trimEnd('.')
    }

    private fun rowsFromMap(data: JSONObject?, source: String): List<FieldRow> {
        if (data == null) return emptyList()
        val keys = data.keys()
        val out = mutableListOf<FieldRow>()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key in skipKeys) continue
            val value = data.opt(key) ?: continue
            if (value is JSONObject || value is JSONArray) continue
            out += FieldRow(key, value.toString(), source)
        }
        return out
    }

    fun securitySummary(raw: String): String {
        return try {
            val sec = JSONObject(raw.trim()).optJSONObject("security")
                ?: return "No liveness checks in this response. If you expected checks, this license may not include liveness."
            val pages = securityPages(sec)
            val docLabel = sec.optString("label").ifBlank {
                statusCell(sec.opt("overall")).ifBlank { "—" }
            }
            if (pages.isEmpty()) {
                return if (docLabel.isNotBlank() && docLabel != "—") {
                    "Document: $docLabel\nNo per-page checks in this response."
                } else {
                    "No liveness checks in this response."
                }
            }
            buildString {
                append("Document: ").append(docLabel)
                for (page in pages) {
                    val name = pageSide(page.optInt("pageIndex", 0))
                    val pageLabel = page.optString("label").ifBlank {
                        statusCell(page.opt("overall")).ifBlank { "—" }
                    }
                    append("\n").append(name).append(": ").append(pageLabel)
                }
            }
        } catch (_: Exception) {
            "No liveness checks in this response."
        }
    }

    fun securityRows(raw: String): List<SecurityRow> {
        return try {
            val sec = JSONObject(raw.trim()).optJSONObject("security") ?: return emptyList()
            val pages = securityPages(sec)
            if (pages.isEmpty()) {
                val docLabel = sec.optString("label").ifBlank {
                    statusCell(sec.opt("overall")).ifBlank { "" }
                }
                return if (docLabel.isNotBlank()) {
                    listOf(SecurityRow("—", "Overall", docLabel))
                } else {
                    emptyList()
                }
            }
            val out = mutableListOf<SecurityRow>()
            for (page in pages) {
                val name = pageSide(page.optInt("pageIndex", 0))
                val pageLabel = page.optString("label").ifBlank {
                    statusCell(page.opt("overall")).ifBlank { "—" }
                }
                out += SecurityRow(name, "Overall", pageLabel)
                val keys = page.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key in securityPageMeta) continue
                    appendSecurityValue(out, name, key, page.opt(key))
                }
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private val securityPageMeta = setOf("pageIndex", "overall", "label", "pages", "presentation")

    private fun securityPages(sec: JSONObject): List<JSONObject> {
        val arr = sec.optJSONArray("pages")
        if (arr != null && arr.length() > 0) {
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }
        val flat = JSONObject()
        val keys = sec.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in securityPageMeta) flat.put(key, sec.opt(key))
        }
        if (flat.length() == 0) return emptyList()
        if (sec.has("overall")) flat.put("overall", sec.opt("overall"))
        if (sec.has("label")) flat.put("label", sec.opt("label"))
        flat.put("pageIndex", 0)
        return listOf(flat)
    }

    private fun pageSide(idx: Int): String = when (idx) {
        0 -> "Front"
        1 -> "Back"
        else -> "Page $idx"
    }

    private fun humanizeKey(key: String): String {
        val spaced = buildString {
            for (ch in key) {
                if (ch.isUpperCase() && isNotEmpty()) append(' ')
                append(ch)
            }
        }.trim()
        if (spaced.isEmpty()) return key
        return spaced.replaceFirstChar { it.uppercase() }
    }

    private fun checkTitle(key: String, raw: Any?): String {
        if (raw is JSONObject) {
            val title = raw.optString("title")
            if (title.isNotBlank()) return title
        }
        return humanizeKey(key)
    }

    private fun statusKind(value: Any?): String {
        var v = value
        if (v is JSONObject) {
            if (v.has("score") && v.opt("score") !is JSONObject) return "score"
            v = v.opt("result") ?: v.opt("label")
        }
        if (v is Number) return "score"
        val s = v?.toString().orEmpty().trim().lowercase().replace(" ", "").replace("_", "")
        return when (s) {
            "success", "pass", "ok", "authentic", "1" -> "success"
            "notchecked", "wasnotdone", "2" -> "notChecked"
            "fail", "failed", "error", "notauthentic", "0" -> "fail"
            else -> "other"
        }
    }

    private fun formatScore(value: Any?): String {
        val n = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        } ?: return value?.toString().orEmpty()
        val s = String.format("%.1f", n).trimEnd('0').trimEnd('.')
        return "${s.ifEmpty { "0" }}%"
    }

    private fun statusCell(value: Any?): String {
        return when (statusKind(value)) {
            "success" -> "Pass"
            "notChecked" -> "Not checked"
            "fail" -> "Fail"
            "score" -> {
                if (value is JSONObject) formatScore(value.opt("score") ?: value.opt("result"))
                else formatScore(value)
            }
            else -> {
                val v = if (value is JSONObject) value.opt("result") ?: value.opt("label") else value
                v?.toString().orEmpty()
            }
        }
    }

    private fun appendSecurityValue(
        rows: MutableList<SecurityRow>,
        pageName: String,
        key: String,
        raw: Any?,
    ) {
        val title = checkTitle(key, raw)
        if (raw is Number || raw !is JSONObject) {
            rows += SecurityRow(pageName, title, statusCell(raw))
            return
        }
        if (raw.has("score") && raw.opt("score") !is JSONObject) {
            rows += SecurityRow(pageName, title, statusCell(raw))
            return
        }
        val kind = statusKind(raw)
        rows += SecurityRow(pageName, title, statusCell(raw.opt("result")))
        val checks = raw.optJSONObject("checks") ?: return
        val checkKeys = mutableListOf<String>()
        val it = checks.keys()
        while (it.hasNext()) checkKeys += it.next()
        if (kind != "fail" && checkKeys.none { statusKind(checks.opt(it)) == "fail" }) return
        for (ck in checkKeys) {
            val cv = checks.opt(ck)
            if (statusKind(cv) == "notChecked" && kind != "fail") continue
            rows += SecurityRow(pageName, "  → ${checkTitle(ck, cv)}", statusCell(cv))
        }
    }
}
