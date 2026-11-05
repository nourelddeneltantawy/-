package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.FirmwareDump
import com.example.data.model.FirmwareFolder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipHelper {

    /**
     * Compresses the selected dump files and folders into a ZIP archive and triggers Android share intent.
     */
    fun zipAndShareDumps(
        context: Context,
        archiveName: String,
        dumps: List<FirmwareDump>,
        folders: List<FirmwareFolder>
    ) {
        if (dumps.isEmpty() && folders.isEmpty()) {
            Toast.makeText(context, "لم يتم اختيار أي ملفات أو مجلدات لضغطها", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val exportDir = File(context.cacheDir, "exports")
            if (!exportDir.exists()) exportDir.mkdirs()

            val cleanArchiveName = archiveName
                .replace("[^a-zA-Z0-9._\u0600-\u06FF-]".toRegex(), "_")
                .ifBlank { "Firmware_Archive_${System.currentTimeMillis()}" }

            val zipFile = File(exportDir, "$cleanArchiveName.zip")
            if (zipFile.exists()) zipFile.delete()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
                val writtenEntryNames = mutableSetOf<String>()

                // 1. Write dumps to zip
                dumps.forEach { dump ->
                    val file = if (dump.localFilePath.isNotBlank()) File(dump.localFilePath) else null
                    val cleanTitle = dump.title.replace("[^a-zA-Z0-9._\\u0600-\\u06FF-]".toRegex(), "_")
                    val entryName = if (cleanTitle.isNotBlank()) "$cleanTitle.${dump.fileType.lowercase()}" else "${dump.processorType}_dump.bin"
                    val uniqueEntry = getUniqueEntryName(entryName, writtenEntryNames)
                    writtenEntryNames.add(uniqueEntry)

                    if (file != null && file.exists()) {
                        zos.putNextEntry(ZipEntry(uniqueEntry))
                        BufferedInputStream(FileInputStream(file)).use { bis ->
                            bis.copyTo(zos)
                        }
                        zos.closeEntry()
                    } else {
                        // Create a metadata placeholder if real binary isn't local
                        zos.putNextEntry(ZipEntry("$uniqueEntry.txt"))
                        val info = "ملف سوفت وير: ${dump.title} - ${dump.applianceModel} (${dump.processorType})\nالحجم: ${dump.fileSizeBytes} بايت\nالرابط: ${dump.fileUrl}\nالبراء إلكترونيك"
                        zos.write(info.toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }
                }

                // If only empty folders were selected
                if (dumps.isEmpty()) {
                    folders.forEach { folder ->
                        val folderEntry = "${folder.name}/info.txt"
                        zos.putNextEntry(ZipEntry(folderEntry))
                        val info = "مجلد: ${folder.name}\nالبراء إلكترونيك"
                        zos.write(info.toByteArray(Charsets.UTF_8))
                        zos.closeEntry()
                    }
                }
            }

            // Share via FileProvider
            val contentUri: Uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    zipFile
                )
            } catch (_: Throwable) {
                Uri.fromFile(zipFile)
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "$archiveName.zip")
                putExtra(Intent.EXTRA_TEXT, "مشاركة أرشيف سوفتات مضغوط من تطبيق البراء إلكترونيك ⚡")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(shareIntent, "مشاركة الأرشيف المضغوط ZIP").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })

            Toast.makeText(context, "تم تجهيز الأرشيف المضغوط بنجاح (${String.format("%.1f", zipFile.length() / 1024.0)} KB) 📦", Toast.LENGTH_SHORT).show()
        } catch (e: Throwable) {
            android.util.Log.e("ZipHelper", "Error zipping dumps", e)
            Toast.makeText(context, "فشل ضغط الملفات: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getUniqueEntryName(name: String, existing: Set<String>): String {
        if (!existing.contains(name)) return name
        val dotIdx = name.lastIndexOf('.')
        val base = if (dotIdx > 0) name.substring(0, dotIdx) else name
        val ext = if (dotIdx > 0) name.substring(dotIdx) else ""
        var count = 1
        while (existing.contains("${base}_$count$ext")) {
            count++
        }
        return "${base}_$count$ext"
    }
}
