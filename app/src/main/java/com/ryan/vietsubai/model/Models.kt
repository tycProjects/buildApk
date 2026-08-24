package com.ryan.vietsubai.model

data class SubtitleSegment(val start: Double, val end: Double, val text: String, val translation: String? = null)
enum class SubtitleSource { SRT_TRANSLATED, SRT_ORIGINAL, OCR, STT }

data class SubtitleSettings(
    val source: SubtitleSource = SubtitleSource.STT,
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "vi",
    val translationPrompt: String = "Dịch tự nhiên, đúng ngữ cảnh, giữ nguyên ý nghĩa và độ dài phù hợp để lồng tiếng."
)

data class FontAsset(val id: String, val name: String, val uri: String, val family: String? = null)

data class SubtitleStyle(
    val fontSize: Float = 20f,
    val textColor: Long = 0xFFFFFFFF,
    val strokeColor: Long = 0xFF000000,
    val strokeWidth: Float = 3f,
    val shadow: Float = 6f,
    val backgroundColor: Long = 0x00000000,
    val backgroundRadius: Float = 12f,
    val align: Int = 1,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val lineSpacing: Float = 1.2f,
    val letterSpacing: Float = 0f,
    val animation: String = "fade",
    val animationMs: Int = 180,
)

data class CropSettings(
    val preset: String = "original",
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
)
data class BlurCropRegion(val id: String, val left: Float, val top: Float, val right: Float, val bottom: Float, val startMs: Long = 0, val endMs: Long = Long.MAX_VALUE, val strength: Float = 0.7f)

data class AiProviderConfig(val id: String,val name: String,val baseUrl:String="",val apiKey:String="",val model:String="",val enabled:Boolean=true)
data class AppConfig(
    val gemini:AiProviderConfig=AiProviderConfig("gemini","Gemini","https://generativelanguage.googleapis.com","","gemini-2.5-flash"),
    val groq:AiProviderConfig=AiProviderConfig("groq","Groq","https://api.groq.com/openai/v1","","llama-3.3-70b-versatile"),
    val stt:AiProviderConfig=AiProviderConfig("stt","Groq STT","https://api.groq.com/openai/v1","","whisper-large-v3-turbo"),
    val tts:AiProviderConfig=AiProviderConfig("tts","TTS"), val ocr:AiProviderConfig=AiProviderConfig("ocr","OCR"),
    val mediaResolverUrl:String="", val targetLanguage:String="vi", val voice:String="vi-VN-HoaiMyNeural",
    val subtitle:SubtitleSettings=SubtitleSettings()
)

data class ProjectSettings(val targetLanguage:String="vi",val sourceLanguage:String="auto",val subtitleSource:SubtitleSource=SubtitleSource.STT,val translationProvider:String="gemini",val translationStyle:String="natural",val voice:String="vi-VN-HoaiMyNeural",val ttsProvider:String="edge",val ttsRate:Float=1f,val keepOriginalVolume:Float=0.05f,val burnSubtitles:Boolean=false,val exportMode:String="mp4",val glossary:String="",val customPrompt:String="")
data class Project(val id:Long=0,val name:String,val sourceUri:String,val outputUri:String?=null,val createdAt:Long=System.currentTimeMillis(),val status:String="ready")
data class JobState(val id:String,val progress:Int=0,val message:String="",val status:String="queued")
enum class AppTab { HOME, EDITOR, CONFIG }
data class EditorDraft(val videoUri:String?=null,val videoName:String="",val trimStartMs:Long=0,val trimEndMs:Long=0,val speed:Float=1f,val volume:Float=1f,val originalAudio:Float=1f,val subtitleEnabled:Boolean=false,val subtitleText:String="",val exportMode:String="mp4",val playheadMs:Long=0,val selectedTool:String="trim",val undoDepth:Int=0,val redoDepth:Int=0,val fonts:List<FontAsset> = emptyList(),val activeFontId:String? = null,val blurRegions:List<BlurCropRegion> = emptyList(),val subtitleRegion:BlurCropRegion = BlurCropRegion("subtitle",.08f,.72f,.92f,.96f),val subtitleSegments:List<SubtitleSegment> = emptyList(),val selectedSubtitleIndex:Int = 0,val ocrScanFps:Float = 2f,val ocrAutoMerge:Boolean = true,val ttsRate:Float = 1f,val ttsPadMs:Long = 0L,val ttsSyncLabel:String = "Chưa đồng bộ",
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    val crop: CropSettings = CropSettings(),
    val renderHardwareAcceleration: Boolean = true,
    val renderOutputFps: Int = 30,
    val autoOcr: Boolean = false,
    val removeOriginalSubtitle: Boolean = false)
