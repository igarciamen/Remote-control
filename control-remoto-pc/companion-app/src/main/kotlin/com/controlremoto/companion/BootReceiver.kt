package com.controlremoto.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Fires on every boot. Re-enables ADB (classic + wireless) and makes sure
 * Tailscale is configured as the OS-managed always-on VPN — that's what
 * actually keeps it connected now (see TailscaleVpnPolicy), no Activity
 * launching involved, so nothing here ever needs to wait or touch the
 * screen.
 *
 * - `adb_enabled` (classic USB debugging) is documented to be settable
 *   this way when the app holds WRITE_SECURE_SETTINGS.
 * - `adb_wifi_enabled` (the wireless debugging toggle used day to day) is
 *   NOT a documented/stable setting for this API, but confirmed to work
 *   on this Redmi 13 (Android 15 / HyperOS) after real reboot tests.
 * - `development_settings_enabled` (the master "Developer Options" toggle
 *   itself) is also attempted, as defense in depth — someone could reach
 *   the "Wireless debugging connected" system notification directly (it
 *   bypasses MIUI's own app-lock on Settings, a real gap we found no way
 *   to close) and turn this master switch off from there. Re-applying it
 *   isn't confirmed to work the same way adb_wifi_enabled does — it's
 *   untested — but costs nothing to attempt as a second line of defense.
 *
 * WRITE_SECURE_SETTINGS has to be granted once, manually, from the PC:
 *   adb shell pm grant com.controlremoto.companion android.permission.WRITE_SECURE_SETTINGS
 * It survives reboots on its own after that — no need to run it again.
 * If MIUI's "Depuración USB (Ajustes de seguridad)" toggle ever gets reset,
 * this permission (and the ADB parts of this receiver) will silently stop
 * working until that toggle is re-enabled and the grant command is re-run.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.i(TAG, "Boot completed — attempting to persist ADB access and Tailscale connectivity")
        PermissionWatcher.checkAndAlert(context)
        trySetGlobalSetting(context, "adb_enabled", 1)
        trySetGlobalSetting(context, "adb_wifi_enabled", 1)
        trySetGlobalSetting(context, "development_settings_enabled", 1)
        TailscaleVpnPolicy.ensureEnabled(context)
        WatchdogScheduler.schedule(context)
    }

    private fun trySetGlobalSetting(context: Context, key: String, value: Int) {
        try {
            val applied = Settings.Global.putInt(context.contentResolver, key, value)
            Log.i(TAG, "Set global $key=$value -> applied=$applied")
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to write $key (grant WRITE_SECURE_SETTINGS first): ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ControlRemotoBoot"
    }
}