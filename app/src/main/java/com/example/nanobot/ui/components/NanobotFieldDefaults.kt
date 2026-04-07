package com.example.nanobot.ui.components

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import com.example.nanobot.ui.theme.NanobotTheme
import com.example.nanobot.ui.theme.Obsidian

@Composable
fun nanobotTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = NanobotTheme.extendedColors.textPrimary,
    unfocusedTextColor = NanobotTheme.extendedColors.textPrimary,
    disabledTextColor = NanobotTheme.extendedColors.textTertiary,
    focusedBorderColor = NanobotTheme.extendedColors.neon,
    unfocusedBorderColor = NanobotTheme.extendedColors.glassBorder,
    disabledBorderColor = NanobotTheme.extendedColors.glassBorder,
    focusedContainerColor = NanobotTheme.extendedColors.glassOverlay,
    unfocusedContainerColor = NanobotTheme.extendedColors.glassOverlay,
    disabledContainerColor = NanobotTheme.extendedColors.glassOverlay,
    cursorColor = NanobotTheme.extendedColors.neon,
    focusedLabelColor = NanobotTheme.extendedColors.neon,
    unfocusedLabelColor = NanobotTheme.extendedColors.textSecondary,
    focusedPlaceholderColor = NanobotTheme.extendedColors.textTertiary,
    unfocusedPlaceholderColor = NanobotTheme.extendedColors.textTertiary,
    focusedSupportingTextColor = NanobotTheme.extendedColors.textSecondary,
    unfocusedSupportingTextColor = NanobotTheme.extendedColors.textSecondary
)

@Composable
fun nanobotSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Obsidian,
    checkedTrackColor = NanobotTheme.extendedColors.neon,
    checkedBorderColor = NanobotTheme.extendedColors.neon,
    uncheckedThumbColor = NanobotTheme.extendedColors.textSecondary,
    uncheckedTrackColor = NanobotTheme.extendedColors.glassOverlay,
    uncheckedBorderColor = NanobotTheme.extendedColors.glassBorder
)
