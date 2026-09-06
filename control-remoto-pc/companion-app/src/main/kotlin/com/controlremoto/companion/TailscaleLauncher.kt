package com.controlremoto.companion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Last-resort visibility only — TailscaleVpnPolicy (the always-on VPN
 * setting) is what actually keeps Tailscale connected now, entirely at
 * the OS level, without ever needing this app to open Tailscale's screen.
 *
 * This class just posts a quiet, ordinary notification if the watchdog
 * ever finds the VPN still down despite the always-on policy being
 * correctly applied — a safety net for visibility, not a mechanism that
 * tries to force anything open. It deliberately never calls
 * startActivity() on Tailscale: that approach was tried at length and
 * confirmed unreliable while the phone is locked (see TailscaleVpnPolicy's
 * doc comment for why), and intrusive while the phone is actively in use.
 */
object TailscaleLauncher {
    private const val TAG = "TailscaleLauncher"
    private const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    private const val CHANNEL_ID = "tailscale_relaunch"
    private const val NOTIFICATION_ID = 2001

    fun notifyDisconnected(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
        if (launchIntent == null) {
            Log.w(TAG, "Tailscale not installed or no launch intent found")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        createChannelIfNeeded(context)

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Tailscale desconectado")
            .setContentText("Toca para abrirlo si sigue sin reconectar solo")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(NOTIFICATION_ID, notification)
            Log.i(TAG, "Posted quiet notification — VPN still down despite always-on policy")
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to post notification: ${e.message}")
        }
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Reconexión de Tailscale",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                manager.createNotificationChannel(channel)
            }
        }
    }
}