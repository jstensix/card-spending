package com.jstensix.cardspending.backup

import java.io.IOException

internal class FakeDriveApi : DriveApi {
    val files = mutableListOf<Pair<DriveFile, String>>()
    var failUpload = false
    var failList = false
    var uploads = 0
    private var clock = 0

    fun seed(name: String, content: String = "{}") = add(name, content)

    private fun add(name: String, content: String): DriveFile {
        clock++
        val file = DriveFile("id$clock", name, String.format("2026-11-%02dT00:00:00Z", clock))
        files += file to content
        return file
    }

    override suspend fun listBackups(token: String): List<DriveFile> {
        if (failList) throw IOException("network down")
        return files.map { it.first }.sortedByDescending { it.createdTime }
    }

    override suspend fun upload(token: String, name: String, content: String): DriveFile {
        if (failUpload) throw IOException("network down")
        uploads++
        return add(name, content)
    }

    override suspend fun download(token: String, fileId: String) = files.first { it.first.id == fileId }.second

    override suspend fun delete(token: String, fileId: String) {
        files.removeAll { it.first.id == fileId }
    }

    override suspend fun fetchEmail(token: String) = "me@example.com"
}

internal class FakeTokenProvider(var result: TokenResult = TokenResult.Token("t")) : TokenProvider {
    override suspend fun accessToken() = result
}
