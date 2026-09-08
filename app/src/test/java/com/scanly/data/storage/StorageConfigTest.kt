package com.scanly.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageConfigTest {

    @Test
    fun testValidTelegramConfig() {
        val config = StorageConfig.Telegram(
            id = "tg_1",
            displayName = "My Telegram",
            botToken = "123456789:ABCdefGhIJKlmNoPQRsTUVwxyZ_12345678",
            chatId = "-100198273645"
        )
        val result = config.validate()
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
        assertEquals(StorageProviderType.TELEGRAM, config.type)
    }

    @Test
    fun testInvalidTelegramConfig_emptyFields() {
        val config = StorageConfig.Telegram(
            id = "tg_2",
            displayName = "",
            botToken = "",
            chatId = ""
        )
        val result = config.validate()
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Display name") })
        assertTrue(result.errors.any { it.contains("Bot token") })
        assertTrue(result.errors.any { it.contains("Chat ID") })
    }

    @Test
    fun testInvalidTelegramConfig_badTokenFormat() {
        val config = StorageConfig.Telegram(
            id = "tg_3",
            displayName = "Bad Token Bot",
            botToken = "invalid_token_no_colon",
            chatId = "@my_channel"
        )
        val result = config.validate()
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("token format") })
    }

    @Test
    fun testValidCloudflareR2Config() {
        val config = StorageConfig.CloudflareR2(
            id = "r2_1",
            displayName = "Cloudflare Backup",
            endpointUrl = "https://1234567890abcdef.r2.cloudflarestorage.com",
            bucketName = "scanly-backups",
            accessKeyId = "AKIAIOSFODNN7EXAMPLE",
            secretAccessKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
            region = "auto"
        )
        val result = config.validate()
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
        assertEquals(StorageProviderType.CLOUDFLARE_R2, config.type)
    }

    @Test
    fun testInvalidCloudflareR2Config_badUrlAndBucket() {
        val config = StorageConfig.CloudflareR2(
            id = "r2_2",
            displayName = "",
            endpointUrl = "ftp://invalid-protocol.com",
            bucketName = "ab", // Too short
            accessKeyId = "",
            secretAccessKey = ""
        )
        val result = config.validate()
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Endpoint URL must start with https://") })
        assertTrue(result.errors.any { it.contains("bucket name length") })
        assertTrue(result.errors.any { it.contains("Access Key ID") })
        assertTrue(result.errors.any { it.contains("Secret Access Key") })
    }

    @Test
    fun testValidGoogleDriveOAuth2Config() {
        val config = StorageConfig.GoogleDrive(
            id = "drive_1",
            displayName = "Personal Drive",
            authType = GoogleDriveAuthType.OAUTH2,
            credentialsJson = "ya29.a0AfH6SMBx...",
            folderId = "folder_123"
        )
        val result = config.validate()
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
        assertEquals(StorageProviderType.GOOGLE_DRIVE, config.type)
    }

    @Test
    fun testValidGoogleDriveServiceAccountConfig() {
        val json = """{"client_email":"test@service.iam.gserviceaccount.com","private_key":"-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgk..."}"""
        val config = StorageConfig.GoogleDrive(
            id = "drive_2",
            displayName = "Work Drive SA",
            authType = GoogleDriveAuthType.SERVICE_ACCOUNT,
            credentialsJson = json
        )
        val result = config.validate()
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun testInvalidGoogleDriveServiceAccountConfig_missingKeys() {
        val json = """{"project_id":"my-project"}"""
        val config = StorageConfig.GoogleDrive(
            id = "drive_3",
            displayName = "Bad SA",
            authType = GoogleDriveAuthType.SERVICE_ACCOUNT,
            credentialsJson = json
        )
        val result = config.validate()
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("client_email") })
    }
}
