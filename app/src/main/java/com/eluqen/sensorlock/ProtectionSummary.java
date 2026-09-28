package com.eluqen.sensorlock;

/** UI-only four-way summary. Does not change sensor state or recovery behaviour. */
final class ProtectionSummary {
    enum State { FULL, CAMERA_ONLY, MICROPHONE_ONLY, OFF }

    private ProtectionSummary() {}

    static State from(boolean cameraBlocked, boolean microphoneBlocked) {
        if (cameraBlocked && microphoneBlocked) return State.FULL;
        if (cameraBlocked) return State.CAMERA_ONLY;
        if (microphoneBlocked) return State.MICROPHONE_ONLY;
        return State.OFF;
    }
}
