package com.runtimebridge.helper;

/**
 * Narrow Core-to-Motion-Helper control surface.
 *
 * Core remains authoritative for Motion Protection configuration and for the
 * privileged Lock/Reboot response. The helper persists only the minimum
 * Direct-Boot cache required to operate independently after reboot.
 */
interface IMotionHelperControl {
    int getProtocolVersion();

    int getOperationalState();

    int applyConfiguration(
        boolean enabled,
        int sensitivity
    );
}
