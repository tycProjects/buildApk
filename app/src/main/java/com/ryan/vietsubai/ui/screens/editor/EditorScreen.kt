package com.ryan.vietsubai.ui.screens.editor
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.TextButton
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.vietsubai.editor.EditorController
import com.ryan.vietsubai.ui.VietsubAIViewModel
import com.ryan.vietsubai.ui.theme.BrandCyan
import com.ryan.vietsubai.ui.theme.BrandIndigo
import com.ryan.vietsubai.ui.theme.BrandPink
import com.ryan.vietsubai.ui.theme.EditorBg
import com.ryan.vietsubai.ui.theme.EditorPanel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(vm: VietsubAIViewModel) {
    val draft by vm.editor.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val controller = remember { EditorController(context) }
    val scope = rememberCoroutineScope()
    var aiBusy by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(draft.playheadMs) }
    var duration by remember { mutableLongStateOf(1L) }
    var fitMode by remember { mutableStateOf(true) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { vm.importVideo(it, it.lastPathSegment ?: "Video") }
    }
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(vm::importSrt) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let { vm.addFont(it, it.lastPathSegment ?: "Custom font") } }

    LaunchedEffect(draft.videoUri) { draft.videoUri?.let { controller.load(Uri.parse(it)) } }
    LaunchedEffect(controller) {
        while (true) {
            position = controller.player.currentPosition.coerceAtLeast(0)
            duration = controller.player.duration.takeIf { it > 0 } ?: 1
            delay(100)
        }
    }
    DisposableEffect(Unit) { onDispose { controller.release() } }

    Column(Modifier.fillMaxSize().background(EditorBg)) {
        EditorTopBar(
            title = draft.videoName.ifBlank { "Video Editor" },
            onBack = {},
            onUndo = vm::undoEditor,
            onRedo = vm::redoEditor,
            canUndo = draft.undoDepth > 0,
            canRedo = draft.redoDepth > 0,
            onRender = vm::queueRender,
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (draft.videoUri != null) {
                    EditorPreview(draft, controller, vm, position)
                } else {
                    EmptyPreview { videoPicker.launch(arrayOf("video/*")) }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatTime(position), color = Color.White, fontWeight = FontWeight.Bold)
                MiniTimeline(position, duration) { newPosition ->
                    position = newPosition
                    controller.seek(newPosition)
                    vm.updatePlayhead(newPosition)
                }
                Text(formatTime(duration), color = Color(0xFF8F96A8))
            }

            EditorSeekBar(position, duration) { newPosition ->
                position = newPosition
                controller.seek(newPosition)
                vm.updatePlayhead(newPosition)
            }

            EditorTimelineTracks(draft, duration, position, onSeek = { newPosition ->
                position = newPosition
                controller.seek(newPosition)
                vm.updatePlayhead(newPosition)
            }, vm = vm)

            EditorTransport(
                draft = draft,
                onPlayPause = controller::playPause,
                onSpeed = { value -> vm.setEditor { it.copy(speed = value) }; controller.speed(value) },
                onVolume = { value -> vm.setEditor { it.copy(volume = value) }; controller.volume(value) },
            )

            EditorToolbar(
                selectedTool = draft.selectedTool,
                onTrim = { vm.setEditor { it.copy(selectedTool = "trim") } },
                onText = { vm.setEditor { it.copy(selectedTool = "text") } },
                onSubtitle = { vm.setEditor { it.copy(selectedTool = "subtitle", subtitleEnabled = true) } },
                onAudio = { vm.setEditor { it.copy(selectedTool = "audio") } },
                onBlur = { vm.setEditor { it.copy(selectedTool = "blur") } },
                onFont = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) },
                onSubtitleCrop = { vm.setEditor { it.copy(selectedTool = "subtitleCrop") } },
                onStyle = { vm.setEditor { it.copy(selectedTool = "style") } },
                onCrop = { vm.setEditor { it.copy(selectedTool = "crop") } },
                onUndo = vm::undoEditor,
                onRedo = vm::redoEditor,
            )

            AnimatedContent(
                targetState = draft.selectedTool,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "editorInspector",
            ) { tool ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    color = EditorPanel,
                    shape = RoundedCornerShape(20.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        when (tool) {
                            "trim" -> TrimControls(draft, vm)
                            "subtitle" -> SubtitleEditorPanel(draft, vm, { srtPicker.launch(arrayOf("application/x-subrip", "text/plain", "*/*")) }, aiBusy, { aiBusy = it }, onSeek = { ms -> controller.seek(ms); position = ms; vm.updatePlayhead(ms) }, onOcrFullVideo = { scope.launch { aiBusy = true; vm.ocrFullVideo(); aiBusy = false } }, onRewriteAll = { scope.launch { aiBusy = true; vm.rewriteAll(); aiBusy = false } })
                            "blur" -> BlurControls(draft, vm)
                            "text" -> FontPanel(draft, vm)
                            "subtitleCrop" -> SubtitleCropPanel(draft, vm)
                            "style" -> SubtitleStylePanel(draft, vm)
                            "crop" -> VideoCropPanel(draft, vm)
                            else -> AudioQuickPanel(draft, vm)
                        }
                    }
                }
            }

            RenderQueueMini(vm)

            EditorActionsRow(
                aiBusy = aiBusy,
                onRender = vm::queueRender,
                onOcrFrame = { scope.launch { vm.ocrCurrentFrame() } },
                onTranslateAll = { scope.launch { aiBusy = true; vm.translateAll(); aiBusy = false } },
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun EditorTopBar(title: String, onBack: () -> Unit, onUndo: () -> Unit, onRedo: () -> Unit, canUndo: Boolean, canRedo: Boolean, onRender: () -> Unit) {
    Surface(color = EditorPanel.copy(alpha = .98f)) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("EDITOR", color = Color(0xFF9299AD), fontWeight = FontWeight.Bold, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            IconButton(enabled = canUndo, onClick = onUndo) { Icon(Icons.Default.Undo, null, tint = if (canUndo) Color.White else Color.Gray) }
            IconButton(enabled = canRedo, onClick = onRedo) { Icon(Icons.Default.Redo, null, tint = if (canRedo) Color.White else Color.Gray) }
            IconButton(onClick = onRender) {
                Icon(Icons.Default.FileDownload, null, tint = Color.White)
            }
            IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
        }
    }
}

