package adb

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object ScriptStore {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file = File(System.getProperty("user.home"), ".control-remoto-pc/scripts.json")

    fun load(): List<Script> {
        if (!file.exists()) return emptyList()
        return try {
            json.decodeFromString(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(scripts: List<Script>) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(scripts))
    }
}