package com.runtimebridge.helper;

/**
 * Narrow helper-to-Core event surface for Motion Protection.
 *
 * The Motion Helper detects/classifies sensor activity only.
 * Core remains authoritative for enabled state and Lock/Reboot execution.
 */
interface IMotionEventSink {
    int getProtocolVersion();

    int reportMotionCandidate(
        long eventElapsedRealtimeMs
    );
}
