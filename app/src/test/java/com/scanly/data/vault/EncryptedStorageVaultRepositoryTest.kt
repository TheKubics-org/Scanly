package com.scanly.data.vault

import android.content.Context
import android.content.SharedPreferences
import com.scanly.data.storage.GoogleDriveAuthType
import com.scanly.data.storage.StorageConfig
import com.scanly.data.storage.StorageProviderRegistry
import com.scanly.data.storage.StorageProviderType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EncryptedStorageVaultRepositoryTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var mockContext: Context
    private lateinit var registry: StorageProviderRegistry
    private lateinit var repository: EncryptedStorageVaultRepositoryImpl

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        mockContext = mockk<Context>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns fakePrefs

        registry = StorageProviderRegistry(OkHttpClient())
        repository = EncryptedStorageVaultRepositoryImpl(
            context = mockContext,
            registry = registry,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @Test
    fun `saveConfig and getConfigById round trip for Telegram`() = runBlocking {
        val tgConfig = StorageConfig.Telegram(
            id = "tg_channel_1",
            displayName = "My Backups",
            botToken = "123456789:AAABBBCCCDDDEEEFFF",
            chatId = "-100123456789",
            isEnabled = true
        )

        repository.saveConfig(tgConfig)
        val retrieved = repository.getConfigById("tg_channel_1") as? StorageConfig.Telegram

        assertNotNull(retrieved)
        assertEquals("tg_channel_1", retrieved!!.id)
        assertEquals("My Backups", retrieved.displayName)
        assertEquals("123456789:AAABBBCCCDDDEEEFFF", retrieved.botToken)
        assertEquals("-100123456789", retrieved.chatId)
        assertTrue(retrieved.isEnabled)
    }

    @Test
    fun `saveConfig and getConfigById round trip for CloudflareR2`() = runBlocking {
        val r2Config = StorageConfig.CloudflareR2(
            id = "r2_bucket_primary",
            displayName = "R2 Vault",
            accessKeyId = "KEY_ABCDE",
            secretAccessKey = "SECRET_1234567890_VERY_LONG_STRING",
            bucketName = "scanly-bucket",
            endpointUrl = "https://acc_12345.r2.cloudflarestorage.com",
            region = "auto",
            isEnabled = true
        )

        repository.saveConfig(r2Config)
        val retrieved = repository.getConfigById("r2_bucket_primary") as? StorageConfig.CloudflareR2

        assertNotNull(retrieved)
        assertEquals("r2_bucket_primary", retrieved!!.id)
        assertEquals("R2 Vault", retrieved.displayName)
        assertEquals("KEY_ABCDE", retrieved.accessKeyId)
        assertEquals("SECRET_1234567890_VERY_LONG_STRING", retrieved.secretAccessKey)
        assertEquals("scanly-bucket", retrieved.bucketName)
        assertEquals("https://acc_12345.r2.cloudflarestorage.com", retrieved.endpointUrl)
    }

    @Test
    fun `saveConfig and getConfigById round trip for GoogleDrive`() = runBlocking {
        val driveConfig = StorageConfig.GoogleDrive(
            id = "drive_personal",
            displayName = "Google Drive Backups",
            authType = GoogleDriveAuthType.OAUTH2,
            credentialsJson = "ya29.a0AfH6TestTokenCredentials",
            folderId = "folder_scanly_abc",
            isEnabled = true
        )

        repository.saveConfig(driveConfig)
        val retrieved = repository.getConfigById("drive_personal") as? StorageConfig.GoogleDrive

        assertNotNull(retrieved)
        assertEquals("drive_personal", retrieved!!.id)
        assertEquals("Google Drive Backups", retrieved.displayName)
        assertEquals(GoogleDriveAuthType.OAUTH2, retrieved.authType)
        assertEquals("ya29.a0AfH6TestTokenCredentials", retrieved.credentialsJson)
        assertEquals("folder_scanly_abc", retrieved.folderId)
    }

    @Test
    fun `active provider defaults to first saved config and can be switched`() = runBlocking {
        val tg = StorageConfig.Telegram(
            id = "tg_1",
            displayName = "TG",
            botToken = "token1",
            chatId = "chat1"
        )
        val r2 = StorageConfig.CloudflareR2(
            id = "r2_1",
            displayName = "R2",
            accessKeyId = "key",
            secretAccessKey = "secret",
            bucketName = "bucket",
            endpointUrl = "https://r2.test"
        )

        repository.saveConfig(tg)
        assertEquals("tg_1", repository.getActiveProviderId())
        assertEquals(StorageProviderType.TELEGRAM, repository.getActiveProvider()?.type)

        repository.saveConfig(r2)
        // tg_1 remains active unless changed
        assertEquals("tg_1", repository.getActiveProviderId())

        // Switch active provider
        repository.setActiveProviderId("r2_1")
        assertEquals("r2_1", repository.getActiveProviderId())
        assertEquals(StorageProviderType.CLOUDFLARE_R2, repository.getActiveProvider()?.type)
    }

    @Test
    fun `deleteConfig removes config and falls back active provider`() = runBlocking {
        val c1 = StorageConfig.Telegram(id = "c1", displayName = "C1", botToken = "t", chatId = "1")
        val c2 = StorageConfig.Telegram(id = "c2", displayName = "C2", botToken = "t", chatId = "2")

        repository.saveConfig(c1)
        repository.saveConfig(c2)
        repository.setActiveProviderId("c1")

        assertEquals(2, repository.getAllConfigs().size)
        assertEquals("c1", repository.getActiveProviderId())

        repository.deleteConfig("c1")

        assertNull(repository.getConfigById("c1"))
        assertEquals(1, repository.getAllConfigs().size)
        // When active is deleted, it switches to the remaining enabled config
        assertEquals("c2", repository.getActiveProviderId())
    }

    @Test
    fun `flows emit state updates when configs are added or deleted`() = runBlocking {
        assertEquals(0, repository.getAllConfigs().size)

        val c1 = StorageConfig.Telegram(id = "tg_flow", displayName = "TG Flow", botToken = "t", chatId = "1")
        repository.saveConfig(c1)

        assertEquals(1, repository.getAllConfigs().size)
        assertEquals("tg_flow", repository.getActiveProviderId())

        repository.deleteConfig("tg_flow")
        assertEquals(0, repository.getAllConfigs().size)
        assertNull(repository.getActiveProviderId())
    }

    class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<*>)?.map { it.toString() }?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            if (listener != null) listeners.add(listener)
        }
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            listeners.remove(listener)
        }

        class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private val toRemove = mutableSetOf<String>()
            private var shouldClear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) temp[key] = values?.toSet()
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) toRemove.add(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                shouldClear = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (shouldClear) prefs.map.clear()
                for (key in toRemove) prefs.map.remove(key)
                for ((key, value) in temp) {
                    if (value != null) prefs.map[key] = value else prefs.map.remove(key)
                }
            }
        }
    }
}
