package adb

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object DeviceStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file = File(System.getProperty("user.home"), ".control-remoto-pc/devices.json")

    fun load(): List<Device> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(devices: List<Device>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(devices))
    }
}