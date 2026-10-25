package com.localruntime.core.smsbridge

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.localruntime.core.admin.HardenedPolicyManager
import com.runtimebridge.helper.HelperProtocol
import com.runtimebridge.helper.ISmsHelperControl

/**
 * Core-side connection to the isolated SMS Helper.
 *
 * The control service is lifecycle-only. Core retains all SMS configuration,
 * matching and wipe authority.
 *
 * No SMS Helper package name is embedded in Core. A Helper is accepted only
 * when exactly one exported service:
 *
 * - advertises the neutral SMS Helper control action;
 * - requires SMS_HELPER_CONTROL_V1;
 * - carries the same signing certificate as Core.
 */
class SmsHelperClient private constructor(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val lock =
        Any()

    @Volatile
    private var remote:
        ISmsHelperControl? =
        null

    /*
     * True while this ServiceConnection is registered with Android.
     *
     * onServiceDisconnected() deliberately does not clear this because Android
     * retains the binding and may reconnect the service automatically.
     */
    @Volatile
    private var bound =
        false

    @Volatile
    private var lifecycleRequired =
        false

    private val connection =
        object : ServiceConnection {

            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                val candidate =
                    ISmsHelperControl.Stub
                        .asInterface(
                            service,
                        )

                try {
                    if (
                        candidate == null ||
                        candidate.getProtocolVersion() !=
                        HelperProtocol.PROTOCOL_VERSION
                    ) {
                        Log.e(
                            TAG,
                            "SMS Helper protocol mismatch",
                        )

                        clearBinding(
                            unbind = true,
                        )

                        return
                    }

                    remote =
                        candidate

                    Log.i(
                        TAG,
                        "Connected to trusted SMS Helper",
                    )
                } catch (
                    throwable: Throwable,
                ) {
                    Log.e(
                        TAG,
                        "SMS Helper handshake failed",
                        throwable,
                    )

                    clearBinding(
                        unbind = true,
                    )
                }
            }

            override fun onServiceDisconnected(
                name: ComponentName?,
            ) {
                remote =
                    null

                Log.w(
                    TAG,
                    "SMS Helper service disconnected",
                )

                /*
                 * Required SMS permission policy is authoritative in Hardened
                 * mode. If the Helper process dies because the runtime grant
                 * was externally revoked, immediately reconcile policy rather
                 * than waiting for a later boot or feature transition.
                 */
                try {
                    val healthy =
                        HardenedPolicyManager(
                            appContext,
                        ).applyRequiredPolicies()

                    Log.w(
                        TAG,
                        "SMS Helper disconnect policy reconciliation " +
                            "healthy=$healthy",
                    )
                } catch (
                    throwable: Throwable,
                ) {
                    Log.e(
                        TAG,
                        "SMS Helper disconnect policy reconciliation failed",
                        throwable,
                    )
                }
            }

            override fun onBindingDied(
                name: ComponentName?,
            ) {
                Log.w(
                    TAG,
                    "SMS Helper binding died",
                )

                clearBinding(
                    unbind = true,
                )

                if (isLifecycleRequired()) {
                    startBinding()
                }
            }

            override fun onNullBinding(
                name: ComponentName?,
            ) {
                Log.e(
                    TAG,
                    "SMS Helper returned null binding",
                )

                clearBinding(
                    unbind = true,
                )
            }
        }

    /**
     * Keep the lifecycle anchor bound only while SMS Wipe is enabled.
     */
    fun setLifecycleRequired(
        required: Boolean,
    ): Boolean {
        synchronized(
            lock,
        ) {
            lifecycleRequired =
                required
        }

        if (!required) {
            clearBinding(
                unbind = true,
            )

            return true
        }

        return startBinding()
    }

    fun trustedPackageName():
        String? =
        findTrustedHelper()
            ?.packageName

    fun hasReceiveSmsPermission():
        Boolean {
        val helperPackage =
            trustedPackageName()
                ?: return false

        return appContext.packageManager
            .checkPermission(
                Manifest.permission.RECEIVE_SMS,
                helperPackage,
            ) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Build an explicit permission-relay Intent inside the same trusted Helper
     * package as the lifecycle control service.
     */
    @Suppress("DEPRECATION")
    fun buildSmsPermissionIntent():
        Intent? {
        val helper =
            findTrustedHelper()
                ?: return null

        val discoveryIntent =
            Intent(
                HelperProtocol
                    .ACTION_REQUEST_SMS_PERMISSION,
            ).setPackage(
                helper.packageName,
            )

        val flags =
            PackageManager.MATCH_DIRECT_BOOT_AWARE or
                PackageManager.MATCH_DIRECT_BOOT_UNAWARE

        val trusted =
            appContext.packageManager
                .queryIntentActivities(
                    discoveryIntent,
                    flags,
                )
                .mapNotNull {
                    it.activityInfo
                }
                .filter {
                    it.packageName ==
                        helper.packageName
                }
                .filter {
                    it.exported
                }
                .filter {
                    it.permission ==
                        HelperProtocol
                            .PERMISSION_SMS_HELPER_CONTROL
                }
                .filter {
                    appContext.packageManager
                        .checkSignatures(
                            appContext.packageName,
                            it.packageName,
                        ) ==
                        PackageManager.SIGNATURE_MATCH
                }
                .map {
                    ComponentName(
                        it.packageName,
                        it.name,
                    )
                }
                .distinct()

        if (
            trusted.size !=
            1
        ) {
            Log.w(
                TAG,
                "Trusted SMS permission Activity candidate count=${trusted.size}",
            )

            return null
        }

        return Intent(
            HelperProtocol
                .ACTION_REQUEST_SMS_PERMISSION,
        ).setComponent(
            trusted.single(),
        )
    }

    private fun startBinding():
        Boolean {
        synchronized(
            lock,
        ) {
            if (
                remote != null ||
                bound
            ) {
                return true
            }

            if (!lifecycleRequired) {
                return true
            }

            val component =
                findTrustedHelper()
                    ?: run {
                        Log.w(
                            TAG,
                            "No unique trusted SMS Helper found",
                        )

                        return false
                    }

            val intent =
                Intent(
                    HelperProtocol
                        .ACTION_BIND_SMS_HELPER_CONTROL,
                ).setComponent(
                    component,
                )

            bound =
                try {
                    appContext.bindService(
                        intent,
                        connection,
                        Context.BIND_AUTO_CREATE,
                    )
                } catch (
                    throwable: Throwable,
                ) {
                    Log.e(
                        TAG,
                        "Failed to bind SMS Helper",
                        throwable,
                    )

                    false
                }

            if (!bound) {
                Log.w(
                    TAG,
                    "SMS Helper bind was not accepted",
                )
            }

            return bound
        }
    }

    private fun isLifecycleRequired():
        Boolean =
        synchronized(
            lock,
        ) {
            lifecycleRequired
        }

    private fun clearBinding(
        unbind: Boolean,
    ) {
        synchronized(
            lock,
        ) {
            remote =
                null

            if (
                unbind &&
                bound
            ) {
                runCatching {
                    appContext.unbindService(
                        connection,
                    )
                }
            }

            bound =
                false
        }
    }

    @Suppress("DEPRECATION")
    private fun findTrustedHelper():
        ComponentName? {
        val intent =
            Intent(
                HelperProtocol
                    .ACTION_BIND_SMS_HELPER_CONTROL,
            )

        val flags =
            PackageManager.MATCH_DIRECT_BOOT_AWARE or
                PackageManager.MATCH_DIRECT_BOOT_UNAWARE

        val trusted =
            appContext.packageManager
                .queryIntentServices(
                    intent,
                    flags,
                )
                .mapNotNull {
                    it.serviceInfo
                }
                .filter {
                    it.exported
                }
                .filter {
                    it.permission ==
                        HelperProtocol
                            .PERMISSION_SMS_HELPER_CONTROL
                }
                .filter {
                    appContext.packageManager
                        .checkSignatures(
                            appContext.packageName,
                            it.packageName,
                        ) ==
                        PackageManager.SIGNATURE_MATCH
                }
                .map {
                    ComponentName(
                        it.packageName,
                        it.name,
                    )
                }
                .distinct()

        if (
            trusted.size !=
            1
        ) {
            Log.w(
                TAG,
                "Trusted SMS Helper candidate count=${trusted.size}",
            )

            return null
        }

        return trusted.single()
    }

    companion object {
        private const val TAG =
            "SmsHelperClient"

        @Volatile
        private var instance:
            SmsHelperClient? =
            null

        fun get(
            context: Context,
        ): SmsHelperClient =
            instance
                ?: synchronized(
                    this,
                ) {
                    instance
                        ?: SmsHelperClient(
                            context.applicationContext,
                        ).also {
                            instance =
                                it
                        }
                }
    }
}
