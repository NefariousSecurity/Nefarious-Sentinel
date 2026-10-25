package com.localruntime.core.management

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.localruntime.core.admin.DeviceAdminReceiver
import com.localruntime.core.admin.HardenedPolicyManager
import com.localruntime.core.log.SecLog
import com.localruntime.core.prefs.SecurePrefs
import com.localruntime.core.sms.SmsAttemptGuard
import com.localruntime.core.sms.SmsKillConfig
import com.localruntime.core.sms.SmsKillConfigStore
import com.localruntime.core.sms.SmsMatchRules
import com.localruntime.core.smsbridge.SmsHelperClient
import com.localruntime.core.wipe.WipeGateway
import com.runtimebridge.api.ManagementProtocol

/**
 * Core-owned SMS Wipe management interface used by the temporary Controller.
 *
 * SMS configuration remains in the credential-protected encrypted preferences
 * store. This preserves the accepted AFU-only SMS Wipe behaviour while the Core
 * UI remains separate from the Device Owner package.
 *
 * The configured keyword is write-only over the management bridge. Controller
 * callers can learn whether a valid keyword exists, but cannot read it back.
 *
 * Authenticated configuration changes clear SMS keyword failure/backoff state.
 */
class SmsManagementManager(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val configStore =
        SmsKillConfigStore(
            SecurePrefs.create(
                appContext,
            ),
        )

    private val attemptGuard =
        SmsAttemptGuard(
            appContext,
        )

    private val wipeGateway =
        WipeGateway(
            appContext,
        )

    private val devicePolicyManager =
        appContext.getSystemService(
            DevicePolicyManager::class.java,
        )

    private val adminComponent =
        ComponentName(
            appContext,
            DeviceAdminReceiver::class.java,
        )

    fun isEnabled(): Boolean =
        configStore
            .load()
            .enabled

    fun hasSmsPermission(): Boolean =
        SmsHelperClient
            .get(
                appContext,
            )
            .hasReceiveSmsPermission()

    fun isSenderConfigured(): Boolean =
        SmsMatchRules.isValidSender(
            configStore
                .load()
                .senderNumber,
        )

    fun getSender(): String =
        configStore
            .load()
            .senderNumber

    fun isKeywordConfigured(): Boolean =
        isValidConfiguredKeyword(
            configStore
                .load()
                .keyword,
        )

    fun isArmed(): Boolean {
        val config =
            configStore.load()

        return config.enabled &&
            prerequisitesReady(
                config,
            )
    }

    fun getSenderMaxLength(): Int =
        ManagementProtocol
            .SMS_SENDER_MAX_LENGTH

    fun getKeywordMinLength(): Int =
        SmsKillConfig
            .MIN_KEYWORD_LENGTH

    fun getKeywordMaxLength(): Int =
        SmsKillConfig
            .MAX_KEYWORD_LENGTH

    /**
     * Grant RECEIVE_SMS to the isolated SMS Helper through Device Owner policy.
     */
    fun grantSmsPermission(): Int {
        return try {
            if (!isCoreDeviceOwner()) {
                return ManagementProtocol
                    .SMS_RESULT_DEVICE_OWNER_REQUIRED
            }

            val manager =
                devicePolicyManager
                    ?: return ManagementProtocol
                        .SMS_RESULT_FAILED

            val helperPackage =
                SmsHelperClient
                    .get(
                        appContext,
                    )
                    .trustedPackageName()
                    ?: return ManagementProtocol
                        .SMS_RESULT_FAILED

            val policyUpdated =
                manager.setPermissionGrantState(
                    adminComponent,
                    helperPackage,
                    Manifest.permission.RECEIVE_SMS,
                    DevicePolicyManager
                        .PERMISSION_GRANT_STATE_GRANTED,
                )

            if (
                !policyUpdated ||
                !hasSmsPermission()
            ) {
                ManagementProtocol
                    .SMS_RESULT_FAILED
            } else {
                ManagementProtocol
                    .SMS_RESULT_OK
            }
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "Failed to grant SMS Helper RECEIVE_SMS through Device Owner policy",
                throwable,
            )

            ManagementProtocol
                .SMS_RESULT_FAILED
        }
    }

    /**
     * Return the SMS Helper RECEIVE_SMS policy to Android's default state.
     */
    fun releaseSmsPermissionPolicy(): Int {
        return try {
            if (!isCoreDeviceOwner()) {
                return ManagementProtocol
                    .SMS_RESULT_DEVICE_OWNER_REQUIRED
            }

            val manager =
                devicePolicyManager
                    ?: return ManagementProtocol
                        .SMS_RESULT_FAILED

            val helperPackage =
                SmsHelperClient
                    .get(
                        appContext,
                    )
                    .trustedPackageName()
                    ?: return ManagementProtocol
                        .SMS_RESULT_FAILED

            val policyUpdated =
                manager.setPermissionGrantState(
                    adminComponent,
                    helperPackage,
                    Manifest.permission.RECEIVE_SMS,
                    DevicePolicyManager
                        .PERMISSION_GRANT_STATE_DEFAULT,
                )

            if (policyUpdated) {
                ManagementProtocol
                    .SMS_RESULT_OK
            } else {
                ManagementProtocol
                    .SMS_RESULT_FAILED
            }
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "Failed to release SMS Helper RECEIVE_SMS Device Owner policy",
                throwable,
            )

            ManagementProtocol
                .SMS_RESULT_FAILED
        }
    }

    /**
     * Store the authorised sender in canonical international form.
     *
     * Blank remains allowed so an authenticated user can deliberately clear
     * configuration. Any non-blank value must satisfy the complete E.164 rule.
     */
    fun setSender(
        sender: String,
    ): Int {
        val trimmed =
            sender.trim()

        if (
            trimmed.length >
            ManagementProtocol
                .SMS_SENDER_MAX_LENGTH
        ) {
            return ManagementProtocol
                .SMS_RESULT_INVALID_SENDER
        }

        val canonical =
            if (trimmed.isBlank()) {
                ""
            } else {
                SmsMatchRules.normalizeSender(
                    trimmed,
                )
            }

        if (
            canonical.isNotBlank() &&
            !SmsMatchRules.isValidSender(
                canonical,
            )
        ) {
            return ManagementProtocol
                .SMS_RESULT_INVALID_SENDER
        }

        return try {
            configStore.setSenderNumber(
                canonical,
            )

            if (
                configStore
                    .load()
                    .senderNumber !=
                canonical
            ) {
                ManagementProtocol
                    .SMS_RESULT_FAILED
            } else {
                attemptGuard.clear()

                ManagementProtocol
                    .SMS_RESULT_OK
            }
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "Failed to update SMS authorised sender",
                throwable,
            )

            ManagementProtocol
                .SMS_RESULT_FAILED
        }
    }

    /**
     * Store the secret trigger keyword.
     *
     * Blank is accepted to deliberately clear the secret. Any non-blank value
     * must satisfy the complete configured length requirement.
     */
    fun setKeyword(
        keyword: String,
    ): Int {
        val trimmed =
            keyword.trim()

        if (
            trimmed.isNotBlank() &&
            trimmed.length !in
                SmsKillConfig.MIN_KEYWORD_LENGTH..
                    SmsKillConfig.MAX_KEYWORD_LENGTH
        ) {
            return ManagementProtocol
                .SMS_RESULT_INVALID_KEYWORD
        }

        return try {
            configStore.setKeyword(
                trimmed,
            )

            if (
                configStore
                    .load()
                    .keyword !=
                trimmed
            ) {
                ManagementProtocol
                    .SMS_RESULT_FAILED
            } else {
                attemptGuard.clear()

                ManagementProtocol
                    .SMS_RESULT_OK
            }
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "Failed to update SMS trigger keyword",
                throwable,
            )

            ManagementProtocol
                .SMS_RESULT_FAILED
        }
    }

    /**
     * Arm or disarm SMS Wipe.
     *
     * Disabling is always permitted. Enabling requires active wipe authority,
     * RECEIVE_SMS, a complete E.164 authorised sender, and a valid secret.
     *
     * Changing armed state is authenticated management activity and therefore
     * clears stale SMS attempt/backoff state.
     */
    fun setEnabled(
        enabled: Boolean,
    ): Int {
        return try {
            if (!enabled) {
                configStore.setEnabled(
                    false,
                )

                attemptGuard.clear()

                val lifecycleHealthy =
                    com.localruntime.core.smsbridge
                        .SmsHelperLifecycleCoordinator
                        .reconcile(
                            appContext,
                        )

                HardenedPolicyManager(
                    appContext,
                ).applyRequiredPolicies()

                return if (
                    !configStore
                        .load()
                        .enabled &&
                    lifecycleHealthy
                ) {
                    ManagementProtocol
                        .SMS_RESULT_OK
                } else {
                    ManagementProtocol
                        .SMS_RESULT_FAILED
                }
            }

            if (!wipeGateway.hasWipeAuthority()) {
                return ManagementProtocol
                    .SMS_RESULT_ADMIN_INACTIVE
            }

            if (!hasSmsPermission()) {
                return ManagementProtocol
                    .SMS_RESULT_PERMISSION_MISSING
            }

            val current =
                configStore.load()

            if (
                !SmsMatchRules.isValidSender(
                    current.senderNumber,
                )
            ) {
                return ManagementProtocol
                    .SMS_RESULT_SENDER_MISSING
            }

            if (
                !isValidConfiguredKeyword(
                    current.keyword,
                )
            ) {
                return ManagementProtocol
                    .SMS_RESULT_KEYWORD_INVALID
            }

            attemptGuard.clear()

            configStore.setEnabled(
                true,
            )

            val lifecycleHealthy =
                com.localruntime.core.smsbridge
                    .SmsHelperLifecycleCoordinator
                    .reconcile(
                        appContext,
                    )

            if (!lifecycleHealthy) {
                configStore.setEnabled(
                    false,
                )

                com.localruntime.core.smsbridge
                    .SmsHelperLifecycleCoordinator
                    .reconcile(
                        appContext,
                    )

                HardenedPolicyManager(
                    appContext,
                ).applyRequiredPolicies()

                return ManagementProtocol
                    .SMS_RESULT_FAILED
            }

            HardenedPolicyManager(
                appContext,
            ).applyRequiredPolicies()

            if (
                configStore
                    .load()
                    .enabled
            ) {
                ManagementProtocol
                    .SMS_RESULT_OK
            } else {
                ManagementProtocol
                    .SMS_RESULT_FAILED
            }
        } catch (
            throwable: Throwable,
        ) {
            SecLog.e(
                TAG,
                "Failed to change SMS Wipe enabled state",
                throwable,
            )

            ManagementProtocol
                .SMS_RESULT_FAILED
        }
    }

    /**
     * Non-destructively apply the configured sender/keyword matching rules.
     *
     * Simulation deliberately does not consume, clear, or enforce live attempt
     * backoff state.
     */
    fun simulateSms(
        sender: String,
        body: String,
    ): Int {
        val config =
            try {
                configStore.load()
            } catch (
                throwable: Throwable,
            ) {
                SecLog.e(
                    TAG,
                    "Failed to load SMS configuration for simulation",
                    throwable,
                )

                return ManagementProtocol
                    .SMS_RESULT_FAILED
            }

        if (!config.enabled) {
            return ManagementProtocol
                .SMS_SIMULATION_DISABLED
        }

        if (!wipeGateway.hasWipeAuthority()) {
            return ManagementProtocol
                .SMS_SIMULATION_ADMIN_INACTIVE
        }

        if (!hasSmsPermission()) {
            return ManagementProtocol
                .SMS_SIMULATION_PERMISSION_MISSING
        }

        if (
            !SmsMatchRules.isValidSender(
                config.senderNumber,
            )
        ) {
            return ManagementProtocol
                .SMS_SIMULATION_SENDER_MISSING
        }

        if (
            !isValidConfiguredKeyword(
                config.keyword,
            )
        ) {
            return ManagementProtocol
                .SMS_SIMULATION_KEYWORD_INVALID
        }

        if (
            !SmsMatchRules.senderMatches(
                sender,
                config.senderNumber,
            )
        ) {
            return ManagementProtocol
                .SMS_SIMULATION_NO_MATCH
        }

        if (
            !SmsMatchRules.keywordMatches(
                body,
                config.keyword,
            )
        ) {
            return ManagementProtocol
                .SMS_SIMULATION_NO_MATCH
        }

        return ManagementProtocol
            .SMS_SIMULATION_MATCH
    }

    private fun prerequisitesReady(
        config: SmsKillConfig,
    ): Boolean =
        wipeGateway.hasWipeAuthority() &&
            hasSmsPermission() &&
            SmsMatchRules.isValidSender(
                config.senderNumber,
            ) &&
            isValidConfiguredKeyword(
                config.keyword,
            )

    private fun isValidConfiguredKeyword(
        keyword: String,
    ): Boolean =
        keyword.length in
            SmsKillConfig.MIN_KEYWORD_LENGTH..
                SmsKillConfig.MAX_KEYWORD_LENGTH

    private fun isCoreDeviceOwner(): Boolean =
        devicePolicyManager
            ?.isDeviceOwnerApp(
                appContext.packageName,
            ) == true

    companion object {
        private const val TAG =
            "SmsManagement"
    }
}
