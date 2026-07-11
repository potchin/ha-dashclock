package uk.co.potchin.hadashclock

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for the Home Assistant connection settings (base URL, long-lived
 * access token, entity ID to poll). Backed by EncryptedSharedPreferences rather than
 * plaintext SharedPreferences, since the access token is a bearer credential.
 */
object HaPrefs {

    const val DEFAULT_ENTITY_ID = "sensor.dashboard_text"

    private const val PREFS_FILE_NAME = "ha_dashclock_secure_prefs"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_ENTITY_ID = "entity_id"

    private fun prefs(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getBaseUrl(context: Context): String =
        prefs(context).getString(KEY_BASE_URL, "").orEmpty()

    fun getAccessToken(context: Context): String =
        prefs(context).getString(KEY_ACCESS_TOKEN, "").orEmpty()

    fun getEntityId(context: Context): String =
        prefs(context).getString(KEY_ENTITY_ID, DEFAULT_ENTITY_ID) ?: DEFAULT_ENTITY_ID

    fun save(context: Context, baseUrl: String, accessToken: String, entityId: String) {
        prefs(context).edit()
            .putString(KEY_BASE_URL, baseUrl.trim().trimEnd('/'))
            .putString(KEY_ACCESS_TOKEN, accessToken.trim())
            .putString(KEY_ENTITY_ID, entityId.trim().ifEmpty { DEFAULT_ENTITY_ID })
            .apply()
    }

    fun isConfigured(context: Context): Boolean =
        getBaseUrl(context).isNotBlank() && getAccessToken(context).isNotBlank()
}
