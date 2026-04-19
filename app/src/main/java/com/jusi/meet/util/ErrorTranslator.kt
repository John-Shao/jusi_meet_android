package com.jusi.meet.util

import android.content.Context
import com.jusi.meet.R
import com.jusi.meet.data.api.dto.ApiErrorBody
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.HttpException
import java.io.IOException

/**
 * Scope hint lets callers override a backend message for a specific
 * endpoint — e.g. `GET /rooms/{id}/` 404 is `{"detail": "No Room matches
 * the given query."}` in English, but from the Join flow we want
 * 「会议号无效」.
 */
enum class ErrorScope {
    GENERIC,
    ROOM_FETCH,
    ROOM_END,
    AUTH_SEND_OTP,
    AUTH_VERIFY_OTP,
}

private val errorBodyAdapter by lazy {
    Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ApiErrorBody::class.java)
}

/**
 * Translate a thrown exception from a Retrofit call into a user-facing
 * Chinese string ready to show in a Toast / inline error.
 *
 * Precedence:
 * 1. Scope-specific HTTP overrides (e.g. ROOM_FETCH 404 → 会议号无效).
 * 2. Global HTTP overrides for 401 / 429.
 * 3. Backend-provided `error` / `detail` message (already localised by the
 *    backend for mobile auth endpoints).
 * 4. Family fallbacks (5xx → 服务异常、其他 → 出错了).
 */
fun Throwable.toUserMessage(
    context: Context,
    scope: ErrorScope = ErrorScope.GENERIC,
): String = when (this) {
    is IOException -> context.getString(R.string.error_network)
    is HttpException -> translateHttp(this, context, scope)
    else -> context.getString(R.string.error_unknown)
}

private fun translateHttp(
    e: HttpException,
    context: Context,
    scope: ErrorScope,
): String {
    val code = e.code()

    if (scope == ErrorScope.ROOM_FETCH && code == 404) {
        return context.getString(R.string.error_room_not_found)
    }
    if (code == 401) return context.getString(R.string.error_auth_expired)
    if (code == 429) return context.getString(R.string.error_too_many_requests)

    parseBody(e)?.let { body ->
        body.error?.takeIf { it.isNotBlank() }?.let { return it }
        body.detail?.takeIf { it.isNotBlank() }?.let { return it }
    }

    if (code in 500..599) return context.getString(R.string.error_server)
    return context.getString(R.string.error_unknown)
}

private fun parseBody(e: HttpException): ApiErrorBody? {
    // response().errorBody() is a one-shot stream; read once.
    val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    if (raw.isNullOrBlank()) return null
    return runCatching { errorBodyAdapter.fromJson(raw) }.getOrNull()
}
