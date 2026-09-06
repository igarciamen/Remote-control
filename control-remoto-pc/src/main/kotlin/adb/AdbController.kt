package adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Wrapper around the adb command-line tool.
 * Every function runs on Dispatchers.IO so it never blocks the UI thread.
 */
object AdbController {

    // Change this if adb.exe is not on your system PATH.
    private const val ADB_PATH = "adb"

    /** Runs an adb command and returns its combined stdout output. */
    private suspend fun runCommand(vararg args: String): String = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(ADB_PATH, *args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /** Same as runCommand, but targets a specific device with -s ip:port.
     *  Use this whenever more than one device could be connected at once,
     *  or the device isn't on the default port 5555 (e.g. wireless debugging's
     *  dynamic port). */
    private suspend fun runCommandFor(ip: String, port: Int, vararg args: String): String =
        runCommand("-s", "$ip:$port", *args)

    suspend fun connect(ip: String, port: Int = 5555): String =
        runCommand("connect", "$ip:$port")

    suspend fun disconnect(): String =
        runCommand("disconnect")

    suspend fun listDevices(): List<String> {
        val raw = runCommand("devices")
        return raw.lines()
            .drop(1) // first line is "List of devices attached"
            .filter { it.isNotBlank() }
    }

    suspend fun tap(x: Int, y: Int): String =
        runCommand("shell", "input", "tap", x.toString(), y.toString())

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): String =
        runCommand("shell", "input", "swipe", x1.toString(), y1.toString(), x2.toString(), y2.toString(), durationMs.toString())

    suspend fun typeText(text: String): String {
        // adb input text does not support spaces directly; %s replaces them.
        val escaped = text.replace(" ", "%s")
        return runCommand("shell", "input", "text", escaped)
    }

    // Common Android keycodes for convenience.
    object KeyCode {
        const val HOME = 3
        const val BACK = 4
        const val RECENTS = 187
        const val VOLUME_UP = 24
        const val VOLUME_DOWN = 25
        const val POWER = 26
        const val WAKEUP = 224
    }

    suspend fun pressKey(keyCode: Int): String =
        runCommand("shell", "input", "keyevent", keyCode.toString())

    suspend fun installApk(localPath: String): String =
        runCommand("install", "-r", localPath)

    suspend fun uninstallApp(packageName: String): String =
        runCommand("uninstall", packageName)

    suspend fun forceStop(packageName: String): String =
        runCommand("shell", "am", "force-stop", packageName)

    suspend fun listInstalledPackages(): List<String> {
        val raw = runCommand("shell", "pm", "list", "packages")
        return raw.lines()
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
    }

    suspend fun pushFile(localPath: String, remotePath: String): String =
        runCommand("push", localPath, remotePath)

    suspend fun pullFile(remotePath: String, localPath: String): String =
        runCommand("pull", remotePath, localPath)

    /** Runs any raw shell command, for the free-form console (Fase 8). */
    suspend fun runShell(command: String): String =
        runCommand("shell", command)

    suspend fun listDirectory(path: String): String {
        val cmd = "ls -p '$path'"
        println("DEBUG comando enviado: $cmd")
        return runCommand("shell", cmd)
    }

    suspend fun setWifiEnabled(enabled: Boolean): String =
        runCommand("shell", "svc", "wifi", if (enabled) "enable" else "disable")

    suspend fun setBluetoothEnabled(enabled: Boolean): String =
        runCommand("shell", "svc", "bluetooth", if (enabled) "enable" else "disable")

    suspend fun setAirplaneMode(enabled: Boolean): String {
        val value = if (enabled) "1" else "0"
        runCommand("shell", "settings", "put", "global", "airplane_mode_on", value)
        return runCommand("shell", "am", "broadcast", "-a", "android.intent.action.AIRPLANE_MODE", "--ez", "state", enabled.toString())
    }

    /**
     * Reads the phone's Android version.
     * Pass ip/port whenever more than one device might be connected, or the
     * device isn't on the default port 5555 — otherwise `adb shell` can target
     * the wrong device (or fail entirely) and this silently returns 0.
     */
    suspend fun getAndroidVersion(ip: String? = null, port: Int? = null): Int {
        val raw = if (ip != null && port != null) {
            runCommandFor(ip, port, "shell", "getprop", "ro.build.version.release")
        } else {
            runCommand("shell", "getprop", "ro.build.version.release")
        }
        // Handles values like "10", "12", "12L", "13" — takes the leading digits only.
        val digits = raw.takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: 0
    }

    suspend fun setBrightness(value: Int): String =
        runCommand("shell", "settings", "put", "system", "screen_brightness", value.coerceIn(0, 255).toString())

