package com.localruntime.core.management

import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import com.localruntime.core.admin.DeviceAdminManager
import com.localruntime.core.beta.BetaLifecycleGuard
import com.runtimebridge.api.ICoreManagement
import com.runtimebridge.api.ManagementProtocol
import java.util.concurrent.ConcurrentHashMap

/**
 * Signature-protected management endpoint for the temporary Controller.
 */
class CoreManagementService : Service() {

    private lateinit var pinManager:
        CoreAuthManager

    private lateinit var betaLifecycleGuard:
        BetaLifecycleGuard

    private lateinit var tamperManager:
        TamperManagementManager

    private lateinit var wipeBehaviourManager:
        WipeBehaviourManagementManager

    private lateinit var usbManager:
        UsbManagementManager

    private lateinit var deadmanManager:
        DeadmanManagementManager

    private lateinit var motionManager:
        MotionManagementManager

    private lateinit var smsManager:
        SmsManagementManager

    /*
     * Authenticated sessions live only in Core process memory.
     *
     * The expiry is an inactivity fallback, not a foreground UI timeout.
     * Valid authenticated calls renew the expiry. The Controller explicitly
     * logs out when it leaves the foreground, while reboot, Core process death,
     * explicit logout, or prolonged loss of the Controller still invalidates
     * the administrative session.
     */
    private val authenticatedSessions =
        ConcurrentHashMap<Int, Long>()

    override fun onCreate() {
        super.onCreate()

        pinManager =
            CoreAuthManager(this)

        betaLifecycleGuard =
            BetaLifecycleGuard(this)

        tamperManager =
            TamperManagementManager(this)

        wipeBehaviourManager =
            WipeBehaviourManagementManager(this)

        usbManager =
            UsbManagementManager(this)

        deadmanManager =
            DeadmanManagementManager(this)

        motionManager =
            MotionManagementManager(this)

        smsManager =
            SmsManagementManager(this)
    }

    private val binder =
        object : ICoreManagement.Stub() {

            override fun getProtocolVersion(): Int {
                enforceTrustedCaller()

                return ManagementProtocol
                    .PROTOCOL_VERSION
            }

            override fun isDeviceOwner(): Boolean {
                enforceTrustedCaller()

                return isCoreDeviceOwner()
            }

            override fun getAuthorityMode(): Int {
                enforceTrustedCaller()

                return getCoreAuthorityMode()
            }

            override fun isBetaBuild(): Boolean {
                enforceTrustedManagementCaller()

                return betaLifecycleGuard
                    .snapshot()
                    .betaBuild
            }

            override fun getBetaLifecycleState(): Int {
                enforceTrustedManagementCaller()

                val snapshot =
                    betaLifecycleGuard
                        .snapshot()

                return when {
                    !snapshot.stateHealthy ->
                        ManagementProtocol
                            .BETA_STATE_ERROR

                    snapshot.currentBuildExpired ->
                        ManagementProtocol
                            .BETA_STATE_CURRENT_BUILD_EXPIRED

                    snapshot.rearmRequired ->
                        ManagementProtocol
                            .BETA_STATE_REARM_REQUIRED

                    else ->
                        ManagementProtocol
                            .BETA_STATE_ACTIVE
                }
            }

            override fun isBetaDestructiveSuppressed():
                Boolean {
                enforceTrustedManagementCaller()

                return betaLifecycleGuard
                    .snapshot()
                    .destructiveSuppressed
            }

            override fun isBetaRearmRequired():
                Boolean {
                enforceTrustedManagementCaller()

                return betaLifecycleGuard
                    .snapshot()
                    .rearmRequired
            }

            override fun getBetaRemainingMs(): Long {
                enforceTrustedManagementCaller()

                return betaLifecycleGuard
                    .snapshot()
                    .remainingMs
            }

            override fun getBetaExpiredVersionCode():
                Int {
                enforceTrustedManagementCaller()

                return betaLifecycleGuard
                    .snapshot()
                    .expiredVersionCode
            }

            override fun isBetaLifecycleStateHealthy():
                Boolean {
                enforceTrustedManagementCaller()

                return betaLifecycleGuard
                    .snapshot()
                    .stateHealthy
            }

