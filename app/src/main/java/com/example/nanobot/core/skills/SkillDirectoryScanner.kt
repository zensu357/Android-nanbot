package com.example.nanobot.core.skills

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScannedSkill(
    val skill: SkillDefinition,
    val sourceTreeUri: Uri,
    val documentUri: Uri
)

interface SkillImportScanner {
    suspend fun scan(treeUri: Uri): List<ScannedSkill>
}

@Singleton
class SkillDirectoryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: SkillMarkdownParser
) : SkillImportScanner {
    override suspend fun scan(treeUri: Uri): List<ScannedSkill> = withContext(Dispatchers.IO) {
        walkDocumentTree(treeUri).mapNotNull { node ->
            if (!node.isSkillMarkdown()) return@mapNotNull null
            runCatching {
                val markdown = context.contentResolver.openInputStream(node.documentUri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                if (markdown.isBlank()) return@runCatching null
                val hash = sha256(markdown)
                val parsed = parser.parse(
                    markdown = markdown,
                    source = SkillSource.IMPORTED,
                    originLabel = node.relativePath,
                    documentUri = node.documentUri.toString(),
                    sourceTreeUri = treeUri.toString(),
                    contentHash = hash
                )
                ScannedSkill(
                    skill = parsed.skill,
                    sourceTreeUri = treeUri,
                    documentUri = node.documentUri
                )
            }.getOrNull()
        }.toList()
    }

    private fun walkDocumentTree(treeUri: Uri): Sequence<DocumentNode> = sequence {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootDocumentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        yieldAll(walkNode(treeUri, rootDocumentUri, ""))
    }

    private fun walkNode(treeUri: Uri, documentUri: Uri, prefix: String): Sequence<DocumentNode> = sequence {
        val documentId = DocumentsContract.getDocumentId(documentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val resolver = context.contentResolver
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val childNodes = runCatching {
            buildList {
                resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    while (cursor.moveToNext()) {
                        val childId = cursor.getString(idIndex)
                        val displayName = cursor.getString(nameIndex).orEmpty()
                        val mimeType = cursor.getString(mimeIndex).orEmpty()
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                        val relativePath = listOf(prefix, displayName).filter { it.isNotBlank() }.joinToString("/")
                        add(DocumentNode(childUri, displayName, mimeType, relativePath))
                    }
                }
            }
        }.getOrDefault(emptyList())

        childNodes.forEach { child ->
            if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                yieldAll(walkNode(treeUri, child.documentUri, child.relativePath))
            } else {
                yield(child)
            }
        }
    }

    private fun DocumentNode.isSkillMarkdown(): Boolean {
        return displayName.equals("SKILL.md", ignoreCase = true) || relativePath.endsWith("/SKILL.md", ignoreCase = true)
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}

data class DocumentNode(
    val documentUri: Uri,
    val displayName: String,
    val mimeType: String,
    val relativePath: String
)
