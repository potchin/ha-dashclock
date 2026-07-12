package uk.co.potchin.hadashclock

import android.content.Intent
import com.google.android.apps.dashclock.api.DashClockExtension
import com.google.android.apps.dashclock.api.ExtensionData

/**
 * A minimal, world-readable DashClock extension that shows the current state of a
 * Home Assistant entity (typically a template sensor) in DashClock-compatible widget
 * hosts such as Chronus.
 *
 * Update strategy is deliberately "free" battery-wise: no custom AlarmManager/
 * WorkManager polling. We rely entirely on the host's own scheduling -
 * [onInitialize] opts into a refresh whenever the screen turns on
 * (UPDATE_REASON_SCREEN_ON), on top of DashClock's normal ~hourly periodic
 * refresh (UPDATE_REASON_PERIODIC) and manual refresh (UPDATE_REASON_MANUAL).
 * [onUpdateData] treats every reason identically and simply re-fetches the
 * entity's current state, which is the correct behaviour for a simple
 * polling extension like this one.
 */
class HaDashClockExtension : DashClockExtension() {

    override fun onInitialize(isReconnect: Boolean) {
        // Refresh whenever the screen turns on, in addition to DashClock's normal
        // ~hourly periodic refresh. Deliberately NOT using any custom
        // AlarmManager/WorkManager polling - screen-on + periodic is the
        // battery-friendly way to get "near real-time" updates via this API.
        setUpdateWhenScreenOn(true)

        // Lets RefreshTapActivity (the widget's clickIntent target) force an
        // out-of-band refresh by notifying this content URI - see
        // RefreshSignalProvider for details.
        addWatchContentUris(arrayOf(RefreshSignalProvider.REFRESH_URI.toString()))
    }

    override fun onUpdateData(reason: Int) {
        val context = applicationContext
        // Tapping the widget's text launches this invisible trampoline activity,
        // which triggers an immediate refresh instead of opening settings.
        val tapIntent = Intent(context, RefreshTapActivity::class.java)

        if (!HaPrefs.isConfigured(context)) {
            publish(
                status = getString(R.string.status_not_configured),
                title = getString(R.string.title_not_configured),
                body = getString(R.string.body_not_configured),
                clickIntent = tapIntent
            )
            return
        }

        val baseUrl = HaPrefs.getBaseUrl(context)
        val accessToken = HaPrefs.getAccessToken(context)
        val entityId = HaPrefs.getEntityId(context)

        when (val result = HaApiClient.fetchEntityState(context, baseUrl, accessToken, entityId)) {
            is HaResult.Success -> {
                val text = result.stateText.trim()
                val newlineIndex = text.indexOf('\n')

                // Avoid showing the same text twice (as both expandedTitle and
                // expandedBody). The first line becomes the headline; only the
                // *remaining* lines become the body. For a single-line state
                // short enough to fit entirely in the title, there's nothing
                // left to show below it, so body is omitted entirely.
                val headline: String
                val body: String?
                if (newlineIndex == -1) {
                    headline = text
                    body = if (text.length > ExtensionData.MAX_EXPANDED_TITLE_LENGTH) text else null
                } else {
                    headline = text.substring(0, newlineIndex).trim()
                    body = text.substring(newlineIndex + 1).trim().ifBlank { null }
                }

                publish(
                    status = headline.take(STATUS_PREVIEW_LENGTH),
                    title = headline,
                    body = body,
                    clickIntent = tapIntent
                )
            }

            is HaResult.Error -> {
                // Clear, explicit failure state rather than crashing or silently
                // leaving stale data on screen.
                publish(
                    status = getString(R.string.status_ha_error),
                    title = getString(R.string.status_ha_error),
                    body = result.message,
                    clickIntent = tapIntent
                )
            }
        }
    }

    private fun publish(status: String, title: String, body: String?, clickIntent: Intent) {
        val data = ExtensionData()
            .visible(true)
            .icon(R.drawable.ic_extension)
            .status(status)
            .expandedTitle(title)
            .clickIntent(clickIntent)

        if (body != null) {
            data.expandedBody(body)
        }

        // Enforces ExtensionData's documented MAX_*_LENGTH limits for us.
        data.clean()

        publishUpdate(data)
    }

    private companion object {
        const val STATUS_PREVIEW_LENGTH = 20
    }
}
