package xyz.larkzhh.swipeback

import android.annotation.SuppressLint
import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** Settling cooldown after a back gesture is committed or cancelled, in milliseconds. */
private const val BACK_COOLDOWN_MS = 280L

/** Gesture progress at which a right swipe activates the predictive back session. */
private const val BACK_ACTIVATION = 0.05f

/** Velocity above which a horizontal drag counts as a fling, in dp per second. */
private const val FLING_VELOCITY_DP = 320f

/**
 * A gesture driven container that turns horizontal drags into navigation transitions.
 *
 * Swiping right pops the current destination with the predictive back animation, swiping left peeks the
 * next destination and commits a forward navigation when the drag passes [forwardThreshold].
 *
 * ```kotlin
 * SwipeBackScaffold(
 *     backEnabled = canGoBack,
 *     forwardPeek = { NextPage() },
 *     onCommitForward = { navigateForward() },
 *     revealEntryId = { backStackEntry.id },
 * ) {
 *     ScrimBox(entryId = backStackEntry.id) { CurrentPage() }
 * }
 * ```
 *
 * @param backEnabled whether swiping right pops the current destination.
 * @param backThreshold fraction of the container width a drag must cover to commit a back navigation,
 *   in `0f..1f`. Defaults to one third of the width.
 * @param forwardPeek content revealed underneath while swiping left, or `null` to disable forward swiping.
 * @param forwardThreshold fraction of the container width a drag must cover to commit a forward
 *   navigation, in `0f..1f`. Defaults to one quarter of the width.
 * @param onCommitForward invoked after a forward swipe is committed and the peek content has been animated
 *   into place.
 * @param dragSensitivity damping applied to finger movement, where the content offset equals
 *   `finger delta * dragSensitivity`. Values below `1f` give a rubber band feel.
 * @param tabContentRegion returns whether the touch position belongs to a horizontally scrollable region
 *   such as a tab row. Pass `null` when the whole container is a single non-tab page.
 * @param excludeRegion returns `true` to let the gesture reach children unchanged, given the pointer down
 *   position in this container's local coordinates and the container size in pixels. Use it for regions
 *   that own horizontal drags, such as a video seek bar. The container size is used for the conversion, so
 *   the coordinate system always matches the position and no measurement passes are involved.
 * @param tabAtLeftmost returns whether the currently selected tab is the leftmost one. A back gesture only
 *   starts from the leftmost tab of a tab region.
 * @param revealEntryId id of the back stack entry revealed while swiping back. Pass the same id to
 *   [ScrimBox] so the current page dims during the gesture.
 * @param content the content of the current page.
 */
