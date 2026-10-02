package xyz.larkzhh.swipeback

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
}