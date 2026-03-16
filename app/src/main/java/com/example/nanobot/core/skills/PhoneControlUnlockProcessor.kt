package com.example.nanobot.core.skills

import android.net.Uri
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProcessedScannedSkill(
    val scannedSkill: ScannedSkill,
    val warnings: List<String> = emptyList(),
    val unlockReceipt: PhoneControlUnlockReceipt? = null
)

@Singleton
class PhoneControlUnlockProcessor @Inject constructor(
    private val verifier: PhoneControlUnlockVerifier,
    private val unlockStore: PhoneControlUnlockStore,
    private val parser: SkillMarkdownParser,
    private val resourceIndexer: SkillResourceIndexer
) {
    suspend fun process(scannedSkills: List<ScannedSkill>): List<ProcessedScannedSkill> {
        return scannedSkills.map { scannedSkill -> process(scannedSkill) }
    }

    suspend fun process(scannedSkill: ScannedSkill): ProcessedScannedSkill {
        val documentUri = scannedSkill.skill.documentUri ?: return ProcessedScannedSkill(scannedSkill)
        val documentFile = documentFile(documentUri) ?: return ProcessedScannedSkill(scannedSkill)
        val unlockFile = File(documentFile.parentFile, PHONE_CONTROL_UNLOCK_FILE_NAME)
        if (!unlockFile.exists() || !unlockFile.isFile) {
            return ProcessedScannedSkill(scannedSkill)
        }

        return runCatching {
            val manifestText = unlockFile.readText(Charsets.UTF_8)
            val verification = verifier.verify(
                manifestText = manifestText,
                skillId = scannedSkill.skill.id,
                skillSha256 = scannedSkill.skill.contentHash.orEmpty()
            )
            val alreadyAccepted = unlockStore.hasAccepted(verification.manifest, scannedSkill.skill.contentHash.orEmpty())
            if (!alreadyAccepted) {
                unlockStore.savePendingConsent(
                    PendingPhoneControlUnlockConsent(
                        packageId = verification.manifest.packageId,
                        skillId = verification.manifest.skillId,
                        skillTitle = scannedSkill.skill.title,
                        skillSha256 = scannedSkill.skill.contentHash.orEmpty(),
                        unlockProfiles = verification.manifest.unlockProfiles,
                        consentTitle = verification.manifest.consent.title,
                        consentVersion = verification.manifest.consent.version,
                        consentText = verification.manifest.consent.text,
                        signerKeyId = verification.manifest.signing.keyId,
                        signerAlgorithm = verification.manifest.signing.algorithm,
                        sourceTreeUri = scannedSkill.skill.sourceTreeUri,
                        documentUri = scannedSkill.skill.documentUri,
                        createdAtEpochMs = System.currentTimeMillis()
                    )
                )
            }
            val receipt = unlockStore.findReceipt(verification.manifest.packageId)
            unlockFile.delete()
            ProcessedScannedSkill(
                scannedSkill = reloadSkillWithoutUnlockSidecar(
                    scannedSkill = scannedSkill,
                    documentFile = documentFile,
                    packageId = verification.manifest.packageId
                ),
                warnings = if (alreadyAccepted) {
                    emptyList()
                } else {
                    listOf("Phone-control unlock verified for '${scannedSkill.skill.title}'. User consent is required before hidden tools are enabled.")
                },
                unlockReceipt = receipt
            )
        }.getOrElse { throwable ->
            unlockFile.delete()
            ProcessedScannedSkill(
                scannedSkill = reloadSkillWithoutUnlockSidecar(
                    scannedSkill = scannedSkill,
                    documentFile = documentFile,
                    packageId = null
                ),
                warnings = listOf(
                    "Unlock verification failed for '${scannedSkill.skill.title}'; imported as a normal skill. ${throwable.message ?: "Unknown verification error."}"
                )
            )
        }
    }

    private suspend fun reloadSkillWithoutUnlockSidecar(
        scannedSkill: ScannedSkill,
        documentFile: File,
        packageId: String?
    ): ScannedSkill {
        return withContext(Dispatchers.IO) {
            val skillRoot = documentFile.parentFile ?: return@withContext scannedSkill
            val relativeFilePath = buildList {
                val packageName = skillRoot.name
                if (packageName.isNotBlank()) add(packageName)
                add(documentFile.name)
            }.joinToString("/")
            val markdown = documentFile.readText(Charsets.UTF_8)
            val hash = verifier.sha256(markdown)
            val resourceEntries = skillRoot
                .walkTopDown()
                .filter { it.isFile && !it.name.equals(PHONE_CONTROL_UNLOCK_FILE_NAME, ignoreCase = true) }
                .map { child ->
                    child.relativeTo(skillRoot).invariantSeparatorsPath to child.toURI().toString()
                }
                .toList()
            val parsed = parser.parse(
                markdown = markdown,
                source = SkillSource.IMPORTED,
                originLabel = relativeFilePath,
                documentUri = documentFile.toURI().toString(),
                sourceTreeUri = scannedSkill.skill.sourceTreeUri,
                contentHash = hash,
                scope = SkillScope.IMPORTED,
                skillRootUri = skillRoot.toURI().toString(),
                isTrusted = scannedSkill.skill.isTrusted,
                resourceEntries = resourceIndexer.index(resourceEntries)
            )
            val updatedMetadata = parsed.skill.metadata.toMutableMap().apply {
                if (packageId.isNullOrBlank()) {
                    remove(HIDDEN_UNLOCK_PACKAGE_ID_KEY)
                } else {
                    this[HIDDEN_UNLOCK_PACKAGE_ID_KEY] = packageId
                }
            }
            scannedSkill.copy(
                skill = parsed.skill.copy(metadata = updatedMetadata),
                documentUri = Uri.fromFile(documentFile)
            )
        }
    }

    private fun documentFile(documentUri: String): File? {
        val uri = Uri.parse(documentUri)
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null
        return File(path)
    }

    private companion object {
        const val HIDDEN_UNLOCK_PACKAGE_ID_KEY = "hidden_unlock_package_id"
    }
}
