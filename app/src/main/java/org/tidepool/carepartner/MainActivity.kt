package org.tidepool.carepartner

import android.app.PendingIntent
import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
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
import org.tidepool.carepartner.backend.UriDeserializer
import org.tidepool.carepartner.ui.theme.LoopFollowTheme
import org.tidepool.sdk.CommunicationHelper
import org.tidepool.sdk.Environments
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        baseContext.readFromDisk()
        PersistentData.renewAuthState()
        if (PersistentData.authState.accessTokenExpiration?.let {
                Instant.now().until(it, ChronoUnit.MILLIS).milliseconds.isPositive()
            } == true || PersistentData.lastEmail != null
        ) {
            authorize()
        }
        setContent {
            LoopFollowTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box {
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier =  Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxHeight()) {
                                Box {
                                    var dropdownExpanded by remember { mutableStateOf(false) }
                                    var selectedEnv by remember { mutableStateOf(PersistentData.environment) }
                                    val interactionSource = remember { MutableInteractionSource() }
                                    
                                    //Image(painter = painterResource(id = R.drawable.logo), contentDescription = "Tidepool Logo")
                                    Image(
                                        painterResource(id = R.drawable.image),
                                        //Icons.Filled.AccountCircle,
                                        contentDescription = "Temp",
                                        modifier = Modifier.clickable(
                                            indication = null,
                                            interactionSource = interactionSource
                                        ) {}.padding(bottom = 10.dp).size(DpSize(40.dp, 40.dp))
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