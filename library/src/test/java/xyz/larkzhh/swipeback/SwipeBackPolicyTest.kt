package xyz.larkzhh.swipeback

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val Size = IntSize(1000, 2000)
private val Touch = Offset(10f, 20f)

private class FakePolicy(
    private val region: Boolean = false,
    private val inRegion: Boolean = false,
    private val leftmost: Boolean = true,
    private val excluded: Boolean = false,
) : SwipeBackPolicy {
    override fun hasScrollableRegion() = region
    override fun isInScrollableRegion(position: Offset, size: IntSize) = inRegion
    override fun isAtLeftmost() = leftmost
    override fun letChildrenHandle(position: Offset, size: IntSize) = excluded
}

class SwipeBackPolicyTest {

    private fun resolve(
        policy: SwipeBackPolicy,
        direction: SwipeBackDirection,
        canHandleBack: Boolean = true,
        backEnabled: Boolean = true,
        forwardEnabled: Boolean = true,
    ) = SwipeBackGestureMath.resolveHandling(
        policy = policy,
        direction = direction,
        position = Touch,
        size = Size,
        canHandleBack = canHandleBack,
        backEnabled = backEnabled,
        forwardEnabled = forwardEnabled,
    )

    private val plain = FakePolicy()

    @Test
    fun `on a plain screen a back swipe is handled`() {
        assertEquals(SwipeBackGestureHandling.HANDLE, resolve(plain, SwipeBackDirection.BACK))
    }

    @Test
    fun `a back swipe during the settle cooldown is consumed`() {
        assertEquals(
            SwipeBackGestureHandling.CONSUME,
            resolve(plain, SwipeBackDirection.BACK, canHandleBack = false),
        )
    }

    @Test
    fun `a back swipe is released while back is disabled`() {
        assertEquals(
            SwipeBackGestureHandling.LET_CHILDREN,
            resolve(plain, SwipeBackDirection.BACK, canHandleBack = false, backEnabled = false),
        )
    }

    @Test
    fun `a forward swipe needs a peek to be handled`() {
        assertEquals(SwipeBackGestureHandling.HANDLE, resolve(plain, SwipeBackDirection.FORWARD))
        assertEquals(
            SwipeBackGestureHandling.LET_CHILDREN,
            resolve(plain, SwipeBackDirection.FORWARD, forwardEnabled = false),
        )
    }

    @Test
    fun `outside a scrollable region both directions are handled`() {
        val policy = FakePolicy(region = true, inRegion = false)
        assertEquals(SwipeBackGestureHandling.HANDLE, resolve(policy, SwipeBackDirection.BACK))
        assertEquals(SwipeBackGestureHandling.HANDLE, resolve(policy, SwipeBackDirection.FORWARD))
    }

    @Test
    fun `outside a scrollable region an unhandled gesture is consumed`() {
        val policy = FakePolicy(region = true, inRegion = false)
        assertEquals(
            SwipeBackGestureHandling.CONSUME,
            resolve(policy, SwipeBackDirection.FORWARD, forwardEnabled = false),
        )
    }

    @Test
    fun `inside a scrollable region only back is handled`() {
        val policy = FakePolicy(region = true, inRegion = true, leftmost = true)
        assertEquals(SwipeBackGestureHandling.HANDLE, resolve(policy, SwipeBackDirection.BACK))
        assertEquals(
            SwipeBackGestureHandling.LET_CHILDREN,
            resolve(policy, SwipeBackDirection.FORWARD),
        )
    }

    @Test
    fun `inside a scrollable region a back swipe during the cooldown is consumed`() {
        val policy = FakePolicy(region = true, inRegion = true, leftmost = true)
        assertEquals(
            SwipeBackGestureHandling.CONSUME,
            resolve(policy, SwipeBackDirection.BACK, canHandleBack = false),
        )
    }

    @Test
    fun `inside a scrollable region away from the leftmost page nothing is handled`() {
        val policy = FakePolicy(region = true, inRegion = true, leftmost = false)
        assertEquals(
            SwipeBackGestureHandling.LET_CHILDREN,
            resolve(policy, SwipeBackDirection.BACK),
        )
        assertEquals(
            SwipeBackGestureHandling.LET_CHILDREN,
            resolve(policy, SwipeBackDirection.FORWARD),
        )
    }

    @Test
    fun `the container claims the gesture from the start unless a region blocks it`() {
        assertFalse(SwipeBackGestureMath.interceptFromStart(plain, Touch, Size))
        assertTrue(SwipeBackGestureMath.interceptFromStart(FakePolicy(region = true, inRegion = false), Touch, Size))
        assertTrue(
            SwipeBackGestureMath.interceptFromStart(
                FakePolicy(region = true, inRegion = true, leftmost = true),
                Touch,
                Size,
            ),
        )
        assertFalse(
            SwipeBackGestureMath.interceptFromStart(
                FakePolicy(region = true, inRegion = true, leftmost = false),
                Touch,
                Size,
            ),
        )
    }

    @Test
    fun `the shortcut parameters build a policy with the same answers`() {
        val policy = legacyPolicy(
            tabContentRegion = { it.x > 100f },
            tabAtLeftmost = { false },
            excludeRegion = { position, _ -> position.y > 500f },
        )
        assertTrue(policy.hasScrollableRegion())
        assertTrue(policy.isInScrollableRegion(Offset(200f, 0f), Size))
        assertFalse(policy.isInScrollableRegion(Offset(50f, 0f), Size))
        assertFalse(policy.isAtLeftmost())
        assertTrue(policy.letChildrenHandle(Offset(0f, 600f), Size))
        assertFalse(policy.letChildrenHandle(Offset(0f, 100f), Size))

        val withoutShortcuts = legacyPolicy(null, { true }, null)
        assertFalse(withoutShortcuts.hasScrollableRegion())
        assertTrue(withoutShortcuts.isAtLeftmost())
        assertFalse(withoutShortcuts.letChildrenHandle(Touch, Size))
    }
}