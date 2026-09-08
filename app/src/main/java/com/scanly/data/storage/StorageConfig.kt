package com.scanly.data.storage

/**
 * Authentication type for Google Drive.
 */
enum class GoogleDriveAuthType {
    OAUTH2,
    SERVICE_ACCOUNT
}

/**
 * Sealed configuration models for BYOS storage destinations.
 */
sealed class StorageConfig {
    abstract val id: String
    abstract val displayName: String
    abstract val isEnabled: Boolean
    abstract val type: StorageProviderType

    data class Telegram(
        override val id: String,
        override val displayName: String,
        override val isEnabled: Boolean = true,
        val botToken: String,
        val chatId: String
    ) : StorageConfig() {
        override val type: StorageProviderType get() = StorageProviderType.TELEGRAM
    }

    data class CloudflareR2(
        override val id: String,
        override val displayName: String,
        override val isEnabled: Boolean = true,
        val endpointUrl: String,
        val bucketName: String,
        val accessKeyId: String,
        val secretAccessKey: String,
        val region: String = "auto"
    ) : StorageConfig() {
        override val type: StorageProviderType get() = StorageProviderType.CLOUDFLARE_R2
    }

    data class GoogleDrive(
        override val id: String,
        override val displayName: String,
        override val isEnabled: Boolean = true,
        val authType: GoogleDriveAuthType,
        val credentialsJson: String,
        val folderId: String? = null
    ) : StorageConfig() {
        override val type: StorageProviderType get() = StorageProviderType.GOOGLE_DRIVE
    }

    fun validate(): ValidationResult {
        return when (this) {
            is Telegram -> validateTelegram(this)
            is CloudflareR2 -> validateR2(this)
            is GoogleDrive -> validateDrive(this)
        }
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList()
    )

    companion object {
        fun validateTelegram(config: Telegram): ValidationResult {
            val errors = mutableListOf<String>()
            if (config.displayName.isBlank()) errors.add("Display name cannot be empty")
            val token = config.botToken.trim()
            if (token.isBlank()) {
                errors.add("Bot token cannot be empty")
            } else if (!token.contains(":") || token.length < 20) {
                errors.add("Invalid Telegram Bot token format")
            }
            if (config.chatId.trim().isBlank()) {
                errors.add("Chat ID cannot be empty")
            }
            return ValidationResult(isValid = errors.isEmpty(), errors = errors)
        }

        fun validateR2(config: CloudflareR2): ValidationResult {
            val errors = mutableListOf<String>()
            if (config.displayName.isBlank()) errors.add("Display name cannot be empty")
            val trimmedUrl = config.endpointUrl.trim()
            if (trimmedUrl.isBlank()) {
                errors.add("Endpoint URL cannot be empty")
            } else if (!trimmedUrl.startsWith("https://") && !trimmedUrl.startsWith("http://")) {
                errors.add("Endpoint URL must start with https:// or http://")
            }
            val trimmedBucket = config.bucketName.trim()
            if (trimmedBucket.isBlank()) {
                errors.add("Bucket name cannot be empty")
            } else if (trimmedBucket.length < 3 || trimmedBucket.length > 63) {
                errors.add("Invalid S3 bucket name length (must be 3-63 characters)")
            }
            if (config.accessKeyId.trim().isBlank()) errors.add("Access Key ID cannot be empty")
            if (config.secretAccessKey.trim().isBlank()) errors.add("Secret Access Key cannot be empty")
            return ValidationResult(isValid = errors.isEmpty(), errors = errors)
        }

        fun validateDrive(config: GoogleDrive): ValidationResult {
            val errors = mutableListOf<String>()
            if (config.displayName.isBlank()) errors.add("Display name cannot be empty")
            val creds = config.credentialsJson.trim()
            if (creds.isBlank()) {
                errors.add("Credentials cannot be empty")
            } else if (config.authType == GoogleDriveAuthType.SERVICE_ACCOUNT) {
                if (!creds.contains("client_email") || !creds.contains("private_key")) {
                    errors.add("Service account JSON must contain 'client_email' and 'private_key'")
                }
            }
            return ValidationResult(isValid = errors.isEmpty(), errors = errors)
        }
    }
}
