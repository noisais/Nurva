package com.bnyro.wallpaper.sync.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores Supabase access/refresh tokens encrypted with a key held by Android Keystore. */
class SecureSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): AuthSession? {
        val encoded = preferences.getString(SESSION_KEY, null) ?: return null
        return try {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            require(combined.size > IV_LENGTH)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, combined.copyOfRange(0, IV_LENGTH))
            )
            val clearText = cipher.doFinal(combined.copyOfRange(IV_LENGTH, combined.size))
            try {
                val json = JSONObject(clearText.toString(Charsets.UTF_8))
                AuthSession(
                    accessToken = json.getString("accessToken"),
                    refreshToken = json.getString("refreshToken"),
                    expiresAtEpochSeconds = json.getLong("expiresAt"),
                    userId = json.getString("userId"),
                    email = json.optString("email")
                )
            } finally {
                clearText.fill(0)
            }
        } catch (_: Exception) {
            clear()
            null
        }
    }

    fun save(session: AuthSession) {
        val clearText = JSONObject()
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("expiresAt", session.expiresAtEpochSeconds)
            .put("userId", session.userId)
            .put("email", session.email)
            .toString()
            .toByteArray(Charsets.UTF_8)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(clearText)
            val stored = cipher.iv + encrypted
            check(
                preferences.edit()
                    .putString(SESSION_KEY, Base64.encodeToString(stored, Base64.NO_WRAP))
                    .commit()
            ) { "Could not securely save the sign-in session" }
        } finally {
            clearText.fill(0)
        }
    }

    fun clear() {
        check(preferences.edit().remove(SESSION_KEY).commit()) { "Could not clear the sign-in session" }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .setUserAuthenticationRequired(false)
                    .build()
            )
            generateKey()
        }
    }

    companion object {
        private const val PREFERENCES = "nurva_secure_session"
        private const val SESSION_KEY = "encrypted_session_v1"
        private const val KEY_ALIAS = "nurva_auth_session_aes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
    }
}

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: String,
    val email: String
)
