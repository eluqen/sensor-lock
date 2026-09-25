# Security

## Reporting a security issue

Please report suspected vulnerabilities privately to support@eluqen.com before public disclosure.

Include the Sensor Lock version, Android version, device model, reproduction steps, relevant logs/screenshots, and whether the issue affects pairing, the local bridge, Quick Settings or sensor state.

## Security model

Sensor Lock uses Android Wireless debugging only to bootstrap or repair a local privileged service. Normal camera/microphone control is performed through that local service.

The ELUQEN release signing key is not stored in this repository.

## What is intentionally public

The application source, embedded open-source dependency source, architecture documentation, manifest permissions and public APK are intentionally visible for audit.

## Platform limitation

A local privileged service can be terminated by Android or lost after reboot. Sensor Lock attempts recovery and reports reconnection requirements instead of hiding the failure.
