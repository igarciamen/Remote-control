# Remote Control — PC to Phone

Herramienta de **soporte y reparación remota de teléfonos Android**, al estilo TeamViewer pero especializada en Android: desde una app de escritorio en **Kotlin + Jetpack Compose for Desktop**, se controla un teléfono a distancia apoyándose en **ADB**, **scrcpy** y **Tailscale** — más una **app Android complementaria** (`companion-app`) que mantiene la conexión activa de forma autónoma en el teléfono, incluso tras reinicios.

![status](https://img.shields.io/badge/status-functional-brightgreen) ![platform](https://img.shields.io/badge/platform-Windows%20%2B%20Android-blue) ![language](https://img.shields.io/badge/kotlin-Compose%20Desktop-purple)

---

## 🏗️ Estructura del repositorio

Este repo contiene **dos proyectos distintos** dentro del mismo Gradle multi-módulo:

```
control-remoto-pc/
├── src/main/kotlin/          ← APP DE ESCRITORIO (corre en la PC, Windows)
└── companion-app/            ← APP ANDROID (se instala en el teléfono)
```

---

## 🖥️ App de escritorio

### Funcionalidades

| Panel | Descripción |
|---|---|
| **Devices** | Lista de dispositivos guardados (nombre, IP Tailscale, puerto). Permite editar el puerto manualmente, conectar, y **auto-descubrir el puerto de depuración inalámbrica** escaneando un rango de puertos y confirmando con un `adb connect` real. |
| **Status & Actions** | Estado de conexión en vivo, acciones rápidas (Home, Back, Recents, Disconnect), y **Wake & Unlock** — enciende la pantalla y la desbloquea de forma remota (requiere la app companion instalada en el teléfono). |
| **Files** | Envía archivos de la PC al teléfono y trae archivos del teléfono a la PC, con navegación de carpetas remotas. |
| **Apps** | Instala APKs, lista apps instaladas con buscador, fuerza el cierre o desinstala aplicaciones. |
| **System** | Controla WiFi y modo avión, ajusta el brillo. (Bluetooth deshabilitado por restricción de fábrica en teléfonos MIUI). |
| **Automation** | Constructor manual de scripts (tap, swipe, esperar, escribir texto, home) que se guardan y reproducen con un clic — útil para repetir el mismo diagnóstico o procedimiento en varios equipos. |

Además: visualización en vivo de la pantalla del teléfono (scrcpy embebido), pantalla de login con contraseña propia (hash SHA-256, nunca en texto plano), y reconexión automática al último dispositivo con reintentos y backoff exponencial.

---

## 📱 App companion (Android)

Instalada en el teléfono para automatizar tareas que ADB por sí solo no puede sostener en el tiempo (especialmente tras un reinicio del teléfono) — pensada para dejar un equipo "siempre disponible" para soporte remoto sin intervención manual repetida.

| Componente | Función |
|---|---|
| `MainActivity` | Pantalla de estado: Device Admin, exclusión de batería, permiso de ajustes seguros. |
| `CompanionDeviceAdminReceiver` | Protección anti-desinstalación accidental. |
| `BootReceiver` | Al reiniciar el teléfono, reactiva ADB (USB + inalámbrico) y la política de VPN siempre activa. |
| `TailscaleVpnPolicy` | Usa la API de **Device Owner** (`setAlwaysOnVpnPackage`) para que Android mismo mantenga Tailscale conectado, sin depender de abrir su interfaz. |
| `WatchdogScheduler` + `TailscaleWatchdogWorker` | Tarea periódica (cada 15 min) que reaplica la política de VPN y verifica conectividad — autocurativo frente a que MIUI resetee ajustes por su cuenta. |
| `PermissionWatcher` | Notifica (una sola vez por incidente) si el permiso `WRITE_SECURE_SETTINGS` se pierde. |
| `UnlockActivity` | Lanzada remotamente vía `adb shell am start`, usa `KeyguardManager.requestDismissKeyguard()` para desbloquear la pantalla — solo funciona si el teléfono no tiene PIN/patrón/huella configurados. |
| `TailscaleAccessibilityService` / `TailscaleLauncher` | Mecanismos experimentales/de respaldo para visibilidad si la política de VPN alguna vez falla. |

**Requisito clave:** la app companion necesita ser **Device Owner** del teléfono (confirmable con `dumpsys device_policy`) para poder fijar la política de VPN siempre activa — esto normalmente requiere configurarlo en un teléfono recién restablecido de fábrica, antes de añadir cualquier cuenta Google.

---

## 🧰 Requisitos previos

- **Windows 10/11**
- **JDK 17+**
- **Android Platform Tools (ADB)** en el `PATH`
- **[scrcpy](https://github.com/Genymobile/scrcpy)** en una ruta fija (por defecto `C:\scrcpy-win64-v4.1`)
- **[Tailscale](https://tailscale.com/download)** con sesión iniciada en la PC y en el teléfono, misma cuenta personal
- **Android Studio** para compilar ambos módulos

### En el teléfono

- Modo desarrollador + depuración USB activada
- Depuración inalámbrica activa (Android 11+) o `adb tcpip 5555` tras cada reinicio (Android 10)
- MIUI: "USB debugging (Security settings)" activado para `tap`/`swipe`/`typeText`
- Para la app companion: Device Owner configurado, y permiso concedido una vez por cable:
  ```
  adb shell pm grant com.controlremoto.companion android.permission.WRITE_SECURE_SETTINGS
  ```

---

## 🚀 Compilar y ejecutar

**App de escritorio:**
```
.\gradlew.bat run
```

**App companion (con el teléfono conectado por ADB):**
```
.\gradlew.bat :companion-app:installDebug
```

**Generar el instalador `.msi` de la app de escritorio:**
```
.\gradlew.bat packageMsi
```
El instalador queda en `build\compose\binaries\main\msi\`.

---

## ⚠️ Limitaciones conocidas

| Limitación | Motivo |
|---|---|
| Bluetooth no controlable vía ADB | MIUI no concede `BLUETOOTH_ADMIN` al shell de ADB |
| `Wake & Unlock` no bypassa un PIN/patrón real | Por diseño de seguridad de Android — solo dismiss de swipe-to-unlock sin credencial |
| La app companion necesita ser Device Owner | Requiere configuración en teléfono recién restablecido de fábrica |
| Tras un reinicio del teléfono sin depuración inalámbrica (Android 10) | Requiere reconectar el cable USB una vez y repetir `adb tcpip 5555` |

---

## 🔐 Seguridad

- Contraseña propia de la app de escritorio: hash SHA-256, nunca en texto plano
- Tailscale mantiene la conexión dentro de una red privada cifrada
- La IP Tailscale del teléfono nunca debe compartirse con terceros
- La app companion no intenta ni puede saltarse un PIN/patrón/huella real

---


