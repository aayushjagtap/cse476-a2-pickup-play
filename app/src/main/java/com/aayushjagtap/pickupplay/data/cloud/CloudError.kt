package com.aayushjagtap.pickupplay.data.cloud

import android.content.Context
import androidx.annotation.StringRes
import com.aayushjagtap.pickupplay.R
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestoreException

/** User-meaningful categories for every Firebase / network failure the app can hit. */
enum class CloudError(@StringRes val messageRes: Int) {
    NOT_CONFIGURED(R.string.cloud_error_not_configured),
    NOT_SIGNED_IN(R.string.cloud_error_not_signed_in),
    OFFLINE(R.string.cloud_error_offline),
    TIMEOUT(R.string.cloud_error_timeout),
    SERVER_UNREACHABLE(R.string.cloud_error_unreachable),
    PERMISSION_DENIED(R.string.cloud_error_permission_denied),
    BAD_CREDENTIALS(R.string.cloud_error_bad_credentials),
    TOO_MANY_ATTEMPTS(R.string.cloud_error_too_many),
    UNKNOWN(R.string.cloud_error_unknown);

    companion object {
        fun from(t: Throwable): CloudError = when (t) {
            is CloudTimeoutException -> TIMEOUT
            is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> BAD_CREDENTIALS
            is FirebaseTooManyRequestsException -> TOO_MANY_ATTEMPTS
            is FirebaseNetworkException -> SERVER_UNREACHABLE
            is FirebaseFirestoreException -> when (t.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> PERMISSION_DENIED
                FirebaseFirestoreException.Code.UNAVAILABLE -> SERVER_UNREACHABLE
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> TIMEOUT
                else -> UNKNOWN
            }
            is CloudException -> t.error
            else -> UNKNOWN
        }
    }
}

/** A cloud call that did not finish within its time limit (no endless spinners). */
class CloudTimeoutException(what: String) : Exception("$what timed out")

/** A failure the app detected itself before calling Firebase (offline, signed out, ...). */
class CloudException(val error: CloudError) : Exception(error.name)

/** A message for the UI, resolved to text only when shown (so it follows the current locale). */
data class UiMessage(@StringRes val res: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(
        res,
        *args.map { if (it is CloudError) context.getString(it.messageRes) else it }.toTypedArray(),
    )
}
