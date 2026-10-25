package com.localruntime.core.smsbridge

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import com.runtimebridge.helper.HelperProtocol
import com.runtimebridge.helper.ISmsEventSink

/**
 * Private same-signature SMS Helper event endpoint.
 *
 * Manifest permission protects the service bind. Every Binder call then
 * independently verifies that the caller is signed with Core's certificate.
 */
class SmsEventSinkService :
    Service() {

    private lateinit var eventHandler:
        SmsEventHandler

    override fun onCreate() {
        super.onCreate()

        eventHandler =
            SmsEventHandler(
                applicationContext,
            )
    }

    override fun onBind(
        intent: Intent?,
    ): IBinder? {
        if (
            intent?.action !=
            HelperProtocol.ACTION_BIND_SMS_EVENTS
        ) {
            return null
        }

        return binder
    }

    private val binder =
        object :
            ISmsEventSink.Stub() {

            override fun getProtocolVersion():
                Int {
                enforceTrustedCaller()

                return HelperProtocol
                    .PROTOCOL_VERSION
            }

            override fun reportSmsMessage(
                sender: String,
                body: String,
            ): Int {
                enforceTrustedCaller()

                return eventHandler
                    .handleMessage(
                        sender,
                        body,
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
                    "No package is associated with SMS Helper caller UID",
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
                "SMS Helper caller signing certificate does not match Core",
            )
        }
    }
}
