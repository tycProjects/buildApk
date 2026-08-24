package com.ryan.vietsubai.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryan.vietsubai.model.AppTab
import com.ryan.vietsubai.ui.components.VietsubBottomBar
import com.ryan.vietsubai.ui.screens.config.ConfigScreen
import com.ryan.vietsubai.ui.screens.editor.EditorScreen
import com.ryan.vietsubai.ui.screens.home.HomeScreen
import com.ryan.vietsubai.ui.theme.Motion
import com.ryan.vietsubai.ui.theme.VietsubAITheme

@Composable
fun VietsubAIApp(vm: VietsubAIViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val darkTheme by vm.darkTheme.collectAsStateWithLifecycle()

    VietsubAITheme(darkTheme = darkTheme) {
        Scaffold(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
            bottomBar = { VietsubBottomBar(current = tab, onSelect = vm::tab) },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.FAST))
                    },
                    label = "tabTransition",
                ) { currentTab ->
                    when (currentTab) {
                        AppTab.HOME -> HomeScreen(vm)
                        AppTab.EDITOR -> EditorScreen(vm)
                        AppTab.CONFIG -> ConfigScreen(vm)
                    }
                }
            }
        }
    }
}
