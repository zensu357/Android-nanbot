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
        File(directory, "pending-consent.json").delete()
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

    suspend fun savePendingConsent(consent: PendingPhoneControlUnlockConsent) = withContext(Dispatchers.IO) {
        val directory = File(context.filesDir, "skill_unlock_receipts/${safeSegment(consent.packageId)}")
        directory.mkdirs()
        File(directory, "pending-consent.json").writeText(json.encodeToString(consent))
    }

    suspend fun getPendingConsents(): List<PendingPhoneControlUnlockConsent> = withContext(Dispatchers.IO) {
        val root = File(context.filesDir, "skill_unlock_receipts")
        if (!root.exists() || !root.isDirectory) return@withContext emptyList()
        root.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { directory ->
                val file = File(directory, "pending-consent.json")
                if (!file.exists()) return@mapNotNull null
                runCatching {
                    json.decodeFromString<PendingPhoneControlUnlockConsent>(file.readText())
                }.getOrNull()
            }
            .sortedByDescending { it.createdAtEpochMs }
    }

    suspend fun findPendingConsent(packageId: String): PendingPhoneControlUnlockConsent? = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "skill_unlock_receipts/${safeSegment(packageId)}/pending-consent.json")
        if (!file.exists()) return@withContext null
        runCatching {
            json.decodeFromString<PendingPhoneControlUnlockConsent>(file.readText())
        }.getOrNull()
    }

    suspend fun acceptPendingConsent(packageId: String): PhoneControlUnlockReceipt? = withContext(Dispatchers.IO) {
        val pending = findPendingConsent(packageId) ?: return@withContext null
        val receipt = PhoneControlUnlockReceipt(
            packageId = pending.packageId,
            skillId = pending.skillId,
            skillSha256 = pending.skillSha256,
            unlockProfiles = pending.unlockProfiles,
            signerKeyId = pending.signerKeyId,
            signerAlgorithm = pending.signerAlgorithm,
            consentTitle = pending.consentTitle,
            consentVersion = pending.consentVersion,
            consentTextSha256 = verifier.sha256(pending.consentText),
            storedAtEpochMs = System.currentTimeMillis(),
            sourceTreeUri = pending.sourceTreeUri,
            documentUri = pending.documentUri
        )
        saveReceipt(receipt)
        receipt
    }

    suspend fun rejectPendingConsent(packageId: String) = withContext(Dispatchers.IO) {
        File(context.filesDir, "skill_unlock_receipts/${safeSegment(packageId)}/pending-consent.json").delete()
    }

    private fun safeSegment(value: String): String {
        return value.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
