package com.ryan.videodownload.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ryan.videodownload.data.model.DownloadStatus
import com.ryan.videodownload.ui.MainViewModel
import com.ryan.videodownload.ui.components.DownloadTaskCard
import com.ryan.videodownload.ui.components.GradientButton
import com.ryan.videodownload.ui.components.QualityBottomSheet
import com.ryan.videodownload.ui.components.UrlInputField
import com.ryan.videodownload.ui.components.VideoInfoCard
import com.ryan.videodownload.ui.theme.BackgroundGradient
import com.ryan.videodownload.ui.theme.DarkBackground
import com.ryan.videodownload.ui.theme.GradientEnd
import com.ryan.videodownload.ui.theme.GradientStart
import com.ryan.videodownload.ui.theme.TextMuted
import com.ryan.videodownload.ui.theme.TextPrimary
import com.ryan.videodownload.ui.theme.TextSecondary

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    // Full-screen player khi đang xem video
    if (state.playingTask != null) {
        VideoPlayerScreen(
            task = state.playingTask!!,
            onClose = viewModel::closePlayer
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundGradient)
                .padding(padding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Column {
                        Text(
                            text = "Tải Video",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tải video đa nền tảng • Nhanh & Đẹp",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }

                item {
                    UrlInputField(
                        value = state.urlInput,
                        onValueChange = viewModel::onUrlChange,
                        onSubmit = viewModel::analyzeUrl
                    )
                }

                item {
                    GradientButton(
                        text = if (state.isAnalyzing) "Đang phân tích…" else "Phân tích & Tải",
                        onClick = viewModel::analyzeUrl,
                        isLoading = state.isAnalyzing,
                        enabled = state.urlInput.isNotBlank() && !state.isAnalyzing
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        GradientStart.copy(alpha = 0.15f),
                                        GradientEnd.copy(alpha = 0.15f)
                                    )
                                )
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Hỗ trợ: YouTube • TikTok • Instagram • Facebook • X/Twitter • Vimeo…\nVideo tải xong sẽ tự lưu vào album máy (Movies/VideoDownloader)",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = state.videoInfo != null,
                        enter = fadeIn() + slideInVertically(),
                        exit = fadeOut()
                    ) {
                        state.videoInfo?.let { info ->
                            VideoInfoCard(
                                title = info.title,
                                author = info.author,
                                duration = info.durationFormatted(),
                                platform = info.platform,
                                thumbnailUrl = info.thumbnailUrl
                            )
                        }
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = state.currentTask?.status == DownloadStatus.DOWNLOADING
                    ) {
                        state.currentTask?.let { task ->
                            Column {
                                Text(
                                    text = "Đang tải xuống",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                DownloadTaskCard(task)
                            }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Lịch sử tải",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                val historyItems = state.history.filter {
                    it.status == DownloadStatus.COMPLETED || it.status == DownloadStatus.FAILED
                }

                if (historyItems.isEmpty()) {
                    item { EmptyHistory() }
                } else {
                    items(historyItems, key = { it.id }) { task ->
                        DownloadTaskCard(
                            task = task,
                            onPlayClick = viewModel::playVideo
                        )
                    }
                }
            }
        }
    }

    if (state.showQualitySheet && state.videoInfo != null) {
        QualityBottomSheet(
            videoInfo = state.videoInfo!!,
            selectedQuality = state.selectedQuality,
            onSelect = viewModel::selectQuality,
            onDownload = viewModel::startDownload,
            onDismiss = viewModel::dismissQualitySheet
        )
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Chưa có video nào được tải",
            color = TextMuted,
            fontSize = 14.sp
        )
    }
}
