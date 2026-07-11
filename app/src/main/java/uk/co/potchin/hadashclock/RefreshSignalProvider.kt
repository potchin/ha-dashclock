package uk.co.potchin.hadashclock

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * A minimal, data-less ContentProvider whose only purpose is to hand out a stable
 * content:// URI. The extension registers this URI with the host via
 * DashClockExtension#addWatchContentUris(), and our own RefreshTapActivity calls
 * ContentResolver#notifyChange() on it when the user taps the widget's text - which
 * makes the host call onUpdateData(UPDATE_REASON_CONTENT_CHANGED) for us. This is
 * the officially documented way for an extension to request an out-of-band refresh;
 * there is no other public API for an extension to "push" a manual update to the host.
 */
class RefreshSignalProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    companion object {
        const val AUTHORITY = "uk.co.potchin.hadashclock.refresh"
        val REFRESH_URI: Uri = Uri.parse("content://$AUTHORITY/trigger")
    }
}
