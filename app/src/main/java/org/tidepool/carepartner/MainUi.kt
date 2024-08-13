package org.tidepool.carepartner

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.tidepool.carepartner.backend.PersistentData
import org.tidepool.carepartner.ui.theme.LoopFollowTheme
import org.tidepool.carepartner.ui.theme.LoopTheme
import org.tidepool.sdk.Environments
import kotlin.time.Duration.Companion.seconds

@Composable
fun HomeUI() {
    val context = LocalContext.current
    LoopFollowTheme {
        
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Box {
                            var dropdownExpanded by remember { mutableStateOf(false) }
                            var selectedEnv by remember { mutableStateOf(PersistentData.environment) }
                            val interactionSource = remember { MutableInteractionSource() }
                            
                            // Image(painter = painterResource(id = R.drawable.logo), contentDescription = "Tidepool Logo")
                            Image(
                                painterResource(id = R.drawable.loop_carepartner_icon),
                                // Icons.Filled.AccountCircle,
                                contentDescription = "Loop Shadow Logo",
                                modifier = Modifier
                                    .clickable(
                                        indication = null,
                                        interactionSource = interactionSource
                                    ) {}
                                    .size(DpSize(180.dp, 180.dp))
                            )
                            
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = {
                                    dropdownExpanded = false
                                    PersistentData.environment = selectedEnv
                                    Toast.makeText(
                                        context,
                                        "Switched to environment ${selectedEnv.envCode}",
                                        Toast.LENGTH_LONG
                                    ).show()
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxHeight()) {
                        Button(
                            onClick = {
                                context.authorize()
                            },
                            // modifier = Modifier.padding(bottom = 60.dp),
                            colors = LoopTheme.current.buttonColors
                        ) {
                            Text(stringResource(R.string.log_in))
                        }
                    }
                }
            }
        }
    }
}