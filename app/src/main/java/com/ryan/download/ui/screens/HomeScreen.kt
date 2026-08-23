package com.ryan.download.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ryan.download.data.DownloadState
import com.ryan.download.ui.components.*
import com.ryan.download.ui.theme.LuxGold
import com.ryan.download.viewmodel.DownloadViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    initialUrl: String? = null,
    onNavigateToHistory: () -> Unit,
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(initialUrl) {
        if (!initialUrl.isNullOrBlank()) {
            viewModel.updateUrl(initialUrl)
            viewModel.analyzeUrl()
        }
    }

    LuxGradientBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Tai Video", fontWeight = FontWeight.Bold, color = LuxGold) },
                    actions = {
                        IconButton(onClick = onNavigateToHistory) {
                            Icon(Icons.Default.History, "Lịch sử", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp).verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(12.dp))
                Text("Tải video đa nền tảng", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text("YouTube • TikTok • Instagram • X • Facebook…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(28.dp))

                UrlInputField(
                    value = uiState.url,
                    onValueChange = viewModel::updateUrl,
                    onPaste = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        viewModel.updateUrl(text)
                    }
                )
                Spacer(Modifier.height(16.dp))

                LuxButton(
                    text = if (uiState.downloadState == DownloadState.ANALYZING) "Đang phân tích…" else "Phân tích liên kết",
                    onClick = viewModel::analyzeUrl,
                    modifier = Modifier.fillMaxWidth(),
                    loading = uiState.downloadState == DownloadState.ANALYZING,
                    enabled = uiState.url.isNotBlank() && uiState.downloadState != DownloadState.ANALYZING,
                    icon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) }
                )

                AnimatedVisibility(uiState.errorMessage != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = MaterialTheme.shapes.medium) {
                        Text(uiState.errorMessage ?: "", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }

                AnimatedVisibility(
                    uiState.videoInfo != null,
                    enter = fadeIn() + slideInVertically({ it / 3 }, spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(24.dp))
                        uiState.videoInfo?.let {
                            VideoPreviewCard(it.title, it.uploader, it.thumbnail, it.duration)
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("Chọn chất lượng", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(uiState.videoInfo?.formats ?: emptyList()) { f ->
                                FormatChip(f, uiState.selectedFormat == f) { viewModel.selectFormat(f) }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        when (uiState.downloadState) {
                            DownloadState.DOWNLOADING -> ProgressCard(uiState.progress, "Đang tải xuống (tối ưu tốc độ)…")
                            DownloadState.COMPLETED -> LuxCard(Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Download, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Tải thành công!", style = MaterialTheme.typography.titleMedium, color = Color.White)
                                        Text("Đã lưu vào Downloads/TaiVideo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                OutlinedButton(onClick = viewModel::reset, modifier = Modifier.fillMaxWidth()) { Text("Tải video khác") }
                            }
                            else -> LuxButton(
                                "Tải xuống", viewModel::startDownload, Modifier.fillMaxWidth(),
                                enabled = uiState.selectedFormat != null,
                                icon = { Icon(Icons.Default.Download, null, Modifier.size(20.dp)) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Hỗ trợ 1000+ trang • Tối ưu tốc độ với aria2c", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
