package uk.co.potchin.hadashclock

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Result of a Home Assistant entity-state fetch. */
sealed class HaResult {
    data class Success(val stateText: String) : HaResult()
    data class Error(val message: String) : HaResult()
}

/**
 * Minimal client for Home Assistant's REST API:
 * GET {base_url}/api/states/{entity_id} with a long-lived access token.
 *
 * https://developers.home-assistant.io/docs/api/rest/
 */
object HaApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetchEntityState(baseUrl: String, accessToken: String, entityId: String): HaResult {
        val cleanBaseUrl = baseUrl.trim().trimEnd('/')
        val cleanToken = accessToken.trim()
        val cleanEntityId = entityId.trim()

        if (cleanBaseUrl.isBlank() || cleanToken.isBlank() || cleanEntityId.isBlank()) {
            return HaResult.Error("Not configured")
        }

        val request = Request.Builder()
            .url("$cleanBaseUrl/api/states/$cleanEntityId")
            .header("Authorization", "Bearer $cleanToken")
            .header("Content-Type", "application/json")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 ->
                        HaResult.Error("HA auth error (${response.code})")

                    response.code == 404 ->
                        HaResult.Error("Entity not found")

                    !response.isSuccessful ->
                        HaResult.Error("HA error (${response.code})")

                    else -> {
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) {
                            HaResult.Error("HA error (empty response)")
                        } else {
                            parseState(body)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            HaResult.Error("HA unreachable")
        } catch (e: Exception) {
            HaResult.Error("HA error")
        }
    }

    private fun parseState(body: String): HaResult {
        return try {
            // org.json unescapes \n sequences in the JSON payload back into real
            // newline characters automatically, so multiline sensor state comes
            // through as-is for ExtensionData.expandedBody() - no HTML/<br> needed.
            val json = JSONObject(body)
            val state = json.optString("state", "")
            HaResult.Success(state)

        } catch (e: Exception) {
            HaResult.Error("HA error (bad response)")
        }
    }
}
