package org.tidepool.carepartner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.with
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import org.tidepool.carepartner.backend.DataUpdater
import org.tidepool.carepartner.backend.PersistentData
import org.tidepool.carepartner.backend.PersistentData.Companion.authState
import org.tidepool.carepartner.backend.PersistentData.Companion.logout
import org.tidepool.carepartner.backend.PillData
import org.tidepool.carepartner.ui.theme.LoopFollowTheme
import org.tidepool.sdk.CommunicationHelper
import org.tidepool.sdk.model.confirmations.Confirmation
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class FollowActivity : ComponentActivity() {
    companion object {
        const val TAG = "FollowActivity"
        val executor = Executors.newScheduledThreadPool(1)
    }

    private var future: ScheduledFuture<*>? = null
    private var updater: DataUpdater? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val resp = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        enableEdgeToEdge()
        setContent {
            if (ex != null) {
                val authService = AuthorizationService(this)
                authService.performTokenRequest(
                    resp!!.createTokenExchangeRequest()
                ) { resp, ex ->
                    authState.update(resp, ex)
                }
            }
            authState.update(resp, ex)
            LoopFollowTheme {
                App(modifier = Modifier.fillMaxSize())
            }
        }
    }

    private val communicationHelper: CommunicationHelper by lazy {
        CommunicationHelper(
            PersistentData.environment
        )
    }

    @Composable
    fun FollowPill(pillData: PillData, expanded: Boolean, inMenu: Boolean, modifier: Modifier = Modifier) {
        var expanded by remember(expanded) { mutableStateOf(expanded) }
        AnimatedContent(expanded, label = "Card",
            transitionSpec = {
                val anim = (expandVertically { it } + fadeIn()).togetherWith(shrinkVertically { it } + fadeOut())
                anim.using(SizeTransform(clip = false))
            }) {
            Card(onClick = { expanded = !expanded }, enabled = !inMenu, modifier = modifier) {

                Text(
                    "data=$pillData",
                    modifier = Modifier.padding(6.dp, 3.dp)
                )

                if (it) {
                    Text("Testing...")
                }
            }
        }
    }

    @Composable
    fun UserImage(modifier: Modifier = Modifier, id: String? = null) {
        if (id.isNullOrBlank()) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_background),
                contentDescription = "Test",
                modifier = modifier.size(20.dp)
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun App(modifier: Modifier = Modifier) {
        val mutableIds = remember { mutableStateOf(mapOf<String, PillData>()) }
        val ids by mutableIds
        val mutableInvitations = remember { mutableStateOf(arrayOf<Confirmation>()) }
        val invitations by mutableInvitations
        var menuVisible by remember { mutableStateOf(false) }

        Box {
            Scaffold(
                modifier = modifier,
                topBar = {
                    CenterAlignedTopAppBar(colors = topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    ), actions = {
                        UserImage(modifier = Modifier
                            .clickable { menuVisible = true }
                            .padding(horizontal = 5.dp))
                    }, title = {
                        Text("Following")
                    })
                }) { innerPadding ->
                LazyColumn(
                    horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier
                        .fillMaxWidth()
                        .padding(innerPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp)
                ) {
                    items(
                        items = ids.toList(),
                        key = { message ->
                            message.first.hashCode() * 31 + message.second.hashCode()
                        }) { data ->
                        FollowPill(data.second,ids.size < 2, menuVisible)
                    }
                }
            }

            AnimatedVisibility(
                visible = menuVisible,
                enter = slideInHorizontally {
                    -it
                },
                exit = slideOutHorizontally {
                    -it
                }
            ) {
                Box(Modifier.fillMaxSize().clickable(enabled = menuVisible) { menuVisible = false })
                Menu()
            }
        }

        LaunchedEffect(true) {
            updater = DataUpdater(
                mutableIds,
                mutableInvitations,
                baseContext
            )
            future = executor.scheduleWithFixedDelay(
                updater, 0, 1, TimeUnit.MINUTES
            )
        }
    }

    @Composable
    fun Menu(modifier: Modifier = Modifier) {
        Row(Modifier.clickable(false) { }) {
            Column(
                modifier = modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxHeight()
                    .width(IntrinsicSize.Min), horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Menu", fontSize = 24.sp, modifier = Modifier
                        .padding(110.dp, 15.dp)
                        .align(Alignment.CenterHorizontally)
                )
                val menuOptionModifier = Modifier.padding(5.dp, 3.dp)
                HorizontalDivider()
                Text("Manage Account", modifier = menuOptionModifier)
                HorizontalDivider()
                Text("Help Center", modifier = menuOptionModifier)
                HorizontalDivider()
                Text(text = "Logout", modifier = menuOptionModifier
                    .clickable { future?.cancel(true); logout() }
                    .fillMaxWidth())
                HorizontalDivider()

            }
            VerticalDivider()
        }
    }

    @Preview(showBackground = true, group = "component")
    @Composable
    fun MenuPreview() {
        LoopFollowTheme {
            Menu()
        }
    }

    @Preview(showBackground = false, group = "component")
    @Composable
    fun FollowerPreview() {
        LoopFollowTheme {
            FollowPill(PillData(130.0), expanded = false, inMenu = false)
        }
    }

    @Preview(showBackground = false, group = "component")
    @Composable
    fun ExpandedFollowerPreview() {
        LoopFollowTheme {
            FollowPill(PillData(130.0), expanded = true, inMenu = false)
        }
    }
}