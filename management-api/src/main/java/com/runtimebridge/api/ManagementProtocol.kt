package com.runtimebridge.api

/**
 * Shared constants for the private Core/Controller management protocol.
 */
object ManagementProtocol {

    const val PROTOCOL_VERSION = 17

    const val ACTION_BIND =
        "com.runtimebridge.action.BIND_V1"

    const val ACTION_SETUP_ADMIN =
        "com.runtimebridge.action.SETUP_ADMIN_V1"

    const val ACTION_REQUEST_SMS_PERMISSION =
        "com.runtimebridge.action.REQUEST_SMS_PERMISSION_V1"

    const val ACTION_REQUEST_SENSOR_PERMISSION =
        "com.runtimebridge.action.REQUEST_SENSOR_PERMISSION_V1"

    const val PERMISSION_CONTROL =
        "com.runtimebridge.permission.CONTROL_V1"

    const val EXTRA_SETUP_HANDOFF_TOKEN =
        "com.runtimebridge.extra.SETUP_HANDOFF_TOKEN_V1"

    const val SETUP_HANDOFF_DURATION_MS =
        60_000L

    const val PIN_MIN_LENGTH = 6
    const val PIN_MAX_LENGTH = 20

    const val PIN_STATE_NOT_ENROLLED = 0
    const val PIN_STATE_READY = 1
    const val PIN_STATE_LOCKED = 2
    const val PIN_STATE_TERMINAL = 3

    const val RESULT_OK = 0
    const val RESULT_INVALID_PIN = 1
    const val RESULT_LOCKED = 2
    const val RESULT_TERMINAL = 3
    const val RESULT_NOT_ENROLLED = 4
    const val RESULT_REPEAT_FAILURE = 5
    const val RESULT_INVALID_FORMAT = 6
    const val RESULT_ALREADY_ENROLLED = 7

    const val SESSION_DURATION_MS = 120_000L

    /*
     * Core device-policy authority modes exposed to the trusted Controller.
     */
    const val AUTHORITY_NONE = 0
    const val AUTHORITY_DEVICE_ADMIN = 1
    const val AUTHORITY_DEVICE_OWNER = 2

    /*
     * Beta lifecycle state exposed by the authoritative Core.
     *
     * These values describe why destructive actions are suppressed. The
     * Controller must treat Core as authoritative rather than deriving Beta
     * lifecycle state from its own BuildConfig.
     */
    const val BETA_STATE_ACTIVE = 0
    const val BETA_STATE_CURRENT_BUILD_EXPIRED = 1
    const val BETA_STATE_REARM_REQUIRED = 2
    const val BETA_STATE_ERROR = 3

    /*
     * Beta lifecycle management results.
     */
    const val BETA_RESULT_OK = 800
    const val BETA_RESULT_CURRENT_BUILD_EXPIRED = 801
    const val BETA_RESULT_REARM_NOT_REQUIRED = 802
    const val BETA_RESULT_VERSION_NOT_NEWER = 803
    const val BETA_RESULT_STATE_ERROR = 804
    const val BETA_RESULT_FAILED = 805

    /*
     * Authenticated Standard-mode Device Admin removal results.
     */
    const val ADMIN_REMOVAL_RESULT_REQUESTED = 500
    const val ADMIN_REMOVAL_RESULT_DEVICE_OWNER = 501
    const val ADMIN_REMOVAL_RESULT_ADMIN_INACTIVE = 502
    const val ADMIN_REMOVAL_RESULT_AUTHORIZATION_FAILED = 503
    const val ADMIN_REMOVAL_RESULT_FAILED = 504

    /*
     * Stable USB connection values exposed over IPC.
     */
    const val USB_CONNECTION_DISCONNECTED = 0
    const val USB_CONNECTION_PENDING = 1
    const val USB_CONNECTION_CHARGE_ONLY = 2
    const val USB_CONNECTION_DATA_CAPABLE = 3

    /*
     * USB management operation results.
     */
    const val USB_RESULT_OK = 100
    const val USB_RESULT_ADMIN_INACTIVE = 101
    const val USB_RESULT_PROTECTION_DISABLED = 102
    const val USB_RESULT_WIRED_CONNECTION = 103
    const val USB_RESULT_USER_LOCKED = 104
    const val USB_RESULT_EXACT_ALARM_UNAVAILABLE = 105
    const val USB_RESULT_MAINTENANCE_INACTIVE = 106
    const val USB_RESULT_EXTENSION_LIMIT = 107
    const val USB_RESULT_FAILED = 108

