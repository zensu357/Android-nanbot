package com.example.nanobot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nanobot.ui.theme.NanobotTheme

@Composable
fun SettingsGroupCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val ext = NanobotTheme.extendedColors

    GlassCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ext.neon,
            modifier = Modifier
                .padding(start = 12.dp, bottom = 8.dp)
                .drawBehind {
                    drawRoundRect(
                        color = ext.neon,
                        cornerRadius = CornerRadius(2.dp.toPx()),
                        topLeft = Offset(-12.dp.toPx(), 0f),
                        size = size.copy(width = 3.dp.toPx())
                    )
                }
        )

        Spacer(modifier = Modifier.height(4.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
