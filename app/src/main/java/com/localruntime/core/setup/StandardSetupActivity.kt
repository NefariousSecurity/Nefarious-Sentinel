package com.localruntime.core.setup

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import com.localruntime.core.R
import com.localruntime.core.admin.DeviceAdminReceiver
import com.localruntime.core.management.SetupHandoffStore
import com.localruntime.core.motionbridge.MotionHelperClient
import com.localruntime.core.smsbridge.SmsHelperClient
import com.runtimebridge.api.ManagementProtocol

/**
 * Small system-UI relay used only by Standard mode.
 *
 * The headless Core keeps no launcher Activity. The trusted same-signed
 * Controller starts this Activity through neutral runtime-bridge actions.
 *
 * Android itself presents the Device Admin activation or Core-owned runtime
 * permission UI. The Activity finishes immediately after that system flow.
 */
class StandardSetupActivity : Activity() {

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(
            savedInstanceState,
        )

        when (intent?.action) {
            ManagementProtocol.ACTION_SETUP_ADMIN ->
                requestDeviceAdmin()

            ManagementProtocol.ACTION_REQUEST_SMS_PERMISSION ->
                requestSmsPermission()

            ManagementProtocol.ACTION_REQUEST_SENSOR_PERMISSION ->
                requestSensorPermission()

            else ->
                finish()
        }
    }

    private fun requestDeviceAdmin() {
        val manager =
            getSystemService(
                DevicePolicyManager::class.java,
            )

        if (manager == null) {
            finish()
            return
        }

        val component =
            ComponentName(
                this,
                DeviceAdminReceiver::class.java,
            )

        if (manager.isAdminActive(component)) {
            finish()
            return
        }

        val setupIntent =
            Intent(
                DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN,
            ).apply {
                putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    component,
                )

                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(
                        R.string.standard_admin_explanation,
                    ),
                )
            }

        startActivityForResult(
            setupIntent,
            REQUEST_DEVICE_ADMIN,
        )
    }

    private fun requestSmsPermission() {
        val handoffToken =
            intent.getStringExtra(
                ManagementProtocol
                    .EXTRA_SETUP_HANDOFF_TOKEN,
            )

        if (
            !SetupHandoffStore.isPending(
                handoffToken,
                ManagementProtocol
                    .ACTION_REQUEST_SMS_PERMISSION,
            )
        ) {
            finish()
            return
        }

        val helperIntent =
            SmsHelperClient
                .get(
                    applicationContext,
                )
                .buildSmsPermissionIntent()

        if (helperIntent == null) {
            completeSmsPermissionHandoff(
                handoffToken,
            )

            finish()
            return
        }

        startActivityForResult(
            helperIntent,
            REQUEST_SMS_PERMISSION,
        )
    }

    private fun requestSensorPermission() {
        val handoffToken =
            intent.getStringExtra(
                ManagementProtocol
                    .EXTRA_SETUP_HANDOFF_TOKEN,
            )

        if (
            !SetupHandoffStore.isPending(
                handoffToken,
                ManagementProtocol
                    .ACTION_REQUEST_SENSOR_PERMISSION,
            )
        ) {
            finish()
            return
        }

        /*
         * OTHER_SENSORS now belongs to the isolated Motion Helper UID.
         *
         * Core keeps ownership of the authenticated one-time handoff, discovers
         * the unique same-signed Helper permission Activity, and launches it
         * explicitly. Android's runtime permission dialog is therefore
         * attributed to the Helper package rather than the Device Owner Core.
         */
        val helperIntent =
            MotionHelperClient
                .get(
                    applicationContext,
                )
                .buildSensorPermissionIntent()

        if (helperIntent == null) {
            /*
             * Do not strand the Controller in a consumed authenticated session
             * if the Helper is unavailable. The refreshed Motion state will
             * still report that monitoring is unavailable.
             */
            completeSensorPermissionHandoff(
                handoffToken,
            )

            finish()
            return
        }

        startActivityForResult(
            helperIntent,
            REQUEST_SENSOR_PERMISSION,
        )
    }

    @Deprecated(
        "Deprecated in Android framework; retained for minSdk 26 compatibility.",
    )
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data,
        )

        if (
            requestCode ==
            REQUEST_DEVICE_ADMIN
        ) {
            finish()
            return
        }

        if (
            requestCode ==
            REQUEST_SENSOR_PERMISSION
        ) {
            completeSensorPermissionHandoff(
                intent.getStringExtra(
                    ManagementProtocol
                        .EXTRA_SETUP_HANDOFF_TOKEN,
                ),
            )

            finish()
            return
        }

        if (
            requestCode ==
            REQUEST_SMS_PERMISSION
        ) {
            completeSmsPermissionHandoff(
                intent.getStringExtra(
                    ManagementProtocol
                        .EXTRA_SETUP_HANDOFF_TOKEN,
                ),
            )

            finish()
        }
    }

    private fun completeSmsPermissionHandoff(
        token: String?,
    ) {
        SetupHandoffStore.complete(
            token,
            ManagementProtocol
                .ACTION_REQUEST_SMS_PERMISSION,
        )
    }

    private fun completeSensorPermissionHandoff(
        token: String?,
    ) {
        SetupHandoffStore.complete(
            token,
            ManagementProtocol
                .ACTION_REQUEST_SENSOR_PERMISSION,
        )
    }

    private companion object {
        private const val REQUEST_DEVICE_ADMIN =
            1001

        private const val REQUEST_SMS_PERMISSION =
            1002

        private const val REQUEST_SENSOR_PERMISSION =
            1003
    }
}
