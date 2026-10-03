# Contributing

Thanks for taking the time to contribute. Issues and pull requests are both welcome.

## Requirements

- JDK 21
- Android SDK with platform 37
- Android Studio with AGP 9 support

## Build and test

```bash
./gradlew :library:assembleDebug
./gradlew :library:testDebugUnitTest
./gradlew :app:assembleDebug
```

`:library` holds the published code, `:app` is a sample that consumes it.

Dependencies resolve from Google and Maven Central. A Chinese mirror is used first by default; set
`USE_CN_MIRROR=false` to skip it.

## Code style

- Public API is documented with English KDoc, including `@param` for every parameter that is not obvious.
- Inline comments explain why something is done, not what the code already says.
- No commented out code and no `TODO` without a linked issue.
- New gesture rules or direction handling need unit tests. Pure logic belongs in
  `SwipeBackGestureMath` so it can be covered on the JVM.

## Commits

The project uses [Conventional Commits](https://www.conventionalcommits.org/): `feat(scope): ...`,
`fix(scope): ...`, `test(scope): ...`, `docs: ...`.

## Pull requests

1. Keep the change focused, one topic per pull request.
2. Add or update tests when behaviour changes.
3. Run `./gradlew :library:testDebugUnitTest` and `./gradlew :app:assembleDebug` before opening the pull
   request.
4. Update `CHANGELOG.md` under `Unreleased`.
