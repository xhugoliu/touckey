package io.github.xhugoliu.touckey.feature.control

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.xhugoliu.touckey.input.InputAction
import io.github.xhugoliu.touckey.input.MouseButton
import io.github.xhugoliu.touckey.ui.theme.TouckeyTheme
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    uiState: ControlUiState,
    onQuickActionTap: (String) -> Unit,
    onTouchpadAction: (InputAction, Boolean) -> Unit,
    onEnvironmentActionTap: (ControlEnvironmentActionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var touchpadCapturing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "Touckey")
                        Text(
                            text = "Bluetooth HID 最小可用链路",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            userScrollEnabled = !touchpadCapturing,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            item {
                StatusCard(
                    connectionLabel = uiState.connectionLabel,
                    connectionDetail = uiState.connectionDetail,
                    hostLabel = uiState.hostLabel,
                    adapterLabel = uiState.adapterLabel,
                    environmentActions = uiState.environmentActions,
                    foregroundHint = uiState.foregroundHint,
                    onEnvironmentActionTap = onEnvironmentActionTap,
                )
            }

            item {
                SectionTitle(
                    title = "触控板 MVP",
                    subtitle = "单指移动、轻点左键、长按拖拽、双指滚动已经接入真实 HID 发送。",
                )
            }

            item {
                TouchpadCard(
                    enabled = uiState.canSendQuickActions,
                    onTouchpadAction = onTouchpadAction,
                    onInteractionLockChanged = { locked -> touchpadCapturing = locked },
                )
            }

            item {
                SectionTitle(
                    title = "首发预设动作",
                    subtitle = "快捷动作继续保留，用来验证键盘和媒体键链路。",
                )
            }

            items(uiState.quickActions) { quickAction ->
                QuickActionCard(
                    quickAction = quickAction,
                    enabled = uiState.canSendQuickActions,
                    onTap = { onQuickActionTap(quickAction.id) },
                )
            }

            item {
                SectionTitle(
                    title = "触控与手势说明",
                    subtitle = "当前优先打磨最小触控板手感，其他复杂手势放在后续阶段。",
                )
            }

            item {
                GestureCard(gestureHints = uiState.gestureHints)
            }

            uiState.lastDispatchMessage?.let { message ->
                item {
                    DispatchCard(message = message)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    connectionLabel: String,
    connectionDetail: String,
    hostLabel: String,
    adapterLabel: String,
    environmentActions: List<ControlEnvironmentAction>,
    foregroundHint: String?,
    onEnvironmentActionTap: (ControlEnvironmentActionId) -> Unit,
) {
    Card {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Text(text = connectionLabel, style = MaterialTheme.typography.headlineSmall)
            Text(text = connectionDetail, style = MaterialTheme.typography.bodyMedium)
            Text(text = "当前手机蓝牙名：$adapterLabel", style = MaterialTheme.typography.bodyMedium)
            Text(text = hostLabel, style = MaterialTheme.typography.labelLarge)
            environmentActions.forEach { action ->
                AssistChip(
                    onClick = { onEnvironmentActionTap(action.id) },
                    label = { Text(text = action.label) },
                )
                Text(text = action.detail, style = MaterialTheme.typography.bodySmall)
            }
            foregroundHint?.let { hint ->
                Text(text = hint, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TouchpadCard(
    enabled: Boolean,
    onTouchpadAction: (InputAction, Boolean) -> Unit,
    onInteractionLockChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val touchSlop = remember { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }
    val longPressTimeoutMillis = remember { ViewConfiguration.getLongPressTimeout().toLong() }

    var mode by remember { mutableStateOf(TouchpadMode.Idle) }
    var downTime by remember { mutableLongStateOf(0L) }
    var lastSingleX by remember { mutableFloatStateOf(0f) }
    var lastSingleY by remember { mutableFloatStateOf(0f) }
    var lastScrollX by remember { mutableFloatStateOf(0f) }
    var lastScrollY by remember { mutableFloatStateOf(0f) }
    var movedDistance by remember { mutableFloatStateOf(0f) }
    var twoFingerMovedDistance by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var twoFingerTapCandidate by remember { mutableStateOf(false) }

    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = MaterialTheme.shapes.large,
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.large,
                    )
                    .pointerInteropFilter { event ->
                        if (!enabled) {
                            false
                        } else {
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    onInteractionLockChanged(true)
                                    mode = TouchpadMode.SingleFinger
                                    downTime = event.eventTime
                                    lastSingleX = event.x
                                    lastSingleY = event.y
                                    movedDistance = 0f
                                    twoFingerMovedDistance = 0f
                                    dragging = false
                                    twoFingerTapCandidate = false
                                    true
                                }

                                MotionEvent.ACTION_POINTER_DOWN -> {
                                    onInteractionLockChanged(true)
                                    if (!dragging && event.pointerCount >= 2) {
                                        mode = TouchpadMode.TwoFingerScroll
                                        lastScrollX = averageX(event)
                                        lastScrollY = averageY(event)
                                        twoFingerMovedDistance = 0f
                                        twoFingerTapCandidate = true
                                    }
                                    true
                                }

                                MotionEvent.ACTION_MOVE -> {
                                    when {
                                        !dragging && event.pointerCount >= 2 -> {
                                            mode = TouchpadMode.TwoFingerScroll
                                            val currentScrollX = averageX(event)
                                            val currentScrollY = averageY(event)
                                            val deltaX = currentScrollX - lastScrollX
                                            val deltaY = currentScrollY - lastScrollY
                                            lastScrollX = currentScrollX
                                            lastScrollY = currentScrollY
                                            twoFingerMovedDistance += hypot(deltaX, deltaY)
                                            if (twoFingerMovedDistance > touchSlop) {
                                                twoFingerTapCandidate = false
                                            }
                                            if (abs(deltaX) >= 0.75f || abs(deltaY) >= 0.75f) {
                                                val horizontal = if (abs(deltaX) > abs(deltaY)) (-deltaX * 2.0f).roundToInt() else 0
                                                val vertical = if (abs(deltaY) >= abs(deltaX)) (-deltaY * 2.2f).roundToInt() else 0
                                                onTouchpadAction(
                                                    InputAction.ScrollAction(
                                                        vertical = vertical,
                                                        horizontal = horizontal,
                                                    ),
                                                    false,
                                                )
                                            }
                                            true
                                        }

                                        event.pointerCount == 1 -> {
                                            val dx = event.x - lastSingleX
                                            val dy = event.y - lastSingleY
                                            movedDistance += hypot(dx, dy)

                                            if (!dragging && movedDistance < touchSlop && event.eventTime - downTime >= longPressTimeoutMillis) {
                                                dragging = true
                                                onTouchpadAction(
                                                    InputAction.MouseButtonPressAction(MouseButton.Left),
                                                    true,
                                                )
                                            }

                                            if (abs(dx) >= 0.35f || abs(dy) >= 0.35f) {
                                                onTouchpadAction(
                                                    InputAction.PointerMoveAction(
                                                        deltaX = dx * 1.9f,
                                                        deltaY = dy * 1.9f,
                                                    ),
                                                    false,
                                                )
                                                lastSingleX = event.x
                                                lastSingleY = event.y
                                            }
                                            true
                                        }

                                        else -> true
                                    }
                                }

                                MotionEvent.ACTION_POINTER_UP -> {
                                    if (dragging) {
                                        true
                                    } else {
                                        mode = TouchpadMode.SingleFinger
                                        movedDistance = touchSlop
                                        val remainingIndex = if (event.actionIndex == 0) 1 else 0
                                        if (remainingIndex < event.pointerCount) {
                                            lastSingleX = event.getX(remainingIndex)
                                            lastSingleY = event.getY(remainingIndex)
                                        }
                                        if (twoFingerTapCandidate && event.eventTime - downTime < longPressTimeoutMillis && twoFingerMovedDistance < touchSlop * 1.5f) {
                                            onTouchpadAction(
                                                InputAction.MouseButtonClickAction(MouseButton.Right),
                                                true,
                                            )
                                        }
                                        twoFingerTapCandidate = false
                                        true
                                    }
                                }

                                MotionEvent.ACTION_UP -> {
                                    if (dragging) {
                                        onTouchpadAction(
                                            InputAction.MouseButtonReleaseAction(MouseButton.Left),
                                            true,
                                        )
                                    } else if (movedDistance < touchSlop) {
                                        onTouchpadAction(
                                            InputAction.MouseButtonClickAction(MouseButton.Left),
                                            true,
                                        )
                                    }
                                    mode = TouchpadMode.Idle
                                    dragging = false
                                    twoFingerTapCandidate = false
                                    movedDistance = 0f
                                    twoFingerMovedDistance = 0f
                                    onInteractionLockChanged(false)
                                    true
                                }

                                MotionEvent.ACTION_CANCEL -> {
                                    if (dragging) {
                                        onTouchpadAction(
                                            InputAction.MouseButtonReleaseAction(MouseButton.Left),
                                            true,
                                        )
                                    }
                                    mode = TouchpadMode.Idle
                                    dragging = false
                                    twoFingerTapCandidate = false
                                    movedDistance = 0f
                                    twoFingerMovedDistance = 0f
                                    onInteractionLockChanged(false)
                                    true
                                }

                                else -> false
                            }
                        }
                    }
                    .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (enabled) "Touchpad Ready" else "Touchpad Waiting",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text =
                        if (enabled) {
                            "单指移动指针，轻点左键，长按后拖拽，双指滑动滚轮，双指轻点右键。"
                        } else {
                            "先完成 HID 连接，触控区才会把动作真正发到电脑端。"
                        },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text =
                        when {
                            dragging -> "当前状态：拖拽中"
                            mode == TouchpadMode.TwoFingerScroll -> "当前状态：双指滚动"
                            else -> "当前状态：等待触控"
                        },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    quickAction: ControlQuickAction,
    enabled: Boolean,
    onTap: () -> Unit,
) {
    Card {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Text(text = quickAction.label, style = MaterialTheme.typography.titleLarge)
            Text(text = quickAction.detail, style = MaterialTheme.typography.bodyMedium)
            AssistChip(
                enabled = enabled,
                onClick = onTap,
                label = { Text(text = if (enabled) "发送该动作" else "等待 HID 连接") },
            )
        }
    }
}

@Composable
private fun GestureCard(gestureHints: List<ControlGestureHint>) {
    Card {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            gestureHints.forEach { hint ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = hint.title, style = MaterialTheme.typography.labelLarge)
                    Text(text = hint.detail, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun DispatchCard(message: String) {
    Card {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(20.dp),
        ) {
            Text(text = "最近一次动作", style = MaterialTheme.typography.titleMedium)
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun averageX(event: MotionEvent): Float {
    if (event.pointerCount <= 0) {
        return 0f
    }

    var total = 0f
    val samples = minOf(event.pointerCount, 2)
    for (index in 0 until samples) {
        total += event.getX(index)
    }
    return total / samples
}

private fun averageY(event: MotionEvent): Float {
    if (event.pointerCount <= 0) {
        return 0f
    }

    var total = 0f
    val samples = minOf(event.pointerCount, 2)
    for (index in 0 until samples) {
        total += event.getY(index)
    }
    return total / samples
}

private enum class TouchpadMode {
    Idle,
    SingleFinger,
    TwoFingerScroll,
}

@Preview(showBackground = true)
@Composable
private fun ControlScreenPreview() {
    TouckeyTheme {
        Surface {
            ControlScreen(
                uiState =
                    ControlUiState(
                        connectionLabel = "未连接",
                        connectionDetail = "先授予蓝牙权限并注册 HID，再让电脑搜索当前手机。",
                        hostLabel = "等待首个已配对桌面设备",
                        adapterLabel = "Xiaomi 15",
                        quickActions =
                            listOf(
                                ControlQuickAction("task_view", "任务视图", "面向 Windows 的窗口总览快捷操作"),
                                ControlQuickAction("play_pause", "播放 / 暂停", "预设媒体控制动作"),
                            ),
                        gestureHints =
                            listOf(
                                ControlGestureHint("单指移动", "驱动相对指针移动"),
                                ControlGestureHint("双指滚动", "映射为标准滚轮"),
                            ),
                        environmentActions =
                            listOf(
                                ControlEnvironmentAction(
                                    id = ControlEnvironmentActionId.RegisterHid,
                                    label = "注册 HID 设备",
                                    detail = "向系统注册 Touckey 的键盘/鼠标/媒体 report。",
                                ),
                            ),
                        canSendQuickActions = false,
                        foregroundHint = "配对测试时请保持 Touckey 在前台，或先启动前台服务。",
                        lastDispatchMessage = "最小蓝牙链路接通后，这里会显示实际发送结果。",
                    ),
                onQuickActionTap = {},
                onTouchpadAction = { _, _ -> },
                onEnvironmentActionTap = {},
            )
        }
    }
}
