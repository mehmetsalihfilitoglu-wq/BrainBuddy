# BrainBuddy release — R8 / ProGuard (minify enabled)

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
