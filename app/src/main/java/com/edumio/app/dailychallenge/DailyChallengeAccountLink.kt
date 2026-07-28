package com.edumio.app.dailychallenge

import android.content.Context
import com.edumio.app.auth.AuthProvider
import kotlin.coroutines.cancellation.CancellationException

/**
 * Links an anonymous session's Daily Challenge state to a real account the first time the user signs in.
 *
 * Without this, [DailyChallengeUser.resolve] would flip from the anon id to the account uid on sign-in,
 * the account uid would have no challenge for today, and [DailyChallengeEngine.getOrCreateToday] would
 * generate a SECOND challenge the same day (extra new questions). Linking copies the anon state to the
 * account (re-keyed) so the same day's challenge — and its retirement/review/streak history — carries
 * over and nothing is regenerated.
 *
 * Idempotent and cheap: safe to call at the start of every Daily-Challenge entry point.
 */
object DailyChallengeAccountLink {

    suspend fun linkIfNeeded(context: Context) {
        val uid = AuthProvider.currentUser(context)?.userId?.takeIf { it.isNotBlank() } ?: return
        val anon = DailyChallengeUser.peekAnonId(context) ?: return
        if (anon == uid) return
        // Copy anon → account (no-ops safely if the account already owns state, or the anon has none).
        // Clear the anon pointer ONLY after the migration completes, so a cancellation (activity destroyed
        // mid-migration) or an IO failure leaves the pointer intact and the next entry point self-heals by
        // retrying. Never swallow CancellationException — that would both break structured concurrency and
        // (with the old unconditional clear) strand a half-migrated account into regenerating a 2nd challenge.
        val completed = try {
            DailyChallengeEngine(context.applicationContext).migrateAccount(anon, uid)
            true
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            false
        }
        if (completed) DailyChallengeUser.clearAnonId(context)
    }
}
