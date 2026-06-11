package com.fenl.fenlzer.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreApiTokenStore(
    context: Context
) : ApiTokenStore {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        TOKEN_PREFS_NAME,
        Context.MODE_PRIVATE
    )

    override fun saveToken(token: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(KEY_IV, Base64.getEncoder().encodeToString(cipher.iv))
            .putString(KEY_TOKEN, Base64.getEncoder().encodeToString(encrypted))
            .apply()
    }

    override fun getToken(): String? {
        val encodedIv = preferences.getString(KEY_IV, null) ?: return null
        val encodedToken = preferences.getString(KEY_TOKEN, null) ?: return null

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.getDecoder().decode(encodedIv))
        )

        val decrypted = cipher.doFinal(Base64.getDecoder().decode(encodedToken))
        return decrypted.toString(Charsets.UTF_8)
    }

    override fun clearToken() {
        preferences.edit()
            .remove(KEY_IV)
            .remove(KEY_TOKEN)
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val TOKEN_PREFS_NAME = "fenlzer_secure_api_token"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "fenlzer_api_token_key"
        const val KEY_IV = "api_token_iv"
        const val KEY_TOKEN = "api_token_ciphertext"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
