# Pairing and recovery guide

## First setup

1. Connect the device to Wi-Fi. Internet is not required.
2. Enable Developer options if necessary.
3. Open Sensor Lock → Help & pairing guide.
4. Tap Start pairing.
5. In Android Wireless debugging, enable Wireless debugging if needed.
6. Open Pair device with pairing code.
7. Keep the pairing-code screen open.
8. In the Sensor Lock notification, find the pairing port.
9. Enter the six-digit Android pairing code.
10. Wait for setup to finish.

## Already connected

If the local service is healthy and the pairing state is valid, Sensor Lock does not start another pairing flow.

## Pairing key removed

If the user removes Sensor Lock from Android's Wireless debugging paired devices, the app marks pairing stale when it can verify the debugging connection, exposes reconnect, and shows a reconnect notification.

A still-running local bridge may continue normal local sensor control until it stops.

## After reboot

Sensor Lock attempts to restore the local bridge automatically. If Android no longer accepts the saved pairing or recovery is unavailable, the user is asked to pair again.
