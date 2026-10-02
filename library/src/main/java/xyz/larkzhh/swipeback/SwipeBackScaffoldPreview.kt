package xyz.larkzhh.swipeback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

private const val PreviewEntryId = "preview"

@Preview(showBackground = true, name = "LTR")
@Composable
private fun SwipeBackScaffoldPreview() {
    PreviewScaffold()
}

@Preview(showBackground = true, name = "RTL")
@Composable
private fun SwipeBackScaffoldRtlPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        PreviewScaffold()
    }
}

@Composable
private fun PreviewScaffold() {
    SwipeBackScaffold(
        backEnabled = true,
        forwardPeek = { PreviewPeek() },
        onBack = {},
        onCommitForward = {},
        revealEntryId = { PreviewEntryId },
    ) {
        ScrimBox(entryId = PreviewEntryId) { PreviewPage() }
    }
}

@Composable
private fun PreviewPage() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = "Current page", style = TextStyle(fontSize = 20.sp))
    }
}

@Composable
private fun PreviewPeek() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1F6FEB)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Next page",
            style = TextStyle(color = Color.White, fontSize = 20.sp),
        )
    }
}