package xyz.larkzhh.swipeback

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**
 * Pure helpers behind the gesture of [SwipeBackScaffold].
 *
 * They live outside the composable so the direction handling and the commit rules can be unit tested
 * without a device.
 */
internal object SwipeBackGestureMath {

    /** Sign of the horizontal drag that pops the current destination: `1f` in LTR and `-1f` in RTL. */
    fun backDragSign(isRtl: Boolean): Float = if (isRtl) -1f else 1f

    /** Whether a drag of the given signed distance counts as a back swipe. */
    fun isBackDrag(accumulatedX: Float, backSign: Float): Boolean = accumulatedX * backSign > 0f

    /** Progress of a back swipe in `0f..1f`, from the dragged distance and the container width. */
    fun backProgress(accumulatedX: Float, containerWidthPx: Float): Float =
        if (containerWidthPx <= 0f) 0f else (accumulatedX / containerWidthPx).coerceIn(0f, 1f)

    /** Whether a back swipe has come far enough to start the predictive back session. */
    fun shouldActivateBack(progress: Float, activationThreshold: Float): Boolean =
        progress >= activationThreshold

    /** Whether the release velocity is a fling in the direction given by [directionSign]. */
    fun isFling(velocityX: Float, directionSign: Float, flingVelocityPx: Float): Boolean =
        velocityX * directionSign >= flingVelocityPx

    /** Whether a released back swipe is committed, either past the threshold or by a fling. */
    fun shouldCommitBack(progress: Float, threshold: Float, fling: Boolean): Boolean =
        progress >= threshold || fling

    /** Whether a released forward swipe is committed, either past the threshold or by a fling. */
    fun shouldCommitForward(
        offsetPx: Float,
        containerWidthPx: Float,
        threshold: Float,
        forwardSign: Float,
        fling: Boolean,
    ): Boolean = fling || (containerWidthPx > 0f && offsetPx * forwardSign >= containerWidthPx * threshold)

    /** Offset range the content can move through while peeking the next destination. */
    fun forwardOffsetRange(containerWidthPx: Float, forwardSign: Float): ClosedFloatingPointRange<Float> {
        val limit = forwardSign * containerWidthPx
        return minOf(0f, limit)..maxOf(0f, limit)
    }

    /** Content offset after dragging the peek by [deltaPx], clamped to the reachable range. */
    fun forwardOffset(offsetPx: Float, deltaPx: Float, containerWidthPx: Float, forwardSign: Float): Float {
        val range = forwardOffsetRange(containerWidthPx, forwardSign)
        return (offsetPx + deltaPx).coerceIn(range.start, range.endInclusive)
    }

    /** Horizontal position of the peek content for the given content offset. */
    fun peekOffsetPx(offsetPx: Float, containerWidthPx: Float, forwardSign: Float): Float =
        offsetPx - forwardSign * containerWidthPx

    /** Whether the peek is visible at the given content offset. */
    fun isPeekVisible(offsetPx: Float, forwardSign: Float): Boolean = offsetPx * forwardSign > 0f
    /**
     * Whether the container claims the gesture from the initial pass when the pointer goes down here.
     *
     * A screen without a scrollable region defers to children first. Inside a scrollable region the container
     * claims the gesture unless the region sits on a page where a back swipe may not start.
     */
    fun interceptFromStart(policy: SwipeBackPolicy, position: Offset, size: IntSize): Boolean =
        if (!policy.hasScrollableRegion()) {
            false
        } else {
            !policy.isInScrollableRegion(position, size) || policy.isAtLeftmost()
        }

    /**
     * Decision table for a resolved gesture.
     *
     * @param canHandleBack whether a back swipe is accepted right now, which also requires the settle
     *   cooldown to have passed.
     * @param backEnabled whether a back swipe is enabled at all.
     * @param forwardEnabled whether the host provided a peek for forward swipes.
     */
    fun resolveHandling(
        policy: SwipeBackPolicy,
        direction: SwipeBackDirection,
        position: Offset,
        size: IntSize,
        canHandleBack: Boolean,
        backEnabled: Boolean,
        forwardEnabled: Boolean,
    ): SwipeBackGestureHandling {
        val hasRegion = policy.hasScrollableRegion()
        val inRegion = hasRegion && policy.isInScrollableRegion(position, size)
        return when {
            inRegion && !policy.isAtLeftmost() -> SwipeBackGestureHandling.LET_CHILDREN
            inRegion -> when {
                direction == SwipeBackDirection.BACK && canHandleBack -> SwipeBackGestureHandling.HANDLE
                direction == SwipeBackDirection.BACK && backEnabled -> SwipeBackGestureHandling.CONSUME
                else -> SwipeBackGestureHandling.LET_CHILDREN
            }
            direction == SwipeBackDirection.BACK && canHandleBack -> SwipeBackGestureHandling.HANDLE
            direction == SwipeBackDirection.BACK && backEnabled -> SwipeBackGestureHandling.CONSUME
            direction == SwipeBackDirection.FORWARD && forwardEnabled -> SwipeBackGestureHandling.HANDLE
            hasRegion -> SwipeBackGestureHandling.CONSUME
            else -> SwipeBackGestureHandling.LET_CHILDREN
        }
    }
}

/** Direction of a horizontal gesture, once it has been resolved. */
internal enum class SwipeBackDirection { BACK, FORWARD }

/** What the container does with a resolved horizontal gesture. */
internal enum class SwipeBackGestureHandling {
    /** Drive the gesture: pop when swiping back, peek when swiping forward. */
    HANDLE,

    /** Keep the gesture away from children without acting on it, so the page stays put. */
    CONSUME,

    /** Hand the gesture to children unchanged. */
    LET_CHILDREN,
}
