package com.localruntime.core.admin

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import com.localruntime.core.log.SecLog
import com.localruntime.core.motion.MotionSensitivityStore
import com.localruntime.core.prefs.SecurePrefs
import com.localruntime.core.motionbridge.MotionHelperClient
import com.localruntime.core.sms.SmsKillConfigStore
import com.localruntime.core.smsbridge.SmsHelperClient
import com.localruntime.core.tamper.TamperProtectionStore
import com.localruntime.core.usb.UsbMaintenanceStore

/**
 * Mandatory baseline policy for Sentinel Hardened / Device Owner mode.
 *
 * Always-enforced baseline:
 * - Safe Mode is blocked.
 * - Core uninstall is blocked.
 *
 * Feature runtime permissions are hardened only while the corresponding
 * protection is enabled:
 * - Snatch Protection -> isolated Motion Helper OTHER_SENSORS.
 * - SMS Wipe -> isolated SMS Helper RECEIVE_SMS.
 *
 * USB Debugging Protection and Wireless Debugging Protection remain
 * explicitly user-controlled and default OFF.
 *
 * In Hardened / Device Owner mode, when BOTH debugging protections are armed
 * and USB Maintenance Mode is inactive, DISALLOW_DEBUGGING_FEATURES is applied
 * as the preventive layer. Maintenance Mode is the bounded authenticated
 * exception that temporarily clears that restriction.
 */
