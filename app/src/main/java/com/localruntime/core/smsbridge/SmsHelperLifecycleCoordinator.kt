package com.localruntime.core.smsbridge

import android.content.Context
import android.os.UserManager
import com.localruntime.core.log.SecLog
import com.localruntime.core.prefs.SecurePrefs
import com.localruntime.core.sms.SmsKillConfigStore

/**
 * Reconcile the SMS Helper lifecycle anchor with Core's authoritative SMS Wipe
 * enabled state.
 *
 * SMS configuration is credential-protected, so reconciliation is deferred
 * until the user's credential-encrypted storage is available.
 */
object SmsHelperLifecycleCoordinator {

    fun reconcile(
        context: Context,
    ): Boolean {
        val appContext =
            context.applicationContext

        val userManager =
            appContext.getSystemService(
                UserManager::class.java,
            )

        if (
            userManager
                ?.isUserUnlocked !=
            true
        ) {
            SecLog.d(
                TAG,
                "SMS_HELPER_LIFECYCLE_DEFERRED userUnlocked=false",
            )

            return true
        }

        val enabled =
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
                    "SMS_HELPER_LIFECYCLE_STATE_READ_FAILED",
                    throwable,
                )

                return false
            }

        val healthy =
            SmsHelperClient
                .get(
                    appContext,
                )
                .setLifecycleRequired(
                    enabled,
                )

        SecLog.w(
            TAG,
            "SMS_HELPER_LIFECYCLE " +
                "enabled=$enabled " +
                "healthy=$healthy",
        )

        return healthy
    }

    private const val TAG =
        "SmsHelperLifecycle"
}
