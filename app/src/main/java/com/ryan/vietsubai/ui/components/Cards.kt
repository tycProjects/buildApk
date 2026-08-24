package com.ryan.vietsubai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    gradient: List<Color>? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val fallback = androidx.compose.material3.MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (containerColor == Color.Unspecified) fallback else containerColor,
            contentColor = contentColor,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .then(if (gradient != null) Modifier.background(Brush.linearGradient(gradient), shape) else Modifier)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
