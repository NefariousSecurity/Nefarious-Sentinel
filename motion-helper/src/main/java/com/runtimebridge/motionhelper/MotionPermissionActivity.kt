package com.runtimebridge.motionhelper

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.ContextCompat

/**
 * Minimal Helper-owned runtime-permission relay.
 *
 * There is no launcher entry. Core discovers this Activity through a neutral
 * action, requires the same signing certificate, and launches it explicitly.
 *
 * Because requestPermissions() runs here, Android grants OTHER_SENSORS to the
 * isolated Motion Helper UID rather than to the persistent Device Owner Core.
 */
class MotionPermissionActivity : Activity() {

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(
            savedInstanceState,
        )

        val permissionDefined =
            try {
                packageManager.getPermissionInfo(
                    PERMISSION_OTHER_SENSORS,
                    0,
                )

                true
            } catch (
                ignored:
                    PackageManager.NameNotFoundException,
            ) {
                false
            }

        /*
         * GrapheneOS defines OTHER_SENSORS as a dangerous runtime permission.
         * On platforms that do not define it there is no permission dialog to
         * present, so return successfully and let normal SensorManager rules
         * determine availability.
         */
        if (!permissionDefined) {
            setResult(
                RESULT_OK,
            )

            finish()
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                PERMISSION_OTHER_SENSORS,
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
                PERMISSION_OTHER_SENSORS,
            ),
            REQUEST_SENSOR_PERMISSION,
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
            REQUEST_SENSOR_PERMISSION
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
        private const val REQUEST_SENSOR_PERMISSION =
            2001

        private const val PERMISSION_OTHER_SENSORS =
            "android.permission.OTHER_SENSORS"
    }
}
