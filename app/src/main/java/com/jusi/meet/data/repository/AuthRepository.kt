package com.jusi.meet.data.repository

import com.jusi.meet.data.api.AuthApi
import com.jusi.meet.data.api.dto.SendOtpRequest
import com.jusi.meet.data.api.dto.VerifyOtpRequest
import com.jusi.meet.data.auth.TokenStore

/**
 * Login / logout flow.  Wraps the mobile auth endpoints and persists the
 * resulting tokens via [TokenStore].
 *
 * The send-otp / verify-otp endpoints do not require authentication; the
 * AuthInterceptor only attaches a Bearer header when one is already present
 * in the TokenStore, so callers don't need to opt out explicitly.
 */
class AuthRepository(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) {

    /** Send a 6-digit SMS code to [phone].  Returns Result.failure on any error. */
    suspend fun sendOtp(phone: String): Result<Unit> = runCatching {
        authApi.sendOtp(SendOtpRequest(phone = phone))
        Unit
    }

    /** Verify [otp] for [phone] and persist the returned tokens. */
    suspend fun verifyOtp(phone: String, otp: String): Result<Unit> = runCatching {
        val resp = authApi.verifyOtp(VerifyOtpRequest(phone = phone, otp = otp))
        tokenStore.accessToken = resp.access_token
        tokenStore.refreshToken = resp.refresh_token
        tokenStore.phone = phone
    }

    fun isLoggedIn(): Boolean = tokenStore.isLoggedIn()

    fun signOut() {
        tokenStore.clear()
    }
}
