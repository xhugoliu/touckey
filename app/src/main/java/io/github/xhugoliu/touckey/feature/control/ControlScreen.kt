package io.github.xhugoliu.touckey.feature.control

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.xhugoliu.touckey.input.InputAction
import io.github.xhugoliu.touckey.input.MouseButton
import io.github.xhugoliu.touckey.input.surface.BehaviorModifierMode
import io.github.xhugoliu.touckey.input.surface.BehaviorReducer
import io.github.xhugoliu.touckey.input.surface.BehaviorState
import io.github.xhugoliu.touckey.input.surface.DefaultKeyboardKeySpec
import io.github.xhugoliu.touckey.input.surface.DefaultSurfaceProfiles
import io.github.xhugoliu.touckey.input.surface.SurfaceEvent
import io.github.xhugoliu.touckey.input.surface.SurfaceZoneRole
import io.github.xhugoliu.touckey.ui.theme.TouckeyTheme
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun ControlScreen(
    uiState: ControlUiState,
    snackbarHost: @Composable () -> Unit,
    onInputAction: (InputAction, Boolean) -> Unit,
    onEnvironmentActionTap: (ControlEnvironmentActionId) -> Unit,
    onConnectionActionTap: (ControlConnectionAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentRoute by rememberSaveable { mutableStateOf(ControlRoute.Console) }
    var currentPage by rememberSaveable { mutableStateOf(ControlPage.Touchpad) }
    var modifierMode by rememberSaveable { mutableStateOf(ModifierMode.Preset) }
    var armedModifiers by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeHoldKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var showConnectionPanel by rememberSaveable { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val defaultSurface = remember { DefaultSurfaceProfiles.defaultKeyboard() }
    val keyboardRows =
        remember(defaultSurface) {
            DefaultSurfaceProfiles.keyboardRows(
                layoutProfile = defaultSurface.layoutProfile,
                keymapProfile = defaultSurface.keymapProfile,
            )
        }

    fun releaseHeldKeys() {
        if (modifierMode == ModifierMode.Hold && activeHoldKeys.isNotEmpty()) {
            activeHoldKeys.forEach { key ->
                onInputAction(InputAction.KeyReleaseAction(key), false)
            }
            activeHoldKeys = emptyList()
        }
    }

    fun dispatchKeyboardEvent(event: SurfaceEvent) {
        val result =
            BehaviorReducer.reduce(
                event = event,
                keymapProfile = defaultSurface.keymapProfile,
                state =
                    BehaviorState(
                        modifierMode = modifierMode.toBehaviorModifierMode(),
                        armedModifiers = armedModifiers,
                        activeHoldKeys = activeHoldKeys,
                    ),
                pageId = DefaultSurfaceProfiles.KEYBOARD_PAGE_ID,
            )
        armedModifiers = result.nextState.armedModifiers
        activeHoldKeys = result.nextState.activeHoldKeys
        result.dispatch.actions.forEach { action ->
            onInputAction(action, result.dispatch.shouldSurfaceResult)
        }
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    colorScheme.background,
                                    colorScheme.surfaceVariant,
                                    colorScheme.background,
                                ),
                        ),
                ),
    ) {
        val isCompactLayout = maxWidth < 720.dp
        val isPortraitLayout = maxHeight > maxWidth
        val contentHorizontalPadding = if (isCompactLayout) 12.dp else 24.dp
        val contentVerticalPadding = if (isCompactLayout) 12.dp else 20.dp
        val portraitChromeInsets =
            if (isPortraitLayout) {
                Modifier
                    .displayCutoutPadding()
                    .systemBarsPadding()
            } else {
                Modifier
            }
        val consoleContentPadding =
            Modifier.padding(
                horizontal = contentHorizontalPadding,
                vertical = if (isPortraitLayout) 10.dp else contentVerticalPadding,
            )

        val onKeyboardZoneTap: (String) -> Unit = { zoneId ->
            if (uiState.isInputEnabled) {
                dispatchKeyboardEvent(
                    SurfaceEvent.ZoneTap(
                        zoneId = zoneId,
                        pointerId = 0,
                        x = 0f,
                        y = 0f,
                    ),
                )
            }
        }
        val onKeyboardZoneDown: (String) -> Unit = { zoneId ->
            if (uiState.isInputEnabled) {
                dispatchKeyboardEvent(
                    SurfaceEvent.ZoneDown(
                        zoneId = zoneId,
                        pointerId = 0,
                        x = 0f,
                        y = 0f,
                    ),
                )
            }
        }
        val onKeyboardZoneUp: (String) -> Unit = { zoneId ->
            if (uiState.isInputEnabled) {
                dispatchKeyboardEvent(
                    SurfaceEvent.ZoneUp(
                        zoneId = zoneId,
                        pointerId = 0,
                        x = 0f,
                        y = 0f,
                    ),
                )
            }
        }

        ConsoleAtmosphere()
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = snackbarHost,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (currentRoute == ControlRoute.Console) {
                    CornerChrome(
                        currentPage = currentPage,
                        connection = uiState.connection,
                        compact = isCompactLayout,
                        showPageTabs = !isPortraitLayout,
                        onPageSelected = { page ->
                            currentPage = page
                            releaseHeldKeys()
                            armedModifiers = emptyList()
                        },
                        onSettingsTap = {
                            releaseHeldKeys()
                            armedModifiers = emptyList()
                            showConnectionPanel = false
                            currentRoute = ControlRoute.Settings
                        },
                        onConnectionTap = {
                            if (uiState.connection.isActionable) {
                                showConnectionPanel = true
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(portraitChromeInsets)
                                .padding(horizontal = if (isCompactLayout) 12.dp else 16.dp, vertical = 12.dp),
                    )
                }
            },
            bottomBar = {
                if (currentRoute == ControlRoute.Console) {
                    uiState.setupPrompt?.let { prompt ->
                        SetupPrompt(
                            prompt = prompt,
                            onActionTap = onEnvironmentActionTap,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = if (isCompactLayout) 12.dp else 20.dp, vertical = 12.dp)
                                    .then(portraitChromeInsets),
                        )
                    }
                }
            },
        ) { innerPadding ->
            when (currentRoute) {
                ControlRoute.Console -> {
                    if (isPortraitLayout) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .then(consoleContentPadding),
                        ) {
                            TouchpadPage(
                                enabled = uiState.isInputEnabled,
                                onTouchpadAction = onInputAction,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(0.60f),
                            )
                            KeyboardPage(
                                enabled = uiState.isInputEnabled,
                                keyRows = keyboardRows,
                                modifierMode = modifierMode,
                                activePresetModifiers = armedModifiers,
                                activeHoldKeys = activeHoldKeys,
                                onZoneTap = onKeyboardZoneTap,
                                onZoneDown = onKeyboardZoneDown,
                                onZoneUp = onKeyboardZoneUp,
                                compact = true,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(0.40f),
                            )
                        }
                    } else {
                        when (currentPage) {
                            ControlPage.Keyboard ->
                                KeyboardPage(
                                    enabled = uiState.isInputEnabled,
                                    keyRows = keyboardRows,
                                    modifierMode = modifierMode,
                                    activePresetModifiers = armedModifiers,
                                    activeHoldKeys = activeHoldKeys,
                                    onZoneTap = onKeyboardZoneTap,
                                    onZoneDown = onKeyboardZoneDown,
                                    onZoneUp = onKeyboardZoneUp,
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                            .then(consoleContentPadding),
                                )

                            ControlPage.Touchpad ->
                                TouchpadPage(
                                    enabled = uiState.isInputEnabled,
                                    onTouchpadAction = onInputAction,
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                            .then(consoleContentPadding),
                                )
                        }
                    }
                }

                ControlRoute.Settings ->
                    SettingsScreen(
                        modifierMode = modifierMode,
                        onModifierModeSelected = { mode ->
                            releaseHeldKeys()
                            armedModifiers = emptyList()
                            modifierMode = mode
                        },
                        onBackTap = {
                            releaseHeldKeys()
                            armedModifiers = emptyList()
                            showConnectionPanel = false
                            currentRoute = ControlRoute.Console
                        },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                    )
            }
        }

        if (showConnectionPanel) {
            ConnectionControlDialog(
                connection = uiState.connection,
                onDismiss = { showConnectionPanel = false },
                onActionTap = { action ->
                    showConnectionPanel = false
                    onConnectionActionTap(action)
                },
            )
        }
    }
}

