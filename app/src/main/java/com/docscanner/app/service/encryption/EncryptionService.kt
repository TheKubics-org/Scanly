@file:Suppress("DEPRECATION")
package com.docscanner.app.service.encryption

import android.app.Application
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionService @Inject constructor(private val context: Application) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    fun encryptFile(inputFile: File, outputFile: File): Result<Unit> {
        return try {
            val encryptedFile = EncryptedFile.Builder(
                context,
                outputFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            FileInputStream(inputFile).use { fis ->
                encryptedFile.openFileOutput().use { fos ->
                    fis.copyTo(fos)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun decryptFile(encryptedFile: File, outputFile: File): Result<Unit> {
        return try {
            val fileToDecrypt = EncryptedFile.Builder(
                context,
                encryptedFile,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()

            fileToDecrypt.openFileInput().use { fis ->
                FileOutputStream(outputFile).use { fos ->
                    fis.copyTo(fos)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isEncrypted(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            val encryptedFile = EncryptedFile.Builder(
                context,
                file,
                masterKey,
                EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
            ).build()
            encryptedFile.openFileInput().use { fis ->
                fis.read()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