            override fun rearmBetaLifecycle(
                pin: String,
            ): Int {
                /*
                 * Re-arming can restore destructive security actions, so it is
                 * deliberately stronger than an ordinary authenticated call:
                 *
                 * 1. an authenticated Controller session must already exist;
                 * 2. the Sentinel management PIN is verified again;
                 * 3. Core independently verifies that this is a newer,
                 *    non-expired build than the build which tripped the latch.
                 */
                enforceAuthenticatedSession()

                val callingUid =
                    Binder.getCallingUid()

                val authenticationResult =
                    pinManager.authenticate(
                        pin,
                    )

                if (
                    authenticationResult !=
                    ManagementProtocol.RESULT_OK
                ) {
                    return authenticationResult
                }

                createSession(
                    callingUid,
                )

                return when (
                    betaLifecycleGuard
                        .rearmAfterUpgrade()
                ) {
                    BetaLifecycleGuard
                        .RearmResult.OK ->
                        ManagementProtocol
                            .BETA_RESULT_OK

                    BetaLifecycleGuard
                        .RearmResult.CURRENT_BUILD_EXPIRED ->
                        ManagementProtocol
                            .BETA_RESULT_CURRENT_BUILD_EXPIRED

                    BetaLifecycleGuard
                        .RearmResult.NOT_REQUIRED ->
                        ManagementProtocol
                            .BETA_RESULT_REARM_NOT_REQUIRED

                    BetaLifecycleGuard
                        .RearmResult.VERSION_NOT_NEWER ->
                        ManagementProtocol
                            .BETA_RESULT_VERSION_NOT_NEWER

                    BetaLifecycleGuard
                        .RearmResult.STATE_ERROR ->
                        ManagementProtocol
                            .BETA_RESULT_STATE_ERROR

                    BetaLifecycleGuard
                        .RearmResult.FAILED ->
                        ManagementProtocol
                            .BETA_RESULT_FAILED
                }
            }

            override fun getPinState(): Int {
                enforceTrustedManagementCaller()

                return pinManager.getPinState()
            }

            override fun getRemainingLockoutMs(): Long {
                enforceTrustedManagementCaller()

                return pinManager
                    .getRemainingLockoutMs()
            }

            override fun enrollPin(
                pin: String,
            ): Int {
                enforceTrustedManagementCaller()

                val result =
                    pinManager.enroll(pin)

                if (
                    result ==
                    ManagementProtocol.RESULT_OK
                ) {
                    createSession(
                        Binder.getCallingUid(),
                    )
                }

                return result
            }

            override fun authenticatePin(
                pin: String,
            ): Int {
                enforceTrustedManagementCaller()

                val result =
                    pinManager.authenticate(pin)

                if (
                    result ==
                    ManagementProtocol.RESULT_OK
                ) {
                    createSession(
                        Binder.getCallingUid(),
                    )
                }

                return result
            }

            override fun isSessionAuthenticated():
                Boolean {
                enforceTrustedManagementCaller()

                return hasValidSession(
                    Binder.getCallingUid(),
                )
            }

            override fun logout() {
                enforceTrustedManagementCaller()

                authenticatedSessions.remove(
                    Binder.getCallingUid(),
                )
            }

            override fun beginSensorPermissionHandoff():
                String {
                enforceAuthenticatedSession()

                /*
                 * Do not create a setup exception when GrapheneOS Sensors
                 * access is already available. In that case the normal
                 * authenticated session remains untouched.
                 */
                if (
                    com.localruntime.core.motionbridge.MotionHelperClient
                        .get(
                            this@CoreManagementService,
                        )
                        .isSensorAccessAvailable()
                ) {
                    return ""
                }

                val callingUid =
                    Binder.getCallingUid()

                val token =
                    SetupHandoffStore.create(
                        callerUid =
                            callingUid,
                        action =
                            ManagementProtocol
                                .ACTION_REQUEST_SENSOR_PERMISSION,
                    )

                /*
                 * The handoff replaces the ordinary authenticated session.
                 * Sentinel is therefore not left authenticated while Android's
                 * permission UI owns the foreground.
                 */
                authenticatedSessions.remove(
                    callingUid,
                )

                return token
            }