@Composable
private fun ConsoleAtmosphere() {
    val colorScheme = MaterialTheme.colorScheme

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    colorScheme.onBackground.copy(alpha = 0.05f),
                                    Color.Transparent,
                                ),
                            radius = 900f,
                        ),
                ),
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    colorScheme.outline.copy(alpha = 0.14f),
                                    Color.Transparent,
                                ),
                            radius = 1100f,
                        ),
                ),
    )
}

@Composable
private fun CornerChrome(
    currentPage: ControlPage,
    connection: ControlConnectionUiState,
    compact: Boolean,
    showPageTabs: Boolean,
    onPageSelected: (ControlPage) -> Unit,
    onSettingsTap: () -> Unit,
    onConnectionTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CornerButton(
                    label = "Settings",
                    emphasized = false,
                    onTap = onSettingsTap,
                    modifier = Modifier.weight(1f),
                )
                ConnectionBadge(
                    connection = connection,
                    modifier = Modifier.weight(1.25f),
                    multiline = false,
                    showDetail = false,
                    onTap = onConnectionTap,
                )
            }

            if (showPageTabs) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ControlPage.entries.forEach { page ->
                        CornerButton(
                            label = page.label,
                            emphasized = currentPage == page,
                            onTap = { onPageSelected(page) },
                        )
                    }
                }
            }
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
            modifier = modifier,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CornerButton(
                        label = "Settings",
                        emphasized = false,
                        onTap = onSettingsTap,
                    )
                    ConnectionBadge(
                        connection = connection,
                        showDetail = false,
                        onTap = onConnectionTap,
                    )
                }
            }

            if (showPageTabs) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ControlPage.entries.forEach { page ->
                        CornerButton(
                            label = page.label,
                            emphasized = currentPage == page,
                            onTap = { onPageSelected(page) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionBadge(
    connection: ControlConnectionUiState,
    modifier: Modifier = Modifier,
    multiline: Boolean = false,
    showDetail: Boolean = true,
    onTap: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor =
        when (connection.accent) {
            ControlStatusAccent.Positive -> colorScheme.onSurface
            ControlStatusAccent.Warning -> colorScheme.onSurface.copy(alpha = 0.72f)
            ControlStatusAccent.Neutral -> colorScheme.onSurface.copy(alpha = 0.44f)
            ControlStatusAccent.Critical -> colorScheme.onSurface.copy(alpha = 0.92f)
        }

    Surface(
        color = colorScheme.surface.copy(alpha = 0.9f),
        shape = RoundedCornerShape(16.dp),
        modifier =
            if (multiline) {
                modifier
            } else {
                modifier.widthIn(max = 420.dp)
            }
                .then(
                    if (connection.isActionable) {
                        Modifier.clickable(onClick = onTap)
                    } else {
                        Modifier
                    },
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor),
            )
            Text(
                text =
                    if (showDetail) {
                        "${connection.label} · ${connection.detail}"
                    } else {
                        connection.label
                    },
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = if (multiline) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConnectionControlDialog(
    connection: ControlConnectionUiState,
    onDismiss: () -> Unit,
    onActionTap: (ControlConnectionAction) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = connection.panelTitle,
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    CornerButton(
                        label = "Close",
                        emphasized = false,
                        onTap = onDismiss,
                    )
                }

                connection.pendingLabel?.let { pendingLabel ->
                    Text(
                        text = pendingLabel,
                        color = colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Text(
                    text = connection.panelDetail,
                    color = colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )

                connection.currentHost?.let { host ->
                    HostSummary(host = host)
                }

                if (connection.recentHosts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Recent hosts",
                            color = colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        connection.recentHosts.forEach { host ->
                            HostSummary(host = host)
                        }
                    }
                }

                if (connection.actions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        connection.actions.forEach { action ->
                            ConnectionActionButton(
                                action = action,
                                onTap = { onActionTap(action) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostSummary(host: ControlHostUiState) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(colorScheme.surfaceVariant)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = if (host.isCurrent) "${host.name} · Current" else host.name,
            color = colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${host.platformLabel} · ${host.address}",
            color = colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConnectionActionButton(
    action: ControlConnectionAction,
    onTap: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val backgroundColor =
        when {
            !action.enabled -> colorScheme.surfaceVariant
            action.emphasized -> colorScheme.primary
            else -> colorScheme.surface
        }
    val borderColor =
        when {
            action.emphasized && action.enabled -> colorScheme.primary
            else -> colorScheme.outline
        }
    val textColor =
        when {
            !action.enabled -> colorScheme.onSurface.copy(alpha = 0.42f)
            action.emphasized -> colorScheme.onPrimary
            else -> colorScheme.onSurface
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(backgroundColor)
                .border(width = 1.dp, color = borderColor, shape = shape)
                .clickable(enabled = action.enabled, onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = action.label,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CornerButton(
    label: String,
    emphasized: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val backgroundColor =
        if (emphasized) {
            colorScheme.primary
        } else {
            colorScheme.surface.copy(alpha = 0.88f)
        }
    val borderColor =
        if (emphasized) {
            colorScheme.primary
        } else {
            colorScheme.outline
        }
    val textColor =
        if (emphasized) {
            colorScheme.onPrimary
        } else {
            colorScheme.onSurface
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .clip(shape)
                .background(backgroundColor)
                .border(width = 1.dp, color = borderColor, shape = shape)
                .clickable(onClick = onTap)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun KeyboardPage(
    enabled: Boolean,
    keyRows: List<List<DefaultKeyboardKeySpec>>,
    modifierMode: ModifierMode,
    activePresetModifiers: List<String>,
    activeHoldKeys: List<String>,
    onZoneTap: (String) -> Unit,
    onZoneDown: (String) -> Unit,
    onZoneUp: (String) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val keyboardShape = RoundedCornerShape(if (compact) 20.dp else 28.dp)
    val keyGridSpacing = if (compact) 4.dp else 8.dp
    val keyboardPadding = if (compact) 6.dp else 12.dp

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = keyboardShape,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(keyGridSpacing),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(keyboardPadding),
        ) {
            keyRows.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(keyGridSpacing),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                ) {
                    row.forEach { key ->
                        KeyboardKey(
                            spec = key,
                            enabled = enabled,
                            modifierMode = modifierMode,
                            activePresetModifiers = activePresetModifiers,
                            activeHoldKeys = activeHoldKeys,
                            onZoneTap = onZoneTap,
                            onZoneDown = onZoneDown,
                            onZoneUp = onZoneUp,
                            compact = compact,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.KeyboardKey(
    spec: DefaultKeyboardKeySpec,
    enabled: Boolean,
    modifierMode: ModifierMode,
    activePresetModifiers: List<String>,
    activeHoldKeys: List<String>,
    onZoneTap: (String) -> Unit,
    onZoneDown: (String) -> Unit,
    onZoneUp: (String) -> Unit,
    compact: Boolean,
) {
    var activeHoldPointerId by remember(spec.zoneId, modifierMode) { mutableStateOf<Int?>(null) }
    val colorScheme = MaterialTheme.colorScheme
    val isHoldPressed = modifierMode == ModifierMode.Hold && activeHoldKeys.contains(spec.keyName)
    val isModifierArmed = modifierMode == ModifierMode.Preset && spec.role == SurfaceZoneRole.Modifier && activePresetModifiers.contains(spec.keyName)
    val activeShift =
        when (modifierMode) {
            ModifierMode.Preset -> activePresetModifiers.contains("Shift")
            ModifierMode.Hold -> activeHoldKeys.contains("Shift")
        }
    val shape = RoundedCornerShape(if (compact) 12.dp else 18.dp)

    val backgroundColor =
        when {
            !enabled -> colorScheme.surfaceVariant
            isHoldPressed || isModifierArmed -> colorScheme.primary
            spec.role == SurfaceZoneRole.Function -> colorScheme.surfaceVariant
            spec.role == SurfaceZoneRole.Navigation -> colorScheme.background
            spec.role == SurfaceZoneRole.System -> colorScheme.surfaceVariant
            else -> colorScheme.surface
        }
    val borderColor =
        when {
            isHoldPressed || isModifierArmed -> colorScheme.primary
            enabled -> colorScheme.outline
            else -> colorScheme.outline.copy(alpha = 0.6f)
        }
    val textColor =
        if (enabled && (isHoldPressed || isModifierArmed)) {
            colorScheme.onPrimary
        } else {
            colorScheme.onSurface.copy(alpha = if (enabled) 0.94f else 0.35f)
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .weight(spec.weight)
                .fillMaxHeight()
                .clip(shape)
                .background(backgroundColor)
                .border(width = 1.dp, color = borderColor, shape = shape)
                .then(
                    if (modifierMode == ModifierMode.Hold) {
                        Modifier.pointerInteropFilter { event ->
                            if (!enabled) {
                                false
                            } else {
                                val actionPointerId =
                                    if (event.pointerCount > 0 && event.actionIndex in 0 until event.pointerCount) {
                                        event.getPointerId(event.actionIndex)
                                    } else {
                                        -1
                                    }
                                val transition =
                                    reduceHoldPointerEvent(
                                        activePointerId = activeHoldPointerId,
                                        actionMasked = event.actionMasked,
                                        actionPointerId = actionPointerId,
                                    )

                                if (transition.shouldPress) {
                                    onZoneDown(spec.zoneId)
                                }
                                if (transition.shouldRelease) {
                                    onZoneUp(spec.zoneId)
                                }
                                activeHoldPointerId = transition.nextPointerId
                                activeHoldPointerId != null || transition.shouldRelease
                            }
                        }
                    } else {
                        Modifier.clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onZoneTap(spec.zoneId)
                        }
                    }
                )
                .padding(
                    horizontal = if (compact) 2.dp else 4.dp,
                    vertical = if (compact) 1.dp else 2.dp,
                ),
    ) {
        Text(
            text = spec.displayLabel(activeShift),
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun TouchpadPage(
    enabled: Boolean,
    onTouchpadAction: (InputAction, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    TouchpadSurface(
        enabled = enabled,
        onTouchpadAction = onTouchpadAction,
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun TouchpadSurface(
    enabled: Boolean,
    onTouchpadAction: (InputAction, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
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

    val borderColor =
        when {
            !enabled -> colorScheme.outline.copy(alpha = 0.5f)
            dragging -> colorScheme.primary
            mode == TouchpadMode.TwoFingerScroll -> colorScheme.secondary
            else -> colorScheme.outline
        }

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(32.dp))
                .background(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    colorScheme.surface,
                                    colorScheme.surfaceVariant,
                                ),
                        ),
                )
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(32.dp),
                )
                .pointerInteropFilter { event ->
                    if (!enabled) {
                        false
                    } else {
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
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
                                true
                            }

                            else -> false
                        }
                    }
                },
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorScheme.surface.copy(alpha = 0.92f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (enabled) "Precision touchpad" else "Connect to unlock the touchpad",
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun SetupPrompt(
    prompt: ControlSetupPrompt,
    onActionTap: (ControlEnvironmentActionId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        color = colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        modifier = modifier.widthIn(max = 520.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = prompt.title,
                color = colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = prompt.detail,
                color = colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                prompt.actions.forEachIndexed { index, action ->
                    CornerButton(
                        label = action.label,
                        emphasized = index == 0,
                        onTap = { onActionTap(action.id) },
                    )
                }
            }
        }
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

private enum class ControlRoute {
    Console,
    Settings,
}

private enum class ControlPage(val label: String) {
    Keyboard("Keyboard"),
    Touchpad("Touchpad"),
}

private enum class TouchpadMode {
    Idle,
    SingleFinger,
    TwoFingerScroll,
}

private fun ModifierMode.toBehaviorModifierMode(): BehaviorModifierMode =
    when (this) {
        ModifierMode.Preset -> BehaviorModifierMode.Preset
        ModifierMode.Hold -> BehaviorModifierMode.Hold
    }


@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun ControlScreenPreview() {
    TouckeyTheme {
        ControlScreen(
            uiState =
                ControlUiState(
                    connection =
                        ControlConnectionUiState(
                            label = "MacBook Pro",
                            detail = "Connected to Hugo's MacBook Pro",
                            accent = ControlStatusAccent.Positive,
                            isActionable = true,
                            panelTitle = "Connection",
                            panelDetail = "Manage the active desktop host.",
                            currentHost =
                                ControlHostUiState(
                                    name = "MacBook Pro",
                                    address = "00:11:22:33:44:55",
                                    platformLabel = "蓝牙主机",
                                    isCurrent = true,
                                ),
                            recentHosts = emptyList(),
                            actions =
                                listOf(
                                    ControlConnectionAction(
                                        id = ControlConnectionActionId.Disconnect,
                                        label = "Disconnect",
                                        emphasized = true,
                                    ),
                                ),
                            pendingLabel = null,
                        ),
                    setupPrompt = null,
                    isInputEnabled = true,
                ),
            snackbarHost = {},
            onInputAction = { _, _ -> },
            onEnvironmentActionTap = {},
            onConnectionActionTap = {},
        )
    }
}
