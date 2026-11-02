package com.bnyro.wallpaper.sync.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM encrypted group-device secrets, with the wrapping key held by Android Keystore. */
class DeviceCredentialStore(context: Context) {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    fun createIdentity(): DeviceIdentity = synchronized(lock) {
        val id = UUID.randomUUID().toString()
        val secretBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        try {
            val secret = secretBytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(secretBytes)
            val combined = iv + encrypted
            if (!preferences.edit().putString(PREFIX + id, Base64.encodeToString(combined, Base64.NO_WRAP)).commit()) {
                throw GeneralSecurityException("Could not persist device credential")
            }
            DeviceIdentity(id, secret)
        } finally { secretBytes.fill(0) }
    }

    fun loadIdentity(deviceId: String): DeviceIdentity? = synchronized(lock) {
        if (!UUID_REGEX.matches(deviceId)) return@synchronized null
        val encoded = preferences.getString(PREFIX + deviceId, null) ?: return@synchronized null
        try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            require(combined.size > 12)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, combined.copyOfRange(0, 12)))
            val secretBytes = cipher.doFinal(combined.copyOfRange(12, combined.size))
            try {
                if (secretBytes.size != 32) null
                else DeviceIdentity(deviceId, secretBytes.joinToString("") { "%02x".format(it.toInt() and 0xff) })
            } finally { secretBytes.fill(0) }
        } catch (_: GeneralSecurityException) { null } catch (_: IllegalArgumentException) { null }
    }

    fun deleteIdentity(deviceId: String): Boolean = synchronized(lock) {
        preferences.edit().remove(PREFIX + deviceId).commit()
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build())
            generateKey()
        }
    }

    data class DeviceIdentity(val deviceId: String, val secret: String)

    private companion object {
        const val PREFS = "nurva_device_credentials"
        const val PREFIX = "device_secret_"
        const val KEY_ALIAS = "nurva_device_credentials_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }
}
