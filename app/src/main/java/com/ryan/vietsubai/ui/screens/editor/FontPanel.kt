package com.ryan.vietsubai.ui.screens.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.ryan.vietsubai.model.EditorDraft
import com.ryan.vietsubai.ui.VietsubAIViewModel

@Composable
fun FontPanel(draft: EditorDraft, vm: VietsubAIViewModel) {
    Column {
        Text("Font custom", color = Color.White)
        if (draft.fonts.isEmpty()) {
            Text("Chưa có font. Dùng nút Font để import TTF/OTF.", color = Color.Gray)
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                draft.fonts.forEach { font ->
                    FilterChip(
                        selected = draft.activeFontId == font.id,
                        onClick = { vm.setEditor { it.copy(activeFontId = font.id) } },
                        label = { Text(font.name) },
                    )
                }
            }
        }
    }
}


@Composable
fun SubtitleCropPanel(draft: EditorDraft, vm: VietsubAIViewModel) {
    val r = draft.subtitleRegion
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Vùng hiển thị phụ đề", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Kéo vùng cyan trực tiếp trên preview để đặt phụ đề.", color = Color.Gray)
        androidx.compose.material3.Switch(checked = draft.removeOriginalSubtitle, onCheckedChange = { v -> vm.setEditor { it.copy(removeOriginalSubtitle = v) } })
        Text(if (draft.removeOriginalSubtitle) "Đang che subtitle gốc khi render" else "Giữ subtitle gốc", color = Color.LightGray)
        CropSlider("Trái", r.left) { v -> vm.setEditor { it.copy(subtitleRegion = r.copy(left = v.coerceAtMost(r.right - .08f))) } }
        CropSlider("Trên", r.top) { v -> vm.setEditor { it.copy(subtitleRegion = r.copy(top = v.coerceAtMost(r.bottom - .08f))) } }
        CropSlider("Phải", r.right) { v -> vm.setEditor { it.copy(subtitleRegion = r.copy(right = v.coerceAtLeast(r.left + .08f))) } }
        CropSlider("Dưới", r.bottom) { v -> vm.setEditor { it.copy(subtitleRegion = r.copy(bottom = v.coerceAtLeast(r.top + .08f))) } }
    }
}

@Composable
private fun CropSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$label ${(value * 100).toInt()}%", color = Color.LightGray, modifier = Modifier.width(62.dp))
        androidx.compose.material3.Slider(value = value, onValueChange = onValueChange, valueRange = 0f..1f, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SubtitleStylePanel(draft: EditorDraft, vm: VietsubAIViewModel) {
    val s = draft.subtitleStyle
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Kiểu phụ đề", color = Color.White, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(s.bold, { vm.setEditor { it.copy(subtitleStyle = s.copy(bold = !s.bold)) } }, label = { Text("Đậm") })
            FilterChip(s.italic, { vm.setEditor { it.copy(subtitleStyle = s.copy(italic = !s.italic)) } }, label = { Text("Nghiêng") })
            FilterChip(s.align == 0, { vm.setEditor { it.copy(subtitleStyle = s.copy(align = 0)) } }, label = { Text("Trái") })
            FilterChip(s.align == 1, { vm.setEditor { it.copy(subtitleStyle = s.copy(align = 1)) } }, label = { Text("Giữa") })
            FilterChip(s.align == 2, { vm.setEditor { it.copy(subtitleStyle = s.copy(align = 2)) } }, label = { Text("Phải") })
        }
        StyleSlider("Cỡ chữ", s.fontSize, 12f..48f) { v -> vm.setEditor { it.copy(subtitleStyle = s.copy(fontSize = v)) } }
        StyleSlider("Viền", s.strokeWidth, 0f..10f) { v -> vm.setEditor { it.copy(subtitleStyle = s.copy(strokeWidth = v)) } }
        StyleSlider("Shadow", s.shadow, 0f..16f) { v -> vm.setEditor { it.copy(subtitleStyle = s.copy(shadow = v)) } }
        StyleSlider("Khoảng chữ", s.letterSpacing, 0f..0.2f) { v -> vm.setEditor { it.copy(subtitleStyle = s.copy(letterSpacing = v)) } }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Không" to "none", "Fade" to "fade", "Pop" to "pop", "Slide" to "slide").forEach { (label, key) ->
                FilterChip(s.animation == key, { vm.setEditor { it.copy(subtitleStyle = s.copy(animation = key)) } }, label = { Text(label) })
            }
        }
        Text("Màu chữ / viền: trắng + đen mặc định. Preset nâng cao có thể thay đổi tiếp.", color = Color.Gray, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun StyleSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("$label ${"%.1f".format(value)}", color = Color.LightGray, modifier = Modifier.width(92.dp))
        androidx.compose.material3.Slider(value = value, onValueChange = onValueChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

@Composable
fun VideoCropPanel(draft: EditorDraft, vm: VietsubAIViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Crop video", color = Color.White, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("Original" to "original", "16:9" to "16:9", "9:16" to "9:16", "1:1" to "1:1", "4:5" to "4:5").forEach { (label, key) ->
                FilterChip(draft.crop.preset == key, { vm.setEditor { it.copy(crop = it.crop.copy(preset = key)) } }, label = { Text(label) })
            }
        }
        StyleSlider("Zoom", draft.crop.zoom, 1f..2f) { v -> vm.setEditor { it.copy(crop = it.crop.copy(zoom = v)) } }
        Text("Kéo trực tiếp preview để căn vùng crop.", color = Color.Gray)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            androidx.compose.material3.Switch(checked = draft.renderHardwareAcceleration, onCheckedChange = { v -> vm.setEditor { it.copy(renderHardwareAcceleration = v) } })
            Text("Hardware acceleration khi render", color = Color.LightGray)
        }
        StyleSlider("FPS", draft.renderOutputFps.toFloat(), 24f..60f) { v -> vm.setEditor { it.copy(renderOutputFps = v.roundToInt()) } }
    }
}
