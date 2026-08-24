package com.ryan.vietsubai.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ryan.vietsubai.model.AppTab
import com.ryan.vietsubai.ui.theme.BrandIndigo

@Composable
fun VietsubBottomBar(current: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(tonalElevation = 0.dp) {
        AppTab.values().forEach { tab ->
            val selected = current == tab
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.15f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "navIconScale",
            )
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = iconFor(tab),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                    )
                },
                label = { Text(labelFor(tab)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BrandIndigo,
                    selectedTextColor = BrandIndigo,
                    indicatorColor = BrandIndigo.copy(alpha = 0.14f),
                ),
            )
        }
    }
}

private fun iconFor(tab: AppTab) = when (tab) {
    AppTab.HOME -> Icons.Default.Home
    AppTab.EDITOR -> Icons.Default.Movie
    AppTab.CONFIG -> Icons.Default.Settings
}

private fun labelFor(tab: AppTab) = when (tab) {
    AppTab.HOME -> "Home"
    AppTab.EDITOR -> "Editor"
    AppTab.CONFIG -> "Config"
}
