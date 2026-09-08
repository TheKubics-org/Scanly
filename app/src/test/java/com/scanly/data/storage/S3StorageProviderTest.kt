package com.scanly.data.storage

import com.scanly.data.storage.s3.S3StorageProvider
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

class S3StorageProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var config: StorageConfig.CloudflareR2

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()

        val mockEndpoint = server.url("/").toString().trimEnd('/')
        config = StorageConfig.CloudflareR2(
            id = "test_r2",
            displayName = "Cloudflare R2 Bucket",
            accessKeyId = "TEST_ACCESS_KEY_ID_123",
            secretAccessKey = "TEST_SECRET_ACCESS_KEY_456_VERY_LONG_KEY",
            bucketName = "scanly-backups",
            endpointUrl = mockEndpoint,
            region = "auto",
            isEnabled = true
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `testConnection sends signed GET request and returns success on 200`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("<ListBucketResult><Name>scanly-backups</Name></ListBucketResult>")
        )

        val provider = S3StorageProvider(config, client)
        val result = provider.testConnection()

        assertTrue(result.isSuccess)
        assertTrue(result.message.contains("scanly-backups"))

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/scanly-backups?max-keys=1", req.path)

        val authHeader = req.getHeader("Authorization")
        assertNotNull(authHeader)
        assertTrue(authHeader!!.startsWith("AWS4-HMAC-SHA256"))
        assertTrue(authHeader.contains("Credential=TEST_ACCESS_KEY_ID_123/"))

        val amzDate = req.getHeader("x-amz-date")
        assertNotNull(amzDate)

        val amzSha256 = req.getHeader("x-amz-content-sha256")
        assertNotNull(amzSha256)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", amzSha256)
    }

    @Test
    fun `testConnection returns failure on 403 Forbidden`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("<Error><Code>InvalidAccessKeyId</Code></Error>")
        )

        val provider = S3StorageProvider(config, client)
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("403"))
    }

    @Test
    fun `uploadFile sends signed PUT request with file payload and returns success`() = runBlocking {
        val testFile = tempFolder.newFile("contract.pdf").apply {
            writeBytes("PDF_SIGNATURE_DATA_99999".toByteArray())
        }

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"b10a8db164e0754105b7a99be72e3fe5\"")
        )

        val provider = S3StorageProvider(config, client)
        var progressReported = false
        val result = provider.uploadFile(
            file = testFile,
            mimeType = "application/pdf",
            remotePath = "2026/09/contract.pdf"
        ) { sent, total ->
            if (sent == total) progressReported = true
        }

        assertTrue(result.isSuccess)
        assertEquals("2026/09/contract.pdf", result.remoteId)
        assertEquals(testFile.length(), result.bytesUploaded)
        assertTrue(progressReported)

        val req = server.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/scanly-backups/2026/09/contract.pdf", req.path)

        val auth = req.getHeader("Authorization")
        assertNotNull(auth)
        assertTrue(auth!!.startsWith("AWS4-HMAC-SHA256"))

        val contentSha256 = req.getHeader("x-amz-content-sha256")
        assertNotNull(contentSha256)
        // Verify payload matches
        assertEquals("PDF_SIGNATURE_DATA_99999", req.body.readUtf8())
    }

    @Test
    fun `uploadFile returns failure on server 500 error`() = runBlocking {
        val testFile = tempFolder.newFile("fail.pdf").apply {
            writeBytes("DUMMY".toByteArray())
        }

        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("<Error><Code>InternalError</Code></Error>")
        )

        val provider = S3StorageProvider(config, client)
        val result = provider.uploadFile(
            file = testFile,
            mimeType = "application/pdf",
            remotePath = "fail.pdf"
        )

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("500"))
    }

    @Test
    fun `deleteFile sends signed DELETE request and returns true on 204`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
        )

        val provider = S3StorageProvider(config, client)
        val success = provider.deleteFile("2026/09/contract.pdf")

        assertTrue(success)
        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/scanly-backups/2026/09/contract.pdf", req.path)
        val auth = req.getHeader("Authorization")
        assertNotNull(auth)
        assertTrue(auth!!.startsWith("AWS4-HMAC-SHA256"))
    }
}
