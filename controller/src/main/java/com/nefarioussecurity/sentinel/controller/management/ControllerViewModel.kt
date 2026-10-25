package com.nefarioussecurity.sentinel.controller.management

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.runtimebridge.api.ICoreManagement
import com.runtimebridge.api.ManagementProtocol

enum class CoreConnectionState {
    IDLE,
    SEARCHING,
    CONNECTING,
    READY,
    CORE_NOT_FOUND,
    SIGNATURE_FAILED,
    PROTOCOL_MISMATCH,
    DEVICE_OWNER_MISSING,
    BIND_FAILED,
    CONNECTION_LOST,
}

data class BetaLifecycleUiState(
    val betaBuild: Boolean = false,
    val state: Int =
        ManagementProtocol.BETA_STATE_ACTIVE,
    val destructiveSuppressed: Boolean = false,
    val rearmRequired: Boolean = false,
    val remainingMs: Long = 0L,
    val expiredVersionCode: Int = 0,
    val stateHealthy: Boolean = true,
)

data class UsbUiState(
    val protectionEnabled: Boolean = false,
    val strictModeEnabled: Boolean = false,
    val connectionState: Int =
        ManagementProtocol.USB_CONNECTION_DISCONNECTED,
    val maintenanceActive: Boolean = false,
    val maintenanceRemainingMs: Long = 0L,
    val maintenanceExtendsUsed: Int = 0,
    val maintenanceMaxExtends: Int = 0,
)

data class TamperUiState(
    val usbDebuggingProtectionEnabled: Boolean = false,
    val wirelessDebuggingProtectionEnabled: Boolean = false,
)

data class WipeBehaviourUiState(
    val esimEraseEnabled: Boolean = false,
    val esimEraseAvailable: Boolean = false,
)

data class DeadmanUiState(
    val state: Int =
        ManagementProtocol.DEADMAN_STATE_DISABLED,
    val enabled: Boolean = false,
    val intervalMs: Long = 0L,
    val expiryWallClockMs: Long = 0L,
    val remainingMs: Long = 0L,
    val minIntervalMs: Long = 0L,
    val maxIntervalMs: Long = 0L,
)

data class MotionUiState(
    val enabled: Boolean = false,
    val operationalState: Int =
        ManagementProtocol.MOTION_OPERATIONAL_DISABLED,
    val sensitivity: Int = 0,
    val minSensitivity: Int = 0,
    val maxSensitivity: Int = 100,
    val action: Int =
        ManagementProtocol.MOTION_ACTION_LOCK,
    val rebootAvailable: Boolean = false,
)

data class SmsUiState(
    val enabled: Boolean = false,
    val permissionGranted: Boolean = false,
    val senderConfigured: Boolean = false,
    val sender: String = "",
    val keywordConfigured: Boolean = false,
    val armed: Boolean = false,
    val senderMaxLength: Int =
        ManagementProtocol.SMS_SENDER_MAX_LENGTH,
    val keywordMinLength: Int =
        ManagementProtocol.SMS_KEYWORD_MIN_LENGTH,
    val keywordMaxLength: Int =
        ManagementProtocol.SMS_KEYWORD_MAX_LENGTH,
)

data class ControllerUiState(
    val connectionState: CoreConnectionState =
        CoreConnectionState.IDLE,

    val coreFound: Boolean = false,
    val signatureVerified: Boolean = false,
    val corePackageName: String? = null,
    val protocolVersion: Int? = null,
    val authorityMode: Int =
        ManagementProtocol.AUTHORITY_NONE,
    val deviceOwner: Boolean = false,
    val adminActive: Boolean = false,

    val pinState: Int? = null,
    val remainingLockoutMs: Long = 0L,
    val authenticated: Boolean = false,

    val betaLifecycle: BetaLifecycleUiState =
        BetaLifecycleUiState(),
    val usb: UsbUiState = UsbUiState(),
    val tamper: TamperUiState = TamperUiState(),
    val wipeBehaviour: WipeBehaviourUiState = WipeBehaviourUiState(),
    val deadman: DeadmanUiState = DeadmanUiState(),
    val motion: MotionUiState = MotionUiState(),
    val sms: SmsUiState = SmsUiState(),

    val removalRequested: Boolean = false,
    val message: String? = null,
)

