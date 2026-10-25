package com.localruntime.core.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.UserManager
import android.util.Log
import com.localruntime.core.log.SecLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.localruntime.core.wipe.WipeGateway

/**
 * Detects the USB host-candidate condition proven during hardware testing
 * and routes the first candidate in each physical connection epoch through
 * the USB Protection policy before allowing it to reach WipeGateway.
 *
 * IMPORTANT:
 * - ordinary charger / charge-only behaviour remains classified exactly as
 *   in the hardware-tested build;
 * - BATTERY_PLUGGED_USB is the trigger condition;
 * - duplicate BATTERY_CHANGED broadcasts in the same connection epoch are
 *   suppressed;
 * - the detector re-arms only after external power is disconnected;
 * - UsbProtectionStore controls the persistent armed / disarmed policy;
 * - UsbMaintenanceStore can temporarily suppress an armed policy;
 * - WipeGateway remains the single implementation of destructive wipe logic.
 *
 * The detector itself remains active even when USB Protection is disabled or
 * Maintenance Mode is active. These states suppress only the destructive
 * policy action.
 *
 * The detector also publishes a non-destructive connection classification for
 * the Maintenance Mode USB tester. This published state does not participate
 * in the destructive policy decision.
 */
object UsbDataProbe {

    private const val TAG =
        "UsbDataProbe"

    private const val USB_STATE_ACTION =
        "android.hardware.usb.action.USB_STATE"

    /**
     * Non-destructive USB connection state exposed to the application UI.
     *
     * DISCONNECTED:
     *     No wired USB connection is currently detected.
     *
     * CONNECTED_PENDING:
     *     External power or a wired connection has appeared, but the detector
     *     does not yet have enough information to classify it safely.
     *
     * CHARGE_ONLY:
     *     Wired external power is present without a USB data-host candidate.
     *
     * DATA_CAPABLE:
     *     The same BATTERY_PLUGGED_USB condition used by the proven USB
     *     Protection trigger has been observed during this connection epoch.
     *
     * DATA_CAPABLE remains latched until physical external power disconnects,
     * matching the destructive detector's existing connection-epoch logic.
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTED_PENDING,
        CHARGE_ONLY,
        DATA_CAPABLE,
    }

    private val _connectionState =
        MutableStateFlow(
            ConnectionState.DISCONNECTED,
        )

    val connectionState: StateFlow<ConnectionState> =
        _connectionState.asStateFlow()

    private val registered =
        AtomicBoolean(false)

    /*
     * One physical external-power connection epoch.
     *
     * Once BATTERY_PLUGGED_USB has produced a candidate, later battery
     * reclassification such as USB -> AC must not re-arm the detector.
     *
     * Reset only after external power is disconnected.
     */
    private val usbCandidateActive =
        AtomicBoolean(false)

    /*
     * Strict Mode wired-connection candidate.
     *
     * This is separate from usbCandidateActive because a charge-only cable is
     * destructive only while Strict USB Mode remains armed.
     */
    private val strictWiredCandidateActive =
        AtomicBoolean(false)

    private val receiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                Log.i(
                    TAG,
                    "Received action=${intent.action}",
                )

                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED -> {
                        /*
                         * Power has appeared, but do not immediately call it
                         * charge-only. Wait for BATTERY_CHANGED to classify the
                         * connection.
                         *
                         * If a data-capable candidate is already latched, never
                         * downgrade the visible state.
                         */
                        if (!usbCandidateActive.get()) {
                            updateConnectionState(
                                ConnectionState.CONNECTED_PENDING,
                            )
                        }
                    }

