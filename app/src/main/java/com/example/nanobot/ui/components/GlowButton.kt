package com.example.nanobot.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.nanobot.ui.theme.NanobotShapes
import com.example.nanobot.ui.theme.NanobotTheme
import com.example.nanobot.ui.theme.Obsidian

@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val ext = NanobotTheme.extendedColors
    val glowAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.35f,
        animationSpec = tween(durationMillis = 220),
        label = "glowButtonAlpha"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .alpha(glowAlpha)
            .drawBehind {
            if (enabled) {
                drawRoundRect(
                    color = ext.neonGlow.copy(alpha = glowAlpha),
                    cornerRadius = CornerRadius(28.dp.toPx()),
                    topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
                    size = size.copy(
                        width = size.width + 8.dp.toPx(),
                        height = size.height + 8.dp.toPx()
                    )
                )
            }
        },
        shape = NanobotShapes.InputBar,
        colors = ButtonDefaults.buttonColors(
            containerColor = ext.neon,
            contentColor = Obsidian,
            disabledContainerColor = ext.neonDim.copy(alpha = 0.3f),
            disabledContentColor = ext.textTertiary
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
