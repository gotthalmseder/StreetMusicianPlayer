package com.example.streetmusicanplayer

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream

@SuppressLint("StaticFieldLeak")
object FolderManager {

    private lateinit var context: Context
    private lateinit var baseUri: Uri

    fun initialize(context: Context, baseUri: Uri) {
        this.context = context.applicationContext
        this.baseUri = baseUri

        // ✅ SAF-Zugriff dauerhaft merken
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("saf_uri", baseUri.toString())
            .apply()
    }

    private fun getBaseUri(): Uri? = baseUri

    fun getTrackInfosFromFolder(folderName: String): List<TrackInfo> {
        val uris = getMp3FilesFromSAF(folderName)
        return uris.map { uri ->
            val name = getFileNameFromUri(uri)
            val modified = getFileModifiedTime(uri) ?: 0L
            TrackInfo(uri.toString(), name, modified)
        }
    }

    fun deleteTrackFromSAF(trackUri: Uri): Boolean {
        return try {
            val doc = DocumentFile.fromSingleUri(context, trackUri)
            if (doc != null && doc.exists()) {
                val name = doc.name ?: "?"
                val result = doc.delete()
                Log.d("FolderManager", if (result) "🗑️ $name deleted" else "❌ $name not deleted")
                result
            } else {
                Log.w("FolderManager", "⚠️ Could not resolve DocumentFile for $trackUri")
                false
            }
        } catch (e: Exception) {
            Log.e("FolderManager", "❌ Error deleting file: ${e.message}")
            false
        }
    }

    fun getFileModifiedTime(uri: Uri): Long? {
        val docFile = DocumentFile.fromSingleUri(context, uri)
        return docFile?.lastModified()
    }

    fun getFileNameFromUri(uri: Uri): String {
        return try {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getString(0).replace(Regex("\\.mp3$", RegexOption.IGNORE_CASE), "")
                    }
                }

            // Fallback: Dateiname aus URI selbst extrahieren
            val path = uri.path ?: return "Unknown Track1"
            val name = path.substringAfterLast('/').replace(Regex("\\.mp3$", RegexOption.IGNORE_CASE), "")
            name.ifBlank { "Unknown Track3" }
        } catch (e: Exception) {
            Log.w("FolderManager", "⚠️ Fehler beim Lesen des Dateinamens: ${e.message}")
            "Unknown Track2"
        }
    }


    fun moveTrackToFolder(trackUri: Uri, targetFolderName: String): Boolean {
        try {
            val parentFolder = DocumentFile.fromTreeUri(context, baseUri) ?: return false
            val targetFolder = parentFolder.findFile(targetFolderName)
                ?: parentFolder.createDirectory(targetFolderName)

            if (targetFolder == null || !targetFolder.isDirectory) return false

            val inputStream = context.contentResolver.openInputStream(trackUri) ?: return false
            val fileName = DocumentFile.fromSingleUri(context, trackUri)?.name ?: return false

            val newFile = targetFolder.createFile("audio/mpeg", fileName) ?: return false
            val outputStream = context.contentResolver.openOutputStream(newFile.uri) ?: return false

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            // Ursprungsdatei löschen
            DocumentFile.fromSingleUri(context, trackUri)?.delete()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getAvailableFolders(): List<String> {
        val rootDocFile = DocumentFile.fromTreeUri(context, baseUri) ?: return emptyList()

        return rootDocFile.listFiles()
            .filter { it.isDirectory && it.name != null }
            .map { it.name!! }
            .sorted()
    }

    fun getSortedMp3Files(folderName: String): List<Uri> {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val savedOrder = prefs.getStringSet("trackOrder_$folderName", null)

        val allTracks = getMp3FilesFromSAF(folderName)
        if (savedOrder == null) return allTracks

        val fileMap = allTracks.associateBy { uri -> getFileNameFromUri(uri) }
        val ordered = savedOrder.mapNotNull { name -> fileMap[name] }

        val missing = allTracks.filterNot { it in ordered }
        return ordered + missing
    }

    fun getNextFolder(currentFolder: String): String {
        val folders = getAvailableFolders()
        val currentIndex = folders.indexOf(currentFolder)
        return if (currentIndex >= 0 && folders.isNotEmpty()) {
            folders[(currentIndex + 1) % folders.size]
        } else {
            folders.firstOrNull() ?: ""
        }
    }

    fun getFolderUri(folderName: String): Uri? {

        val docId = DocumentsContract.getTreeDocumentId(baseUri)
        val folderPath = "$docId/$folderName"
        return DocumentsContract.buildDocumentUriUsingTree(baseUri, folderPath)
    }

    fun saveInputStreamToSAF(folderUri: Uri, fileName: String, inputStream: InputStream) {
        val mimeType = "audio/mpeg"

        try {
            val newFileUri = DocumentsContract.createDocument(
                context.contentResolver,
                folderUri,
                mimeType,
                fileName
            ) ?: return

            context.contentResolver.openOutputStream(newFileUri)?.use { outputStream ->
                inputStream.copyTo(outputStream)
            }

            Log.d("SAF", "✅ File $fileName saved to SAF folder $folderUri")
        } catch (e: Exception) {
            Log.e("SAF", "❌ Failed to save $fileName to SAF", e)
        }
    }

    fun getMp3FilesFromSAF(folderName: String): List<Uri> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            baseUri,
            DocumentsContract.getTreeDocumentId(baseUri)
        )

        val fileList = mutableListOf<Uri>()

        context.contentResolver.query(
            childrenUri, arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ), null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val mimeType = cursor.getString(1)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (docId.endsWith(folderName)) {
                        val folderUri = DocumentsContract.buildChildDocumentsUriUsingTree(baseUri, docId)

                        context.contentResolver.query(
                            folderUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null
                        )?.use { innerCursor ->
                            while (innerCursor.moveToNext()) {
                                val fileId = innerCursor.getString(0)
                                val fileUri = DocumentsContract.buildDocumentUriUsingTree(baseUri, fileId)

                                try {
                                    if (fileUri.toString().endsWith(".mp3", ignoreCase = true)) {
                                        getFileNameFromUri(fileUri)
                                        fileList.add(fileUri)
                                    }
                                } catch (e: Exception) {
                                    Log.w("FolderManager", "⚠️ Datei konnte nicht gelesen werden: $fileUri (${e.message})")
                                }
                            }
                        }
                    }
                }
            }
        }

        return fileList
    }

    fun ensureFolderExists(folderPath: String): DocumentFile? {
        val base = DocumentFile.fromTreeUri(context, baseUri) ?: return null
        var current = base

        folderPath.split("/").forEach { segment ->
            current = current.findFile(segment) ?: current.createDirectory(segment) ?: return null
        }

        return current
    }
}
