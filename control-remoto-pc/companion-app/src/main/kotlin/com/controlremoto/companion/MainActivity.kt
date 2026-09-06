package com.controlremoto.companion

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var restrictionsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(DevicePolicyManager::class.java)
        adminComponent = ComponentName(this, CompanionDeviceAdminReceiver::class.java)
        restrictionsButton = findViewById(R.id.restrictionsButton)

        // Start the watchdog immediately on install/open, without waiting
        // for the next reboot — BootReceiver alone would leave a gap right
        // after installing, before the first reboot ever happens.
        WatchdogScheduler.schedule(this)

        // Needed so PermissionWatcher's alert notification can actually be
        // shown (Android 13+ requires this to be granted at runtime).
        requestNotificationPermissionIfNeeded()

        findViewById<Button>(R.id.adminButton).setOnClickListener {
            if (!devicePolicyManager.isAdminActive(adminComponent)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Protege esta app contra desinstalación accidental."
                    )
                }
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.batteryButton).setOnClickListener {
            val powerManager = getSystemService(PowerManager::class.java)
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

        restrictionsButton.setOnClickListener { onRestrictionsButtonClicked() }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        // Also re-check on every visit to the screen, not just from the
        // periodic 15-min watchdog — catches the problem sooner if you
        // happen to open the app right after it broke.
        PermissionWatcher.checkAndAlert(this)
    }

    // --- Restrictions toggle, PIN-protected in both directions ---------

    private fun onRestrictionsButtonClicked() {
        if (!AdminPinStore.isPinSet(this)) {
            promptSetPin()
            return
        }
        val currentlyEnabled = RestrictionsManager.isEnabled(this)
        promptVerifyPin { pin ->
            if (!AdminPinStore.verify(this, pin)) {
                Toast.makeText(this, "PIN incorrecto", Toast.LENGTH_SHORT).show()
                return@promptVerifyPin
            }
            val success = if (currentlyEnabled) {
                RestrictionsManager.disable(this)
            } else {
                RestrictionsManager.enable(this)
            }
            if (success) {
                Toast.makeText(
                    this,
                    if (currentlyEnabled) "Restricciones desactivadas" else "Restricciones activadas",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(this, "No se pudo aplicar el cambio", Toast.LENGTH_SHORT).show()
            }
            refreshStatus()
        }
    }

    private fun promptSetPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Elige un PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Configurar PIN de administrador")
            .setMessage("Este PIN se pedirá cada vez que actives o desactives las restricciones.")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val pin = input.text.toString()
                if (pin.length < 4) {
                    Toast.makeText(this, "El PIN debe tener al menos 4 dígitos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                AdminPinStore.setPin(this, pin)
                Toast.makeText(this, "PIN guardado. Toca el botón de nuevo para activar restricciones.", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun promptVerifyPin(onVerified: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Ingresa el PIN")
            .setView(input)
            .setPositiveButton("Continuar") { _, _ -> onVerified(input.text.toString()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
            }
        }
    }

    private fun refreshStatus() {
        val isAdmin = devicePolicyManager.isAdminActive(adminComponent)
        val powerManager = getSystemService(PowerManager::class.java)
        val batteryExempt = powerManager.isIgnoringBatteryOptimizations(packageName)
        val secureSettingsOk = PermissionWatcher.hasSecureSettingsPermission(this)
        val restrictionsOn = RestrictionsManager.isEnabled(this)

        findViewById<TextView>(R.id.statusText).text = buildString {
            append(if (isAdmin) "✔ Device Admin activo\n" else "✘ Device Admin NO activo\n")
            append(if (batteryExempt) "✔ Excluida de optimización de batería\n" else "✘ NO excluida de batería\n")
            append(
                if (secureSettingsOk) "✔ Permiso de ajustes seguros OK\n"
                else "✘ SIN permiso de ajustes seguros — reactiva \"Depuración USB (Ajustes de seguridad)\" y vuelve a conceder por cable\n"
            )
            append(if (restrictionsOn) "🔒 Restricciones ACTIVAS" else "🔓 Restricciones inactivas")
        }

        restrictionsButton.text = if (restrictionsOn) "Quitar restricciones (PIN)" else "Restringir acceso (PIN)"

        // While restrictions are on, these two buttons are locked too — no
        // point letting someone poke at admin/battery system dialogs while
        // the phone is meant to be in restricted mode.
        findViewById<Button>(R.id.adminButton).isEnabled = !restrictionsOn
        findViewById<Button>(R.id.batteryButton).isEnabled = !restrictionsOn
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 100
    }
}