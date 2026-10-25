package com.localruntime.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.localruntime.core.admin.DeviceAdminManager
import com.localruntime.core.admin.HardenedPackageChangeReceiver
import com.localruntime.core.admin.HardenedPolicyManager
import com.localruntime.core.motion.MotionSensitivityStore
import com.localruntime.core.motionbridge.MotionHelperClient
import com.localruntime.core.smsbridge.SmsHelperLifecycleCoordinator
import com.localruntime.core.usb.UsbDataProbe
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * [HiltAndroidApp] generates the root Hilt component and enables dependency
 * injection throughout the application.
 *
 * This class is declared in the manifest with
 * `android:name=".RuntimeApplication"`.
 */
@HiltAndroidApp
class RuntimeApplication : Application() {

    /*
     * Held for the lifetime of the Application process.
     *
     * PACKAGE_ADDED / PACKAGE_REPLACED are protected system broadcasts.
     * Registration is dynamic because modern Android does not reliably deliver
     * these implicit package broadcasts to manifest-declared app receivers.
     */
    private val hardenedPackageChangeReceiver =
        HardenedPackageChangeReceiver()

    /*
     * SMS Wipe configuration is credential-protected.
     *
     * ACTION_USER_UNLOCKED is delivered only to runtime-registered receivers,
     * so Core uses this process-lifetime receiver to reconcile SMS permission
     * policy and the SMS Helper lifecycle anchor immediately after first unlock.
     */
    private val userUnlockReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (
                    intent.action !=
                    Intent.ACTION_USER_UNLOCKED
                ) {
                    return
                }

                HardenedPolicyManager(
                    context,
                ).applyRequiredPolicies()

                SmsHelperLifecycleCoordinator
                    .reconcile(
                        context,
                    )
            }
        }

    override fun onCreate() {
        super.onCreate()

        /*
         * Sentinel Beta destructive actions are mediated by Core's lifecycle
         * gate. Remove any stale Android-native failed-password wipe threshold
         * that could otherwise execute outside that gate.
         */
        DeviceAdminManager(
            this,
        ).clearNativeFailedPasswordWipePolicy()

        registerHardenedPackageChangeReceiver()
        registerUserUnlockReceiver()

        HardenedPolicyManager(
            this,
        ).applyRequiredPolicies()

        /*
         * If Core starts after first unlock, restore the SMS lifecycle anchor
         * immediately. During Direct Boot this safely defers until
         * ACTION_USER_UNLOCKED.
         */
        SmsHelperLifecycleCoordinator
            .reconcile(
                this,
            )

        /*
         * Synchronize Core's Direct-Boot-safe authoritative Motion
         * enabled+sensitivity state into the isolated Motion Helper.
         *
         * MotionHelperClient queues the newest state while Android completes the
         * asynchronous same-signature service binding.
         */
        val motionState =
            MotionSensitivityStore(
                this,
            ).load()

        MotionHelperClient
            .get(this)
            .applyConfiguration(
                enabled =
                    motionState.enabled,
                sensitivity =
                    motionState.sensitivity,
            )

        UsbDataProbe.register(this)
        createNotificationChannels()
    }

    private fun registerHardenedPackageChangeReceiver() {
        val filter =
            IntentFilter().apply {
                addAction(
                    Intent.ACTION_PACKAGE_ADDED,
                )

                addAction(
                    Intent.ACTION_PACKAGE_REPLACED,
                )

                addDataScheme(
                    "package",
                )
            }

        ContextCompat.registerReceiver(
            this,
            hardenedPackageChangeReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun registerUserUnlockReceiver() {
        ContextCompat.registerReceiver(
            this,
            userUnlockReceiver,
            IntentFilter(
                Intent.ACTION_USER_UNLOCKED,
            ),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val notificationManager =
            getSystemService(NotificationManager::class.java)
                ?: return

        runCatching {
            notificationManager.deleteNotificationChannel(
                "runtime.channel.legacy.1",
            )
        }

        runCatching {
            notificationManager.deleteNotificationChannel(
                "runtime.channel.legacy.2",
            )
        }

        val usbChannel = NotificationChannel(
            CHANNEL_USB_KILL,
            getString(
                R.string.usb_channel_name,
            ),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description =
                getString(
                    R.string.usb_channel_desc,
                )

            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }

        notificationManager.createNotificationChannel(
            usbChannel,
        )
    }

    companion object {
        const val CHANNEL_USB_KILL =
            "runtime.channel.primary"
    }
}