class ControllerViewModel(
    application: Application,
) : AndroidViewModel(application) {

    var uiState: ControllerUiState =
        ControllerUiState()
        private set

    private val observers =
        mutableSetOf<(ControllerUiState) -> Unit>()

    private var api:
        ICoreManagement? = null

    private var serviceConnection:
        ServiceConnection? = null

    private var isBound =
        false

    private var active =
        false

    /*
     * Kept only in Controller process memory. If the Controller process dies,
     * the handoff is intentionally lost and the user must authenticate again.
     */
    private var pendingSensorPermissionHandoffToken:
        String? = null

    private var pendingSmsPermissionHandoffToken:
        String? = null

    private val mainHandler =
        Handler(
            Looper.getMainLooper(),
        )

    private val refreshRunnable =
        object : Runnable {
            override fun run() {
                if (!active) {
                    return
                }

                refresh()

                mainHandler.postDelayed(
                    this,
                    REFRESH_INTERVAL_MS,
                )
            }
        }

    fun observe(
        observer: (ControllerUiState) -> Unit,
    ) {
        observers += observer
        observer(uiState)
    }

    fun removeObserver(
        observer: (ControllerUiState) -> Unit,
    ) {
        observers -= observer
    }

    private fun update(
        transform: (
            ControllerUiState,
        ) -> ControllerUiState,
    ) {
        uiState =
            transform(
                uiState,
            )

        observers.toList()
            .forEach {
                it(uiState)
            }
    }

    fun start() {
        active =
            true

        mainHandler.removeCallbacks(
            refreshRunnable,
        )

        if (
            isBound &&
            api != null
        ) {
            refresh()

            mainHandler.postDelayed(
                refreshRunnable,
                REFRESH_INTERVAL_MS,
            )

            return
        }

        discoverAndBind()
    }

    fun stop() {
        active =
            false

        mainHandler.removeCallbacks(
            refreshRunnable,
        )

        if (
            pendingSensorPermissionHandoffToken ==
            null &&
            pendingSmsPermissionHandoffToken ==
            null
        ) {
            endSession(
                message = null,
            )
        }

        /*
         * When a permission handoff is pending, Core has already destroyed the
         * ordinary authenticated session. Disconnect without issuing a second
         * logout so the one-use handoff can be redeemed when Android returns
         * to Sentinel.
         */
        disconnect()
    }

    fun reconnect() {
        disconnect()

        if (active) {
            discoverAndBind()
        }
    }

    @Suppress("DEPRECATION")
    private fun discoverAndBind() {
        val context =
            getApplication<Application>()

        update {
            it.copy(
                connectionState =
                    CoreConnectionState.SEARCHING,
                coreFound =
                    false,
                signatureVerified =
                    false,
                corePackageName =
                    null,
                protocolVersion =
                    null,
                authorityMode =
                    ManagementProtocol.AUTHORITY_NONE,
                deviceOwner =
                    false,
                adminActive =
                    false,
                authenticated =
                    false,
                message =
                    null,
            )
        }

        val discoveryIntent =
            Intent(
                ManagementProtocol.ACTION_BIND,
            )

        val candidates =
            context.packageManager
                .queryIntentServices(
                    discoveryIntent,
                    0,
                )

        if (candidates.isEmpty()) {
            update {
                it.copy(
                    connectionState =
                        CoreConnectionState.CORE_NOT_FOUND,
                    message =
                        "Sentinel Core was not found.",
                )
            }

            return
        }

        update {
            it.copy(
                coreFound =
                    true,
            )
        }

        val trustedCandidates =
            candidates.filter {
                resolveInfo ->

                val info =
                    resolveInfo.serviceInfo
                        ?: return@filter false

                if (!info.exported) {
                    return@filter false
                }

                if (
                    info.permission !=
                    ManagementProtocol.PERMISSION_CONTROL
                ) {
                    return@filter false
                }

                context.packageManager
                    .checkSignatures(
                        context.packageName,
                        info.packageName,
                    ) ==
                    PackageManager.SIGNATURE_MATCH
            }

        if (trustedCandidates.isEmpty()) {
            update {
                it.copy(
                    connectionState =
                        CoreConnectionState.SIGNATURE_FAILED,
                    signatureVerified =
                        false,
                    message =
                        "Core signature verification failed.",
                )
            }

            return
        }

        /*
         * Core discovery deliberately does not depend on a hard-coded package
         * name. Therefore multiple same-signed implementations are ambiguous
         * and must fail closed rather than relying on PackageManager ordering.
         */
        if (trustedCandidates.size != 1) {
            update {
                it.copy(
                    connectionState =
                        CoreConnectionState.BIND_FAILED,
                    signatureVerified =
                        true,
                    corePackageName =
                        null,
                    message =
                        "Multiple trusted Sentinel Core services were found " +
                            "(${trustedCandidates.size}).",
                )
            }

            return
        }

        val serviceInfo =
            trustedCandidates
                .single()
                .serviceInfo

        update {
            it.copy(
                connectionState =
                    CoreConnectionState.CONNECTING,
                signatureVerified =
                    true,
                corePackageName =
                    serviceInfo.packageName,
                message =
                    null,
            )
        }

        val explicitIntent =
            Intent(
                ManagementProtocol.ACTION_BIND,
            ).apply {
                component =
                    ComponentName(
                        serviceInfo.packageName,
                        serviceInfo.name,
                    )
            }

        val connection =
            object : ServiceConnection {

                override fun onServiceConnected(
                    name: ComponentName,
                    service: IBinder,
                ) {
                    isBound =
                        true

                    val management =
                        ICoreManagement.Stub
                            .asInterface(
                                service,
                            )

                    api =
                        management

                    runCatching {
                        val protocol =
                            management
                                .getProtocolVersion()

                        if (
                            protocol !=
                            ManagementProtocol.PROTOCOL_VERSION
                        ) {
                            update {
                                it.copy(
                                    connectionState =
                                        CoreConnectionState.PROTOCOL_MISMATCH,
                                    protocolVersion =
                                        protocol,
                                    message =
                                        "Unsupported Core protocol $protocol.",
                                )
                            }

                            return@runCatching
                        }

                        val authorityMode =
                            management
                                .getAuthorityMode()

                        update {
                            it.copy(
                                connectionState =
                                    CoreConnectionState.READY,
                                protocolVersion =
                                    protocol,
                                authorityMode =
                                    authorityMode,
                                deviceOwner =
                                    authorityMode ==
                                        ManagementProtocol.AUTHORITY_DEVICE_OWNER,
                                adminActive =
                                    authorityMode !=
                                        ManagementProtocol.AUTHORITY_NONE,
                                message =
                                    null,
                            )
                        }

                        val handoffRedeemed =
                            redeemPendingSensorPermissionHandoff(
                                management,
                            ) ||
                                redeemPendingSmsPermissionHandoff(
                                    management,
                                )

                        if (!handoffRedeemed) {
                            refreshAuthentication()
                        }
                    }.onFailure {
                        update {
                            it.copy(
                                connectionState =
                                    CoreConnectionState.BIND_FAILED,
                                message =
                                    "Core IPC failed: " +
                                        it.message,
                            )
                        }
                    }

                    if (active) {
                        mainHandler.removeCallbacks(
                            refreshRunnable,
                        )

                        mainHandler.postDelayed(
                            refreshRunnable,
                            REFRESH_INTERVAL_MS,
                        )
                    }
                }

                override fun onServiceDisconnected(
                    name: ComponentName,
                ) {
                    api =
                        null

                    isBound =
                        false

                    update {
                        it.copy(
                            connectionState =
                                CoreConnectionState.CONNECTION_LOST,
                            authenticated =
                                false,
                            message =
                                "Core connection lost.",
                        )
                    }
                }

                override fun onNullBinding(
                    name: ComponentName,
                ) {
                    /*
                     * bindService() succeeded, but the selected service did not
                     * provide a management Binder. Explicitly unregister the
                     * connection and leave CONNECTING rather than remaining
                     * indefinitely at "Verifying Core".
                     */
                    disconnect()

                    update {
                        it.copy(
                            connectionState =
                                CoreConnectionState.BIND_FAILED,
                            authenticated =
                                false,
                            message =
                                "Sentinel Core returned no management Binder.",
                        )
                    }
                }

                override fun onBindingDied(
                    name: ComponentName,
                ) {
                    /*
                     * Android reports a permanently invalid binding separately
                     * from an ordinary transient service disconnect.
                     */
                    disconnect()

                    update {
                        it.copy(
                            connectionState =
                                CoreConnectionState.BIND_FAILED,
                            authenticated =
                                false,
                            message =
                                "Sentinel Core binding died. Reconnect required.",
                        )
                    }
                }
            }

        serviceConnection =
            connection

        val started =
            runCatching {
                context.bindService(
                    explicitIntent,
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
            }.getOrDefault(false)

        if (!started) {
            serviceConnection =
                null

            update {
                it.copy(
                    connectionState =
                        CoreConnectionState.BIND_FAILED,
                    message =
                        "Unable to bind to Sentinel Core.",
                )
            }
        }
    }

    fun refresh() {
        if (
            uiState.connectionState !=
            CoreConnectionState.READY
        ) {
            return
        }

        refreshAuthentication()
    }

    private fun refreshAuthentication() {
        val management =
            api
                ?: return

        runCatching {
            val authorityMode =
                management
                    .getAuthorityMode()

            val authenticated =
                management
                    .isSessionAuthenticated()

            val pinState =
                management
                    .getPinState()

            val remainingLockoutMs =
                management
                    .getRemainingLockoutMs()

            update {
                it.copy(
                    authorityMode =
                        authorityMode,
                    deviceOwner =
                        authorityMode ==
                            ManagementProtocol.AUTHORITY_DEVICE_OWNER,
                    adminActive =
                        authorityMode !=
                            ManagementProtocol.AUTHORITY_NONE,
                    pinState =
                        pinState,
                    remainingLockoutMs =
                        remainingLockoutMs,
                    authenticated =
                        authenticated,
                )
            }

            if (authenticated) {
                refreshProtectedState(
                    management,
                )
            }
        }.onFailure {
            update {
                it.copy(
                    authenticated =
                        false,
                    message =
                        "Unable to read Core authentication state.",
                )
            }
        }
    }

    private fun refreshProtectedState(
        management: ICoreManagement,
    ) {
        runCatching {
            val betaLifecycle =
                BetaLifecycleUiState(
                    betaBuild =
                        management
                            .isBetaBuild(),
                    state =
                        management
                            .getBetaLifecycleState(),
                    destructiveSuppressed =
                        management
                            .isBetaDestructiveSuppressed(),
                    rearmRequired =
                        management
                            .isBetaRearmRequired(),
                    remainingMs =
                        management
                            .getBetaRemainingMs(),
                    expiredVersionCode =
                        management
                            .getBetaExpiredVersionCode(),
                    stateHealthy =
                        management
                            .isBetaLifecycleStateHealthy(),
                )

            val tamper =
                TamperUiState(
                    usbDebuggingProtectionEnabled =
                        management
                            .isUsbDebuggingProtectionEnabled(),
                    wirelessDebuggingProtectionEnabled =
                        management
                            .isWirelessDebuggingProtectionEnabled(),
                )

            val wipeBehaviour =
                WipeBehaviourUiState(
                    esimEraseEnabled =
                        management
                            .isEsimEraseEnabled(),
                    esimEraseAvailable =
                        management
                            .isEsimEraseAvailable(),
                )

            val usb =
                UsbUiState(
                    protectionEnabled =
                        management
                            .isUsbProtectionEnabled(),
                    strictModeEnabled =
                        management
                            .isUsbStrictModeEnabled(),
                    connectionState =
                        management
                            .getUsbConnectionState(),
                    maintenanceActive =
                        management
                            .isUsbMaintenanceActive(),
                    maintenanceRemainingMs =
                        management
                            .getUsbMaintenanceRemainingMs(),
                    maintenanceExtendsUsed =
                        management
                            .getUsbMaintenanceExtendsUsed(),
                    maintenanceMaxExtends =
                        management
                            .getUsbMaintenanceMaxExtends(),
                )

            val deadman =
                DeadmanUiState(
                    state =
                        management
                            .getDeadmanState(),
                    enabled =
                        management
                            .isDeadmanEnabled(),
                    intervalMs =
                        management
                            .getDeadmanIntervalMs(),
                    expiryWallClockMs =
                        management
                            .getDeadmanExpiryWallClockMs(),
                    remainingMs =
                        management
                            .getDeadmanRemainingMs(),
                    minIntervalMs =
                        management
                            .getDeadmanMinIntervalMs(),
                    maxIntervalMs =
                        management
                            .getDeadmanMaxIntervalMs(),
                )

            val motion =
                MotionUiState(
                    enabled =
                        management
                            .isMotionEnabled(),
                    operationalState =
                        management
                            .getMotionOperationalState(),
                    sensitivity =
                        management
                            .getMotionSensitivity(),
                    minSensitivity =
                        management
                            .getMotionMinSensitivity(),
                    maxSensitivity =
                        management
                            .getMotionMaxSensitivity(),
                    action =
                        management
                            .getMotionAction(),
                    rebootAvailable =
                        management
                            .isMotionRebootAvailable(),
                )

            val sms =
                SmsUiState(
                    enabled =
                        management
                            .isSmsEnabled(),
                    permissionGranted =
                        management
                            .hasSmsPermission(),
                    senderConfigured =
                        management
                            .isSmsSenderConfigured(),
                    sender =
                        management
                            .getSmsSender(),
                    keywordConfigured =
                        management
                            .isSmsKeywordConfigured(),
                    armed =
                        management
                            .isSmsArmed(),
                    senderMaxLength =
                        management
                            .getSmsSenderMaxLength(),
                    keywordMinLength =
                        management
                            .getSmsKeywordMinLength(),
                    keywordMaxLength =
                        management
                            .getSmsKeywordMaxLength(),
                )

            update {
                it.copy(
                    betaLifecycle =
                        betaLifecycle,
                    usb =
                        usb,
                    tamper =
                        tamper,
                    wipeBehaviour =
                        wipeBehaviour,
                    deadman =
                        deadman,
                    motion =
                        motion,
                    sms =
                        sms,
                )
            }
        }.onFailure {
            /*
             * Re-check authentication if the Core rejects a protected read.
             * Normal foreground refreshes renew the Core's bounded fallback
             * expiry, while leaving the app explicitly logs out.
             */
            refreshAuthenticationAfterProtectedFailure()
        }
    }

    private fun refreshAuthenticationAfterProtectedFailure() {
        val management =
            api
                ?: return

        val authenticated =
            runCatching {
                management
                    .isSessionAuthenticated()
            }.getOrDefault(false)

        update {
            it.copy(
                authenticated =
                    authenticated,
                message =
                    if (authenticated) {
                        "Unable to refresh Core state."
                    } else {
                        null
                    },
            )
        }
    }

    fun enrollPin(
        pin: String,
    ) {
        val management =
            api
                ?: return

        val result =
            runCatching {
                management
                    .enrollPin(
                        pin,
                    )
            }.getOrElse {
                setMessage(
                    "PIN enrolment failed.",
                )
                return
            }

        setMessage(
            authResultText(
                result,
                enrolling = true,
            ),
        )

        refreshAuthentication()
    }

    fun authenticatePin(
        pin: String,
    ) {
        val management =
            api
                ?: return

        val result =
            runCatching {
                management
                    .authenticatePin(
                        pin,
                    )
            }.getOrElse {
                setMessage(
                    "Authentication failed.",
                )
                return
            }

        setMessage(
            authResultText(
                result,
                enrolling = false,
            ),
        )

        refreshAuthentication()
    }

    fun rearmBetaLifecycle(
        pin: String,
    ) {
        val management =
            api
                ?: return

        if (!uiState.authenticated) {
            setMessage(
                "Authenticate before re-arming Beta protections.",
            )
            return
        }

        val result =
            runCatching {
                management
                    .rearmBetaLifecycle(
                        pin,
                    )
            }.getOrElse {
                setMessage(
                    "Beta re-arm operation failed.",
                )
                return
            }

        setMessage(
            betaRearmResultText(
                result,
            ),
        )

        refreshAuthentication()
    }

    fun logout() {
        endSession(
            message =
                "Sentinel session ended.",
        )
    }

    private fun endSession(
        message: String?,
    ) {
        runCatching {
            api?.logout()
        }

        update {
            it.copy(
                authenticated =
                    false,
                message =
                    message,
            )
        }
    }

    fun beginStandardRemoval(
        pin: String,
    ) {
        val management =
            api
                ?: return

        if (
            uiState.authorityMode !=
            ManagementProtocol.AUTHORITY_DEVICE_ADMIN
        ) {
            setMessage(
                if (
                    uiState.authorityMode ==
                    ManagementProtocol.AUTHORITY_DEVICE_OWNER
                ) {
                    "Standard-mode removal is unavailable in Hardened mode."
                } else {
                    "Standard-mode Device Admin authority is not active."
                },
            )

            return
        }

        val result =
            runCatching {
                management
                    .removeStandardAdminSafely(
                        pin,
                    )
            }.getOrElse {
                setMessage(
                    "Standard-mode removal request failed.",
                )

                return
            }

        if (
            result ==
            ManagementProtocol
                .ADMIN_REMOVAL_RESULT_REQUESTED
        ) {
            update {
                it.copy(
                    removalRequested =
                        true,
                    message =
                        "Device Admin release authorised. Waiting for Android to release Standard-mode authority.",
                )
            }
        } else {
            update {
                it.copy(
                    removalRequested =
                        false,
                    message =
                        adminRemovalResultText(
                            result,
                        ),
                )
            }
        }

        refreshAuthentication()
    }

    fun cancelRemovalAfterCoreUninstallCancelled() {
        update {
            it.copy(
                removalRequested =
                    false,
                message =
                    "Core uninstall was cancelled. Device Admin is no longer active; reactivate Standard mode to restore policy protection.",
            )
        }

        if (active) {
            refresh()
        }
    }

    private fun betaLifecycleBlocksConfiguration():
        Boolean {
        if (
            !uiState.betaLifecycle
                .destructiveSuppressed
        ) {
            return false
        }

        setMessage(
            when (
                uiState.betaLifecycle
                    .state
            ) {
                ManagementProtocol
                    .BETA_STATE_CURRENT_BUILD_EXPIRED ->
                    "This Beta build is expired. Protection configuration is locked."

                ManagementProtocol
                    .BETA_STATE_REARM_REQUIRED ->
                    "Protection configuration is locked until Sentinel is re-armed."

                ManagementProtocol
                    .BETA_STATE_ERROR ->
                    "Protection configuration is locked because Beta lifecycle state is unhealthy."

                else ->
                    "Protection configuration is locked while destructive actions are suppressed."
            },
        )

        return true
    }

    fun setUsbDebuggingProtectionEnabled(
        enabled: Boolean,
    ) {
        performTamperOperation {
            it.setUsbDebuggingProtectionEnabled(
                enabled,
            )
        }
    }

    fun setWirelessDebuggingProtectionEnabled(
        enabled: Boolean,
    ) {
        performTamperOperation {
            it.setWirelessDebuggingProtectionEnabled(
                enabled,
            )
        }
    }

    private fun performTamperOperation(
        operation: (
            ICoreManagement,
        ) -> Int,
    ) {
        if (betaLifecycleBlocksConfiguration()) {
            return
        }

        val management =
            api
                ?: return

        val result =
            runCatching {
                operation(
                    management,
                )
            }.getOrElse {
                setMessage(
                    "Tamper-proofing operation failed.",
                )
                return
            }

        setMessage(
            when (result) {
                ManagementProtocol.TAMPER_RESULT_OK ->
                    "Tamper-proofing setting updated."

                ManagementProtocol.TAMPER_RESULT_FAILED ->
                    "Unable to save tamper-proofing setting."

                ManagementProtocol
                    .TAMPER_RESULT_USB_DEBUGGING_ACTIVE ->
                    "Turn off USB debugging before enabling USB Debugging Protection."

                ManagementProtocol
                    .TAMPER_RESULT_WIRELESS_DEBUGGING_ACTIVE ->
                    "Turn off Wireless Debugging before enabling Wireless Debugging Protection."

                else ->
                    "Unknown tamper-proofing result: $result"
            },
        )

        refreshAuthentication()
    }

    fun setEsimEraseEnabled(
        enabled: Boolean,
    ) {
        performWipeBehaviourOperation {
            it.setEsimEraseEnabled(
                enabled,
            )
        }
    }

    private fun performWipeBehaviourOperation(
        operation: (
            ICoreManagement,
        ) -> Int,
    ) {
        if (betaLifecycleBlocksConfiguration()) {
            return
        }

        val management =
            api
                ?: return

        val result =
            runCatching {
                operation(
                    management,
                )
            }.getOrElse {
                setMessage(
                    "Wipe behaviour operation failed.",
                )
                return
            }

        setMessage(
            when (result) {
                ManagementProtocol
                    .WIPE_BEHAVIOUR_RESULT_OK ->
                    "Wipe behaviour updated."

                ManagementProtocol
                    .WIPE_BEHAVIOUR_RESULT_DEVICE_OWNER_REQUIRED ->
                    "eSIM erasure requires Hardened Device Owner mode."

                ManagementProtocol
                    .WIPE_BEHAVIOUR_RESULT_FAILED ->
                    "Unable to save wipe behaviour."

                else ->
                    "Unknown wipe behaviour result: $result"
            },
        )

        refreshAuthentication()
    }

    fun setUsbProtectionEnabled(
        enabled: Boolean,
    ) {
        performUsbOperation {
            it.setUsbProtectionEnabled(
                enabled,
            )
        }
    }

    fun setUsbStrictModeEnabled(
        enabled: Boolean,
    ) {
        performUsbOperation {
            it.setUsbStrictModeEnabled(
                enabled,
            )
        }
    }

    fun startUsbMaintenance() {
        performUsbOperation {
            it.startUsbMaintenance()
        }
    }

    fun extendUsbMaintenance() {
        performUsbOperation {
            it.extendUsbMaintenance()
        }
    }

    fun cancelUsbMaintenance() {
        performUsbOperation {
            it.cancelUsbMaintenance()
        }
    }

    private fun performUsbOperation(
        operation: (
            ICoreManagement,
        ) -> Int,
    ) {
        if (betaLifecycleBlocksConfiguration()) {
            return
        }

        val management =
            api
                ?: return

        val result =
            runCatching {
                operation(
                    management,
                )
            }.getOrElse {
                setMessage(
                    "USB operation failed.",
                )
                return
            }

        setMessage(
            usbResultText(
                result,
            ),
        )

        refreshAuthentication()
    }

    fun setDeadmanEnabled(
        enabled: Boolean,
    ) {
        performDeadmanOperation {
            it.setDeadmanEnabled(
                enabled,
            )
        }
    }

    fun setDeadmanIntervalMs(
        intervalMs: Long,
    ) {
        performDeadmanOperation {
            it.setDeadmanIntervalMs(
                intervalMs,
            )
        }
    }

    fun checkInDeadman() {
        performDeadmanOperation {
            it.checkInDeadman()
        }
    }

    private fun performDeadmanOperation(
        operation: (
            ICoreManagement,
        ) -> Int,
    ) {
        if (betaLifecycleBlocksConfiguration()) {
            return
        }

        val management =
            api
                ?: return

        val result =
            runCatching {
                operation(
                    management,
                )
            }.getOrElse {
                setMessage(
                    "Dead Man operation failed.",
                )
                return
            }

        setMessage(
            deadmanResultText(
                result,
            ),
        )

        refreshAuthentication()
    }

    fun setMotionEnabled(
        enabled: Boolean,
    ) {
        val management =
            api
                ?: return

        val result =
            performMotionOperation {
                it.setMotionEnabled(
                    enabled,
                )
            }

        if (
            !enabled ||
            result !=
            ManagementProtocol.MOTION_RESULT_OK
        ) {
            return
        }

        /*
         * Ask the Core for a one-use setup handoff only when GrapheneOS
         * Sensors access is actually missing. An empty token means no Android
         * permission flow is required, so Sentinel stays in the current
         * authenticated session without leaving the foreground.
         */
        val handoffToken =
            runCatching {
                management
                    .beginSensorPermissionHandoff()
            }.getOrElse {
                setMessage(
                    "Unable to prepare Motion Sensors permission setup.",
                )

                refreshAuthentication()

                return
            }

        if (handoffToken.isBlank()) {
            return
        }

        pendingSensorPermissionHandoffToken =
            handoffToken

        val launched =
            launchCoreActivity(
                action =
                    ManagementProtocol
                        .ACTION_REQUEST_SENSOR_PERMISSION,
                failureMessage =
                    "Unable to open Motion Sensors permission setup.",
                handoffToken =
                    handoffToken,
            )

        if (!launched) {
            /*
             * beginSensorPermissionHandoff() already invalidated the ordinary
             * Core session. If Android cannot launch the trusted setup
             * Activity, fail closed to normal PIN authentication.
             */
            pendingSensorPermissionHandoffToken =
                null

            update {
                it.copy(
                    authenticated =
                        false,
                )
            }
        }
    }

    fun setMotionSensitivity(
        sensitivity: Int,
    ) {
        performMotionOperation {
            it.setMotionSensitivity(
                sensitivity,
            )
        }
    }

    fun setMotionAction(
        action: Int,
    ) {
        performMotionOperation {
            it.setMotionAction(
                action,
            )
        }
    }

    private fun performMotionOperation(
        operation: (
            ICoreManagement,
        ) -> Int,
    ): Int? {
        if (betaLifecycleBlocksConfiguration()) {
            return null
        }

        val management =
            api
                ?: return null

        val result =
            runCatching {
                operation(
                    management,
                )
            }.getOrElse {
                setMessage(
                    "Snatch Protection operation failed.",
                )
                return null
            }

        setMessage(
            motionResultText(
                result,
            ),
        )

        refreshAuthentication()

        return result
    }

    fun grantSmsPermission() {
        if (betaLifecycleBlocksConfiguration()) {
            return
        }

        val management =
            api
                ?: return

        if (uiState.deviceOwner) {
            performSmsOperation {
                it.grantSmsPermission()
            }

            return
        }

        val handoffToken =
            runCatching {
                management
                    .beginSmsPermissionHandoff()
            }.getOrElse {
                setMessage(
                    "Unable to prepare SMS permission setup.",
                )

                refreshAuthentication()
                return
            }

        if (handoffToken.isBlank()) {
            refresh()
            return
        }

        pendingSmsPermissionHandoffToken =
            handoffToken

        val launched =
            launchCoreActivity(
                action =
                    ManagementProtocol
                        .ACTION_REQUEST_SMS_PERMISSION,
                failureMessage =
                    "Unable to open SMS permission setup.",
                handoffToken =
                    handoffToken,
            )

        if (!launched) {
            pendingSmsPermissionHandoffToken =
                null

            update {
                it.copy(
                    authenticated =
                        false,
                )
            }
        }
    }

    fun activateStandardAdmin() {
        launchCoreActivity(
            action =
                ManagementProtocol.ACTION_SETUP_ADMIN,
            failureMessage =
                "Unable to open Core Device Admin setup.",
        )
    }

    fun releaseSmsPermissionPolicy() {
        performSmsOperation {
            it.releaseSmsPermissionPolicy()
        }
    }

    fun setSmsSender(
        sender: String,
    ) {
        performSmsOperation {
            it.setSmsSender(
                sender,
            )
        }
    }

    fun setSmsKeyword(
        keyword: String,
    ) {
        performSmsOperation {
            it.setSmsKeyword(
                keyword,
            )
        }
    }

    fun setSmsEnabled(
        enabled: Boolean,
    ) {
        performSmsOperation {
            it.setSmsEnabled(
                enabled,
            )
        }
    }

    private fun performSmsOperation(
        operation: (
            ICoreManagement,
        ) -> Int,
    ) {
        if (betaLifecycleBlocksConfiguration()) {
            return
        }

        val management =
            api
                ?: return

        val result =
            runCatching {
                operation(
                    management,
                )
            }.getOrElse {
                setMessage(
                    "SMS Wipe operation failed.",
                )
                return
            }

        setMessage(
            smsResultText(
                result,
            ),
        )

        refreshAuthentication()
    }

    fun simulateSms(
        sender: String,
        body: String,
    ) {
        val management =
            api
                ?: return

        val result =
            runCatching {
                management
                    .simulateSms(
                        sender,
                        body,
                    )
            }.getOrElse {
                setMessage(
                    "SMS simulation failed.",
                )
                return
            }

        setMessage(
            smsSimulationResultText(
                result,
            ),
        )

        refreshAuthentication()
    }

    private fun redeemPendingSensorPermissionHandoff(
        management: ICoreManagement,
    ): Boolean {
        val token =
            pendingSensorPermissionHandoffToken
                ?: return false

        /*
         * Consume locally before making the IPC call. A failed/expired token
         * therefore cannot be retried indefinitely from the Controller.
         */
        pendingSensorPermissionHandoffToken =
            null

        val redeemed =
            runCatching {
                management
                    .redeemSensorPermissionHandoff(
                        token,
                    )
            }.getOrDefault(
                false,
            )

        if (!redeemed) {
            update {
                it.copy(
                    authenticated =
                        false,
                )
            }

            return false
        }

        update {
            it.copy(
                authenticated =
                    true,
                message =
                    null,
            )
        }

        refreshProtectedState(
            management,
        )

        return true
    }

    private fun redeemPendingSmsPermissionHandoff(
        management: ICoreManagement,
    ): Boolean {
        val token =
            pendingSmsPermissionHandoffToken
                ?: return false

        /*
         * Consume locally first so a failed or expired setup token cannot be
         * repeatedly replayed from the Controller process.
         */
        pendingSmsPermissionHandoffToken =
            null

        val redeemed =
            runCatching {
                management
                    .redeemSmsPermissionHandoff(
                        token,
                    )
            }.getOrDefault(
                false,
            )

        if (!redeemed) {
            update {
                it.copy(
                    authenticated =
                        false,
                )
            }

            return false
        }

        update {
            it.copy(
                authenticated =
                    true,
                message =
                    null,
            )
        }

        refreshProtectedState(
            management,
        )

        return true
    }

    fun clearMessage() {
        update {
            it.copy(
                message =
                    null,
            )
        }
    }


    @Suppress("DEPRECATION")
    private fun launchCoreActivity(
        action: String,
        failureMessage: String,
        handoffToken: String? = null,
    ): Boolean {
        val context =
            getApplication<Application>()

        val discoveryIntent =
            Intent(
                action,
            )

        val trustedCandidates =
            context.packageManager
                .queryIntentActivities(
                    discoveryIntent,
                    0,
                )
                .filter {
                    resolveInfo ->

                    val info =
                        resolveInfo.activityInfo
                            ?: return@filter false

                    if (!info.exported) {
                        return@filter false
                    }

                    if (
                        info.permission !=
                        ManagementProtocol.PERMISSION_CONTROL
                    ) {
                        return@filter false
                    }

                    context.packageManager
                        .checkSignatures(
                            context.packageName,
                            info.packageName,
                        ) ==
                        PackageManager.SIGNATURE_MATCH
                }

        if (trustedCandidates.isEmpty()) {
            setMessage(
                failureMessage,
            )

            return false
        }

        if (trustedCandidates.size != 1) {
            setMessage(
                "Multiple trusted Sentinel Core activities matched this request.",
            )

            return false
        }

        val info =
            trustedCandidates
                .single()
                .activityInfo

        val explicitIntent =
            Intent(
                action,
            ).apply {
                component =
                    ComponentName(
                        info.packageName,
                        info.name,
                    )

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK,
                )

                if (!handoffToken.isNullOrBlank()) {
                    putExtra(
                        ManagementProtocol
                            .EXTRA_SETUP_HANDOFF_TOKEN,
                        handoffToken,
                    )
                }
            }

        return runCatching {
            context.startActivity(
                explicitIntent,
            )

            true
        }.getOrElse {
            setMessage(
                failureMessage,
            )

            false
        }
    }

    private fun setMessage(
        message: String?,
    ) {
        update {
            it.copy(
                message =
                    message,
            )
        }
    }

    private fun authResultText(
        result: Int,
        enrolling: Boolean,
    ): String =
        when (result) {
            ManagementProtocol.RESULT_OK ->
                if (enrolling) {
                    "Sentinel PIN enrolled."
                } else {
                    "Authentication successful."
                }

            ManagementProtocol.RESULT_INVALID_PIN ->
                "Incorrect Sentinel PIN."

            ManagementProtocol.RESULT_LOCKED ->
                "Authentication temporarily locked."

            ManagementProtocol.RESULT_TERMINAL ->
                "Authentication is permanently locked."

            ManagementProtocol.RESULT_NOT_ENROLLED ->
                "Sentinel PIN has not been enrolled."

            ManagementProtocol.RESULT_REPEAT_FAILURE ->
                "PIN confirmation failed."

            ManagementProtocol.RESULT_INVALID_FORMAT ->
                "PIN must contain ${ManagementProtocol.PIN_MIN_LENGTH}-${ManagementProtocol.PIN_MAX_LENGTH} digits."

            ManagementProtocol.RESULT_ALREADY_ENROLLED ->
                "A Sentinel PIN is already enrolled."

            else ->
                "Unknown authentication result: $result"
        }

    private fun betaRearmResultText(
        result: Int,
    ): String =
        when (result) {
            ManagementProtocol.BETA_RESULT_OK ->
                "Beta protections re-armed for this upgraded build."

            ManagementProtocol
                .BETA_RESULT_CURRENT_BUILD_EXPIRED ->
                "This Beta build is expired and cannot be re-armed."

            ManagementProtocol
                .BETA_RESULT_REARM_NOT_REQUIRED ->
                "Beta protections do not require re-arming."

            ManagementProtocol
                .BETA_RESULT_VERSION_NOT_NEWER ->
                "Install a newer Sentinel build before re-arming protections."

            ManagementProtocol
                .BETA_RESULT_STATE_ERROR ->
                "Beta lifecycle state is unhealthy. Destructive actions remain suppressed."

            ManagementProtocol.BETA_RESULT_FAILED ->
                "Unable to persist the Beta re-arm state. Destructive actions remain suppressed."

            ManagementProtocol.RESULT_INVALID_PIN,
            ManagementProtocol.RESULT_LOCKED,
            ManagementProtocol.RESULT_TERMINAL,
            ManagementProtocol.RESULT_NOT_ENROLLED,
            ManagementProtocol.RESULT_REPEAT_FAILURE,
            ManagementProtocol.RESULT_INVALID_FORMAT,
            ManagementProtocol.RESULT_ALREADY_ENROLLED ->
                authResultText(
                    result,
                    enrolling = false,
                )

            else ->
                "Unknown Beta re-arm result: $result"
        }

    private fun adminRemovalResultText(
        result: Int,
    ): String =
        when (result) {
            ManagementProtocol.ADMIN_REMOVAL_RESULT_REQUESTED ->
                "Device Admin removal requested. Waiting for Android to release Standard-mode authority."

            ManagementProtocol.ADMIN_REMOVAL_RESULT_DEVICE_OWNER ->
                "Safe Standard-mode removal is unavailable in Hardened mode."

            ManagementProtocol.ADMIN_REMOVAL_RESULT_ADMIN_INACTIVE ->
                "Standard-mode Device Admin authority is already inactive."

            ManagementProtocol.ADMIN_REMOVAL_RESULT_AUTHORIZATION_FAILED ->
                "Unable to create the one-use safe-removal authorization."

            ManagementProtocol.ADMIN_REMOVAL_RESULT_FAILED ->
                "Android rejected the Standard-mode Device Admin removal request."

            ManagementProtocol.RESULT_INVALID_PIN,
            ManagementProtocol.RESULT_LOCKED,
            ManagementProtocol.RESULT_TERMINAL,
            ManagementProtocol.RESULT_NOT_ENROLLED,
            ManagementProtocol.RESULT_REPEAT_FAILURE,
            ManagementProtocol.RESULT_INVALID_FORMAT,
            ManagementProtocol.RESULT_ALREADY_ENROLLED ->
                authResultText(
                    result,
                    enrolling = false,
                )

            else ->
                "Unknown Device Admin removal result: $result"
        }

    private fun usbResultText(
        result: Int,
    ): String =
        when (result) {
            ManagementProtocol.USB_RESULT_OK ->
                "USB configuration updated."

            ManagementProtocol.USB_RESULT_ADMIN_INACTIVE ->
                "Device administration is not active."

            ManagementProtocol.USB_RESULT_PROTECTION_DISABLED ->
                "USB Protection must be enabled first."

            ManagementProtocol.USB_RESULT_WIRED_CONNECTION ->
                "Disconnect wired USB before performing this operation."

            ManagementProtocol.USB_RESULT_USER_LOCKED ->
                "Unlock the device before performing this operation."

            ManagementProtocol.USB_RESULT_EXACT_ALARM_UNAVAILABLE ->
                "Exact alarm scheduling is unavailable."

            ManagementProtocol.USB_RESULT_MAINTENANCE_INACTIVE ->
                "USB Maintenance is not active."

            ManagementProtocol.USB_RESULT_EXTENSION_LIMIT ->
                "USB Maintenance extension limit reached."

            ManagementProtocol.USB_RESULT_FAILED ->
                "USB operation failed."

            else ->
                "Unknown USB result: $result"
        }

    private fun deadmanResultText(
        result: Int,
    ): String =
        when (result) {
            ManagementProtocol.DEADMAN_RESULT_OK ->
                "Dead Man configuration updated."

            ManagementProtocol.DEADMAN_RESULT_ADMIN_INACTIVE ->
                "Device administration is not active."

            ManagementProtocol.DEADMAN_RESULT_INVALID_STATE ->
                "Dead Man state is invalid."

            ManagementProtocol.DEADMAN_RESULT_EXPIRED ->
                "Dead Man deadline has already expired."

            ManagementProtocol.DEADMAN_RESULT_DISABLED ->
                "Dead Man is disabled."

            ManagementProtocol.DEADMAN_RESULT_INVALID_INTERVAL ->
                "Dead Man interval is outside the allowed range."

            ManagementProtocol.DEADMAN_RESULT_FAILED ->
                "Dead Man operation failed."

            else ->
                "Unknown Dead Man result: $result"
        }

    private fun motionResultText(
        result: Int,
    ): String =
        when (result) {
            ManagementProtocol.MOTION_RESULT_OK ->
                "Snatch Protection configuration updated."

            ManagementProtocol.MOTION_RESULT_ADMIN_INACTIVE ->
                "Device administration is not active."

            ManagementProtocol.MOTION_RESULT_INVALID_SENSITIVITY ->
                "Motion sensitivity is invalid."

            ManagementProtocol.MOTION_RESULT_INVALID_ACTION ->
                "Snatch response is invalid."

            ManagementProtocol.MOTION_RESULT_FAILED ->
                "Snatch Protection operation failed."

            ManagementProtocol.MOTION_RESULT_REBOOT_REQUIRES_DEVICE_OWNER ->
                "Reboot requires Hardened mode. Device Owner provisioning is required."

            else ->
                "Unknown Motion result: $result"
        }

    private fun smsResultText(
        result: Int,
    ): String =
        when (result) {
            ManagementProtocol.SMS_RESULT_OK ->
                "SMS Wipe configuration updated."

            ManagementProtocol.SMS_RESULT_ADMIN_INACTIVE ->
                "Device administration is not active."

            ManagementProtocol.SMS_RESULT_PERMISSION_MISSING ->
                "Core SMS permission is missing."

            ManagementProtocol.SMS_RESULT_SENDER_MISSING ->
                "Authorised SMS sender is not configured."

            ManagementProtocol.SMS_RESULT_KEYWORD_INVALID ->
                "SMS trigger keyword is not configured correctly."

            ManagementProtocol.SMS_RESULT_INVALID_SENDER ->
                "Authorised sender number is invalid."

            ManagementProtocol.SMS_RESULT_INVALID_KEYWORD ->
                "SMS trigger keyword is invalid."

            ManagementProtocol.SMS_RESULT_DEVICE_OWNER_REQUIRED ->
                "Hardened Device Owner authority is required for this operation."

            ManagementProtocol.SMS_RESULT_FAILED ->
                "SMS Wipe operation failed."

            else ->
                "Unknown SMS result: $result"
        }

    private fun smsSimulationResultText(
        result: Int,
    ): String =
        when (result) {
            ManagementProtocol.SMS_SIMULATION_MATCH ->
                "Simulation matched the configured SMS wipe trigger."

            ManagementProtocol.SMS_SIMULATION_NO_MATCH ->
                "Simulation did not match the configured trigger."

            ManagementProtocol.SMS_SIMULATION_DISABLED ->
                "SMS Wipe is disabled."

            ManagementProtocol.SMS_SIMULATION_ADMIN_INACTIVE ->
                "Device administration is not active."

            ManagementProtocol.SMS_SIMULATION_PERMISSION_MISSING ->
                "Core SMS permission is missing."

            ManagementProtocol.SMS_SIMULATION_SENDER_MISSING ->
                "Authorised sender is not configured."

            ManagementProtocol.SMS_SIMULATION_KEYWORD_INVALID ->
                "Trigger keyword is not configured correctly."

            else ->
                "Unknown SMS simulation result: $result"
        }

    private fun disconnect() {
        mainHandler.removeCallbacks(
            refreshRunnable,
        )

        val context =
            getApplication<Application>()

        val connection =
            serviceConnection

        /*
         * serviceConnection represents a successful or pending bind
         * registration. Do not gate unbindService() on isBound because
         * onNullBinding() and other pre-connection failure paths occur before
         * onServiceConnected() sets isBound=true.
         */
        if (connection != null) {
            runCatching {
                context.unbindService(
                    connection,
                )
            }
        }

        api =
            null

        serviceConnection =
            null

        isBound =
            false
    }

    override fun onCleared() {
        active =
            false

        mainHandler.removeCallbacks(
            refreshRunnable,
        )

        /*
         * Best-effort final logout in case the ViewModel is cleared without a
         * preceding Activity.onStop(). The Core's bounded inactivity expiry
         * remains the fallback if IPC is already unavailable.
         */
        runCatching {
            api?.logout()
        }

        disconnect()

        super.onCleared()
    }

    companion object {
        private const val REFRESH_INTERVAL_MS =
            1_000L
    }
}
