package com.runtimebridge.smshelper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.runtimebridge.helper.HelperProtocol
import com.runtimebridge.helper.ISmsEventSink

/**
 * Narrow SMS Helper -> Core event client.
 *
 * Core is discovered by neutral action and accepted only when its signing
 * certificate matches this Helper package.
 */
class SmsEventClient(
    context: Context,
) {

    private val appContext =
        context.applicationContext

    private val mainHandler =
        Handler(
            Looper.getMainLooper(),
        )

    @Volatile
    private var sink:
        ISmsEventSink? =
        null

    @Volatile
    private var bound =
        false

    @Volatile
    private var pendingMessage:
        PendingMessage? =
        null

    private val timeoutRunnable =
        Runnable {
            val pending =
                pendingMessage
                    ?: return@Runnable

            pendingMessage = null

            Log.e(
                TAG,
                "Timed out waiting for Core SMS event sink",
            )

            pending.complete(
                null,
            )

            disconnect()
        }

    private val connection =
        object : ServiceConnection {

            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                val candidate =
                    ISmsEventSink.Stub.asInterface(
                        service,
                    )

                try {
                    if (
                        candidate.getProtocolVersion() !=
                        HelperProtocol.PROTOCOL_VERSION
                    ) {
                        Log.e(
                            TAG,
                            "Core SMS event protocol mismatch",
                        )

                        completePending(
                            null,
                        )

                        disconnect()
                        return
                    }

                    sink =
                        candidate

                    Log.i(
                        TAG,
                        "Connected to trusted Core SMS event sink",
                    )

                    val pending =
                        pendingMessage

                    if (pending != null) {
                        sendMessage(
                            candidate,
                            pending,
                        )
                    }
                } catch (
                    throwable: Throwable,
                ) {
                    sink = null

                    Log.e(
                        TAG,
                        "Core SMS event handshake failed",
                        throwable,
                    )

                    completePending(
                        null,
                    )

                    disconnect()
                }
            }

            override fun onServiceDisconnected(
                name: ComponentName?,
            ) {
                sink = null

                Log.w(
                    TAG,
                    "Core SMS event sink disconnected",
                )
            }

            override fun onBindingDied(
                name: ComponentName?,
            ) {
                sink = null

                Log.e(
                    TAG,
                    "Core SMS event binding died",
                )

                completePending(
                    null,
                )

                disconnect()
            }

            override fun onNullBinding(
                name: ComponentName?,
            ) {
                sink = null

                Log.e(
                    TAG,
                    "Core SMS event sink returned null binding",
                )

                completePending(
                    null,
                )

                disconnect()
            }
        }

    /**
     * Deliver exactly one logical SMS candidate.
     *
     * Only one delivery may be in flight for an SmsEventClient instance.
     */
    fun reportMessage(
        sender: String,
        body: String,
        onComplete: (Int?) -> Unit,
    ) {
        if (pendingMessage != null) {
            Log.e(
                TAG,
                "SMS candidate rejected because another delivery is pending",
            )

            onComplete(
                null,
            )

            return
        }

        pendingMessage =
            PendingMessage(
                sender = sender,
                body = body,
                complete = onComplete,
            )

        mainHandler.postDelayed(
            timeoutRunnable,
            BIND_TIMEOUT_MS,
        )

        val currentSink =
            sink

        if (currentSink != null) {
            sendMessage(
                currentSink,
                pendingMessage
                    ?: return,
            )

            return
        }

        connect()
    }

    fun disconnect() {
        sink = null

        mainHandler.removeCallbacks(
            timeoutRunnable,
        )

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

    private fun connect() {
        if (bound) {
            return
        }

        val component =
            findTrustedCoreSink()
                ?: run {
                    Log.w(
                        TAG,
                        "No trusted Core SMS event sink available",
                    )

                    completePending(
                        null,
                    )

                    return
                }

        val intent =
            Intent(
                HelperProtocol.ACTION_BIND_SMS_EVENTS,
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
                    "Failed to bind Core SMS event sink",
                    throwable,
                )

                false
            }

        if (!bound) {
            Log.w(
                TAG,
                "Core SMS event bind was not accepted",
            )

            completePending(
                null,
            )
        }
    }

    private fun sendMessage(
        currentSink: ISmsEventSink,
        pending: PendingMessage,
    ) {
        try {
            val result =
                currentSink.reportSmsMessage(
                    pending.sender,
                    pending.body,
                )

            Log.i(
                TAG,
                "SMS candidate delivered result=$result",
            )

            completePending(
                result,
            )
        } catch (
            throwable: Throwable,
        ) {
            sink = null

            Log.e(
                TAG,
                "SMS candidate delivery failed",
                throwable,
            )

            completePending(
                null,
            )
        } finally {
            disconnect()
        }
    }

    private fun completePending(
        result: Int?,
    ) {
        mainHandler.removeCallbacks(
            timeoutRunnable,
        )

        val pending =
            pendingMessage
                ?: return

        pendingMessage = null

        runCatching {
            pending.complete(
                result,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun findTrustedCoreSink():
        ComponentName? {
        val queryIntent =
            Intent(
                HelperProtocol.ACTION_BIND_SMS_EVENTS,
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

    private data class PendingMessage(
        val sender: String,
        val body: String,
        val complete: (Int?) -> Unit,
    )

    companion object {
        private const val TAG =
            "SmsEventClient"

        private const val BIND_TIMEOUT_MS =
            8_000L
    }
}
