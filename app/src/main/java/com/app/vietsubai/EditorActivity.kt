package com.app.vietsubai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Choreographer
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.UUID
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var cueContainer: LinearLayout
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var subtitleOverlay: TextView
    private val cues = mutableListOf<SubtitleCue>()
    private var renderedFile: File? = null
    private var lastOverlayText = ""
    private var overlayRunning = false
    private val overlayFrameCallback = object : Choreographer.FrameCallback { override fun doFrame(frameTimeNanos: Long) { if (::player.isInitialized) { val text = cues.firstOrNull { player.currentPosition in it.startMs..it.endMs }?.text.orEmpty(); if (text != lastOverlayText) { subtitleOverlay.text = text; lastOverlayText = text } }; if (overlayRunning) Choreographer.getInstance().postFrameCallback(this) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_editor)
        findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0).animateEntrance()
        findViewById<Button>(R.id.exportButton).enablePressMotion()
        playerView = findViewById(R.id.playerView); cueContainer = findViewById(R.id.cueContainer); status = findViewById(R.id.editorStatus); progress=findViewById(R.id.exportProgress); subtitleOverlay=findViewById(R.id.subtitleOverlay)
        val videoUri = Uri.parse(intent.getStringExtra(EXTRA_VIDEO))
        cues += SrtParser.parse(intent.getStringExtra(EXTRA_SRT).orEmpty())
        findViewById<Spinner>(R.id.fontSpinner).adapter = adapter(listOf("Arial", "Roboto", "sans-serif", "DejaVu Sans"))
        findViewById<Spinner>(R.id.alignmentSpinner).adapter = adapter(listOf("Bottom center (2)", "Bottom left (1)", "Bottom right (3)", "Top center (8)"))
        findViewById<Spinner>(R.id.bitrateSpinner).adapter = adapter(listOf("1500k", "2500k", "4000k", "6000k"))
        findViewById<Spinner>(R.id.resolutionSpinner).adapter = adapter(listOf("original", "640x360", "1280x720", "1920x1080"))
        findViewById<Spinner>(R.id.presetSpinner).adapter = adapter(listOf("ultrafast", "veryfast", "faster", "fast", "medium"))
        findViewById<Spinner>(R.id.formatSpinner).adapter = adapter(SubtitleFormat.values().map { it.name })
        val loadControl = DefaultLoadControl.Builder().setBufferDurationsMs(1500, 5000, 250, 500).setPrioritizeTimeOverSizeThresholds(true).build()
        player = ExoPlayer.Builder(this).setLoadControl(loadControl).setSeekBackIncrementMs(5000).setSeekForwardIncrementMs(5000).build().also { it.setMediaItem(MediaItem.fromUri(videoUri)); it.prepare(); playerView.player = it }
        renderCueEditor()
        startOverlayUpdates()
        findViewById<Button>(R.id.exportButton).setOnClickListener { exportVideo(videoUri) }
    }

    private fun renderCueEditor() {
        cueContainer.removeAllViews()
        cues.forEachIndexed { index, cue ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 8) }
            val time = TextView(this).apply { text = "Cue ${cue.index} — chạm để phát"; setOnClickListener { player.seekTo(cue.startMs); player.play() } }
            val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val start = EditText(this).apply { setText(SrtParser.formatTime(cue.startMs)); hint = "Bắt đầu"; setSingleLine() }
            val end = EditText(this).apply { setText(SrtParser.formatTime(cue.endMs)); hint = "Kết thúc"; setSingleLine() }
            start.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) SrtParser.parseDisplayTime(start.text.toString())?.let { cue.startMs=it } }
            end.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) SrtParser.parseDisplayTime(end.text.toString())?.let { cue.endMs=it } }
            times.addView(start, LinearLayout.LayoutParams(0,-2,1f)); times.addView(end, LinearLayout.LayoutParams(0,-2,1f))
            val edit = EditText(this).apply { setText(cue.text); minLines = 2; hint = "Nội dung phụ đề"; setSelectAllOnFocus(false) }
            edit.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) cue.text = edit.text.toString() }
            row.addView(time, LinearLayout.LayoutParams(-1, -2)); row.addView(times, LinearLayout.LayoutParams(-1, -2)); row.addView(edit, LinearLayout.LayoutParams(-1, -2)); cueContainer.addView(row, ViewGroup.LayoutParams(-1, -2))
        }
    }

    private fun adapter(items: List<String>) = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)

    private fun exportVideo(videoUri: Uri) {
        cues.forEachIndexed { i, cue ->
            val row = cueContainer.getChildAt(i) as LinearLayout
            val times = row.getChildAt(1) as LinearLayout
            SrtParser.parseDisplayTime((times.getChildAt(0) as EditText).text.toString())?.let { cue.startMs = it }
            SrtParser.parseDisplayTime((times.getChildAt(1) as EditText).text.toString())?.let { cue.endMs = it }
            cue.text = (row.getChildAt(2) as EditText).text.toString()
        }
        progress.visibility = ProgressBar.VISIBLE; progress.isIndeterminate = false; progress.progress = 0; status.text = "Đang đưa tác vụ vào hàng đợi..."
        val font = findViewById<Spinner>(R.id.fontSpinner).selectedItem.toString()
        val fontSize = findViewById<EditText>(R.id.fontSizeInput).text.toString().toIntOrNull()?.coerceIn(8, 96) ?: 22
        val textColor = findViewById<EditText>(R.id.textColorInput).text.toString().trim().takeIf { Regex("^#?[0-9A-Fa-f]{6}$").matches(it) }?.let { if (it.startsWith("#")) it else "#$it" } ?: "#FFFFFF"
        val outlineColor = findViewById<EditText>(R.id.outlineColorInput).text.toString().trim().takeIf { Regex("^#?[0-9A-Fa-f]{6}$").matches(it) }?.let { if (it.startsWith("#")) it else "#$it" } ?: "#000000"
        val alignment = when (findViewById<Spinner>(R.id.alignmentSpinner).selectedItemPosition) { 1 -> 1; 2 -> 3; 3 -> 8; else -> 2 }
        val bitrate = findViewById<Spinner>(R.id.bitrateSpinner).selectedItem.toString()
        val resolution = findViewById<Spinner>(R.id.resolutionSpinner).selectedItem.toString()
        val preset = findViewById<Spinner>(R.id.presetSpinner).selectedItem.toString()
        val format = findViewById<Spinner>(R.id.formatSpinner).selectedItem.toString()
        val input = Data.Builder().putString(VideoBurnInWorker.KEY_VIDEO_URI, videoUri.toString()).putString(VideoBurnInWorker.KEY_SRT, SrtParser.serialize(cues)).putString(VideoBurnInWorker.KEY_FONT, font).putInt(VideoBurnInWorker.KEY_FONT_SIZE, fontSize).putString(VideoBurnInWorker.KEY_PRIMARY_COLOR, textColor).putString(VideoBurnInWorker.KEY_OUTLINE_COLOR, outlineColor).putInt(VideoBurnInWorker.KEY_OUTLINE, 2).putInt(VideoBurnInWorker.KEY_ALIGNMENT, alignment).putString(VideoBurnInWorker.KEY_BITRATE, bitrate).putString(VideoBurnInWorker.KEY_RESOLUTION, resolution).putString(VideoBurnInWorker.KEY_PRESET, preset).putString(VideoBurnInWorker.KEY_FORMAT, format).build()
        val request = OneTimeWorkRequestBuilder<VideoBurnInWorker>().setInputData(input).addTag("video-export").build()
        WorkManager.getInstance(this).enqueue(request)
        observeExport(request.id)
    }

    private fun observeExport(id: UUID) {
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(id).observe(this) { info ->
            if (info == null) return@observe
            val value = info.progress.getInt("progress", 0); progress.progress = value
            when (info.state) {
                WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> status.text = if (value > 0) "Đang burn-in phụ đề: $value%" else "Đang chuẩn bị FFmpeg..."
                WorkInfo.State.SUCCEEDED -> { renderedFile = File(info.outputData.getString(VideoBurnInWorker.KEY_OUTPUT)!!); progress.visibility = ProgressBar.GONE; status.text = "Render xong. Chọn nơi lưu MP4."; saveRenderedVideo() }
                WorkInfo.State.FAILED -> { progress.visibility = ProgressBar.GONE; status.text = "Export thất bại: ${info.outputData.getString("error") ?: "unknown"}" }
                WorkInfo.State.CANCELLED -> { progress.visibility = ProgressBar.GONE; status.text = "Đã hủy export." }
                else -> Unit
            }
        }
    }

    private fun saveRenderedVideo() { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { type = "video/mp4"; putExtra(Intent.EXTRA_TITLE, "translated-video.mp4") }, REQUEST_SAVE) }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode == REQUEST_SAVE && resultCode == RESULT_OK && data?.data != null) lifecycleScope.launch(Dispatchers.IO) { contentResolver.openOutputStream(data.data!!)?.use { out -> renderedFile!!.inputStream().use { it.copyTo(out) } }; withContext(Dispatchers.Main) { status.text = "Đã lưu video MP4." } } }
    private fun startOverlayUpdates() { if (!overlayRunning) { overlayRunning = true; Choreographer.getInstance().postFrameCallback(overlayFrameCallback) } }
    override fun onResume() { super.onResume(); if (::player.isInitialized) startOverlayUpdates() }
    override fun onPause() { overlayRunning = false; Choreographer.getInstance().removeFrameCallback(overlayFrameCallback); super.onPause() }
    override fun onDestroy() { if (::player.isInitialized) { playerView.player = null; player.release() }; super.onDestroy() }
    companion object { const val EXTRA_VIDEO = "video_uri"; const val EXTRA_SRT = "srt_text"; private const val REQUEST_SAVE = 91 }
}