            override fun redeemSensorPermissionHandoff(
                token: String,
            ): Boolean {
                enforceTrustedManagementCaller()

                val callingUid =
                    Binder.getCallingUid()

                val accepted =
                    SetupHandoffStore.consume(
                        token =
                            token,
                        callerUid =
                            callingUid,
                        action =
                            ManagementProtocol
                                .ACTION_REQUEST_SENSOR_PERMISSION,
                    )

                if (accepted) {
                    createSession(
                        callingUid,
                    )
                }

                return accepted
            }

            override fun beginSmsPermissionHandoff():
                String {
                enforceAuthenticatedSession()

                if (
                    smsManager.hasSmsPermission()
                ) {
                    return ""
                }

                val callingUid =
                    Binder.getCallingUid()

                val token =
                    SetupHandoffStore.create(
                        callerUid =
                            callingUid,
                        action =
                            ManagementProtocol
                                .ACTION_REQUEST_SMS_PERMISSION,
                    )

                /*
                 * As with Motion, the setup handoff replaces the ordinary
                 * authenticated session while Android owns the foreground.
                 */
                authenticatedSessions.remove(
                    callingUid,
                )

                return token
            }

            override fun redeemSmsPermissionHandoff(
                token: String,
            ): Boolean {
                enforceTrustedManagementCaller()

                val callingUid =
                    Binder.getCallingUid()

                val accepted =
                    SetupHandoffStore.consume(
                        token =
                            token,
                        callerUid =
                            callingUid,
                        action =
                            ManagementProtocol
                                .ACTION_REQUEST_SMS_PERMISSION,
                    )

                if (accepted) {
                    createSession(
                        callingUid,
                    )
                }

                return accepted
            }

            override fun removeStandardAdminSafely(
                pin: String,
            ): Int {
                /*
                 * Removal is deliberately stronger than an ordinary protected
                 * management call:
                 *
                 * 1. the caller must already hold a valid authenticated Core
                 *    session;
                 * 2. the Core independently verifies the Sentinel PIN again;
                 * 3. only then may Standard Device Admin authority be released.
                 */
                enforceAuthenticatedSession()

                val callingUid =
                    Binder.getCallingUid()

                val authenticationResult =
                    pinManager.authenticate(
                        pin,
                    )

                if (
                    authenticationResult !=
                    ManagementProtocol.RESULT_OK
                ) {
                    return authenticationResult
                }

                /*
                 * Fresh authentication succeeded. Renew the session while the
                 * removal transaction is executed.
                 */
                createSession(
                    callingUid,
                )

                val removalResult =
                    DeviceAdminManager(
                        this@CoreManagementService,
                    ).removeStandardAdminSafely()

                val mappedResult =
                    when (removalResult) {
                        DeviceAdminManager
                            .StandardAdminRemovalResult
                            .REQUESTED ->
                            ManagementProtocol
                                .ADMIN_REMOVAL_RESULT_REQUESTED

                        DeviceAdminManager
                            .StandardAdminRemovalResult
                            .DEVICE_OWNER ->
                            ManagementProtocol
                                .ADMIN_REMOVAL_RESULT_DEVICE_OWNER

                        DeviceAdminManager
                            .StandardAdminRemovalResult
                            .ADMIN_INACTIVE ->
                            ManagementProtocol
                                .ADMIN_REMOVAL_RESULT_ADMIN_INACTIVE

                        DeviceAdminManager
                            .StandardAdminRemovalResult
                            .AUTHORIZATION_FAILED ->
                            ManagementProtocol
                                .ADMIN_REMOVAL_RESULT_AUTHORIZATION_FAILED

                        DeviceAdminManager
                            .StandardAdminRemovalResult
                            .FAILED ->
                            ManagementProtocol
                                .ADMIN_REMOVAL_RESULT_FAILED
                    }

                if (
                    mappedResult ==
                    ManagementProtocol
                        .ADMIN_REMOVAL_RESULT_REQUESTED
                ) {
                    /*
                     * Once Sentinel has deliberately begun dismantling Standard
                     * mode, do not leave an authenticated management session
                     * behind.
                     */
                    authenticatedSessions.remove(
                        callingUid,
                    )
                }

                return mappedResult
            }

