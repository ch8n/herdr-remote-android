package com.herdr.remote.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.net.NetworkInterface

enum class TailscaleState {
    CONNECTED,
    DISCONNECTED,
    NOT_INSTALLED
}

data class TailscaleStatus(
    val state: TailscaleState,
    val ipAddress: String? = null,
    val isAppInstalled: Boolean = false,
    val detail: String = ""
)

object TailscaleHelper {
    const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

    fun isTailscaleInstalled(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            val info = pm.getPackageInfo(TAILSCALE_PACKAGE, 0)
            info != null
        } catch (e: Exception) {
            try {
                val launch = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
                launch != null
            } catch (ex: Exception) {
                false
            }
        }
    }

    fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
    }

    fun getTailscaleIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (intf in interfaces) {
                val name = intf.name.lowercase()
                val addrs = intf.inetAddresses
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress ?: ""
                        // Tailscale IPv4 addresses are in the 100.64.0.0/10 CGNAT range
                        if (host.startsWith("100.") || name.contains("tun") || name.contains("tailscale")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return null
    }

    fun getStatus(context: Context): TailscaleStatus {
        val installed = isTailscaleInstalled(context)
        val vpnActive = isVpnActive(context)
        val tsIp = getTailscaleIpAddress()

        return when {
            vpnActive -> {
                TailscaleStatus(
                    state = TailscaleState.CONNECTED,
                    ipAddress = tsIp,
                    isAppInstalled = installed,
                    detail = if (tsIp != null) "Tailscale Active ($tsIp)" else "Tailscale VPN Active"
                )
            }
            installed -> {
                TailscaleStatus(
                    state = TailscaleState.DISCONNECTED,
                    ipAddress = null,
                    isAppInstalled = true,
                    detail = "Tailscale Disconnected • Tap to open"
                )
            }
            else -> {
                TailscaleStatus(
                    state = TailscaleState.NOT_INSTALLED,
                    ipAddress = null,
                    isAppInstalled = false,
                    detail = "Tailscale Not Installed • Tap to install"
                )
            }
        }
    }

    fun openTailscaleOrPlayStore(context: Context) {
        val pm = context.packageManager

        // 1. Direct launch intent for package
        try {
            val launchIntent = pm.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launchIntent)
                return
            }
        } catch (e: Exception) {
            // Fall through to query
        }

        // 2. Explicit MAIN/LAUNCHER query for package
        try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(TAILSCALE_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val matches = pm.queryIntentActivities(launcherIntent, 0)
            if (matches.isNotEmpty()) {
                context.startActivity(launcherIntent)
                return
            }
        } catch (e: Exception) {
            // Fall through to Play Store
        }

        // 3. Fallback to Google Play Store only if app is not installed
        try {
            val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$TAILSCALE_PACKAGE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playStoreIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$TAILSCALE_PACKAGE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    fun observeStatus(context: Context): Flow<TailscaleStatus> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

        trySend(getStatus(context))

        if (cm == null) {
            awaitClose {}
            return@callbackFlow
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getStatus(context))
            }

            override fun onLost(network: Network) {
                trySend(getStatus(context))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(getStatus(context))
            }
        }

        cm.registerNetworkCallback(request, callback)

        awaitClose {
            try {
                cm.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                // Ignore unregister errors
            }
        }
    }
}
