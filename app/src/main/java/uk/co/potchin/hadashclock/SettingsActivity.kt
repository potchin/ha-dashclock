package uk.co.potchin.hadashclock

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/**
 * Simple settings screen for the extension: Home Assistant base URL, long-lived
 * access token, and the entity ID to poll. Reachable both as a normal launcher app
 * icon and as the extension's "settingsActivity" from DashClock/Chronus.
 */
class SettingsActivity : Activity() {

    private lateinit var baseUrlField: EditText
    private lateinit var tokenField: EditText
    private lateinit var entityIdField: EditText
    private lateinit var statusText: TextView

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        baseUrlField = findViewById(R.id.field_base_url)
        tokenField = findViewById(R.id.field_access_token)
        entityIdField = findViewById(R.id.field_entity_id)
        statusText = findViewById(R.id.text_status)

        tokenField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        baseUrlField.setText(HaPrefs.getBaseUrl(this))
        tokenField.setText(HaPrefs.getAccessToken(this))
        entityIdField.setText(HaPrefs.getEntityId(this))

        findViewById<Button>(R.id.button_save).setOnClickListener { save() }
        findViewById<Button>(R.id.button_test).setOnClickListener { testConnection() }
    }

    private fun save() {
        val baseUrl = baseUrlField.text.toString().trim()
        val token = tokenField.text.toString().trim()
        val entityId = entityIdField.text.toString().trim().ifEmpty { HaPrefs.DEFAULT_ENTITY_ID }

        if (baseUrl.isBlank() || token.isBlank()) {
            statusText.text = getString(R.string.error_missing_fields)
            return
        }

        HaPrefs.save(this, baseUrl, token, entityId)
        entityIdField.setText(entityId)
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        statusText.text = getString(R.string.settings_saved)
    }

    private fun testConnection() {
        val baseUrl = baseUrlField.text.toString().trim()
        val token = tokenField.text.toString().trim()
        val entityId = entityIdField.text.toString().trim().ifEmpty { HaPrefs.DEFAULT_ENTITY_ID }

        statusText.text = getString(R.string.testing_connection)

        Thread {
            val result = HaApiClient.fetchEntityState(baseUrl, token, entityId)
            mainHandler.post {
                statusText.text = when (result) {
                    is HaResult.Success -> getString(R.string.test_success, result.stateText.take(60))
                    is HaResult.Error -> getString(R.string.test_failed, result.message)
                }
            }
        }.start()
    }
}
