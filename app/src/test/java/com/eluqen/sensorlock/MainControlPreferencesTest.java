package com.eluqen.sensorlock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MainControlPreferencesTest {
    @Test public void switchingLastCameraOffAutoSelectsMicrophone() {
        assertEquals(TilePreferences.MODE_MICROPHONE,
                MainControlPreferences.nextMode(TilePreferences.MODE_CAMERA,
                        TilePreferences.MODE_CAMERA, false));
    }

    @Test public void switchingLastMicrophoneOffAutoSelectsCamera() {
        assertEquals(TilePreferences.MODE_CAMERA,
                MainControlPreferences.nextMode(TilePreferences.MODE_MICROPHONE,
                        TilePreferences.MODE_MICROPHONE, false));
    }

    @Test public void bothCanBeSelectedAndIndividualDeselectionPreservesOther() {
        assertEquals(TilePreferences.MODE_BOTH,
                MainControlPreferences.nextMode(TilePreferences.MODE_CAMERA,
                        TilePreferences.MODE_MICROPHONE, true));
        assertEquals(TilePreferences.MODE_CAMERA,
                MainControlPreferences.nextMode(TilePreferences.MODE_BOTH,
                        TilePreferences.MODE_MICROPHONE, false));
        assertEquals(TilePreferences.MODE_MICROPHONE,
                MainControlPreferences.nextMode(TilePreferences.MODE_BOTH,
                        TilePreferences.MODE_CAMERA, false));
    }

    @Test public void allLegalTransitionsNeverProduceZeroOrInvalidMode() {
        for (int initial : new int[] {TilePreferences.MODE_BOTH,
                TilePreferences.MODE_CAMERA, TilePreferences.MODE_MICROPHONE, 0, 99}) {
            for (int sensor : new int[] {TilePreferences.MODE_CAMERA,
                    TilePreferences.MODE_MICROPHONE}) {
                for (boolean enabled : new boolean[] {true, false}) {
                    int next = MainControlPreferences.nextMode(initial, sensor, enabled);
                    assertTrue(TilePreferences.isValidMode(next));
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownSensorIsRejected() {
        MainControlPreferences.nextMode(TilePreferences.MODE_BOTH, 0, false);
    }
}
