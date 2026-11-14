package dev.blazelight.p4oc.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reads `ConnectivityManager`'s metered flag for the default network. Unknown/unavailable
 * states are reported as unmetered so the base (unreduced) behavior is preserved.
 */
class AndroidNetworkMeteredSource(context: Context) : NetworkMeteredSource {
    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(ConnectivityManager::class.java)

    override val metered: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.isMetered())
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        trySend(isActiveNetworkMetered())
        val registered = connectivityManager
            ?.let { manager -> runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess }
            ?: false
        awaitClose {
            if (registered) {
                runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
            }
        }
    }.distinctUntilChanged()

    private fun isActiveNetworkMetered(): Boolean {
        val manager = connectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.isMetered()
    }
}

private fun NetworkCapabilities.isMetered(): Boolean =
    !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
