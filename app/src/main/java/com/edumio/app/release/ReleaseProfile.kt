package com.edumio.app.release

import com.edumio.app.BuildConfig

/**
 * Central switch for the **Spark-safe v1.0** release. When `BuildConfig.SPARK_SAFE` is true, every feature
 * that needs deployed Cloud Functions / Blaze / live Play products stays **dormant behind its existing seam**
 * — no visible feature can fail because the backend isn't deployed. Firebase Analytics + Crashlytics remain
 * active (they work on the free Spark plan). Flip the flag to false (or per-buildType) once the backend is
 * live; **no code is deleted or forked**.
 *
 * The boolean decisions live in [ReleaseFlags] (pure, unit-tested); this object just binds them to
 * `BuildConfig`.
 */
object ReleaseProfile {
    val sparkSafe: Boolean get() = BuildConfig.SPARK_SAFE

    /** Account creation + cloud sign-in UI (needs cloud sync to be meaningful). */
    val cloudAccountEnabled: Boolean get() = ReleaseFlags.cloudAccountEnabled(sparkSafe)

    /** Cloud (Firestore) synchronisation of learning state. */
    val cloudSyncEnabled: Boolean get() = ReleaseFlags.cloudSyncEnabled(sparkSafe)

    /** Purchasable subscriptions (needs Play products + server verification). */
    val purchasesEnabled: Boolean get() = ReleaseFlags.purchasesEnabled(sparkSafe)

    /** Server-verified entitlement (needs the purchase-verification Cloud Function). */
    val serverEntitlementEnabled: Boolean get() = ReleaseFlags.serverEntitlementEnabled(sparkSafe)

    /** Firebase Analytics — Spark-compatible, always allowed when Firebase is configured. */
    val analyticsEnabled: Boolean get() = ReleaseFlags.analyticsEnabled(sparkSafe)

    /** Firebase Crashlytics — Spark-compatible, always allowed when Firebase is configured. */
    val crashlyticsEnabled: Boolean get() = ReleaseFlags.crashlyticsEnabled(sparkSafe)

    /**
     * Master switch for ALL user-visible Premium UI — paywall, upsell/promo cards, premium badges, and
     * locked-feature "unlock with Premium" CTAs. The first Play release is entirely FREE, so this is false.
     * Billing/entitlement code stays intact (kept independent of SPARK_SAFE); only the UI surfaces hide.
     */
    const val premiumEnabled: Boolean = false

    /**
     * Master switch for the Lig (league / weekly ranking) surface. It has no backend, so its opponents are
     * locally simulated — presenting them as real competitors is both off the MVP keep-list and a
     * deceptive-behaviour risk. Hidden for the first Play release; the `league/` code stays in the repo.
     */
    const val leagueEnabled: Boolean = false
}

/**
 * Pure Spark-safe decisions (no Android/BuildConfig deps → unit-tested). In Spark-safe mode every
 * backend-dependent feature is OFF; Analytics/Crashlytics stay ON.
 */
object ReleaseFlags {
    fun cloudAccountEnabled(sparkSafe: Boolean): Boolean = !sparkSafe
    fun cloudSyncEnabled(sparkSafe: Boolean): Boolean = !sparkSafe
    fun purchasesEnabled(sparkSafe: Boolean): Boolean = !sparkSafe
    fun serverEntitlementEnabled(sparkSafe: Boolean): Boolean = !sparkSafe
    fun analyticsEnabled(@Suppress("UNUSED_PARAMETER") sparkSafe: Boolean): Boolean = true
    fun crashlyticsEnabled(@Suppress("UNUSED_PARAMETER") sparkSafe: Boolean): Boolean = true
}