            override fun isUsbDebuggingProtectionEnabled():
                Boolean {
                enforceAuthenticatedSession()

                return tamperManager
                    .isUsbDebuggingProtectionEnabled()
            }

            override fun isWirelessDebuggingProtectionEnabled():
                Boolean {
                enforceAuthenticatedSession()

                return tamperManager
                    .isWirelessDebuggingProtectionEnabled()
            }

            override fun setUsbDebuggingProtectionEnabled(
                enabled: Boolean,
            ): Int {
                enforceAuthenticatedSession()

                return tamperManager
                    .setUsbDebuggingProtectionEnabled(
                        enabled,
                    )
            }

            override fun setWirelessDebuggingProtectionEnabled(
                enabled: Boolean,
            ): Int {
                enforceAuthenticatedSession()

                return tamperManager
                    .setWirelessDebuggingProtectionEnabled(
                        enabled,
                    )
            }

            override fun isEsimEraseEnabled(): Boolean {
                enforceAuthenticatedSession()

                return wipeBehaviourManager
                    .isEsimEraseEnabled()
            }

            override fun isEsimEraseAvailable(): Boolean {
                enforceAuthenticatedSession()

                return wipeBehaviourManager
                    .isEsimEraseAvailable()
            }

            override fun setEsimEraseEnabled(
                enabled: Boolean,
            ): Int {
                enforceAuthenticatedSession()

                return wipeBehaviourManager
                    .setEsimEraseEnabled(
                        enabled,
                    )
            }

            override fun isUsbProtectionEnabled():
                Boolean {
                enforceAuthenticatedSession()

                return usbManager
                    .isProtectionEnabled()
            }

            override fun isUsbStrictModeEnabled():
                Boolean {
                enforceAuthenticatedSession()

                return usbManager
                    .isStrictModeEnabled()
            }

            override fun getUsbConnectionState(): Int {
                enforceAuthenticatedSession()

                return usbManager
                    .getConnectionState()
            }

            override fun isUsbMaintenanceActive():
                Boolean {
                enforceAuthenticatedSession()

                return usbManager
                    .isMaintenanceActive()
            }

            override fun getUsbMaintenanceRemainingMs():
                Long {
                enforceAuthenticatedSession()

                return usbManager
                    .getMaintenanceRemainingMs()
            }

            override fun getUsbMaintenanceExtendsUsed():
                Int {
                enforceAuthenticatedSession()

                return usbManager
                    .getMaintenanceExtendsUsed()
            }

            override fun getUsbMaintenanceMaxExtends():
                Int {
                enforceAuthenticatedSession()

                return usbManager
                    .getMaintenanceMaxExtends()
            }

            override fun setUsbProtectionEnabled(
                enabled: Boolean,
            ): Int {
                enforceAuthenticatedSession()

                return usbManager
                    .setProtectionEnabled(
                        enabled,
                    )
            }

            override fun setUsbStrictModeEnabled(
                enabled: Boolean,
            ): Int {
                enforceAuthenticatedSession()

                return usbManager
                    .setStrictModeEnabled(
                        enabled,
                    )
            }

            override fun startUsbMaintenance(): Int {
                enforceAuthenticatedSession()

                return usbManager
                    .startMaintenance()
            }

            override fun extendUsbMaintenance(): Int {
                enforceAuthenticatedSession()

                return usbManager
                    .extendMaintenance()
            }

            override fun cancelUsbMaintenance(): Int {
                enforceAuthenticatedSession()

                return usbManager
                    .cancelMaintenance()
            }

            override fun getDeadmanState(): Int {
                enforceAuthenticatedSession()

                return deadmanManager
                    .getState()
            }

            override fun isDeadmanEnabled(): Boolean {
                enforceAuthenticatedSession()

                return deadmanManager
                    .isEnabled()
            }

            override fun getDeadmanIntervalMs(): Long {
                enforceAuthenticatedSession()

                return deadmanManager
                    .getIntervalMs()
            }

