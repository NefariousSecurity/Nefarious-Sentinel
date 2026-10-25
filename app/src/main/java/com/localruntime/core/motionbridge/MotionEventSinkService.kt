package com.localruntime.core.motionbridge

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import com.runtimebridge.helper.HelperProtocol
import com.runtimebridge.helper.IMotionEventSink

/**
 * Private same-signature Motion Helper event endpoint.
 */
class MotionEventSinkService :
    Service() {

    private lateinit var candidateHandler:
        MotionCandidateHandler

    override fun onCreate() {
        super.onCreate()

        candidateHandler =
            MotionCandidateHandler(
                applicationContext,
            )
    }

    override fun onBind(
        intent: Intent?,
    ): IBinder? {
        if (
            intent?.action !=
            HelperProtocol.ACTION_BIND_MOTION_EVENTS
        ) {
            return null
        }

        /*
         * Match CoreManagementService's established pattern:
         *
         * - manifest permission gates the bind;
         * - Binder methods perform the explicit signing-certificate check.
         */
        return binder
    }

    private val binder =
        object :
            IMotionEventSink.Stub() {

            override fun getProtocolVersion():
                Int {
                enforceTrustedCaller()

                return HelperProtocol
                    .PROTOCOL_VERSION
            }

            override fun reportMotionCandidate(
                eventElapsedRealtimeMs: Long,
            ): Int {
                enforceTrustedCaller()

                return candidateHandler
                    .handleCandidate(
                        eventElapsedRealtimeMs,
                    )
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
                "Motion Helper caller signing certificate does not match Core",
            )
        }
    }
}
