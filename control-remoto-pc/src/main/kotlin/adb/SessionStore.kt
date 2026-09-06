package adb

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SessionState(val lastDeviceName: String)

object SessionStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file = File(System.getProperty("user.home"), ".control-remoto-pc/session.json")

    fun loadLastDevice(): String? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString<SessionState>(file.readText()).lastDeviceName
        } catch (e: Exception) {
            null
        }
    }

    fun saveLastDevice(name: String) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(SessionState(name)))
    }

    fun clear() {
        if (file.exists()) file.delete()
    }
}