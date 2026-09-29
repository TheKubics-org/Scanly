@file:Suppress("DEPRECATION")
package com.thekubics.scanly.data.vault

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.thekubics.scanly.data.storage.GoogleDriveAuthType
import com.thekubics.scanly.data.storage.StorageConfig
import com.thekubics.scanly.data.storage.StorageProvider
import com.thekubics.scanly.data.storage.StorageProviderRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedStorageVaultRepositoryImpl(
    @ApplicationContext private val context: Context,
    private val registry: StorageProviderRegistry,
    private val ioDispatcher: CoroutineDispatcher
) : StorageVaultRepository {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        registry: StorageProviderRegistry
    ) : this(context, registry, Dispatchers.IO)

    private val mutex = Mutex()

    private val prefs: SharedPreferences by lazy {
        createSecurePreferences()
    }

    private val _configsFlow = MutableStateFlow<List<StorageConfig>>(emptyList())
    private val _activeConfigFlow = MutableStateFlow<StorageConfig?>(null)

    init {
        // Guarantee the app never crashes at startup if Keystore/secure storage is unavailable.
        // In that rare case the vault degrades to a non-persistent in-memory store.
        runCatching { loadInitialState() }
    }

    private fun createSecurePreferences(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Throwable) {
            // Allow plaintext fallback ONLY in JVM test runners without hardware Android Keystore
            if (isJvmTestEnvironment()) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            } else {
                // Never persist BYOS credentials to insecure plaintext storage on-device, and
                // never crash the app — degrade to a transient in-memory vault instead.
                InMemoryPreferences()
            }
        }
    }

    private fun isJvmTestEnvironment(): Boolean {
        val vmName = System.getProperty("java.vm.name") ?: ""
        return vmName.contains("HotSpot", ignoreCase = true) ||
               vmName.contains("OpenJDK", ignoreCase = true) ||
               vmName.contains("JVM", ignoreCase = true)
    }

    private fun loadInitialState() {
        val configs = readConfigsFromPrefs()
        _configsFlow.value = configs
        val activeId = prefs.getString(KEY_ACTIVE_PROVIDER_ID, null)
        _activeConfigFlow.value = configs.find { it.id == activeId } ?: configs.firstOrNull { it.isEnabled }
    }

    override suspend fun getAllConfigs(): List<StorageConfig> = withContext(ioDispatcher) {
        mutex.withLock {
            readConfigsFromPrefs()
        }
    }

    override suspend fun getConfigById(id: String): StorageConfig? = withContext(ioDispatcher) {
        mutex.withLock {
            readConfigById(id)
        }
    }

    override suspend fun saveConfig(config: StorageConfig) = withContext(ioDispatcher) {
        mutex.withLock {
            val currentIds = prefs.getStringSet(KEY_CONFIG_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
            currentIds.add(config.id)

            val serialized = serializeConfig(config)
            prefs.edit()
                .putStringSet(KEY_CONFIG_IDS, currentIds)
                .putString(KEY_CONFIG_PREFIX + config.id, serialized)
                .apply()

            val updatedConfigs = readConfigsFromPrefs()
            _configsFlow.value = updatedConfigs

            val currentActiveId = prefs.getString(KEY_ACTIVE_PROVIDER_ID, null)
            if (currentActiveId == null || currentActiveId == config.id) {
                setActiveProviderIdInternal(config.id)
            } else {
                _activeConfigFlow.value = updatedConfigs.find { it.id == currentActiveId }
            }
        }
    }

    override suspend fun deleteConfig(id: String) = withContext(ioDispatcher) {
        mutex.withLock {
            val currentIds = prefs.getStringSet(KEY_CONFIG_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
            currentIds.remove(id)

            val currentActiveId = prefs.getString(KEY_ACTIVE_PROVIDER_ID, null)
            val editor = prefs.edit()
                .putStringSet(KEY_CONFIG_IDS, currentIds)
                .remove(KEY_CONFIG_PREFIX + id)

            if (currentActiveId == id) {
                // Deleted config was the active one — clear and pick a new fallback
                editor.remove(KEY_ACTIVE_PROVIDER_ID)
                editor.apply()

                val updatedConfigs = readConfigsFromPrefs()
                _configsFlow.value = updatedConfigs
                val newActive = updatedConfigs.firstOrNull { it.isEnabled }
                setActiveProviderIdInternal(newActive?.id)
            } else {
                // Non-active config deleted — preserve active provider selection as-is
                editor.apply()

                val updatedConfigs = readConfigsFromPrefs()
                _configsFlow.value = updatedConfigs
                _activeConfigFlow.value = updatedConfigs.find { it.id == currentActiveId }
            }
        }
    }

    override suspend fun getActiveProviderId(): String? = withContext(ioDispatcher) {
        mutex.withLock {
            prefs.getString(KEY_ACTIVE_PROVIDER_ID, null)
        }
    }

    override suspend fun setActiveProviderId(id: String?) = withContext(ioDispatcher) {
        mutex.withLock {
            setActiveProviderIdInternal(id)
        }
    }

    private fun setActiveProviderIdInternal(id: String?) {
        if (id == null) {
            prefs.edit().remove(KEY_ACTIVE_PROVIDER_ID).apply()
            _activeConfigFlow.value = null
        } else {
            prefs.edit().putString(KEY_ACTIVE_PROVIDER_ID, id).apply()
            val config = readConfigById(id)
            _activeConfigFlow.value = config
        }
    }

    override suspend fun getActiveProvider(): StorageProvider? = withContext(ioDispatcher) {
        mutex.withLock {
            val activeId = prefs.getString(KEY_ACTIVE_PROVIDER_ID, null)
            val config = (if (activeId != null) readConfigById(activeId) else null)
                ?: readConfigsFromPrefs().firstOrNull { it.isEnabled }
                ?: return@withContext null
            registry.createProvider(config)
        }
    }

    override suspend fun getProviderById(id: String): StorageProvider? = withContext(ioDispatcher) {
        mutex.withLock {
            val config = readConfigById(id) ?: return@withContext null
            registry.createProvider(config)
        }
    }

    override fun observeConfigs(): Flow<List<StorageConfig>> = _configsFlow.asStateFlow()

    override fun observeActiveConfig(): Flow<StorageConfig?> = _activeConfigFlow.asStateFlow()

    private fun readConfigsFromPrefs(): List<StorageConfig> {
        val ids = prefs.getStringSet(KEY_CONFIG_IDS, emptySet()) ?: emptySet()
        return ids.mapNotNull { readConfigById(it) }
    }

    private fun readConfigById(id: String): StorageConfig? {
        val rawJson = prefs.getString(KEY_CONFIG_PREFIX + id, null) ?: return null
        return deserializeConfig(rawJson)
    }

    companion object {
        private const val PREFS_NAME = "scanly_secure_byos_vault"
        private const val KEY_ACTIVE_PROVIDER_ID = "active_provider_id"
        private const val KEY_CONFIG_IDS = "config_ids"
        private const val KEY_CONFIG_PREFIX = "config_json_"

        fun serializeConfig(config: StorageConfig): String {
            val json = JSONObject()
            json.put("type", when (config) {
                is StorageConfig.Telegram -> "TELEGRAM"
                is StorageConfig.CloudflareR2 -> "CLOUDFLARE_R2"
                is StorageConfig.GoogleDrive -> "GOOGLE_DRIVE"
            })
            json.put("id", config.id)
            json.put("displayName", config.displayName)
            json.put("isEnabled", config.isEnabled)
            when (config) {
                is StorageConfig.Telegram -> {
                    json.put("botToken", config.botToken)
                    json.put("chatId", config.chatId)
                }
                is StorageConfig.CloudflareR2 -> {
                    json.put("endpointUrl", config.endpointUrl)
                    json.put("bucketName", config.bucketName)
                    json.put("accessKeyId", config.accessKeyId)
                    json.put("secretAccessKey", config.secretAccessKey)
                    json.put("region", config.region)
                }
                is StorageConfig.GoogleDrive -> {
                    json.put("authType", config.authType.name)
                    json.put("credentialsJson", config.credentialsJson)
                    config.folderId?.let { json.put("folderId", it) }
                }
            }
            return json.toString()
        }

        fun deserializeConfig(json: String): StorageConfig? {
            return try {
                val obj = JSONObject(json)
                val type = obj.optString("type").ifBlank { return null }
                val id = obj.optString("id").ifBlank { return null }
                val displayName = obj.optString("displayName", "")
                val isEnabled = obj.optBoolean("isEnabled", true)

                when (type) {
                    "TELEGRAM" -> {
                        StorageConfig.Telegram(
                            id, displayName, isEnabled,
                            botToken = obj.optString("botToken"),
                            chatId = obj.optString("chatId")
                        )
                    }
                    "CLOUDFLARE_R2" -> {
                        StorageConfig.CloudflareR2(
                            id, displayName, isEnabled,
                            endpointUrl = obj.optString("endpointUrl"),
                            bucketName = obj.optString("bucketName"),
                            accessKeyId = obj.optString("accessKeyId"),
                            secretAccessKey = obj.optString("secretAccessKey"),
                            region = obj.optString("region", "auto")
                        )
                    }
                    "GOOGLE_DRIVE" -> {
                        val authType = runCatching {
                            GoogleDriveAuthType.valueOf(obj.optString("authType", "OAUTH2"))
                        }.getOrDefault(GoogleDriveAuthType.OAUTH2)
                        StorageConfig.GoogleDrive(
                            id, displayName, isEnabled, authType,
                            credentialsJson = obj.optString("credentialsJson"),
                            folderId = if (obj.has("folderId")) obj.optString("folderId") else null
                        )
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Ephemeral SharedPreferences that never touches disk. Used only as a last-resort
 * fallback when hardware-backed Keystore is unavailable, so the app keeps running
 * without ever persisting BYOS credentials insecurely.
 */
private class InMemoryPreferences : SharedPreferences {
    private val map = java.util.concurrent.ConcurrentHashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = map.toMap().toMutableMap()

    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (map[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue

    override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue

    override fun contains(key: String): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor { pending[key] = value; return this }
        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor { pending[key] = values; return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { pending[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { pending[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { pending[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { pending[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { removals.add(key); return this }
        override fun clear(): SharedPreferences.Editor { pending.clear(); removals.clear(); return this }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            removals.forEach { map.remove(it) }
            pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            removals.clear()
            pending.clear()
        }
    }
}
