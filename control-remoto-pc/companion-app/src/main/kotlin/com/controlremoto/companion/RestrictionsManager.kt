package com.controlremoto.companion

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import android.util.Log

/**
 * Toggles a small, specific set of Device Owner restrictions — not a full
 * kiosk/launcher replacement. Everything else on the phone (Settings,
 * Security app, all normal apps) keeps working exactly as before; only
 * the handful of genuinely destructive actions below get disabled.
 *
 * All of this is fully reversible at any time by calling disable() again
 * — nothing here is a one-way trip, and nothing requires a factory reset
 * to undo. It can also be reverted remotely from the PC via ADB if the
 * app itself is ever unreachable:
 *   adb shell dpm clear-restriction com.controlremoto.companion android.os.DISALLOW_FACTORY_RESET
 *   (repeat per restriction — see RESTRICTIONS below for the exact names)
 */
object RestrictionsManager {
    private const val TAG = "RestrictionsManager"
    private const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"

    // Tried hiding com.android.settings too — confirmed via
    // `adb shell dumpsys package com.android.settings | findstr hidden`
    // that Android silently refuses (hidden=false even after the call),
    // even with Device Owner. Settings is one of a small set of packages
    // Android protects from being hidden by any app, no matter the
    // privilege level — a real platform limit, not a bug here. Only
    // Security Center (a removable Xiaomi extra, not core Android) can
    // actually be hidden this way.
    private val HIDDEN_PACKAGES = listOf(SECURITY_CENTER_PACKAGE)

    private val RESTRICTIONS = listOf(
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_UNINSTALL_APPS,
        UserManager.DISALLOW_NETWORK_RESET,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        // Added because Settings itself can't be hidden (confirmed:
        // Android refuses even for Device Owner) — these block the
        // specific actions inside Settings that actually cause real
        // damage, instead of hiding the whole app.
        UserManager.DISALLOW_CONFIG_LOCALE,      // can't leave the phone in an unreadable language
        UserManager.DISALLOW_CONFIG_VPN,         // can't disable/forget Tailscale
        UserManager.DISALLOW_APPS_CONTROL,       // can't force-stop / clear data via "App info"
        UserManager.DISALLOW_CONFIG_WIFI,        // can't disconnect/forget the saved WiFi
        UserManager.DISALLOW_MODIFY_ACCOUNTS     // can't add/remove Google/Xiaomi accounts
        // Deliberately NOT including DISALLOW_DEBUGGING_FEATURES: that
        // would kill Developer Options entirely, including USB/Wireless
        // debugging — the exact channel this whole project depends on to
        // control the phone remotely. Disabling it here would be close to
        // a point of no return, since ADB itself would stop working and
        // there'd be no remote way to undo it.
    )

    fun isEnabled(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        val admin = adminComponent(context)
        // If the first restriction is set, treat the whole group as "on" —
        // they're always toggled together by this app.
        return dpm.getUserRestrictions(admin).getBoolean(UserManager.DISALLOW_FACTORY_RESET, false)
    }

    fun enable(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Not device owner — cannot set restrictions")
            return false
        }
        val admin = adminComponent(context)
        return try {
            RESTRICTIONS.forEach { dpm.addUserRestriction(admin, it) }
            HIDDEN_PACKAGES.forEach { dpm.setApplicationHidden(admin, it, true) }
            Log.i(TAG, "Restrictions enabled")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable restrictions: ${e.message}")
            false
        }
    }

    fun disable(context: Context): Boolean {
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "Not device owner — cannot clear restrictions")
            return false
        }
        val admin = adminComponent(context)
        return try {
            RESTRICTIONS.forEach { dpm.clearUserRestriction(admin, it) }
            HIDDEN_PACKAGES.forEach { dpm.setApplicationHidden(admin, it, false) }
            Log.i(TAG, "Restrictions disabled")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable restrictions: ${e.message}")
            false
        }
    }

    private fun adminComponent(context: Context) =
        ComponentName(context, CompanionDeviceAdminReceiver::class.java)
}