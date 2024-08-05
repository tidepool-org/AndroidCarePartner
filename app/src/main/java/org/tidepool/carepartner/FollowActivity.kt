package org.tidepool.carepartner

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize.Min
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import org.tidepool.carepartner.backend.DataUpdater
import org.tidepool.carepartner.backend.PersistentData.Companion.authState
import org.tidepool.carepartner.backend.PersistentData.Companion.logout
import org.tidepool.carepartner.backend.PillData
import org.tidepool.carepartner.ui.theme.*
import org.tidepool.sdk.model.confirmations.Confirmation
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource

class FollowActivity : ComponentActivity() {
    companion object {
        const val TAG = "FollowActivity"
        val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
    }

    private var future: ScheduledFuture<*>? = null
    private var updater: DataUpdater? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val resp = AuthorizationResponse.fromIntent(intent)
        val ex = AuthorizationException.fromIntent(intent)
        enableEdgeToEdge()
        setContent {
            if (resp != null) {
                val authService = AuthorizationService(this)
                authService.performTokenRequest(
                    resp.createTokenExchangeRequest()
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
    
    /**
     * Card that displays the information for a single user
     * @param pillData The data to display
     * @param mutableExpanded The state the determines if the card is expanded
     * @param inMenu if the menu is open
     * @param modifier The [Modifier] to apply
     */
    @Composable
    fun FollowPill(pillData: PillData, mutableExpanded: MutableState<Boolean>, inMenu: Boolean, modifier: Modifier = Modifier) {
        var expanded by mutableExpanded
        AnimatedContent(expanded, label = "Card",
            transitionSpec = {
                val anim = (expandVertically { it } + fadeIn()).togetherWith(shrinkVertically { it } + fadeOut())
                anim.using(SizeTransform(clip = false))
            }) { cardExpanded ->
            val outerCardColor = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            Card(onClick = { expanded = !expanded }, enabled = !inMenu, modifier = modifier, colors=outerCardColor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    UserImage(pillData)
                    Text(
                        text = pillData.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 20.sp,
                        lineHeight = 24.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        if (cardExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = "expand arrow"
                    )
                }
                val innerCardColor = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                    .fillMaxWidth()
                    .height(Min)
                    .width(Min)) {
                    Card(colors = innerCardColor, modifier = Modifier
                        .padding(10.dp)
                        .width(120.dp)
                        .fillMaxHeight()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier
                            .padding(5.dp)
                            .fillMaxSize()) {
                            Spacer(modifier = Modifier)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = pillData.bg?.roundToInt()?.toString() ?: "---",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 30.sp,
                                    lineHeight = 35.8.sp
                                )
                                Text(
                                    text = "mg/dL",
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 11.sp,
                                    color = Grey0300
                                )
                            }
                        }
                    }
                    Image(
                        painter = painterResource(id = R.drawable.loop_indicator),
                        contentDescription = "looping"
                    )
                    Card(colors = innerCardColor,modifier = Modifier
                        .padding(10.dp)
                        .width(120.dp)
                        .fillMaxHeight()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier
                            .padding(5.dp)
                            .fillMaxSize()) {
                            Text(
                                text = pillData.basalRate?.let {
                                    String.format(Locale.getDefault(), "%1.2f", it)
                                } ?: "---",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 30.sp,
                                lineHeight = 35.8.sp,
                                color = LoopTheme.current.insulin
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "U/hr",
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = Grey0300
                            )
                        }
                    }
                }

                if (cardExpanded) {
                    Card(colors = innerCardColor, modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth()) {
                        DetailedInfo(
                            title = "Change in Glucose",
                            name = "Reading",
                            lastInstance = pillData.lastGlucose,
                            modifier = Modifier.padding(5.dp)
                        ) {
                            Row {
                                val change = pillData.glucoseChange?.roundToInt()
                                Text(
                                    text = change?.toString() ?: "---",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp,
                                    lineHeight = 28.64.sp,
                                    color = LoopTheme.current.bloodGlucose
                                )
                            }
                        }
                        HorizontalDivider()
                        DetailedInfo(title = "Active Insulin",
                            name = "Bolus",
                            lastInstance = pillData.lastBolus,
                            modifier = Modifier.padding(5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                val text = pillData.activeInsulin?.amount?.let {
                                    String.format(Locale.getDefault(), "%1.2f", it)
                                } ?: "---"
                                Text(
                                    text = text,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp,
                                    lineHeight = 28.64.sp,
                                    color = LoopTheme.current.insulin
                                )
                                Text(
                                    text = "U",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    lineHeight = 19.09.sp,
                                    color = LoopTheme.current.insulin,
                                    modifier = Modifier.padding(start = 2.dp, end = 5.dp)
                                )
                            }
                        }
                        HorizontalDivider()
                        DetailedInfo(
                            title = "Active Carbs",
                            name = "Entry",
                            lastInstance = pillData.lastCarbEntry,
                            modifier = Modifier.padding(5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = pillData.activeCarbs?.amount?.roundToInt()?.toString() ?: "---",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp,
                                    lineHeight = 28.64.sp,
                                    color = LoopTheme.current.carbohydrates
                                )
                                Text(
                                    text = "g",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    lineHeight = 19.09.sp,
                                    color = LoopTheme.current.carbohydrates,
                                    modifier = Modifier.padding(start = 2.dp, end = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    
    /**
     * The detailed information that is displayed when the follow pill is expanded.
     * @param title The large text that describes the information in [display]
     * @param name The text to put after "Last" to describe the time ago
     * @param lastInstance The last instance of this thing occurring. If it is null, it will not display a time ago.
     * @param modifier The modifier for this detailedInfo
     * @param display The display for the text that shows the amount.
     */
    @Composable
    fun DetailedInfo(title: String, name: String, lastInstance: Instant?, modifier: Modifier = Modifier, display: @Composable () -> Unit) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Normal,
                    fontSize = 17.sp,
                    lineHeight = 20.29.sp
                )
                var minutesPast by remember(lastInstance) { mutableStateOf(lastInstance?.until(Instant.now())) }
                val text = minutesPast?.let { diff ->
                    if (diff >= 1.hours) {
                        val hours = diff.inWholeHours
                        "$hours hour${if (hours != 1L) "s" else ""} ago"
                    } else {
                        val minutes = diff.inWholeMinutes
                        "$minutes min${if (minutes != 1L) "s" else ""} ago"
                    }
                    
                } ?: ""
                Text(
                    text = "Last $name: $text",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.71.sp,
                    color = Color(0xFF92949C)
                )
                LaunchedEffect(key1 = minutesPast) {
                    lastInstance?.let { last ->
                        val timeSince = last.until(Instant.now())
                        val toDelay = if (timeSince <= 1.hours) {
                            (timeSince.inWholeMinutes + 1).minutes - timeSince
                        } else {
                            (timeSince.inWholeHours + 1).hours - timeSince
                        }
                        delay(toDelay)
                        minutesPast = last.until(Instant.now())
                    }
                }
            }
            display()
        }
    }
    
    /**
     * The User icon of the current user
     * @param pillData The data to use for displaying the user icon
     */
    @Composable
    fun UserImage(pillData: PillData, modifier: Modifier = Modifier) {
        Icon(
            Icons.Filled.AccountCircle,
            contentDescription = "${pillData.name} image",
            modifier = modifier
                .padding(horizontal = 5.dp)
        )
    }
    
    /**
     * The application to render. This is not in the callback so that if there is a method to
     * simulate communication with the backend, the entire app can be displayed with dummy data.
     */
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
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = "account",
                            modifier = Modifier
                                .clickable { menuVisible = true }
                                .padding(horizontal = 5.dp)
                        )
                    }, title = {
                        Text("Following")
                    })
                }) { innerPadding ->
                val states = remember { HashMap<String, MutableState<Boolean>>() }
                var closedInitial by remember { mutableStateOf(false) }
                var toggle by remember { mutableStateOf(true) }
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
                            message.hashCode()
                        }) { (id, data) ->
                        if (ids.size > 1 && !closedInitial) {
                            states.computeIfAbsent(id) { mutableStateOf(false) }.value = false
                            closedInitial = true
                        }
                        val state = states.computeIfAbsent(id) { mutableStateOf(ids.size < 2) }
                        FollowPill(data, state, menuVisible)
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(enabled = menuVisible) { menuVisible = false })
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
                    .width(Min), horizontalAlignment = Alignment.Start
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

    @Preview(showBackground = true, group = "mockup")
    @Composable
    fun MenuPreview() {
        LoopFollowTheme {
            Menu()
        }
    }

    @Preview(name = "Light Mode", showBackground = false, group = "component")
    @Preview(name = "Dark Mode", showBackground = false, group = "dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun FollowerPreview() {
        LoopFollowTheme {
            FollowPill(
                PillData(130.0),
                mutableExpanded = remember { mutableStateOf(false) },
                inMenu = false
            )
        }
    }
    
    @Preview(name = "Light Mode", showBackground = false, group = "component")
    @Preview(name = "Dark Mode", showBackground = false, group = "dark mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun ExpandedFollowerPreview() {
        LoopFollowTheme {
            val lastReading = Instant.now().minus(1L, ChronoUnit.MINUTES)
            val lastBolus = Instant.now().minus(2L, ChronoUnit.MINUTES)
            val lastEntry = Instant.now().minus(1L, ChronoUnit.HOURS)
            FollowPill(
                PillData(
                    130.0,
                    lastGlucose = lastReading,
                    lastBolus = lastBolus,
                    lastCarbEntry = lastEntry,
                    glucoseChange = -5.0
                ),
                mutableExpanded = remember { mutableStateOf(true) },
                inMenu = false
            )
        }
    }
}

fun Instant.until(other: Instant): Duration {
    return until(other, ChronoUnit.NANOS).nanoseconds
}