package com.scanly.data.storage

import com.scanly.data.storage.drive.GoogleDriveStorageProvider
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

class GoogleDriveStorageProviderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var config: StorageConfig.GoogleDrive

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient()

        config = StorageConfig.GoogleDrive(
            id = "test_drive",
            displayName = "My Google Drive",
            authType = GoogleDriveAuthType.OAUTH2,
            credentialsJson = "ya29.a0AfH6SMTestToken12345",
            folderId = null,
            isEnabled = true
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createProvider(customConfig: StorageConfig.GoogleDrive = config): GoogleDriveStorageProvider {
        val baseUrl = server.url("/").toString().trimEnd('/')
        return GoogleDriveStorageProvider(
            config = customConfig,
            client = client,
            customBaseUrl = baseUrl
        )
    }

    @Test
    fun `testConnection calls about endpoint and succeeds on 200`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"user":{"displayName":"John Doe","emailAddress":"john.doe@gmail.com"}}""")
        )

        val provider = createProvider()
        val result = provider.testConnection()

        assertTrue(result.isSuccess)
        assertTrue(result.message.contains("john.doe@gmail.com"))

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/drive/v3/about?fields=user,storageQuota", req.path)
        assertEquals("Bearer ya29.a0AfH6SMTestToken12345", req.getHeader("Authorization"))
    }

    @Test
    fun `testConnection calls folder endpoint when folderId is specified`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"folder_xyz_123","name":"ScanlyBackups"}""")
        )

        val folderConfig = config.copy(folderId = "folder_xyz_123")
        val provider = createProvider(folderConfig)
        val result = provider.testConnection()

        assertTrue(result.isSuccess)

        val req = server.takeRequest()
        assertEquals("GET", req.method)
        assertEquals("/drive/v3/files/folder_xyz_123?fields=id,name,capabilities", req.path)
    }

    @Test
    fun `testConnection extracts access_token when JSON credentials provided`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"user":{"displayName":"OAuth User","emailAddress":"oauth@test.com"}}""")
        )

        val jsonConfig = config.copy(
            credentialsJson = """{"access_token": "ya29.parsed_from_json", "token_type": "Bearer"}"""
        )
        val provider = createProvider(jsonConfig)
        val result = provider.testConnection()

        assertTrue(result.isSuccess)
        val req = server.takeRequest()
        assertEquals("Bearer ya29.parsed_from_json", req.getHeader("Authorization"))
    }

    @Test
    fun `testConnection returns failure on 401 unauthorized`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"code":401,"message":"Invalid Credentials"}}""")
        )

        val provider = createProvider()
        val result = provider.testConnection()

        assertFalse(result.isSuccess)
        assertTrue(result.message.contains("401"))
    }

    @Test
    fun `uploadFile performs multipart upload and parses file ID`() = runBlocking {
        val testFile = tempFolder.newFile("scanned_doc.pdf").apply {
            writeBytes("PDF_MOCK_CONTENT_SCAN".toByteArray())
        }

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"kind":"drive#file","id":"drive_file_98765","name":"scanned_doc.pdf","mimeType":"application/pdf"}""")
        )

        val provider = createProvider()
        var progressReported = false
        val result = provider.uploadFile(
            file = testFile,
            mimeType = "application/pdf",
            remotePath = "scanned_doc.pdf"
        ) { sent, total ->
            if (sent == total) progressReported = true
        }

        assertTrue(result.isSuccess)
        assertEquals("drive_file_98765", result.remoteId)
        assertEquals("https://drive.google.com/file/d/drive_file_98765/view", result.remoteUrl)
        assertEquals(testFile.length(), result.bytesUploaded)
        assertTrue(progressReported)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/upload/drive/v3/files?uploadType=multipart", req.path)
        assertEquals("Bearer ya29.a0AfH6SMTestToken12345", req.getHeader("Authorization"))

        val body = req.body.readUtf8()
        assertTrue(body.contains("scanned_doc.pdf"))
        assertTrue(body.contains("application/pdf"))
        assertTrue(body.contains("PDF_MOCK_CONTENT_SCAN"))
    }

    @Test
    fun `uploadFile returns failure on HTTP error`() = runBlocking {
        val testFile = tempFolder.newFile("error_file.pdf").apply {
            writeBytes("DUMMY".toByteArray())
        }

        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"error":{"message":"User storage quota exceeded"}}""")
        )

        val provider = createProvider()
        val result = provider.uploadFile(
            file = testFile,
            mimeType = "application/pdf",
            remotePath = "error_file.pdf"
        )

        assertFalse(result.isSuccess)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("403"))
    }

    @Test
    fun `deleteFile sends DELETE request and returns true on 204`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
        )

        val provider = createProvider()
        val success = provider.deleteFile("drive_file_98765")

        assertTrue(success)

        val req = server.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/drive/v3/files/drive_file_98765", req.path)
        assertEquals("Bearer ya29.a0AfH6SMTestToken12345", req.getHeader("Authorization"))
    }
}
