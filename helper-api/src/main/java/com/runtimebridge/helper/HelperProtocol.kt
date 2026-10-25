package com.runtimebridge.helper

/**
 * Shared constants for Sentinel's private helper/Core IPC.
 *
 * No Core or helper package names are embedded here. Components are discovered
 * through neutral actions and are protected with same-signing-certificate
 * permissions plus caller verification at the service boundary.
 */
object HelperProtocol {

    const val PROTOCOL_VERSION = 1

    /*
     * Helper -> Core event sinks.
     */
    const val ACTION_BIND_MOTION_EVENTS =
        "com.runtimebridge.action.BIND_MOTION_EVENTS_V1"

    const val ACTION_BIND_SMS_EVENTS =
        "com.runtimebridge.action.BIND_SMS_EVENTS_V1"

    /*
     * Core -> Helper control services.
     */
    const val ACTION_BIND_MOTION_HELPER_CONTROL =
        "com.runtimebridge.action.BIND_MOTION_HELPER_CONTROL_V1"

    const val ACTION_BIND_SMS_HELPER_CONTROL =
        "com.runtimebridge.action.BIND_SMS_HELPER_CONTROL_V1"

    /*
     * Core -> Motion Helper system-UI relay.
     */
    const val ACTION_REQUEST_MOTION_SENSOR_PERMISSION =
        "com.runtimebridge.action.REQUEST_MOTION_SENSOR_PERMISSION_V1"

    /*
     * Core -> SMS Helper system-UI relay.
     */
    const val ACTION_REQUEST_SMS_PERMISSION =
        "com.runtimebridge.action.REQUEST_SMS_RECEIVE_PERMISSION_V1"

    /*
     * Separate signature permissions preserve least privilege between the
     * different IPC surfaces.
     */
    const val PERMISSION_MOTION_EVENTS =
        "com.runtimebridge.permission.MOTION_EVENTS_V1"

    const val PERMISSION_SMS_EVENTS =
        "com.runtimebridge.permission.SMS_EVENTS_V1"

    const val PERMISSION_MOTION_HELPER_CONTROL =
        "com.runtimebridge.permission.MOTION_HELPER_CONTROL_V1"

    const val PERMISSION_SMS_HELPER_CONTROL =
        "com.runtimebridge.permission.SMS_HELPER_CONTROL_V1"

    /*
     * Helper -> Core event results.
     */
    const val EVENT_RESULT_ACCEPTED = 0
    const val EVENT_RESULT_DISABLED = 1
    const val EVENT_RESULT_UNAVAILABLE = 2
    const val EVENT_RESULT_REJECTED = 3

    /*
     * Motion Helper operational state.
     */
    const val MOTION_STATE_DISABLED = 0
    const val MOTION_STATE_STARTING = 1
    const val MOTION_STATE_ACTIVE = 2
    const val MOTION_STATE_SENSOR_PERMISSION_REQUIRED = 3
    const val MOTION_STATE_ACCELEROMETER_UNAVAILABLE = 4
    const val MOTION_STATE_FAILED = 5

    /*
     * Core -> Motion Helper configuration results.
     */
    const val MOTION_CONFIG_RESULT_OK = 100
    const val MOTION_CONFIG_RESULT_INVALID_SENSITIVITY = 101
    const val MOTION_CONFIG_RESULT_FAILED = 102

    const val MOTION_MIN_SENSITIVITY = 0
    const val MOTION_MAX_SENSITIVITY = 100
}
