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

/**
 * User-facing error + a hint on whether another attempt could succeed.
 *
 * [retryable] drives UI affordances: the Snackbar shows a 重试 action
 * only when this is `true`. It is NOT a correctness flag — retrying a
 * non-retryable error won't crash, it just won't help (e.g. 404 "会议号
 * 无效" stays 404).
 */
data class UserError(val message: String, val retryable: Boolean)

private val errorBodyAdapter by lazy {
    Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(ApiErrorBody::class.java)
}

/**
 * Translate a thrown exception from a Retrofit call into a user-facing
 * Chinese message plus a retryability hint.
 *
 * Retryability rules:
 * - `IOException` — transient network glitch → retryable.
 * - 401 — needs re-login, retry won't help → not retryable.
 * - 429 — try again later → retryable.
 * - 404 in `ROOM_FETCH` — wrong meeting ID → not retryable.
 * - 5xx — server hiccup → retryable.
 * - Other 4xx — client-side error surfaced from backend body → not
 *   retryable (e.g. "验证码错误", "仅房主可结束会议").
 * - Unknown — assume retryable (helpful default).
 */
fun Throwable.toUserError(
    context: Context,
    scope: ErrorScope = ErrorScope.GENERIC,
): UserError = when (this) {
    is IOException -> UserError(context.getString(R.string.error_network), retryable = true)
    is HttpException -> translateHttp(this, context, scope)
    else -> UserError(context.getString(R.string.error_unknown), retryable = true)
}

/**
 * Convenience for call sites that only need the message (inline red
 * text, full-screen ErrorView). Wraps [toUserError].
 */
fun Throwable.toUserMessage(
    context: Context,
    scope: ErrorScope = ErrorScope.GENERIC,
): String = toUserError(context, scope).message

private fun translateHttp(
    e: HttpException,
    context: Context,
    scope: ErrorScope,
): UserError {
    val code = e.code()

    if (scope == ErrorScope.ROOM_FETCH && code == 404) {
        return UserError(context.getString(R.string.error_room_not_found), retryable = false)
    }
    if (code == 401) {
        return UserError(context.getString(R.string.error_auth_expired), retryable = false)
    }
    if (code == 429) {
        return UserError(context.getString(R.string.error_too_many_requests), retryable = true)
    }

    val bodyMessage = parseBody(e)?.let { body ->
        body.error?.takeIf { it.isNotBlank() }
            ?: body.detail?.takeIf { it.isNotBlank() }
    }

    if (code in 500..599) {
        return UserError(
            message = bodyMessage ?: context.getString(R.string.error_server),
            retryable = true,
        )
    }

    // Other 4xx: backend rejected the request on a client-side condition;
    // the same payload won't succeed on retry.
    if (bodyMessage != null) {
        return UserError(message = bodyMessage, retryable = false)
    }
    return UserError(context.getString(R.string.error_unknown), retryable = true)
}

private fun parseBody(e: HttpException): ApiErrorBody? {
    // response().errorBody() is a one-shot stream; read once.
    val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
    if (raw.isNullOrBlank()) return null
    return runCatching { errorBodyAdapter.fromJson(raw) }.getOrNull()
}
