package com.example.nanobot.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.nanobot.ui.components.GlassCard
import com.example.nanobot.ui.components.GlowButton
import com.example.nanobot.ui.components.nanobotTextFieldColors
import com.example.nanobot.ui.theme.NanobotTheme
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onProviderChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onPresetChange: (String) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    val ext = NanobotTheme.extendedColors
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ext.neonGlow.copy(alpha = 0.18f), MaterialTheme.colorScheme.background)
                    )
                )
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "J.A.R.V.I.S Nanobot",
                        style = MaterialTheme.typography.titleLarge,
                        color = ext.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Set up your local-first Android agent in a few steps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.textSecondary
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    when (page) {
                        0 -> WelcomePage()
                        1 -> ProviderPage(
                            state = state,
                            onProviderChange = onProviderChange,
                            onApiKeyChange = onApiKeyChange,
                            onBaseUrlChange = onBaseUrlChange,
                            onModelChange = onModelChange
                        )
                        2 -> CustomizePage(
                            state = state,
                            onPresetChange = onPresetChange,
                            onSystemPromptChange = onSystemPromptChange
                        )
                    }
                }

                state.errorMessage?.let { errorText ->
                    Text(
                        text = errorText,
                        color = ext.errorRed,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(3) { index ->
                        val isActive = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(if (isActive) 10.dp else 6.dp)
                                .background(
                                    color = if (isActive) ext.neon else ext.textTertiary,
                                    shape = CircleShape
                                )
                        )
                        if (index < 2) {
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage > 0) {
                        TextButton(onClick = {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }) {
                            Text("Back")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    if (pagerState.currentPage < 2) {
                        GlowButton(
                            text = "Next",
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        )
                    } else {
                        GlowButton(
                            text = if (state.isSaving) "Setting up..." else "Start Chatting",
                            onClick = onContinue,
                            enabled = !state.isSaving
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    val ext = NanobotTheme.extendedColors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "J.A.R.V.I.S Nanobot",
                style = MaterialTheme.typography.headlineLarge,
                color = ext.textPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Build your first Android-native agent workspace.",
                style = MaterialTheme.typography.titleMedium,
                color = ext.neon,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Set a provider, choose a model, and create the first persistent chat session. You can adjust everything later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = ext.textSecondary
            )
        }
    }
}

@Composable
private fun ProviderPage(
    state: OnboardingUiState,
    onProviderChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onModelChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Provider Setup",
                style = MaterialTheme.typography.titleLarge,
                color = NanobotTheme.extendedColors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.providerType,
                onValueChange = onProviderChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Provider") },
                supportingText = { Text("Try openai_compatible, openrouter, or azure_openai") },
                colors = nanobotTextFieldColors()
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") },
                colors = nanobotTextFieldColors()
            )
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                colors = nanobotTextFieldColors()
            )
            OutlinedTextField(
                value = state.model,
                onValueChange = onModelChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                colors = nanobotTextFieldColors()
            )
        }
    }
}

@Composable
private fun CustomizePage(
    state: OnboardingUiState,
    onPresetChange: (String) -> Unit,
    onSystemPromptChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Customize Behavior",
                style = MaterialTheme.typography.titleLarge,
                color = NanobotTheme.extendedColors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = state.presetId,
                onValueChange = onPresetChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Prompt Preset") },
                supportingText = { Text("Available: ${state.availablePresets.joinToString()}") },
                colors = nanobotTextFieldColors()
            )
            OutlinedTextField(
                value = state.systemPrompt,
                onValueChange = onSystemPromptChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                label = { Text("Custom User Instructions") },
                colors = nanobotTextFieldColors()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Nanobot stores settings locally with DataStore and keeps conversations in Room so you can continue later.",
                style = MaterialTheme.typography.bodySmall,
                color = NanobotTheme.extendedColors.textSecondary
            )
        }
    }
}