                    Intent.ACTION_POWER_DISCONNECTED -> {
                        /*
                         * The authoritative candidate latch is still reset by
                         * BATTERY_CHANGED plugged=0 below. This UI state update
                         * simply gives the tester immediate disconnect feedback.
                         */
                        updateConnectionState(
                            ConnectionState.DISCONNECTED,
                        )
                    }
                }

                if (
                    intent.action ==
                    USB_STATE_ACTION
                ) {
                    val connected =
                        intent.getBooleanExtra(
                            "connected",
                            false,
                        )

                    /*
                     * Standard USB Protection deliberately ignores this signal
                     * and continues using the hardware-proven
                     * BATTERY_PLUGGED_USB classifier.
                     *
                     * Strict Mode is intentionally broader. A live Android USB
                     * connection is sufficient to qualify.
                     */
                    if (
                        connected &&
                        isStrictModeArmed(
                            context,
                        )
                    ) {
                        if (
                            strictWiredCandidateActive.compareAndSet(
                                false,
                                true,
                            )
                        ) {
                            handleStrictWiredCandidate(
                                context = context,
                                reason =
                                    "STRICT_USB_STATE_CONNECTED",
                            )
                        } else {
                            Log.i(
                                TAG,
                                "Strict USB candidate already active; " +
                                    "suppressing duplicate USB_STATE",
                            )
                        }
                    }
                }

                if (
                    intent.action ==
                    Intent.ACTION_BATTERY_CHANGED
                ) {
                    val plugged =
                        intent.getIntExtra(
                            BatteryManager.EXTRA_PLUGGED,
                            -1,
                        )

                    Log.i(
                        TAG,
                        "Battery plugged classification=$plugged",
                    )

                    val wiredPowerPresent =
                        (
                            plugged > 0 &&
                                plugged !=
                                BatteryManager.BATTERY_PLUGGED_WIRELESS
                            )

                    if (
                        plugged ==
                        BatteryManager.BATTERY_PLUGGED_USB
                    ) {
                        /*
                         * Preserve the hardware-proven normal USB Protection
                         * classifier exactly as before.
                         */
                        updateConnectionState(
                            ConnectionState.DATA_CAPABLE,
                        )

                        if (
                            usbCandidateActive.compareAndSet(
                                false,
                                true,
                            )
                        ) {
                            handleUsbCandidate(
                                context = context,
                                reason =
                                    "BATTERY_PLUGGED_USB",
                            )
                        } else {
                            Log.i(
                                TAG,
                                "USB host candidate already active; " +
                                    "suppressing duplicate",
                            )
                        }
                    } else if (plugged == 0) {
                        updateConnectionState(
                            ConnectionState.DISCONNECTED,
                        )

                        if (
                            usbCandidateActive.compareAndSet(
                                true,
                                false,
                            )
                        ) {
                            Log.i(
                                TAG,
                                "USB host candidate latch reset: " +
                                    "external power disconnected",
                            )
                        }

                        if (
                            strictWiredCandidateActive.compareAndSet(
                                true,
                                false,
                            )
                        ) {
                            Log.i(
                                TAG,
                                "Strict USB candidate latch reset: " +
                                    "external power disconnected",
                            )
                        }
                    } else if (usbCandidateActive.get()) {
                        /*
                         * Preserve an already-proven normal data-host
                         * classification across Android power-source
                         * reclassification.
                         */
                        updateConnectionState(
                            ConnectionState.DATA_CAPABLE,
                        )
                    } else {
                        when (plugged) {
                            BatteryManager.BATTERY_PLUGGED_AC -> {
                                updateConnectionState(
                                    ConnectionState.CHARGE_ONLY,
                                )
                            }

                            BatteryManager.BATTERY_PLUGGED_WIRELESS -> {
                                /*
                                 * Wireless charging is explicitly permitted by
                                 * Strict USB Mode.
                                 */
                                updateConnectionState(
                                    ConnectionState.DISCONNECTED,
                                )
                            }

                            else -> {
                                updateConnectionState(
                                    ConnectionState.CONNECTED_PENDING,
                                )
                            }
                        }

                        /*
                         * Strict Mode treats every externally powered wired
                         * connection as hostile, regardless of whether Android
                         * exposes it as AC, USB, dock, or another non-wireless
                         * powered state.
                         */
                        if (
                            wiredPowerPresent &&
                            isStrictModeArmed(
                                context,
                            )
                        ) {
                            if (
                                strictWiredCandidateActive.compareAndSet(
                                    false,
                                    true,
                                )
                            ) {
                                handleStrictWiredCandidate(
                                    context = context,
                                    reason =
                                        "STRICT_WIRED_POWER_$plugged",
                                )
                            } else {
                                Log.i(
                                    TAG,
                                    "Strict USB candidate already active; " +
                                        "suppressing duplicate power event",
                                )
                            }
                        }
                    }
                }

                val extras =
                    intent.extras

                if (extras == null) {
                    SecLog.v(
                        TAG,
                        "No extras",
                    )

                    return
                }

                for (
                    key in
                    extras.keySet().sorted()
                ) {
                    val value =
                        try {
                            extras.get(key)
                        } catch (
                            throwable: Throwable
                        ) {
                            "<error reading extra: " +
                                "${throwable.javaClass.simpleName}>"
                        }

                    SecLog.v(
                        TAG,
                        "extra[$key]=" +
                            "${value?.javaClass?.name}: $value",
                    )
                }
            }
        }

    /**
     * Update the UI-facing USB tester classification.
     *
     * This function deliberately has no policy side effects.
     */
    private fun updateConnectionState(
        newState: ConnectionState,
    ) {
        if (_connectionState.value == newState) {
            return
        }

        Log.i(
            TAG,
            "USB tester state " +
                "${_connectionState.value} -> $newState",
        )

        _connectionState.value =
            newState
    }

    /**
     * Apply the persistent USB Protection policy and temporary Maintenance
     * Mode policy to a qualifying USB data-host candidate.
     *
     * Both policy lookups are Direct-Boot safe.
     *
     * Effective destructive policy:
     *
     *     protection.enabled && !maintenance.active
     */
    private fun handleUsbCandidate(
        context: Context,
        reason: String,
    ) {
        val appContext =
            context.applicationContext

        val protectionState =
            UsbProtectionStore(
                appContext,
            ).load()

        if (!protectionState.enabled) {
            Log.w(
                TAG,
                "USB_DATA_HOST_WIPE_SUPPRESSED " +
                    "reason=PROTECTION_DISABLED " +
                    "candidateReason=$reason",
            )

            return
        }

        val maintenanceState =
            UsbMaintenanceStore(
                appContext,
            ).load()

        if (maintenanceState.active) {
            Log.w(
                TAG,
                "USB_DATA_HOST_WIPE_SUPPRESSED " +
                    "reason=MAINTENANCE_ACTIVE " +
                    "candidateReason=$reason " +
                    "remainingMs=" +
                    "${maintenanceState.remainingMs}",
            )

            return
        }

        triggerWipe(
            context = appContext,
            reason = reason,
        )
    }

    /**
     * True only when persistent USB Protection and Strict USB Mode are both
     * armed in Direct-Boot-safe storage.
     */
    private fun isStrictModeArmed(
        context: Context,
    ): Boolean {
        val state =
            UsbProtectionStore(
                context.applicationContext,
            ).load()

        return (
            state.enabled &&
                state.strictMode
            )
    }

    /**
     * Apply Strict USB Mode policy to a wired-connection candidate.
     *
     * Strict Mode remains subordinate to normal USB Protection and Maintenance
     * Mode. All three authoritative states are re-read immediately before any
     * destructive action.
     */
    private fun handleStrictWiredCandidate(
        context: Context,
        reason: String,
    ) {
        val appContext =
            context.applicationContext

        val protectionState =
            UsbProtectionStore(
                appContext,
            ).load()

        if (!protectionState.enabled) {
            Log.w(
                TAG,
                "USB_STRICT_WIPE_SUPPRESSED " +
                    "reason=PROTECTION_DISABLED " +
                    "candidateReason=$reason",
            )

            return
        }

        if (!protectionState.strictMode) {
            Log.w(
                TAG,
                "USB_STRICT_WIPE_SUPPRESSED " +
                    "reason=STRICT_MODE_DISABLED " +
                    "candidateReason=$reason",
            )

            return
        }

        val maintenanceState =
            UsbMaintenanceStore(
                appContext,
            ).load()

        if (maintenanceState.active) {
            Log.w(
                TAG,
                "USB_STRICT_WIPE_SUPPRESSED " +
                    "reason=MAINTENANCE_ACTIVE " +
                    "candidateReason=$reason " +
                    "remainingMs=${maintenanceState.remainingMs}",
            )

            return
        }

        triggerWipe(
            context = appContext,
            reason = reason,
            logPrefix = "USB_STRICT",
        )
    }

    /**
     * Route an armed USB policy event to the single wipe implementation.
     */
    private fun triggerWipe(
        context: Context,
        reason: String,
        logPrefix: String = "USB_DATA_HOST",
    ) {
        val userManager =
            context.getSystemService(
                UserManager::class.java,
            )

        val userUnlocked =
            userManager?.isUserUnlocked ?: false

        val state =
            if (userUnlocked) {
                "USER_UNLOCKED"
            } else {
                "DIRECT_BOOT_BFU"
            }

        Log.w(
            TAG,
            "${logPrefix}_WIPE_TRIGGER " +
                "state=$state " +
                "reason=$reason",
        )

        val wipeGateway =
            WipeGateway(
                context.applicationContext,
            )

        val result =
            wipeGateway.wipeNow()

        /*
         * On a successful wipe the process/device will normally disappear
         * before this result can be meaningfully observed. This log is mainly
         * useful for NotAdmin / Error diagnostics.
         */
        Log.w(
            TAG,
            "${logPrefix}_WIPE_RESULT " +
                "result=$result",
        )
    }

    /**
     * Re-evaluate the currently latched USB candidate after an external policy
     * transition such as Maintenance Mode expiry.
     *
     * The candidate latch itself is not modified here.
     *
     * If there is no active candidate, no USB policy action is taken.
     *
     * If there is an active candidate, route it back through
     * handleUsbCandidate(). That method re-reads both authoritative
     * Direct-Boot-safe policy stores before any destructive action can occur.
     *
     * This means an expiry recheck cannot bypass:
     *
     * - persistent USB Protection being disabled; or
     * - Maintenance Mode still being active.
     */
    fun reevaluateActiveCandidate(
        context: Context,
        reason: String,
    ): Boolean {
        val dataHostActive =
            usbCandidateActive.get()

        val strictWiredActive =
            strictWiredCandidateActive.get()

        Log.w(
            TAG,
            "USB candidate policy recheck " +
                "reason=$reason " +
                "dataHostActive=$dataHostActive " +
                "strictWiredActive=$strictWiredActive",
        )

        if (dataHostActive) {
            handleUsbCandidate(
                context = context.applicationContext,
                reason = reason,
            )

            return true
        }

        if (strictWiredActive) {
            handleStrictWiredCandidate(
                context = context.applicationContext,
                reason = reason,
            )

            return true
        }

        return false
    }

    /**
     * Enforce Strict USB Mode against a wired connection that already exists
     * when the process starts, including LOCKED_BOOT_COMPLETED / BFU startup.
     *
     * ACTION_BATTERY_CHANGED is sticky and available from Device Protected
     * Storage context on the tested GrapheneOS device.
     *
     * Wireless charging is explicitly excluded.
     */
    private fun enforceStartupStrictMode(
        context: Context,
    ) {
        val appContext =
            context.applicationContext

        if (
            !isStrictModeArmed(
                appContext,
            )
        ) {
            return
        }

        val batteryIntent =
            try {
                appContext.registerReceiver(
                    null,
                    IntentFilter(
                        Intent.ACTION_BATTERY_CHANGED,
                    ),
                )
            } catch (
                throwable: Throwable,
            ) {
                Log.e(
                    TAG,
                    "Strict startup battery query failed",
                    throwable,
                )

                null
            }

        if (batteryIntent == null) {
            Log.w(
                TAG,
                "Strict startup battery state unavailable",
            )

            return
        }

        val plugged =
            batteryIntent.getIntExtra(
                BatteryManager.EXTRA_PLUGGED,
                -1,
            )

        val wiredPowerPresent =
            (
                plugged > 0 &&
                    plugged !=
                    BatteryManager.BATTERY_PLUGGED_WIRELESS
                )

        Log.i(
            TAG,
            "Strict startup check " +
                "batteryPlugged=$plugged " +
                "wiredPowerPresent=$wiredPowerPresent",
        )

        if (!wiredPowerPresent) {
            return
        }

        when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> {
                updateConnectionState(
                    ConnectionState.DATA_CAPABLE,
                )
            }

            BatteryManager.BATTERY_PLUGGED_AC -> {
                updateConnectionState(
                    ConnectionState.CHARGE_ONLY,
                )
            }

            else -> {
                updateConnectionState(
                    ConnectionState.CONNECTED_PENDING,
                )
            }
        }

        if (
            strictWiredCandidateActive.compareAndSet(
                false,
                true,
            )
        ) {
            handleStrictWiredCandidate(
                context = appContext,
                reason =
                    "STRICT_STARTUP_WIRED_POWER_$plugged",
            )
        } else {
            Log.i(
                TAG,
                "Strict startup candidate already active; " +
                    "suppressing duplicate",
            )
        }
    }

    fun register(
        context: Context,
    ) {
        if (
            !registered.compareAndSet(
                false,
                true,
            )
        ) {
            Log.i(
                TAG,
                "USB probe already registered; " +
                    "skipping duplicate registration",
            )

            return
        }

        val filter =
            IntentFilter().apply {
                addAction(
                    USB_STATE_ACTION,
                )

                addAction(
                    "android.hardware.usb.action.USB_PORT_CHANGED",
                )

                addAction(
                    Intent.ACTION_POWER_CONNECTED,
                )

                addAction(
                    Intent.ACTION_POWER_DISCONNECTED,
                )

                addAction(
                    Intent.ACTION_BATTERY_CHANGED,
                )
            }

        try {
            context.applicationContext.registerReceiver(
                receiver,
                filter,
            )

            Log.i(
                TAG,
                "USB probe registered",
            )

            enforceStartupStrictMode(
                context.applicationContext,
            )
        } catch (
            throwable: Throwable
        ) {
            registered.set(
                false,
            )

            Log.e(
                TAG,
                "USB probe registration failed",
                throwable,
            )

            throw throwable
        }
    }
}
