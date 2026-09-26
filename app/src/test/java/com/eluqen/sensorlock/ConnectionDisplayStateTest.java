package com.eluqen.sensorlock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ConnectionDisplayStateTest {
    @Test public void routineHealthCheckOnReadyBridgeNeverShowsReconnecting() {
        assertFalse(ConnectionDisplayState.isReconnecting(true, true, false));
        assertTrue(ConnectionDisplayState.isVerified(true, true, false));
    }
    @Test public void genuinelyUnreadyBridgeShowsReconnectingDuringRecovery() {
        assertTrue(ConnectionDisplayState.isReconnecting(true, false, false));
        assertFalse(ConnectionDisplayState.isVerified(true, false, false));
    }
    @Test public void repairMarkedBridgeIsNotShownAsVerified() {
        assertTrue(ConnectionDisplayState.isReconnecting(true, true, true));
        assertFalse(ConnectionDisplayState.isVerified(true, true, true));
    }
    @Test public void completedFailedCheckMustNotLeaveReconnectingLatched() {
        assertFalse(ConnectionDisplayState.isReconnecting(false, false, true));
        assertFalse(ConnectionDisplayState.isVerified(true, false, true));
    }
    @Test public void lackOfPermissionNeverShowsVerified() {
        assertFalse(ConnectionDisplayState.isVerified(false, true, false));
    }
}
