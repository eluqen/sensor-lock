package com.eluqen.sensorlock;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static com.eluqen.sensorlock.ProtectionSummary.State;

public class ProtectionSummaryTest {
    @Test public void bothBlockedIsFullProtection() {
        assertEquals(State.FULL, ProtectionSummary.from(true, true));
    }
    @Test public void cameraOnlyIsNamedCorrectly() {
        assertEquals(State.CAMERA_ONLY, ProtectionSummary.from(true, false));
    }
    @Test public void microphoneOnlyIsNamedCorrectly() {
        assertEquals(State.MICROPHONE_ONLY, ProtectionSummary.from(false, true));
    }
    @Test public void bothAvailableIsOff() {
        assertEquals(State.OFF, ProtectionSummary.from(false, false));
    }
}