    /*
     * Debugging tamper-protection management results.
     */
    const val TAMPER_RESULT_OK = 600
    const val TAMPER_RESULT_FAILED = 601
    const val TAMPER_RESULT_USB_DEBUGGING_ACTIVE = 602
    const val TAMPER_RESULT_WIRELESS_DEBUGGING_ACTIVE = 603

    /*
     * Triggered-wipe behaviour management results.
     */
    const val WIPE_BEHAVIOUR_RESULT_OK = 700
    const val WIPE_BEHAVIOUR_RESULT_DEVICE_OWNER_REQUIRED = 701
    const val WIPE_BEHAVIOUR_RESULT_FAILED = 702

    /*
     * Stable Dead Man state values exposed over IPC.
     */
    const val DEADMAN_STATE_DISABLED = 0
    const val DEADMAN_STATE_INVALID = 1
    const val DEADMAN_STATE_EXPIRED = 2
    const val DEADMAN_STATE_ARMED = 3

    /*
     * Dead Man management operation results.
     */
    const val DEADMAN_RESULT_OK = 200
    const val DEADMAN_RESULT_ADMIN_INACTIVE = 201
    const val DEADMAN_RESULT_INVALID_STATE = 202
    const val DEADMAN_RESULT_EXPIRED = 203
    const val DEADMAN_RESULT_DISABLED = 204
    const val DEADMAN_RESULT_INVALID_INTERVAL = 205
    const val DEADMAN_RESULT_FAILED = 206

    /*
     * Stable Motion Protection action values exposed over IPC.
     */
    const val MOTION_ACTION_LOCK = 0
    const val MOTION_ACTION_REBOOT = 1

    /*
     * Effective Motion Protection runtime state.
     */
    const val MOTION_OPERATIONAL_DISABLED = 0
    const val MOTION_OPERATIONAL_STARTING = 1
    const val MOTION_OPERATIONAL_ACTIVE = 2
    const val MOTION_OPERATIONAL_SENSOR_PERMISSION_REQUIRED = 3
    const val MOTION_OPERATIONAL_ACCELEROMETER_UNAVAILABLE = 4
    const val MOTION_OPERATIONAL_FAILED = 5

    /*
     * Motion Protection management operation results.
     */
    const val MOTION_RESULT_OK = 300
    const val MOTION_RESULT_ADMIN_INACTIVE = 301
    const val MOTION_RESULT_INVALID_SENSITIVITY = 302
    const val MOTION_RESULT_INVALID_ACTION = 303
    const val MOTION_RESULT_FAILED = 304
    const val MOTION_RESULT_REBOOT_REQUIRES_DEVICE_OWNER = 305

    /*
     * SMS Wipe configuration limits.
     *
     * Sender values are stored in canonical E.164 form.
     *
     * The trigger secret minimum is deliberately twelve characters because SMS
     * Wipe is a remotely reachable destructive action.
     */
    const val SMS_SENDER_MAX_LENGTH = 20
    const val SMS_KEYWORD_MIN_LENGTH = 12
    const val SMS_KEYWORD_MAX_LENGTH = 50

    /*
     * SMS Wipe management operation results.
     */
    const val SMS_RESULT_OK = 400
    const val SMS_RESULT_ADMIN_INACTIVE = 401
    const val SMS_RESULT_PERMISSION_MISSING = 402
    const val SMS_RESULT_SENDER_MISSING = 403
    const val SMS_RESULT_KEYWORD_INVALID = 404
    const val SMS_RESULT_INVALID_SENDER = 405
    const val SMS_RESULT_INVALID_KEYWORD = 406
    const val SMS_RESULT_DEVICE_OWNER_REQUIRED = 407
    const val SMS_RESULT_FAILED = 408

    /*
     * Non-destructive SMS simulation results.
     */
    const val SMS_SIMULATION_MATCH = 420
    const val SMS_SIMULATION_NO_MATCH = 421
    const val SMS_SIMULATION_DISABLED = 422
    const val SMS_SIMULATION_ADMIN_INACTIVE = 423
    const val SMS_SIMULATION_PERMISSION_MISSING = 424
    const val SMS_SIMULATION_SENDER_MISSING = 425
    const val SMS_SIMULATION_KEYWORD_INVALID = 426
}
