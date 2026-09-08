package com.faceplugin.documentreader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import java.io.File

/**
 * Shows [DocumentReaderSDK.recognize] JSON: document name, OCR/MRZ/Barcode fields, and `images`.
 */
class ResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_JSON = "result_json"
        const val EXTRA_JSON_FILE = "result_json_file"

        fun open(context: Context, json: String) {
            val file = File(context.cacheDir, "last_result.json")
            file.writeText(json)
            context.startActivity(
                Intent(context, ResultActivity::class.java)
                    .putExtra(EXTRA_JSON_FILE, file.absolutePath)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val json = loadJson()

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = getString(R.string.result_title)
        toolbar.setNavigationOnClickListener { finish() }

        findViewById<TextView>(R.id.txtSummary).text = ResultParser.summary(json)
        findViewById<TextView>(R.id.txtResult).text = ResultParser.pretty(json)

        val container = findViewById<LinearLayout>(R.id.fieldsContainer)
        val inflater = LayoutInflater.from(this)
        for (row in ResultParser.rows(json)) {
            val view = inflater.inflate(R.layout.item_field_row, container, false)
            view.findViewById<TextView>(R.id.txtFieldKey).text = row.key
            view.findViewById<TextView>(R.id.txtFieldValue).text = row.value
            view.findViewById<TextView>(R.id.txtFieldSource).text = row.source
            container.addView(view)
        }

        populateImages(findViewById(R.id.imagesContainer), ResultParser.images(json))

        findViewById<TextView>(R.id.txtSecuritySummary).text = ResultParser.securitySummary(json)
        val securityContainer = findViewById<LinearLayout>(R.id.securityContainer)
        val secRows = ResultParser.securityRows(json)
        if (secRows.isEmpty()) {
            securityContainer.addView(TextView(this).apply {
                text = getString(R.string.security_empty)
                setTextColor(getColor(R.color.fp_muted))
                textSize = 14f
            })
        } else {
            for (row in secRows) {
                val view = inflater.inflate(R.layout.item_security_row, securityContainer, false)
                view.findViewById<TextView>(R.id.txtSecPage).text = row.page
                view.findViewById<TextView>(R.id.txtSecCheck).text = row.check
                view.findViewById<TextView>(R.id.txtSecStatus).text = row.status
                securityContainer.addView(view)
            }
        }

        val scrollResult = findViewById<ScrollView>(R.id.scrollResult)
        val scrollSecurity = findViewById<ScrollView>(R.id.scrollSecurity)
        val scrollImages = findViewById<ScrollView>(R.id.scrollImages)
        val scrollRaw = findViewById<ScrollView>(R.id.scrollRaw)
        val tabs = findViewById<TabLayout>(R.id.tabLayout)
        tabs.addTab(tabs.newTab().setText(R.string.tab_result))
        tabs.addTab(tabs.newTab().setText(R.string.tab_security))
        tabs.addTab(tabs.newTab().setText(R.string.tab_images))
        tabs.addTab(tabs.newTab().setText(R.string.tab_raw))
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                scrollResult.visibility = if (tab.position == 0) View.VISIBLE else View.GONE
                scrollSecurity.visibility = if (tab.position == 1) View.VISIBLE else View.GONE
                scrollImages.visibility = if (tab.position == 2) View.VISIBLE else View.GONE
                scrollRaw.visibility = if (tab.position == 3) View.VISIBLE else View.GONE
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun loadJson(): String {
        val path = intent.getStringExtra(EXTRA_JSON_FILE)
        if (!path.isNullOrEmpty()) {
            val file = File(path)
            if (file.isFile) return file.readText()
        }
        return intent.getStringExtra(EXTRA_JSON) ?: ""
    }

    private fun populateImages(container: LinearLayout, images: List<ResultImage>) {
        container.removeAllViews()
        if (images.isEmpty()) {
            container.addView(TextView(this).apply {
                text = getString(R.string.images_empty)
                setTextColor(getColor(R.color.fp_muted))
                textSize = 14f
            })
            return
        }
        val pad = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 12f, resources.displayMetrics
        ).toInt()
        val grouped = images.groupBy { it.category.ifBlank { "Image" } }
        for ((category, items) in grouped) {
            for (item in items) {
                container.addView(TextView(this).apply {
                    text = category
                    setTextColor(getColor(R.color.fp_accent))
                    textSize = 15f
                    setPadding(0, pad, 0, pad / 2)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                container.addView(ImageView(this).apply {
                    adjustViewBounds = true
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageBitmap(item.bitmap)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = pad }
                })
            }
        }
    }
}
