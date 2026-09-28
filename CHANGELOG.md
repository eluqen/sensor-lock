# Changelog

## 1.2.0

- Replaced the shared main action with two independent, direct Camera and Microphone controls.
- Made verified protection status explicit for either individual sensor, both, or neither.
- Kept Quick Settings sensor selection independent from home-screen controls and improved its guidance.
- Updated Help and About in English, Persian and French, with clearer pairing prompts.
- Improved background connection validation, repair feedback and restart recovery. Connection Status appears when action is needed.
- Added ACCESS_NETWORK_STATE for local Wi-Fi availability checks; normal sensor control remains local.

Official release APK: `releases/SensorLock-1.2.0.apk`

Version: `1.2.0` · Version code: `3`

SHA-256: `79680abf4b6ea68e466caeaca6071329b7acb4101d99dce6d595da6e316244a7`

## 1.1.0

- Added independent Camera and Microphone selection for the main protection action, while preserving explicit reporting of the actual combined sensor state.
- Added independent camera-and-microphone, camera-only, and microphone-only targets for the Quick Settings tile.
- Improved Quick Settings lifecycle reliability and prevention of repeated queued actions.
- Improved pairing-code submission, automatic discovery of the local pairing port, and recovery after app updates or device reboot.
- Improved connection-health updates and selected-sensor messages in Persian, English, and French.
- Updated About content.
- No new Android permissions. No remote analytics, tracking, or cloud backend.

Official release APK: `releases/SensorLock-1.1.0.apk`

Version: `1.1.0` · Version code: `2`

SHA-256: `c7aee394b03f88873345fb7ae957c907327d58dc3eccfffb5a0936315c7a1fc3`

## 1.0.0

Initial public release.

- System-level camera and microphone protection.
- Independent sensor state verification and Mixed-state reporting.
- Quick Settings control.
- On-device Wireless Debugging pairing and local shell bridge.
- Connection recovery after reboot.
- Persian, English, and French user interface.
