package org.tidepool.carepartner

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import org.tidepool.carepartner.backend.PersistentData
import org.tidepool.carepartner.backend.PersistentData.Companion.writeToDisk
import org.tidepool.carepartner.ui.theme.LoopFollowTheme
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

class FollowActivity : ComponentActivity() {
    companion object {
        
        const val TAG = "FollowActivity"
        val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    }
    
    private lateinit var ui: FollowUI
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = FollowUI().apply { lifecycle.addObserver(this) }
        val resp = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        
        // Debug the authorization response and exception
        android.util.Log.d(TAG, "=== FollowActivity onCreate Debug ===")
        android.util.Log.d(
            TAG,
            "Authorization response from intent: ${if (resp != null) "PRESENT" else "NULL"}"
        )
        android.util.Log.d(
            TAG,
            "Authorization exception from intent: ${if (ex != null) "PRESENT (${ex.error})" else "NULL"}"
        )
        if (resp != null) {
            android.util.Log.d(TAG, "Response details:")
            android.util.Log.d(TAG, "  - Authorization code: ${resp.authorizationCode}")
            android.util.Log.d(TAG, "  - Access token: ${resp.accessToken}")
            android.util.Log.d(TAG, "  - State: ${resp.state}")
            android.util.Log.d(
                TAG,
                "  - Configuration present: ${resp.request.configuration != null}"
            )
        }
        android.util.Log.d(TAG, "=== End FollowActivity Debug ===")
        
        @SuppressLint("SourceLockedOrientationActivity")
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (resp != null) {
            android.util.Log.v(TAG, "Processing authorization response in FollowActivity")
            android.util.Log.v(TAG, "Auth response - code: ${resp.authorizationCode != null}")
            android.util.Log.v(TAG, "Auth response - access token: ${resp.accessToken != null}")
            android.util.Log.v(TAG, "Auth response - state: ${resp.state}")
            
            // First update the auth state with the authorization response
            PersistentData.authState.update(resp, ex)
            writeToDisk()
            
            android.util.Log.v(TAG, "Auth state updated with authorization response")
            android.util.Log.v(
                TAG,
                "Auth state - lastAuthResponse: ${PersistentData.authState.lastAuthorizationResponse != null}"
            )
            android.util.Log.v(
                TAG,
                "Auth state - configuration: ${PersistentData.authState.authorizationServiceConfiguration != null}"
            )
            
            val authService = AuthorizationService(this)
            authService.performTokenRequest(
                resp.createTokenExchangeRequest()
            ) { newResp, newEx ->
                when {
                    newEx != null   -> android.util.Log.e(TAG, "Token exchange failed in FollowActivity", newEx)
                    
                    newResp != null -> {
                        android.util.Log.v(TAG, "Token exchange successful in FollowActivity")
                        android.util.Log.v(
                            TAG,
                            "Token response - access token: ${newResp.accessToken != null}"
                        )
                        android.util.Log.v(
                            TAG,
                            "Token response - refresh token: ${newResp.refreshToken != null}"
                        )
                        android.util.Log.v(
                            TAG,
                            "Token response - ID token: ${newResp.idToken != null}"
                        )
                    }
                    else            -> android.util.Log.e(TAG, "Token exchange returned null response without error in FollowActivity")
                }
                // Update with the token response
                PersistentData.authState.update(newResp, newEx)
                
                android.util.Log.v(TAG, "Auth state updated with token response")
                android.util.Log.v(
                    TAG,
                    "Final auth state - lastAuthResponse: ${PersistentData.authState.lastAuthorizationResponse != null}"
                )
                android.util.Log.v(
                    TAG,
                    "Final auth state - configuration: ${PersistentData.authState.authorizationServiceConfiguration != null}"
                )
                android.util.Log.v(
                    TAG,
                    "Final auth state - isAuthorized: ${PersistentData.authState.isAuthorized}"
                )
                
                writeToDisk()
            }
        } else if (ex != null) {
            // Handle authorization error
            android.util.Log.w(TAG, "Authorization failed", ex)
            PersistentData.authState.update(null as AuthorizationResponse?, ex)
            writeToDisk()
        }
        val backPressed = mutableStateOf(false)
        onBackPressedDispatcher.addCallback(this) {
            backPressed.value = true
        }
        enableEdgeToEdge()
        setContent {
            LoopFollowTheme {
                ui.App(modifier = Modifier.fillMaxSize(), backPressed = backPressed)
            }
        }
    }
}

fun Instant.until(other: Instant): Duration {
    return until(other, ChronoUnit.NANOS).nanoseconds
}

operator fun Instant.plus(duration: Duration): Instant {
    return plusNanos(duration.inWholeNanoseconds)
}

operator fun Instant.minus(duration: Duration): Instant {
    return minusNanos(duration.inWholeNanoseconds)
}
