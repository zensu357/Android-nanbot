package com.example.nanobot.core.skills

import android.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class PhoneControlUnlockVerifier @Inject constructor() {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = false
    }

    fun verify(manifestText: String, skillId: String, skillSha256: String): PhoneControlUnlockVerificationResult {
        val manifest = json.decodeFromString<PhoneControlUnlockManifest>(manifestText)
        require(manifest.version == SUPPORTED_VERSION) { "Unsupported phone-control unlock version '${manifest.version}'." }
        require(manifest.packageId.isNotBlank()) { "Unlock manifest packageId is required." }
        require(manifest.skillId.isNotBlank()) { "Unlock manifest skillId is required." }
        require(manifest.skillId == skillId) { "Unlock manifest skillId '${manifest.skillId}' does not match imported skill '$skillId'." }
        require(manifest.skillSha256.isNotBlank()) { "Unlock manifest skillSha256 is required." }
        require(manifest.skillSha256.equals(skillSha256, ignoreCase = true)) {
            "Unlock manifest hash does not match the imported SKILL.md content."
        }
        require(manifest.unlockProfiles.isNotEmpty()) { "Unlock manifest unlockProfiles must not be empty." }
        require(manifest.unlockProfiles.all { it.isNotBlank() }) { "Unlock manifest unlockProfiles contain blank values." }
        require(manifest.skillPath.isNotBlank()) { "Unlock manifest skillPath is required." }
        require(manifest.consent.title.isNotBlank()) { "Unlock manifest consent.title is required." }
        require(manifest.consent.version.isNotBlank()) { "Unlock manifest consent.version is required." }
        require(manifest.consent.text.isNotBlank()) { "Unlock manifest consent.text is required." }
        require(manifest.signing.keyId.isNotBlank()) { "Unlock manifest signing.keyId is required." }
        require(manifest.signing.signature.isNotBlank()) { "Unlock manifest signing.signature is required." }
        require(manifest.signing.algorithm.equals(ED25519_ALGORITHM, ignoreCase = true)) {
            "Unsupported unlock signing algorithm '${manifest.signing.algorithm}'."
        }

        val canonicalPayload = canonicalPayload(manifest)
        val publicKey = trustedPublicKey(manifest.signing.keyId)
        val signatureBytes = runCatching {
            Base64.decode(manifest.signing.signature, Base64.DEFAULT)
        }.getOrElse {
            throw IllegalArgumentException("Unlock manifest signature is not valid base64.")
        }
        val verifier = Signature.getInstance(ED25519_ALGORITHM)
        verifier.initVerify(publicKey)
        verifier.update(canonicalPayload.toByteArray(Charsets.UTF_8))
        require(verifier.verify(signatureBytes)) { "Unlock manifest signature verification failed." }

        return PhoneControlUnlockVerificationResult(
            manifest = manifest,
            canonicalPayload = canonicalPayload
        )
    }

    fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun canonicalPayload(manifest: PhoneControlUnlockManifest): String {
        val payload = buildJsonObject {
            put("consent", buildJsonObject {
                put("text", JsonPrimitive(manifest.consent.text))
                put("title", JsonPrimitive(manifest.consent.title))
                put("version", JsonPrimitive(manifest.consent.version))
            })
            put("packageId", JsonPrimitive(manifest.packageId))
            put("skillId", JsonPrimitive(manifest.skillId))
            put("skillPath", JsonPrimitive(manifest.skillPath))
            put("skillSha256", JsonPrimitive(manifest.skillSha256))
            put("signing", buildJsonObject {
                put("algorithm", JsonPrimitive(manifest.signing.algorithm))
                put("keyId", JsonPrimitive(manifest.signing.keyId))
            })
            put("unlockProfiles", json.encodeToJsonElement(ListSerializer(String.serializer()), manifest.unlockProfiles))
            put("version", JsonPrimitive(manifest.version))
        }
        return json.encodeToString(canonicalizeElement(payload))
    }

    private fun canonicalizeElement(value: JsonElement): JsonElement {
        return when (value) {
            is JsonObject -> JsonObject(
                value.entries
                    .sortedBy { it.key }
                    .associate { (key, child) -> key to canonicalizeElement(child) }
            )
            is JsonArray -> JsonArray(value.map(::canonicalizeElement))
            else -> value
        }
    }

    private fun trustedPublicKey(keyId: String): PublicKey {
        val encoded = TRUSTED_PUBLIC_KEYS[keyId]
            ?: throw IllegalArgumentException("Unlock manifest signer '$keyId' is not trusted by this build.")
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        return KeyFactory.getInstance(ED25519_ALGORITHM).generatePublic(X509EncodedKeySpec(bytes))
    }

    private companion object {
        const val SUPPORTED_VERSION = 1
        const val ED25519_ALGORITHM = "Ed25519"

        val TRUSTED_PUBLIC_KEYS: Map<String, String> = mapOf(
            // Test-only placeholder key. Replace with production publisher keys before shipping unlock packages.
            "test-phone-control-key" to "MCowBQYDK2VwAyEArfzqoX1WW6yvUMEJzorLtROqvRMtjfJ1iHTo2dd/H0M="
        )
    }
}
