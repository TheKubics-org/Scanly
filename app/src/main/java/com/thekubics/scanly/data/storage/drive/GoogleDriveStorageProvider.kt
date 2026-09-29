package com.thekubics.scanly.data.storage.drive

import android.util.Base64
import com.thekubics.scanly.data.storage.CountingRequestBody
import com.thekubics.scanly.data.storage.GoogleDriveAuthType
import com.thekubics.scanly.data.storage.StorageConfig
import com.thekubics.scanly.data.storage.StorageDownloadResult
import com.thekubics.scanly.data.storage.StorageProvider
import com.thekubics.scanly.data.storage.StorageProviderType
import com.thekubics.scanly.data.storage.StorageTestResult
import com.thekubics.scanly.data.storage.StorageUploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
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

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedTokenExpiresAtMs: Long = 0L

    private fun getAccessToken(): String {
        val trimmed = config.credentialsJson.trim()
        if (trimmed.startsWith("{") && trimmed.contains("\"access_token\"")) {
            val pattern = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"")
            val matcher = pattern.matcher(trimmed)
            if (matcher.find()) return matcher.group(1) ?: trimmed
        }
        if (trimmed.startsWith("{") &&
            trimmed.contains("\"private_key\"") &&
            trimmed.contains("\"client_email\"")
        ) {
            val now = System.currentTimeMillis()
            cachedToken?.takeIf { now < cachedTokenExpiresAtMs - 60_000L }?.let { return it }
            val minted = mintServiceAccountAccessToken(trimmed)
            cachedToken = minted
            cachedTokenExpiresAtMs = now + 3_500_000L
            return minted
        }
        return trimmed
    }

    private fun mintServiceAccountAccessToken(saJson: String): String {
        val email = extractJsonString(saJson, "client_email")
            ?: error("Service account JSON missing client_email")
        val privateKeyPem = extractJsonString(saJson, "private_key")
            ?.replace("\\n", "\n")
            ?: error("Service account JSON missing private_key")

        val nowSec = System.currentTimeMillis() / 1000L
        val header = base64Url("""{"alg":"RS256","typ":"JWT"}""".toByteArray(Charsets.UTF_8))
        val claim = base64Url(
            """{"iss":"$email","scope":"https://www.googleapis.com/auth/drive.file","aud":"https://oauth2.googleapis.com/token","iat":$nowSec,"exp":${nowSec + 3600}}"""
                .toByteArray(Charsets.UTF_8)
        )
        val signingInput = "$header.$claim"
        val signatureBytes = signRs256(signingInput.toByteArray(Charsets.UTF_8), privateKeyPem)
        val assertion = "$signingInput.${base64Url(signatureBytes)}"

        val body = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("assertion", assertion)
            .build()
        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val resp = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Service account token exchange failed (HTTP ${response.code}): $resp")
            }
            return extractJsonString(resp, "access_token")
                ?: error("Token response missing access_token")
        }
    }

    private fun signRs256(data: ByteArray, privateKeyPem: String): ByteArray {
        val cleaned = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.decode(cleaned, Base64.DEFAULT)
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(key)
        sig.update(data)
        return sig.sign()
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Pattern.compile("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1)?.replace("\\\"", "\"") else null
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
                .header("Connection", "close")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val body = response.body?.byteStream()?.readNBytes(64 * 1024)?.toString(Charsets.UTF_8) ?: ""
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
        progressCallback: ((bytesSent: Long, totalBytes: Long) -> Unit)?,
        existingRemoteId: String?
    ): StorageUploadResult = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val fileName = if (remotePath.contains("/")) remotePath.substringAfterLast("/") else remotePath
            val parentFolderJson = if (!config.folderId.isNullOrBlank()) ",\"parents\":[\"${config.folderId}\"]" else ""
            val metadataJson = """{"name":"$fileName","mimeType":"$mimeType"$parentFolderJson}"""

            // files.create is create-only: calling it again for a document we have
            // already uploaded leaves a second copy in Drive ("<name> (1).pdf").
            // When we know the previous file id, update that object in place instead.
            val knownId = existingRemoteId?.trim()?.takeIf { it.isNotEmpty() && it != FALLBACK_ID_PREFIX }
            val isUpdate = knownId != null

            val requestBody: RequestBody = if (isUpdate) {
                // PATCH .../files/{fileId}?uploadType=media replaces content only.
                file.asRequestBody(mimeType.toMediaTypeOrNull())
            } else {
                MultipartBody.Builder()
                    .setType("multipart/related".toMediaTypeOrNull() ?: MultipartBody.FORM)
                    .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                    .addPart(file.asRequestBody(mimeType.toMediaTypeOrNull()))
                    .build()
            }

            val countedBody = if (progressCallback != null) {
                CountingRequestBody(requestBody) { sent, total ->
                    progressCallback(sent, total)
                }
            } else {
                requestBody
            }

            val url = if (isUpdate) {
                "$apiHost/upload/drive/v3/files/${encodeRemoteId(knownId)}?uploadType=media"
            } else {
                "$apiHost/upload/drive/v3/files?uploadType=multipart"
            }

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Connection", "close")
                .apply { if (isUpdate) patch(countedBody) else post(countedBody) }
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.byteStream()?.readNBytes(64 * 1024)?.toString(Charsets.UTF_8) ?: ""
                if (response.isSuccessful) {
                    // A media PATCH returns an empty body; keep the id we patched.
                    val fileId = extractJsonString(body, "id") ?: knownId ?: "drive_${System.currentTimeMillis()}"
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

    /** URL-path-encodes a Drive file id so it is safe inside the request path. */
    private fun encodeRemoteId(id: String): String =
        java.net.URLEncoder.encode(id, Charsets.UTF_8.name()).replace("+", "%20")

    private companion object {
        /** Local fallback id shape; never a real Drive file id, so never PATCH it. */
        const val FALLBACK_ID_PREFIX = "drive_"
    }

    override suspend fun downloadFile(
        remotePath: String,
        targetFile: File,
        progressCallback: ((bytesDownloaded: Long, totalBytes: Long) -> Unit)?
    ): StorageDownloadResult = withContext(Dispatchers.IO) {
        try {
            val token = getAccessToken()
            val request = Request.Builder()
                .url("$apiHost/drive/v3/files/$remotePath?alt=media")
                .header("Authorization", "Bearer $token")
                .header("Connection", "close")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.byteStream()?.readNBytes(64 * 1024)?.toString(Charsets.UTF_8) ?: ""
                    return@withContext StorageDownloadResult(
                        isSuccess = false,
                        errorMessage = "Drive download failed (HTTP ${response.code}): $body"
                    )
                }
                val body = response.body ?: return@withContext StorageDownloadResult(
                    isSuccess = false,
                    errorMessage = "Drive download returned an empty body"
                )
                val total = body.contentLength()
                var downloaded = 0L
                try {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { fos ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                fos.write(buffer, 0, read)
                                downloaded += read
                                progressCallback?.invoke(downloaded, total)
                            }
                        }
                    }
                    StorageDownloadResult(
                        isSuccess = true,
                        localFile = targetFile,
                        bytesDownloaded = downloaded
                    )
                } catch (e: Exception) {
                    runCatching { targetFile.delete() }
                    StorageDownloadResult(
                        isSuccess = false,
                        errorMessage = e.message ?: "Drive download failed"
                    )
                }
            }
        } catch (e: Exception) {
            StorageDownloadResult(
                isSuccess = false,
                errorMessage = e.message ?: "Drive download failed"
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
}
