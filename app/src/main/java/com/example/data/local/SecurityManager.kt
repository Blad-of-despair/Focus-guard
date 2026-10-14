package com.example.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecurityManager(private val context: Context, private val prefs: FocusGuardPreferences) {

    private val keyStore: KeyStore? by lazy {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        } catch (e: Throwable) {
            null
        }
    }

    companion object {
        private const val KEY_ALIAS = "FocusGuardMasterKey"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128

        @Volatile
        var isSessionUnlocked: Boolean = false
        var lastUnlockedTime: Long = 0L

        fun markUnlocked() {
            isSessionUnlocked = true
            lastUnlockedTime = System.currentTimeMillis()
        }

        fun lockSession() {
            isSessionUnlocked = false
        }
    }

    init {
        createKeyStoreKeyIfNeeded()
    }

    private fun createKeyStoreKeyIfNeeded() {
        try {
            val ks = keyStore ?: return
            if (!ks.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        return biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray(Charsets.UTF_8))
    }

    suspend fun setupNewPin(pin: String) {
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)

        val hash = hashPin(pin, salt)
        val saltHex = bytesToHex(salt)
        val hashHex = bytesToHex(hash)

        prefs.savePin(saltHex, hashHex)
        markUnlocked()
    }

    suspend fun verifyPin(inputPin: String): Boolean {
        val currentPrefs = context.dataStore.data.first()
        val saltHex = currentPrefs[FocusGuardPreferences.KEY_PIN_SALT] ?: return false
        val storedHashHex = currentPrefs[FocusGuardPreferences.KEY_PIN_HASH] ?: return false

        val salt = hexToBytes(saltHex)
        val computedHash = hashPin(inputPin, salt)
        val computedHashHex = bytesToHex(computedHash)

        val isMatch = constantTimeEquals(computedHashHex, storedHashHex)
        if (isMatch) {
            markUnlocked()
        }
        return isMatch
    }

    suspend fun isPinConfigured(): Boolean {
        val currentPrefs = context.dataStore.data.first()
        return currentPrefs[FocusGuardPreferences.KEY_IS_PIN_SET] ?: false
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val result = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val octet = byte.toInt()
            val firstIndex = (octet and 0xF0).ushr(4)
            val secondIndex = octet and 0x0F
            result.append(hexChars[firstIndex])
            result.append(hexChars[secondIndex])
        }
        return result.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = (
                    (Character.digit(hex[i], 16) shl 4) +
                            Character.digit(hex[i + 1], 16)
                    ).toByte()
            i += 2
        }
        return data
    }
}
