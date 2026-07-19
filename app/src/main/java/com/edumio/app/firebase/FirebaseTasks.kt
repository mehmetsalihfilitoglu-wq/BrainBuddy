package com.edumio.app.firebase

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Await a Google Play Services [Task] from a coroutine without pulling in the
 * kotlinx-coroutines-play-services dependency. Cancels the coroutine if the task is cancelled and
 * propagates the task's exception on failure.
 */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