@SuppressLint("VisibleForTests")
@Composable
fun SwipeBackScaffold(
    backEnabled: Boolean,
    backThreshold: Float = 1f / 3f,
    forwardPeek: (@Composable () -> Unit)? = null,
    forwardThreshold: Float = 1f / 4f,
    onCommitForward: () -> Unit = {},
    dragSensitivity: Float = 0.6f,
    tabContentRegion: ((Offset) -> Boolean)? = null,
    excludeRegion: ((pos: Offset, size: IntSize) -> Boolean)? = null,
    tabAtLeftmost: () -> Boolean = { true },
    revealEntryId: () -> String? = { null },
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val screenWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    val flingVelocityPx = with(density) { FLING_VELOCITY_DP.dp.toPx() }
    val scope = rememberCoroutineScope()
    val forwardActive = forwardPeek != null
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val backActive = backEnabled && backDispatcher != null
    val hasTabRegion = tabContentRegion != null
    val offsetX = remember { Animatable(0f) } // Offset applied while peeking the next destination.

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (backActive || forwardActive) {
                    Modifier.pointerInput(backActive, forwardActive, hasTabRegion, excludeRegion != null) {
                        var gestureIsBack: Boolean? = null // Direction resolved for the current gesture.
                        var backAccumulatedX = 0f
                        var backStarted = false
                        var backCoolingDown = false

                        detectLockingHorizontalDrag(
                            interceptAtDown = { pos ->
                                if (!hasTabRegion) {
                                    false
                                } else {
                                    val inTab = tabContentRegion?.invoke(pos) ?: false
                                    !inTab || tabAtLeftmost()
                                }
                            },
                            bypassAtDown = { pos -> excludeRegion?.invoke(pos, size) ?: false },
                            resolveLock = { isBack, pos ->
                                val canBack = backActive && !backCoolingDown
                                if (!hasTabRegion) {
                                    // Non-tab pages, such as a detail screen, accept the back gesture everywhere.
                                    when {
                                        isBack && canBack -> LockMode.ACT
                                        isBack && backActive -> LockMode.CONSUME
                                        !isBack && forwardActive -> LockMode.ACT
                                        else -> LockMode.RELEASE
                                    }
                                } else {
                                    val inTab = tabContentRegion?.invoke(pos) ?: false
                                    if (inTab) {
                                        // Inside a tab region the back gesture only starts from the leftmost tab.
                                        when {
                                            isBack && tabAtLeftmost() && canBack -> LockMode.ACT
                                            isBack && tabAtLeftmost() && backActive -> LockMode.CONSUME
                                            else -> LockMode.RELEASE
                                        }
                                    } else {
                                        // Outside a tab region of a tabbed screen the back gesture always applies.
                                        when {
                                            isBack && canBack -> LockMode.ACT
                                            isBack && backActive -> LockMode.CONSUME
                                            !isBack && forwardActive -> LockMode.ACT
                                            else -> LockMode.CONSUME
                                        }
                                    }
                                }
                            },
                            onLock = { isBack, _ ->
                                gestureIsBack = isBack
                                // Reset the peek offset left over from a previously committed forward swipe.
                                if (offsetX.value != 0f) scope.launch { offsetX.snapTo(0f) }
                                if (isBack) {
                                    backAccumulatedX = 0f
                                    backStarted = false
                                }
                            },
                            onDrag = { dragAmount, pos ->
                                val damped = dragAmount * dragSensitivity
                                if (gestureIsBack == true) {
                                    backAccumulatedX = (backAccumulatedX + damped).coerceAtLeast(0f)
                                    val progress = (backAccumulatedX / screenWidthPx).coerceIn(0f, 1f)
                                    if (!backStarted && progress >= BACK_ACTIVATION) {
                                        // Past the activation threshold: start a predictive back session so the
                                        // previous destination is revealed and dimmed.
                                        backStarted = true
                                        SwipeBackNavState.gestureDrivenPop = true // This pop is driven by the gesture.
                                        SwipeBackNavState.suppressForwardEnter = true // Suppress the forward enter.
                                        SwipeBackScrimState.revealEntryId = revealEntryId()
                                        backDispatcher?.dispatchOnBackStarted(
                                            BackEventCompat(pos.x, pos.y, 0f, BackEventCompat.EDGE_LEFT)
                                        )
                                    }
                                    if (backStarted) {
                                        SwipeBackScrimState.progress = progress
                                        backDispatcher?.dispatchOnBackProgressed(
                                            BackEventCompat(pos.x, pos.y, progress, BackEventCompat.EDGE_LEFT)
                                        )
                                    }
                                } else {
                                    val target = (offsetX.value + damped).coerceIn(-screenWidthPx, 0f)
                                    scope.launch { offsetX.snapTo(target) }
                                }
                            },
                            onDragEnd = { velocityX ->
                                if (gestureIsBack == true) {
                                    val backFling = velocityX >= flingVelocityPx
                                    // Drag that never passed the activation threshold.
                                    if (!backStarted) {
                                        if (backFling) backDispatcher?.onBackPressed() // A fling pops immediately.
                                        SwipeBackScrimState.revealEntryId = null
                                        SwipeBackScrimState.progress = 0f
                                    } else {
                                        val progress = (backAccumulatedX / screenWidthPx).coerceIn(0f, 1f)
                                        // Settling cooldown before the next gesture is accepted.
                                        backCoolingDown = true
                                        if (progress >= backThreshold || backFling) {
                                            // Committed: past the threshold or a fling.
                                            backDispatcher?.onBackPressed()
                                            SwipeBackScrimState.revealEntryId = null
                                            SwipeBackScrimState.progress = 0f
                                            scope.launch {
                                                delay(BACK_COOLDOWN_MS.milliseconds)
                                                // Release the gesture flags once the exit animation has played out.
                                                SwipeBackNavState.gestureDrivenPop = false
                                                SwipeBackNavState.suppressForwardEnter = false
                                                backCoolingDown = false
                                            }
                                        } else {
                                            // Slow drag that stayed below the threshold.
                                            scope.launch {
                                                val startNanos = withFrameNanos { it } // Timestamp of the next frame.
                                                val durationNanos = 160_000_000L // Manual spring back of about 160ms.
                                                var t = 0f
                                                // Spring the page back frame by frame.
                                                while (t < 1f) {
                                                    val now = withFrameNanos { it }
                                                    t = ((now - startNanos).toFloat() / durationNanos).coerceIn(0f, 1f)
                                                    val p = progress * (1f - t)
                                                    SwipeBackScrimState.progress = p // Progress held by this frame.
                                                    backDispatcher?.dispatchOnBackProgressed(
                                                        BackEventCompat(0f, 0f, p, BackEventCompat.EDGE_LEFT)
                                                    )
                                                }
                                                repeat(3) {
                                                    backDispatcher?.dispatchOnBackProgressed(
                                                        BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT)
                                                    )
                                                    withFrameNanos { }
                                                }
                                                backDispatcher?.dispatchOnBackCancelled()
                                                SwipeBackScrimState.revealEntryId = null
                                                SwipeBackScrimState.progress = 0f
                                                SwipeBackNavState.gestureDrivenPop = false
                                                SwipeBackNavState.suppressForwardEnter = false
                                                backCoolingDown = false
                                            }
                                        }
                                    }
                                } else {
                                    val current = offsetX.value
                                    // Flung to the left.
                                    val forwardFling = velocityX <= -flingVelocityPx
                                    scope.launch {
                                        if (forwardActive &&
                                            (current <= -screenWidthPx * forwardThreshold || forwardFling)
                                        ) {
                                            offsetX.animateTo(-screenWidthPx, tween(220))
                                            onCommitForward()
                                            // Keep the peek until the new page has drawn its first frame.
                                            repeat(6) { withFrameNanos { } }
                                            offsetX.snapTo(0f)
                                        } else {
                                            offsetX.animateTo(0f, tween(220))
                                        }
                                    }
                                }
                                gestureIsBack = null
                            },
                        )
                    }
                } else Modifier
            ),
    )   {
        val offset = offsetX.value

        // Content of the current destination.
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }

        // Peek of the next destination.
        if (forwardActive && offset < 0f) {
            val forwardOffsetPx = (offset + screenWidthPx).roundToInt()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(forwardOffsetPx, 0) },
            ) {
                forwardPeek?.invoke()
            }
        }
    }
}

