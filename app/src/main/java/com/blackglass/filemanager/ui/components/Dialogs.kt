package com.blackglass.filemanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blackglass.filemanager.data.FileItem
import com.blackglass.filemanager.ui.theme.CardBlack
import com.blackglass.filemanager.ui.theme.FaintWhite
import com.blackglass.filemanager.ui.theme.MutedWhite
import com.blackglass.filemanager.ui.theme.PureBlack
import com.blackglass.filemanager.ui.theme.PureWhite
import com.blackglass.filemanager.util.FileUtils

@Composable
fun TextInputDialog(
    title: String,
    initialValue: String = "",
    confirmLabel: String = "Create",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBlack,
        titleContentColor = PureWhite,
        textContentColor = MutedWhite,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PureWhite,
                    unfocusedTextColor = PureWhite,
                    focusedBorderColor = PureWhite,
                    unfocusedBorderColor = FaintWhite,
                    cursorColor = PureWhite
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = PureBlack)
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MutedWhite) }
        }
    )
}

@Composable
fun ConfirmDeleteDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBlack,
        titleContentColor = PureWhite,
        textContentColor = MutedWhite,
        title = { Text(if (count == 1) "Delete item?" else "Delete $count items?") },
        text = { Text("This can't be undone.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = PureBlack)
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MutedWhite) }
        }
    )
}

@Composable
fun FileDetailsDialog(item: FileItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBlack,
        titleContentColor = PureWhite,
        textContentColor = MutedWhite,
        title = { Text(item.name) },
        text = {
            Column {
                DetailRow("Type", if (item.isDirectory) "Folder" else (item.extension.ifBlank { "File" }))
                DetailRow("Size", FileUtils.formatSize(item.sizeBytes))
                DetailRow("Modified", FileUtils.formatDate(item.lastModified))
                DetailRow("Path", item.path)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = PureWhite) }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = FaintWhite, style = MaterialTheme.typography.labelSmall)
        Text(text = value, color = PureWhite, style = MaterialTheme.typography.bodyMedium)
    }
}
