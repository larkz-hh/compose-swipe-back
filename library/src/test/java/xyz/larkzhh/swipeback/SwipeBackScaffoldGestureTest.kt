package xyz.larkzhh.swipeback

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PageTag = "page"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class SwipeBackScaffoldGestureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var backs = 0
    private var commits = 0

    private fun setScaffold(isRtl: Boolean = false) {
        composeRule.setContent {
            val page: @Composable () -> Unit = {
                SwipeBackScaffold(
                    backEnabled = true,
                    forwardPeek = { Box(Modifier.fillMaxSize()) },
                    onBack = { backs++ },
                    onCommitForward = { commits++ },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(PageTag),
                    )
                }
            }
            if (isRtl) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    page()
                }
            } else {
                page()
            }
        }
    }

    private fun dragPage(block: androidx.compose.ui.test.TouchInjectionScope.() -> Unit) {
        composeRule.onNodeWithTag(PageTag).performTouchInput(block)
        composeRule.waitForIdle()
    }

    @Test
    fun `a drag below the threshold springs back without navigating`() {
        setScaffold()

        dragPage { swipeRight(startX = left + 5f, endX = left + 105f) }

        assertEquals(0, backs)
        assertEquals(0, commits)
        composeRule.waitUntil(timeoutMillis = 2_000) { SwipeBackScrimState.progress == 0f }
        assertEquals(0f, SwipeBackScrimState.progress, 0.001f)
        assertNull(SwipeBackScrimState.revealEntryId)
    }

    @Test
    fun `swiping toward the start edge pops the page in LTR`() {
        setScaffold()
        dragPage { swipeRight(startX = left + 5f, endX = right - 5f) }
        assertEquals(1, backs)
        assertEquals(0, commits)
    }

    @Test
    fun `swiping toward the end edge commits a forward navigation in LTR`() {
        setScaffold()
        dragPage { swipeLeft(startX = right - 5f, endX = left + 5f) }
        assertEquals(0, backs)
        assertEquals(1, commits)
    }

    @Test
    fun `swiping toward the start edge pops the page in RTL`() {
        setScaffold(isRtl = true)
        dragPage { swipeLeft(startX = right - 5f, endX = left + 5f) }
        assertEquals(1, backs)
        assertEquals(0, commits)
    }

    @Test
    fun `swiping toward the end edge commits a forward navigation in RTL`() {
        setScaffold(isRtl = true)
        dragPage { swipeRight(startX = left + 5f, endX = right - 5f) }
        assertEquals(0, backs)
        assertEquals(1, commits)
    }

    @Test
    fun `a policy that hands the gesture to children blocks navigation`() {
        var backs = 0

        composeRule.setContent {
            SwipeBackScaffold(
                backEnabled = true,
                forwardPeek = { Box(Modifier.fillMaxSize()) },
                onBack = { backs++ },
                policy = object : SwipeBackPolicy {
                    override fun letChildrenHandle(position: Offset, size: IntSize) = true
                },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(PageTag),
                )
            }
        }

        composeRule.onNodeWithTag(PageTag).performTouchInput { swipeRight(startX = left + 5f, endX = right - 5f) }
        composeRule.waitForIdle()

        assertEquals(0, backs)
    }
}