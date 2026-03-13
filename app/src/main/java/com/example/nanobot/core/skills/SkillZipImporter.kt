package com.example.nanobot.core.skills

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SkillZipImporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun importArchive(archiveUri: Uri): Uri = withContext(Dispatchers.IO) {
        val importRoot = File(context.filesDir, "skill_archives").apply { mkdirs() }
        val targetDir = File(importRoot, UUID.randomUUID().toString()).apply { mkdirs() }
        context.contentResolver.openInputStream(archiveUri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val rawName = entry.name.replace('\\', '/')
                    if (rawName.isBlank()) {
                        entry = zip.nextEntry
                        continue
                    }
                    val output = File(targetDir, rawName).canonicalFile
                    check(output.path.startsWith(targetDir.canonicalPath + File.separator) || output.path == targetDir.canonicalPath) {
                        "Zip entry escapes target directory: $rawName"
                    }
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else {
                        output.parentFile?.mkdirs()
                        output.outputStream().use { sink -> zip.copyTo(sink) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Unable to open zip archive for reading.")
        Uri.fromFile(targetDir)
    }
}
