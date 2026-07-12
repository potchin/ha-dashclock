package uk.co.potchin.hadashclock

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Gives a radio that's still reconnecting - most commonly right after the screen turns
 * on and WiFi hasn't re-associated yet - a bounded chance to come back online *before*
 * even attempting the HTTP call. This is the fix for the extension racing ahead of WiFi
 * reconnection on wake: without it, the very first update after screen-on can hit a
 * "no network" error immediately, before Android has had a chance to reconnect.
 *
 * Returns immediately if a validated, internet-capable network is already active (the
 * common case - most updates aren't happening right after wake). Otherwise waits,
 * event-driven via [ConnectivityManager.NetworkCallback] (no polling), for one to
 * appear, up to [timeoutMs]. Always returns normally, never throws - if no network
 * shows up in time, the caller proceeds anyway and lets the HTTP call (with its own
 * retry logic) fail/succeed normally.
 */
internal fun awaitUsableNetwork(context: Context, timeoutMs: Long) {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return

    if (hasValidatedInternet(connectivityManager)) {
        return
    }

    val latch = CountDownLatch(1)
    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        .build()

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            latch.countDown()
        }
    }

    var registered = false
    try {
        connectivityManager.registerNetworkCallback(request, callback)
        registered = true
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
    } catch (e: Exception) {
        // Some restricted ROMs/security policies can refuse callback registration -
        // just proceed without waiting rather than failing the whole update.
    } finally {
        if (registered) {
            try {
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Already unregistered, or never fully registered - ignore.
            }
        }
    }
}

private fun hasValidatedInternet(connectivityManager: ConnectivityManager): Boolean {
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
