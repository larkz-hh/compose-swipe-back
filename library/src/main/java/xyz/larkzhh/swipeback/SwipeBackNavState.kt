package xyz.larkzhh.swipeback

/**
 * Flags that [SwipeBackScaffold] sets to coordinate its gesture with the host navigation graph.
 *
 * Navigation transitions are declared where the graph is built, while the gesture happens inside
 * [SwipeBackScaffold]. Read these flags from the transition specs of your `NavHost` to keep the two in sync.
 */
object SwipeBackNavState {
    /** Suppresses the enter transition of the destination being navigated to. */
    var suppressForwardEnter = false

    /** Suppresses the pop animation of the current navigation event. */
    var suppressPopAnim = false

    /** `true` while a pop was triggered by the swipe gesture, so that only that pop animates out. */
    var gestureDrivenPop = false
}