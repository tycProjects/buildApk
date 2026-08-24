package com.ryan.videodownload.data.downloader

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.ryan.videodownload.data.model.Platform
import com.ryan.videodownload.data.model.VideoInfo
import com.ryan.videodownload.data.model.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * VideoExtractor thật – lấy metadata + direct URL:
 * 1) YouTube: Piped public API
 * 2) TikTok: tikwm.com
 * 3) Cobalt community instances (đa nền tảng)
 * 4) oEmbed fallback (title + thumbnail)
 */
class VideoExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private val pipedInstances = listOf(
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://api.piped.private.coffee",
        "https://pipedapi.nosebs.ru",
        "https://piped-api.privacyredirect.com"
    )

    private val cobaltInstances = listOf(
        "https://api.cobalt.best",
        "https://cobalt-api.kwiatekmiki.com",
        "https://co.wuk.sh"
    )

    suspend fun extract(url: String): Result<VideoInfo> = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("URL trống"))
        }
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("URL không hợp lệ (cần https://...)"))
        }

        val platform = detectPlatform(cleanUrl)
        val errors = mutableListOf<String>()

        when (platform) {
            Platform.YOUTUBE -> {
                extractYoutube(cleanUrl)?.let { return@withContext Result.success(it) }
                    ?: errors.add("YouTube/Piped")
            }
            Platform.TIKTOK -> {
                extractTikTok(cleanUrl)?.let { return@withContext Result.success(it) }
                    ?: errors.add("TikTok/tikwm")
            }
            else -> { }
        }

        extractCobalt(cleanUrl, platform)?.let { return@withContext Result.success(it) }
            ?: errors.add("Cobalt")

        // oEmbed: chỉ metadata (có thể không có link tải)
        val oembed = extractOEmbed(cleanUrl, platform)
        if (oembed != null) {
            if (oembed.qualities.isNotEmpty()) {
                return@withContext Result.success(oembed)
            }
            // Có title/thumb nhưng không có stream → báo rõ
            return@withContext Result.failure(
                Exception(
                    "Đã lấy được tiêu đề «${oembed.title}» nhưng không có link tải trực tiếp.\n" +
                    "Nền tảng $platform đang chặn extractor. Thử lại sau hoặc dùng link khác."
                )
            )
        }
        errors.add("oEmbed")

        Result.failure(
            Exception(
                "Không lấy được thông tin video từ $platform.\n" +
                "Đã thử: ${errors.joinToString(", ")}.\n" +
                "Kiểm tra mạng / dùng link công khai."
            )
        )
    }

    fun detectPlatform(url: String): Platform {
        val lower = url.lowercase()
        return when {
            lower.contains("youtube.com") || lower.contains("youtu.be") -> Platform.YOUTUBE
            lower.contains("tiktok.com") || lower.contains("vm.tiktok.com") ||
                lower.contains("vt.tiktok.com") -> Platform.TIKTOK
            lower.contains("instagram.com") || lower.contains("instagr.am") -> Platform.INSTAGRAM
            lower.contains("facebook.com") || lower.contains("fb.watch") -> Platform.FACEBOOK
            lower.contains("twitter.com") || lower.contains("x.com") || lower.contains("t.co") -> Platform.TWITTER
            lower.contains("vimeo.com") -> Platform.VIMEO
            else -> Platform.OTHER
        }
    }

    private fun extractYoutube(url: String): VideoInfo? {
        val videoId = extractYoutubeId(url) ?: return null
        for (base in pipedInstances) {
            try {
                val req = Request.Builder()
                    .url("$base/streams/$videoId")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val json = JsonParser.parseString(body).asJsonObject
                    if (json.has("error")) return@use

                    val title = json.get("title")?.asString ?: "YouTube Video"
                    val uploader = json.get("uploader")?.asString
                    val duration = json.get("duration")?.asLong ?: 0L
                    val thumbnail = json.get("thumbnailUrl")?.asString
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    val qualities = mutableListOf<VideoQuality>()

                    json.getAsJsonArray("videoStreams")?.forEach { el ->
                        val s = el.asJsonObject
                        val streamUrl = s.get("url")?.asString ?: return@forEach
                        val quality = s.get("quality")?.asString
                            ?: s.get("qualityLabel")?.asString
                            ?: "video"
                        val mime = s.get("mimeType")?.asString ?: "video/mp4"
                        val format = if (mime.contains("webm")) "webm" else "mp4"
                        val size = try { s.get("contentLength")?.asLong } catch (_: Exception) { null }
                        val videoOnly = s.get("videoOnly")?.asBoolean == true
                        val fps = try { s.get("fps")?.asInt } catch (_: Exception) { null }
                        qualities.add(
                            VideoQuality(quality, format, size, streamUrl, !videoOnly, fps)
                        )
                    }

                    json.getAsJsonArray("audioStreams")?.forEach { el ->
                        val s = el.asJsonObject
                        val streamUrl = s.get("url")?.asString ?: return@forEach
                        val quality = s.get("quality")?.asString ?: "Audio"
                        val mime = s.get("mimeType")?.asString ?: "audio/mp4"
                        val format = when {
                            mime.contains("webm") -> "webm"
                            else -> "m4a"
                        }
                        val size = try { s.get("contentLength")?.asLong } catch (_: Exception) { null }
                        qualities.add(
                            VideoQuality("Audio • $quality", format, size, streamUrl, true)
                        )
                    }

                    json.get("hls")?.asString?.let { hls ->
                        qualities.add(VideoQuality("HLS adaptive", "m3u8", null, hls, true))
                    }

                    if (qualities.isEmpty()) return@use

                    val sorted = qualities.sortedWith(
                        compareByDescending<VideoQuality> { it.hasAudio }
                            .thenByDescending { parseHeight(it.quality) }
                    )

                    return VideoInfo(
                        title = title,
                        thumbnailUrl = thumbnail,
                        duration = duration,
                        platform = Platform.YOUTUBE,
                        originalUrl = url,
                        author = uploader,
                        qualities = sorted
                    )
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun extractYoutubeId(url: String): String? {
        val patterns = listOf(
            Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/shorts/)([\\w-]{11})"),
            Pattern.compile("youtube\\.com/embed/([\\w-]{11})"),
            Pattern.compile("youtube\\.com/v/([\\w-]{11})")
        )
        for (p in patterns) {
            val m = p.matcher(url)
            if (m.find()) return m.group(1)
        }
        return null
    }

    private fun extractTikTok(url: String): VideoInfo? {
        val encoded = URLEncoder.encode(url, "UTF-8")
        val endpoints = listOf(
            "https://www.tikwm.com/api/?url=$encoded&hd=1",
            "https://tikwm.com/api/?url=$encoded&hd=1"
        )
        for (endpoint in endpoints) {
            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val root = JsonParser.parseString(body).asJsonObject
                    if (root.get("code")?.asInt != 0) return@use
                    val data = root.getAsJsonObject("data") ?: return@use

                    val title = data.get("title")?.asString
                        ?: data.get("id")?.asString
                        ?: "TikTok Video"
                    val authorObj = data.getAsJsonObject("author")
                    val author = authorObj?.get("unique_id")?.asString
                        ?: authorObj?.get("nickname")?.asString
                    val duration = data.get("duration")?.asLong ?: 0L
                    val cover = data.get("cover")?.asString
                        ?: data.get("origin_cover")?.asString

                    val qualities = mutableListOf<VideoQuality>()
                    data.get("hdplay")?.asString?.let {
                        qualities.add(VideoQuality("HD (không watermark)", "mp4", null, it, true))
                    }
                    data.get("play")?.asString?.let {
                        qualities.add(VideoQuality("SD (không watermark)", "mp4", null, it, true))
                    }
                    data.get("wmplay")?.asString?.let {
                        qualities.add(VideoQuality("Có watermark", "mp4", null, it, true))
                    }
                    data.get("music")?.asString?.let {
                        qualities.add(VideoQuality("Audio", "mp3", null, it, true))
                    }

                    if (qualities.isEmpty()) return@use

                    return VideoInfo(
                        title = title,
                        thumbnailUrl = cover,
                        duration = duration,
                        platform = Platform.TIKTOK,
                        originalUrl = url,
                        author = author?.let { if (it.startsWith("@")) it else "@$it" },
                        qualities = qualities
                    )
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun extractCobalt(url: String, platform: Platform): VideoInfo? {
        val qualitiesToTry = listOf("720", "480", "1080", "360")
        for (base in cobaltInstances) {
            for (q in qualitiesToTry) {
                try {
                    val payload = JsonObject().apply {
                        addProperty("url", url)
                        addProperty("videoQuality", q)
                        addProperty("filenameStyle", "basic")
                        addProperty("downloadMode", "auto")
                    }
                    val apiUrl = if (base.endsWith("/")) base else "$base/"
                    val req = Request.Builder()
                        .url(apiUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .post(payload.toString().toRequestBody(jsonMedia))
                        .build()
                    client.newCall(req).execute().use { resp ->
                        val body = resp.body?.string() ?: return@use
                        val json = JsonParser.parseString(body).asJsonObject
                        val status = json.get("status")?.asString ?: return@use

                        when (status) {
                            "tunnel", "redirect" -> {
                                val downloadUrl = json.get("url")?.asString ?: return@use
                                val filename = json.get("filename")?.asString ?: "video.mp4"
                                val title = filename.substringBeforeLast(".").ifBlank { "Video" }
                                val format = filename.substringAfterLast(".", "mp4")
                                return VideoInfo(
                                    title = title,
                                    platform = platform,
                                    originalUrl = url,
                                    qualities = listOf(
                                        VideoQuality("${q}p", format, null, downloadUrl, true)
                                    )
                                )
                            }
                            "picker" -> {
                                val picker = json.getAsJsonArray("picker") ?: return@use
                                val qualities = mutableListOf<VideoQuality>()
                                picker.forEachIndexed { i, el ->
                                    val item = el.asJsonObject
                                    val itemUrl = item.get("url")?.asString ?: return@forEachIndexed
                                    val type = item.get("type")?.asString ?: "video"
                                    qualities.add(
                                        VideoQuality(
                                            if (type == "photo") "Ảnh ${i + 1}" else "Video ${i + 1}",
                                            if (type == "photo") "jpg" else "mp4",
                                            null,
                                            itemUrl,
                                            type != "photo"
                                        )
                                    )
                                }
                                if (qualities.isEmpty()) return@use
                                return VideoInfo(
                                    title = "Media • $platform",
                                    platform = platform,
                                    originalUrl = url,
                                    qualities = qualities
                                )
                            }
                            else -> return@use
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    private fun extractOEmbed(url: String, platform: Platform): VideoInfo? {
        val oembedUrl = when (platform) {
            Platform.YOUTUBE ->
                "https://www.youtube.com/oembed?url=${URLEncoder.encode(url, "UTF-8")}&format=json"
            Platform.VIMEO ->
                "https://vimeo.com/api/oembed.json?url=${URLEncoder.encode(url, "UTF-8")}"
            Platform.TIKTOK ->
                "https://www.tiktok.com/oembed?url=${URLEncoder.encode(url, "UTF-8")}"
            else -> null
        } ?: return null

        return try {
            val req = Request.Builder()
                .url(oembedUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string() ?: return null
                val json = JsonParser.parseString(body).asJsonObject
                val title = json.get("title")?.asString ?: "Video"
                val author = json.get("author_name")?.asString
                val thumb = json.get("thumbnail_url")?.asString
                VideoInfo(
                    title = title,
                    thumbnailUrl = thumb,
                    duration = 0,
                    platform = platform,
                    originalUrl = url,
                    author = author,
                    qualities = emptyList()
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHeight(quality: String): Int {
        val m = Pattern.compile("(\\d{3,4})").matcher(quality)
        return if (m.find()) m.group(1).toIntOrNull() ?: 0 else 0
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
