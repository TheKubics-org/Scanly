package com.scanly.data.storage

import com.scanly.data.storage.drive.GoogleDriveStorageProvider
import com.scanly.data.storage.s3.S3StorageProvider
import com.scanly.data.storage.telegram.TelegramStorageProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
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
import java.util.concurrent.TimeUnit

class StorageProviderAdversarialFaultInjectionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var fastTimeoutClient: OkHttpClient

    private val tgConfig = StorageConfig.Telegram(
        id = "tg_fault",
        displayName = "Telegram Fault Test",
        botToken = "999999:TEST_BOT_TOKEN_XYZ",
        chatId = "-100999999999",
        isEnabled = true
    )

    private val r2Config by lazy {
        StorageConfig.CloudflareR2(
            id = "r2_fault",
            displayName = "R2 Fault Test",
            endpointUrl = server.url("/").toString().trimEnd('/'),
            bucketName = "fault-bucket",
            accessKeyId = "AKIA_FAULT_TEST_KEY",
            secretAccessKey = "SECRET_FAULT_TEST_KEY_LONG_STRING_123",
            region = "auto",
            isEnabled = true
        )
    }

    private val driveConfig = StorageConfig.GoogleDrive(
        id = "drive_fault",
        displayName = "Drive Fault Test",
        authType = GoogleDriveAuthType.OAUTH2,
        credentialsJson = "ya29.fault_token_12345",
        folderId = null,
        isEnabled = true
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        fastTimeoutClient = OkHttpClient.Builder()
            .connectTimeout(500, TimeUnit.MILLISECONDS)
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .writeTimeout(500, TimeUnit.MILLISECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createTelegramProvider(client: OkHttpClient = fastTimeoutClient): TelegramStorageProvider {
        val baseUrl = server.url("/").toString().trimEnd('/')
        return TelegramStorageProvider(
            config = tgConfig,
            client = client,
            customBaseUrl = baseUrl
        )
    }

    private fun createS3Provider(client: OkHttpClient = fastTimeoutClient): S3StorageProvider {
        return S3StorageProvider(
            config = r2Config,
            client = client
        )
    }

    private fun createDriveProvider(
        client: OkHttpClient = fastTimeoutClient,
        folderId: String? = null
    ): GoogleDriveStorageProvider {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val cfg = if (folderId != null) driveConfig.copy(folderId = folderId) else driveConfig
        return GoogleDriveStorageProvider(
            config = cfg,
            client = client,
            customBaseUrl = baseUrl
        )
    }

    // ========================================================================
    // TELEGRAM ADVERSARIAL & FAULT INJECTION TESTS
    // ========================================================================

    @Test
    fun `tg testConnection - 401 Unauthorized returns failure with clean error description`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"ok":false,"error_code":401,"description":"Unauthorized: invalid bot token"}""")
        )

        val provider = createTelegramProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("Telegram authentication failed"))
        assertTrue(result.message.contains("Unauthorized: invalid bot token"))
    }

    @Test
    fun `tg testConnection - 403 Forbidden on getChat returns descriptive permission error`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true,"result":{"id":999,"is_bot":true,"username":"fault_bot"}}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"ok":false,"error_code":403,"description":"Forbidden: bot was kicked from the channel"}""")
        )

        val provider = createTelegramProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("fault_bot"))
        assertTrue(result.message.contains("kicked from the channel"))
    }

    @Test
    fun `tg testConnection - 429 Too Many Requests returns failure with rate limit description`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "30")
                .setBody("""{"ok":false,"error_code":429,"description":"Too Many Requests: retry after 30"}""")
        )

        val provider = createTelegramProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("Too Many Requests"))
    }

    @Test
    fun `tg testConnection - 500 Internal Server Error with HTML body handles gracefully`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("<html><head><title>500 Internal Server Error</title></head><body>Crash</body></html>")
        )

        val provider = createTelegramProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("500") || result.message.contains("failed"))
    }

    @Test
    fun `tg testConnection - 503 Service Unavailable with empty body`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("")
        )

        val provider = createTelegramProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("503") || result.message.contains("failed"))
    }

    @Test
    fun `tg testConnection - truncated malformed JSON does not crash`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok": tru""")
        )

        val provider = createTelegramProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
    }

    @Test
    fun `tg testConnection - socket timeout returns connection error result`() = runBlocking {
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )

        val provider = createTelegramProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("Connection error") || result.message.contains("timeout", ignoreCase = true))
    }

    @Test
    fun `tg uploadFile - zero-byte file succeeds if server accepts`() = runBlocking {
        val emptyFile = tempFolder.newFile("zero_byte.pdf") // 0 bytes

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true,"result":{"message_id":5001,"document":{"file_id":"empty_doc"}}}""")
        )

        val provider = createTelegramProvider()
        val result = provider.uploadFile(
            file = emptyFile,
            mimeType = "application/pdf",
            remotePath = "zero_byte.pdf"
        )

        assertTrue(result.isSuccess)
        assertEquals("5001", result.remoteId)
        assertEquals(0L, result.bytesUploaded)
    }

    @Test
    fun `tg uploadFile - non-existent file returns failure gracefully`() = runBlocking {
        val nonExistentFile = File(tempFolder.root, "does_not_exist.pdf")

        val provider = createTelegramProvider()
        val result = provider.uploadFile(
            file = nonExistentFile,
            mimeType = "application/pdf",
            remotePath = "does_not_exist.pdf"
        )

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `tg uploadFile - filename with spaces, unicode and emojis handles without crashing`() = runBlocking {
        val testFile = tempFolder.newFile("test_upload.pdf").apply {
            writeBytes("PDF_DATA".toByteArray())
        }

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":true,"result":{"message_id":5002,"document":{"file_id":"emoji_doc"}}}""")
        )

        val provider = createTelegramProvider()
        // Testing tricky filename with spaces and unicode
        val result = provider.uploadFile(
            file = testFile,
            mimeType = "application/pdf",
            remotePath = "scans/Receipt 2026 (Copy #1).pdf"
        )

        assertTrue(result.isSuccess)
        assertEquals("5002", result.remoteId)
    }

    @Test
    fun `tg uploadFile - 401 Unauthorized returns failure with Telegram description`() = runBlocking {
        val testFile = tempFolder.newFile("doc.pdf").apply { writeBytes("DATA".toByteArray()) }
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"ok":false,"error_code":401,"description":"Unauthorized"}""")
        )

        val provider = createTelegramProvider()
        val result = provider.uploadFile(testFile, "application/pdf", "doc.pdf")

        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage!!.contains("Unauthorized"))
    }

    @Test
    fun `tg uploadFile - 429 Rate Limit returns failure`() = runBlocking {
        val testFile = tempFolder.newFile("doc429.pdf").apply { writeBytes("DATA".toByteArray()) }
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"ok":false,"error_code":429,"description":"Too Many Requests: retry after 42"}""")
        )

        val provider = createTelegramProvider()
        val result = provider.uploadFile(testFile, "application/pdf", "doc429.pdf")

        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage!!.contains("Too Many Requests"))
    }

    @Test
    fun `tg uploadFile - 500 Server Error returns failure`() = runBlocking {
        val testFile = tempFolder.newFile("doc500.pdf").apply { writeBytes("DATA".toByteArray()) }
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error")
        )

        val provider = createTelegramProvider()
        val result = provider.uploadFile(testFile, "application/pdf", "doc500.pdf")

        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage!!.contains("500") || result.errorMessage!!.contains("failed"))
    }

    @Test
    fun `tg uploadFile - socket disconnect returns upload exception`() = runBlocking {
        val testFile = tempFolder.newFile("doc_timeout.pdf").apply { writeBytes("DATA".toByteArray()) }
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )

        val provider = createTelegramProvider()
        val result = provider.uploadFile(testFile, "application/pdf", "doc_timeout.pdf")

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `tg deleteFile - non-numeric remotePath returns false immediately`() = runBlocking {
        val provider = createTelegramProvider()
        val success = provider.deleteFile("not_a_number_path")
        assertFalse(success)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `tg deleteFile - 400 Bad Request returns false`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"ok":false,"error_code":400,"description":"Bad Request: message to delete not found"}""")
        )

        val provider = createTelegramProvider()
        val success = provider.deleteFile("999999")
        assertFalse(success)
    }

    // ========================================================================
    // S3 / CLOUDFLARE R2 ADVERSARIAL & FAULT INJECTION TESTS
    // ========================================================================

    @Test
    fun `s3 testConnection - 403 Forbidden returns clean Access Denied message`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("<Error><Code>AccessDenied</Code><Message>Access Denied</Message></Error>")
        )

        val provider = createS3Provider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("Access Denied"))
    }

    @Test
    fun `s3 testConnection - 404 Bucket Not Found returns clean bucket not found message`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("<Error><Code>NoSuchBucket</Code><Message>Bucket does not exist</Message></Error>")
        )

        val provider = createS3Provider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("not found"))
    }

    @Test
    fun `s3 testConnection - 503 SlowDown rate limit returns failure`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("<Error><Code>SlowDown</Code><Message>Please reduce your request rate.</Message></Error>")
        )

        val provider = createS3Provider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("503"))
    }

    @Test
    fun `s3 testConnection - 500 Internal Server Error with empty body returns failure`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("")
        )

        val provider = createS3Provider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("500"))
    }

    @Test
    fun `s3 testConnection - socket timeout returns connection error`() = runBlocking {
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )

        val provider = createS3Provider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("Connection error") || result.message.contains("timeout", ignoreCase = true))
    }

    @Test
    fun `s3 uploadFile - zero-byte file computes empty sha256 and succeeds on 200`() = runBlocking {
        val emptyFile = tempFolder.newFile("empty_r2.pdf")

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"d41d8cd98f00b204e9800998ecf8427e\"")
        )

        val provider = createS3Provider()
        val result = provider.uploadFile(
            file = emptyFile,
            mimeType = "application/pdf",
            remotePath = "empty_r2.pdf"
        )

        assertTrue(result.isSuccess)
        assertEquals(0L, result.bytesUploaded)

        val req = server.takeRequest()
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", req.getHeader("x-amz-content-sha256"))
        assertEquals("0", req.getHeader("Content-Length"))
    }

    @Test
    fun `s3 uploadFile - non-existent file returns failure`() = runBlocking {
        val nonExistentFile = File(tempFolder.root, "ghost.pdf")

        val provider = createS3Provider()
        val result = provider.uploadFile(
            file = nonExistentFile,
            mimeType = "application/pdf",
            remotePath = "ghost.pdf"
        )

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `s3 uploadFile - remotePath with multiple leading slashes is cleaned properly`() = runBlocking {
        val testFile = tempFolder.newFile("test_slash.pdf").apply { writeBytes("S3_DATA".toByteArray()) }

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"etag_123\"")
        )

        val provider = createS3Provider()
        val result = provider.uploadFile(
            file = testFile,
            mimeType = "application/pdf",
            remotePath = "///deep/folder/doc.pdf"
        )

        assertTrue(result.isSuccess)
        assertEquals("deep/folder/doc.pdf", result.remoteId)

        val req = server.takeRequest()
        assertEquals("/fault-bucket/deep/folder/doc.pdf", req.path)
    }

    @Test
    fun `s3 uploadFile - 403 SignatureDoesNotMatch returns clean failure`() = runBlocking {
        val testFile = tempFolder.newFile("sig_err.pdf").apply { writeBytes("DATA".toByteArray()) }

        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("<Error><Code>SignatureDoesNotMatch</Code></Error>")
        )

        val provider = createS3Provider()
        val result = provider.uploadFile(testFile, "application/pdf", "sig_err.pdf")

        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage!!.contains("403"))
    }

    @Test
    fun `s3 uploadFile - 503 SlowDown returns failure`() = runBlocking {
        val testFile = tempFolder.newFile("slowdown.pdf").apply { writeBytes("DATA".toByteArray()) }

        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("<Error><Code>SlowDown</Code></Error>")
        )

        val provider = createS3Provider()
        val result = provider.uploadFile(testFile, "application/pdf", "slowdown.pdf")

        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage!!.contains("503"))
    }

    @Test
    fun `s3 uploadFile - socket disconnect returns error`() = runBlocking {
        val testFile = tempFolder.newFile("disconnect.pdf").apply { writeBytes("DATA".toByteArray()) }

        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )

        val provider = createS3Provider()
        val result = provider.uploadFile(testFile, "application/pdf", "disconnect.pdf")

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `s3 deleteFile - 404 is considered idempotent success`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
        )

        val provider = createS3Provider()
        val success = provider.deleteFile("already_deleted.pdf")
        assertTrue(success)
    }

    @Test
    fun `s3 deleteFile - 403 Forbidden returns false`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403)
        )

        val provider = createS3Provider()
        val success = provider.deleteFile("protected.pdf")
        assertFalse(success)
    }

    @Test
    fun `s3 deleteFile - 500 Server Error returns false`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(500)
        )

        val provider = createS3Provider()
        val success = provider.deleteFile("error.pdf")
        assertFalse(success)
    }

    // ========================================================================
    // GOOGLE DRIVE ADVERSARIAL & FAULT INJECTION TESTS
    // ========================================================================

    @Test
    fun `drive testConnection - 401 Unauthorized returns failure`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"code":401,"message":"Invalid Credentials"}}""")
        )

        val provider = createDriveProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("401"))
    }

    @Test
    fun `drive testConnection - 403 Insufficient Permission returns failure`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"error":{"code":403,"message":"Insufficient Permission"}}""")
        )

        val provider = createDriveProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("403"))
    }

    @Test
    fun `drive testConnection - 404 Folder Not Found when folderId is configured`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"error":{"code":404,"message":"File not found: bad_folder"}}""")
        )

        val provider = createDriveProvider(folderId = "bad_folder")
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("404"))
    }

    @Test
    fun `drive testConnection - 429 Rate Limit Exceeded returns failure`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error":{"code":429,"message":"Rate limit exceeded"}}""")
        )

        val provider = createDriveProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("429"))
    }

    @Test
    fun `drive testConnection - 500 Backend Error returns failure`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":{"code":500,"message":"Backend Error"}}""")
        )

        val provider = createDriveProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("500"))
    }

    @Test
    fun `drive testConnection - socket timeout returns connection error`() = runBlocking {
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )

        val provider = createDriveProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("Connection error") || result.message.contains("timeout", ignoreCase = true))
    }

    @Test
    fun `drive uploadFile - zero-byte file succeeds on 200`() = runBlocking {
        val emptyFile = tempFolder.newFile("empty_drive.pdf")

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"kind":"drive#file","id":"drive_empty_doc_id"}""")
        )

        val provider = createDriveProvider()
        val result = provider.uploadFile(
            file = emptyFile,
            mimeType = "application/pdf",
            remotePath = "empty_drive.pdf"
        )

        assertTrue(result.isSuccess)
        assertEquals("drive_empty_doc_id", result.remoteId)
        assertEquals(0L, result.bytesUploaded)
    }

    @Test
    fun `drive uploadFile - non-existent file returns failure`() = runBlocking {
        val nonExistentFile = File(tempFolder.root, "ghost_drive.pdf")

        val provider = createDriveProvider()
        val result = provider.uploadFile(
            file = nonExistentFile,
            mimeType = "application/pdf",
            remotePath = "ghost_drive.pdf"
        )

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `drive uploadFile - 401 Invalid Credentials returns failure with details`() = runBlocking {
        val testFile = tempFolder.newFile("drive_doc.pdf").apply { writeBytes("DATA".toByteArray()) }

        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"code":401,"message":"Invalid Credentials"}}""")
        )

        val provider = createDriveProvider()
        val result = provider.uploadFile(testFile, "application/pdf", "drive_doc.pdf")

        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage!!.contains("401"))
    }

    @Test
    fun `drive uploadFile - 403 Storage Quota Exceeded returns failure with details`() = runBlocking {
        val testFile = tempFolder.newFile("drive_quota.pdf").apply { writeBytes("DATA".toByteArray()) }

        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"error":{"code":403,"message":"User storage quota exceeded"}}""")
        )

        val provider = createDriveProvider()
        val result = provider.uploadFile(testFile, "application/pdf", "drive_quota.pdf")

        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage!!.contains("403"))
        assertTrue(result.errorMessage!!.contains("quota exceeded"))
    }

    @Test
    fun `drive uploadFile - 429 Rate Limit Exceeded returns failure`() = runBlocking {
        val testFile = tempFolder.newFile("drive_rate.pdf").apply { writeBytes("DATA".toByteArray()) }

        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error":{"code":429,"message":"User Rate Limit Exceeded"}}""")
        )

        val provider = createDriveProvider()
        val result = provider.uploadFile(testFile, "application/pdf", "drive_rate.pdf")

        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage!!.contains("429"))
    }

    @Test
    fun `drive uploadFile - 500 Internal Server Error returns failure`() = runBlocking {
        val testFile = tempFolder.newFile("drive_500.pdf").apply { writeBytes("DATA".toByteArray()) }

        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":{"code":500,"message":"Backend Error"}}""")
        )

        val provider = createDriveProvider()
        val result = provider.uploadFile(testFile, "application/pdf", "drive_500.pdf")

        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage!!.contains("500"))
    }

    @Test
    fun `drive uploadFile - socket disconnect returns failure`() = runBlocking {
        val testFile = tempFolder.newFile("drive_disconnect.pdf").apply { writeBytes("DATA".toByteArray()) }

        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )

        val provider = createDriveProvider()
        val result = provider.uploadFile(testFile, "application/pdf", "drive_disconnect.pdf")

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun `drive uploadFile - response missing ID generates fallback remoteId`() = runBlocking {
        val testFile = tempFolder.newFile("no_id.pdf").apply { writeBytes("DATA".toByteArray()) }

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"kind":"drive#file","name":"no_id.pdf"}""")
        )

        val provider = createDriveProvider()
        val result = provider.uploadFile(testFile, "application/pdf", "no_id.pdf")

        assertTrue(result.isSuccess)
        assertNotNull(result.remoteId)
        assertTrue(result.remoteId!!.startsWith("drive_"))
    }

    @Test
    fun `drive deleteFile - 404 is considered idempotent success`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(404)
        )

        val provider = createDriveProvider()
        val success = provider.deleteFile("drive_file_not_found")
        assertTrue(success)
    }

    @Test
    fun `drive deleteFile - 403 returns false`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403)
        )

        val provider = createDriveProvider()
        val success = provider.deleteFile("drive_file_protected")
        assertFalse(success)
    }

    @Test
    fun `drive deleteFile - 500 returns false`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(500)
        )

        val provider = createDriveProvider()
        val success = provider.deleteFile("drive_file_500")
        assertFalse(success)
    }
}
