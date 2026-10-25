package com.runtimebridge.motionhelper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.runtimebridge.helper.HelperProtocol
import com.runtimebridge.helper.IMotionEventSink

class MotionEventClient(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    @Volatile
    private var sink:
        IMotionEventSink? =
        null

    @Volatile
    private var bound =
        false

    @Volatile
    private var pendingCandidateElapsedMs:
        Long? =
        null

    private val connection =
        object : ServiceConnection {

            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                val candidate =
                    IMotionEventSink.Stub.asInterface(
                        service,
                    )

                try {
                    if (
                        candidate.getProtocolVersion() !=
                        HelperProtocol.PROTOCOL_VERSION
                    ) {
                        Log.e(
                            TAG,
                            "Core Motion event protocol mismatch",
                        )

                        sink = null
                        return
                    }

                    sink =
                        candidate

                    Log.i(
                        TAG,
                        "Connected to trusted Core Motion event sink",
                    )

                    pendingCandidateElapsedMs
                        ?.also {
                            pendingCandidateElapsedMs =
                                null

                            sendCandidate(
                                candidate,
                                it,
                            )
                        }
                } catch (
                    throwable: Throwable,
                ) {
                    sink = null

                    Log.e(
                        TAG,
                        "Core Motion event handshake failed",
                        throwable,
                    )
                }
            }

            override fun onServiceDisconnected(
                name: ComponentName?,
            ) {
                sink = null

                Log.w(
                    TAG,
                    "Core Motion event sink disconnected",
                )
            }

            override fun onBindingDied(
                name: ComponentName?,
            ) {
                sink = null
                bound = false

                runCatching {
                    appContext.unbindService(
                        this,
                    )
                }

                connect()
            }

            override fun onNullBinding(
                name: ComponentName?,
            ) {
                sink = null
                bound = false

                runCatching {
                    appContext.unbindService(
                        this,
                    )
                }
            }
        }

    fun connect() {
        if (bound) {
            return
        }

        val component =
            findTrustedCoreSink()
                ?: run {
                    Log.w(
                        TAG,
                        "No trusted Core Motion event sink available",
                    )

                    return
                }

        val intent =
            Intent(
                HelperProtocol.ACTION_BIND_MOTION_EVENTS,
            ).setComponent(
                component,
            )

        bound =
            try {
                appContext.bindService(
                    intent,
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
            } catch (
                throwable: Throwable,
            ) {
                Log.e(
                    TAG,
                    "Failed to bind Core Motion event sink",
                    throwable,
                )

                false
            }

        if (!bound) {
            Log.w(
                TAG,
                "Core Motion event bind was not accepted",
            )
        }
    }

    fun reportCandidate(
        eventElapsedRealtimeMs: Long,
    ) {
        val currentSink =
            sink

        if (currentSink == null) {
            pendingCandidateElapsedMs =
                eventElapsedRealtimeMs

            connect()

            return
        }

        sendCandidate(
            currentSink,
            eventElapsedRealtimeMs,
        )
    }

    fun disconnect() {
        sink = null

        if (!bound) {
            return
        }

        runCatching {
            appContext.unbindService(
                connection,
            )
        }

        bound = false
    }

    private fun sendCandidate(
        currentSink: IMotionEventSink,
        eventElapsedRealtimeMs: Long,
    ) {
        try {
            val result =
                currentSink.reportMotionCandidate(
                    eventElapsedRealtimeMs,
                )

            Log.i(
                TAG,
                "Motion candidate delivered result=$result",
            )
        } catch (
            throwable: Throwable,
        ) {
            sink = null
            pendingCandidateElapsedMs =
                eventElapsedRealtimeMs

            Log.e(
                TAG,
                "Motion candidate delivery failed",
                throwable,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun findTrustedCoreSink():
        ComponentName? {
        val queryIntent =
            Intent(
                HelperProtocol.ACTION_BIND_MOTION_EVENTS,
            )

        val flags =
            PackageManager.MATCH_DIRECT_BOOT_AWARE or
                PackageManager.MATCH_DIRECT_BOOT_UNAWARE

        val candidates =
            appContext.packageManager
                .queryIntentServices(
                    queryIntent,
                    flags,
                )

        for (resolveInfo in candidates) {
            val serviceInfo =
                resolveInfo.serviceInfo
                    ?: continue

            if (!serviceInfo.exported) {
                continue
            }

            val signatureMatch =
                appContext.packageManager
                    .checkSignatures(
                        appContext.packageName,
                        serviceInfo.packageName,
                    ) ==
                    PackageManager.SIGNATURE_MATCH

            if (!signatureMatch) {
                continue
            }

            return ComponentName(
                serviceInfo.packageName,
                serviceInfo.name,
            )
        }

        return null
    }

    companion object {
        private const val TAG =
            "MotionEventClient"
    }
}
