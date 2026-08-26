package com.app.vietsubai

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf

class DownloadActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var progress: ProgressBar
    private lateinit var stage: TextView
    private lateinit var status: TextView
    private lateinit var percent: TextView
    private lateinit var meta: TextView
    private lateinit var openButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download)
        findViewById<ViewGroup>(android.R.id.content).getChildAt(0).animateEntrance()
        urlInput = findViewById(R.id.videoUrlInput)
        progress = findViewById(R.id.downloadProgress)
        stage = findViewById(R.id.downloadStage)
        status = findViewById(R.id.downloadStatus)
        percent = findViewById(R.id.downloadPercent)
        meta = findViewById(R.id.downloadMeta)
        openButton = findViewById(R.id.openDownloadedButton)
        findViewById<Button>(R.id.downloadUrlButton).apply {
            enablePressMotion()
            setOnClickListener { enqueueDownload() }
        }
        openButton.apply {
            enablePressMotion()
            setOnClickListener { openDownloadedVideo() }
        }
        intent.getStringExtra(EXTRA_URL)?.let { urlInput.setText(it) }
    }

    private fun enqueueDownload() {
        val url = urlInput.text.toString().trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            showError("URL không hợp lệ. Hãy dán link bắt đầu bằng http:// hoặc https://")
            return
        }
        findViewById<Button>(R.id.downloadUrlButton).isEnabled = false
        openButton.visibility = View.GONE
        progress.isIndeterminate = true
        progress.progress = 0
        percent.text = "0%"
        stage.text = "Đang chờ"
        status.setTextColor(Color.WHITE)
        status.text = "Đang kiểm tra URL media trực tiếp..."
        meta.text = ""
        val request = OneTimeWorkRequestBuilder<PlatformDownloadWorker>()
            .setInputData(workDataOf(PlatformDownloadWorker.KEY_URL to url))
            .addTag("platform-download")
            .build()
        WorkManager.getInstance(this).enqueue(request)
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(request.id).observe(this) { info ->
            if (info == null) return@observe
            val value = info.progress.getInt("progress", 0).coerceIn(0, 100)
            progress.isIndeterminate = info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING && info.progress.keyValueMap.isEmpty()
            if (!progress.isIndeterminate) {
                progress.progress = value
                percent.text = "$value%"
            }
            val downloaded = info.progress.getLong("downloaded", 0L)
            val total = info.progress.getLong("total", -1L)
            if (downloaded > 0L) meta.text = "${formatBytes(downloaded)}${if (total > 0L) " / ${formatBytes(total)}" else ""}"
            when (info.state) {
                WorkInfo.State.ENQUEUED -> { stage.text = "Đang chờ"; status.text = "Tác vụ được giữ trong background..." }
                WorkInfo.State.RUNNING -> { stage.text = "Đang tải video"; status.text = "Đang nhận dữ liệu từ URL" }
                WorkInfo.State.SUCCEEDED -> {
                    progress.isIndeterminate = false
                    progress.progress = 100
                    percent.text = "100%"
                    stage.text = "Tải hoàn tất"
                    status.setTextColor(Color.rgb(120, 230, 180))
                    status.text = "Video đã được tải vào bộ nhớ ứng dụng."
                    openButton.visibility = View.VISIBLE
                }
                WorkInfo.State.FAILED -> {
                    showError(info.outputData.getString("error") ?: "Không thể tải video")
                    findViewById<Button>(R.id.downloadUrlButton).isEnabled = true
                }
                WorkInfo.State.CANCELLED -> {
                    showError("Tác vụ tải đã bị hủy")
                    findViewById<Button>(R.id.downloadUrlButton).isEnabled = true
                }
                else -> Unit
            }
        }
    }

    private fun openDownloadedVideo() {
        // The worker output is observed again from the latest completed work in the next screen.
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    private fun showError(message: String) {
        progress.isIndeterminate = false
        stage.text = "Tải thất bại"
        status.setTextColor(Color.rgb(255, 120, 140))
        status.text = message
    }

    private fun formatBytes(value: Long): String = when {
        value < 1024L -> "${value} B"
        value < 1024L * 1024L -> "%.1f KB".format(value / 1024.0)
        value < 1024L * 1024L * 1024L -> "%.1f MB".format(value / 1024.0 / 1024.0)
        else -> "%.2f GB".format(value / 1024.0 / 1024.0 / 1024.0)
    }

    companion object { const val EXTRA_URL = "extra_url" }
}
