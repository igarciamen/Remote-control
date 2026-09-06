package security

import java.io.File
import java.security.MessageDigest

object PasswordStore {
    private val file = File(System.getProperty("user.home"), ".control-remoto-pc/password.hash")

    fun isPasswordSet(): Boolean = file.exists()

    fun setPassword(password: String) {
        file.parentFile?.mkdirs()
        file.writeText(hash(password))
    }

    fun verify(password: String): Boolean {
        if (!file.exists()) return false
        return file.readText() == hash(password)
    }

    private fun hash(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}