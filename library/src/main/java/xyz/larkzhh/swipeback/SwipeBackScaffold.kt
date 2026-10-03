package xyz.larkzhh.swipeback

import android.annotation.SuppressLint
import androidx.activity.BackEventCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Default values for the tuning knobs of [SwipeBackScaffold].
 *
 * Every value can be overridden through the parameter of the same meaning, so an app can match the gesture
 * to its own look and feel without touching the library.
 */
object SwipeBackDefaults {
    /** Fraction of the container width a back swipe must cover to commit. */
    const val BackThreshold = 1f / 3f

    /** Fraction of the container width a forward swipe must cover to commit. */
    const val ForwardThreshold = 1f / 4f

    /** Damping applied to finger movement while swiping. */
    const val DragSensitivity = 0.6f

    /** Gesture progress at which a back swipe starts the predictive back session. */
    const val BackActivationThreshold = 0.05f

    /** Velocity above which a horizontal drag counts as a fling. */
    val FlingVelocity = 320.dp

    /** Settling cooldown after a back gesture is committed or cancelled. */
    const val SettleCooldownMillis = 280L

    /** Duration of the spring back animation played when a back swipe stays below the threshold. */
    const val SpringBackDurationMillis = 160

    /** Duration of the animation that moves the peek in or out around a forward swipe. */
    const val ForwardAnimationMillis = 220

    /** Spring back animation played when a back swipe stays below the commit threshold. */
    val SpringBackSpec: AnimationSpec<Float> = tween(SpringBackDurationMillis, easing = LinearEasing)

    /** Animation that moves the peek in or out around a forward swipe. */
    val ForwardAnimationSpec: AnimationSpec<Float> = tween(ForwardAnimationMillis)
}

