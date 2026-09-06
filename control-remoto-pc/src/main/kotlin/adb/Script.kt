package adb

import kotlinx.serialization.Serializable

@Serializable
data class ScriptAction(
    val type: String, // "tap", "swipe", "wait", "type_text", "press_key"
    val x: Int? = null,
    val y: Int? = null,
    val x2: Int? = null,
    val y2: Int? = null,
    val durationMs: Int? = null,
    val text: String? = null,
    val keyCode: Int? = null,
    val waitMs: Long? = null
)

@Serializable
data class Script(
    val name: String,
    val actions: List<ScriptAction>
)