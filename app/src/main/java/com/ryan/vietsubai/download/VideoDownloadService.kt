package com.ryan.vietsubai.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.work.*
import com.ryan.vietsubai.data.AppDatabase
import com.ryan.vietsubai.data.DownloadJobEntity
import com.ryan.vietsubai.model.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

class VideoDownloadService(private val context: Context) {
    private val db = AppDatabase.get(context)
    fun downloadDirect(url: String, title: String = "Vietsub AI video"): Pair<String, Long> {
        val jobId = "download_${System.currentTimeMillis()}"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title).setDescription("Vietsub AI")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "Vietsub AI/$jobId.mp4")
            .setAllowedOverMetered(true).setAllowedOverRoaming(false)
        val downloadId = context.getSystemService(DownloadManager::class.java).enqueue(request)
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<DownloadMonitorWorker>().setInputData(workDataOf("job_id" to jobId, "download_id" to downloadId)).build())
        return jobId to downloadId
    }
    fun resolveAndDownload(pageUrl: String, config: AppConfig): UUID {
        val work = OneTimeWorkRequestBuilder<ResolveDownloadWorker>()
            .setInputData(workDataOf("page_url" to pageUrl, "resolver" to config.mediaResolverUrl))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueue(work); return work.id
    }
}

class ResolveDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val page = inputData.getString("page_url") ?: return@withContext Result.failure()
        val resolver = inputData.getString("resolver").orEmpty()
        if (resolver.isBlank()) return@withContext Result.failure(workDataOf("error" to "Chưa cấu hình Media Resolver API"))
        return@withContext try {
            val endpoint = resolver.trimEnd('/') + "/resolve?url=" + URLEncoder.encode(page, "UTF-8")
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000; conn.readTimeout = 30_000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body); val mediaUrl = json.optString("url")
            if (mediaUrl.isBlank()) return@withContext Result.failure(workDataOf("error" to "Resolver không trả về media URL"))
            val title = json.optString("title", "Vietsub AI video").take(80)
            val dm = applicationContext.getSystemService(DownloadManager::class.java)
            val request = DownloadManager.Request(Uri.parse(mediaUrl)).setTitle(title).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED).setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "Vietsub AI/resolved_${System.currentTimeMillis()}.mp4")
            val id = dm.enqueue(request)
            val jobId = "download_${System.currentTimeMillis()}"
            AppDatabase.get(applicationContext).downloadJobDao().upsert(DownloadJobEntity(jobId,page,title,0,"downloading",id))
            WorkManager.getInstance(applicationContext).enqueue(OneTimeWorkRequestBuilder<DownloadMonitorWorker>().setInputData(workDataOf("job_id" to jobId, "download_id" to id)).build())
            Result.success(workDataOf("download_id" to id, "job_id" to jobId))
        } catch (e: Exception) { Result.failure(workDataOf("error" to (e.message ?: "Download failed"))) }
    }
}

class DownloadMonitorWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val jobId=inputData.getString("job_id") ?: return Result.failure(); val id=inputData.getLong("download_id", -1)
        if (id<0) return Result.failure(); val dm=applicationContext.getSystemService(DownloadManager::class.java); val db=AppDatabase.get(applicationContext)
        while (true) {
            val c=dm.query(DownloadManager.Query().setFilterById(id)) ?: return Result.retry()
            c.use { if(!it.moveToFirst()) return Result.failure(); val status=it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)); val done=it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)); val total=it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)); val progress=if(total>0)(done*100/total).toInt() else 0
                when(status){DownloadManager.STATUS_SUCCESSFUL->{db.downloadJobDao().get(jobId)?.let { db.downloadJobDao().upsert(it.copy(progress=100,status="done")) }; return Result.success()}
                    DownloadManager.STATUS_FAILED->{db.downloadJobDao().get(jobId)?.let { db.downloadJobDao().upsert(it.copy(progress=progress,status="failed")) }; return Result.failure()}
                    else->db.downloadJobDao().get(jobId)?.let { db.downloadJobDao().upsert(it.copy(progress=progress,status="downloading")) }}
            }
            delay(1000)
        }
    }
}
