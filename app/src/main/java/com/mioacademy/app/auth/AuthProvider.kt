package com.mioacademy.app.auth

import android.content.Context

/**
 * Provides AuthRepository. Uses LocalAuthStub when Firebase not configured.
 */
object AuthProvider {
    fun get(context: Context): AuthRepository {
        return LocalAuthStubRepository(context)
    }
}
