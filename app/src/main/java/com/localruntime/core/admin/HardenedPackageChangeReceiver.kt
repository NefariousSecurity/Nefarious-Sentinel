package com.localruntime.core.admin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.localruntime.core.log.SecLog
import com.localruntime.core.motionbridge.MotionHelperClient
import com.localruntime.core.smsbridge.SmsHelperClient

/**
 * Reconcile mandatory Hardened policy when a trusted Helper package is added
 * or replaced after Core has already been provisioned.
 */
class HardenedPackageChangeReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action =
            intent.action
                ?: return

        if (
            action !=
            Intent.ACTION_PACKAGE_ADDED &&
            action !=
            Intent.ACTION_PACKAGE_REPLACED
        ) {
            return
        }

        /*
         * Android emits PACKAGE_ADDED with EXTRA_REPLACING=true as part of an
         * APK update, followed by PACKAGE_REPLACED.
         *
         * Ignore the replacement-flavoured PACKAGE_ADDED so Hardened policy is
         * reconciled once per update rather than twice.
         *
         * A genuinely new Helper installation still arrives as PACKAGE_ADDED
         * with EXTRA_REPLACING=false and is handled normally.
         */
        if (
            action ==
            Intent.ACTION_PACKAGE_ADDED &&
            intent.getBooleanExtra(
                Intent.EXTRA_REPLACING,
                false,
            )
        ) {
            return
        }

        val changedPackage =
            intent.data
                ?.schemeSpecificPart
                ?: return

        val appContext =
            context.applicationContext

        val trustedHelpers =
            setOfNotNull(
                MotionHelperClient
                    .get(
                        appContext,
                    )
                    .trustedPackageName(),
                SmsHelperClient
                    .get(
                        appContext,
                    )
                    .trustedPackageName(),
            )

        if (
            changedPackage !in
            trustedHelpers
        ) {
            return
        }

        val healthy =
            HardenedPolicyManager(
                appContext,
            ).applyRequiredPolicies()

        SecLog.w(
            TAG,
            "HARDENED_HELPER_PACKAGE_CHANGE " +
                "action=$action " +
                "package=$changedPackage " +
                "healthy=$healthy",
        )
    }

    private companion object {
        private const val TAG =
            "HardenedPackageChange"
    }
}
