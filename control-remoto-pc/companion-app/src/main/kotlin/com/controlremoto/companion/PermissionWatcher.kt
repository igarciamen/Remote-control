package com.controlremoto.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Detects whether the app still holds WRITE_SECURE_SETTINGS, and — if it
 * silently lost it (e.g. MIUI reset the "Depuración USB (Ajustes de
 * seguridad)" toggle) — posts a notification so the person actually
 * notices, instead of BootReceiver/TailscaleWatchdogWorker failing quietly
 * forever.
 *
 * The alert is shown only once per "loss event" (tracked via
 * SharedPreferences) so it doesn't repeat every 15 minutes while the
 * problem remains unresolved — and it's cleared automatically once the
 * permission is detected as granted again.
 */
object PermissionWatcher {
    private const val PREFS_NAME = "permission_watcher"
    private const val KEY_ALERT_SHOWN = "secure_settings_alert_shown"
    private const val CHANNEL_ID = "permission_alerts"
    private const val NOTIFICATION_ID = 1001

    fun hasSecureSettingsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            "android.permission.WRITE_SECURE_SETTINGS"
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Call this from the watchdog (and from BootReceiver) on every check.
     * Shows an alert the first time the permission is found missing, and
     * silently resets the "already alerted" flag once it's granted again.
     */
    fun checkAndAlert(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val granted = hasSecureSettingsPermission(context)

        if (granted) {
            if (prefs.getBoolean(KEY_ALERT_SHOWN, false)) {
                prefs.edit().putBoolean(KEY_ALERT_SHOWN, false).apply()
                clearNotification(context)
            }
            return
        }

        if (!prefs.getBoolean(KEY_ALERT_SHOWN, false)) {
            showAlert(context)
            prefs.edit().putBoolean(KEY_ALERT_SHOWN, true).apply()
        }
    }

    private fun showAlert(context: Context) {
        createChannelIfNeeded(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Control Remoto Companion")
            .setContentText("Perdió el permiso de ajustes seguros — el auto-arranque de ADB/Tailscale dejó de funcionar")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "El permiso WRITE_SECURE_SETTINGS ya no está concedido. Probablemente MIUI " +
                            "reseteó \"Depuración USB (Ajustes de seguridad)\". Reactívalo y vuelve a " +
                            "correr por USB: adb shell pm grant com.controlremoto.companion " +
                            "android.permission.WRITE_SECURE_SETTINGS"
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — nothing more we can do from
            // a background worker; MainActivity's status line still shows it.
        }
    }

    private fun clearNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Alertas de permisos",
                    NotificationManager.IMPORTANCE_HIGH
                )
                manager.createNotificationChannel(channel)
            }
        }
    }
}