class HardenedPolicyManager(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val dpm =
        appContext.getSystemService(
            DevicePolicyManager::class.java,
        )

    private val userManager =
        appContext.getSystemService(
            UserManager::class.java,
        )

    private val adminComponent =
        DeviceAdminReceiver.getComponentName(
            appContext,
        )

    /**
     * Reapply the complete Hardened policy.
     *
     * This method is intentionally idempotent. It is safe to call after
     * process restart, package replacement, feature changes and boot recovery.
     */
    fun applyRequiredPolicies(): Boolean {
        val manager =
            dpm
                ?: run {
                    SecLog.e(
                        TAG,
                        "HARDENED_POLICY_FAILED reason=DPM_UNAVAILABLE",
                    )

                    return false
                }

        if (
            !manager.isDeviceOwnerApp(
                appContext.packageName,
            )
        ) {
            SecLog.d(
                TAG,
                "HARDENED_POLICY_SKIPPED authority=STANDARD",
            )

            return false
        }

        return try {
            manager.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_SAFE_BOOT,
            )

            manager.setUninstallBlocked(
                adminComponent,
                appContext.packageName,
                true,
            )

            val helperUninstallBlocksHealthy =
                reconcileHelperUninstallBlocks(
                    manager,
                )

            val helperUserControlHealthy =
                reconcileHelperUserControlDisabledPackages(
                    manager,
                )

            val debuggingRestrictionHealthy =
                reconcileDebuggingRestriction(
                    manager,
                )

            val featurePermissionsHealthy =
                reconcileFeaturePermissions(
                    manager,
                )

            val safeBootBlocked =
                manager
                    .getUserRestrictions(
                        adminComponent,
                    )
                    .getBoolean(
                        UserManager.DISALLOW_SAFE_BOOT,
                        false,
                    )

            val coreUninstallBlocked =
                manager.isUninstallBlocked(
                    adminComponent,
                    appContext.packageName,
                )

            SecLog.w(
                TAG,
                "HARDENED_POLICY_APPLIED " +
                    "safeBootBlocked=$safeBootBlocked " +
                    "coreUninstallBlocked=$coreUninstallBlocked " +
                    "helperUninstallBlocksHealthy=$helperUninstallBlocksHealthy " +
                    "helperUserControlHealthy=$helperUserControlHealthy " +
                    "debuggingRestrictionHealthy=$debuggingRestrictionHealthy " +
                    "featurePermissionsHealthy=$featurePermissionsHealthy",
            )

            safeBootBlocked &&
                coreUninstallBlocked &&
                helperUninstallBlocksHealthy &&
                helperUserControlHealthy &&
                debuggingRestrictionHealthy &&
                featurePermissionsHealthy
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "HARDENED_POLICY_FAILED",
                throwable,
            )

            false
        }
    }

    /**
     * Protect installed trusted Helper packages from uninstall in Hardened
     * mode while deliberately leaving the temporary Controller removable.
     *
     * Missing Helpers are not treated as a policy failure because Core may be
     * provisioned before the Helpers. Package-install/replacement broadcasts
     * re-run this policy as soon as a trusted Helper appears.
     */
    private fun reconcileHelperUninstallBlocks(
        manager: DevicePolicyManager,
    ): Boolean {
        val helpers =
            listOf(
                "MOTION" to
                    MotionHelperClient
                        .get(
                            appContext,
                        )
                        .trustedPackageName(),
                "SMS" to
                    SmsHelperClient
                        .get(
                            appContext,
                        )
                        .trustedPackageName(),
            )

        var healthy =
            true

        helpers.forEach {
            (feature, packageName) ->

            if (packageName == null) {
                SecLog.d(
                    TAG,
                    "HARDENED_HELPER_UNINSTALL_DEFERRED helper=$feature",
                )

                return@forEach
            }

            try {
                manager.setUninstallBlocked(
                    adminComponent,
                    packageName,
                    true,
                )

                val blocked =
                    manager.isUninstallBlocked(
                        adminComponent,
                        packageName,
                    )

                SecLog.w(
                    TAG,
                    "HARDENED_HELPER_UNINSTALL " +
                        "helper=$feature " +
                        "package=$packageName " +
                        "blocked=$blocked",
                )

                if (!blocked) {
                    healthy =
                        false
                }
            } catch (
                throwable: Throwable,
            ) {
                healthy =
                    false

                SecLog.e(
                    TAG,
                    "HARDENED_HELPER_UNINSTALL_FAILED " +
                        "helper=$feature " +
                        "package=$packageName",
                    throwable,
                )
            }
        }

        return healthy
    }

    /**
     * Disable normal user control over trusted Sentinel Helpers in Hardened
     * mode.
     *
     * Android's Device Owner policy prevents the user from force-stopping or
     * clearing application data for packages in this list. Controller is
     * deliberately excluded because it remains removable/user-controlled.
     *
     * Sentinel sets only packages it independently discovers through its
     * same-signature Helper trust boundary. On API 34+, Android returns the
     * resolved policy across admins, so that resolved list must never be
     * copied back into Sentinel's own policy.
     */
    private fun reconcileHelperUserControlDisabledPackages(
        manager: DevicePolicyManager,
    ): Boolean {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.R
        ) {
            SecLog.e(
                TAG,
                "HARDENED_HELPER_USER_CONTROL_UNAVAILABLE " +
                    "sdk=${Build.VERSION.SDK_INT}",
            )

            return false
        }

        val helpers =
            listOf(
                "MOTION" to
                    MotionHelperClient
                        .get(
                            appContext,
                        )
                        .trustedPackageName(),
                "SMS" to
                    SmsHelperClient
                        .get(
                            appContext,
                        )
                        .trustedPackageName(),
            )

        helpers.forEach {
            (feature, packageName) ->

            if (packageName == null) {
                SecLog.d(
                    TAG,
                    "HARDENED_HELPER_USER_CONTROL_DEFERRED helper=$feature",
                )
            }
        }

        val discoveredPackages =
            helpers
                .mapNotNull {
                    it.second
                }
                .distinct()

        /*
         * During staged provisioning there may not yet be any Helper installed.
         * That is not a policy failure. Package-install/replacement
         * reconciliation will apply protection once trusted Helpers appear.
         */
        if (discoveredPackages.isEmpty()) {
            return true
        }

        return try {
            /*
             * API 34+ returns the resolved policy from
             * getUserControlDisabledPackages(), which may include packages
             * protected by other admins/system authorities. Do not merge that
             * resolved list into Sentinel's own policy.
             *
             * Sentinel owns only the currently discovered same-signed Helpers.
             */
            val requiredPackages =
                discoveredPackages
                    .sorted()

            manager.setUserControlDisabledPackages(
                adminComponent,
                requiredPackages,
            )

            val appliedPackages =
                manager
                    .getUserControlDisabledPackages(
                        adminComponent,
                    )
                    .toSet()

            val missingPackages =
                discoveredPackages
                    .filterNot {
                        it in appliedPackages
                    }

            val healthy =
                missingPackages.isEmpty()

            SecLog.w(
                TAG,
                "HARDENED_HELPER_USER_CONTROL " +
                    "packages=${discoveredPackages.sorted().joinToString(",")} " +
                    "healthy=$healthy",
            )

            if (!healthy) {
                SecLog.e(
                    TAG,
                    "HARDENED_HELPER_USER_CONTROL_MISSING " +
                        "packages=${missingPackages.sorted().joinToString(",")}",
                )
            }

            healthy
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "HARDENED_HELPER_USER_CONTROL_FAILED",
                throwable,
            )

            false
        }
    }

    /**
     * Reconcile only Sentinel's conditional debugging restriction.
     *
     * This is intentionally public because Tamper Proofing and USB Maintenance
     * change the inputs to this policy at runtime.
     *
     * Standard / Device Admin mode has no Device Owner authority to apply the
     * restriction, so it is treated as not applicable rather than as a policy
     * failure.
     */
    fun applyDebuggingRestrictionPolicy(): Boolean {
        val manager =
            dpm
                ?: run {
                    SecLog.e(
                        TAG,
                        "DEBUGGING_RESTRICTION_FAILED reason=DPM_UNAVAILABLE",
                    )

                    return false
                }

        if (
            !manager.isDeviceOwnerApp(
                appContext.packageName,
            )
        ) {
            SecLog.d(
                TAG,
                "DEBUGGING_RESTRICTION_SKIPPED authority=STANDARD",
            )

            return true
        }

        return try {
            reconcileDebuggingRestriction(
                manager,
            )
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "DEBUGGING_RESTRICTION_FAILED",
                throwable,
            )

            false
        }
    }

    /**
     * Apply or clear DISALLOW_DEBUGGING_FEATURES from authoritative state.
     *
     * Fail-closed rule:
     * if Maintenance state cannot be read, Maintenance is treated as inactive.
     */
    private fun reconcileDebuggingRestriction(
        manager: DevicePolicyManager,
    ): Boolean {
        val tamperStore =
            TamperProtectionStore(
                appContext,
            )

        val usbTamperEnabled =
            tamperStore
                .isUsbDebuggingProtectionEnabled()

        val wirelessTamperEnabled =
            tamperStore
                .isWirelessDebuggingProtectionEnabled()

        val maintenanceActive =
            try {
                UsbMaintenanceStore(
                    appContext,
                ).load().active
            } catch (
                throwable: Throwable,
            ) {
                SecLog.e(
                    TAG,
                    "DEBUGGING_RESTRICTION_MAINTENANCE_READ_FAILED " +
                        "failClosed=true",
                    throwable,
                )

                false
            }

        val restrictionRequired =
            usbTamperEnabled &&
                wirelessTamperEnabled &&
                !maintenanceActive

        if (restrictionRequired) {
            manager.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_DEBUGGING_FEATURES,
            )
        } else {
            manager.clearUserRestriction(
                adminComponent,
                UserManager.DISALLOW_DEBUGGING_FEATURES,
            )
        }

        val restrictionApplied =
            manager
                .getUserRestrictions(
                    adminComponent,
                )
                .getBoolean(
                    UserManager.DISALLOW_DEBUGGING_FEATURES,
                    false,
                )

        val healthy =
            restrictionApplied ==
                restrictionRequired

        SecLog.w(
            TAG,
            "DEBUGGING_RESTRICTION_POLICY " +
                "usbTamper=$usbTamperEnabled " +
                "wirelessTamper=$wirelessTamperEnabled " +
                "maintenanceActive=$maintenanceActive " +
                "required=$restrictionRequired " +
                "applied=$restrictionApplied " +
                "healthy=$healthy",
        )

        return healthy
    }

    /**
     * Keep isolated Helper runtime permissions hardened only while their
     * corresponding protection feature actually requires them.
     *
     * Motion Protection state is Device-Protected / Direct Boot-safe, so the
     * Motion Helper Sensors permission can be reconciled before first unlock.
     *
     * SMS Wipe configuration is Credential-Protected / AFU-only, so SMS policy
     * reconciliation remains deferred until the user is unlocked.
     *
     * When a feature is disabled, Device Owner permission control is released
     * back to DEFAULT rather than retaining a permanent policy-fixed grant.
     */
    private fun reconcileFeaturePermissions(
        manager: DevicePolicyManager,
    ): Boolean {
        val motionEnabled =
            try {
                MotionSensitivityStore(
                    appContext,
                ).load().enabled
            } catch (
                throwable: Throwable,
            ) {
                SecLog.e(
                    TAG,
                    "HARDENED_MOTION_STATE_READ_FAILED",
                    throwable,
                )

                return false
            }

        val motionHelperPackage =
            MotionHelperClient
                .get(
                    appContext,
                )
                .trustedPackageName()

        val motionHealthy =
            if (motionHelperPackage == null) {
                SecLog.w(
                    TAG,
                    "HARDENED_MOTION_HELPER_UNAVAILABLE required=$motionEnabled",
                )

                !motionEnabled
            } else {
                reconcileRuntimePermission(
                    manager = manager,
                    packageName =
                        motionHelperPackage,
                    permission =
                        MOTION_SENSORS_PERMISSION,
                    required =
                        motionEnabled,
                    feature =
                        "MOTION_HELPER",
                )
            }

        val unlocked =
            userManager
                ?.isUserUnlocked ==
                true

        val smsHealthy =
            if (!unlocked) {
                SecLog.d(
                    TAG,
                    "HARDENED_SMS_PERMISSION_DEFERRED userUnlocked=false",
                )

                true
            } else {
                val smsEnabled =
                    try {
                        SmsKillConfigStore(
                            SecurePrefs.create(
                                appContext,
                            ),
                        ).load().enabled
                    } catch (
                        throwable: Throwable,
                    ) {
                        SecLog.e(
                            TAG,
                            "HARDENED_SMS_STATE_READ_FAILED",
                            throwable,
                        )

                        return false
                    }

                val helperPackage =
                    SmsHelperClient
                        .get(
                            appContext,
                        )
                        .trustedPackageName()

                if (helperPackage == null) {
                    SecLog.w(
                        TAG,
                        "HARDENED_SMS_HELPER_UNAVAILABLE required=$smsEnabled",
                    )

                    !smsEnabled
                } else {
                    reconcileRuntimePermission(
                        manager = manager,
                        packageName =
                            helperPackage,
                        permission =
                            Manifest.permission.RECEIVE_SMS,
                        required =
                            smsEnabled,
                        feature =
                            "SMS_HELPER",
                    )
                }
            }

        return motionHealthy &&
            smsHealthy
    }

    /**
     * Reconcile one feature-owned runtime permission.
     *
     * GrapheneOS permits ADB shell to override a POLICY_FIXED runtime grant.
     * Android kills the affected process when that happens.
     *
     * When a required permission is found denied after restart, Sentinel first
     * clears the contradictory fixed policy with DEFAULT, then establishes
     * GRANTED again.
     *
     * When the feature is disabled, Sentinel releases Device Owner policy
     * control with DEFAULT. The current Android runtime grant is deliberately
     * left untouched so disabling a feature does not itself kill Core.
     */
    private fun reconcileRuntimePermission(
        manager: DevicePolicyManager,
        packageName: String,
        permission: String,
        required: Boolean,
        feature: String,
    ): Boolean {
        val permissionDefined =
            try {
                appContext.packageManager
                    .getPermissionInfo(
                        permission,
                        0,
                    )

                true
            } catch (
                ignored:
                    PackageManager.NameNotFoundException,
            ) {
                false
            }

        if (!permissionDefined) {
            SecLog.d(
                TAG,
                "HARDENED_PERMISSION_NOT_PRESENT " +
                    "feature=$feature " +
                    "permission=$permission",
            )

            return true
        }

        fun permissionGranted(): Boolean =
            appContext.packageManager
                .checkPermission(
                    permission,
                    packageName,
                ) ==
                PackageManager.PERMISSION_GRANTED

        var policyState =
            manager.getPermissionGrantState(
                adminComponent,
                packageName,
                permission,
            )

        var granted =
            permissionGranted()

        if (!required) {
            val released =
                if (
                    policyState !=
                    DevicePolicyManager
                        .PERMISSION_GRANT_STATE_DEFAULT
                ) {
                    manager.setPermissionGrantState(
                        adminComponent,
                        packageName,
                        permission,
                        DevicePolicyManager
                            .PERMISSION_GRANT_STATE_DEFAULT,
                    )
                } else {
                    true
                }

            policyState =
                manager.getPermissionGrantState(
                    adminComponent,
                    packageName,
                    permission,
                )

            granted =
                permissionGranted()

            val healthy =
                released &&
                    policyState ==
                    DevicePolicyManager
                        .PERMISSION_GRANT_STATE_DEFAULT

            SecLog.w(
                TAG,
                "HARDENED_PERMISSION_RELEASE " +
                    "feature=$feature " +
                    "permission=$permission " +
                    "released=$released " +
                    "policyState=$policyState " +
                    "granted=$granted " +
                    "healthy=$healthy",
            )

            return healthy
        }

        if (
            policyState ==
                DevicePolicyManager
                    .PERMISSION_GRANT_STATE_DENIED ||
            !granted
        ) {
            SecLog.w(
                TAG,
                "HARDENED_PERMISSION_REPAIR " +
                    "feature=$feature " +
                    "permission=$permission " +
                    "previousPolicyState=$policyState " +
                    "previousGranted=$granted",
            )

            manager.setPermissionGrantState(
                adminComponent,
                packageName,
                permission,
                DevicePolicyManager
                    .PERMISSION_GRANT_STATE_DEFAULT,
            )
        }

        val policyApplied =
            manager.setPermissionGrantState(
                adminComponent,
                packageName,
                permission,
                DevicePolicyManager
                    .PERMISSION_GRANT_STATE_GRANTED,
            )

        policyState =
            manager.getPermissionGrantState(
                adminComponent,
                packageName,
                permission,
            )

        granted =
            permissionGranted()

        val healthy =
            policyApplied &&
                policyState ==
                    DevicePolicyManager
                        .PERMISSION_GRANT_STATE_GRANTED &&
                granted

        SecLog.w(
            TAG,
            "HARDENED_PERMISSION_REQUIRED " +
                "feature=$feature " +
                "permission=$permission " +
                "policyApplied=$policyApplied " +
                "policyState=$policyState " +
                "granted=$granted " +
                "healthy=$healthy",
        )

        return healthy
    }

    companion object {

        private const val TAG =
            "HardenedPolicy"

        private const val MOTION_SENSORS_PERMISSION =
            "android.permission.OTHER_SENSORS"
    }
}
