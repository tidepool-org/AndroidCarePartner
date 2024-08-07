package org.tidepool.carepartner.backend

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.gson.GsonBuilder
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.ResponseTypeValues
import org.tidepool.carepartner.FollowActivity
import org.tidepool.carepartner.MainActivity
import org.tidepool.sdk.CommunicationHelper
import org.tidepool.sdk.Environment
import org.tidepool.sdk.Environments
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class PersistentData {
    private class DataHolder {
        var authState: AuthState = AuthState()
        var lastEmail: String? = null
        var environment: Environment = Environments.Production
    }
    companion object {
        private val redirectUri = Uri.parse("org.tidepool.carepartner://tidepool_service_callback")
        private const val FILENAME = "persistent-data"
        private val data = DataHolder()
        private var _authState by data::authState
        val authState by this::_authState
        var environment by data::environment
        private var _lastEmail by data::lastEmail
        val lastEmail by this::_lastEmail
        
        private val lock = ReentrantLock()
        
        private val gson by lazy {
            GsonBuilder().apply {
                registerTypeAdapter(Uri::class.java, UriDeserializer())
                registerTypeAdapterFactory(EnvironmentTypeAdapter())
            }.create()
        }
        
        fun Context.readFromDisk() {
            Log.v(TAG, "Waiting for lock...")
            lock.withLock {
                if (fileList().contains(FILENAME)) {
                    Log.v(TAG, "Reading from disk...")
                    openFileInput(FILENAME).reader().use { reader ->
                        val tmpData = gson.fromJson(reader, DataHolder::class.java)
                        data.lastEmail = tmpData.lastEmail
                        data.authState = tmpData.authState
                        data.environment = tmpData.environment
                    }
                }
            }
            Log.v(TAG, "Done with lock")
        }
        
        fun Context.writeToDisk() {
            Log.v(TAG, "Waiting for lock...")
            lock.withLock {
                Log.v(TAG, "Writing to file...")
                
                openFileOutput(FILENAME, Context.MODE_PRIVATE).writer().use { writer ->
                    Log.v(TAG, "Starting Write...")
                    gson.toJson(data, writer)
                    Log.v(TAG, "Ending Write...")
                }
            }
            Log.v(TAG, "Done with lock")
        }

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

        fun Context.logout() {
            Log.v(TAG, "logout...")
            data.lastEmail = null
            val authService = AuthorizationService(this)
            val endSessionRequest = EndSessionRequest.Builder(_authState.authorizationServiceConfiguration ?: throw NullPointerException("No configuration"))
                .setIdTokenHint(_authState.idToken)
                .setPostLogoutRedirectUri(redirectUri)
                .build()

            authService.performEndSessionRequest(
                endSessionRequest,
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
                PendingIntent.getActivity(this, 0, Intent(this, FollowActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            )
            _authState = AuthState()
            writeToDisk()
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
                    Log.v(TAG, "Fresh Token callback called")
                    if (ex != null) {
                        continuation.resumeWithException(ex)
                    } else {
                        continuation.resume(accessToken!!)
                    }
                }
            }
        }
        
        suspend fun Context.saveEmail() {
            _lastEmail = CommunicationHelper(environment).users.getCurrentUserInfo(getAccessToken()).username
            Log.v(TAG, "lastEmail: $_lastEmail")
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
                redirectUri
            ).apply {
                setScope("openid email")
                _lastName?.let {
                    setLoginHint(it)
                }
            }
        }
        
        val AuthState.accessTokenExpiration: Instant?
            get() = accessTokenExpirationTime?.let { Instant.ofEpochMilli(it) }
    }
}