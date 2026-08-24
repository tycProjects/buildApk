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
    suspend fun ocrCurrentFrame():Result<String> = runCatching{val uri=_editor.value.videoUri?:error("Chưa có video");val r=MediaMetadataRetriever();r.setDataSource(getApplication<Application>(),Uri.parse(uri));val bitmap=r.getFrameAtTime((_editor.value.playheadMs*1000),MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?:error("Không lấy được frame");r.release();val region=_editor.value.blurRegions.firstOrNull()?:BlurCropRegion("ocr",.05f,.05f,.95f,.35f);val text=ocr.recognize(bitmap,floatArrayOf(region.left,region.top,region.right,region.bottom));setEditor{it.copy(subtitleText=text,subtitleEnabled=true,selectedTool="subtitle")};text}
    override fun onCleared(){tts.shutdown();super.onCleared()}
    fun queueRender(){val d=_editor.value;if(d.videoUri==null){_status.value="Chưa có video";return};val id="render_${System.currentTimeMillis()}";viewModelScope.launch{db.renderJobDao().upsert(RenderJobEntity(id,d.videoName,0,"queued","queued"))};val subtitlesJson=if(d.subtitleEnabled&&d.subtitleSegments.isNotEmpty())com.ryan.vietsubai.media.SubtitleSerializer.toJson(d.subtitleSegments) else "";val req=OneTimeWorkRequestBuilder<com.ryan.vietsubai.media.ProcessingWorker>().setInputData(workDataOf("job_id" to id,"project_name" to d.videoName,"source_uri" to d.videoUri,"target_language" to _config.value.subtitle.targetLanguage,"voice" to _config.value.voice,"burn_subtitles" to d.subtitleEnabled,"subtitles_json" to subtitlesJson)).setConstraints(Constraints.Builder().build()).build();WorkManager.getInstance(getApplication()).enqueue(req);_status.value="Đã thêm vào render queue"}
}
