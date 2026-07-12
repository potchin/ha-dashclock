package uk.co.potchin.hadashclock

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Result of a Home Assistant entity-state fetch.
 *
 * [Error.retryable] distinguishes transient failures worth retrying (network
 * unreachable, timeouts, 5xx server errors - the "wifi hasn't reconnected yet" /
 * "HA briefly restarting" family) from permanent ones that retrying can't fix
 * (bad token, unknown entity, not configured).
 */
sealed class HaResult {
    data class Success(val stateText: String) : HaResult()
    data class Error(val message: String, val retryable: Boolean = true) : HaResult()
}

/**
 * Minimal client for Home Assistant's REST API:
 * GET {base_url}/api/states/{entity_id} with a long-lived access token.
 *
 * https://developers.home-assistant.io/docs/api/rest/
 *
 * Retry strategy (all of this runs on DashClockExtension's own background
 * HandlerThread, never the main thread, so blocking here is safe - see
 * DashClockExtension.onUpdate()/onUpdateData() upstream):
 *   1. [awaitUsableNetwork] first gives a reconnecting radio (typically right after
 *      screen-on) a bounded, event-driven chance to come back before the first
 *      attempt even happens.
 *   2. The HTTP call itself is retried up to [MAX_ATTEMPTS] times with backoff
 *      ([RETRY_DELAYS_MS]), but only for [HaResult.Error.retryable] failures -
 *      permanent errors (auth, missing entity, not configured) fail fast instead
 *      of wasting time retrying something that can't succeed.
 */
object HaApiClient {

    private const val NETWORK_WAIT_TIMEOUT_MS = 10_000L
    private const val MAX_ATTEMPTS = 3
    private val RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetchEntityState(
        context: Context,
        baseUrl: String,
        accessToken: String,
        entityId: String
    ): HaResult {
        val cleanBaseUrl = baseUrl.trim().trimEnd('/')
        val cleanToken = accessToken.trim()
        val cleanEntityId = entityId.trim()

        if (cleanBaseUrl.isBlank() || cleanToken.isBlank() || cleanEntityId.isBlank()) {
            return HaResult.Error("Not configured", retryable = false)
        }

        awaitUsableNetwork(context, NETWORK_WAIT_TIMEOUT_MS)

        val request = Request.Builder()
            .url("$cleanBaseUrl/api/states/$cleanEntityId")
            .header("Authorization", "Bearer $cleanToken")
            .header("Content-Type", "application/json")
            .get()
            .build()

        var lastError = HaResult.Error("HA unreachable")
        for (attempt in 0 until MAX_ATTEMPTS) {
            when (val result = performRequest(request)) {
                is HaResult.Success -> return result
                is HaResult.Error -> {
                    lastError = result
                    val isLastAttempt = attempt == MAX_ATTEMPTS - 1
                    if (!result.retryable || isLastAttempt) {
                        return result
                    }
                    try {
                        Thread.sleep(RETRY_DELAYS_MS[attempt])
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return result
                    }
                }
            }
        }
        return lastError
    }

    private fun performRequest(request: Request): HaResult {
        return try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 ->
                        HaResult.Error("HA auth error (${response.code})", retryable = false)

                    response.code == 404 ->
                        HaResult.Error("Entity not found", retryable = false)

                    !response.isSuccessful ->
                        // Typically a 5xx - HA restarting/overloaded is exactly the kind
                        // of transient condition worth retrying.
                        HaResult.Error("HA error (${response.code})", retryable = true)

                    else -> {
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) {
                            HaResult.Error("HA error (empty response)", retryable = true)
                        } else {
                            parseState(body)
                        }
                    }
                }
            }
        } catch (e: IOException) {
            // Network unreachable, connection refused, timed out, DNS not resolved yet
            // (radio still reconnecting), etc. - the main case this retry logic exists
            // for.
            HaResult.Error("HA unreachable", retryable = true)
        } catch (e: Exception) {
            HaResult.Error("HA error", retryable = true)
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
            // Could be a genuinely malformed/unexpected payload, or a body that got
            // truncated mid-transfer by a flaky connection - worth one more try.
            HaResult.Error("HA error (bad response)", retryable = true)
        }
    }
}
