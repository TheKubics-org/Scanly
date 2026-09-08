package com.scanly.data.storage

import com.scanly.data.storage.telegram.TelegramStorageProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TelegramStorageProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var config: StorageConfig.Telegram

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()
        config = StorageConfig.Telegram(
            id = "test_tg",
            displayName = "My Telegram Channel",
            botToken = "123456789:ABCdefGHIjklMNOpqrSTUvwxYZ",
            chatId = "-100123456789",
            isEnabled = true
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createProvider(): TelegramStorageProvider {
        val baseUrl = server.url("/").toString().trimEnd('/')
        return TelegramStorageProvider(
            config = config,
            client = client,
            customBaseUrl = baseUrl
        )
    }

    @Test
    fun `testConnection succeeds when getMe and getChat return valid ok responses`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true,"result":{"id":12345,"is_bot":true,"username":"scanly_bot"}}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true,"result":{"id":-100123456789,"title":"Scanly Archive","type":"channel"}}""")
        )

        val provider = createProvider()
        val result = provider.testConnection()

        assertTrue(result.isSuccess)
        assertTrue(result.message.contains("scanly_bot"))
        assertTrue(result.message.contains("Scanly Archive"))

        val req1 = server.takeRequest()
        assertEquals("/getMe", req1.path)
        assertEquals("GET", req1.method)

        val req2 = server.takeRequest()
        assertEquals("/getChat?chat_id=-100123456789", req2.path)
        assertEquals("GET", req2.method)
    }

    @Test
    fun `testConnection fails when getMe returns 401 unauthorized`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"ok":false,"error_code":401,"description":"Unauthorized"}""")
        )

        val provider = createProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("Unauthorized"))
    }

    @Test
    fun `testConnection fails when getChat returns error`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true,"result":{"id":12345,"is_bot":true,"username":"scanly_bot"}}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"ok":false,"error_code":400,"description":"Bad Request: chat not found"}""")
        )

        val provider = createProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("chat not found"))
    }

    @Test
    fun `uploadFile successfully uploads multipart document and returns message_id`() = runBlocking {
        val testFile = tempFolder.newFile("receipt.pdf").apply {
            writeBytes("PDF_DUMMY_CONTENT_12345".toByteArray())
        }

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true,"result":{"message_id":998877,"document":{"file_id":"BQADBAADtest"}}}""")
        )

        val provider = createProvider()
        var progressCalled = false
        val result = provider.uploadFile(
            file = testFile,
            mimeType = "application/pdf",
            remotePath = "scans/receipt.pdf"
        ) { sent, total ->
            if (sent == total) progressCalled = true
        }

        assertTrue(result.isSuccess)
        assertEquals("998877", result.remoteId)
        assertEquals("tg://msg?chat=-100123456789&id=998877", result.remoteUrl)
        assertEquals(testFile.length(), result.bytesUploaded)
        assertTrue(progressCalled)

        val req = server.takeRequest()
        assertEquals("/sendDocument", req.path)
        assertEquals("POST", req.method)
        val contentType = req.getHeader("Content-Type")
        assertNotNull(contentType)
        assertTrue(contentType!!.startsWith("multipart/form-data"))

        val body = req.body.readUtf8()
        assertTrue(body.contains("-100123456789"))
        assertTrue(body.contains("receipt.pdf"))
        assertTrue(body.contains("PDF_DUMMY_CONTENT_12345"))
    }

    @Test
    fun `uploadFile fails when server returns error`() = runBlocking {
        val testFile = tempFolder.newFile("sample.txt").apply {
            writeText("Hello Telegram")
        }

        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"ok":false,"error_code":400,"description":"Bad Request: file too large"}""")
        )

        val provider = createProvider()
        val result = provider.uploadFile(
            file = testFile,
            mimeType = "text/plain",
            remotePath = "sample.txt"
        )

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("file too large"))
    }

    @Test
    fun `deleteFile sends post to deleteMessage and returns true on success`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true,"result":true}""")
        )

        val provider = createProvider()
        val success = provider.deleteFile("998877")

        assertTrue(success)
        val req = server.takeRequest()
        assertEquals("/deleteMessage", req.path)
        assertEquals("POST", req.method)
        val body = req.body.readUtf8()
        assertTrue(body.contains("998877"))
        assertTrue(body.contains("-100123456789"))
    }
}
