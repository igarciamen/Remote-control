package com.controlremoto.companion

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

/**
 * Uses the official Android Enterprise API to make Tailscale an "always-on
 * VPN" — this puts the OS itself in charge of Tailscale's VpnService
 * connection lifecycle (reconnecting it automatically on boot, network
 * changes, after being killed by Doze, etc.), with NO dependency on ever
 * opening Tailscale's Activity at all.
 *
 * Why this replaces the earlier "relaunch the Activity" approach: a real
 * reboot test proved that starting Tailscale's Activity from behind a
 * secure lock screen (via BootReceiver/the watchdog) never actually
 * completes its reconnect logic — Tailscale doesn't declare
 * `showWhenLocked` in its own manifest (only its developers could add
 * that), so the Activity gets created but never truly becomes active
 * while locked. No amount of extra delay fixed that; it's a hard OS-level
 * limit we don't control. This sidesteps it completely by never needing
 * an Activity in the first place.
 *
 * Requires this app to be the phone's Device Owner (confirmed active via
 * `dumpsys device_policy`) — setAlwaysOnVpnPackage() is a
 * DevicePolicyManager API restricted to device/profile owners.
 *
 * lockdownEnabled is explicitly false: other apps must keep working with
 * normal internet even while Tailscale's tunnel is reconnecting — this
 * was confirmed necessary earlier in the project (enabling lockdown
 * blocked WhatsApp/Chrome during VPN transitions). With lockdown off,
 * apps simply fall back to the normal network path whenever the VPN
 * tunnel isn't currently up — they're never forced through it.
 */
object TailscaleVpnPolicy {
    private const val TAG = "TailscaleVpnPolicy"
    private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

    /**
     * Idempotent — safe to call on every boot and every watchdog tick.
     * MIUI has been observed to silently reset several unrelated
     * VPN-adjacent settings on its own in this project, so re-applying
     * this periodically makes the whole thing self-healing instead of a
     * one-time setup that could quietly stop working.
     */
    fun ensureEnabled(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false

        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Not device owner — cannot set always-on VPN")
            return false
        }

        val admin = ComponentName(context, CompanionDeviceAdminReceiver::class.java)
        return try {
            dpm.setAlwaysOnVpnPackage(admin, TAILSCALE_PACKAGE, /* lockdownEnabled = */ false)
            Log.i(TAG, "Always-on VPN set to Tailscale (lockdown disabled)")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set always-on VPN: ${e.message}")
            false
        }
    }
}