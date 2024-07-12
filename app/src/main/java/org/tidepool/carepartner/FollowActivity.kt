package org.tidepool.carepartner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.tidepool.carepartner.backend.BackendViewModel
import org.tidepool.carepartner.ui.theme.LoopFollowTheme

class FollowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val backendViewModel: BackendViewModel = viewModel()
            LoopFollowTheme {
                App(modifier = Modifier.fillMaxSize())
            }
        }
    }
    
    @Composable
    fun FollowPill(id: String, modifier: Modifier = Modifier) {
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
    fun UserImage(id: String? = null, modifier: Modifier = Modifier) {
        if (id.isNullOrBlank()) {
            Image(painter = painterResource(id = R.drawable.ic_launcher_background),
                contentDescription = "Test",
                modifier=modifier.size(20.dp))
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun App(modifier: Modifier = Modifier, idSupplier: () -> Array<String> = this::getIds) {
        Scaffold(modifier = modifier, topBar = {
            TopAppBar(colors= topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),title = {
                Following()
            })
        }) { innerPadding ->
            val scrollState: ScrollState = rememberScrollState();
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (id: String in idSupplier()) {
                    FollowPill(id)
                }
            }
        }
    }

    private fun getIds(): Array<String> {
        return arrayOf("Testing 1")
    }

    private fun getTestingIds(): Array<String> {
        return Array(20) { index ->
            "Testing ${index + 1}"
        }
    }

    @Preview(showBackground = false, group = "component")
    @Composable
    fun FollowerPreview() {
        LoopFollowTheme {
            FollowPill(id = "Preview Test")
        }
    }

    @Preview(showBackground = true, group = "mockup",
        device = "spec:id=reference_phone,shape=Normal,width=411,height=891,unit=dp,dpi=420",
        showSystemUi = true
    )
    @Composable
    fun ScrollTest() {
        LoopFollowTheme {
            App(modifier = Modifier.fillMaxSize(), idSupplier = this::getTestingIds)
        }
    }
}