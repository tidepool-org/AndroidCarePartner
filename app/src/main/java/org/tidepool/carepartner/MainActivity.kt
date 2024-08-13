package org.tidepool.carepartner

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.runtime.*
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationService
import org.tidepool.carepartner.backend.PersistentData
import org.tidepool.carepartner.backend.PersistentData.Companion.NoAuthorizationException
import org.tidepool.carepartner.backend.PersistentData.Companion.accessTokenExpiration
import org.tidepool.carepartner.backend.PersistentData.Companion.getAccessToken
import org.tidepool.carepartner.backend.PersistentData.Companion.readFromDisk
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @SuppressLint("SourceLockedOrientationActivity")
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        numRetries = 0
        setContent {
            HomeUI()
            LaunchedEffect(true) {
                baseContext.readFromDisk()
                try {
                    withTimeout(15.seconds) {
                        getAccessToken()
                    }
                } catch (_: TimeoutCancellationException) {
                    // don't care if it times out
                } catch (_: AuthorizationException) {
                    // don't care if access token get fails
                } catch (_: NoAuthorizationException) {
                    // don't care if it can't perform the authorization
                }
                
                if (
                    PersistentData.authState.accessTokenExpiration?.let {
                        Instant.now().until(it) > 10.seconds
                    } == true
                ) {
                    startActivity(Intent(baseContext, FollowActivity::class.java))
                } else if (PersistentData.lastEmail != null) {
                    authorize()
                }
            }
        }
    }
}

fun Context.authorize() {
    AuthorizationService(this).performAuthorizationRequest(
        PersistentData.getAuthRequestBuilder().build(),
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, FollowActivity::class.java),
            PendingIntent.FLAG_MUTABLE
        ),
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_MUTABLE
        )
    )
}