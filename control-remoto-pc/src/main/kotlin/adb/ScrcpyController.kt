package adb

import java.io.File

object ScrcpyController {

    private const val SCRCPY_DIR = "C:\\scrcpy-win64-v4.1"
    private const val SCRCPY_EXE_PATH = "$SCRCPY_DIR\\scrcpy.exe"

    private var process: Process? = null

    fun start(ip: String, port: Int = 5555, windowTitle: String = "My Phone") {
        if (process?.isAlive == true) {
            println("scrcpy is already running.")
            return
        }
        val builder = ProcessBuilder(
            SCRCPY_EXE_PATH,
            "-s", "$ip:$port",
            "--window-title=$windowTitle",
            "--no-audio"
        )
        builder.directory(File(SCRCPY_DIR))
        builder.redirectErrorStream(true)
        process = builder.start()
    }

    fun stop() {
        val current = process
        process = null
        if (current == null) return
        try {
            current.destroy()
            // Give Windows a moment to actually release the window/port
            // before the caller tries to start a new instance right after.
            val exited = current.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            if (!exited) {
                current.destroyForcibly()
            }
        } catch (e: Exception) {
            println("Error stopping scrcpy: ${e.message}")
        }
    }

    fun isRunning(): Boolean = process?.isAlive == true
}