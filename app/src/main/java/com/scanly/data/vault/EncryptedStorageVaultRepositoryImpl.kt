package com.scanly.data.vault

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.scanly.data.storage.GoogleDriveAuthType
import com.scanly.data.storage.StorageConfig
import com.scanly.data.storage.StorageProvider
import com.scanly.data.storage.StorageProviderRegistry
import com.scanly.data.storage.StorageProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
        loadInitialState()
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
                throw SecurityException("Secure hardware Keystore initialization failed. Cannot persist BYOS credentials insecurely: ${e.localizedMessage}", e)
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
            return when (config) {
                is StorageConfig.Telegram -> {
                    """{"type":"TELEGRAM","id":"${escape(config.id)}","displayName":"${escape(config.displayName)}","isEnabled":${config.isEnabled},"botToken":"${escape(config.botToken)}","chatId":"${escape(config.chatId)}"}"""
                }
                is StorageConfig.CloudflareR2 -> {
                    """{"type":"CLOUDFLARE_R2","id":"${escape(config.id)}","displayName":"${escape(config.displayName)}","isEnabled":${config.isEnabled},"endpointUrl":"${escape(config.endpointUrl)}","bucketName":"${escape(config.bucketName)}","accessKeyId":"${escape(config.accessKeyId)}","secretAccessKey":"${escape(config.secretAccessKey)}","region":"${escape(config.region)}"}"""
                }
                is StorageConfig.GoogleDrive -> {
                    val folder = config.folderId?.let { "\"${escape(it)}\"" } ?: "null"
                    """{"type":"GOOGLE_DRIVE","id":"${escape(config.id)}","displayName":"${escape(config.displayName)}","isEnabled":${config.isEnabled},"authType":"${config.authType.name}","credentialsJson":"${escape(config.credentialsJson)}","folderId":$folder}"""
                }
            }
        }

        fun deserializeConfig(json: String): StorageConfig? {
            return try {
                val type = extractString(json, "type") ?: return null
                val id = extractString(json, "id") ?: return null
                val displayName = extractString(json, "displayName") ?: ""
                val isEnabled = extractBoolean(json, "isEnabled") ?: true

                when (type) {
                    "TELEGRAM" -> {
                        val botToken = extractString(json, "botToken") ?: ""
                        val chatId = extractString(json, "chatId") ?: ""
                        StorageConfig.Telegram(id, displayName, isEnabled, botToken, chatId)
                    }
                    "CLOUDFLARE_R2" -> {
                        val endpointUrl = extractString(json, "endpointUrl") ?: ""
                        val bucketName = extractString(json, "bucketName") ?: ""
                        val accessKeyId = extractString(json, "accessKeyId") ?: ""
                        val secretAccessKey = extractString(json, "secretAccessKey") ?: ""
                        val region = extractString(json, "region") ?: "auto"
                        StorageConfig.CloudflareR2(id, displayName, isEnabled, endpointUrl, bucketName, accessKeyId, secretAccessKey, region)
                    }
                    "GOOGLE_DRIVE" -> {
                        val authTypeName = extractString(json, "authType") ?: "OAUTH2"
                        val authType = try { GoogleDriveAuthType.valueOf(authTypeName) } catch (e: Exception) { GoogleDriveAuthType.OAUTH2 }
                        val credentialsJson = extractString(json, "credentialsJson") ?: ""
                        val folderId = extractNullableString(json, "folderId")
                        StorageConfig.GoogleDrive(id, displayName, isEnabled, authType, credentialsJson, folderId)
                    }
                    else -> null
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun escape(s: String): String {
            val sb = StringBuilder(s.length + 16)
            for (c in s) {
                when (c) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\b' -> sb.append("\\b")
                    '\u000c' -> sb.append("\\f")
                    else -> sb.append(c)
                }
            }
            return sb.toString()
        }

        private fun extractString(json: String, key: String): String? {
            val keyPattern = "\"$key\""
            var keyIndex = json.indexOf(keyPattern)
            while (keyIndex != -1) {
                var i = keyIndex + keyPattern.length
                while (i < json.length && json[i].isWhitespace()) i++
                if (i < json.length && json[i] == ':') {
                    i++
                    while (i < json.length && json[i].isWhitespace()) i++
                    if (i < json.length && json[i] == '"') {
                        i++ // skip opening quote
                        val sb = StringBuilder()
                        var escaped = false
                        while (i < json.length) {
                            val ch = json[i]
                            if (escaped) {
                                when (ch) {
                                    '\\' -> sb.append('\\')
                                    '"' -> sb.append('"')
                                    'n' -> sb.append('\n')
                                    'r' -> sb.append('\r')
                                    't' -> sb.append('\t')
                                    'b' -> sb.append('\b')
                                    'f' -> sb.append('\u000c')
                                    '/' -> sb.append('/')
                                    'u' -> {
                                        if (i + 4 < json.length) {
                                            val hex = json.substring(i + 1, i + 5)
                                            val code = hex.toIntOrNull(16)
                                            if (code != null) {
                                                sb.append(code.toChar())
                                                i += 4
                                            } else {
                                                sb.append("\\u")
                                            }
                                        } else {
                                            sb.append("\\u")
                                        }
                                    }
                                    else -> {
                                        sb.append('\\')
                                        sb.append(ch)
                                    }
                                }
                                escaped = false
                            } else if (ch == '\\') {
                                escaped = true
                            } else if (ch == '"') {
                                return sb.toString()
                            } else {
                                sb.append(ch)
                            }
                            i++
                        }
                        return null // unclosed string
                    } else if (json.startsWith("null", i)) {
                        return null
                    }
                }
                keyIndex = json.indexOf(keyPattern, keyIndex + 1)
            }
            return null
        }

        private fun extractNullableString(json: String, key: String): String? {
            val keyPattern = "\"$key\""
            var keyIndex = json.indexOf(keyPattern)
            while (keyIndex != -1) {
                var i = keyIndex + keyPattern.length
                while (i < json.length && json[i].isWhitespace()) i++
                if (i < json.length && json[i] == ':') {
                    i++
                    while (i < json.length && json[i].isWhitespace()) i++
                    if (json.startsWith("null", i)) return null
                    return extractString(json, key)
                }
                keyIndex = json.indexOf(keyPattern, keyIndex + 1)
            }
            return null
        }

        private fun extractBoolean(json: String, key: String): Boolean? {
            val keyPattern = "\"$key\""
            var keyIndex = json.indexOf(keyPattern)
            while (keyIndex != -1) {
                var i = keyIndex + keyPattern.length
                while (i < json.length && json[i].isWhitespace()) i++
                if (i < json.length && json[i] == ':') {
                    i++
                    while (i < json.length && json[i].isWhitespace()) i++
                    if (json.startsWith("true", i)) return true
                    if (json.startsWith("false", i)) return false
                    return null
                }
                keyIndex = json.indexOf(keyPattern, keyIndex + 1)
            }
            return null
        }
    }
}
