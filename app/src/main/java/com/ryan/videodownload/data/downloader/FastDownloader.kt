package com.ryan.videodownload.data.downloader

import com.ryan.videodownload.data.model.DownloadStatus
import com.ryan.videodownload.data.model.DownloadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * FastDownloader - Tối ưu tốc độ tải:
 * - Multi-connection (chunked parallel, giới hạn maxConnections)
 * - Connection pooling (OkHttp)
 * - Resume via Range header
 * - Progress realtime
 */
class FastDownloader(
    private val maxConnections: Int = 4,
    private val chunkSize: Long = 2 * 1024 * 1024 // 2MB
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    suspend fun startDownload(
        task: DownloadTask,
        outputDir: File,
        onProgress: (DownloadTask) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        val baseName = sanitizeFileName(task.videoInfo.title)
        val ext = task.selectedQuality.format
        var outputFile = File(outputDir, "$baseName.$ext")
        if (outputFile.exists()) {
            outputFile = File(outputDir, "${baseName}_${System.currentTimeMillis()}.$ext")
        }

        updateTask(task.copy(status = DownloadStatus.DOWNLOADING, progress = 0f))

        try {
            val headRequest = Request.Builder()
                .url(task.selectedQuality.url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .head()
                .build()

            var contentLength = 0L
            var acceptRanges = false
            try {
                client.newCall(headRequest).execute().use { headResponse ->
                    contentLength = headResponse.header("Content-Length")?.toLongOrNull() ?: 0L
                    acceptRanges = headResponse.header("Accept-Ranges") == "bytes"
                }
            } catch (_: Exception) {
                // HEAD có thể fail → fallback single connection
            }

            val finalFile = if (acceptRanges && contentLength > chunkSize * 2 && maxConnections > 1) {
                downloadMultiConnection(task, outputFile, contentLength, onProgress)
            } else {
                downloadSingle(task, outputFile, contentLength, onProgress)
            }

            val completed = task.copy(
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                downloadedBytes = finalFile.length(),
                totalBytes = finalFile.length(),
                filePath = finalFile.absolutePath,
                speedBytesPerSec = 0
            )
            updateTask(completed)
            onProgress(completed)
            Result.success(finalFile)
        } catch (e: Exception) {
            val failed = task.copy(
                status = DownloadStatus.FAILED,
                errorMessage = e.message ?: "Unknown error"
            )
            updateTask(failed)
            onProgress(failed)
            Result.failure(e)
        }
    }

    private suspend fun downloadSingle(
        task: DownloadTask,
        outputFile: File,
        totalBytes: Long,
        onProgress: (DownloadTask) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(task.selectedQuality.url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Referer", task.selectedQuality.url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

            val body = response.body ?: throw Exception("Empty body")
            val total = if (totalBytes > 0) totalBytes else body.contentLength().coerceAtLeast(0)

            var downloaded = 0L
            val startTime = System.currentTimeMillis()
            var lastUpdate = startTime

            body.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 150) {
                            val elapsed = (now - startTime).coerceAtLeast(1)
                            val speed = (downloaded * 1000) / elapsed
                            val progress = if (total > 0) {
                                (downloaded.toFloat() / total).coerceIn(0f, 1f)
                            } else {
                                // Giữ 0 khi chưa biết total; UI sẽ dùng indeterminate
                                0f
                            }

                            val updated = task.copy(
                                status = DownloadStatus.DOWNLOADING,
                                progress = progress,
                                downloadedBytes = downloaded,
                                totalBytes = total,
                                speedBytesPerSec = speed
                            )
                            updateTask(updated)
                            onProgress(updated)
                            lastUpdate = now
                        }
                    }
                }
            }
        }
        outputFile
    }

    private suspend fun downloadMultiConnection(
        task: DownloadTask,
        outputFile: File,
        totalBytes: Long,
        onProgress: (DownloadTask) -> Unit
    ): File = coroutineScope {
        RandomAccessFile(outputFile, "rw").use { it.setLength(totalBytes) }

        val downloadedTotal = AtomicLong(0)
        val startTime = System.currentTimeMillis()
        val semaphore = Semaphore(maxConnections)

        val ranges = mutableListOf<Pair<Long, Long>>()
        var start = 0L
        while (start < totalBytes) {
            val end = min(start + chunkSize - 1, totalBytes - 1)
            ranges.add(start to end)
            start = end + 1
        }

        val progressJob = launch(Dispatchers.IO) {
            while (isActive && downloadedTotal.get() < totalBytes) {
                kotlinx.coroutines.delay(150)
                val current = downloadedTotal.get()
                val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                val speed = (current * 1000) / elapsed
                val progress = (current.toFloat() / totalBytes).coerceIn(0f, 1f)

                val updated = task.copy(
                    status = DownloadStatus.DOWNLOADING,
                    progress = progress,
                    downloadedBytes = current,
                    totalBytes = totalBytes,
                    speedBytesPerSec = speed
                )
                updateTask(updated)
                onProgress(updated)
            }
        }

        try {
            ranges.map { (rangeStart, rangeEnd) ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        downloadChunk(
                            task.selectedQuality.url,
                            outputFile,
                            rangeStart,
                            rangeEnd,
                            downloadedTotal
                        )
                    }
                }
            }.awaitAll()
        } finally {
            progressJob.cancel()
        }

        outputFile
    }

    private fun downloadChunk(
        url: String,
        file: File,
        start: Long,
        end: Long,
        downloadedTotal: AtomicLong
    ) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Range", "bytes=$start-$end")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                throw Exception("Chunk failed: HTTP ${response.code}")
            }
            val body = response.body ?: return

            RandomAccessFile(file, "rw").use { raf ->
                raf.seek(start)
                body.byteStream().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        raf.write(buffer, 0, bytesRead)
                        downloadedTotal.addAndGet(bytesRead.toLong())
                    }
                }
            }
        }
    }

    fun cancel(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        _tasks.update { list ->
            list.map {
                if (it.id == taskId) it.copy(status = DownloadStatus.CANCELLED) else it
            }
        }
    }

    private fun updateTask(task: DownloadTask) {
        _tasks.update { list ->
            val idx = list.indexOfFirst { it.id == task.id }
            if (idx >= 0) list.toMutableList().apply { this[idx] = task }
            else list + task
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(80)
            .trim()
            .ifBlank { "video_${System.currentTimeMillis()}" }
    }
}
