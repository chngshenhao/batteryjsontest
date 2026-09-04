# BatteryJSONTest

Android app that monitors device battery status, writes it as local JSON, and uploads it to a SOAP/ASMX endpoint every 1 minute via a foreground service.

## Requirements

- [Android Studio](https://developer.android.com/studio) (already detected on this machine)
- **JDK 17 or 21** (Temurin 21 recommended — Android Studio’s bundled Java 25 is not compatible with this project’s Gradle toolchain)
- Android SDK with **Platform 36** and build-tools (Gradle can download these on first build)
- An Android emulator or a physical device with USB debugging enabled

## Setup (Android Studio)

1. Open Android Studio → **File → Open** → select this folder:
   `C:\Users\chngsh\Desktop\batteryjsontest`
2. Wait for Gradle sync to finish.
3. Set the Gradle JDK (required if sync/build fails with an error like `IllegalArgumentException: 25.0.2`):
   - **File → Settings → Build, Execution, Deployment → Build Tools → Gradle**
   - **Gradle JDK** → choose **Temurin-21** (or download JDK 21 from the dropdown)
4. Create/start a device:
   - **Device Manager** → create a Virtual Device, **or**
   - Connect a phone with USB debugging enabled
5. Click **Run** (green play button) to install and launch the app.

On first launch (Android 13+), allow **notifications** when prompted so the foreground service can show its status.

## Usage

1. Tap **Start Service** — a persistent notification appears (“Battery monitoring active”).
2. The screen refreshes every 5 seconds with:
   - local `battery_status.json` (`deviceNumber`, `batteryPct`, `isCharging`, `timestamp`)
   - last SOAP upload status
3. Tap **Stop Service** to stop monitoring.

### Example battery JSON

```json
{
  "deviceNumber": "DEV-A1B2C3D4",
  "batteryPct": 80,
  "isCharging": true,
  "timestamp": "2026-08-03T17:00:00+08:00"
}
```

The SOAP endpoint is configured in `BatteryMonitorService.java` (`SOAP_URL`). Update it if your server address differs.

## Command-line build (optional)

Use JDK 21 (not Android Studio’s Java 25):

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot"
.\gradlew.bat assembleDebug
```

Debug APK output:

`app\build\outputs\apk\debug\app-debug.apk`

Unit tests:

```powershell
.\gradlew.bat test
```

## Project structure

| Path | Role |
|------|------|
| `app/src/main/java/.../MainActivity.java` | UI: start/stop service, show JSON + upload status |
| `app/src/main/java/.../BatteryMonitorService.java` | Foreground service: read battery, write JSON, SOAP upload |
| `app/src/main/java/.../DeviceIdUtil.java` | Persistent local device id (`DEV-XXXXXXXX`) |
| `app/src/main/java/.../DeviceIdContentProvider.java` | Exposes Device ID to same-device apps (e.g. return_QR) |
| `local.properties` | Local Android SDK path (do not commit secrets; already gitignored) |

## Sharing Device ID with return_QR

On the same PDA, `return_QR` reads this app’s Device ID via:

`content://com.example.batteryjsontest.deviceid/device`

Install **batteryjsontest** first (so the provider is present), then install/run **return_QR**. The QR app shows a QR whose payload is the plain text `DEV-...` string — the same value stored in `pda_prefs` / `battery_status.json`.

## Notes

- This is an **Android/Gradle** project. Do **not** run `npm install` — there is no Node/`package.json` setup.
- SOAP upload targets `http://bkn1atm03s100/services/automation_smartcabinet.asmx` by default; the device/emulator must reach that host on your network.

## License

MIT — see the `LICENSE` file if present.
