package com.scanly.data.storage.drive

import com.scanly.data.storage.GoogleDriveAuthType
import com.scanly.data.storage.StorageConfig
import com.scanly.data.storage.StorageProvider
import com.scanly.data.storage.StorageProviderType
import com.scanly.data.storage.StorageTestResult
import com.scanly.data.storage.StorageUploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.regex.Pattern

class GoogleDriveStorageProvider(
    val config: StorageConfig.GoogleDrive,
    private val client: OkHttpClient = OkHttpClient(),
    private val customBaseUrl: String? = null
) : StorageProvider {

    override val id: String get() = config.id
    override val type: StorageProviderType get() = StorageProviderType.GOOGLE_DRIVE
    override val displayName: String get() = config.displayName
    override val isEnabled: Boolean get() = config.isEnabled

    private val apiHost: String get() = customBaseUrl?.trimEnd('/') ?: "https://www.googleapis.com"

    private fun getAccessToken(): String {
        val trimmed = config.credentialsJson.trim()
        if (trimmed.startsWith("{") && trimmed.contains("\"access_token\"")) {
            val pattern = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"")
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) return matcher.group(1) ?: trimmed
        }
        return trimmed
    }

    override suspend fun testConnection(): StorageTestResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val token = getAccessToken()
            val url = if (!config.folderId.isNullOrBlank()) {
                "$apiHost/drive/v3/files/${config.folderId}?fields=id,name,capabilities"
            } else {
                "$apiHost/drive/v3/about?fields=user,storageQuota"
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val userEmail = extractJsonString(body, "emailAddress") ?: "Authorized User"
                    StorageTestResult(
                        isSuccess = true,
                        message = "Connected to Google Drive ($userEmail)",
                        latencyMs = latency,
                        details = body
                    )
                } else {
                    StorageTestResult(
                        isSuccess = false,
                        message = "Google Drive check failed (HTTP ${response.code}): ${response.message}",
                        latencyMs = latency,
                        details = body
                    )
                }
            }
        } catch (e: Exception) {
            StorageTestResult(
                isSuccess = false,
                message = "Connection error: ${e.message ?: e.javaClass.simpleName}",
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    override suspend fun uploadFile(
        file: File,
        mimeType: String,
        remotePath: String,
        progressCallback: ((bytesSent: Long, totalBytes: Long) -> Unit)?
    ): StorageUploadResult = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val fileName = if (remotePath.contains("/")) remotePath.substringAfterLast("/") else remotePath
            val parentFolderJson = if (!config.folderId.isNullOrBlank()) ",\"parents\":[\"${config.folderId}\"]" else ""
            val metadataJson = """{"name":"$fileName","mimeType":"$mimeType"$parentFolderJson}"""

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaTypeOrNull() ?: MultipartBody.FORM)
                .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                .addPart(file.asRequestBody(mimeType.toMediaTypeOrNull()))
                .build()

            val request = Request.Builder()
                .url("$apiHost/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()

            progressCallback?.invoke(file.length(), file.length())

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val fileId = extractJsonString(body, "id") ?: "drive_${System.currentTimeMillis()}"
                    StorageUploadResult(
                        isSuccess = true,
                        remoteId = fileId,
                        remoteUrl = "https://drive.google.com/file/d/$fileId/view",
                        bytesUploaded = file.length()
                    )
                } else {
                    StorageUploadResult(
                        isSuccess = false,
                        errorMessage = "Drive upload failed (HTTP ${response.code}): $body"
                    )
                }
            }
        } catch (e: Exception) {
            StorageUploadResult(
                isSuccess = false,
                errorMessage = e.message ?: "Drive upload exception"
            )
        }
    }

    override suspend fun deleteFile(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val request = Request.Builder()
                .url("$apiHost/drive/v3/files/$remotePath")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 204 || response.code == 404
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Pattern.compile("\"$key\"\\s*:\\s*\"([^\"]+)\"")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1) else null
    }
}
