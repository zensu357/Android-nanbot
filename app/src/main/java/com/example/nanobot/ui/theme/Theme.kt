package com.example.nanobot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ExtendedColors(
    val userBubble: Color,
    val assistantBubble: Color,
    val toolBubble: Color,
    val neon: Color,
    val neonDim: Color,
    val neonGlow: Color,
    val glassBorder: Color,
    val glassOverlay: Color,
    val codeBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val errorRed: Color,
    val warningAmber: Color,
    val successGreen: Color
)

private val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        userBubble = Color.Unspecified,
        assistantBubble = Color.Unspecified,
        toolBubble = Color.Unspecified,
        neon = Color.Unspecified,
        neonDim = Color.Unspecified,
        neonGlow = Color.Unspecified,
        glassBorder = Color.Unspecified,
        glassOverlay = Color.Unspecified,
        codeBackground = Color.Unspecified,
        textPrimary = Color.Unspecified,
        textSecondary = Color.Unspecified,
        textTertiary = Color.Unspecified,
        errorRed = Color.Unspecified,
        warningAmber = Color.Unspecified,
        successGreen = Color.Unspecified
    )
}

private val DarkColors = darkColorScheme(
    primary = Neon,
    onPrimary = Obsidian,
    primaryContainer = NeonDim,
    onPrimaryContainer = TextPrimary,
    secondary = Slate,
    onSecondary = TextPrimary,
    secondaryContainer = Graphite,
    onSecondaryContainer = TextPrimary,
    tertiary = NeonDim,
    onTertiary = TextPrimary,
    tertiaryContainer = Graphite,
    onTertiaryContainer = TextSecondary,
    background = Obsidian,
    onBackground = TextPrimary,
    surface = Gunmetal,
    onSurface = TextPrimary,
    surfaceVariant = Slate,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = Obsidian,
    errorContainer = Color(0xFF3D1C1C),
    onErrorContainer = ErrorRed,
    outline = Graphite,
    outlineVariant = Color(0xFF2A2F35)
)

private val DarkExtendedColors = ExtendedColors(
    userBubble = UserBubble,
    assistantBubble = AssistantBubble,
    toolBubble = ToolBubble,
    neon = Neon,
    neonDim = NeonDim,
    neonGlow = NeonGlow,
    glassBorder = GlassBorder,
    glassOverlay = GlassOverlay,
    codeBackground = CodeBackground,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textTertiary = TextTertiary,
    errorRed = ErrorRed,
    warningAmber = WarningAmber,
    successGreen = SuccessGreen
)

object NanobotTheme {
    val extendedColors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current

    @Composable
    operator fun invoke(
        darkTheme: Boolean = true,
        content: @Composable () -> Unit
    ) {
        val colorScheme = DarkColors
        val extendedColors = DarkExtendedColors

        CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = NanobotTypography,
                content = content
            )
        }
    }
}
