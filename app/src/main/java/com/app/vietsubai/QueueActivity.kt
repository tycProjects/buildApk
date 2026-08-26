package com.app.vietsubai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*

class QueueActivity : AppCompatActivity() {
    private var videos: List<Uri> = emptyList()
    private lateinit var count: TextView
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        videos = uris
        uris.forEach { runCatching { contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        count.text = if (uris.isEmpty()) "Chưa chọn video" else "Đã chọn ${uris.size} video"
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_queue)
        findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0).animateEntrance()
        findViewById<Button>(R.id.selectManyButton).enablePressMotion(); findViewById<Button>(R.id.startQueueButton).enablePressMotion()
        count = findViewById(R.id.selectedCount)
        val mode=findViewById<Spinner>(R.id.queueMode); val source=findViewById<Spinner>(R.id.queueSource); val target=findViewById<Spinner>(R.id.queueTarget); val bitrate=findViewById<Spinner>(R.id.queueBitrate); val resolution=findViewById<Spinner>(R.id.queueResolution); val preset=findViewById<Spinner>(R.id.queuePreset)
        mode.adapter=adapter(listOf("STT: nhận dạng lời thoại","OCR: đọc phụ đề trên hình")); source.adapter=adapter(LanguageCatalog.source.map { it.label }); target.adapter=adapter(LanguageCatalog.target.map { it.label }); bitrate.adapter=adapter(listOf("1500k","2500k","4000k","6000k")); resolution.adapter=adapter(listOf("original","640x360","1280x720","1920x1080")); preset.adapter=adapter(listOf("ultrafast","veryfast","faster","fast","medium"))
        findViewById<Button>(R.id.selectManyButton).setOnClickListener { picker.launch(arrayOf("video/*")) }
        findViewById<Button>(R.id.startQueueButton).setOnClickListener { enqueue(mode.selectedItem.toString().startsWith("OCR"), LanguageCatalog.source[source.selectedItemPosition].code, LanguageCatalog.target[target.selectedItemPosition].code, bitrate.selectedItem.toString(), resolution.selectedItem.toString(), preset.selectedItem.toString()) }
    }
    private fun adapter(items: List<String>)=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items)
    private fun enqueue(ocr:Boolean,source:String,target:String,bitrate:String,resolution:String,preset:String) {
        if (videos.isEmpty()) { Toast.makeText(this,"Hãy chọn ít nhất một video",Toast.LENGTH_SHORT).show(); return }
        if (!ApiKeyStore(this).isConfigured()) { Toast.makeText(this,"Hãy nhập API key trong Settings",Toast.LENGTH_LONG).show(); return }
        var continuation: WorkContinuation? = null
        videos.forEach { uri ->
            val historyId=HistoryStore(this).addQueued(uri.lastPathSegment ?: "video")
            val data=workDataOf(QueuedVideoWorker.KEY_URI to uri.toString(),QueuedVideoWorker.KEY_HISTORY_ID to historyId,QueuedVideoWorker.KEY_MODE to if(ocr) "ocr" else "stt",QueuedVideoWorker.KEY_SOURCE to source,QueuedVideoWorker.KEY_TARGET to target,QueuedVideoWorker.KEY_BITRATE to bitrate,QueuedVideoWorker.KEY_RESOLUTION to resolution,QueuedVideoWorker.KEY_PRESET to preset)
            val request=OneTimeWorkRequestBuilder<QueuedVideoWorker>().setInputData(data).addTag("video-export-queue").build()
            continuation=if(continuation==null) WorkManager.getInstance(this).beginUniqueWork(QUEUE_NAME,ExistingWorkPolicy.APPEND_OR_REPLACE,request) else continuation!!.then(request)
        }
        continuation?.enqueue(); Toast.makeText(this,"Đã đưa ${videos.size} video vào hàng đợi",Toast.LENGTH_LONG).show(); finish()
    }
    companion object { private const val QUEUE_NAME="subtitle-video-queue" }
}
