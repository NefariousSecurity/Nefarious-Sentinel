# ══════════════════════════════════════════════════════════════════
# Runtime Core - ProGuard / R8 rules
# ══════════════════════════════════════════════════════════════════

# ── Device Admin ────────────────────────────────────────────────
# Android references this manifest component by class name.
#
# AGP also generates manifest-derived keep rules automatically, but this
# explicit rule documents the Core's device-policy entry point and provides
# defense in depth against future build-system changes.
-keep class com.localruntime.core.admin.DeviceAdminReceiver { *; }

# ── USB Probe Components ────────────────────────────────────────
# These are the current manifest-bound USB detection components.
#
# Keep these names synchronized with AndroidManifest.xml and the source tree
# whenever the USB probe implementation is renamed or refactored.
-keep class com.localruntime.core.usb.UsbDataProbeService { *; }
-keep class com.localruntime.core.usb.UsbDataProbeBootReceiver { *; }

# ── Hilt ────────────────────────────────────────────────────────
# Preserve Hilt classes and generated dependency-injection infrastructure.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Kotlin Metadata / Runtime Annotations ───────────────────────
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

# ── EncryptedSharedPreferences / Tink ───────────────────────────
# Existing Sentinel configuration storage still uses
# androidx.security EncryptedSharedPreferences and Tink internally.
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ── Release Logging ─────────────────────────────────────────────
# Remove verbose/debug Android logs from optimized release builds.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# ── Optional Crypto Providers ───────────────────────────────────
# Some transitive crypto code can reference providers that are not packaged
# with Sentinel on every Android configuration.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