/** How a locked horizontal gesture is handled. */
private enum class LockMode { ACT, CONSUME, RELEASE }

/**
 * Consumes horizontal drags on an overlay so that they never reach the page swipe of [SwipeBackScaffold].
 *
 * The gesture detector of [SwipeBackScaffold] sits on an ancestor node and decides based on whether any
 * child consumed the movement: a drag that nobody consumes is treated as a page swipe. Overlay scrims
 * usually only have `clickable`, which handles taps without consuming movement, so a horizontal drag on an
 * overlay would still swipe the page underneath. Add this modifier to the overlay root to prevent that.
 *
 * Two deliberate tradeoffs:
 * - events are consumed in the main pass, which is earlier than the ancestor and still leaves inner
 *   gestures such as list scrolling untouched
 * - consumption only starts once the touch slop is exceeded, and neither the down nor the up event is
 *   touched, so the overlay keeps its own click handling
 *
 * This modifier only applies when [SwipeBackScaffold] resolves gestures in the main pass, which is the case
 * when `tabContentRegion` is `null`. With a `tabContentRegion` the detector uses the initial pass, so the
 * region has to be excluded on the scaffold side instead.
 *
 * @param enabled set to `false` to keep the modifier in the chain while disabling its behavior.
 */
fun Modifier.blockPageSwipe(enabled: Boolean = true): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(Unit) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                var totalX = 0f
                var totalY = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    val delta = change.positionChangeIgnoreConsumed()
                    totalX += delta.x
                    totalY += delta.y
                    if (abs(totalX) > touchSlop && abs(totalX) > abs(totalY)) change.consume()
                }
            }
        }
    }

