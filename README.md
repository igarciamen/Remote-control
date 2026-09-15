# Remote Control - PC to Phone

A remote support and repair tool for Android phones, similar to TeamViewer but built specifically for Android: a desktop application in Kotlin with Jetpack Compose for Desktop controls a phone remotely, relying on ADB, scrcpy, and Tailscale, plus a companion Android app that keeps the connection active on the phone on its own, even after reboots.

---

## Repository structure

This repo contains two separate projects inside the same multi-module Gradle setup:

```
control-remoto-pc/
├── src/main/kotlin/          (DESKTOP APP, runs on Windows)
└── companion-app/            (ANDROID APP, installed on the phone)
```

---

## Demo

https://github.com/user-attachments/assets/eb0822c8-9e22-45a9-b7ea-29e3379cc554


## Desktop app

### Features

| Panel | Description |
|---|---|
| **Devices** | List of saved devices (name, Tailscale IP, port). Lets you edit the port manually, connect, and auto discover the wireless debugging port by scanning a port range and confirming with a real `adb connect`. |
| **Status & Actions** | Live connection status, quick actions (Home, Back, Recents, Disconnect), and Wake & Unlock: turns on the screen and unlocks it remotely (requires the companion app installed on the phone). |
| **Files** | Sends files from the PC to the phone and pulls files from the phone to the PC, with remote folder browsing. |
| **Apps** | Installs APKs, lists installed apps with search, force stops or uninstalls applications. |
| **System** | Controls WiFi and airplane mode, adjusts brightness. (Bluetooth is disabled due to a factory restriction on MIUI phones.) |
| **Automation** | A manual script builder (tap, swipe, wait, type text, home) that saves and replays action sequences with one click, useful for repeating the same diagnostic or procedure across several devices. |

Also included: live screen mirroring (embedded scrcpy), a login screen with its own password (SHA-256 hash, never stored in plain text), and automatic reconnection to the last device with retries and exponential backoff.

---

## Companion app (Android)

Installed on the phone to automate tasks that ADB alone cannot sustain over time, especially after the phone reboots. Built so a device can stay available for remote support without repeated manual steps.

| Component | Function |
|---|---|
| `MainActivity` | Status screen: Device Admin, battery optimization exclusion, secure settings permission. |
| `CompanionDeviceAdminReceiver` | Protection against accidental uninstall. |
| `BootReceiver` | On reboot, re-enables ADB (USB and wireless) and the always-on VPN policy. |
| `TailscaleVpnPolicy` | Uses Android's Device Owner API (`setAlwaysOnVpnPackage`) so the OS itself keeps Tailscale connected, without relying on opening its interface. |
| `WatchdogScheduler` + `TailscaleWatchdogWorker` | A periodic task (every 15 minutes) that reapplies the VPN policy and checks connectivity, self-healing if MIUI resets settings on its own. |
| `PermissionWatcher` | Notifies (once per incident) if the `WRITE_SECURE_SETTINGS` permission is lost. |
| `UnlockActivity` | Launched remotely via `adb shell am start`, uses `KeyguardManager.requestDismissKeyguard()` to unlock the screen. Only works if the phone has no PIN, pattern, or fingerprint set. |
| `TailscaleAccessibilityService` / `TailscaleLauncher` | Experimental or fallback mechanisms for visibility if the VPN policy ever fails. |

**Key requirement:** the companion app needs to be the phone's Device Owner (verifiable with `dumpsys device_policy`) to set the always-on VPN policy. This normally requires setting it up on a phone that has just been reset to factory settings, before adding any Google account.

---

## Requirements

- Windows 10/11
- JDK 17+
- Android Platform Tools (ADB) on the `PATH`
- [scrcpy](https://github.com/Genymobile/scrcpy) in a fixed path (default `C:\scrcpy-win64-v4.1`)
- [Tailscale](https://tailscale.com/download) signed in on both the PC and the phone, same personal account
- Android Studio to build both modules

### On the phone

- Developer mode and USB debugging enabled
- Wireless debugging active (Android 11+) or `adb tcpip 5555` after each reboot (Android 10)
- MIUI: "USB debugging (Security settings)" enabled for `tap`/`swipe`/`typeText`
- For the companion app: Device Owner configured, and the permission granted once over cable:
  ```
  adb shell pm grant com.controlremoto.companion android.permission.WRITE_SECURE_SETTINGS
  ```

---

## Build and run

**Desktop app:**
```
.\gradlew.bat run
```

**Companion app (with the phone connected via ADB):**
```
.\gradlew.bat :companion-app:installDebug
```

**Generate the desktop app's `.msi` installer:**
```
.\gradlew.bat packageMsi
```
The installer ends up in `build\compose\binaries\main\msi\`.

---

## Known limitations

| Limitation | Reason |
|---|---|
| Bluetooth cannot be controlled via ADB | MIUI does not grant `BLUETOOTH_ADMIN` to the ADB shell |
| `Wake & Unlock` cannot bypass a real PIN/pattern | By Android's own security design, it only dismisses swipe-to-unlock without a credential |
| The companion app needs to be Device Owner | Requires setup on a phone freshly reset to factory settings |
| After a phone reboot without wireless debugging (Android 10) | Requires reconnecting the USB cable once and repeating `adb tcpip 5555` |

---

## Security

- The desktop app's own password is stored as a SHA-256 hash, never in plain text
- Tailscale keeps the connection inside an encrypted private network
- The phone's Tailscale IP should never be shared with third parties
- The companion app does not attempt, and cannot, bypass a real PIN, pattern, or fingerprint

