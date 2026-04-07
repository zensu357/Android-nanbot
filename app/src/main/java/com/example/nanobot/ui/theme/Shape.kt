package com.example.nanobot.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object NanobotShapes {
    val UserBubble = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 6.dp,
        bottomStart = 20.dp,
        bottomEnd = 20.dp
    )

    val AssistantBubble = RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 20.dp,
        bottomStart = 20.dp,
        bottomEnd = 20.dp
    )

    val ToolBubble = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val CardSmall = RoundedCornerShape(12.dp)
    val InputBar = RoundedCornerShape(24.dp)
    val TextField = RoundedCornerShape(12.dp)
    val Chip = RoundedCornerShape(999.dp)
    val Drawer = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 24.dp,
        bottomStart = 0.dp,
        bottomEnd = 24.dp
    )
    val BottomSheet = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp
    )
}
