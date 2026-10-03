# compose-swipe-back

[![JitPack](https://jitpack.io/v/larkz-hh/compose-swipe-back.svg)](https://jitpack.io/#larkz-hh/compose-swipe-back)
[![CI](https://github.com/larkz-hh/compose-swipe-back/actions/workflows/ci.yml/badge.svg)](https://github.com/larkz-hh/compose-swipe-back/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-26-green.svg)](#引入)
[![Compose](https://img.shields.io/badge/Compose-1.7%2B-4285F4.svg)](#引入)
[![Stars](https://img.shields.io/github/stars/larkz-hh/compose-swipe-back?style=flat)](https://github.com/larkz-hh/compose-swipe-back/stargazers)

[English](README.md) · [简体中文](README.zh-CN.md)

compose-swipe-back 是一个用横向滑动处理返回和前进的 Compose 库，Android 13 及以上使用系统的预测性返回动画。它不依赖任何导航方案，可配合 Navigation Compose，也可配合自建的返回栈。

## 特性

- 返回与前进均由拖拽驱动。
- Android 13 及以上使用系统的预测性返回动画。
- 跟随布局方向，RTL 布局无需额外代码。
- 阈值、阻尼与时长均可通过 `SwipeBackDefaults` 覆盖。

## 引入

```kotlin
implementation("com.github.larkz-hh:compose-swipe-back:0.2.0")
```

要求 minSdk 26、Compose 1.7+、activity-compose 1.8+。预测性返回动画需要 Android 13+，并在 `<application>` 上启用 `android:enableOnBackInvokedCallback`。

## 用法

将当前页面包裹在 `SwipeBackScaffold` 中，`forwardPeek` 用于指定将要露出的下一个页面：

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

未提供 `onBack` 时，返回手势交由 `OnBackPressedDispatcher` 处理。

如需调整手感，在调用处覆盖 `SwipeBackDefaults` 中的默认值：

```kotlin
SwipeBackScaffold(
    backEnabled = canGoBack,
    flingVelocity = 400.dp,
    backActivationThreshold = 0.12f,
    settleCooldownMillis = 200,
) { /* ... */ }
```

## API 参考

`SwipeBackScaffold` 的参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `backEnabled` | — | 是否启用返回手势 |
| `backThreshold` | `1f / 3f` | 返回手势需覆盖的宽度比例 |
| `forwardPeek` | `null` | 前进手势露出的页面，传 `null` 可禁用前进手势 |
| `forwardThreshold` | `1f / 4f` | 前进手势需覆盖的宽度比例 |
| `onCommitForward` | `{}` | 前进提交后的回调 |
| `onBack` | `null` | 返回提交时回调，替代 `OnBackPressedDispatcher` |
| `dragSensitivity` | `0.6f` | 手指位移的阻尼系数 |
| `backActivationThreshold` | `0.05f` | 开启预测性返回会话的进度 |
| `flingVelocity` | `320.dp` | 视为快速轻弹的速度 |
| `settleCooldownMillis` | `280L` | 一次返回手势后的冷却时间 |
| `springBackAnimationSpec` | `SwipeBackDefaults.SpringBackSpec` | 返回手势未达阈值时播放的动画 |
| `forwardAnimationSpec` | `SwipeBackDefaults.ForwardAnimationSpec` | 前进预览滑入滑出的动画 |
| `tabContentRegion` | `null` | 属于横向滚动区域的触摸位置 |
| `excludeRegion` | `null` | 该区域内手势交由子级处理 |
| `tabAtLeftmost` | `{ true }` | 当前选中的 Tab 是否为最左侧 |
| `revealEntryId` | `{ null }` | 返回过程中要露出的页面 id |
| `content` | — | 当前页面的内容 |

`SwipeBackDefaults` 是这些默认值的集中定义处。`ScrimBox(entryId, maxAlpha = 0.3f)` 在页面被露出时压暗它，
`Modifier.blockPageSwipe()` 用于拦截遮罩层上的横向拖拽。

手势进行期间会设置以下标志位，供自行管理转场的宿主读取：

| 标志 | 含义 |
| --- | --- |
| `suppressForwardEnter` | 前进手势已经露出即将进入的页面 |
| `suppressPopAnim` | 本次弹出由手势触发 |
| `gestureDrivenPop` | 本次弹出来自滑动手势 |

## 注意事项

- 遮罩层（如底部弹层）通常只处理点击事件，在其上横向拖动会穿透到下层页面并触发页面手势。在遮罩根节点上添加 `Modifier.blockPageSwipe()` 可阻止穿透。
- 页面内包含横向 Pager 或 Tab 行时，传入 `tabContentRegion` 与 `tabAtLeftmost`，返回手势将仅在最左侧页面生效；对自行处理横向拖动的区域（例如视频进度条），通过 `excludeRegion` 将其排除。
- RTL 布局下返回手势为从右边缘向左滑动，派发事件的边缘由 `EDGE_LEFT` 变为 `BackEventCompat.EDGE_RIGHT`。
- 使用 Navigation Compose 时，手势进行期间会设置以下三个标志位，在转场配置中读取可避免同一动画播放两次：

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

## 示例

`:app` 模块以一个页面计数器演示该组件的用法：

```bash
./gradlew :app:installDebug
```

库的源码中包含 LTR 与 RTL 两个 `@Preview`，在 Android Studio 中打开模块即可直接渲染，无需运行应用。

## 使用方

- [Lime](https://github.com/larkz-hh/lime-app)：一个 Compose 社区应用。

## 参与贡献

欢迎提交 Issue 与 PR。构建命令、代码风格和提交规范见 [CONTRIBUTING.md](CONTRIBUTING.md)，版本变更见 [CHANGELOG.md](CHANGELOG.md)。

## 许可证

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

维护者 [@larkz-hh](https://github.com/larkz-hh)
