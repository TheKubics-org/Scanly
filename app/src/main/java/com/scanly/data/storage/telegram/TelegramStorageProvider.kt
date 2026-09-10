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
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class TelegramStorageProvider(
    val config: StorageConfig.Telegram,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val customBaseUrl: String? = null
) : StorageProvider {

    override val id: String get() = config.id
    override val type: StorageProviderType get() = StorageProviderType.TELEGRAM
    override val displayName: String get() = config.displayName
    override val isEnabled: Boolean get() = config.isEnabled

    val cleanChatId: String get() = sanitizeTelegramChatId(config.chatId)

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
                val botId = extractJsonLong(body, "id")

                val chatRequest = Request.Builder()
                    .url("$baseUrl/getChat?chat_id=$cleanChatId")
                    .get()
                    .build()

                client.newCall(chatRequest).execute().use { chatResponse ->
                    val chatBody = chatResponse.body?.string() ?: ""
                    val totalLatency = System.currentTimeMillis() - start
                    if (chatResponse.isSuccessful && chatBody.contains("\"ok\":true")) {
                        val chatTitle = extractJsonString(chatBody, "title")
                            ?: extractJsonString(chatBody, "username")
                            ?: cleanChatId
                        val chatType = extractJsonString(chatBody, "type") ?: "chat"

                        // For channels or supergroups, check if bot has post privileges
                        var memberStatusHint = ""
                        if (botId != null && (chatType == "channel" || chatType == "supergroup")) {
                            try {
                                val memberReq = Request.Builder()
                                    .url("$baseUrl/getChatMember?chat_id=$cleanChatId&user_id=$botId")
                                    .get()
                                    .build()
                                client.newCall(memberReq).execute().use { mResp ->
                                    val mBody = mResp.body?.string() ?: ""
                                    if (mResp.isSuccessful && mBody.contains("\"ok\":true")) {
                                        val status = extractJsonString(mBody, "status")
                                        if (status != "administrator" && status != "creator") {
                                            memberStatusHint = " (Note: Bot is '$status'. Please ensure bot is an Administrator with 'Post Messages' permission to upload documents.)"
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        StorageTestResult(
                            isSuccess = true,
                            message = "Verified as @$botUsername with access to '$chatTitle'$memberStatusHint",
                            latencyMs = totalLatency,
                            details = chatBody
                        )
                    } else {
                        val rawDesc = extractErrorDescription(chatBody) ?: "Chat not found or bot lacks permissions"
                        val userFriendlyDesc = formatTelegramError(rawDesc, cleanChatId, botUsername)
                        StorageTestResult(
                            isSuccess = false,
                            message = "Bot verified (@$botUsername), but cannot access chat '$cleanChatId': $userFriendlyDesc",
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
        if (!file.exists()) {
            return@withContext StorageUploadResult(
                isSuccess = false,
                errorMessage = "Cannot upload: file does not exist (${file.absolutePath})"
            )
        }

        try {
            val mediaType = (mimeType.ifBlank { "application/pdf" }).toMediaTypeOrNull()
            val fileBody = file.asRequestBody(mediaType)
            val fileName = if (remotePath.contains("/")) remotePath.substringAfterLast("/") else remotePath

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", cleanChatId)
                .addFormDataPart("caption", "📄 Scanly Backup: $fileName")
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
                        remoteUrl = "tg://msg?chat=$cleanChatId&id=$messageId",
                        bytesUploaded = file.length()
                    )
                } else {
                    val rawDesc = extractErrorDescription(body) ?: "HTTP ${response.code}: ${response.message}"
                    val userFriendlyDesc = formatTelegramError(rawDesc, cleanChatId, null)
                    StorageUploadResult(
                        isSuccess = false,
                        errorMessage = "Telegram upload failed: $userFriendlyDesc"
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
                .addFormDataPart("chat_id", cleanChatId)
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

    private fun formatTelegramError(rawDesc: String, targetChat: String, botUsername: String?): String {
        val lower = rawDesc.lowercase()
        return when {
            lower.contains("chat not found") ->
                "$rawDesc. For Telegram channels, ensure (1) target '$targetChat' is correct, and (2) you have added your bot ${if (botUsername != null) "@$botUsername " else ""}as an Administrator to the channel."
            lower.contains("need administrator rights") || lower.contains("not a member") || lower.contains("bot was kicked") ->
                "$rawDesc. The bot must be added as an Administrator with 'Post Messages' permission enabled in the target channel."
            lower.contains("unauthorized") ->
                "$rawDesc. Invalid bot token. Please check the token provided by @BotFather."
            else -> rawDesc
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

    companion object {
        fun sanitizeTelegramChatId(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.startsWith("https://t.me/") || trimmed.startsWith("http://t.me/") || trimmed.startsWith("t.me/")) {
                val path = trimmed.substringAfter("t.me/").trim('/')
                if (path.startsWith("c/")) {
                    val idPart = path.removePrefix("c/").substringBefore('/')
                    return if (idPart.startsWith("-100")) idPart else "-100$idPart"
                } else {
                    val username = path.substringBefore('/')
                    return if (username.startsWith("@")) username else "@$username"
                }
            }
            if (trimmed.startsWith("@") || trimmed.startsWith("-100")) {
                return trimmed
            }
            if (trimmed.startsWith("-") && trimmed.drop(1).all { it.isDigit() }) {
                val digits = trimmed.drop(1)
                return if (digits.startsWith("100")) trimmed else "-100$digits"
            }
            // If it starts with a letter and is alphanumeric (likely public channel username without @)
            if (trimmed.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]{3,}$"))) {
                return "@$trimmed"
            }
            return trimmed
        }
    }
}
