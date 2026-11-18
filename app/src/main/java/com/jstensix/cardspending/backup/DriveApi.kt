package com.jstensix.cardspending.backup

import java.io.IOException
import kotlinx.serialization.Serializable

@Serializable
data class DriveFile(val id: String, val name: String, val createdTime: String)

class DriveHttpException(val code: Int, message: String) : IOException(message)

/** The slice of Google Drive v3 the backup needs; everything lives in the hidden appDataFolder. */
interface DriveApi {
    /** Newest first. */
    suspend fun listBackups(token: String): List<DriveFile>
    suspend fun upload(token: String, name: String, content: String): DriveFile
    suspend fun download(token: String, fileId: String): String
    suspend fun delete(token: String, fileId: String)
    suspend fun fetchEmail(token: String): String
}
