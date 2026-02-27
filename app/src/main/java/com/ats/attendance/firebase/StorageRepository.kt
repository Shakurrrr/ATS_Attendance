package com.ats.attendance.firebase

import android.content.Context
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

class StorageRepository(context: Context) {
    private val storage = FirebaseStorage.getInstance()
    private val cacheDir = File(context.cacheDir, "pdf_cache").apply { mkdirs() }

    fun cachedFileFor(storagePath: String): File =
        File(cacheDir, storagePath.replace("/", "_"))

    suspend fun downloadToCache(storagePath: String): File {
        val cached = cachedFileFor(storagePath)
        if (cached.exists() && cached.length() > 0) return cached

        val tmp = File(cacheDir, "${cached.name}.tmp")
        if (tmp.exists()) tmp.delete()

        storage.reference.child(storagePath).getFile(tmp).await()

        if (cached.exists()) cached.delete()
        tmp.renameTo(cached)

        return cached
    }

    suspend fun getDownloadUrl(storagePath: String): String {
        return try {
            val ref = storage.reference.child(storagePath)
            ref.downloadUrl.await().toString() // This will return the download URL
        } catch (e: Exception) {
            throw Exception("Failed to fetch download URL: ${e.message}")
        }
    }
}