            override fun getDeadmanExpiryWallClockMs():
                Long {
                enforceAuthenticatedSession()

                return deadmanManager
                    .getExpiryWallClockMs()
            }

            override fun getDeadmanRemainingMs(): Long {
                enforceAuthenticatedSession()

                return deadmanManager
                    .getRemainingMs()
            }

            override fun getDeadmanMinIntervalMs(): Long {
                enforceAuthenticatedSession()

                return deadmanManager
                    .getMinIntervalMs()
            }

            override fun getDeadmanMaxIntervalMs(): Long {
                enforceAuthenticatedSession()

                return deadmanManager
                    .getMaxIntervalMs()
            }

            override fun setDeadmanEnabled(
                enabled: Boolean,
            ): Int {
                enforceAuthenticatedSession()

                return deadmanManager
                    .setEnabled(
                        enabled,
                    )
            }

            override fun setDeadmanIntervalMs(
                intervalMs: Long,
            ): Int {
                enforceAuthenticatedSession()

                return deadmanManager
                    .setIntervalMs(
                        intervalMs,
                    )
            }

            override fun checkInDeadman(): Int {
                enforceAuthenticatedSession()

                return deadmanManager
                    .checkIn()
            }

            override fun isMotionEnabled(): Boolean {
                enforceAuthenticatedSession()

                return motionManager
                    .isEnabled()
            }

            override fun getMotionOperationalState(): Int {
                enforceAuthenticatedSession()

                return motionManager
                    .getOperationalState()
            }

            override fun getMotionSensitivity(): Int {
                enforceAuthenticatedSession()

                return motionManager
                    .getSensitivity()
            }

            override fun getMotionMinSensitivity(): Int {
                enforceAuthenticatedSession()

                return motionManager
                    .getMinSensitivity()
            }

            override fun getMotionMaxSensitivity(): Int {
                enforceAuthenticatedSession()

                return motionManager
                    .getMaxSensitivity()
            }

            override fun getMotionAction(): Int {
                enforceAuthenticatedSession()

                return motionManager
                    .getAction()
            }

            override fun isMotionRebootAvailable():
                Boolean {
                enforceAuthenticatedSession()

                return motionManager
                    .isRebootAvailable()
            }

            override fun setMotionEnabled(
                enabled: Boolean,
            ): Int {
                enforceAuthenticatedSession()

                return motionManager
                    .setEnabled(
                        enabled,
                    )
            }

            override fun setMotionSensitivity(
                sensitivity: Int,
            ): Int {
                enforceAuthenticatedSession()

                return motionManager
                    .setSensitivity(
                        sensitivity,
                    )
            }

            override fun setMotionAction(
                action: Int,
            ): Int {
                enforceAuthenticatedSession()

                return motionManager
                    .setAction(
                        action,
                    )
            }

            override fun isSmsEnabled(): Boolean {
                enforceAuthenticatedSession()

                return smsManager
                    .isEnabled()
            }

            override fun hasSmsPermission(): Boolean {
                enforceAuthenticatedSession()

                return smsManager
                    .hasSmsPermission()
            }

            override fun isSmsSenderConfigured():
                Boolean {
                enforceAuthenticatedSession()

                return smsManager
                    .isSenderConfigured()
            }

            override fun getSmsSender(): String {
                enforceAuthenticatedSession()

                return smsManager
                    .getSender()
            }

            override fun isSmsKeywordConfigured():
                Boolean {
                enforceAuthenticatedSession()

                return smsManager
                    .isKeywordConfigured()
            }

            override fun isSmsArmed(): Boolean {
                enforceAuthenticatedSession()

                return smsManager
                    .isArmed()
            }

            override fun getSmsSenderMaxLength():
                Int {
                enforceAuthenticatedSession()

                return smsManager
                    .getSenderMaxLength()
            }

            override fun getSmsKeywordMinLength():
                Int {
                enforceAuthenticatedSession()

                return smsManager
                    .getKeywordMinLength()
            }

            override fun getSmsKeywordMaxLength():
                Int {
                enforceAuthenticatedSession()

                return smsManager
                    .getKeywordMaxLength()
            }

