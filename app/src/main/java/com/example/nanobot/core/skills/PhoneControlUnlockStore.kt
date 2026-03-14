package com.example.nanobot.core.skills

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Singleton
class PhoneControlUnlockStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val verifier: PhoneControlUnlockVerifier
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = true
    }

    suspend fun saveReceipt(receipt: PhoneControlUnlockReceipt) = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "skill_unlock_receipts/${safeSegment(receipt.packageId)}")
        directory.mkdirs()
        File(directory, "p-c.unlock.json").writeText(json.encodeToString(receipt))
    }

    suspend fun findReceipt(packageId: String): PhoneControlUnlockReceipt? = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "skill_unlock_receipts/${safeSegment(packageId)}/p-c.unlock.json")
        if (!file.exists()) return@withContext null
        runCatching {
            json.decodeFromString<PhoneControlUnlockReceipt>(file.readText())
        }.getOrNull()
    }

    suspend fun hasAccepted(
        manifest: PhoneControlUnlockManifest,
        skillSha256: String
    ): Boolean {
        val receipt = findReceipt(manifest.packageId) ?: return false
        return receipt.skillId == manifest.skillId &&
            receipt.skillSha256.equals(skillSha256, ignoreCase = true) &&
            receipt.signerKeyId == manifest.signing.keyId &&
            receipt.signerAlgorithm.equals(manifest.signing.algorithm, ignoreCase = true) &&
            receipt.consentVersion == manifest.consent.version &&
            receipt.consentTextSha256 == verifier.sha256(manifest.consent.text) &&
            receipt.unlockProfiles == manifest.unlockProfiles
    }

    suspend fun recordAcceptance(
        manifest: PhoneControlUnlockManifest,
        skillSha256: String,
        sourceTreeUri: String?,
        documentUri: String?
    ) {
        saveReceipt(
            PhoneControlUnlockReceipt(
                packageId = manifest.packageId,
                skillId = manifest.skillId,
                skillSha256 = skillSha256,
                unlockProfiles = manifest.unlockProfiles,
                signerKeyId = manifest.signing.keyId,
                signerAlgorithm = manifest.signing.algorithm,
                consentTitle = manifest.consent.title,
                consentVersion = manifest.consent.version,
                consentTextSha256 = verifier.sha256(manifest.consent.text),
                storedAtEpochMs = System.currentTimeMillis(),
                sourceTreeUri = sourceTreeUri,
                documentUri = documentUri
            )
        )
    }

    private fun safeSegment(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
