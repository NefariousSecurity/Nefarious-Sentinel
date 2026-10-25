package com.runtimebridge.smshelper

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * Minimal SMS Helper-owned runtime-permission relay.
 *
 * There is no launcher entry. Core discovers this Activity through a neutral
 * action, requires the same signing certificate, and launches it explicitly.
 *
 * Because requestPermissions() runs here, Android grants RECEIVE_SMS to the
 * isolated SMS Helper UID rather than to the persistent Core UID.
 */
class SmsPermissionActivity :
    Activity() {

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(
            savedInstanceState,
        )

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECEIVE_SMS,
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            setResult(
                RESULT_OK,
            )

            finish()
            return
        }

        requestPermissions(
            arrayOf(
                Manifest.permission.RECEIVE_SMS,
            ),
            REQUEST_SMS_PERMISSION,
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults,
        )

        if (
            requestCode !=
            REQUEST_SMS_PERMISSION
        ) {
            return
        }

        val granted =
            grantResults.isNotEmpty() &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED

        setResult(
            if (granted) {
                RESULT_OK
            } else {
                RESULT_CANCELED
            },
        )

        finish()
    }

    private companion object {
        private const val REQUEST_SMS_PERMISSION =
            2101
    }
}
