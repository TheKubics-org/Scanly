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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

class EncryptedStorageVaultRepositoryAdversarialTest {

    private lateinit var fakePrefs: ThreadSafeFakeSharedPreferences
    private lateinit var mockContext: Context
    private lateinit var registry: StorageProviderRegistry
    private lateinit var repository: EncryptedStorageVaultRepositoryImpl

    @Before
    fun setUp() {
        fakePrefs = ThreadSafeFakeSharedPreferences()
        mockContext = mockk<Context>(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns fakePrefs

        registry = StorageProviderRegistry(OkHttpClient())
        repository = EncryptedStorageVaultRepositoryImpl(
            context = mockContext,
            registry = registry,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    // 1. Extreme token lengths (10k, 50k characters)
    @Test
    fun testExtremeTokenLengthsSerialization() = runBlocking {
        val longToken = "A".repeat(10_000)
        val tgConfig = StorageConfig.Telegram(
            id = "tg_long",
            displayName = "Long Token TG",
            botToken = longToken,
            chatId = "-100" + "9".repeat(100),
            isEnabled = true
        )

        repository.saveConfig(tgConfig)
        val retrieved = repository.getConfigById("tg_long") as? StorageConfig.Telegram
        assertNotNull(retrieved)
        assertEquals(longToken, retrieved!!.botToken)
        assertEquals(10_000, retrieved.botToken.length)
    }

    @Test
    fun testExtremeLengthGoogleDriveServiceAccount() = runBlocking {
        val massiveKey = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC3" + "x".repeat(30_000)
        val credentialsJson = """{"type":"service_account","project_id":"scanly","private_key":"$massiveKey"}"""

        val driveConfig = StorageConfig.GoogleDrive(
            id = "drive_massive",
            displayName = "Massive Drive",
            authType = GoogleDriveAuthType.SERVICE_ACCOUNT,
            credentialsJson = credentialsJson,
            isEnabled = true
        )

        repository.saveConfig(driveConfig)
        val retrieved = repository.getConfigById("drive_massive") as? StorageConfig.GoogleDrive
        assertNotNull(retrieved)
        assertEquals(credentialsJson, retrieved!!.credentialsJson)
    }

    // 2. Special Characters, Escaping, Quotes, Slashes, Unicode
    @Test
    fun testSpecialCharactersInFields() = runBlocking {
        val trickyDisplayName = """My "Backups" & 'Scans' \ / : * ? < > | \n \r \t 🚀"""
        val trickySecret = """sec\"ret\\with\newlines\nand\r\tvalues\\"""

        val r2Config = StorageConfig.CloudflareR2(
            id = "r2_tricky",
            displayName = trickyDisplayName,
            accessKeyId = "KEY_!@#\$%^&*()",
            secretAccessKey = trickySecret,
            bucketName = "scanly-vault",
            endpointUrl = "https://example.r2.cloudflarestorage.com"
        )

        repository.saveConfig(r2Config)
        val retrieved = repository.getConfigById("r2_tricky") as? StorageConfig.CloudflareR2
        assertNotNull(retrieved)
        assertEquals(trickyDisplayName, retrieved!!.displayName)
        println("Original secret: $trickySecret")
        println("Retrieved secret: ${retrieved.secretAccessKey}")
        assertEquals(trickySecret, retrieved.secretAccessKey)
    }

    @Test
    fun testGoogleDriveServiceAccountNestedJsonRoundTrip() = runBlocking {
        val saJson = """{"type":"service_account","project_id":"scanly-byos-123","private_key_id":"key789","client_email":"sa@scanly.iam.gserviceaccount.com"}"""

        val config = StorageConfig.GoogleDrive(
            id = "drive_sa",
            displayName = "Drive SA",
            authType = GoogleDriveAuthType.SERVICE_ACCOUNT,
            credentialsJson = saJson,
            folderId = "folder_xyz_123"
        )

        repository.saveConfig(config)
        val retrieved = repository.getConfigById("drive_sa") as? StorageConfig.GoogleDrive
        assertNotNull(retrieved)
        assertEquals(saJson, retrieved!!.credentialsJson)
        assertEquals("folder_xyz_123", retrieved.folderId)
        assertEquals(GoogleDriveAuthType.SERVICE_ACCOUNT, retrieved.authType)
    }

    // 3. Corrupted / Malformed JSON resilience
    @Test
    fun testMalformedJsonDeserializationHandledGracefully() {
        assertNull(EncryptedStorageVaultRepositoryImpl.deserializeConfig(""))
        assertNull(EncryptedStorageVaultRepositoryImpl.deserializeConfig("{invalid json}"))
        assertNull(EncryptedStorageVaultRepositoryImpl.deserializeConfig("{}"))
        assertNull(EncryptedStorageVaultRepositoryImpl.deserializeConfig("""{"type":"UNKNOWN_PROVIDER","id":"123"}"""))
        assertNull(EncryptedStorageVaultRepositoryImpl.deserializeConfig("""{"type":"TELEGRAM"}""")) // missing id
    }

    @Test
    fun testCorruptedConfigInPrefsDoesNotCrashGetAllConfigs() = runBlocking {
        // Manually inject corrupted JSON into fakePrefs
        fakePrefs.edit()
            .putStringSet("config_ids", mutableSetOf("valid_1", "corrupt_1"))
            .putString("config_json_valid_1", """{"type":"TELEGRAM","id":"valid_1","displayName":"Valid","isEnabled":true,"botToken":"tok","chatId":"chat"}""")
            .putString("config_json_corrupt_1", """{corrupted: "not valid json""")
            .apply()

        val configs = repository.getAllConfigs()
        assertEquals(1, configs.size)
        assertEquals("valid_1", configs[0].id)
    }

    // 4. Active Provider Deletion & Fallback Edge Cases
    @Test
    fun testDeleteOnlyActiveProviderLeavesNullActive() = runBlocking {
        val c1 = StorageConfig.Telegram(id = "c1", displayName = "C1", botToken = "t", chatId = "1")
        repository.saveConfig(c1)
        assertEquals("c1", repository.getActiveProviderId())

        repository.deleteConfig("c1")
        assertNull(repository.getActiveProviderId())
        assertNull(repository.getActiveProvider())
    }

    @Test
    fun testDeleteActiveWhenMultipleEnabledPicksRemainingEnabled() = runBlocking {
        val c1 = StorageConfig.Telegram(id = "c1", displayName = "C1", botToken = "t", chatId = "1", isEnabled = true)
        val c2 = StorageConfig.Telegram(id = "c2", displayName = "C2", botToken = "t", chatId = "2", isEnabled = true)
        repository.saveConfig(c1)
        repository.saveConfig(c2)
        repository.setActiveProviderId("c1")

        repository.deleteConfig("c1")
        assertEquals("c2", repository.getActiveProviderId())
    }

    @Test
    fun testDeleteActiveWhenAllRemainingAreDisabledPicksNull() = runBlocking {
        val c1 = StorageConfig.Telegram(id = "c1", displayName = "C1", botToken = "t", chatId = "1", isEnabled = true)
        val c2 = StorageConfig.Telegram(id = "c2", displayName = "C2", botToken = "t", chatId = "2", isEnabled = false)
        repository.saveConfig(c1)
        repository.saveConfig(c2)
        repository.setActiveProviderId("c1")

        repository.deleteConfig("c1")
        // c2 is disabled, so active provider should be null
        assertNull(repository.getActiveProviderId())
    }

    @Test
    fun testDeleteNonActiveConfigPreservesActiveSelection() = runBlocking {
        // Critical edge case: user selected c2 as active.
        // c1 and c3 also exist. User deletes c3 (an inactive provider).
        // c2 should STILL remain the active provider!
        val c1 = StorageConfig.Telegram(id = "c1", displayName = "C1", botToken = "t", chatId = "1", isEnabled = true)
        val c2 = StorageConfig.Telegram(id = "c2", displayName = "C2", botToken = "t", chatId = "2", isEnabled = true)
        val c3 = StorageConfig.Telegram(id = "c3", displayName = "C3", botToken = "t", chatId = "3", isEnabled = true)

        repository.saveConfig(c1)
        repository.saveConfig(c2)
        repository.saveConfig(c3)

        repository.setActiveProviderId("c2")
        assertEquals("c2", repository.getActiveProviderId())

        // Delete c3 (non-active)
        repository.deleteConfig("c3")

        // Active provider MUST still be c2!
        val activeAfterDelete = repository.getActiveProviderId()
        println("Active after deleting non-active c3: $activeAfterDelete")
        assertEquals("Deleting an inactive provider must not change the active provider", "c2", activeAfterDelete)
    }

    // 5. Concurrent stress testing across coroutines
    @Test
    fun testConcurrentOperationsStress() = runBlocking {
        val numOps = 50
        val jobs = (1..numOps).map { i ->
            async(Dispatchers.IO) {
                val config = StorageConfig.Telegram(
                    id = "tg_$i",
                    displayName = "TG $i",
                    botToken = "token_$i",
                    chatId = "chat_$i",
                    isEnabled = (i % 2 == 0)
                )
                repository.saveConfig(config)
                val fetched = repository.getConfigById("tg_$i")
                assertNotNull(fetched)

                if (i % 3 == 0) {
                    repository.deleteConfig("tg_$i")
                }
            }
        }

        jobs.awaitAll()

        val allConfigs = repository.getAllConfigs()
        assertTrue(allConfigs.isNotEmpty())
    }

    // Thread-safe fake SharedPreferences for concurrent coroutine testing
    class ThreadSafeFakeSharedPreferences : SharedPreferences {
        val map = ConcurrentHashMap<String, Any>()
        private val listeners = Collections.synchronizedSet(mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>())

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<*>)?.map { it.toString() }?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = ThreadSafeFakeEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            if (listener != null) listeners.add(listener)
        }
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            listeners.remove(listener)
        }

        class ThreadSafeFakeEditor(private val prefs: ThreadSafeFakeSharedPreferences) : SharedPreferences.Editor {
            private val temp = ConcurrentHashMap<String, Any>()
            private val toRemove = Collections.synchronizedSet(mutableSetOf<String>())
            private var shouldClear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) {
                    if (value != null) temp[key] = value else toRemove.add(key)
                }
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) {
                    if (values != null) temp[key] = HashSet(values) else toRemove.add(key)
                }
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
                synchronized(prefs) {
                    if (shouldClear) prefs.map.clear()
                    for (key in toRemove) prefs.map.remove(key)
                    for ((key, value) in temp) {
                        prefs.map[key] = value
                    }
                }
            }
        }
    }
}
