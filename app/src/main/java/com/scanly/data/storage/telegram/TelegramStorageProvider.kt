package com.scanly.data.storage.telegram

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
import java.io.File
import java.util.regex.Pattern

class TelegramStorageProvider(
    val config: StorageConfig.Telegram,
    private val client: OkHttpClient = OkHttpClient(),
    private val customBaseUrl: String? = null
) : StorageProvider {

    override val id: String get() = config.id
    override val type: StorageProviderType get() = StorageProviderType.TELEGRAM
    override val displayName: String get() = config.displayName
    override val isEnabled: Boolean get() = config.isEnabled

    private val baseUrl: String get() = customBaseUrl ?: "https://api.telegram.org/bot${config.botToken}"

    override suspend fun testConnection(): StorageTestResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val meRequest = Request.Builder()
                .url("$baseUrl/getMe")
                .get()
                .build()

            client.newCall(meRequest).execute().use { response ->
                val body = response.body?.string() ?: ""
                val latency = System.currentTimeMillis() - start
                if (!response.isSuccessful || !body.contains("\"ok\":true")) {
                    val desc = extractErrorDescription(body) ?: "HTTP ${response.code}: ${response.message}"
                    return@withContext StorageTestResult(
                        isSuccess = false,
                        message = "Telegram authentication failed: $desc",
                        latencyMs = latency,
                        details = body
                    )
                }

                val botUsername = extractJsonString(body, "username") ?: "Bot"
                val chatRequest = Request.Builder()
                    .url("$baseUrl/getChat?chat_id=${config.chatId}")
                    .get()
                    .build()

                client.newCall(chatRequest).execute().use { chatResponse ->
                    val chatBody = chatResponse.body?.string() ?: ""
                    val totalLatency = System.currentTimeMillis() - start
                    if (chatResponse.isSuccessful && chatBody.contains("\"ok\":true")) {
                        val chatTitle = extractJsonString(chatBody, "title") ?: config.chatId
                        StorageTestResult(
                            isSuccess = true,
                            message = "Verified as @$botUsername with access to '$chatTitle'",
                            latencyMs = totalLatency,
                            details = chatBody
                        )
                    } else {
                        val chatDesc = extractErrorDescription(chatBody) ?: "Chat not found or bot lacks permissions"
                        StorageTestResult(
                            isSuccess = false,
                            message = "Bot verified (@$botUsername), but cannot access chat '${config.chatId}': $chatDesc",
                            latencyMs = totalLatency,
                            details = chatBody
                        )
                    }
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
            val mediaType = (mimeType.ifBlank { "application/pdf" }).toMediaTypeOrNull()
            val fileBody = file.asRequestBody(mediaType)
            val fileName = if (remotePath.contains("/")) remotePath.substringAfterLast("/") else remotePath

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", config.chatId)
                .addFormDataPart("caption", "Scanly Backup: $fileName")
                .addFormDataPart("document", fileName, fileBody)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/sendDocument")
                .post(requestBody)
                .build()

            progressCallback?.invoke(file.length(), file.length())

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful && body.contains("\"ok\":true")) {
                    val messageId = extractJsonLong(body, "message_id") ?: System.currentTimeMillis()
                    StorageUploadResult(
                        isSuccess = true,
                        remoteId = messageId.toString(),
                        remoteUrl = "tg://msg?chat=${config.chatId}&id=$messageId",
                        bytesUploaded = file.length()
                    )
                } else {
                    val errorDesc = extractErrorDescription(body) ?: "HTTP ${response.code}: ${response.message}"
                    StorageUploadResult(
                        isSuccess = false,
                        errorMessage = "Telegram upload failed: $errorDesc"
                    )
                }
            }
        } catch (e: Exception) {
            StorageUploadResult(
                isSuccess = false,
                errorMessage = e.message ?: "Upload exception"
            )
        }
    }

    override suspend fun deleteFile(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val messageId = remotePath.toLongOrNull() ?: return@withContext false
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", config.chatId)
                .addFormDataPart("message_id", messageId.toString())
                .build()

            val request = Request.Builder()
                .url("$baseUrl/deleteMessage")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                response.isSuccessful && body.contains("\"ok\":true")
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun extractErrorDescription(json: String): String? {
        val pattern = Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+)\"")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractJsonString(json: String, key: String): String? {
        val pattern = Pattern.compile("\"$key\"\\s*:\\s*\"([^\"]+)\"")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun extractJsonLong(json: String, key: String): Long? {
        val pattern = Pattern.compile("\"$key\"\\s*:\\s*(\\d+)")
        val matcher = pattern.matcher(json)
        return if (matcher.find()) matcher.group(1)?.toLongOrNull() else null
    }
}
