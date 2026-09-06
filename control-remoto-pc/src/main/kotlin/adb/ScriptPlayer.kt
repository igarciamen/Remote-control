package adb

import kotlinx.coroutines.delay

object ScriptPlayer {
    suspend fun play(script: Script) {
        for (action in script.actions) {
            when (action.type) {
                "tap" -> AdbController.tap(action.x ?: 0, action.y ?: 0)
                "swipe" -> AdbController.swipe(
                    action.x ?: 0, action.y ?: 0,
                    action.x2 ?: 0, action.y2 ?: 0,
                    action.durationMs ?: 300
                )
                "wait" -> delay(action.waitMs ?: 1000)
                "type_text" -> AdbController.typeText(action.text ?: "")
                "press_key" -> AdbController.pressKey(action.keyCode ?: AdbController.KeyCode.HOME)
            }
        }
    }
}