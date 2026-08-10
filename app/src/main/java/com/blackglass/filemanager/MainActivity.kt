package com.blackglass.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.blackglass.filemanager.data.FileItem
import com.blackglass.filemanager.ui.screens.FileListScreen
import com.blackglass.filemanager.ui.screens.PermissionScreen
import com.blackglass.filemanager.ui.theme.GlassFileManagerTheme
import com.blackglass.filemanager.ui.theme.PureBlack
import com.blackglass.filemanager.util.PermissionUtils
import com.blackglass.filemanager.viewmodel.FileViewModel
import java.io.File

class FileViewModelFactory(private val rootDir: File) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FileViewModel(rootDir) as T
    }
}

class MainActivity : ComponentActivity() {

    private val rootDir: File by lazy { Environment.getExternalStorageDirectory() }

    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result observed via recomposition on resume */ }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result observed via recomposition on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GlassFileManagerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = PureBlack) {
                    var hasAccess by remember { mutableStateOf(PermissionUtils.hasStorageAccess(this)) }

                    // Re-check whenever the activity resumes (e.g. returning from Settings)
                    androidx.compose.runtime.DisposableEffect(Unit) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                hasAccess = PermissionUtils.hasStorageAccess(this@MainActivity)
                            }
                        }
                        lifecycle.addObserver(observer)
                        onDispose { lifecycle.removeObserver(observer) }
                    }

                    if (!hasAccess) {
                        PermissionScreen(onGrantClick = { requestAccess() })
                    } else {
                        val viewModel: FileViewModel = viewModel(factory = FileViewModelFactory(rootDir))

                        BackHandler(enabled = true) {
                            if (!viewModel.goBack()) {
                                finish()
                            }
                        }

                        val (used, total) = remember { storageStats() }

                        FileListScreen(
                            viewModel = viewModel,
                            usedBytes = used,
                            totalBytes = total,
                            onOpenFile = { openFile(it) },
                            onShareFiles = { shareFiles(it) },
                            onBackAtRoot = { finish() }
                        )
                    }
                }
            }
        }
    }

    private fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                manageStorageLauncher.launch(PermissionUtils.manageAllFilesIntent(this))
            } catch (_: Exception) {
                manageStorageLauncher.launch(PermissionUtils.manageAllFilesFallbackIntent())
            }
        } else {
            legacyPermissionLauncher.launch(PermissionUtils.legacyPermissions())
        }
    }

    private fun storageStats(): Pair<Long, Long> {
        return try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            val total = stat.blockCountLong * stat.blockSizeLong
            val free = stat.availableBlocksLong * stat.blockSizeLong
            Pair(total - free, total)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    private fun openFile(item: FileItem) {
        try {
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", item.file)
            val mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(item.extension) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            // No app can handle this file type; silently ignore rather than crash.
        }
    }

    private fun shareFiles(items: List<FileItem>) {
        if (items.isEmpty()) return
        try {
            val uris = ArrayList(items.map { item ->
                FileProvider.getUriForFile(this, "$packageName.fileprovider", item.file)
            })
            val intent = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            startActivity(Intent.createChooser(intent, "Share via"))
        } catch (_: Exception) {
            // Ignore share failures gracefully.
        }
    }
}
