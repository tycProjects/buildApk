package com.ducknovis.discautoquests

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ducknovis.discautoquests.data.QuestStatus
import com.ducknovis.discautoquests.data.Session
import com.ducknovis.discautoquests.ui.DaqTheme
import com.ducknovis.discautoquests.ui.MainViewModel
import com.ducknovis.discautoquests.ui.QuestUiItem
import com.ducknovis.discautoquests.ui.UiState

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* ignore; FGS still works, notification may be limited */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            DaqTheme {
                val state by viewModel.state.collectAsState()
                MainScreen(
                    state = state,
                    onDismissWarning = viewModel::dismissWarning,
                    onTokenChange = viewModel::onTokenChange,
                    onAddSession = viewModel::addSession,
                    onSelectSession = viewModel::selectSession,
                    onToggleStartStop = viewModel::toggleStartStop
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun MainScreen(
    state: UiState,
    onDismissWarning: () -> Unit,
    onTokenChange: (String) -> Unit,
    onAddSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onToggleStartStop: () -> Unit
) {
    val context = LocalContext.current
    val isRunning = state.runningSessionId != null && state.runningSessionId == state.activeSessionId

    if (state.showWarning) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text("Cảnh báo & Trách nhiệm", color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Dùng user token và auto quest có thể vi phạm ToS Discord. Bạn tự chịu trách nhiệm về mọi rủi ro (ban tài khoản, mất dữ liệu, v.v.). Không chia sẻ token.",
                        color = Color(0xFFF59E0B),
                        fontSize = 13.sp
                    )
                    Text(
                        "Official: https://github.com/Nguoibianhz/Discord-Auto-Quests",
                        color = Color(0xFF60A5FA),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Nguoibianhz/Discord-Auto-Quests"))
                            )
                        }
                    )
                    Text(
                        "Android: https://github.com/ducknogit/discord-auto-quests-mobile",
                        color = Color(0xFF60A5FA),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ducknogit/discord-auto-quests-mobile"))
                            )
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissWarning) {
                    Text("Tôi hiểu", color = Color(0xFFE5E7EB), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF0A0A0A)
        )
    }

    Scaffold(
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column {
                Text(
                    "Discord Auto Quest Android",
                    color = Color(0xFFE5E7EB),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Native Android · Make by ducknovis · official by hieudz",
                    color = Color(0xFFE0E7FF),
                    fontSize = 13.sp
                )
            }

            CardBox {
                Text("Discord User Token", color = Color(0xFFE5E7EB), fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = state.tokenInput,
                    onValueChange = onTokenChange,
                    enabled = !state.tokenLocked,
                    placeholder = { Text("dMh8...your token", color = Color(0xFF5F6B86)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1F1F1F),
                        unfocusedBorderColor = Color(0xFF1F1F1F),
                        focusedTextColor = Color(0xFFE5E7EB),
                        unfocusedTextColor = Color(0xFFE5E7EB),
                        cursorColor = Color(0xFF38BDF8),
                        focusedContainerColor = Color(0xFF111111),
                        unfocusedContainerColor = Color(0xFF111111)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BlackButton(
                        text = "Thêm session",
                        onClick = onAddSession,
                        modifier = Modifier.weight(1f)
                    )
                    BlackButton(
                        text = if (isRunning) "Stop" else "Start",
                        onClick = onToggleStartStop,
                        enabled = state.sessions.isNotEmpty() || state.tokenInput.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardBox(modifier = Modifier.weight(1f)) {
                    Text("Sessions", color = Color(0xFFE5E7EB), fontWeight = FontWeight.SemiBold)
                    if (state.sessions.isEmpty()) {
                        Text("Chưa có session", color = Color.White, fontSize = 12.sp)
                    }
                    state.sessions.forEach { session ->
                        SessionItem(
                            session = session,
                            active = session.id == state.activeSessionId,
                            onClick = { onSelectSession(session.id) }
                        )
                    }
                }
                CardBox(modifier = Modifier.weight(1f)) {
                    Text("Orbs", color = Color(0xFFE5E7EB), fontWeight = FontWeight.SemiBold)
                    Text(
                        state.orbs?.toString() ?: "...",
                        color = Color(0xFFFBBF24),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            CardBox {
                Text("Quests", color = Color(0xFFE5E7EB), fontWeight = FontWeight.SemiBold)
                val quests = state.activeSessionId?.let { state.questsBySession[it] }.orEmpty()
                if (quests.isEmpty()) {
                    Text("Chưa có quest nào", color = Color.White, fontSize = 12.sp)
                } else {
                    quests.forEach { q -> QuestRow(q) }
                }
            }

            CardBox(modifier = Modifier.heightIn(max = 260.dp)) {
                Text("Logs", color = Color(0xFFE5E7EB), fontWeight = FontWeight.SemiBold)
                val logs = state.activeSessionId?.let { state.logsBySession[it] }.orEmpty()
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(logs) { line ->
                        Text(line, color = Color(0xFFE5E7EB), fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A0A0A))
            .border(1.dp, Color(0xFF1F1F1F), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        content()
    }
}

@Composable
private fun BlackButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Black,
            contentColor = Color(0xFFE5E7EB),
            disabledContainerColor = Color(0xFF111111),
            disabledContentColor = Color(0xFF666666)
        ),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1F1F1F))
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SessionItem(session: Session, active: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                1.dp,
                if (active) Color(0xFF22304A) else Color(0xFF1F2937),
                RoundedCornerShape(10.dp)
            )
            .background(if (active) Color(0xFF161B22) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text("ID ${session.id.take(6)}", color = Color(0xFFE5E7EB), fontWeight = FontWeight.SemiBold)
        Text("Status: ${session.status.name.lowercase()}", color = Color(0xFF9BA2B0), fontSize = 12.sp)
        Text("Orbs: ${session.orbs ?: "..."}", color = Color(0xFF9BA2B0), fontSize = 12.sp)
    }
}

@Composable
private fun QuestRow(item: QuestUiItem) {
    val statusColor = when (item.status) {
        QuestStatus.RUNNING -> Color(0xFF38BDF8)
        QuestStatus.DONE -> Color(0xFF4ADE80)
        QuestStatus.CLAIMED -> Color(0xFF9BA2B0)
        QuestStatus.FAILED -> Color(0xFFF87171)
        QuestStatus.SKIPPED -> Color(0xFFFBBF24)
        else -> Color(0xFF38BDF8)
    }
    val barColor = when (item.status) {
        QuestStatus.DONE, QuestStatus.CLAIMED -> Color(0xFF4ADE80)
        QuestStatus.FAILED -> Color(0xFFF87171)
        QuestStatus.RUNNING -> Color(0xFF38BDF8)
        else -> Color(0xFF38BDF8)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, color = Color(0xFFE5E7EB), fontWeight = FontWeight.SemiBold)
                Text(item.reward, color = Color(0xFF9BA2B0), fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    item.status.name.lowercase(),
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
                val label = if (item.target > 0) {
                    "${item.progress}/${item.target}s"
                } else {
                    "${item.remaining}s"
                }
                Text(label, color = Color(0xFF9BA2B0), fontSize = 12.sp)
            }
        }
        LinearProgressIndicator(
            progress = item.fraction,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = Color(0xFF1F1F1F)
        )
    }
}
