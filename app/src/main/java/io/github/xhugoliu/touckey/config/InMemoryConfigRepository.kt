package io.github.xhugoliu.touckey.config

import io.github.xhugoliu.touckey.gesture.GestureKind
import io.github.xhugoliu.touckey.gesture.GesturePreset
import io.github.xhugoliu.touckey.input.InputAction
import io.github.xhugoliu.touckey.input.MouseButton
import io.github.xhugoliu.touckey.input.QuickActionDefinition

class InMemoryConfigRepository : ConfigRepository {
    override fun loadQuickActions(): List<QuickActionDefinition> =
        listOf(
            QuickActionDefinition(
                id = "task_view",
                label = "任务视图",
                detail = "面向 Windows 的窗口总览快捷操作",
                action = InputAction.KeyComboAction(keys = listOf("Tab"), modifiers = listOf("Win")),
            ),
            QuickActionDefinition(
                id = "mission_control",
                label = "Mission Control",
                detail = "面向 macOS 的桌面与窗口概览",
                action = InputAction.KeyComboAction(keys = listOf("Up"), modifiers = listOf("Ctrl")),
            ),
            QuickActionDefinition(
                id = "play_pause",
                label = "播放 / 暂停",
                detail = "预设媒体控制，适合作为首发快捷动作",
                action = InputAction.ConsumerControlAction(usage = "PlayPause"),
            ),
            QuickActionDefinition(
                id = "browser_back",
                label = "浏览器后退",
                detail = "鼠标侧键风格的浏览历史后退动作",
                action = InputAction.MouseButtonClickAction(button = MouseButton.Back),
            ),
        )

    override fun loadGesturePresets(): List<GesturePreset> =
        listOf(
            GesturePreset(
                kind = GestureKind.SingleFingerMove,
                summary = "单指移动指针",
                action = InputAction.PointerMoveAction(deltaX = 0f, deltaY = 0f),
            ),
            GesturePreset(
                kind = GestureKind.TwoFingerScroll,
                summary = "双指滚动页面或列表，支持纵向和横向滚动",
                action = InputAction.ScrollAction(vertical = -120),
            ),
            GesturePreset(
                kind = GestureKind.DoubleTapAndHold,
                summary = "双击并按住，进入拖拽模式",
                action = InputAction.MouseButtonClickAction(button = MouseButton.Left),
            ),
            GesturePreset(
                kind = GestureKind.TwoFingerTap,
                summary = "双指轻点触发右键",
                action = InputAction.MouseButtonClickAction(button = MouseButton.Right),
            ),
        )
}
