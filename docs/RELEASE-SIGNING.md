# Release signing

The official ELUQEN production keystore and passwords are intentionally not included in this repository.

The public APK can be verified against the certificate fingerprint documented in the root README.

For local builds:
1. Copy release-signing.properties.example to release-signing.properties.
2. Create or select your own keystore.
3. Fill in the local paths and credentials.
4. Build with Gradle.

Never commit release-signing.properties, JKS/keystore files, passwords or private keys.