/**
 * Resolves a horizontal drag once, then reports the whole gesture through [onDrag] and [onDragEnd].
 *
 * The direction and the handling mode are decided a single time per gesture, which keeps the container
 * from changing its mind halfway through a drag.
 *
 * @param interceptAtDown whether the gesture is intercepted from the initial pass when the pointer goes
 *   down at this position.
 * @param bypassAtDown whether the gesture is handed to children unchanged.
 * @param resolveLock handling mode for a resolved horizontal drag.
 * @param onLock invoked once a drag has been locked in.
 * @param onDrag invoked for every movement of a locked gesture that acts.
 * @param onDragEnd invoked with the horizontal velocity when the pointer goes up.
 */
private suspend fun PointerInputScope.detectLockingHorizontalDrag(
    interceptAtDown: (Offset) -> Boolean,
    bypassAtDown: (Offset) -> Boolean = { false },
    resolveLock: (isBack: Boolean, position: Offset) -> LockMode,
    onLock: (isBack: Boolean, position: Offset) -> Unit,
    onDrag: (dragAmount: Float, position: Offset) -> Unit,
    onDragEnd: (velocityX: Float) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (bypassAtDown(down.position)) return@awaitEachGesture // Excluded region: leave it to children.
        val intercept = interceptAtDown(down.position) // Whether the gesture is intercepted at once.
        val pointerId = down.id
        val touchSlop = viewConfiguration.touchSlop // System touch slop.
        var accumulatedX = 0f
        var accumulatedY = 0f
        var locked = false
        var acting = false
        val velocityTracker = VelocityTracker() // Tells a fling from a slow drag on release.
        velocityTracker.addPosition(down.uptimeMillis, down.position)
        try {
            while (true) {
                val pass = if (locked || intercept) PointerEventPass.Initial else PointerEventPass.Main
                val event = awaitPointerEvent(pass)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                if (change.changedToUpIgnoreConsumed()) {
                    if (locked) change.consume()
                    break
                }
                velocityTracker.addPosition(change.uptimeMillis, change.position)

                if (locked) {
                    change.consume()
                    if (acting) onDrag(change.positionChangeIgnoreConsumed().x, change.position)
                    continue
                }

                val delta = change.positionChangeIgnoreConsumed()
                if (!intercept && change.isConsumed && (delta.x != 0f || delta.y != 0f)) break
                accumulatedX += delta.x
                accumulatedY += delta.y
                val absX = abs(accumulatedX)
                val absY = abs(accumulatedY)
                when {
                    absX > touchSlop && absX > absY -> {
                        val isBack = accumulatedX > 0f
                        when (resolveLock(isBack, change.position)) {
                            LockMode.RELEASE -> break
                            LockMode.CONSUME -> {
                                locked = true
                                acting = false
                                change.consume()
                            }
                            LockMode.ACT -> {
                                locked = true
                                acting = true
                                change.consume()
                                onLock(isBack, change.position)
                                onDrag(accumulatedX, change.position)
                            }
                        }
                    }
                    // Vertical drag: give way to children.
                    absY > touchSlop && absY > absX -> break
                }
            }
        } finally {
            if (locked && acting) onDragEnd(velocityTracker.calculateVelocity().x)
        }
    }
}