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
import io.github.xhugoliu.touckey.input.InputAction
import io.github.xhugoliu.touckey.input.MouseButton
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
    modifier: Modifier = Modifier,
) {
    var currentRoute by rememberSaveable { mutableStateOf(ControlRoute.Console) }
    var currentPage by rememberSaveable { mutableStateOf(ControlPage.Touchpad) }
    var modifierMode by rememberSaveable { mutableStateOf(ModifierMode.Preset) }
    var armedModifiers by remember { mutableStateOf<List<String>>(emptyList()) }
    var activeHoldKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    val colorScheme = MaterialTheme.colorScheme

    fun releaseHeldKeys() {
        if (modifierMode == ModifierMode.Hold && activeHoldKeys.isNotEmpty()) {
            activeHoldKeys.forEach { key ->
                onInputAction(InputAction.KeyReleaseAction(key), false)
            }
            activeHoldKeys = emptyList()
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

        val onKeyboardPresetModifierToggle: (String) -> Unit = { modifierName ->
            if (uiState.isInputEnabled) {
                armedModifiers = toggleArmedModifier(armedModifiers, modifierName)
            }
        }
        val onKeyboardHoldPress: (String) -> Unit = { keyName ->
            if (uiState.isInputEnabled && keyName !in activeHoldKeys) {
                activeHoldKeys = activeHoldKeys + keyName
                onInputAction(InputAction.KeyPressAction(keyName), false)
            }
        }
        val onKeyboardHoldRelease: (String) -> Unit = { keyName ->
            if (keyName in activeHoldKeys) {
                activeHoldKeys = activeHoldKeys - keyName
                onInputAction(InputAction.KeyReleaseAction(keyName), false)
            }
        }
        val onKeyboardTap: (KeyboardKeySpec) -> Unit = { key ->
            if (uiState.isInputEnabled) {
                onInputAction(
                    InputAction.KeyComboAction(
                        keys = listOf(key.keyName),
                        modifiers = armedModifiers,
                    ),
                    false,
                )
                armedModifiers = modifiersAfterKeyTap(modifierMode, armedModifiers)
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
                            currentRoute = ControlRoute.Settings
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
                                modifierMode = modifierMode,
                                activePresetModifiers = armedModifiers,
                                activeHoldKeys = activeHoldKeys,
                                onPresetModifierToggle = onKeyboardPresetModifierToggle,
                                onHoldKeyPress = onKeyboardHoldPress,
                                onHoldKeyRelease = onKeyboardHoldRelease,
                                onKeyTap = onKeyboardTap,
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
                                    modifierMode = modifierMode,
                                    activePresetModifiers = armedModifiers,
                                    activeHoldKeys = activeHoldKeys,
                                    onPresetModifierToggle = onKeyboardPresetModifierToggle,
                                    onHoldKeyPress = onKeyboardHoldPress,
                                    onHoldKeyRelease = onKeyboardHoldRelease,
                                    onKeyTap = onKeyboardTap,
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
                            currentRoute = ControlRoute.Console
                        },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                    )
            }
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
    modifier: Modifier = Modifier,
) {
    if (compact) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier,
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
            )
        }

        if (showPageTabs) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                ControlPage.entries.forEach { page ->
                    CornerButton(
                        label = page.label,
                        emphasized = currentPage == page,
                        onTap = { onPageSelected(page) },
                    )
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
            },
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
    modifierMode: ModifierMode,
    activePresetModifiers: List<String>,
    activeHoldKeys: List<String>,
    onPresetModifierToggle: (String) -> Unit,
    onHoldKeyPress: (String) -> Unit,
    onHoldKeyRelease: (String) -> Unit,
    onKeyTap: (KeyboardKeySpec) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val keyRows = keyboardRows
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
                            onPresetModifierToggle = onPresetModifierToggle,
                            onHoldKeyPress = onHoldKeyPress,
                            onHoldKeyRelease = onHoldKeyRelease,
                            onKeyTap = onKeyTap,
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
    spec: KeyboardKeySpec,
    enabled: Boolean,
    modifierMode: ModifierMode,
    activePresetModifiers: List<String>,
    activeHoldKeys: List<String>,
    onPresetModifierToggle: (String) -> Unit,
    onHoldKeyPress: (String) -> Unit,
    onHoldKeyRelease: (String) -> Unit,
    onKeyTap: (KeyboardKeySpec) -> Unit,
    compact: Boolean,
) {
    var activeHoldPointerId by remember(spec.keyName, modifierMode) { mutableStateOf<Int?>(null) }
    val colorScheme = MaterialTheme.colorScheme
    val isHoldPressed = modifierMode == ModifierMode.Hold && activeHoldKeys.contains(spec.keyName)
    val isModifierArmed = modifierMode == ModifierMode.Preset && spec.kind == KeyboardKeyKind.Modifier && activePresetModifiers.contains(spec.keyName)
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
            spec.kind == KeyboardKeyKind.Function -> colorScheme.surfaceVariant
            spec.kind == KeyboardKeyKind.Navigation -> colorScheme.background
            spec.kind == KeyboardKeyKind.System -> colorScheme.surfaceVariant
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
                                    onHoldKeyPress(spec.keyName)
                                }
                                if (transition.shouldRelease) {
                                    onHoldKeyRelease(spec.keyName)
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
                            when (spec.kind) {
                                KeyboardKeyKind.Modifier -> onPresetModifierToggle(spec.keyName)
                                else -> onKeyTap(spec)
                            }
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

private enum class KeyboardKeyKind {
    Character,
    Modifier,
    Function,
    Navigation,
    System,
}

private data class KeyboardKeySpec(
    val keyName: String,
    val label: String,
    val weight: Float = 1f,
    val kind: KeyboardKeyKind = KeyboardKeyKind.Character,
    val shiftLabel: String? = null,
)

private fun KeyboardKeySpec.displayLabel(shiftActive: Boolean): String = if (shiftActive) shiftLabel ?: label else label

private val keyboardRows =
    listOf(
        listOf(
            KeyboardKeySpec("Escape", "Esc", kind = KeyboardKeyKind.Function),
            KeyboardKeySpec("F1", "F1", kind = KeyboardKeyKind.Function),
            KeyboardKeySpec("F2", "F2", kind = KeyboardKeyKind.Function),
            KeyboardKeySpec("F3", "F3", kind = KeyboardKeyKind.Function),
            KeyboardKeySpec("F4", "F4", kind = KeyboardKeyKind.Function),
            KeyboardKeySpec("F5", "F5", kind = KeyboardKeyKind.Function),
            KeyboardKeySpec("F6", "F6", kind = KeyboardKeyKind.Function),
            KeyboardKeySpec("F7", "F7", kind = KeyboardKeyKind.Function),
            KeyboardKeySpec("F8", "F8", kind = KeyboardKeyKind.Function),
            KeyboardKeySpec("F9", "F9", kind = KeyboardKeyKind.Function),
            KeyboardKeySpec("F10", "F10", kind = KeyboardKeyKind.Function),
            KeyboardKeySpec("F11", "F11", kind = KeyboardKeyKind.Function),
            KeyboardKeySpec("F12", "F12", kind = KeyboardKeyKind.Function),
            KeyboardKeySpec("Home", "Home", kind = KeyboardKeyKind.Navigation),
            KeyboardKeySpec("End", "End", kind = KeyboardKeyKind.Navigation),
            KeyboardKeySpec("PageUp", "PgUp", kind = KeyboardKeyKind.Navigation),
            KeyboardKeySpec("PageDown", "PgDn", kind = KeyboardKeyKind.Navigation),
            KeyboardKeySpec("Delete", "Delete", kind = KeyboardKeyKind.Navigation),
        ),
        listOf(
            KeyboardKeySpec("Grave", "`", shiftLabel = "~"),
            KeyboardKeySpec("1", "1", shiftLabel = "!"),
            KeyboardKeySpec("2", "2", shiftLabel = "@"),
            KeyboardKeySpec("3", "3", shiftLabel = "#"),
            KeyboardKeySpec("4", "4", shiftLabel = "$"),
            KeyboardKeySpec("5", "5", shiftLabel = "%"),
            KeyboardKeySpec("6", "6", shiftLabel = "^"),
            KeyboardKeySpec("7", "7", shiftLabel = "&"),
            KeyboardKeySpec("8", "8", shiftLabel = "*"),
            KeyboardKeySpec("9", "9", shiftLabel = "("),
            KeyboardKeySpec("0", "0", shiftLabel = ")"),
            KeyboardKeySpec("Minus", "-", shiftLabel = "_"),
            KeyboardKeySpec("Equal", "=", shiftLabel = "+"),
            KeyboardKeySpec("Backspace", "Backspace", weight = 1.8f, kind = KeyboardKeyKind.Navigation),
        ),
        listOf(
            KeyboardKeySpec("Tab", "Tab", weight = 1.4f, kind = KeyboardKeyKind.Navigation),
            KeyboardKeySpec("Q", "q", shiftLabel = "Q"),
            KeyboardKeySpec("W", "w", shiftLabel = "W"),
            KeyboardKeySpec("E", "e", shiftLabel = "E"),
            KeyboardKeySpec("R", "r", shiftLabel = "R"),
            KeyboardKeySpec("T", "t", shiftLabel = "T"),
            KeyboardKeySpec("Y", "y", shiftLabel = "Y"),
            KeyboardKeySpec("U", "u", shiftLabel = "U"),
            KeyboardKeySpec("I", "i", shiftLabel = "I"),
            KeyboardKeySpec("O", "o", shiftLabel = "O"),
            KeyboardKeySpec("P", "p", shiftLabel = "P"),
            KeyboardKeySpec("LeftBracket", "[", shiftLabel = "{"),
            KeyboardKeySpec("RightBracket", "]", shiftLabel = "}"),
            KeyboardKeySpec("Backslash", "\\", shiftLabel = "|"),
        ),
        listOf(
            KeyboardKeySpec("CapsLock", "Caps", weight = 1.8f, kind = KeyboardKeyKind.System),
            KeyboardKeySpec("A", "a", shiftLabel = "A"),
            KeyboardKeySpec("S", "s", shiftLabel = "S"),
            KeyboardKeySpec("D", "d", shiftLabel = "D"),
            KeyboardKeySpec("F", "f", shiftLabel = "F"),
            KeyboardKeySpec("G", "g", shiftLabel = "G"),
            KeyboardKeySpec("H", "h", shiftLabel = "H"),
            KeyboardKeySpec("J", "j", shiftLabel = "J"),
            KeyboardKeySpec("K", "k", shiftLabel = "K"),
            KeyboardKeySpec("L", "l", shiftLabel = "L"),
            KeyboardKeySpec("Semicolon", ";", shiftLabel = ":"),
            KeyboardKeySpec("Quote", "'", shiftLabel = "\""),
            KeyboardKeySpec("Enter", "Enter", weight = 2f, kind = KeyboardKeyKind.Navigation),
        ),
        listOf(
            KeyboardKeySpec("Shift", "Shift", weight = 1.6f, kind = KeyboardKeyKind.Modifier),
            KeyboardKeySpec("Z", "z", shiftLabel = "Z"),
            KeyboardKeySpec("X", "x", shiftLabel = "X"),
            KeyboardKeySpec("C", "c", shiftLabel = "C"),
            KeyboardKeySpec("V", "v", shiftLabel = "V"),
            KeyboardKeySpec("B", "b", shiftLabel = "B"),
            KeyboardKeySpec("N", "n", shiftLabel = "N"),
            KeyboardKeySpec("M", "m", shiftLabel = "M"),
            KeyboardKeySpec("Comma", ",", shiftLabel = "<"),
            KeyboardKeySpec("Period", ".", shiftLabel = ">"),
            KeyboardKeySpec("Slash", "/", shiftLabel = "?"),
            KeyboardKeySpec("Up", "Up", kind = KeyboardKeyKind.Navigation),
        ),
        listOf(
            KeyboardKeySpec("Ctrl", "Ctrl", weight = 1.2f, kind = KeyboardKeyKind.Modifier),
            KeyboardKeySpec("Cmd", "Cmd", weight = 1.2f, kind = KeyboardKeyKind.Modifier),
            KeyboardKeySpec("Alt", "Alt", weight = 1.2f, kind = KeyboardKeyKind.Modifier),
            KeyboardKeySpec("Space", "Space", weight = 4.2f, kind = KeyboardKeyKind.System),
            KeyboardKeySpec("Left", "Left", kind = KeyboardKeyKind.Navigation),
            KeyboardKeySpec("Down", "Down", kind = KeyboardKeyKind.Navigation),
            KeyboardKeySpec("Right", "Right", kind = KeyboardKeyKind.Navigation),
        ),
    )

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
                        ),
                    setupPrompt = null,
                    isInputEnabled = true,
                ),
            snackbarHost = {},
            onInputAction = { _, _ -> },
            onEnvironmentActionTap = {},
        )
    }
}