    suspend fun connectWithRetry(ip: String, port: Int = 5555, maxAttempts: Int = 4): Pair<Boolean, String> {
        var lastResult = ""
        var delayMs = 1000L
        repeat(maxAttempts) { attempt ->
            lastResult = connect(ip, port)
            val success = lastResult.contains("connected to") && !lastResult.contains("failed")
            if (success) return true to lastResult
            if (attempt < maxAttempts - 1) {
                delay(delayMs)
                delayMs *= 2 // exponential backoff: 1s, 2s, 4s...
            }
        }
        return false to lastResult
    }

    /**
     * Reads the phone's screen resolution via `wm size`, e.g. parses
     * "Physical size: 1080x2400" -> (1080, 2400). Falls back to a common
     * resolution if parsing fails, so the unlock swipe still has a chance
     * of working on an unknown device.
     */
    suspend fun getScreenSize(): Pair<Int, Int> {
        val raw = runCommand("shell", "wm", "size")
        val match = Regex("""(\d+)x(\d+)""").find(raw)
        return if (match != null) {
            val (w, h) = match.destructured
            w.toInt() to h.toInt()
        } else {
            1080 to 2400
        }
    }

    /**
     * Wakes and unlocks the screen by launching UnlockActivity from the
     * companion Android app (see companion-app module). This uses
     * `am start`, NOT input injection — `adb shell input keyevent/swipe`
     * gets blocked by Android with a SecurityException while the lock
     * screen is active (INJECT_EVENTS is denied in that state), but
     * launching an activity is allowed even when locked.
     *
     * Requires the companion app (com.controlremoto.companion) to be
     * installed on the phone. If it isn't, this will fail silently
     * (the intent just won't resolve) — no crash, but no unlock either.
     *
     * If the lock screen has no PIN/pattern/fingerprint, this dismisses it
     * automatically. If it does, it'll wake the screen and show the normal
     * unlock UI instead — it can't bypass an actual PIN, by design.
     */
    suspend fun wakeAndUnlock(): String =
        runCommand("shell", "am", "start", "-n", "com.controlremoto.companion/.UnlockActivity")

    /**
     * Scans a range of TCP ports on the given IP to find Android's wireless
     * debugging port, when it isn't known ahead of time (e.g. the phone
     * rebooted while away from home and its dynamic port can't be read from
     * the screen).
     *
     * How it works: wireless debugging's port is randomly chosen from the
     * ephemeral port range on each activation — but it's always somewhere in
     * a bounded range (observed values so far: 37859, 38955, 46551, 46811).
     * This opens many short-lived probe connections in parallel, keeps the
     * ones that accept a TCP connection, then confirms each candidate with
     * a real `adb connect` — a plain open port isn't proof by itself, only
     * ADB accepting the handshake is.
     *
     * `onProgress` reports 0f..1f so the UI can show a progress bar; this
     * can take 20-40 seconds depending on range size and network latency.
     */
    suspend fun discoverWirelessDebugPort(
        ip: String,
        portRangeStart: Int = 30000,
        portRangeEnd: Int = 65000,
        concurrency: Int = 300,
        connectTimeoutMs: Int = 250,
        onProgress: (Float) -> Unit = {}
    ): Int? = withContext(Dispatchers.IO) {
        val semaphore = Semaphore(concurrency)
        val totalPorts = portRangeEnd - portRangeStart + 1
        var scanned = 0

        val openPorts = coroutineScope {
            (portRangeStart..portRangeEnd).map { port ->
                async {
                    semaphore.withPermit {
                        val isOpen = try {
                            Socket().use { socket ->
                                socket.connect(InetSocketAddress(ip, port), connectTimeoutMs)
                                true
                            }
                        } catch (e: Exception) {
                            false
                        }
                        synchronized(this@AdbController) {
                            scanned++
                            onProgress(scanned.toFloat() / totalPorts)
                        }
                        if (isOpen) port else null
                    }
                }
            }.awaitAll().filterNotNull()
        }

        // A raw open port could be anything (another service on the phone).
        // Confirm with a real adb connect — only the actual adb endpoint
        // will respond with "connected to".
        for (candidatePort in openPorts) {
            val result = connect(ip, candidatePort)
            if (result.contains("connected to") && !result.contains("failed")) {
                return@withContext candidatePort
            } else {
                disconnect()
            }
        }
        null
    }
    suspend fun getCallLog(daysBack: Int = 7): String {
        val sinceMillis = System.currentTimeMillis() - daysBack.toLong() * 24 * 60 * 60 * 1000
        val cmd = "content query --uri content://call_log/calls --projection number:date:type:duration --where \"date\\>$sinceMillis\""
        return runCommand("shell", cmd)
    }
}