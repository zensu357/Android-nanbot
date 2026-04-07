package com.example.nanobot.ui.components

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.nanobot.ui.theme.NanobotShapes
import com.example.nanobot.ui.theme.NanobotTheme
import com.example.nanobot.ui.theme.Obsidian

@Composable
fun NanobotInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isRunning: Boolean,
    isSending: Boolean,
    isCancelling: Boolean,
    onSendClick: () -> Unit,
    onCancelClick: () -> Unit,
    onAttachClick: () -> Unit,
    voiceContent: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ext = NanobotTheme.extendedColors
    val infiniteTransition = rememberInfiniteTransition(label = "inputGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "inputGlowAlpha"
    )
    val borderColor = remember(isRunning, glowAlpha, ext) {
        if (isRunning) ext.neon.copy(alpha = glowAlpha) else ext.glassBorder
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = NanobotShapes.InputBar,
        innerPadding = 4.dp,
        borderColor = borderColor,
        borderWidth = if (isRunning) 1.5.dp else 1.dp
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onAttachClick,
                enabled = !isRunning && !isSending
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Attach",
                    tint = ext.textSecondary
                )
            }

            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = placeholder,
                        color = ext.textTertiary,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ext.textPrimary),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = ext.textPrimary,
                    unfocusedTextColor = ext.textPrimary,
                    cursorColor = ext.neon
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                maxLines = 6
            )

            voiceContent?.invoke()

            IconButton(
                onClick = if (isRunning) onCancelClick else onSendClick,
                enabled = !isCancelling
            ) {
                when {
                    isCancelling || isSending -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ext.neon
                        )
                    }

                    isRunning -> {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop",
                            tint = ext.errorRed
                        )
                    }

                    else -> {
                        Surface(
                            shape = NanobotShapes.Chip,
                            color = ext.neon
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Obsidian,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
