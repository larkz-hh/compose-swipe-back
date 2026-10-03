package xyz.larkzhh.swipeback

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * State shared between [SwipeBackScaffold] and [ScrimBox] while a back gesture is in progress.
 *
 * [SwipeBackScaffold] writes to this object during the gesture and [ScrimBox] reads from it, so a page can
 * dim itself without threading the gesture progress through the composition.
 */
object SwipeBackScrimState {
    /** Id of the entry being revealed, or `null` when no back gesture is in progress. */
    var revealEntryId by mutableStateOf<String?>(null)

    /** Progress of the back gesture in `0f..1f`, where `1f` means fully swiped. */
    var progress by mutableFloatStateOf(0f)
}

/**
 * A container that dims its [content] while the page is being revealed by a back gesture.
 *
 * Wrap the content of every destination that participates in the gesture and pass the same id that
 * [SwipeBackScaffold] reports through its `revealEntryId` parameter.
 *
 * @param entryId id of the navigation back stack entry this page belongs to. It is compared against
 *   [SwipeBackScrimState.revealEntryId] to decide whether this page is the one being revealed.
 * @param maxAlpha alpha of the scrim at the start of the gesture. It fades out as the gesture progresses.
 * @param scrimColor color of the scrim. Defaults to black.
 * @param content the content of the page.
 */
@Composable
fun ScrimBox(
    entryId: String?,
    maxAlpha: Float = 0.3f,
    scrimColor: Color = Color.Black,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        content()
        val isRevealTarget = entryId != null && SwipeBackScrimState.revealEntryId == entryId
        val alpha = if (isRevealTarget) {
            ((1f - SwipeBackScrimState.progress) * maxAlpha).coerceIn(0f, maxAlpha)
        } else 0f
        if (alpha > 0.001f) {
            Box(Modifier.matchParentSize().background(scrimColor.copy(alpha = alpha)))
        }
    }
}