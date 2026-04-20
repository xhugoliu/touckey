package io.github.xhugoliu.touckey.feature.control

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.xhugoliu.touckey.ui.theme.TouckeyTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    uiState: ControlUiState,
    onQuickActionTap: (String) -> Unit,
    onEnvironmentActionTap: (ControlEnvironmentActionId) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    title = "首发预设动作",
                    subtitle = "先用固定快捷动作打磨控制体验，再逐步开放配置能力。",
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
                    title = "触控与手势占位",
                    subtitle = "手势层先保留入口，后续再接具体识别与 HID 转换逻辑。",
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
                onEnvironmentActionTap = {},
            )
        }
    }
}
