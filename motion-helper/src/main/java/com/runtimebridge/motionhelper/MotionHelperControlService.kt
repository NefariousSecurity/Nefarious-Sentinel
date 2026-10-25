package com.runtimebridge.motionhelper

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.runtimebridge.helper.HelperProtocol
import com.runtimebridge.helper.IMotionHelperControl

class MotionHelperControlService :
    Service() {

    private lateinit var configStore:
        MotionHelperConfigStore

    override fun onCreate() {
        super.onCreate()

        configStore =
            MotionHelperConfigStore(
                applicationContext,
            )
    }

    override fun onBind(
        intent: Intent?,
    ): IBinder? {
        if (
            intent?.action !=
            HelperProtocol
                .ACTION_BIND_MOTION_HELPER_CONTROL
        ) {
            return null
        }

        /*
         * The manifest signature permission gates binding.
         * Individual Binder methods independently verify the caller signer.
         */
        return binder
    }

    private val binder =
        object :
            IMotionHelperControl.Stub() {

            override fun getProtocolVersion():
                Int {
                enforceTrustedCaller()

                return HelperProtocol
                    .PROTOCOL_VERSION
            }

            override fun getOperationalState():
                Int {
                enforceTrustedCaller()

                val state =
                    configStore.load()

                if (!state.enabled) {
                    return HelperProtocol
                        .MOTION_STATE_DISABLED
                }

                val operational =
                    MotionHelperProbeService
                        .getOperationalState()

                if (
                    operational ==
                    HelperProtocol
                        .MOTION_STATE_DISABLED
                ) {
                    runCatching {
                        MotionHelperProbeService.start(
                            applicationContext,
                        )
                    }

                    return HelperProtocol
                        .MOTION_STATE_STARTING
                }

                if (
                    operational ==
                    HelperProtocol
                        .MOTION_STATE_SENSOR_PERMISSION_REQUIRED &&
                    MotionHelperProbeService
                        .isSensorAccessAvailable(
                            applicationContext,
                        )
                ) {
                    runCatching {
                        MotionHelperProbeService
                            .reloadConfiguration(
                                applicationContext,
                            )
                    }

                    return HelperProtocol
                        .MOTION_STATE_STARTING
                }

                return operational
            }

            override fun applyConfiguration(
                enabled: Boolean,
                sensitivity: Int,
            ): Int {
                enforceTrustedCaller()

                if (
                    sensitivity !in
                    HelperProtocol.MOTION_MIN_SENSITIVITY..
                        HelperProtocol.MOTION_MAX_SENSITIVITY
                ) {
                    return HelperProtocol
                        .MOTION_CONFIG_RESULT_INVALID_SENSITIVITY
                }

                val previous =
                    configStore.load()

                if (
                    !configStore.apply(
                        enabled,
                        sensitivity,
                    )
                ) {
                    return HelperProtocol
                        .MOTION_CONFIG_RESULT_FAILED
                }

                return try {
                    when {
                        !enabled -> {
                            MotionHelperProbeService.stop(
                                applicationContext,
                            )
                        }

                        !previous.enabled -> {
                            MotionHelperProbeService.start(
                                applicationContext,
                            )
                        }

                        else -> {
                            MotionHelperProbeService
                                .reloadConfiguration(
                                    applicationContext,
                                )
                        }
                    }

                    HelperProtocol
                        .MOTION_CONFIG_RESULT_OK
                } catch (
                    throwable: Throwable,
                ) {
                    Log.e(
                        TAG,
                        "Failed to apply Motion Helper runtime configuration",
                        throwable,
                    )

                    HelperProtocol
                        .MOTION_CONFIG_RESULT_FAILED
                }
            }
        }

    private fun enforceTrustedCaller() {
        val callingUid =
            Binder.getCallingUid()

        val callerPackages =
            packageManager
                .getPackagesForUid(
                    callingUid,
                )
                ?: throw SecurityException(
                    "No package is associated with Motion Helper caller UID",
                )

        val trusted =
            callerPackages.any {
                callerPackage ->

                packageManager
                    .checkSignatures(
                        packageName,
                        callerPackage,
                    ) ==
                    PackageManager.SIGNATURE_MATCH
            }

        if (!trusted) {
            throw SecurityException(
                "Motion Helper caller signing certificate does not match",
            )
        }
    }

    companion object {
        private const val TAG =
            "MotionHelperControl"
    }
}
