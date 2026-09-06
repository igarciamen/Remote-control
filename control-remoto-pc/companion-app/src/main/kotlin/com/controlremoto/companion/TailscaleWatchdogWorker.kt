package com.controlremoto.companion

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodic watchdog — complements BootReceiver.
 *
 * Tailscale's actual connection is now managed entirely by Android itself
 * via TailscaleVpnPolicy (the official Device Owner "always-on VPN" API),
 * so this worker's job is much simpler than it used to be:
 *   1. Re-apply the always-on VPN policy (idempotent) — MIUI has been
 *      observed to silently reset VPN-adjacent settings on its own, so
 *      this makes the setup self-healing rather than a one-time thing.
 *   2. If the VPN is still somehow down after that, post a quiet
 *      notification — nothing here ever opens Tailscale's screen or
 *      interrupts whatever the phone is doing.
 */
class TailscaleWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        Log.i(TAG, "Watchdog tick — checking Tailscale connectivity")

        PermissionWatcher.checkAndAlert(context)
        TailscaleVpnPolicy.ensureEnabled(context)

        if (!isVpnActive(context)) {
            Log.w(TAG, "No active VPN network found even with always-on policy applied")
            TailscaleLauncher.notifyDisconnected(context)
        } else {
            Log.i(TAG, "VPN network active, nothing to do")
        }

        trySetGlobalSetting(context, "adb_wifi_enabled", 1)
        // Re-applied every tick, not just at boot — this is specifically
        // to self-heal if someone reaches the "Wireless debugging
        // connected" system notification (a path that bypasses MIUI's own
        // app-lock on Settings) and turns off the master Developer Options
        // switch from there. Untested whether re-writing this actually
        // restores the toggle the same way adb_wifi_enabled does, but
        // costs nothing to keep trying.
        trySetGlobalSetting(context, "development_settings_enabled", 1)
        ensureWifiEnabled(context)

        return Result.success()
    }

    /**
     * Device Owner apps are specifically exempt from the Android 10+
     * restriction that normally blocks apps from toggling WiFi
     * programmatically — this is documented, not a workaround. Mobile
     * data has no equivalent public API at any privilege level (not even
     * for Device Owner), so it can't be self-healed the same way — that's
     * a real platform gap, not something missed here.
     */
    private fun ensureWifiEnabled(context: Context) {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            if (!wifiManager.isWifiEnabled) {
                @Suppress("DEPRECATION")
                wifiManager.isWifiEnabled = true
                Log.i(TAG, "Re-enabled WiFi (was off)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to re-enable WiFi: ${e.message}")
        }
    }

    /** Checks if any currently active network is a validated VPN connection. */
    private fun isVpnActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps != null &&
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    private fun trySetGlobalSetting(context: Context, key: String, value: Int) {
        try {
            Settings.Global.putInt(context.contentResolver, key, value)
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to write $key: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "TailscaleWatchdog"
        const val WORK_NAME = "tailscale_watchdog"
    }
}