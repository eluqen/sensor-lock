package com.eluqen.sensorlock;

/** Pure classification. A network failure is never proof that ADB pairing was revoked. */
final class ConnectionPhase {
    enum Pairing { LAST_KNOWN, VERIFIED, REVOKED }
    enum State {
        WAITING_FOR_WIFI,
        WAITING_FOR_ADB,
        RECOVERING_BRIDGE,
        ACTIVE_PAIRING_UNCHECKED,
        ACTIVE_PAIRING_VERIFIED,
        ACTIVE_REPAIR_PAIRING,
        REPAIR_PAIRING,
        BRIDGE_UNAVAILABLE
    }

    private ConnectionPhase() {}

    static State classify(boolean wifiConnected, boolean adbEndpointObserved,
                          boolean bridgeFunctional, Pairing pairing,
                          boolean recoveryInFlight) {
        // The local shell bridge is useful without Wi-Fi. Always prefer its live state.
        if (bridgeFunctional) {
            if (pairing == Pairing.REVOKED) return State.ACTIVE_REPAIR_PAIRING;
            return pairing == Pairing.VERIFIED
                    ? State.ACTIVE_PAIRING_VERIFIED : State.ACTIVE_PAIRING_UNCHECKED;
        }
        if (!wifiConnected) return State.WAITING_FOR_WIFI;
        if (pairing == Pairing.REVOKED) return State.REPAIR_PAIRING;
        if (!adbEndpointObserved) return State.WAITING_FOR_ADB;
        return recoveryInFlight ? State.RECOVERING_BRIDGE : State.BRIDGE_UNAVAILABLE;
    }

    /** Two explicit authentication rejections establish revoked state; timeouts do not. */
    static final class AuthenticationEvidence {
        private int consecutiveRejections;
        private Pairing pairing = Pairing.LAST_KNOWN;

        Pairing current() { return pairing; }
        int consecutiveRejections() { return consecutiveRejections; }

        void authenticated() {
            pairing = Pairing.VERIFIED;
            consecutiveRejections = 0;
        }

        void authenticationRejected() {
            if (pairing == Pairing.REVOKED) return;
            if (++consecutiveRejections >= 2) pairing = Pairing.REVOKED;
        }

        void transportUnavailable() {
            // Retain last evidence, but never convert Wi-Fi/mDNS/TLS timeout to revocation.
            consecutiveRejections = 0;
        }

        void pairingCompleted() { authenticated(); }
    }
}