/**
 * A gesture driven container that turns horizontal drags into navigation transitions.
 *
 * A swipe toward the start edge pops the current destination with the predictive back animation, a swipe
 * toward the end edge peeks the next destination and commits a forward navigation when the drag passes
 * [forwardThreshold].
 *
 * The gesture follows the layout direction: in an RTL layout the back swipe comes from the right edge and
 * the peek is revealed on the left, so the component can be used in mirrored locales without changes.
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
 * @param backEnabled whether a swipe toward the start edge pops the current destination. Completing a pop
 *   needs either an `OnBackPressedDispatcher` or [onBack].
 * @param backThreshold fraction of the container width a drag must cover to commit a back navigation,
 *   in `0f..1f`. Defaults to [SwipeBackDefaults.BackThreshold].
 * @param forwardPeek content revealed underneath while swiping toward the end edge, or `null` to disable
 *   forward swiping.
 * @param forwardThreshold fraction of the container width a drag must cover to commit a forward
 *   navigation, in `0f..1f`. Defaults to [SwipeBackDefaults.ForwardThreshold].
 * @param onCommitForward invoked after a forward swipe is committed and the peek content has been animated
 *   into place.
 * @param onBack invoked when a back swipe is committed, instead of sending the back press to the
 *   `OnBackPressedDispatcher`. Provide it to drive a custom navigation stack, or to use the gesture where no
 *   dispatcher exists, such as in a preview. Predictive back events are only sent to the dispatcher when this
 *   is `null`, because the platform expects the back press that ends the session, and a session that never
 *   ends would leave the system transition hanging.
 * @param dragSensitivity damping applied to finger movement, where the content offset equals
 *   `finger delta * dragSensitivity`. Values below `1f` give a rubber band feel. Defaults to
 *   [SwipeBackDefaults.DragSensitivity].
 * @param backActivationThreshold gesture progress at which a back swipe starts the predictive back
 *   session. Raise it to require a more deliberate swipe before the previous destination is revealed.
 *   Defaults to [SwipeBackDefaults.BackActivationThreshold].
 * @param flingVelocity velocity above which a horizontal drag counts as a fling and commits immediately,
 *   regardless of the drag distance. Defaults to [SwipeBackDefaults.FlingVelocity].
 * @param settleCooldownMillis cooldown after a back gesture is committed or cancelled during which no new
 *   back gesture is accepted. It keeps the tail of one gesture from being picked up by the next.
 *   Defaults to [SwipeBackDefaults.SettleCooldownMillis].
 * @param springBackAnimationSpec animation played when a back swipe stays below [backThreshold], driving the
 *   gesture progress back to zero. Defaults to [SwipeBackDefaults.SpringBackSpec].
 * @param forwardAnimationSpec animation that slides the current page out and the peek into place around a
 *   forward swipe. Defaults to [SwipeBackDefaults.ForwardAnimationSpec].
 * @param policy decides where the gesture may start and when it gives way to children. When it is `null`,
 *   a policy is built from [tabContentRegion], [tabAtLeftmost] and [excludeRegion].
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
    backThreshold: Float = SwipeBackDefaults.BackThreshold,
    forwardPeek: (@Composable () -> Unit)? = null,
    forwardThreshold: Float = SwipeBackDefaults.ForwardThreshold,
    onCommitForward: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    dragSensitivity: Float = SwipeBackDefaults.DragSensitivity,
    backActivationThreshold: Float = SwipeBackDefaults.BackActivationThreshold,
    flingVelocity: Dp = SwipeBackDefaults.FlingVelocity,
    settleCooldownMillis: Long = SwipeBackDefaults.SettleCooldownMillis,
    springBackAnimationSpec: AnimationSpec<Float> = SwipeBackDefaults.SpringBackSpec,
    forwardAnimationSpec: AnimationSpec<Float> = SwipeBackDefaults.ForwardAnimationSpec,
    policy: SwipeBackPolicy? = null,
    tabContentRegion: ((Offset) -> Boolean)? = null,
    excludeRegion: ((pos: Offset, size: IntSize) -> Boolean)? = null,
    tabAtLeftmost: () -> Boolean = { true },
    revealEntryId: () -> String? = { null },
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val backDragSign = SwipeBackGestureMath.backDragSign(isRtl)
    val forwardDragSign = -backDragSign
    val windowWidthPx = LocalWindowInfo.current.containerSize.width.toFloat()
    var containerWidthPx by remember { mutableFloatStateOf(windowWidthPx) }
    val flingVelocityPx = with(density) { flingVelocity.toPx() }
    val scope = rememberCoroutineScope()
    val forwardActive = forwardPeek != null
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val backActive = backEnabled && (onBack != null || backDispatcher != null)
    val gesturePolicy = policy ?: legacyPolicy(tabContentRegion, tabAtLeftmost, excludeRegion)
    val offsetX = remember { Animatable(0f) } // Offset applied while peeking the next destination.

    // The gesture handler below outlives a single recomposition, so every tuning value and callback is read
    // through the latest state instead of being captured once.
    val currentDragSensitivity by rememberUpdatedState(dragSensitivity)
    val currentBackThreshold by rememberUpdatedState(backThreshold)
    val currentForwardThreshold by rememberUpdatedState(forwardThreshold)
    val currentBackActivation by rememberUpdatedState(backActivationThreshold)
    val currentFlingVelocityPx by rememberUpdatedState(flingVelocityPx)
    val currentSettleCooldown by rememberUpdatedState(settleCooldownMillis)
    val currentSpringBackSpec by rememberUpdatedState(springBackAnimationSpec)
    val currentForwardAnimationSpec by rememberUpdatedState(forwardAnimationSpec)
    val currentContainerWidthPx by rememberUpdatedState(containerWidthPx)
    val currentIsRtl by rememberUpdatedState(isRtl)
    val currentOnCommitForward by rememberUpdatedState(onCommitForward)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentPolicy by rememberUpdatedState(gesturePolicy)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerWidthPx = it.width.toFloat() }
            .then(
                if (backActive || forwardActive) {
                    Modifier.pointerInput(backActive, forwardActive, gesturePolicy.hasScrollableRegion()) {
                        var gestureIsBack: Boolean? = null // Direction resolved for the current gesture.
                        var backAccumulatedX = 0f
                        var backStarted = false
                        var backCoolingDown = false
                        val gestureBackSign = SwipeBackGestureMath.backDragSign(currentIsRtl)
                        val gestureForwardSign = -gestureBackSign
                        val gestureBackEdge =
                            if (currentIsRtl) BackEventCompat.EDGE_RIGHT else BackEventCompat.EDGE_LEFT
                        val gestureDispatcher = if (currentOnBack == null) backDispatcher else null
                        val performBack: () -> Unit = {
                            val callback = currentOnBack
                            if (callback != null) callback() else backDispatcher?.onBackPressed()
                        }

                        detectLockingHorizontalDrag(
                            backSign = gestureBackSign,
                            interceptAtDown = { pos ->
                                SwipeBackGestureMath.interceptFromStart(currentPolicy, pos, size)
                            },
                            bypassAtDown = { pos -> currentPolicy.letChildrenHandle(pos, size) },
                            resolveLock = { isBack, pos ->
                                val handling = SwipeBackGestureMath.resolveHandling(
                                    policy = currentPolicy,
                                    direction = if (isBack) SwipeBackDirection.BACK else SwipeBackDirection.FORWARD,
                                    position = pos,
                                    size = size,
                                    canHandleBack = backActive && !backCoolingDown,
                                    backEnabled = backActive,
                                    forwardEnabled = forwardActive,
                                )
                                when (handling) {
                                    SwipeBackGestureHandling.HANDLE -> LockMode.ACT
                                    SwipeBackGestureHandling.CONSUME -> LockMode.CONSUME
                                    SwipeBackGestureHandling.LET_CHILDREN -> LockMode.RELEASE
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
                                val damped = dragAmount * currentDragSensitivity
                                if (gestureIsBack == true) {
                                    backAccumulatedX = (backAccumulatedX + damped * gestureBackSign).coerceAtLeast(0f)
                                    val progress = SwipeBackGestureMath.backProgress(backAccumulatedX, currentContainerWidthPx)
                                    if (!backStarted && SwipeBackGestureMath.shouldActivateBack(progress, currentBackActivation)) {
                                        // Past the activation threshold: start a predictive back session so the
                                        // previous destination is revealed and dimmed.
                                        backStarted = true
                                        SwipeBackNavState.gestureDrivenPop = true // This pop is driven by the gesture.
                                        SwipeBackNavState.suppressForwardEnter = true // Suppress the forward enter.
                                        SwipeBackScrimState.revealEntryId = revealEntryId()
                                        gestureDispatcher?.dispatchOnBackStarted(
                                            BackEventCompat(pos.x, pos.y, 0f, gestureBackEdge)
                                        )
                                    }
                                    if (backStarted) {
                                        SwipeBackScrimState.progress = progress
                                        gestureDispatcher?.dispatchOnBackProgressed(
                                            BackEventCompat(pos.x, pos.y, progress, gestureBackEdge)
                                        )
                                    }
                                } else {
                                    val target = SwipeBackGestureMath.forwardOffset(
                                        offsetX.value,
                                        damped,
                                        currentContainerWidthPx,
                                        gestureForwardSign,
                                    )
                                    scope.launch { offsetX.snapTo(target) }
                                }
                            },
                            onDragEnd = { velocityX ->
                                if (gestureIsBack == true) {
                                    val backFling = SwipeBackGestureMath.isFling(velocityX, gestureBackSign, currentFlingVelocityPx)
                                    // Drag that never passed the activation threshold.
                                    if (!backStarted) {
                                        if (backFling) performBack()
                                        SwipeBackScrimState.revealEntryId = null
                                        SwipeBackScrimState.progress = 0f
                                    } else {
                                        val progress = SwipeBackGestureMath.backProgress(backAccumulatedX, currentContainerWidthPx)
                                        // Settling cooldown before the next gesture is accepted.
                                        backCoolingDown = true
                                        if (SwipeBackGestureMath.shouldCommitBack(progress, currentBackThreshold, backFling)) {
                                            // Committed: past the threshold or a fling.
                                            performBack()
                                            SwipeBackScrimState.revealEntryId = null
                                            SwipeBackScrimState.progress = 0f
                                            scope.launch {
                                                delay(currentSettleCooldown.milliseconds)
                                                // Release the gesture flags once the exit animation has played out.
                                                SwipeBackNavState.gestureDrivenPop = false
                                                SwipeBackNavState.suppressForwardEnter = false
                                                backCoolingDown = false
                                            }
                                        } else {
                                            // Slow drag that stayed below the threshold.
                                            scope.launch {
                                                val springBack = Animatable(progress)
                                                springBack.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = currentSpringBackSpec,
                                                ) {
                                                    val frame = value.coerceIn(0f, 1f)
                                                    SwipeBackScrimState.progress = frame
                                                    gestureDispatcher?.dispatchOnBackProgressed(
                                                        BackEventCompat(0f, 0f, frame, gestureBackEdge)
                                                    )
                                                }
                                                repeat(3) {
                                                    gestureDispatcher?.dispatchOnBackProgressed(
                                                        BackEventCompat(0f, 0f, 0f, gestureBackEdge)
                                                    )
                                                    withFrameNanos { }
                                                }
                                                gestureDispatcher?.dispatchOnBackCancelled()
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
                                    val forwardFling = SwipeBackGestureMath.isFling(velocityX, gestureForwardSign, currentFlingVelocityPx)
                                    scope.launch {
                                        if (forwardActive &&
                                            SwipeBackGestureMath.shouldCommitForward(
                                                current,
                                                currentContainerWidthPx,
                                                currentForwardThreshold,
                                                gestureForwardSign,
                                                forwardFling,
                                            )
                                        ) {
                                            offsetX.animateTo(
                                                gestureForwardSign * currentContainerWidthPx,
                                                currentForwardAnimationSpec,
                                            )
                                            currentOnCommitForward()
                                            // Keep the peek until the new page has drawn its first frame.
                                            repeat(6) { withFrameNanos { } }
                                            offsetX.snapTo(0f)
                                        } else {
                                            offsetX.animateTo(0f, currentForwardAnimationSpec)
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
        if (forwardActive && SwipeBackGestureMath.isPeekVisible(offset, forwardDragSign)) {
            val forwardOffsetPx =
                SwipeBackGestureMath.peekOffsetPx(offset, containerWidthPx, forwardDragSign).roundToInt()
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
 * @param backSign sign of the horizontal drag that counts as a back swipe: `1f` in LTR and `-1f` in RTL.
 * @param interceptAtDown whether the gesture is intercepted from the initial pass when the pointer goes
 *   down at this position.
 * @param bypassAtDown whether the gesture is handed to children unchanged.
 * @param resolveLock handling mode for a resolved horizontal drag.
 * @param onLock invoked once a drag has been locked in.
 * @param onDrag invoked for every movement of a locked gesture that acts.
 * @param onDragEnd invoked with the horizontal velocity when the pointer goes up.
 */
private suspend fun PointerInputScope.detectLockingHorizontalDrag(
    backSign: Float,
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
                        val isBack = SwipeBackGestureMath.isBackDrag(accumulatedX, backSign)
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