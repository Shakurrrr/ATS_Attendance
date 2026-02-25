package com.ats.attendance.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

object DownloadUtils {

    /**
     * Saves a local file into the user's public Downloads folder using MediaStore.
     * Works cleanly on Android 10+ (including minSdk 31) with no permissions.
     *
     * @return Uri of the saved download (can be used to open/share)
     */
    @Throws(IOException::class)
    fun savePdfToDownloads(
        context: Context,
        sourceFile: File,
        displayName: String = sourceFile.name
    ): Uri {
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values)
            ?: throw IOException("Failed to create MediaStore record")

        try {
            resolver.openOutputStream(itemUri, "w")?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            } ?: throw IOException("Failed to open output stream")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)

            return itemUri
        } catch (e: Exception) {
            // cleanup partially written item
            resolver.delete(itemUri, null, null)
            throw e
        }
    }
}