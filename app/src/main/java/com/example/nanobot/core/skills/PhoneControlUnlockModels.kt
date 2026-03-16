package com.example.nanobot.core.skills

import kotlinx.serialization.Serializable

const val PHONE_CONTROL_UNLOCK_FILE_NAME = "phone-control.unlock"

@Serializable
data class PhoneControlUnlockConsent(
    val title: String,
    val version: String,
    val text: String
)

@Serializable
data class PhoneControlUnlockSigning(
    val keyId: String,
    val algorithm: String,
    val signature: String
)

@Serializable
data class PhoneControlUnlockManifest(
    val version: Int,
    val packageId: String,
    val skillId: String,
    val skillPath: String,
    val skillSha256: String,
    val unlockProfiles: List<String>,
    val consent: PhoneControlUnlockConsent,
    val signing: PhoneControlUnlockSigning
)

@Serializable
data class PhoneControlUnlockReceipt(
    val packageId: String,
    val skillId: String,
    val skillSha256: String,
    val unlockProfiles: List<String>,
    val signerKeyId: String,
    val signerAlgorithm: String,
    val consentTitle: String,
    val consentVersion: String,
    val consentTextSha256: String,
    val storedAtEpochMs: Long,
    val sourceTreeUri: String?,
    val documentUri: String?,
    val unlockFileName: String = PHONE_CONTROL_UNLOCK_FILE_NAME
)

@Serializable
data class PendingPhoneControlUnlockConsent(
    val packageId: String,
    val skillId: String,
    val skillTitle: String,
    val skillSha256: String,
    val unlockProfiles: List<String>,
    val consentTitle: String,
    val consentVersion: String,
    val consentText: String,
    val signerKeyId: String,
    val signerAlgorithm: String,
    val sourceTreeUri: String?,
    val documentUri: String?,
    val createdAtEpochMs: Long
)

data class PhoneControlUnlockVerificationResult(
    val manifest: PhoneControlUnlockManifest,
    val canonicalPayload: String
)