            override fun grantSmsPermission(): Int {
                enforceAuthenticatedSession()

                return smsManager
                    .grantSmsPermission()
            }

            override fun releaseSmsPermissionPolicy():
                Int {
                enforceAuthenticatedSession()

                return smsManager
                    .releaseSmsPermissionPolicy()
            }

            override fun setSmsSender(
                sender: String,
            ): Int {
                enforceAuthenticatedSession()

                return smsManager
                    .setSender(
                        sender,
                    )
            }

            override fun setSmsKeyword(
                keyword: String,
            ): Int {
                enforceAuthenticatedSession()

                return smsManager
                    .setKeyword(
                        keyword,
                    )
            }

            override fun setSmsEnabled(
                enabled: Boolean,
            ): Int {
                enforceAuthenticatedSession()

                return smsManager
                    .setEnabled(
                        enabled,
                    )
            }

            override fun simulateSms(
                sender: String,
                body: String,
            ): Int {
                enforceAuthenticatedSession()

                return smsManager
                    .simulateSms(
                        sender,
                        body,
                    )
            }
        }

    override fun onBind(
        intent: Intent?,
    ): IBinder? {
        if (
            intent?.action !=
            ManagementProtocol.ACTION_BIND
        ) {
            return null
        }

        return binder
    }

    private fun createSession(
        uid: Int,
    ) {
        renewSession(
            uid,
        )
    }

    private fun renewSession(
        uid: Int,
    ) {
        authenticatedSessions[uid] =
            SystemClock.elapsedRealtime() +
                ManagementProtocol
                    .SESSION_DURATION_MS
    }

    private fun hasValidSession(
        uid: Int,
    ): Boolean {
        val expiry =
            authenticatedSessions[uid]
                ?: return false

        if (
            SystemClock.elapsedRealtime() >=
            expiry
        ) {
            authenticatedSessions.remove(
                uid,
            )

            return false
        }

        /*
         * Keep the authenticated session alive while the trusted Controller is
         * actively using the Core. The Controller explicitly logs out from
         * Activity.onStop(), so leaving Sentinel still requires the PIN on
         * return. If that logout cannot be delivered, this bounded expiry
         * remains as the fail-safe.
         */
        renewSession(
            uid,
        )

        return true
    }

    private fun enforceAuthenticatedSession() {
        enforceTrustedManagementCaller()

        val uid =
            Binder.getCallingUid()

        if (!hasValidSession(uid)) {
            throw SecurityException(
                "Authenticated management session required",
            )
        }
    }

    private fun enforceTrustedManagementCaller() {
        /*
         * Trust is established by the matching signing certificate.
         *
         * Device-policy authority is deliberately not required here because
         * Standard mode must be able to enrol/authenticate and launch setup
         * before Device Admin has been activated.
         */
        enforceTrustedCaller()
    }

    private fun getCoreAuthorityMode(): Int {
        val admin =
            DeviceAdminManager(
                this,
            )

        return when {
            admin.isDeviceOwner() ->
                ManagementProtocol
                    .AUTHORITY_DEVICE_OWNER

            admin.isAdminActive() ->
                ManagementProtocol
                    .AUTHORITY_DEVICE_ADMIN

            else ->
                ManagementProtocol
                    .AUTHORITY_NONE
        }
    }

    private fun isCoreDeviceOwner(): Boolean {
        val manager =
            getSystemService(
                DevicePolicyManager::class.java,
            ) ?: return false

        return manager.isDeviceOwnerApp(
            packageName,
        )
    }

    private fun enforceTrustedCaller() {
        val callingUid =
            Binder.getCallingUid()

        val callerPackages =
            packageManager.getPackagesForUid(
                callingUid,
            )
                ?: throw SecurityException(
                    "No package is associated with the calling UID",
                )

        val trusted =
            callerPackages.any {
                callerPackage ->
                packageManager.checkSignatures(
                    packageName,
                    callerPackage,
                ) ==
                    PackageManager.SIGNATURE_MATCH
            }

        if (!trusted) {
            throw SecurityException(
                "Management caller signing certificate does not match Core",
            )
        }
    }
}
