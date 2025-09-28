package org.tidepool.carepartner.backend

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.ResponseTypeValues
import org.tidepool.carepartner.FollowActivity
import org.tidepool.carepartner.MainActivity
import org.tidepool.carepartner.TidepoolApplication
import org.tidepool.carepartner.backend.jank.retrieveConfiguration
import org.tidepool.sdk.Environment
import org.tidepool.sdk.Environments
import org.tidepool.sdk.model.BloodGlucose.Units
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.seconds

class PersistentData {
    private class DataHolder {
        
        var authStateJson: String? = null
        var lastEmail: String? = null
        var lastName: String? = null
        var environment: Environment = Environments.Qa1
        var unit: Units = Units.MilligramsPerDeciliter
    }
    
    companion object {
        
        private val redirectUri = Uri.parse("org.tidepool.carepartner://tidepool_service_callback")
        private const val FILENAME = "persistent-data"
        private val data = DataHolder()
        private var _authState: AuthState = AuthState()
        val authState: AuthState
            get() = _authState
        var environment by data::environment
        private var _lastEmail by data::lastEmail
        val lastEmail by this::_lastEmail
        private var _lastName by data::lastName
        val lastName by this::_lastName
        var unit by data::unit
        
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
                        data.authStateJson = tmpData.authStateJson
                        data.environment = tmpData.environment
                        data.lastName = tmpData.lastName
                        data.unit = tmpData.unit
                        
                        _authState =
                            data.authStateJson?.let {
                                AuthState.jsonDeserialize(it)
                            } ?: run {
                                AuthState()
                            }
                        
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
                    data.authStateJson = _authState.jsonSerializeString()
                    gson.toJson(data, writer)
                    Log.v(TAG, "Ending Write...")
                }
            }
            Log.v(TAG, "Done with lock")
        }
        
        private const val TAG = "PersistentData"
        
        private suspend fun Context.ensureAuthConfiguration() {
            if (_authState.authorizationServiceConfiguration == null) {
                Log.v(TAG, "No authorization configuration found, retrieving...")
                
                val uriString =
                    "${environment.auth.url}/realms/${environment.envCode}/.well-known/openid-configuration"
                try {
                    val configuration = withTimeout(15.seconds) {
                        retrieveConfiguration(Uri.parse(uriString))
                    }
                    
                    // If we have existing tokens/auth data, we can't just replace the AuthState
                    // because that would lose the mLastAuthorizationResponse needed for refresh
                    // Instead, we need to handle this case by storing the configuration separately
                    
                    val currentState = _authState
                    val hasExistingData = currentState.accessToken != null ||
                            currentState.refreshToken != null ||
                            currentState.lastAuthorizationResponse != null
                    
                    if (hasExistingData && currentState.lastAuthorizationResponse == null) {
                        // This is the problematic case: we have tokens but no lastAuthorizationResponse
                        // This can happen if the AuthState was deserialized without the configuration
                        // In this case, we need to clear the state and force re-authentication
                        Log.w(
                            TAG,
                            "Found tokens but no authorization response - this indicates a corrupted auth state"
                        )
                        Log.w(TAG, "Clearing auth state to force fresh authentication")
                        _authState = AuthState(configuration)
                        writeToDisk()
                        throw NoAuthorizationException()
                    } else if (!hasExistingData) {
                        // No existing data, safe to create fresh AuthState
                        _authState = AuthState(configuration)
                        writeToDisk()
                    } else {
                        // We have existing data and lastAuthorizationResponse
                        // The configuration should come from the authorization response
                        // But if it doesn't, we have a problem that can't be easily fixed
                        Log.w(TAG, "Have existing auth data but missing configuration")
                        Log.w(TAG, "This may indicate a serialization/deserialization issue")
                        
                        // Try to get configuration from the authorization response
                        val configFromResponse =
                            currentState.lastAuthorizationResponse?.request?.configuration
                        if (configFromResponse == null) {
                            Log.e(TAG, "Authorization response exists but has no configuration")
                            Log.e(TAG, "Clearing auth state to force fresh authentication")
                            _authState = AuthState(configuration)
                            writeToDisk()
                            throw NoAuthorizationException()
                        } else {
                            Log.v(TAG, "Configuration found in existing authorization response")
                            // The configuration should already be available via getAuthorizationServiceConfiguration()
                            // If we reach here, there might be a different issue
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to retrieve authorization configuration", e)
                    throw e
                }
            }
        }
        
        fun Context.logout() {
            Log.v(TAG, "logout...")
            
            // Shutdown TidepoolSDK before clearing auth data
            try {
                TidepoolApplication.shutdownSDK()
                Log.v(TAG, "TidepoolSDK shutdown completed")
            } catch (e: Exception) {
                Log.w(TAG, "Error shutting down TidepoolSDK during logout", e)
            }
            
            _lastEmail = null
            _lastName = null
            val authService = AuthorizationService(this)
            val endSessionRequest = _authState.authorizationServiceConfiguration?.let { config ->
                _authState.idToken?.let { idToken ->
                    EndSessionRequest.Builder(config)
                        .setIdTokenHint(idToken)
                        .setPostLogoutRedirectUri(redirectUri)
                        .build()
                }
            } ?: run {
                Log.w(TAG, "Can't logout! going directly to MainActivity")
                null
            }
            
            _authState = AuthState()
            writeToDisk()
            
            endSessionRequest?.let {
                authService.performEndSessionRequest(
                    it,
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE
                    ),
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, FollowActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } ?: startActivity(Intent(this, MainActivity::class.java))
        }
        
        fun Context.clearAuthData() {
            Log.v(TAG, "Clearing authentication data...")
            _authState = AuthState()
            _lastEmail = null
            _lastName = null
            writeToDisk()
        }
        
        /**
         * Thrown if there is no last authorization response.
         * This should only be thrown if authorization isn't performed in the right order
         */
        class NoAuthorizationException : Exception("No Authorization Response")
        
        private suspend fun Context.exchangeAuthCode() = suspendCoroutine { continuation ->
            Log.v(TAG, "Exchanging authorization code for tokens...")
            val resp = _authState.lastAuthorizationResponse ?: throw NoAuthorizationException()
            
            // Additional verification to ensure we have what we need for token refresh later
            if (resp.request.configuration == null) {
                Log.e(
                    TAG,
                    "Authorization response has no configuration - this will cause refresh problems"
                )
                continuation.resumeWithException(IllegalStateException("Authorization response missing configuration"))
                return@suspendCoroutine
            }
            
            AuthorizationService(this).performTokenRequest(resp.createTokenExchangeRequest()) { newResp, ex ->
                if (ex != null) {
                    Log.e(TAG, "Token exchange failed: ${ex.error} - ${ex.errorDescription}", ex)
                    Log.e(TAG, "Error URI: ${ex.errorUri}")
                    Log.e(TAG, "Error code: ${ex.code}, Error type: ${ex.type}")
                } else {
                    Log.e(TAG, "Token exchange returned null response without error")
                }
                
                _authState.update(newResp, ex)
                
                // Verify we still have the auth response after update
                if (ex != null) {
                    continuation.resumeWithException(ex)
                } else {
                    Log.v(TAG, "Saving tokens to disk...")
                    writeToDisk()
                    continuation.resume(Unit)
                }
            }
        }
        
        suspend fun Context.getAccessToken(): String {
            Log.v(TAG, "Getting access token...")
            // Check for the specific problematic state: we have tokens but no lastAuthorizationResponse
            if ((_authState.accessToken != null || _authState.refreshToken != null) &&
                _authState.lastAuthorizationResponse == null
            ) {
                Log.w(
                    TAG,
                    "DETECTED PROBLEMATIC STATE: Have tokens but no lastAuthorizationResponse"
                )
                Log.w(
                    TAG,
                    "This prevents token refresh - clearing auth state to force re-authentication")
                
                // Clear the corrupted auth state
                _authState = AuthState()
                writeToDisk()
                
                throw NoAuthorizationException()
            }
            
            // Ensure we have the authorization configuration
            ensureAuthConfiguration()
            
            // Check if we already have tokens (from FollowActivity token exchange)
            if (_authState.accessToken != null || _authState.refreshToken != null) {
                Log.v(TAG, "Tokens already exist, skipping auth code exchange...")
            } else if (_authState.lastAuthorizationResponse != null) {
                // Wait up to 5 seconds for FollowActivity to complete token exchange
                var waitCount = 0
                while (waitCount < 50 && _authState.accessToken == null && _authState.refreshToken == null) {
                    kotlinx.coroutines.delay(100) // Wait 100ms
                    waitCount++
                    if (waitCount % 10 == 0) { // Log every second
                        Log.v(
                            TAG,
                            "Still waiting for FollowActivity token exchange... ${waitCount / 10}s elapsed"
                        )
                    }
                }
                
                if (_authState.accessToken == null && _authState.refreshToken == null) {
                    Log.e(TAG, "FollowActivity did not complete token exchange within 5 seconds")
                    Log.e(
                        TAG,
                        "This should not happen - FollowActivity should handle token exchange"
                    )
                    throw IllegalStateException("Token exchange timeout - FollowActivity did not complete token exchange")
                }
            } else {
                Log.w(TAG, "No authorization response or tokens available")
                throw NoAuthorizationException()
            }
            
            // Final configuration check before calling performActionWithFreshTokens
            if (_authState.authorizationServiceConfiguration == null) {
                Log.e(TAG, "Still no authorization configuration after setup attempts")
                Log.e(
                    TAG,
                    "AuthState details - accessToken: ${if (_authState.accessToken != null) "present" else "null"}"
                )
                Log.e(
                    TAG,
                    "AuthState details - refreshToken: ${if (_authState.refreshToken != null) "present" else "null"}"
                )
                Log.e(
                    TAG,
                    "AuthState details - lastAuthResponse: ${if (_authState.lastAuthorizationResponse != null) "present" else "null"}"
                )
                throw IllegalStateException("No authorization configuration available after retrieval attempts")
            }
            
            return suspendCancellableCoroutine { continuation ->
                val authService by lazy { AuthorizationService(this) }
                _authState.performActionWithFreshTokens(authService) { accessToken, _, ex ->
                    if (ex != null) {
                        Log.e(TAG, "Failed to get fresh token", ex)
                        continuation.resumeWithException(ex)
                    } else if (accessToken == null) {
                        Log.e(TAG, "Access token is null after refresh")
                        continuation.resumeWithException(IllegalStateException("Access token is null"))
                    } else {
                        Log.v(
                            TAG,
                            "Successfully obtained access token: ${accessToken.take(5)}..."
                        )
                        lock.withLock {
                            writeToDisk()  // Save any token updates
                        }
                        continuation.resume(accessToken)
                    }
                }
            }
        }
        
        suspend fun Context.saveEmail(name: String, email: String) {
            lock.withLock {
                _lastName = name
                _lastEmail = email
            }
            Log.v(TAG, "lastEmail: $_lastEmail, lastName: $name")
        }

        suspend fun Context.getIdToken(): String {
            // Ensure we have the authorization configuration
            ensureAuthConfiguration()
            
            // Check if we already have tokens (from FollowActivity token exchange)
            if (_authState.accessToken != null || _authState.refreshToken != null) {
                Log.v(TAG, "Tokens already exist for ID token, skipping auth code exchange...")
            } else if (_authState.lastAuthorizationResponse != null) {
                // Only exchange auth code if we have an authorization response but no tokens
                Log.v(TAG, "Have auth response but no ID token, exchanging auth code...")
                exchangeAuthCode()
            } else {
                Log.w(TAG, "No authorization response or tokens available for ID token")
                throw NoAuthorizationException()
            }
            
            return suspendCancellableCoroutine { continuation ->
                _authState.performActionWithFreshTokens(AuthorizationService(this)) { _, idToken, ex ->
                    if (ex != null) {
                        Log.e(TAG, "Failed to get fresh ID token", ex)
                        continuation.resumeWithException(ex)
                    } else if (idToken == null) {
                        Log.e(TAG, "ID token is null after refresh")
                        continuation.resumeWithException(IllegalStateException("ID token is null"))
                    } else {
                        Log.v(TAG, "Successfully obtained ID token: ${idToken.take(5)}...")
                        lock.withLock {
                            writeToDisk()
                        }
                        continuation.resume(idToken)
                    }
                }
            }
        }
        
        fun Context.getAuthRequestBuilder(): AuthorizationRequest.Builder = runBlocking {
            // Ensure we have the authorization configuration
            ensureAuthConfiguration()
            return@runBlocking AuthorizationRequest.Builder(
                _authState.authorizationServiceConfiguration
                    ?: throw AssertionError("No Configuration"),
                "tidepool-carepartner-android",
                ResponseTypeValues.CODE,
                redirectUri
            ).apply {
                setScope("openid email offline_access")
                _lastEmail?.let {
                    setLoginHint(it)
                }
            }
        }
        
        val AuthState.accessTokenExpiration: Instant?
            get() = accessTokenExpirationTime?.let { Instant.ofEpochMilli(it) }
    }
}