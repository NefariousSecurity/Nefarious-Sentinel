package com.runtimebridge.helper;

/**
 * Narrow helper-to-Core event surface for SMS Wipe.
 *
 * The SMS Helper extracts sender/body only.
 * Core retains the authorised sender, secret keyword and wipe authority.
 */
interface ISmsEventSink {
    int getProtocolVersion();

    int reportSmsMessage(
        String sender,
        String body
    );
}
