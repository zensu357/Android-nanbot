package com.example.nanobot.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nanobot.ui.theme.NanobotShapes
import com.example.nanobot.ui.theme.NanobotTheme

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = NanobotShapes.Card,
    innerPadding: Dp = 16.dp,
    borderColor: Color = NanobotTheme.extendedColors.glassBorder,
    borderWidth: Dp = 1.dp,
    backgroundColor: Color = NanobotTheme.extendedColors.glassOverlay,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val animatedScale by animateFloatAsState(
        targetValue = if (highlighted) 1.01f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glassCardScale"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (highlighted) NanobotTheme.extendedColors.neon.copy(alpha = 0.45f) else borderColor,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "glassCardBorder"
    )
    val animatedBackgroundColor by animateColorAsState(
        targetValue = if (highlighted) {
            backgroundColor.copy(alpha = (backgroundColor.alpha + 0.08f).coerceAtMost(0.28f))
        } else {
            backgroundColor
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "glassCardBackground"
    )

    Column(
        modifier = modifier
            .scale(animatedScale)
            .clip(shape)
            .background(animatedBackgroundColor)
            .border(width = borderWidth, color = animatedBorderColor, shape = shape)
            .padding(innerPadding),
        content = content
    )
}
