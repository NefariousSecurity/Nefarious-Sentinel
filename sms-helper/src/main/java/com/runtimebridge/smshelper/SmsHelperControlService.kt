package com.runtimebridge.smshelper

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import com.runtimebridge.helper.HelperProtocol
import com.runtimebridge.helper.ISmsHelperControl

/**
 * Minimal lifecycle anchor for the isolated SMS Helper.
 *
 * This service owns no sender, keyword, SMS matching, Device Admin, Device
 * Owner, or wipe authority. Core binds to it only while SMS Wipe is enabled so
 * Android can restore the Helper process after an unexpected process death or
 * user Force Stop.
 */
class SmsHelperControlService :
    Service() {

    override fun onBind(
        intent: Intent?,
    ): IBinder? {
        if (
            intent?.action !=
            HelperProtocol
                .ACTION_BIND_SMS_HELPER_CONTROL
        ) {
            return null
        }

        return binder
    }

    private val binder =
        object :
            ISmsHelperControl.Stub() {

            override fun getProtocolVersion():
                Int {
                enforceTrustedCaller()

                return HelperProtocol
                    .PROTOCOL_VERSION
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
                "SMS Helper caller signing certificate does not match",
            )
        }
    }
}
