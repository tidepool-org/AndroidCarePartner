package org.tidepool.carepartner.backend

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.tidepool.sdk.Environment
import org.tidepool.sdk.Environments
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class PersistentData {
    companion object {
        @Volatile
        private var _authState = AuthState()
        val authState by this::_authState
        @Volatile
        var environment: Environment = Environments.Qa1

        private const val TAG = "PersistentData"

        fun renewAuthState() {
            val env by lazy(this::environment)
            val uriString = "${env.auth.url}/realms/${env.envCode}/.well-known/openid-configuration"
            Log.v(TAG, "URI: \"${uriString}\"")
            AuthorizationServiceConfiguration.fetchFromUrl(
                Uri.parse(uriString)
            ) { serviceConfiguration, ex ->
                if (ex != null) {
                    _authState = AuthState()
                    throw ex
                }
                _authState = AuthState(serviceConfiguration!!)
            }
        }

        private suspend fun Context.exchangeAuthCode() = suspendCoroutine { continuation ->
            val resp = authState.lastAuthorizationResponse ?: throw NullPointerException("No Authorization Response")
            AuthorizationService(this).performTokenRequest(resp.createTokenExchangeRequest()) { newResp, ex ->
                _authState.update(newResp, ex)
                continuation.resume(Unit)
            }
        }

        suspend fun Context.getAccessToken(): String {
            if (authState.accessToken == null) {
                exchangeAuthCode()
            }
            return suspendCancellableCoroutine { continuation ->
                val authService by lazy { AuthorizationService(this) }
                _authState.performActionWithFreshTokens(authService) { accessToken, _, ex ->
                    if (ex != null) {
                        continuation.resumeWithException(ex)
                    } else {
                        continuation.resume(accessToken!!)
                    }
                }
            }
        }

        suspend fun Context.getIdToken(): String {
            if (authState.idToken == null) {
                exchangeAuthCode()
            }
            return suspendCancellableCoroutine { continuation ->
                _authState.performActionWithFreshTokens(AuthorizationService(this)) { _, idToken, ex ->
                    if (ex != null) {
                        continuation.resumeWithException(ex)
                    } else {
                        continuation.resume(idToken!!)
                    }
                }
            }
        }

        fun getAuthRequestBuilder(): AuthorizationRequest.Builder {
            renewAuthState()
            return AuthorizationRequest.Builder(
                _authState.authorizationServiceConfiguration ?: throw NullPointerException("No Configuration"),
                "tidepool-carepartner-android",
                ResponseTypeValues.CODE,
                Uri.parse("org.tidepool.carepartner://tidepool_service_callback")
            ).apply {
                setScope("openid email")
                setLoginHint("wavedashing@madeline.celeste.com")
                //setLoginHint("jdoe@user.example.com")
            }
        }
    }
}