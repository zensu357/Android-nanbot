package com.example.nanobot.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_skills")
data class CustomSkillEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val version: String,
    val tagsJson: String,
    val instructions: String,
    val whenToUse: String,
    val summaryPrompt: String,
    val workflowJson: String,
    val constraintsJson: String,
    val outputContract: String,
    val examplesJson: String,
    val recommendedToolsJson: String,
    val activationKeywordsJson: String,
    val priority: Int,
    val maxPromptChars: Int,
    val originLabel: String?,
    val documentUri: String,
    val sourceTreeUri: String,
    val contentHash: String,
    val importedAt: Long,
    val updatedAt: Long,
    val legacyPromptFragment: String
)
