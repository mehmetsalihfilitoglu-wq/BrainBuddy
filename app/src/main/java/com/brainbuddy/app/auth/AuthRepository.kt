package com.brainbuddy.app.auth

/**
 * Auth abstraction: signUp, signIn, signOut, currentUserId, currentEmail.
 * Use FirebaseAuthAuthRepository when google-services.json exists;
 * otherwise LocalAuthStubRepository for development.
 */
interface AuthRepository {
    suspend fun signUp(email: String, password: String): AuthResult
    suspend fun signIn(email: String, password: String): AuthResult
    fun signOut()
    fun currentUserId(): String?
    fun currentEmail(): String?
    fun isSignedIn(): Boolean
}

sealed class AuthResult {
    data class Success(val userId: String, val email: String) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
