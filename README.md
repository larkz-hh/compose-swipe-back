# compose-swipe-back

[![JitPack](https://jitpack.io/v/larkz-hh/compose-swipe-back.svg)](https://jitpack.io/#larkz-hh/compose-swipe-back)
[![CI](https://github.com/larkz-hh/compose-swipe-back/actions/workflows/ci.yml/badge.svg)](https://github.com/larkz-hh/compose-swipe-back/actions/workflows/ci.yml)

[English](README.md) · [简体中文](README.zh-CN.md)

compose-swipe-back is a Compose library that handles back and forward navigation with horizontal swipe
gestures, including the system predictive back animation on Android 13 and above. It has no navigation
dependency and works with Navigation Compose or a custom back stack.

## Features

- Back and forward navigation driven by the drag itself.
- The system predictive back animation on Android 13 and above.
- Direction aware: RTL layouts get the mirrored gesture with no extra code.
- Every threshold, damping value and duration is overridable through `SwipeBackDefaults`.

## Download

```kotlin
implementation("com.github.larkz-hh:compose-swipe-back:0.1.0")
```

minSdk 26, Compose 1.7+ and activity-compose 1.8+. The predictive back animation needs Android 13+ and
`android:enableOnBackInvokedCallback="true"` on your `<application>`.

## Usage

Wrap the current screen in `SwipeBackScaffold` and provide the next screen through `forwardPeek`:

```kotlin
SwipeBackScaffold(
    backEnabled = canGoBack,
    forwardPeek = { NextScreen() },
    onCommitForward = { navigateForward() },
    onBack = { navigateBack() },
    revealEntryId = { "detail" },
) {
    ScrimBox(entryId = "detail") {
        DetailContent()
    }
}
```

Without `onBack`, the gesture posts to the `OnBackPressedDispatcher` instead.

Thresholds, damping and durations have defaults in `SwipeBackDefaults` and can be overridden at the call site:

```kotlin
SwipeBackScaffold(
    backEnabled = canGoBack,
    flingVelocity = 400.dp,
    backActivationThreshold = 0.12f,
    settleCooldownMillis = 200,
) { /* ... */ }
```

## API reference

Parameters of `SwipeBackScaffold`:

| Parameter | Default | Description |
| --- | --- | --- |
| `backEnabled` | — | Enables the back swipe |
| `backThreshold` | `1f / 3f` | Width fraction a back swipe must cover |
| `forwardPeek` | `null` | Screen revealed by a forward swipe; `null` disables it |
| `forwardThreshold` | `1f / 4f` | Width fraction a forward swipe must cover |
| `onCommitForward` | `{}` | Called after a forward swipe is committed |
| `onBack` | `null` | Called on commit instead of the `OnBackPressedDispatcher` |
| `dragSensitivity` | `0.6f` | Damping applied to finger movement |
| `backActivationThreshold` | `0.05f` | Progress that starts the predictive back session |
| `flingVelocity` | `320.dp` | Velocity that counts as a fling |
| `settleCooldownMillis` | `280L` | Cooldown after a back gesture |
| `springBackDurationMillis` | `160` | Duration of the spring back animation |
| `forwardAnimationMillis` | `220` | Duration of the peek animation |
| `tabContentRegion` | `null` | Touch positions that belong to a horizontally scrollable region |
| `excludeRegion` | `null` | Positions where the gesture is handed to children |
| `tabAtLeftmost` | `{ true }` | Whether the selected tab is the leftmost one |
| `revealEntryId` | `{ null }` | Id of the entry revealed while swiping back |
| `content` | — | Content of the current screen |

`SwipeBackDefaults` holds those defaults. `ScrimBox(entryId, maxAlpha = 0.3f)` dims a page while it is being
revealed, and `Modifier.blockPageSwipe()` keeps drags on an overlay away from the page gesture.

Flags the gesture sets for hosts that animate their own transitions:

| Flag | Meaning |
| --- | --- |
| `suppressForwardEnter` | A forward gesture already revealed the incoming screen |
| `suppressPopAnim` | The pop was triggered by the gesture |
| `gestureDrivenPop` | The pop came from the swipe gesture |

## Notes

- Overlays such as bottom sheets usually handle clicks only, so a horizontal drag on top of one still reaches
  the page gesture underneath. Add `Modifier.blockPageSwipe()` to the overlay root to prevent that.
- When a screen contains a horizontal pager or a tab row, pass `tabContentRegion` and `tabAtLeftmost` so the back
  gesture only starts from the leftmost tab, and `excludeRegion` for regions that handle horizontal drags
  themselves, such as a video seek bar.
- In RTL layouts the back gesture runs from the right edge to the left, and the dispatched edge changes from
  `EDGE_LEFT` to `BackEventCompat.EDGE_RIGHT`.
- With Navigation Compose the gesture sets three flags while it is running. Reading them from your transition
  specs prevents the same animation from playing twice:

```kotlin
composable(
    route = "detail",
    enterTransition = {
        if (SwipeBackNavState.suppressForwardEnter) EnterTransition.None else fadeIn()
    },
    popExitTransition = {
        if (SwipeBackNavState.suppressPopAnim && !SwipeBackNavState.gestureDrivenPop) {
            ExitTransition.None
        } else {
            fadeOut()
        }
    },
)
```

## Sample

`:app` demonstrates the component with a page counter:

```bash
./gradlew :app:installDebug
```

The library sources contain two `@Preview` composables, one for LTR and one for RTL, so opening the module in
Android Studio renders the component without running the app.

## Used by

- [Lime](https://github.com/larkz-hh/lime-app): a Compose community app.

## Contributing

Issues and pull requests are welcome. Build commands, code style and commit conventions are in
[CONTRIBUTING.md](CONTRIBUTING.md), and released changes are listed in [CHANGELOG.md](CHANGELOG.md).

## License

```
Copyright 2026 larkz-hh

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

Maintained by [@larkz-hh](https://github.com/larkz-hh).
