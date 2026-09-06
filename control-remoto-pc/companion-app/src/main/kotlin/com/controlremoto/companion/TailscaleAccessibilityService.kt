package com.controlremoto.companion

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Doesn't read or act on any accessibility event — it exists purely to keep
 * a persistent, system-bound process alive so that BootReceiver / the
 * watchdog have a *different* calling context available to attempt
 * launching Tailscale from.
 *
 * Why this is worth trying: a plain startActivity() from BootReceiver (a
 * BroadcastReceiver with proc state RECEIVER) was confirmed blocked by
 * Android's Background Activity Launch protection — see
 * TailscaleLauncher's doc comment for the exact log line. An active,
 * system-connected AccessibilityService process is a different kind of
 * component; whether its calling context is exempt from the same BAL check
 * is NOT guaranteed or documented — this is an experiment, not a confirmed
 * fix. If it doesn't work either, TailscaleLauncher's full-screen-intent
 * notification remains as the fallback (which does at least notify you,
 * even if it can't auto-open on this OEM).
 *
 * Requires the user to manually enable this once:
 * Ajustes → Accesibilidad → Servicios instalados → Control Remoto Companion → activar.
 * This is a genuine manual step — Android does not allow enabling
 * accessibility services programmatically, by design (it's a
 * powerful permission).
 */
class TailscaleAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
        Log.i(TAG, "Accessibility service destroyed")
    }

    // Intentionally empty — this service doesn't read screen content or
    // react to anything; it only exists to provide a running process.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /**
     * Attempts to open Tailscale directly from this service's own context.
     * Returns true only if the call completed without throwing — it does
     * NOT confirm Android actually let the activity appear on screen (BAL
     * blocks fail silently, same as everywhere else in this app). The real
     * confirmation is whether Tailscale shows "Connected" afterwards.
     */
    fun tryLaunchTailscale(): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
            ?: run {
                Log.w(TAG, "Tailscale not installed or no launch intent found")
                return false
            }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try {
            startActivity(launchIntent)
            Log.i(TAG, "startActivity() call completed from AccessibilityService context")
            true
        } catch (e: Exception) {
            Log.w(TAG, "startActivity() from AccessibilityService failed: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "TailscaleAccessSvc"
        private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

        @Volatile
        private var instance: TailscaleAccessibilityService? = null

        /** Null if the user hasn't enabled the service in Settings. */
        fun getRunningInstance(): TailscaleAccessibilityService? = instance
    }
}