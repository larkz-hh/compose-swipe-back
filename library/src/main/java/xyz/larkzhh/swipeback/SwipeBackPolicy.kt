package xyz.larkzhh.swipeback

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**
 * Where the gesture of [SwipeBackScaffold] may start, and when it should give way to children.
 *
 * Every method has a default, so an implementation only overrides what it needs. The shortcut parameters
 * `tabContentRegion`, `tabAtLeftmost` and `excludeRegion` of [SwipeBackScaffold] build this policy
 * internally, so passing them equals overriding the matching methods here.
 */
interface SwipeBackPolicy {
    /** Whether this screen contains a horizontally scrollable region, such as a tab row. */
    fun hasScrollableRegion(): Boolean = false

    /** Whether the touch position belongs to that region. */
    fun isInScrollableRegion(position: Offset, size: IntSize): Boolean = false

    /** Whether the current page of that region is the leftmost one, where a back swipe may start. */
    fun isAtLeftmost(): Boolean = true

    /** Whether the whole gesture at that position is left to children, such as a video seek bar. */
    fun letChildrenHandle(position: Offset, size: IntSize): Boolean = false
}

/**
 * Builds the policy that the shortcut parameters of [SwipeBackScaffold] stand for.
 *
 * Passing those parameters equals implementing a policy whose methods return them.
 */
internal fun legacyPolicy(
    tabContentRegion: ((Offset) -> Boolean)?,
    tabAtLeftmost: () -> Boolean,
    excludeRegion: ((Offset, IntSize) -> Boolean)?,
): SwipeBackPolicy = object : SwipeBackPolicy {
    override fun hasScrollableRegion() = tabContentRegion != null

    override fun isInScrollableRegion(position: Offset, size: IntSize) =
        tabContentRegion?.invoke(position) ?: false

    override fun isAtLeftmost() = tabAtLeftmost()

    override fun letChildrenHandle(position: Offset, size: IntSize) =
        excludeRegion?.invoke(position, size) ?: false
}