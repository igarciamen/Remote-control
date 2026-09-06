package com.controlremoto.companion

import android.content.Context
import java.security.MessageDigest

/**
 * Same pattern as the desktop app's PasswordStore: a SHA-256 hash saved to
 * a private file, never the PIN itself. First call to setPin() establishes
 * it; every later call to verify() checks against that saved hash.
 */
object AdminPinStore {
    private const val FILE_NAME = "admin_pin.hash"

    private fun file(context: Context) = context.filesDir.resolve(FILE_NAME)

    fun isPinSet(context: Context): Boolean = file(context).exists()

    fun setPin(context: Context, pin: String) {
        file(context).writeText(hash(pin))
    }

    fun verify(context: Context, pin: String): Boolean {
        val f = file(context)
        if (!f.exists()) return false
        return f.readText() == hash(pin)
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}