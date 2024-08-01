package org.tidepool.carepartner

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import net.openid.appauth.AuthorizationService
import org.tidepool.carepartner.backend.PersistentData
import org.tidepool.carepartner.ui.theme.LoopFollowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LoopFollowTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PersistentData.renewAuthState()
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxHeight()) {
                            Button(
                                onClick = { authorize() },
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                Text(text = "Log In")
                            }
                        }
                    }
                }
            }
        }
    }

    private val authService: AuthorizationService by lazy { AuthorizationService(this) }

    private fun authorize() {
        authService.performAuthorizationRequest(
            PersistentData.getAuthRequestBuilder().build(),
            PendingIntent.getActivity(this, 0, Intent(this, FollowActivity::class.java), PendingIntent.FLAG_MUTABLE),
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_MUTABLE)
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LoopFollowTheme {
        Greeting("Android")
    }
}