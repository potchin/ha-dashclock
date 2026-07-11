package uk.co.potchin.hadashclock

import android.app.Activity
import android.os.Bundle

/**
 * Invisible "trampoline" activity used as the widget's clickIntent target: tapping
 * the extension's status/text starts this activity, which immediately notifies
 * RefreshSignalProvider's content URI (which the extension registers via
 * addWatchContentUris()) and finishes without ever actually becoming visible. See
 * RefreshSignalProvider for why a content URI, rather than a broadcast or direct
 * service call, is the correct mechanism here.
 *
 * android:theme is Theme.Translucent.NoDisplay (set in the manifest), which is
 * exactly designed for this "never actually shown, just do something and finish"
 * pattern.
 */
class RefreshTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentResolver.notifyChange(RefreshSignalProvider.REFRESH_URI, null)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
