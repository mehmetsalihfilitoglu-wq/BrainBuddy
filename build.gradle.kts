plugins {
    // ✅ Plugin'ler burada VERSION ile tanımlanır ama apply edilmez
    id("com.android.application") version "8.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    // Firebase build plugins — declared here (apply false) and applied by :app ONLY when the owner's
    // google-services.json is present. Absent → the app builds in local-fallback mode (no Firebase).
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}