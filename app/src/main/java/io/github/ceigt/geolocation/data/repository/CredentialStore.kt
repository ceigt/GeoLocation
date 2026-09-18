package io.github.ceigt.geolocation.data.repository

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import io.github.ceigt.geolocation.data.KEY_AMAP_SECURITY_CODE
import io.github.ceigt.geolocation.data.KEY_AMAP_WEB_KEY
import io.github.ceigt.geolocation.data.KEY_BAIDU_MAP_AK
import io.github.ceigt.geolocation.data.KEY_GOOGLE_MAPS_API_KEY
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts user-provided web-map credentials at rest with a non-exportable Android Keystore key. */
internal object CredentialStore {
    private const val TAG = "CredentialStore"
    private const val KEY_ALIAS = "geolocation_map_credentials_v1"
    private const val PREFIX = "enc:v1:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val credentialKeys = setOf(
        KEY_BAIDU_MAP_AK, KEY_AMAP_WEB_KEY, KEY_AMAP_SECURITY_CODE, KEY_GOOGLE_MAPS_API_KEY
    )

    fun read(prefs: SharedPreferences, key: String, default: String = ""): String {
        val stored = prefs.getString(key, null) ?: return default
        if (!stored.startsWith(PREFIX)) return stored
        return runCatching { decrypt(stored) }
            .onFailure { Log.e(TAG, "Unable to decrypt $key", it) }
            .getOrDefault(default)
    }

    fun encrypted(value: String): String = if (value.isEmpty()) "" else encrypt(value)

    /** One-time, best-effort migration for credentials written by versions before 2.1.6. */
    fun migratePlaintext(prefs: SharedPreferences) {
        val plaintext = credentialKeys.mapNotNull { key ->
            prefs.getString(key, null)?.takeIf { it.isNotEmpty() && !it.startsWith(PREFIX) }
                ?.let { key to it }
        }
        if (plaintext.isEmpty()) return
        runCatching {
            val editor = prefs.edit()
            plaintext.forEach { (key, value) -> editor.putString(key, encrypt(value)) }
            check(editor.commit()) { "Credential migration could not be persisted" }
        }.onFailure { Log.e(TAG, "Credential migration failed", it) }
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val payload = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
        require(payload.size > 12) { "Invalid credential payload" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload, 0, 12))
        return cipher.doFinal(payload, 12, payload.size - 12).toString(Charsets.UTF_8)
    }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }
}