@Composable
private fun MiniTimeline(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    val progress = if (duration <= 0) 0f else (position.toFloat() / duration).coerceIn(0f, 1f)
    Box(Modifier.width(130.dp).height(12.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF292E3C)).clickable { onSeek((duration * progress).toLong()) }) {
        Box(Modifier.fillMaxWidth(progress).height(12.dp).background(Brush.horizontalGradient(listOf(BrandIndigo, BrandPink, BrandCyan))))
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    return "%02d:%02d".format(total / 60, total % 60)
}

@Composable
private fun AudioQuickPanel(draft: com.ryan.vietsubai.model.EditorDraft, vm: VietsubAIViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Audio", color = Color.White, fontWeight = FontWeight.Bold)
        androidx.compose.material3.Slider(value = draft.volume, onValueChange = { newValue -> vm.setEditor { it.copy(volume = newValue) } }, valueRange = 0f..1f)
        Text("Âm lượng ${(draft.volume * 100).toInt()}%", color = Color(0xFF9CA3B5))
    }
}

@Composable
private fun RenderQueueMini(vm: VietsubAIViewModel) {
    val jobs by vm.renderJobs.collectAsState(initial = emptyList())

    val activeJobs = jobs
        .filter { it.status == "running" || it.status == "queued" }
        .take(2)

    if (activeJobs.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Render queue · ${activeJobs.size}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            TextButton(
                onClick = { vm.cancelRenderQueue() }
            ) {
                Text(
                    text = "Dừng",
                    color = Color(0xFFFF7A9A)
                )
            }
        }

        activeJobs.forEach { job ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.projectName,
                    color = Color(0xFFB8BECE),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "${job.progress}% · ${job.stage}",
                    color = BrandCyan
                )
            }
        }
    }
}
