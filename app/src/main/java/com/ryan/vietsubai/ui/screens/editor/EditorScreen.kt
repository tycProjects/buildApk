package com.ryan.vietsubai.ui.screens.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.vietsubai.editor.EditorController
import com.ryan.vietsubai.ui.VietsubAIViewModel
import com.ryan.vietsubai.ui.theme.EditorBg
import com.ryan.vietsubai.ui.theme.EditorPanel
import com.ryan.vietsubai.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(vm: VietsubAIViewModel) {
    val draft by vm.editor.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val controller = remember { EditorController(context) }
    val scope = rememberCoroutineScope()

    var aiBusy by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(draft.playheadMs) }
    var duration by remember { mutableLongStateOf(1L) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { vm.importVideo(it, it.lastPathSegment ?: "Video") }
    }
    val srtPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(vm::importSrt)
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { vm.addFont(it, it.lastPathSegment ?: "Custom font") }
    }

    LaunchedEffect(draft.videoUri) {
        draft.videoUri?.let { controller.load(Uri.parse(it)) }
    }
    LaunchedEffect(controller) {
        while (true) {
            position = controller.player.currentPosition
            duration = controller.player.duration.takeIf { it > 0 } ?: 1
            delay(150)
        }
    }
    DisposableEffect(Unit) { onDispose { controller.release() } }

    Column(Modifier.fillMaxSize().background(EditorBg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (draft.videoUri != null) {
                EditorPreview(draft = draft, controller = controller, vm = vm)
            } else {
                EmptyPreview(onImport = { videoPicker.launch(arrayOf("video/*")) })
            }
        }

        EditorToolbar(
            onTrim = { vm.setEditor { it.copy(selectedTool = "trim") } },
            onText = { vm.setEditor { it.copy(selectedTool = "text") } },
            onSubtitle = { vm.setEditor { it.copy(selectedTool = "subtitle", subtitleEnabled = true) } },
            onAudio = { vm.setEditor { it.copy(selectedTool = "audio") } },
            onBlur = { vm.setEditor { it.copy(selectedTool = "blur") } },
            onFont = { fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/octet-stream")) },
            onUndo = vm::undoEditor,
            onRedo = vm::redoEditor,
        )

        Column(
            modifier = Modifier.background(EditorPanel).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(draft.videoName.ifBlank { "Editor" }, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${position / 1000}s / ${duration / 1000}s", color = Color.Gray)
            }

            EditorSeekBar(position = position, duration = duration) { newPosition ->
                position = newPosition
                controller.seek(newPosition)
                vm.updatePlayhead(newPosition)
            }

            EditorTimelineTracks(draft = draft)

            EditorTransport(
                draft = draft,
                onPlayPause = controller::playPause,
                onSpeed = { value -> vm.setEditor { it.copy(speed = value) }; controller.speed(value) },
                onVolume = { value -> vm.setEditor { it.copy(volume = value) }; controller.volume(value) },
            )

            AnimatedContent(
                targetState = draft.selectedTool,
                transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST)) },
                label = "toolPanel",
            ) { tool ->
                when (tool) {
                    "trim" -> TrimControls(draft = draft, vm = vm)
                    "subtitle" -> SubtitleEditorPanel(
                        draft = draft,
                        vm = vm,
                        onImportSrt = { srtPicker.launch(arrayOf("application/x-subrip", "text/plain", "*/*")) },
                        aiBusy = aiBusy,
                        setBusy = { aiBusy = it },
                    )
                    "blur" -> BlurControls(draft = draft, vm = vm)
                    "text" -> FontPanel(draft = draft, vm = vm)
                    else -> Unit
                }
            }

            EditorActionsRow(
                aiBusy = aiBusy,
                onRender = vm::queueRender,
                onOcrFrame = { scope.launch { vm.ocrCurrentFrame() } },
                onTranslateAll = {
                    scope.launch {
                        aiBusy = true
                        vm.translateAll()
                        aiBusy = false
                    }
                },
            )
        }
    }
}
