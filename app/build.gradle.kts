import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// ── Release signing ─────────────────────────────────────────────────────────────────────────────
// Credentials live in keystore.properties (git-ignored; see keystore.properties.example). NEVER commit
// secrets. When absent, release signing is simply not configured and a release build fails with a clear
// message (see the taskGraph check below). Debug builds are unaffected (they use the Android debug key).
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val hasReleaseSigning = keystorePropertiesFile.exists()
if (hasReleaseSigning) FileInputStream(keystorePropertiesFile).use { keystoreProperties.load(it) }

android {
    namespace = "com.edumio.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.edumio.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"

        // Play Store için gerçek URL ekleyin; boş bırakılırsa "Web'de görüntüle" butonu gizlenir
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"\"")
        buildConfigField("String", "TERMS_URL", "\"\"")
        // Embed seed version so audit screen can show it without importing DbSeeder
        buildConfigField("int", "DB_SEED_VERSION", "9")

        // 🔥 adaptive icon hatasını engelle
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // Only defined when keystore.properties is present, so a checkout without the secrets file still
        // builds debug. storeFile is resolved relative to the repo root (edumio_release.jks lives there).
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed with the EDUmio release key only when configured; otherwise the taskGraph check below
            // fails a release build with actionable guidance (debug stays on the debug key).
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    testOptions {
        unitTests.all {
            it.testLogging.showStandardStreams = true
        }
    }

    // Make the exported Room schemas available to Migration instrumentation tests (MigrationTestHelper).
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

// Export Room schemas to a stable, version-controlled directory (app/schemas/<db-fqcn>/<version>.json).
// These frozen JSONs are the migration baseline; never edit a shipped schema, only add the next version.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Fail a RELEASE build with clear guidance when signing is not configured (keystore.properties absent).
// Debug builds are never affected.
gradle.taskGraph.whenReady {
    val releaseArtifactRequested = allTasks.any { task ->
        (task.name.startsWith("assemble") || task.name.startsWith("bundle")) && task.name.contains("Release")
    }
    if (releaseArtifactRequested && !hasReleaseSigning) {
        throw GradleException(
            "EDUmio release signing is not configured. Copy keystore.properties.example to " +
                "keystore.properties and fill in the EDUmio release keystore (edumio_release.jks) " +
                "credentials. Secrets must never be committed. See docs/PHASE0_RELEASE_SIGNING_REPORT.md."
        )
    }
}

dependencies {
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.2.0")
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // ── Firebase (behind provider seams; inert at runtime until google-services.json is supplied) ──
    // The BoM pins mutually-compatible versions. FirebaseInitProvider no-ops without a configured default
    // app (logs a warning, never crashes); all EDUmio code checks FirebaseConfig.isConfigured() first.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-messaging")
    // Google Sign-In via Credential Manager (used by the Google auth path; harmless when unused).
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}

// ── Firebase build plugins: applied ONLY when the owner's google-services.json is present ──────────────
// Present  → google-services generates google_app_id etc. and Crashlytics symbol upload is enabled.
// Absent   → neither plugin applies; the app builds and runs in local-fallback mode with no Firebase.
// google-services.json is git-ignored and must never be committed (see docs/OWNER_CONSOLE_SETUP_GUIDE.md).
if (project.file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    logger.lifecycle("EDUmio: google-services.json found → Firebase build plugins ACTIVE.")
} else {
    logger.lifecycle("EDUmio: no google-services.json → Firebase inactive (local-fallback build).")
}