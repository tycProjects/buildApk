package com.ryan.vietsubai.ui

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.ryan.vietsubai.ai.*
import com.ryan.vietsubai.config.ConfigStore
import com.ryan.vietsubai.data.*
import com.ryan.vietsubai.download.VideoDownloadService
import com.ryan.vietsubai.editor.EditorAutosave
import com.ryan.vietsubai.editor.FontManager
import com.ryan.vietsubai.model.*
import com.ryan.vietsubai.ocr.OcrEngine
import com.ryan.vietsubai.tts.TtsService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VietsubAIViewModel(app: Application) : AndroidViewModel(app) {
    private val db=AppDatabase.get(app); private val configStore=ConfigStore(app); private val downloader=VideoDownloadService(app)
    private val autosave=EditorAutosave(app); private val fontManager=FontManager(app); private val ocr=OcrEngine(); private val tts=TtsService(app)
    private val _tab=MutableStateFlow(AppTab.HOME); val tab=_tab.asStateFlow()
    private val themePrefs = app.getSharedPreferences("ui_theme", android.content.Context.MODE_PRIVATE)
    private val _darkTheme = MutableStateFlow(themePrefs.getBoolean("dark_theme", false))
    val darkTheme = _darkTheme.asStateFlow()
    fun toggleDarkTheme(enabled: Boolean) {
        _darkTheme.value = enabled
        themePrefs.edit().putBoolean("dark_theme", enabled).apply()
    }
    private val _config=MutableStateFlow(AppConfig()); val config=_config.asStateFlow()
    private val _editor=MutableStateFlow(autosave.load() ?: EditorDraft()); val editor=_editor.asStateFlow()
    val projects=db.projectDao().observeAll(); val renderJobs=db.renderJobDao().observeAll(); val downloadJobs=db.downloadJobDao().observeAll()
    private val _downloadMessage=MutableStateFlow(""); val downloadMessage=_downloadMessage.asStateFlow()
    private val _status=MutableStateFlow(""); val status=_status.asStateFlow()
    private val undo=ArrayDeque<EditorDraft>(); private val redo=ArrayDeque<EditorDraft>()
    init{viewModelScope.launch{_config.value=configStore.load()}}
    fun tab(t:AppTab){_tab.value=t}
    fun importVideo(uri:Uri,name:String){
        val app=getApplication<Application>()
        runCatching { app.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        _editor.value=EditorDraft(videoUri=uri.toString(),videoName=name)
        autosave.save(_editor.value)
        undo.clear(); redo.clear(); _tab.value=AppTab.EDITOR
        viewModelScope.launch(Dispatchers.IO) {
            db.projectDao().insert(ProjectEntity(name=name, sourceUri=uri.toString(), status="ready"))
        }
    }
    fun openProject(p:ProjectEntity){importVideo(Uri.parse(p.sourceUri),p.name)}
    fun downloadUrl(url:String){if(url.isBlank()){_downloadMessage.value="Hãy dán URL trước.";return};val direct=url.matches(Regex("https?://[^ ]+\\.(mp4|mov|m4v|webm)(\\?.*)?",RegexOption.IGNORE_CASE));if(direct){val (jobId,downloadId)=downloader.downloadDirect(url);viewModelScope.launch{db.downloadJobDao().upsert(DownloadJobEntity(jobId,url,"Vietsub AI video",0,"downloading",downloadId))};_downloadMessage.value="Đã đưa vào hàng đợi tải."}else{downloader.resolveAndDownload(url,_config.value);_downloadMessage.value="Đang phân giải link nền tảng…"}}
    fun updatePlayhead(ms:Long){_editor.update{it.copy(playheadMs=ms)};autosave.save(_editor.value)}
    fun setEditor(block:(EditorDraft)->EditorDraft){undo.addLast(_editor.value);if(undo.size>60)undo.removeFirst();redo.clear();_editor.update(block);autosave.save(_editor.value)}
    fun undoEditor(){if(undo.isNotEmpty()){redo.addLast(_editor.value);_editor.value=undo.removeLast();autosave.save(_editor.value)}}
    fun redoEditor(){if(redo.isNotEmpty()){undo.addLast(_editor.value);_editor.value=redo.removeLast();autosave.save(_editor.value)}}
    fun saveConfig(c:AppConfig){_config.value=c;viewModelScope.launch{configStore.save(c)}}
    fun testGemini(){viewModelScope.launch{_status.value=runCatching{GeminiClient(_config.value.gemini).generateText("Reply only: OK")}.fold({"✓ Gemini hoạt động: ${it.take(20)}"},{"✗ Gemini lỗi: ${it.message}"})}}
    fun testGroq(){viewModelScope.launch{_status.value=runCatching{GroqClient(_config.value.groq).chat("Reply only: OK")}.fold({"✓ Groq hoạt động: ${it.take(20)}"},{"✗ Groq lỗi: ${it.message}"})}}
    fun saveSubtitleSettings(s:SubtitleSettings){saveConfig(_config.value.copy(subtitle=s))}
    fun addFont(uri:Uri,name:String){viewModelScope.launch{runCatching{fontManager.import(uri,name)}.onSuccess{asset->setEditor{it.copy(fonts=it.fonts+asset,activeFontId=asset.id,selectedTool="text")}}.onFailure{_status.value="Không import được font: ${it.message}"}}}
    fun addBlurRegion(){val r=BlurCropRegion("blur_${System.currentTimeMillis()}",.2f,.2f,.8f,.8f);setEditor{it.copy(blurRegions=it.blurRegions+r,selectedTool="blur")}}
    fun importSrt(uri:Uri){viewModelScope.launch{val raw=withContext(Dispatchers.IO){getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}.orEmpty()};val parsed=com.ryan.vietsubai.subtitle.SrtParser.parse(raw);setEditor{it.copy(subtitleSegments=parsed,subtitleText=parsed.firstOrNull()?.text.orEmpty(),subtitleEnabled=true,selectedTool="subtitle")}}}
    suspend fun translateAll():Result<Int> = runCatching{val s=_config.value.subtitle;val p=SubtitleAiPipeline(GeminiClient(_config.value.gemini),GroqClient(_config.value.groq),TranslationMemory(db.translationMemoryDao()));val result=p.translateSegments(_editor.value.subtitleSegments,s.sourceLanguage,s.targetLanguage,s.translationPrompt);setEditor{it.copy(subtitleSegments=result,subtitleText=result.firstOrNull()?.translation?:result.firstOrNull()?.text.orEmpty())};result.size}
    suspend fun geminiTranslate(text:String):Result<String>{val s=_config.value.subtitle;val mem=TranslationMemory(db.translationMemoryDao());return runCatching{mem.get(text,s.sourceLanguage,s.targetLanguage,s.translationPrompt)?:GeminiClient(_config.value.gemini).translate(text,s.targetLanguage,s.translationPrompt).also{mem.put(text,it,s.sourceLanguage,s.targetLanguage,"gemini",s.translationPrompt)}}}
    suspend fun groqRewrite(text:String):Result<String> = runCatching{GroqClient(_config.value.groq).chat("Rewrite this subtitle naturally in ${_config.value.subtitle.targetLanguage}, concise for dubbing. Return only the line.\n$text")}
    fun syncCurrentTts(){val d=_editor.value;val s=d.subtitleSegments.getOrNull(d.selectedSubtitleIndex)?:return;val plan=tts.sync(s.translation?:s.text,(s.start*1000).toLong(),(s.end*1000).toLong());setEditor{it.copy(ttsRate=plan.playbackRate,ttsPadMs=plan.padMs,ttsSyncLabel="${plan.playbackRate}x · pad ${plan.padMs}ms")}}
    suspend fun ocrCurrentFrame():Result<String> = runCatching{val uri=_editor.value.videoUri?:error("Chưa có video");val r=MediaMetadataRetriever();r.setDataSource(getApplication<Application>(),Uri.parse(uri));val bitmap=r.getFrameAtTime((_editor.value.playheadMs*1000),MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?:error("Không lấy được frame");r.release();val region=_editor.value.blurRegions.firstOrNull()?:BlurCropRegion("ocr",.05f,.05f,.95f,.35f);val text=ocr.recognize(bitmap,floatArrayOf(region.left,region.top,region.right,region.bottom));val start=_editor.value.playheadMs/1000.0;val end=start+2.5;val idx=_editor.value.subtitleSegments.indexOfFirst{segment -> start>=segment.start&&start<=segment.end};setEditor{editor -> val list=editor.subtitleSegments.toMutableList();if(idx>=0){list[idx]=list[idx].copy(text=text,translation=text)}else{list.add(SubtitleSegment(start,end,text,text))};editor.copy(subtitleText=text,subtitleEnabled=true,selectedTool="subtitle",subtitleSegments=list,selectedSubtitleIndex=if(idx>=0)idx else list.lastIndex)};text}

    fun updateSubtitleSegment(index: Int, start: Double, end: Double, text: String? = null, translation: String? = null) {
        setEditor { d ->
            val list = d.subtitleSegments.toMutableList()
            if (index !in list.indices) return@setEditor d
            val old = list[index]
            list[index] = old.copy(start = start.coerceAtLeast(0.0), end = end.coerceAtLeast(start + .05), text = text ?: old.text, translation = translation ?: old.translation)
            d.copy(subtitleSegments = list)
        }
    }

    fun splitSelectedSubtitle(atMs: Long) {
        val d = _editor.value
        val i = d.selectedSubtitleIndex
        val old = d.subtitleSegments.getOrNull(i) ?: return
        val at = atMs / 1000.0
        if (at <= old.start + .05 || at >= old.end - .05) return
        val a = old.copy(end = at)
        val b = old.copy(start = at)
        setEditor { it.copy(subtitleSegments = it.subtitleSegments.toMutableList().also { list -> list[i] = a; list.add(i + 1, b) }, selectedSubtitleIndex = i + 1, subtitleText = b.translation ?: b.text) }
    }

    fun mergeSelectedWithNext() {
        val d = _editor.value
        val i = d.selectedSubtitleIndex
        if (i !in 0 until d.subtitleSegments.lastIndex) return
        val a = d.subtitleSegments[i]; val b = d.subtitleSegments[i + 1]
        val merged = a.copy(end = b.end, text = (a.text + " " + b.text).trim(), translation = listOfNotNull(a.translation, b.translation).joinToString(" ").ifBlank { null })
        setEditor { it.copy(subtitleSegments = it.subtitleSegments.toMutableList().also { list -> list[i] = merged; list.removeAt(i + 1) }, subtitleText = merged.translation ?: merged.text) }
    }

    fun autoSyncSubtitles() {
        val d = _editor.value
        val items = d.subtitleSegments
        if (items.isEmpty()) return
        val synced = items.mapIndexed { index, item ->
            val nextStart = items.getOrNull(index + 1)?.start ?: item.end
            val desired = ((item.translation ?: item.text).length / 13.0).coerceIn(.8, 6.0)
            item.copy(end = (item.start + desired).coerceAtMost(nextStart.coerceAtLeast(item.start + .15)))
        }
        setEditor { it.copy(subtitleSegments = synced) }
    }

    suspend fun rewriteAll(): Result<Int> = runCatching {
        val items = _editor.value.subtitleSegments
        val result = items.map { item -> item.copy(translation = GroqClient(_config.value.groq).chat("Rewrite this subtitle naturally in ${_config.value.subtitle.targetLanguage}, concise, preserve names and meaning. Return only the line.\n${item.translation ?: item.text}")) }
        setEditor { it.copy(subtitleSegments = result, subtitleText = result.firstOrNull()?.translation ?: it.subtitleText) }
        result.size
    }

    suspend fun ocrFullVideo(): Result<Int> = runCatching { withContext(Dispatchers.IO) {
        val d = _editor.value
        val uri = d.videoUri ?: error("Chưa có video")
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(getApplication<Application>(), Uri.parse(uri))
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val step = (1000.0 / d.ocrScanFps.coerceIn(.5f, 10f)).toLong().coerceAtLeast(120)
        val found = mutableListOf<SubtitleSegment>()
        var t = 0L
        while (t < duration) {
            val bitmap = retriever.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                val region = d.subtitleRegion
                val text = ocr.recognize(bitmap, floatArrayOf(region.left, region.top, region.right, region.bottom)).trim()
                if (text.isNotBlank()) {
                    val start = t / 1000.0
                    val last = found.lastOrNull()
                    if (d.ocrAutoMerge && last != null && last.text.equals(text, ignoreCase = true) && start - last.end < 1.2) {
                        found[found.lastIndex] = last.copy(end = (start + step / 1000.0).coerceAtMost(duration / 1000.0))
                    } else {
                        found += SubtitleSegment(start, (start + step / 1000.0).coerceAtMost(duration / 1000.0), text, text)
                    }
                }
                bitmap.recycle()
            }
            t += step
        }
        retriever.release()
        setEditor { it.copy(subtitleSegments = found, subtitleText = found.firstOrNull()?.text.orEmpty(), subtitleEnabled = found.isNotEmpty(), selectedSubtitleIndex = 0, selectedTool = "subtitle") }
        found.size
    }}

    override fun onCleared(){tts.shutdown();super.onCleared()}
    fun cancelRenderQueue(){ WorkManager.getInstance(getApplication()).cancelUniqueWork("render_queue"); viewModelScope.launch { db.renderJobDao().observeAll().first().filter { it.status == "queued" || it.status == "running" }.forEach { db.renderJobDao().upsert(it.copy(status="cancelled", stage="cancelled")) } }; _status.value="Đã dừng render queue" }
    fun queueRender(){val d=_editor.value;if(d.videoUri==null){_status.value="Chưa có video";return};val id="render_${System.currentTimeMillis()}";viewModelScope.launch{db.renderJobDao().upsert(RenderJobEntity(id,d.videoName,0,"queued","queued"))};val subtitlesJson=if(d.subtitleEnabled&&d.subtitleSegments.isNotEmpty())com.ryan.vietsubai.media.SubtitleSerializer.toJson(d.subtitleSegments) else "";val fontPath=d.fonts.firstOrNull{it.id==d.activeFontId}?.uri.orEmpty();val req=OneTimeWorkRequestBuilder<com.ryan.vietsubai.media.ProcessingWorker>().setInputData(workDataOf("job_id" to id,"project_name" to d.videoName,"source_uri" to d.videoUri,"trim_start" to d.trimStartMs,"trim_end" to d.trimEndMs,"target_language" to _config.value.subtitle.targetLanguage,"voice" to _config.value.voice,"burn_subtitles" to d.subtitleEnabled,"remove_original_subtitle" to d.removeOriginalSubtitle,"subtitles_json" to subtitlesJson,"font_uri" to fontPath,"subtitle_left" to d.subtitleRegion.left,"subtitle_top" to d.subtitleRegion.top,"subtitle_right" to d.subtitleRegion.right,"subtitle_bottom" to d.subtitleRegion.bottom,
            "crop_preset" to d.crop.preset, "crop_zoom" to d.crop.zoom,
            "style_font_size" to d.subtitleStyle.fontSize, "style_text_color" to d.subtitleStyle.textColor,
            "style_stroke_color" to d.subtitleStyle.strokeColor, "style_stroke_width" to d.subtitleStyle.strokeWidth,
            "style_shadow" to d.subtitleStyle.shadow, "style_background_color" to d.subtitleStyle.backgroundColor,
            "style_background_radius" to d.subtitleStyle.backgroundRadius, "style_align" to d.subtitleStyle.align,
            "style_bold" to d.subtitleStyle.bold, "style_italic" to d.subtitleStyle.italic,
            "style_line_spacing" to d.subtitleStyle.lineSpacing, "style_letter_spacing" to d.subtitleStyle.letterSpacing,
            "style_animation" to d.subtitleStyle.animation, "style_animation_ms" to d.subtitleStyle.animationMs,
            "hardware_acceleration" to d.renderHardwareAcceleration, "output_fps" to d.renderOutputFps)).setConstraints(Constraints.Builder().build()).build();WorkManager.getInstance(getApplication()).beginUniqueWork("render_queue", ExistingWorkPolicy.APPEND_OR_REPLACE, req).enqueue();_status.value="Đã thêm vào render queue"}
}
