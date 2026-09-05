package com.sxtools

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var urlInput: EditText
    private lateinit var outputArea: EditText
    private lateinit var fetchBtn: Button
    private lateinit var copyBtn: Button
    private lateinit var toastView: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cache = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Root layout
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0b0e14"))
            setPadding(40, 40, 40, 40)
            gravity = Gravity.CENTER
        }

        // Container
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#151e28"))
            setPadding(48, 48, 48, 48)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Header
        val title = TextView(this).apply {
            text = "⬡ SXTOOLS"
            setTextColor(Color.parseColor("#5fc9ff"))
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val status = TextView(this).apply {
            text = "● ONLINE"
            setTextColor(Color.parseColor("#7ddfa0"))
            setPadding(24, 8, 24, 8)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.SPACE_BETWEEN
            addView(title)
            addView(status)
        }
        container.addView(header)

        // Meta
        val meta = TextView(this).apply {
            text = "v1.0.0  |  DEV: SAN  |  SDK: Android 14+"
            setTextColor(Color.parseColor("#8a9cb0"))
            textSize = 12f
            setPadding(0, 12, 0, 20)
        }
        container.addView(meta)

        // Input and button
        urlInput = EditText(this).apply {
            hint = "Enter full URL (https://...)"
            setHintTextColor(Color.parseColor("#8a9cb0"))
            setTextColor(Color.parseColor("#d4e2f0"))
            setBackgroundColor(Color.parseColor("#0f171f"))
            setPadding(32, 24, 32, 24)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        fetchBtn = Button(this).apply {
            text = "▶ CONVERT"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2a5a7a"))
        }
        val inputGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(urlInput)
            addView(fetchBtn)
        }
        container.addView(inputGroup)

        // Result area
        val resultArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0b1117"))
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                700
            ).apply { topMargin = 24 }
        }
        copyBtn = Button(this).apply {
            text = "📋 COPY HTML"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2a5a4a"))
            textSize = 12f
        }
        outputArea = EditText(this).apply {
            hint = "Hasil HTML akan muncul di sini..."
            setHintTextColor(Color.parseColor("#4a607a"))
            setTextColor(Color.parseColor("#c8d8e8"))
            background = null
            setPadding(24, 24, 24, 24)
            minLines = 15
            maxLines = 20
            isFocusable = false
        }
        resultArea.addView(copyBtn)
        resultArea.addView(outputArea)
        container.addView(resultArea)

        // Footer
        val footer = TextView(this).apply {
            text = "⚡ SXTOOLS · RAW HTML EXTRACTOR · CORS-PROXY ENABLED"
            setTextColor(Color.parseColor("#4a607a"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }
        container.addView(footer)

        root.addView(container)
        setContentView(root)

        // Toast view
        toastView = TextView(this).apply {
            setBackgroundColor(Color.parseColor("#2a4a3a"))
            setTextColor(Color.parseColor("#b0e0c0"))
            setPadding(32, 16, 32, 16)
            visibility = View.GONE
        }
        root.addView(toastView)

        // Event listeners
        fetchBtn.setOnClickListener { fetchAndConvert(urlInput.text.toString()) }
        copyBtn.setOnClickListener { copyText(outputArea.text.toString()) }
    }

    private fun showToast(msg: String) {
        mainHandler.post {
            toastView.text = msg
            toastView.visibility = View.VISIBLE
            mainHandler.postDelayed({ toastView.visibility = View.GONE }, 2000)
        }
    }

    private fun copyText(text: String) {
        if (text.isBlank()) {
            showToast("⚠ No content to copy")
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("HTML", text)
        clipboard.setPrimaryClip(clip)
        showToast("✓ HTML copied")
    }

    private fun fetchAndConvert(url: String) {
        if (url.isBlank()) {
            outputArea.setText("ERROR: URL cannot be empty.")
            return
        }
        var fullUrl = url.trim()
        if (!fullUrl.startsWith("http")) fullUrl = "https://" + fullUrl
        if (cache.containsKey(fullUrl)) {
            outputArea.setText(cache[fullUrl])
            showToast("✓ Loaded from cache")
            return
        }

        fetchBtn.isEnabled = false
        fetchBtn.text = "⏳ FETCHING"
        outputArea.setText("⏳ Fetching $fullUrl ...\n")

        val finalFullUrl = fullUrl
        executor.execute {
            var conn: HttpURLConnection? = null
            try {
                val proxyUrl = "https://api.allorigins.win/raw?url=" + URLEncoder.encode(finalFullUrl, "UTF-8")
                val urlObj = URL(proxyUrl)
                conn = urlObj.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                if (conn.responseCode != 200) throw Exception("HTTP ${'$'}{conn.responseCode}")

                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line).append('\n')
                }
                reader.close()
                val html = response.toString()
                cache[finalFullUrl] = html
                mainHandler.post {
                    outputArea.setText(html)
                    fetchBtn.isEnabled = true
                    fetchBtn.text = "▶ CONVERT"
                }
            } catch (e: Exception) {
                mainHandler.post {
                    outputArea.setText("ERROR: ${'$'}{e.message}\n\nPossible reasons:\n- Proxy down\n- Invalid URL")
                    fetchBtn.isEnabled = true
                    fetchBtn.text = "▶ CONVERT"
                }
            } finally {
                conn?.disconnect()
            }
        }
    }
}
