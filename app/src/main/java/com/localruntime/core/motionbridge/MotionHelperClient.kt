package com.localruntime.core.motionbridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.localruntime.core.admin.HardenedPolicyManager
import com.runtimebridge.helper.HelperProtocol
import com.runtimebridge.helper.IMotionHelperControl

/**
 * Core-side connection to the isolated Motion Helper.
 *
 * Core remains authoritative for configuration. If Android has not completed
 * the asynchronous service bind yet, only the newest desired configuration is
 * retained and delivered when the trusted helper connects.
 */
class MotionHelperClient private constructor(
    context: Context,
) {

    enum class ConfigurationDispatch {
        APPLIED,
        QUEUED,
        UNAVAILABLE,
        INVALID,
        FAILED,
    }

    private data class PendingConfiguration(
        val enabled: Boolean,
        val sensitivity: Int,
    )

    private val appContext =
        context.applicationContext

    private val lock =
        Any()

    @Volatile
    private var remote:
        IMotionHelperControl? =
        null

    /*
     * True while this ServiceConnection is registered with Android.
     *
     * onServiceDisconnected() does not clear this because Android keeps the
     * binding and may reconnect the service automatically.
     */
    @Volatile
    private var bound =
        false

    @Volatile
    private var pendingConfiguration:
        PendingConfiguration? =
        null

    private val connection =
        object : ServiceConnection {

            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                val candidate =
                    IMotionHelperControl.Stub
                        .asInterface(
                            service,
                        )

                try {
                    if (
                        candidate.getProtocolVersion() !=
                        HelperProtocol.PROTOCOL_VERSION
                    ) {
                        Log.e(
                            TAG,
                            "Motion Helper protocol mismatch",
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
                        "Connected to trusted Motion Helper",
                    )

                    deliverPendingConfiguration(
                        candidate,
                    )
                } catch (
                    throwable: Throwable,
                ) {
                    Log.e(
                        TAG,
                        "Motion Helper handshake failed",
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
                /*
                 * Android retains the ServiceConnection after an unexpected
                 * service-process death and will reconnect it automatically.
                 */
                remote =
                    null

                Log.w(
                    TAG,
                    "Motion Helper service disconnected",
                )

                /*
                 * A runtime-permission revoke kills the Helper process. In
                 * Hardened / Device Owner mode, immediately reconcile policy
                 * so a required permission whose actual runtime grant was
                 * overridden can be repaired without polling.
                 *
                 * Standard mode safely no-ops inside HardenedPolicyManager.
                 */
                try {
                    val healthy =
                        HardenedPolicyManager(
                            appContext,
                        ).applyRequiredPolicies()

                    Log.w(
                        TAG,
                        "Motion Helper disconnect policy reconciliation " +
                            "healthy=$healthy",
                    )
                } catch (
                    throwable: Throwable,
                ) {
                    Log.e(
                        TAG,
                        "Motion Helper disconnect policy reconciliation failed",
                        throwable,
                    )
                }
            }

            override fun onBindingDied(
                name: ComponentName?,
            ) {
                Log.w(
                    TAG,
                    "Motion Helper binding died",
                )

                clearBinding(
                    unbind = true,
                )

                startBinding()
            }

            override fun onNullBinding(
                name: ComponentName?,
            ) {
                Log.e(
                    TAG,
                    "Motion Helper returned null binding",
                )

                clearBinding(
                    unbind = true,
                )
            }
        }

    /**
     * Ensure the trusted helper is either connected or being connected.
     *
     * Returns false only when no unique trusted helper can be discovered or
     * Android rejects the bind request.
     */
    fun startBinding():
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

            val component =
                findTrustedHelper()
                    ?: run {
                        Log.w(
                            TAG,
                            "No unique trusted Motion Helper found",
                        )

                        return false
                    }

            val intent =
                Intent(
                    HelperProtocol
                        .ACTION_BIND_MOTION_HELPER_CONTROL,
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
                        "Failed to bind Motion Helper",
                        throwable,
                    )

                    false
                }

            if (!bound) {
                Log.w(
                    TAG,
                    "Motion Helper bind was not accepted",
                )
            }

            return bound
        }
    }

    fun isTrustedHelperAvailable():
        Boolean =
        trustedPackageName() !=
            null

    /**
     * Return the uniquely trusted Motion Helper package discovered through the
     * same-signature control-service boundary.
     *
     * Core deliberately embeds no Helper package name.
     */
    fun trustedPackageName():
        String? =
        findTrustedHelper()
            ?.packageName

    fun getOperationalState():
        Int? {
        val current =
            remote

        if (current == null) {
            startBinding()
            return null
        }

        return try {
            current.getOperationalState()
        } catch (
            throwable: Throwable,
        ) {
            Log.e(
                TAG,
                "Motion Helper operational-state query failed",
                throwable,
            )

            remote =
                null

            null
        }
    }

    /**
     * Deliver or queue the latest authoritative Core configuration.
     *
     * Multiple configuration changes while Android is connecting collapse into
     * one newest state. This avoids replaying stale sensitivity/enabled values.
     */
    fun applyConfiguration(
        enabled: Boolean,
        sensitivity: Int,
    ): ConfigurationDispatch {
        if (
            sensitivity !in
            HelperProtocol.MOTION_MIN_SENSITIVITY..
                HelperProtocol.MOTION_MAX_SENSITIVITY
        ) {
            return ConfigurationDispatch.INVALID
        }

        val desired =
            PendingConfiguration(
                enabled = enabled,
                sensitivity = sensitivity,
            )

        pendingConfiguration =
            desired

        val current =
            remote

        if (current == null) {
            return if (
                startBinding()
            ) {
                ConfigurationDispatch.QUEUED
            } else {
                ConfigurationDispatch.UNAVAILABLE
            }
        }

        return deliverConfiguration(
            current,
            desired,
        )
    }

    /**
     * Check the Sensors runtime permission on the isolated Helper UID.
     *
     * Core deliberately does not use its own OTHER_SENSORS grant here. The
     * trusted Helper package discovered through the control-service boundary is
     * the package that owns the accelerometer classifier.
     */
    fun isSensorAccessAvailable():
        Boolean {
        val helper =
            findTrustedHelper()
                ?: return false

        return appContext.packageManager
            .checkPermission(
                PERMISSION_OTHER_SENSORS,
                helper.packageName,
            ) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Build an explicit Intent for the trusted Helper-owned permission relay.
     *
     * The Activity must:
     * - live in the same package as the uniquely trusted Helper service,
     * - be exported,
     * - require the Helper control signature permission, and
     * - carry the same signing certificate as Core.
     *
     * No Helper package name is embedded in Core.
     */
    @Suppress("DEPRECATION")
    fun buildSensorPermissionIntent():
        Intent? {
        val helper =
            findTrustedHelper()
                ?: return null

        val discoveryIntent =
            Intent(
                HelperProtocol
                    .ACTION_REQUEST_MOTION_SENSOR_PERMISSION,
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
                            .PERMISSION_MOTION_HELPER_CONTROL
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
                "Trusted Motion permission Activity candidate count=${trusted.size}",
            )

            return null
        }

        return Intent(
            HelperProtocol
                .ACTION_REQUEST_MOTION_SENSOR_PERMISSION,
        ).setComponent(
            trusted.single(),
        )
    }

    private fun deliverPendingConfiguration(
        current: IMotionHelperControl,
    ) {
        val desired =
            pendingConfiguration
                ?: return

        val result =
            deliverConfiguration(
                current,
                desired,
            )

        Log.i(
            TAG,
            "Pending Motion Helper configuration result=$result",
        )
    }

    private fun deliverConfiguration(
        current: IMotionHelperControl,
        desired: PendingConfiguration,
    ): ConfigurationDispatch {
        return try {
            when (
                current.applyConfiguration(
                    desired.enabled,
                    desired.sensitivity,
                )
            ) {
                HelperProtocol
                    .MOTION_CONFIG_RESULT_OK -> {
                    /*
                     * Do not erase a newer value that may have been queued while
                     * this Binder transaction was in progress.
                     */
                    if (
                        pendingConfiguration ==
                        desired
                    ) {
                        pendingConfiguration =
                            null
                    }

                    ConfigurationDispatch.APPLIED
                }

                HelperProtocol
                    .MOTION_CONFIG_RESULT_INVALID_SENSITIVITY ->
                    ConfigurationDispatch.INVALID

                else ->
                    ConfigurationDispatch.FAILED
            }
        } catch (
            throwable: Throwable,
        ) {
            Log.e(
                TAG,
                "Motion Helper configuration delivery failed",
                throwable,
            )

            remote =
                null

            ConfigurationDispatch.FAILED
        }
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
                    .ACTION_BIND_MOTION_HELPER_CONTROL,
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
                            .PERMISSION_MOTION_HELPER_CONTROL
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
                "Trusted Motion Helper candidate count=${trusted.size}",
            )

            return null
        }

        return trusted.single()
    }

    companion object {
        private const val PERMISSION_OTHER_SENSORS =
            "android.permission.OTHER_SENSORS"

        private const val TAG =
            "MotionHelperClient"

        @Volatile
        private var instance:
            MotionHelperClient? =
            null

        fun get(
            context: Context,
        ): MotionHelperClient =
            instance
                ?: synchronized(
                    this,
                ) {
                    instance
                        ?: MotionHelperClient(
                            context.applicationContext,
                        ).also {
                            instance =
                                it
                        }
                }
    }
}
