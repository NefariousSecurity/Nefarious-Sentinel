package com.runtimebridge.smshelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * Isolated SMS ingress point.
 *
 * This Helper owns RECEIVE_SMS but owns no Sentinel policy or wipe authority.
 */
class SmsIngressReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (
            intent.action !=
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        ) {
            return
        }

        val messages =
            Telephony.Sms.Intents.getMessagesFromIntent(
                intent,
            )

        if (messages.isNullOrEmpty()) {
            return
        }

        val sender =
            messages
                .firstNotNullOfOrNull {
                    it.displayOriginatingAddress
                }
                ?: return

        /*
         * Treat Android's segments as one logical incoming message.
         *
         * The Helper still performs no sender or keyword matching.
         */
        val body =
            messages
                .mapNotNull {
                    it.displayMessageBody
                }
                .joinToString(
                    separator = "",
                )

        if (body.isEmpty()) {
            return
        }

        Log.i(
            TAG,
            "SMS received body_len=${body.length}",
        )

        val pendingResult =
            goAsync()

        val client =
            SmsEventClient(
                context.applicationContext,
            )

        client.reportMessage(
            sender = sender,
            body = body,
        ) {
            result ->

            Log.i(
                TAG,
                "SMS candidate completion result=$result",
            )

            client.disconnect()

            pendingResult.finish()
        }
    }

    companion object {
        private const val TAG =
            "SmsIngressReceiver"
    }
}
