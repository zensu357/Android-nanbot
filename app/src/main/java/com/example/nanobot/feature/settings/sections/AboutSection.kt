package com.example.nanobot.feature.settings.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nanobot.ui.components.SettingsGroupCard

@Composable
fun AboutSection(
    onOpenRepository: () -> Unit,
    onOpenAuthor: () -> Unit,
    onOpenTelegram: () -> Unit
) {
    SettingsGroupCard(title = "About") {
        SectionOutlinedButton(
            text = "GitHub Repository",
            onClick = onOpenRepository,
            modifier = Modifier.fillMaxWidth()
        )
        SectionOutlinedButton(
            text = "Author: zensu357",
            onClick = onOpenAuthor,
            modifier = Modifier.fillMaxWidth()
        )
        SectionOutlinedButton(
            text = "Telegram Group",
            onClick = onOpenTelegram,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
