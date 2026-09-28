package com.eluqen.sensorlock;

/** Pure UI-state decisions; never starts recovery or changes sensor privacy. */
final class ConnectionDisplayState {
    private ConnectionDisplayState() {}

    static boolean isReconnecting(boolean backgroundCheckInFlight,
                                  boolean bridgeReady, boolean repairRequired) {
        return backgroundCheckInFlight && (!bridgeReady || repairRequired);
    }

    static boolean isVerified(boolean hasPermission, boolean bridgeReady,
                              boolean repairRequired) {
        return hasPermission && bridgeReady && !repairRequired;
    }

    /** Sensor state is not current/verified while repair is required, even if a stale bridge exists. */
    static boolean isSensorStateVerified(boolean hasPermission, boolean bridgeReady,
                                         boolean repairRequired, boolean pairingRevoked) {
        return hasPermission && bridgeReady && !repairRequired;
    }
}
