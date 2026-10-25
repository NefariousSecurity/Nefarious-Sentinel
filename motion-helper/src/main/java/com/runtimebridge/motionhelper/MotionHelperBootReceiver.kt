package com.runtimebridge.motionhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MotionHelperBootReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action =
            intent.action

        if (
            action !=
            Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action !=
            Intent.ACTION_BOOT_COMPLETED &&
            action !=
            Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val state =
            MotionHelperConfigStore(
                context.applicationContext,
            ).load()

        if (!state.enabled) {
            return
        }

        runCatching {
            MotionHelperProbeService.start(
                context.applicationContext,
            )
        }.onFailure {
            Log.e(
                TAG,
                "Motion Helper boot recovery failed",
                it,
            )
        }
    }

    companion object {
        private const val TAG =
            "MotionHelperBoot"
    }
}
