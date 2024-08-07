package org.tidepool.carepartner

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import net.openid.appauth.AuthorizationService
import org.tidepool.carepartner.backend.PersistentData
import org.tidepool.carepartner.backend.PersistentData.Companion.accessTokenExpiration
import org.tidepool.carepartner.backend.PersistentData.Companion.readFromDisk
import org.tidepool.carepartner.ui.theme.LoopFollowTheme
import org.tidepool.carepartner.ui.theme.LoopTheme
import org.tidepool.sdk.Environments
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @SuppressLint("SourceLockedOrientationActivity")
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        baseContext.readFromDisk()
        if (
            PersistentData.authState.accessTokenExpiration?.let {
                Instant.now().until(it) > 10.seconds
            } == true
        ) {
            startActivity(Intent(this, FollowActivity::class.java))
        } else {
            PersistentData.renewAuthState()
            if (PersistentData.lastEmail != null) {
                authorize()
            }
        }
        setContent {
            LoopFollowTheme {
                
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxHeight()) {
                                Box {
                                    var dropdownExpanded by remember { mutableStateOf(false) }
                                    var selectedEnv by remember { mutableStateOf(PersistentData.environment) }
                                    val interactionSource = remember { MutableInteractionSource() }
                                    
                                    //Image(painter = painterResource(id = R.drawable.logo), contentDescription = "Tidepool Logo")
                                    Image(
                                        painterResource(id = R.drawable.loop_carepartner_icon),
                                        //Icons.Filled.AccountCircle,
                                        contentDescription = "Temp",
                                        modifier = Modifier.clickable(
                                            indication = null,
                                            interactionSource = interactionSource
                                        ) {}.size(DpSize(180.dp, 180.dp))
                                    )
                                    
                                    DropdownMenu(
                                        expanded = dropdownExpanded,
                                        onDismissRequest = {
                                            dropdownExpanded = false
                                            PersistentData.environment = selectedEnv
                                            PersistentData.renewAuthState()
                                            Toast.makeText(baseContext, "Switched to environment ${selectedEnv.envCode}", Toast.LENGTH_LONG).show()
                                        },
                                    ) {
                                        Environments.entries.forEach {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(text = it.envCode)
                                                },
                                                onClick = {
                                                    selectedEnv = it
                                                }
                                            )
                                        }
                                    }
                                    LaunchedEffect(key1 = interactionSource) {
                                        interactionSource.interactions.collectLatest { interaction ->
                                            if (interaction is PressInteraction.Press) {
                                                delay(2.seconds)
                                                dropdownExpanded = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier =  Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxHeight()) {
                                Button(
                                    onClick = { authorize() },
                                    //modifier = Modifier.padding(bottom = 60.dp),
                                    colors = LoopTheme.current.buttonColors
                                ) {
                                    Text(text = "Log In")
                                }
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