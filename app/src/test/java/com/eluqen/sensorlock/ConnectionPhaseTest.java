package com.eluqen.sensorlock;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.eluqen.sensorlock.ConnectionPhase.*;

public class ConnectionPhaseTest {
    @Test public void rebootWithoutWifiIsNotRevoked() {
        assertEquals(State.WAITING_FOR_WIFI, classify(false, false, false,
                Pairing.LAST_KNOWN, true));
    }
    @Test public void newlyConnectedWifiWhileAdbWarmsIsNotRevoked() {
        assertEquals(State.WAITING_FOR_ADB, classify(true, false, false,
                Pairing.LAST_KNOWN, true));
    }
    @Test public void liveBridgeRemainsActiveWithoutWifi() {
        assertEquals(State.ACTIVE_PAIRING_VERIFIED, classify(false, false, true,
                Pairing.VERIFIED, false));
    }
    @Test public void bridgeCanStillOperateAfterConfirmedPairingRevocation() {
        assertEquals(State.ACTIVE_REPAIR_PAIRING, classify(true, true, true,
                Pairing.REVOKED, false));
        assertEquals(State.REPAIR_PAIRING, classify(true, true, false,
                Pairing.REVOKED, false));
    }
    @Test public void firstAuthenticationRejectionIsNotConclusive() {
        AuthenticationEvidence e = new AuthenticationEvidence();
        e.authenticationRejected();
        assertEquals(Pairing.LAST_KNOWN, e.current());
        assertEquals(1, e.consecutiveRejections());
        e.transportUnavailable();
        assertEquals(0, e.consecutiveRejections());
        assertEquals(Pairing.LAST_KNOWN, e.current());
    }
    @Test public void consecutiveExplicitRejectionsConfirmRevocation() {
        AuthenticationEvidence e = new AuthenticationEvidence();
        e.authenticationRejected();e.authenticationRejected();
        assertEquals(Pairing.REVOKED, e.current());
        e.transportUnavailable();
        assertEquals(Pairing.REVOKED, e.current());
        e.pairingCompleted();
        assertEquals(Pairing.VERIFIED, e.current());
    }
    @Test public void successfulAuthenticationResetsRejectionEvidence() {
        AuthenticationEvidence e = new AuthenticationEvidence();
        e.authenticationRejected(); e.authenticated(); e.authenticationRejected();
        assertEquals(Pairing.VERIFIED, e.current());
        assertEquals(1, e.consecutiveRejections());
    }
    @Test public void readyNetworkWithMissingBridgeDoesNotImplyRevoked() {
        assertEquals(State.RECOVERING_BRIDGE, classify(true, true, false,
                Pairing.LAST_KNOWN, true));
        assertEquals(State.BRIDGE_UNAVAILABLE, classify(true, true, false,
                Pairing.LAST_KNOWN, false));
    }
}
