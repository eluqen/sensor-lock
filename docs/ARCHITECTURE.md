# Sensor Lock architecture

## Components

The Android app process provides the UI, language handling, Quick Settings tile, pairing workflow, cached state, connection health monitoring and boot recovery scheduling.

PairingReceiver and AdbConnectionManager use the embedded LibADB source to pair with the same device through Android Wireless debugging.

## Local privileged bridge

ShellBridge is launched from the installed Sensor Lock APK using app_process under Android's shell identity.

The normal app communicates with the bridge using an Android abstract local socket.

A random socket name and token are generated and stored privately by the app. Commands are rejected when the token does not match.

## Sensor control

The bridge executes Android sensor privacy commands for camera and microphone.

After each change, Sensor Lock reads the real system state back and verifies each sensor independently.

This supports three user-visible states: both blocked, both available, or Mixed.

## State model

Sensor Lock intentionally treats these as different states:
1. Android version support
2. secure-settings grant
3. pairing credential validity
4. local bridge health
5. camera privacy state
6. microphone privacy state
7. Quick Settings tile presence

A valid permission does not automatically mean the pairing key is still valid, and a revoked pairing key does not automatically mean a still-running local bridge has stopped.

## Recovery

When the bridge is missing, Sensor Lock attempts recovery using the existing pairing.

After boot, BootReceiver schedules RecoveryJobService after a short delay so Android has time to finish startup.

If recovery fails, Sensor Lock records a repair-required state and shows a reconnect notification.

## Network behavior

Normal local-bridge commands do not require an internet service.

Wireless debugging requires Wi-Fi during initial pairing or repair because that is an Android platform requirement.

Sensor Lock performs no automatic online version check. The Cafe Bazaar update button only opens the Bazaar update UI when the user taps it.
