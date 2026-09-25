# Sensor Lock

Sensor Lock is an open-source Android privacy utility by ELUQEN for system-level camera and microphone control.

It can block or allow both sensors, verify their real state, show a Mixed state when camera and microphone differ, and expose the same control as a Quick Settings tile.

## Download

Signed public APK: releases/SensorLock-1.0.0.apk

- Package: com.eluqen.sensorlock
- Version: 1.0.0
- Version code: 1
- Minimum Android: Android 11 / API 30
- Target SDK: 35
- SHA-256: d4d0c5ea12caad6bd108927f59ddf91d7b18c182993a98c9ca18d0477bdc8131

ELUQEN signing certificate SHA-256:
AC:22:3F:F9:0D:1F:DA:E6:F5:5F:0D:7C:2C:C6:EE:34:7C:20:E1:61:EE:D4:2A:24:A0:38:84:A6:3D:CF:20:34

## Compatibility

Sensor Lock is designed for Android phones and tablets running Android 11 or later.

- Android 11 / API 30 minimum
- No root required
- English, Persian and French
- Quick Settings tile
- Compatibility is intentionally based on Android version only
- Manufacturer-specific Android behavior may affect Wireless debugging or recovery

## Why the first setup needs Wi-Fi

Android does not let a normal third-party app directly obtain the system privileges required for these privacy controls.

For first setup, Sensor Lock uses Android Wireless debugging to bootstrap a local privileged service on the same device.

Internet access is not required. Android Wireless debugging does require the phone or tablet to be connected to a Wi-Fi network during pairing.

After successful setup, normal protection commands run through a local service on the device and do not require Wi-Fi or internet while that service remains active.

## First-time setup

1. Connect the phone or tablet to Wi-Fi. Internet is not required.
2. If Developer options are hidden, enable them.
3. Common Android/Samsung path: Settings → About phone/tablet → Software information → Build number.
4. Tap Build number 7 times.
5. Open Sensor Lock → Help & pairing guide.
6. Tap Start pairing. Sensor Lock starts its pairing flow first and then opens Wireless debugging.
7. Enable Wireless debugging if needed.
8. Tap Pair device with pairing code and keep that screen open.
9. Open the Sensor Lock notification.
10. Tap Find pairing port.
11. Enter the 6-digit Android pairing code.
12. Wait for setup to finish. The pairing notification disappears automatically when the connection is ready.

## Normal use

Once connected:
- Protect camera & microphone blocks both sensors.
- Turn protection off makes both available again.
- Check protection verifies the real current state.
- The Quick Settings tile performs the same control without opening the app.
- Camera and microphone are tracked separately.

### Mixed state

Android or another system event can sometimes change only one sensor, for example during a call.

Sensor Lock does not hide this. If one sensor is blocked and the other is available, the app reports Mixed state. Using Protect attempts to block both again.

## Reboot and recovery

The local privileged service can stop after a reboot or if Android terminates it.

Sensor Lock listens for device boot, schedules a recovery attempt, tries to restore the local service using the existing pairing, and shows a reconnect notification if automatic recovery is not possible.

There is no permanent connected notification after successful setup.

## Permissions and transparency

| Permission / component | Purpose |
| --- | --- |
| WRITE_SECURE_SETTINGS | Granted locally during setup so Sensor Lock can manage temporary setup/debug settings needed for recovery. |
| INTERNET | Required by Android for the local ADB/TLS socket used during pairing or repair. Sensor Lock has no cloud API backend. |
| POST_NOTIFICATIONS | Pairing code entry and connection-loss/reconnect alerts. |
| RECEIVE_BOOT_COMPLETED | Attempts service recovery after reboot. |
| Quick Settings tile binding | Lets Android expose the Sensor Lock tile. |

## Privacy

Sensor Lock has no account system, ads, analytics, tracking SDK, cloud backend, personal-data upload, or remote telemetry endpoint.

Normal sensor control stays on the device. External links to ELUQEN, GitHub and Cafe Bazaar open only when the user taps them.

See PRIVACY.md.

## Technical overview

1. The app pairs with the device's own Android Debug Bridge daemon using Android Wireless debugging.
2. The initial bootstrap grants Sensor Lock the required secure-settings permission.
3. Sensor Lock launches ShellBridge from its own APK with Android shell privileges using app_process.
4. The normal app process communicates with that bridge through an Android abstract local socket protected by a random socket name and token.
5. The bridge changes sensor privacy through Android's cmd sensor_privacy interface.
6. Every operation reads the current privacy state back and verifies camera and microphone separately.
7. Pairing validity, bridge health and privacy state are tracked separately.

More detail: docs/ARCHITECTURE.md

## Building from source

Requirements:
- JDK compatible with the included Gradle setup
- Android SDK API 35
- Android build tools 35.x

Create local.properties with your Android SDK path.

For a locally signed release, copy release-signing.properties.example to release-signing.properties and point it to your own keystore.

Build command:
./gradlew assembleRelease --no-daemon --max-workers=1

The ELUQEN production signing key is intentionally not included in this repository.

## Project structure

- app/ — Sensor Lock application
- libadb/ — embedded LibADB Android source used for local pairing/bootstrap
- docs/ — architecture, pairing and release-signing notes
- releases/ — public signed APK and checksums
- LICENSES/ — license texts used by bundled components

## Open source

Sensor Lock application code is released under Apache License 2.0.

Bundled third-party components retain their own licenses. See THIRD_PARTY_NOTICES.md and LICENSES/.

## Links

- Website: https://eluqen.com
- Source: https://github.com/eluqen/sensor-lock
- Support: support@eluqen.com
- Cafe Bazaar package: com.eluqen.sensorlock

## Security

For security reports, see SECURITY.md.
