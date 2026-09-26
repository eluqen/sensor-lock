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
}
