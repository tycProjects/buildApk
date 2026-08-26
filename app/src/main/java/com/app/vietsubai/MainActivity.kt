package com.app.vietsubai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var selectedVideo: Uri? = null
    private var translatedCues = mutableListOf<SubtitleCue>()
    private lateinit var statusText: TextView
    private lateinit var previewText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var translateButton: Button
    private lateinit var exportSrtButton: Button
    private lateinit var editButton: Button

    private val videoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@registerForActivityResult
        selectedVideo = uri
        statusText.text = "Đã chọn video từ album. Chọn STT hoặc OCR rồi bấm Nhận dạng và dịch."
        findViewById<View>(R.id.processingControls).visibility = View.VISIBLE
        translateButton.isEnabled = true
        exportSrtButton.isEnabled = false
        editButton.isEnabled = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0).animateEntrance()
        listOf(R.id.settingsButton, R.id.queueButton, R.id.queueButtonSecondary, R.id.historyButton, R.id.selectVideoButton, R.id.pasteUrlButton, R.id.clipboardButton, R.id.translateButton, R.id.exportButton, R.id.editButton).forEach { findViewById<View>(it).enablePressMotion() }
        statusText = findViewById(R.id.statusText); previewText = findViewById(R.id.previewText); progressBar = findViewById(R.id.progressBar)
        translateButton = findViewById(R.id.translateButton); exportSrtButton = findViewById(R.id.exportButton); editButton = findViewById(R.id.editButton)
        val mode = findViewById<Spinner>(R.id.sourceModeSpinner); val source = findViewById<Spinner>(R.id.sourceLanguageSpinner); val target = findViewById<Spinner>(R.id.targetLanguageSpinner)
        mode.adapter = adapter(listOf("STT: nhận dạng lời thoại", "OCR: đọc phụ đề trên hình")); source.adapter = adapter(LanguageCatalog.source.map { it.label }); target.adapter = adapter(LanguageCatalog.target.map { it.label })
        findViewById<Button>(R.id.settingsButton).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<Button>(R.id.queueButton).setOnClickListener { startActivity(Intent(this, QueueActivity::class.java)) }
        findViewById<Button>(R.id.historyButton).setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        findViewById<Button>(R.id.selectVideoButton).setOnClickListener { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
        findViewById<Button>(R.id.queueButtonSecondary).setOnClickListener { startActivity(Intent(this, QueueActivity::class.java)) }
        findViewById<Button>(R.id.pasteUrlButton).setOnClickListener { startActivity(Intent(this, DownloadActivity::class.java)) }
        findViewById<Button>(R.id.clipboardButton).setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty().trim()
            if (text.startsWith("http://") || text.startsWith("https://")) {
                startActivity(Intent(this, DownloadActivity::class.java).putExtra(DownloadActivity.EXTRA_URL, text))
            } else {
                Toast.makeText(this, "Clipboard chưa có URL hợp lệ", Toast.LENGTH_SHORT).show()
            }
        }
        translateButton.setOnClickListener { process(mode.selectedItem.toString().startsWith("OCR"), LanguageCatalog.source[source.selectedItemPosition].code, LanguageCatalog.target[target.selectedItemPosition].code) }
        exportSrtButton.setOnClickListener { saveSrt() }
        editButton.setOnClickListener { openEditor() }
    }

    private fun adapter(items: List<String>) = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)

    private fun process(ocr: Boolean, source: String, target: String) {
        val uri = selectedVideo ?: return
        val keyStore = ApiKeyStore(this)
        if (!keyStore.isConfigured()) { statusText.text = "Hãy mở Cài đặt và nhập đủ Gemini/Groq API key trước khi xử lý."; return }
        setBusy(true, true, if (ocr) "Đang lấy frame và chạy Gemini OCR..." else "Đang tách audio và chạy Groq STT...")
        lifecycleScope.launch {
            runCatching {
                val api = AiSubtitleApi(keyStore.gemini(), keyStore.groq())
                DirectSubtitlePipeline(this@MainActivity, api).run(uri, if (ocr) "ocr" else "stt", source, target) { value, message -> runOnUiThread { progressBar.progress = value; statusText.text = message } }
            }.onSuccess { cues ->
                translatedCues = cues; previewText.text = SrtParser.serialize(cues); exportSrtButton.isEnabled = true; editButton.isEnabled = true
                setBusy(false, false, "Hoàn tất. Mở editor để xem video và chỉnh từng cue trước khi export.")
            }.onFailure { setBusy(false, false, "Lỗi AI: ${it.message}") }
        }
    }

    private fun openEditor() {
        val video = selectedVideo ?: return
        startActivity(Intent(this, EditorActivity::class.java).apply { putExtra(EditorActivity.EXTRA_VIDEO, video.toString()); putExtra(EditorActivity.EXTRA_SRT, SrtParser.serialize(translatedCues)) })
    }

    private fun saveSrt() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "application/x-subrip"; putExtra(Intent.EXTRA_TITLE, "translated.srt") }, REQUEST_SRT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == REQUEST_SRT && resultCode == RESULT_OK && data?.data != null) lifecycleScope.launch { contentResolver.openOutputStream(data.data!!)?.use { it.write(SrtParser.serialize(translatedCues).toByteArray()) }; Toast.makeText(this@MainActivity, "Đã xuất SRT", Toast.LENGTH_SHORT).show() } }

    private fun setBusy(busy: Boolean, indeterminate: Boolean, message: String) { progressBar.visibility = if (busy) View.VISIBLE else View.GONE; progressBar.isIndeterminate = indeterminate; translateButton.isEnabled = !busy; statusText.text = message }
    companion object { private const val REQUEST_SRT = 42 }
}
