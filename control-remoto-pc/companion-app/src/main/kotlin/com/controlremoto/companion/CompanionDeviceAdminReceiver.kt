package com.controlremoto.companion

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Once activated (Settings → Security → Device administrators, or via the
 * button in MainActivity), the app can no longer be uninstalled directly —
 * this receiver has to be deactivated first.
 *
 * This is intentionally minimal: no password policies, no wipe capability,
 * just the anti-uninstall friction.
 */
class CompanionDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Control Remoto: administrador de dispositivo activado", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Control Remoto: administrador de dispositivo desactivado", Toast.LENGTH_SHORT).show()
    }
}