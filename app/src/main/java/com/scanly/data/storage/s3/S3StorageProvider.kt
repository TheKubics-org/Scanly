package com.scanly.data.storage.s3

import com.scanly.data.storage.StorageConfig
import com.scanly.data.storage.StorageProvider
import com.scanly.data.storage.StorageProviderType
import com.scanly.data.storage.StorageTestResult
import com.scanly.data.storage.StorageUploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.Date

class S3StorageProvider(
    val config: StorageConfig.CloudflareR2,
    private val client: OkHttpClient = OkHttpClient()
) : StorageProvider {

    override val id: String get() = config.id
    override val type: StorageProviderType get() = StorageProviderType.CLOUDFLARE_R2
    override val displayName: String get() = config.displayName
    override val isEnabled: Boolean get() = config.isEnabled

    private val baseEndpoint: String get() = config.endpointUrl.trimEnd('/')

    override suspend fun testConnection(): StorageTestResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val targetUrl = "$baseEndpoint/${config.bucketName}?max-keys=1"
            val emptyPayloadSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

            val signedHeaders = AwsSigV4Signer.sign(
                method = "GET",
                url = targetUrl,
                headers = emptyMap(),
                payloadSha256 = emptyPayloadSha256,
                date = Date(),
                accessKeyId = config.accessKeyId,
                secretAccessKey = config.secretAccessKey,
                region = config.region
            )

            val requestBuilder = Request.Builder().url(targetUrl).get()
            for ((k, v) in signedHeaders) {
                requestBuilder.header(k, v)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    StorageTestResult(
                        isSuccess = true,
                        message = "Connected to bucket '${config.bucketName}' successfully",
                        latencyMs = latency
                    )
                } else {
                    val msg = when (response.code) {
                        403 -> "Access Denied: Check Access Key ID and Secret Access Key permissions."
                        404 -> "Bucket '${config.bucketName}' not found."
                        else -> "HTTP ${response.code}: ${response.message}"
                    }
                    StorageTestResult(
                        isSuccess = false,
                        message = "S3/R2 check failed (HTTP ${response.code}): $msg",
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
            val cleanPath = remotePath.trimStart('/')
            val targetUrl = "$baseEndpoint/${config.bucketName}/$cleanPath"
            val fileBytes = file.readBytes()
            val payloadSha256 = AwsSigV4Signer.sha256Hex(fileBytes)
            val mediaType = (mimeType.ifBlank { "application/pdf" }).toMediaTypeOrNull()

            val initialHeaders = mutableMapOf<String, String>()
            initialHeaders["Content-Type"] = mimeType.ifBlank { "application/pdf" }
            initialHeaders["Content-Length"] = fileBytes.size.toString()

            val signedHeaders = AwsSigV4Signer.sign(
                method = "PUT",
                url = targetUrl,
                headers = initialHeaders,
                payloadSha256 = payloadSha256,
                date = Date(),
                accessKeyId = config.accessKeyId,
                secretAccessKey = config.secretAccessKey,
                region = config.region
            )

            val fileBody = file.asRequestBody(mediaType)
            val requestBuilder = Request.Builder().url(targetUrl).put(fileBody)
            for ((k, v) in signedHeaders) {
                requestBuilder.header(k, v)
            }

            progressCallback?.invoke(file.length(), file.length())

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    StorageUploadResult(
                        isSuccess = true,
                        remoteId = cleanPath,
                        remoteUrl = targetUrl,
                        bytesUploaded = file.length()
                    )
                } else {
                    val body = response.body?.string() ?: ""
                    StorageUploadResult(
                        isSuccess = false,
                        errorMessage = "S3 PUT failed (HTTP ${response.code}): ${response.message}\n$body"
                    )
                }
            }
        } catch (e: Exception) {
            StorageUploadResult(
                isSuccess = false,
                errorMessage = e.message ?: "S3 upload failed"
            )
        }
    }

    override suspend fun deleteFile(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanPath = remotePath.trimStart('/')
            val targetUrl = "$baseEndpoint/${config.bucketName}/$cleanPath"
            val emptySha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

            val signedHeaders = AwsSigV4Signer.sign(
                method = "DELETE",
                url = targetUrl,
                headers = emptyMap(),
                payloadSha256 = emptySha256,
                date = Date(),
                accessKeyId = config.accessKeyId,
                secretAccessKey = config.secretAccessKey,
                region = config.region
            )

            val requestBuilder = Request.Builder().url(targetUrl).delete()
            for ((k, v) in signedHeaders) {
                requestBuilder.header(k, v)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                response.isSuccessful || response.code == 204 || response.code == 404
            }
        } catch (e: Exception) {
            false
        }
    }
}
