package com.jstensix.cardspending.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DriveClient(
    private val http: OkHttpClient = OkHttpClient(),
    private val baseUrl: HttpUrl = "https://www.googleapis.com/".toHttpUrl(),
) : DriveApi {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json; charset=UTF-8".toMediaType()

    @Serializable private data class FileList(val files: List<DriveFile> = emptyList())
    @Serializable private data class Metadata(val name: String, val parents: List<String>)
    @Serializable private data class UserInfo(val email: String)

    override suspend fun listBackups(token: String): List<DriveFile> {
        val url = baseUrl.newBuilder().addPathSegments("drive/v3/files")
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("fields", "files(id,name,createdTime)")
            .addQueryParameter("orderBy", "createdTime desc")
            .addQueryParameter("pageSize", "100")
            .build()
        return json.decodeFromString(FileList.serializer(), call(token, Request.Builder().url(url).get())).files
    }

    override suspend fun upload(token: String, name: String, content: String): DriveFile {
        val url = baseUrl.newBuilder().addPathSegments("upload/drive/v3/files")
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,name,createdTime")
            .build()
        val metadata = json.encodeToString(Metadata.serializer(), Metadata(name, listOf("appDataFolder")))
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody(jsonType))
            .addPart(content.toRequestBody(jsonType))
            .build()
        return json.decodeFromString(DriveFile.serializer(), call(token, Request.Builder().url(url).post(body)))
    }

    override suspend fun download(token: String, fileId: String): String {
        val url = baseUrl.newBuilder().addPathSegments("drive/v3/files").addPathSegment(fileId)
            .addQueryParameter("alt", "media").build()
        return call(token, Request.Builder().url(url).get())
    }

    override suspend fun delete(token: String, fileId: String) {
        val url = baseUrl.newBuilder().addPathSegments("drive/v3/files").addPathSegment(fileId).build()
        call(token, Request.Builder().url(url).delete())
    }

    override suspend fun fetchEmail(token: String): String {
        val url = baseUrl.newBuilder().addPathSegments("oauth2/v3/userinfo").build()
        return json.decodeFromString(UserInfo.serializer(), call(token, Request.Builder().url(url).get())).email
    }

    private suspend fun call(token: String, builder: Request.Builder): String = withContext(Dispatchers.IO) {
        http.newCall(builder.header("Authorization", "Bearer $token").build()).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw DriveHttpException(response.code, text.take(200))
            text
        }
    }
}
