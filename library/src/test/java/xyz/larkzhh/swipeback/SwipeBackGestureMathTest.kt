package xyz.larkzhh.swipeback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeBackGestureMathTest {

    private val width = 1000f
    private val flingVelocity = 960f
    private val ltr = SwipeBackGestureMath.backDragSign(isRtl = false)
    private val rtl = SwipeBackGestureMath.backDragSign(isRtl = true)

    @Test
    fun `back drag sign is positive in LTR and negative in RTL`() {
        assertEquals(1f, ltr, 0f)
        assertEquals(-1f, rtl, 0f)
    }

    @Test
    fun `rightward drag is a back gesture in LTR only`() {
        assertTrue(SwipeBackGestureMath.isBackDrag(20f, ltr))
        assertFalse(SwipeBackGestureMath.isBackDrag(20f, rtl))
    }

    @Test
    fun `leftward drag is a back gesture in RTL only`() {
        assertTrue(SwipeBackGestureMath.isBackDrag(-20f, rtl))
        assertFalse(SwipeBackGestureMath.isBackDrag(-20f, ltr))
    }

    @Test
    fun `a drag without horizontal movement is not a back gesture`() {
        assertFalse(SwipeBackGestureMath.isBackDrag(0f, ltr))
        assertFalse(SwipeBackGestureMath.isBackDrag(0f, rtl))
    }

    @Test
    fun `back progress is the dragged fraction of the container width`() {
        assertEquals(0.25f, SwipeBackGestureMath.backProgress(250f, width), 0.0001f)
        assertEquals(1f, SwipeBackGestureMath.backProgress(width, width), 0.0001f)
    }

    @Test
    fun `back progress is clamped to zero and one`() {
        assertEquals(0f, SwipeBackGestureMath.backProgress(-400f, width), 0.0001f)
        assertEquals(1f, SwipeBackGestureMath.backProgress(width * 3f, width), 0.0001f)
    }

    @Test
    fun `back progress is zero while the container width is unknown`() {
        assertEquals(0f, SwipeBackGestureMath.backProgress(400f, 0f), 0.0001f)
    }

    @Test
    fun `the predictive back session starts at the activation threshold`() {
        assertFalse(SwipeBackGestureMath.shouldActivateBack(0.049f, 0.05f))
        assertTrue(SwipeBackGestureMath.shouldActivateBack(0.05f, 0.05f))
        assertTrue(SwipeBackGestureMath.shouldActivateBack(0.4f, 0.05f))
    }

    @Test
    fun `a fling is measured in the direction of the gesture`() {
        assertTrue(SwipeBackGestureMath.isFling(1200f, ltr, flingVelocity))
        assertFalse(SwipeBackGestureMath.isFling(1200f, rtl, flingVelocity))
        assertTrue(SwipeBackGestureMath.isFling(-1200f, rtl, flingVelocity))
        assertFalse(SwipeBackGestureMath.isFling(900f, ltr, flingVelocity))
    }

    @Test
    fun `a back swipe commits past the threshold or on a fling`() {
        assertTrue(SwipeBackGestureMath.shouldCommitBack(0.34f, 1f / 3f, fling = false))
        assertFalse(SwipeBackGestureMath.shouldCommitBack(0.3f, 1f / 3f, fling = false))
        assertTrue(SwipeBackGestureMath.shouldCommitBack(0.05f, 1f / 3f, fling = true))
    }

    @Test
    fun `a forward swipe commits past the threshold in either direction`() {
        assertTrue(SwipeBackGestureMath.shouldCommitForward(-300f, width, 0.25f, -1f, fling = false))
        assertFalse(SwipeBackGestureMath.shouldCommitForward(-200f, width, 0.25f, -1f, fling = false))
        assertTrue(SwipeBackGestureMath.shouldCommitForward(300f, width, 0.25f, 1f, fling = false))
        assertTrue(SwipeBackGestureMath.shouldCommitForward(10f, width, 0.25f, 1f, fling = true))
    }

    @Test
    fun `a forward swipe never commits while the container width is unknown`() {
        assertFalse(SwipeBackGestureMath.shouldCommitForward(0f, 0f, 0.25f, 1f, fling = false))
    }

    @Test
    fun `the content offset stays inside the reachable range in LTR`() {
        val range = SwipeBackGestureMath.forwardOffsetRange(width, -1f)
        assertEquals(-width, range.start, 0f)
        assertEquals(0f, range.endInclusive, 0f)
        assertEquals(-200f, SwipeBackGestureMath.forwardOffset(0f, -200f, width, -1f), 0.0001f)
        assertEquals(-width, SwipeBackGestureMath.forwardOffset(-900f, -400f, width, -1f), 0.0001f)
        assertEquals(0f, SwipeBackGestureMath.forwardOffset(-200f, 400f, width, -1f), 0.0001f)
    }

    @Test
    fun `the content offset stays inside the reachable range in RTL`() {
        val range = SwipeBackGestureMath.forwardOffsetRange(width, 1f)
        assertEquals(0f, range.start, 0f)
        assertEquals(width, range.endInclusive, 0f)
        assertEquals(200f, SwipeBackGestureMath.forwardOffset(0f, 200f, width, 1f), 0.0001f)
        assertEquals(width, SwipeBackGestureMath.forwardOffset(900f, 400f, width, 1f), 0.0001f)
        assertEquals(0f, SwipeBackGestureMath.forwardOffset(200f, -400f, width, 1f), 0.0001f)
    }

    @Test
    fun `the peek is placed on the opposite side of the container`() {
        assertEquals(800f, SwipeBackGestureMath.peekOffsetPx(-200f, width, -1f), 0.0001f)
        assertEquals(-800f, SwipeBackGestureMath.peekOffsetPx(200f, width, 1f), 0.0001f)
        assertEquals(0f, SwipeBackGestureMath.peekOffsetPx(-width, width, -1f), 0.0001f)
    }

    @Test
    fun `the peek is visible only while the content is offset forward`() {
        assertTrue(SwipeBackGestureMath.isPeekVisible(-1f, -1f))
        assertFalse(SwipeBackGestureMath.isPeekVisible(0f, -1f))
        assertFalse(SwipeBackGestureMath.isPeekVisible(1f, -1f))
        assertTrue(SwipeBackGestureMath.isPeekVisible(1f, 1f))
        assertFalse(SwipeBackGestureMath.isPeekVisible(-1f, 1f))
    }
}