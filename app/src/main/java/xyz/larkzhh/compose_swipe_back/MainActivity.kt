package xyz.larkzhh.compose_swipe_back

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import xyz.larkzhh.compose_swipe_back.ui.theme.ComposeswipebackTheme
import xyz.larkzhh.swipeback.ScrimBox
import xyz.larkzhh.swipeback.SwipeBackScaffold

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComposeswipebackTheme {
                SwipeBackDemo()
            }
        }
    }
}

@Composable
private fun SwipeBackDemo() {
    var page by remember { mutableIntStateOf(1) }

    BackHandler(enabled = page > 1) { page -= 1 }

    SwipeBackScaffold(
        backEnabled = page > 1,
        forwardPeek = { PeekPage(page + 1) },
        onCommitForward = { page += 1 },
        revealEntryId = { "page-$page" },
    ) {
        ScrimBox(entryId = "page-$page") {
            PageContent(page = page, onNext = { page += 1 })
        }
    }
}

@Composable
private fun PageContent(page: Int, onNext: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Page $page", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "Swipe right to go back, swipe left to peek the next page",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onNext) { Text("Next page") }
        }
    }
}

@Composable
private fun PeekPage(page: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1F6FEB)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Page $page",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}