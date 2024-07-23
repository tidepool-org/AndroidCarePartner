package org.tidepool.carepartner

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import org.tidepool.carepartner.backend.PersistentData
import org.tidepool.carepartner.backend.PersistentData.Companion.getAccessToken
import org.tidepool.carepartner.backend.PillData
import org.tidepool.carepartner.ui.theme.LoopFollowTheme
import org.tidepool.sdk.CommunicationHelper
import org.tidepool.sdk.model.BloodGlucose
import org.tidepool.sdk.model.BloodGlucose.Units.Companion.convert
import org.tidepool.sdk.model.data.BaseData
import org.tidepool.sdk.model.data.ContinuousGlucoseData
import java.time.Instant
import java.time.temporal.ChronoUnit

class FollowActivity : ComponentActivity() {
    companion object {
        const val TAG = "FollowActivity"
    }
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
                    PersistentData.authState.update(resp, ex)
                }
            }
            PersistentData.authState.update(resp, ex)
            LoopFollowTheme {
                App(modifier = Modifier.fillMaxSize())
            }
        }
    }

    private val communicationHelper: CommunicationHelper by lazy { CommunicationHelper(PersistentData.environment) }
    
    @Composable
    fun FollowPill(id: PillData, modifier: Modifier = Modifier) {
        Card (modifier = modifier) {
            Text(
                "Follower pill here (id: $id)",
                modifier = Modifier.padding(6.dp, 3.dp)
            )
        }
    }

    @Composable
    fun Following(modifier: Modifier = Modifier) {
        Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = modifier.weight(1f))
            Text("Following", modifier=modifier)
            Spacer(modifier = modifier.weight(1f))
            UserImage(modifier = modifier)
        }
    }

    @Composable
    fun UserImage(modifier: Modifier = Modifier, id: String? = null) {
        if (id.isNullOrBlank()) {
            Image(painter = painterResource(id = R.drawable.ic_launcher_background),
                contentDescription = "Test",
                modifier=modifier.size(20.dp))
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun App(modifier: Modifier = Modifier, userIdSupplier: CoroutineScope.(Context) -> ReceiveChannel<String> = CoroutineScope::getIds, dataSupplier: suspend (String, Context) -> PillData = this::getData) {
        var ids = remember { mutableStateOf(mapOf<String, PillData>()) }
        Scaffold(modifier = modifier, topBar = {
            TopAppBar(colors= topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),title = {
                Following()
            })
        }) { innerPadding ->
            LazyColumn(
                horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier
                    .fillMaxWidth()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(
                    items = ids.value.toList(),
                    key = {
                        message -> message.first.hashCode() * 31 + message.second.hashCode()
                    }) { data ->
                    FollowPill(data.second)
                }
            }
        }

        LaunchedEffect(true) {
            for (id in userIdSupplier(baseContext)) {
                Log.v(TAG, "Id supplied: $id")
                val mutable = ids.value.toMutableMap()
                mutable[id] = dataSupplier(id, baseContext)
                ids.value = mutable.toMap()
            }
            Log.v(TAG, "Finished iterating through channel")
        }
    }



    @Preview(showBackground = false, group = "component")
    @Composable
    fun FollowerPreview() {
        LoopFollowTheme {
            FollowPill(PillData(130.0))
        }
    }

    @Preview(showBackground = true, group = "mockup",
        device = "spec:id=reference_phone,shape=Normal,width=411,height=891,unit=dp,dpi=420",
        showSystemUi = true
    )
    @Composable
    fun ScrollTest() {
        LoopFollowTheme {
            App(modifier = Modifier.fillMaxSize(), userIdSupplier = { _ -> getTestingIds() }) { _, _ ->
                dummy()
            }
        }
    }

    private suspend fun getData(id: String, context: Context): PillData {
        val result = communicationHelper.data.getDataForUser(context.getAccessToken(), userId = id, types= arrayOf("cbg"), startDate = Instant.now().minus(1, ChronoUnit.DAYS))
        Log.v(TAG, "getData result Array Length: ${result.size}")
        val data = result.filter { value ->
            value.type == BaseData.DataType.cbg
        }.maxBy { value ->
            value.time ?: Instant.MIN
        } as ContinuousGlucoseData?

        Log.v(TAG, "DATA: $data")
        Log.v(TAG, "Time: ${data?.time.toString()}")
        var mgdl: Double = -1.0
        val value = data?.value
        if (value != null) {
            mgdl = data.units.convert(value, BloodGlucose.Units.milligramsPerDeciliter)
        }
        Log.v(TAG, "Value: $mgdl mg/dL")
        return PillData(mgdl)
    }
}

private suspend fun dummy(): PillData {
    delay(1_000L)
    return PillData(-1.0)
}

private fun CoroutineScope.getTestingIds(): ReceiveChannel<String> {
    val channel = Channel<String>()
    launch {
        for (i in 1..20) {
            channel.send("Testing $i")
        }
        channel.close()
    }
    return channel
}

private fun CoroutineScope.getIds(context: Context): ReceiveChannel<String> {
    val channel = Channel<String>()
    val helper = CommunicationHelper(PersistentData.environment)
    launch {
        channel.send(helper.users.getCurrentUserInfo(context.getAccessToken()).userid)
        channel.close()
    }
    return channel
}