package com.jstensix.cardspending.backup

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class DriveClientTest {
    private val server = MockWebServer()
    private lateinit var client: DriveClient

    @Before fun setUp() {
        server.start()
        client = DriveClient(baseUrl = server.url("/"))
    }

    @After fun tearDown() = server.close()

    private fun respond(body: String = "", code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test fun listBackups_sendsQueryAndParses() = runBlocking {
        respond(
            """{"files":[{"id":"b","name":"backup-2.json","createdTime":"2026-11-18T02:00:00Z"},""" +
                """{"id":"a","name":"backup-1.json","createdTime":"2026-11-17T02:00:00Z"}]}""",
        )
        val files = client.listBackups("t")
        val request = server.takeRequest()
        assertEquals("/drive/v3/files", request.url.encodedPath)
        assertEquals("appDataFolder", request.url.queryParameter("spaces"))
        assertEquals("Bearer t", request.headers["Authorization"])
        assertEquals(listOf("b", "a"), files.map { it.id })
        assertEquals("backup-2.json", files.first().name)
    }

    @Test fun upload_putsFileInAppDataFolder() = runBlocking {
        respond("""{"id":"new","name":"backup-x.json","createdTime":"2026-11-18T02:00:00Z"}""")
        val file = client.upload("t", "backup-x.json", """{"cards":[]}""")
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/upload/drive/v3/files", request.url.encodedPath)
        assertEquals("multipart", request.url.queryParameter("uploadType"))
        val body = request.body!!.utf8()
        assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
        assertTrue(body.contains("""{"cards":[]}"""))
        assertEquals("new", file.id)
    }

    @Test fun download_returnsBody() = runBlocking {
        respond("""{"schemaVersion":1}""")
        assertEquals("""{"schemaVersion":1}""", client.download("t", "abc"))
        val request = server.takeRequest()
        assertEquals("/drive/v3/files/abc", request.url.encodedPath)
        assertEquals("media", request.url.queryParameter("alt"))
    }

    @Test fun delete_sendsDelete() = runBlocking {
        respond(code = 204)
        client.delete("t", "abc")
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/drive/v3/files/abc", request.url.encodedPath)
    }

    @Test fun unauthorized_throwsWithCode() = runBlocking {
        respond("""{"error":"invalid"}""", code = 401)
        try {
            client.listBackups("t")
            fail("expected DriveHttpException")
        } catch (e: DriveHttpException) {
            assertEquals(401, e.code)
        }
    }

    @Test fun fetchEmail_readsUserinfo() = runBlocking {
        respond("""{"sub":"1","email":"a@b.c","email_verified":true}""")
        assertEquals("a@b.c", client.fetchEmail("t"))
        assertEquals("/oauth2/v3/userinfo", server.takeRequest().url.encodedPath)
    }
}
