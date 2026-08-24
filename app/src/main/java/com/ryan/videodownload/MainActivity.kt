package com.ryan.videodownload

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ryan.videodownload.ui.MainViewModel
import com.ryan.videodownload.ui.screens.HomeScreen
import com.ryan.videodownload.ui.theme.DarkBackground
import com.ryan.videodownload.ui.theme.VideoDownloaderTheme
import com.ryan.videodownload.utils.PermissionHelper

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Xin quyền runtime (notification Android 13+, storage API <= 28)
        PermissionHelper.requestIfNeeded(this)

        // Handle shared URL from other apps
        handleSharedIntent(intent)

        setContent {
            VideoDownloaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    HomeScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                val urlRegex = Regex("""https?://[^\s]+""")
                val match = urlRegex.find(sharedText)
                if (match != null) {
                    viewModel.setSharedUrl(match.value)
                }
            }
        }
    }
}
