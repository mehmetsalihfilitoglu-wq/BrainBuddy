# EDUmio release — R8 / ProGuard (minify enabled)

# Stack traces: keep line numbers (file name hidden)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Play Services Ads references API 35+ optional media APIs; not present on older compile SDK / stubs.
# AGP writes these to build/outputs/mapping/release/missing_rules.txt when R8 fails.
-dontwarn android.media.LoudnessCodecController
-dontwarn android.media.LoudnessCodecController$OnLoudnessCodecUpdateListener

# --- Room: runtime ships consumer rules; keep annotations for Kotlin metadata edge cases ---
-keepattributes Signature
-keepattributes *Annotation*

# --- WorkManager: workers are referenced by class; library consumer rules usually suffice ---
# If a worker fails to instantiate at runtime, add explicit -keep for that Worker subclass.

# --- Strip verbose logging from RELEASE (Phase 0 §9) ---
# Quiz/quality-engine diagnostics log stems/answers/choices at d/v/i/w. R8 removes these calls (and
# their now-dead string building) from the release build, so no answer text leaks to Logcat in
# production. Log.e is kept for genuine error diagnostics (its remaining sites log only ids/counts).
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}
