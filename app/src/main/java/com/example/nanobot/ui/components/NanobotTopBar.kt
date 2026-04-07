package com.example.nanobot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.nanobot.ui.theme.NanobotShapes
import com.example.nanobot.ui.theme.NanobotTheme

@Composable
fun NanobotTopBar(
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    navigationIcon: ImageVector? = null,
    navigationContentDescription: String? = null,
    onNavigationClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val ext = NanobotTheme.extendedColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ext.glassOverlay.copy(alpha = 0.18f))
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (navigationIcon != null && onNavigationClick != null) {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = navigationIcon,
                    contentDescription = navigationContentDescription,
                    tint = ext.textPrimary
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ext.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.takeIf { it.isNotBlank() }?.let { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        badgeText?.takeIf { it.isNotBlank() }?.let { model ->
            Surface(
                shape = NanobotShapes.Chip,
                color = ext.neonDim.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, ext.glassBorder),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                Text(
                    text = model,
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.neon,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 140.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        actions()
    }
}
