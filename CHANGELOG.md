# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `ScrimBox` accepts a `scrimColor`, so the scrim no longer has to be black.
- `SwipeBackDefaults.SpringBackSpec` and `SwipeBackDefaults.ForwardAnimationSpec`.

### Changed

- `SwipeBackScaffold` takes `springBackAnimationSpec` and `forwardAnimationSpec` instead of
  `springBackDurationMillis` and `forwardAnimationMillis`. Defaults keep the previous durations and the spring
  back keeps its linear easing, so the out of the box behaviour is unchanged.

## [0.1.0] - 2026-10-03

### Added

- `SwipeBackScaffold` gesture container with predictive back and forward peek.
- `ScrimBox` and `SwipeBackScrimState` for dimming the page during a back gesture.
- `SwipeBackNavState` flags for suppressing host navigation transitions.
- `Modifier.blockPageSwipe` so overlays keep horizontal drags away from the page gesture.
- `SwipeBackDefaults` plus matching parameters for every tuning value.
- Support for RTL layouts, where the back gesture comes from the right edge.
- Unit tests for the gesture math and gesture integration tests running on Robolectric.
