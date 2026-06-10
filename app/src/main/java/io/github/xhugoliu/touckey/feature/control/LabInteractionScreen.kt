package io.github.xhugoliu.touckey.feature.control

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xhugoliu.touckey.input.InputAction
import kotlinx.coroutines.delay

@Composable
internal fun LabInteractionPage(
    onInputAction: (InputAction, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val touchSlop = remember { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }
    val segmentPx = remember(touchSlop) { touchSlop * 1.9f }

    var currentLayer by rememberSaveable { mutableStateOf(LabLayer.Default) }
    var leftCandidate by remember { mutableStateOf(LabCell()) }
    var rightCandidate by remember { mutableStateOf(LabCell()) }
    var leftTriggerState by remember { mutableStateOf(LabTriggerState()) }
    var rightTriggerState by remember { mutableStateOf(LabTriggerState()) }
    var leftMatrixActive by remember { mutableStateOf(false) }
    var rightMatrixActive by remember { mutableStateOf(false) }
    var leftPendingHorizontalGesture by remember { mutableStateOf<LabGesture?>(null) }
    var rightPendingHorizontalGesture by remember { mutableStateOf<LabGesture?>(null) }

    LaunchedEffect(leftTriggerState.activeHoldBinding, rightTriggerState.activeHoldBinding) {
        val repeatActions =
            listOfNotNull(
                leftTriggerState.activeHoldBinding?.repeatAction,
                rightTriggerState.activeHoldBinding?.repeatAction,
            )
        if (repeatActions.isEmpty()) {
            return@LaunchedEffect
        }
        while (true) {
            repeatActions.forEach { action ->
                onInputAction(action, false)
            }
            delay(LAB_REPEAT_INTERVAL_MILLIS)
        }
    }

    fun pulse(pattern: LabHapticPattern) {
        pattern.constants.forEach { constant ->
            view.performHapticFeedback(constant)
        }
    }

    fun triggerStateFor(side: LabSide): LabTriggerState =
        when (side) {
            LabSide.Left -> leftTriggerState
            LabSide.Right -> rightTriggerState
        }

    fun setTriggerState(
        side: LabSide,
        state: LabTriggerState,
    ) {
        when (side) {
            LabSide.Left -> leftTriggerState = state
            LabSide.Right -> rightTriggerState = state
        }
    }

    fun dispatchTransition(
        update: () -> Unit,
    ) {
        val previousStates = listOf(leftTriggerState, rightTriggerState)
        update()
        val nextStates = listOf(leftTriggerState, rightTriggerState)
        labStateTransitionActions(previousStates, nextStates).forEach { action ->
            onInputAction(action, false)
        }
    }

    fun candidateFor(side: LabSide): LabCell =
        when (side) {
            LabSide.Left -> leftCandidate
            LabSide.Right -> rightCandidate
        }

    fun setCandidate(
        side: LabSide,
        cell: LabCell,
    ) {
        when (side) {
            LabSide.Left -> leftCandidate = cell
            LabSide.Right -> rightCandidate = cell
        }
    }

    fun releaseHold(
        side: LabSide,
        pulseFeedback: Boolean,
        clearLocks: Boolean,
    ) {
        val currentState = triggerStateFor(side)
        if (currentState.activeHoldBinding == null) {
            return
        }
        dispatchTransition {
            setTriggerState(
                side,
                currentState.copy(
                    activeHoldBinding = null,
                    lockedModifiers = if (clearLocks) emptyList() else currentState.lockedModifiers,
                ),
            )
        }
        if (pulseFeedback) {
            pulse(LabHapticPattern.HoldUp)
        }
    }

    fun clearTriggerStateForLayerSwitch() {
        dispatchTransition {
            leftTriggerState = labResetTriggerStateForLayerSwitch()
            rightTriggerState = labResetTriggerStateForLayerSwitch()
        }
    }

    fun performLayerSwitch(layerSwitch: LabLayerSwitch) {
        clearTriggerStateForLayerSwitch()
        leftCandidate = labResetCellForLayerSwitch()
        rightCandidate = labResetCellForLayerSwitch()
        leftPendingHorizontalGesture = null
        rightPendingHorizontalGesture = null
        currentLayer = labLayerAfter(currentLayer, layerSwitch)
        pulse(LabHapticPattern.LayerSwitch)
    }

    fun performGesture(
        side: LabSide,
        gesture: LabGesture,
    ) {
        val current = candidateFor(side)
        val move = applyLabGesture(current, gesture)
        val triggerState = triggerStateFor(side)
        val interruptedBinding = interruptedLabHoldBinding(triggerState.activeHoldBinding, move)
        val nextLockedModifiers = interruptedLockedModifiers(triggerState.lockedModifiers, move)

        when {
            gesture == LabGesture.Tap -> {
                if (nextLockedModifiers != triggerState.lockedModifiers) {
                    setTriggerState(side, triggerState.copy(lockedModifiers = nextLockedModifiers))
                }
                setCandidate(side, move.nextCell)
                pulse(LabHapticPattern.Reset)
            }

            move.blocked -> {
            }

            move.moved -> {
                interruptedBinding?.let {
                    releaseHold(side, pulseFeedback = true, clearLocks = true)
                }
                if (nextLockedModifiers != triggerStateFor(side).lockedModifiers) {
                    setTriggerState(
                        side,
                        triggerStateFor(side).copy(lockedModifiers = nextLockedModifiers),
                    )
                }
                setCandidate(side, move.nextCell)
                pulse(LabHapticPattern.OrthogonalMove)
            }
        }
    }

    fun pendingHorizontalGestureFor(side: LabSide): LabGesture? =
        when (side) {
            LabSide.Left -> leftPendingHorizontalGesture
            LabSide.Right -> rightPendingHorizontalGesture
        }

    fun setPendingHorizontalGesture(
        side: LabSide,
        gesture: LabGesture?,
    ) {
        when (side) {
            LabSide.Left -> leftPendingHorizontalGesture = gesture
            LabSide.Right -> rightPendingHorizontalGesture = gesture
        }
    }

    fun matrixActiveFor(side: LabSide): Boolean =
        when (side) {
            LabSide.Left -> leftMatrixActive
            LabSide.Right -> rightMatrixActive
        }

    fun otherSide(side: LabSide): LabSide =
        when (side) {
            LabSide.Left -> LabSide.Right
            LabSide.Right -> LabSide.Left
        }

    fun commitPendingHorizontalGesture(side: LabSide) {
        val pending = pendingHorizontalGestureFor(side) ?: return
        setPendingHorizontalGesture(side, null)
        performGesture(side, pending)
    }

    fun commitPendingHorizontalGestures() {
        commitPendingHorizontalGesture(LabSide.Left)
        commitPendingHorizontalGesture(LabSide.Right)
    }

    fun onGesture(
        side: LabSide,
        gesture: LabGesture,
    ) {
        val other = otherSide(side)
        if (gesture == LabGesture.Tap) {
            commitPendingHorizontalGestures()
            performGesture(side, gesture)
            return
        }

        val horizontal = gesture == LabGesture.Left || gesture == LabGesture.Right
        if (horizontal && matrixActiveFor(other)) {
            val otherPending = pendingHorizontalGestureFor(other)
            if (otherPending != null) {
                val leftGesture = if (side == LabSide.Left) gesture else otherPending
                val rightGesture = if (side == LabSide.Right) gesture else otherPending
                val layerSwitch = labLayerSwitchForGestures(leftGesture, rightGesture)
                setPendingHorizontalGesture(other, null)
                if (layerSwitch != null) {
                    performLayerSwitch(layerSwitch)
                } else {
                    performGesture(other, otherPending)
                    performGesture(side, gesture)
                }
                return
            }

            commitPendingHorizontalGesture(side)
            setPendingHorizontalGesture(side, gesture)
            return
        }

        commitPendingHorizontalGestures()
        performGesture(side, gesture)
    }

    fun onHoldDown(
        side: LabSide,
        binding: LabBinding,
    ) {
        if (!binding.enabled || triggerStateFor(side).activeHoldBinding != null) {
            return
        }
        dispatchTransition {
            setTriggerState(
                side,
                triggerStateFor(side).copy(activeHoldBinding = binding),
            )
        }
        pulse(LabHapticPattern.HoldDown)
    }

    fun onToggleLock(
        side: LabSide,
        binding: LabBinding,
    ) {
        val modifierKey = binding.lockableModifierKey ?: return
        if (!isLockableModifier(modifierKey)) {
            return
        }
        dispatchTransition {
            val currentState = triggerStateFor(side)
            val nextLocked = toggleLockedModifier(currentState.lockedModifiers, modifierKey)
            setTriggerState(
                side,
                currentState.copy(
                    lockedModifiers = nextLocked,
                    activeHoldBinding =
                        if (currentState.activeHoldBinding?.activeKeyboardKeys == listOf(modifierKey)) {
                            null
                        } else {
                            currentState.activeHoldBinding
                        },
                ),
            )
        }
        pulse(LabHapticPattern.LockToggle)
    }

    fun onHoldUp(
        side: LabSide,
        clearLocks: Boolean,
    ) {
        if (triggerStateFor(side).activeHoldBinding == null) {
            return
        }
        releaseHold(side, pulseFeedback = true, clearLocks = clearLocks)
    }

    fun onMatrixActiveChange(
        side: LabSide,
        active: Boolean,
    ) {
        if (!active) {
            commitPendingHorizontalGesture(side)
        }
        when (side) {
            LabSide.Left -> leftMatrixActive = active
            LabSide.Right -> rightMatrixActive = active
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        LabSidePane(
            side = LabSide.Left,
            layer = currentLayer,
            candidate = leftCandidate,
            activeHoldLabel = leftTriggerState.activeHoldBinding?.label,
            lockedModifiers = leftTriggerState.lockedModifiers,
            tapSlopPx = touchSlop * 1.25f,
            segmentPx = segmentPx,
            onGesture = { onGesture(LabSide.Left, it) },
            onHoldDown = { binding -> onHoldDown(LabSide.Left, binding) },
            onToggleLock = { binding -> onToggleLock(LabSide.Left, binding) },
            onHoldUp = { clearLocks -> onHoldUp(LabSide.Left, clearLocks) },
            onMatrixActiveChange = { active -> onMatrixActiveChange(LabSide.Left, active) },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        )
        LabSidePane(
            side = LabSide.Right,
            layer = currentLayer,
            candidate = rightCandidate,
            activeHoldLabel = rightTriggerState.activeHoldBinding?.label,
            lockedModifiers = rightTriggerState.lockedModifiers,
            tapSlopPx = touchSlop * 1.25f,
            segmentPx = segmentPx,
            onGesture = { onGesture(LabSide.Right, it) },
            onHoldDown = { binding -> onHoldDown(LabSide.Right, binding) },
            onToggleLock = { binding -> onToggleLock(LabSide.Right, binding) },
            onHoldUp = { clearLocks -> onHoldUp(LabSide.Right, clearLocks) },
            onMatrixActiveChange = { active -> onMatrixActiveChange(LabSide.Right, active) },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        )
    }
}

@Composable
private fun LabSidePane(
    side: LabSide,
    layer: LabLayer,
    candidate: LabCell,
    activeHoldLabel: String?,
    lockedModifiers: List<String>,
    tapSlopPx: Float,
    segmentPx: Float,
    onGesture: (LabGesture) -> Unit,
    onHoldDown: (LabBinding) -> Unit,
    onToggleLock: (LabBinding) -> Unit,
    onHoldUp: (Boolean) -> Unit,
    onMatrixActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        LabHoldZone(
            side = side,
            candidateBinding = labBinding(side = side, cell = candidate, layer = layer),
            activeHoldLabel = activeHoldLabel,
            lockedModifiers = lockedModifiers,
            onHoldDown = onHoldDown,
            onToggleLock = onToggleLock,
            onHoldUp = onHoldUp,
            modifier =
                Modifier
                    .weight(2f)
                    .fillMaxWidth(),
        )
        LabMatrixZone(
            side = side,
            layer = layer,
            candidate = candidate,
            lockedModifiers = lockedModifiers,
            tapSlopPx = tapSlopPx,
            segmentPx = segmentPx,
            onGesture = onGesture,
            onActiveChange = onMatrixActiveChange,
            modifier =
                Modifier
                    .weight(8f)
                    .fillMaxWidth(),
        )
    }
}

@Composable
private fun LabHoldZone(
    side: LabSide,
    candidateBinding: LabBinding,
    activeHoldLabel: String?,
    lockedModifiers: List<String>,
    onHoldDown: (LabBinding) -> Unit,
    onToggleLock: (LabBinding) -> Unit,
    onHoldUp: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activePointerId by remember { mutableStateOf<Int?>(null) }
    var activeHoldTriggered by remember { mutableStateOf(false) }
    var lockTriggered by remember { mutableStateOf(false) }
    var lastUpTimeMillis by remember { mutableStateOf<Long?>(null) }
    var lastUpModifierKey by remember { mutableStateOf<String?>(null) }
    val colorScheme = MaterialTheme.colorScheme
    val doubleTapTimeoutMillis = ViewConfiguration.getDoubleTapTimeout().toLong()
    val active = activeHoldLabel != null
    val enabled = candidateBinding.enabled
    val modifierKey = candidateBinding.lockableModifierKey

    fun resetPointerState() {
        activePointerId = null
        activeHoldTriggered = false
        lockTriggered = false
    }

    Surface(
        color =
            when {
                active -> colorScheme.primary
                !enabled -> colorScheme.surfaceVariant.copy(alpha = 0.56f)
                else -> colorScheme.surface.copy(alpha = 0.94f)
            },
        shape = RoundedCornerShape(24.dp),
        modifier =
            modifier
                .pointerInteropFilter { event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_POINTER_DOWN,
                        -> {
                            if (activePointerId == null) {
                                activePointerId = event.pointerIdAtAction()
                                activeHoldTriggered = false
                                lockTriggered = false
                                if (enabled) {
                                    onHoldDown(candidateBinding)
                                    activeHoldTriggered = true
                                }
                            }
                            true
                        }

                        MotionEvent.ACTION_MOVE -> true

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL,
                        -> {
                            if (activePointerId != null) {
                                val isDoubleTap =
                                    enabled &&
                                        event.actionMasked == MotionEvent.ACTION_UP &&
                                        modifierKey != null &&
                                        modifierKey == lastUpModifierKey &&
                                        lastUpTimeMillis?.let { previousUp ->
                                            event.eventTime - previousUp <= doubleTapTimeoutMillis
                                        } == true
                                val preserveCurrentLock = modifierKey != null && modifierKey in lockedModifiers
                                if (activeHoldTriggered && !lockTriggered) {
                                    if (isDoubleTap) {
                                        onToggleLock(candidateBinding)
                                        lockTriggered = true
                                        activeHoldTriggered = false
                                    } else {
                                        onHoldUp(!preserveCurrentLock)
                                    }
                                }
                                if (event.actionMasked == MotionEvent.ACTION_UP) {
                                    lastUpTimeMillis = event.eventTime
                                    lastUpModifierKey = modifierKey
                                } else {
                                    lastUpTimeMillis = null
                                    lastUpModifierKey = null
                                }
                                resetPointerState()
                            }
                            true
                        }

                        MotionEvent.ACTION_POINTER_UP -> {
                            if (event.pointerIdAtAction() == activePointerId) {
                                val isDoubleTap =
                                    enabled &&
                                        modifierKey != null &&
                                        modifierKey == lastUpModifierKey &&
                                        lastUpTimeMillis?.let { previousUp ->
                                            event.eventTime - previousUp <= doubleTapTimeoutMillis
                                        } == true
                                val preserveCurrentLock = modifierKey != null && modifierKey in lockedModifiers
                                if (activeHoldTriggered && !lockTriggered) {
                                    if (isDoubleTap) {
                                        onToggleLock(candidateBinding)
                                        lockTriggered = true
                                        activeHoldTriggered = false
                                    } else {
                                        onHoldUp(!preserveCurrentLock)
                                    }
                                }
                                lastUpTimeMillis = event.eventTime
                                lastUpModifierKey = modifierKey
                                resetPointerState()
                            }
                            true
                        }

                        else -> true
                    }
                },
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text =
                    if (active) {
                        "HOLD ${activeHoldLabel.orEmpty()}"
                    } else if (lockedModifiers.isNotEmpty()) {
                        "LOCK ${lockedModifiers.joinToString("+")}"
                    } else if (!enabled) {
                        "NONE"
                    } else {
                        "HOLD ${candidateBinding.label}"
                    },
                color =
                    when {
                        active -> colorScheme.onPrimary
                        !enabled -> colorScheme.onSurfaceVariant
                        else -> colorScheme.onSurface
                    },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LabMatrixZone(
    side: LabSide,
    layer: LabLayer,
    candidate: LabCell,
    lockedModifiers: List<String>,
    tapSlopPx: Float,
    segmentPx: Float,
    onGesture: (LabGesture) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var activePointerId by remember { mutableStateOf<Int?>(null) }
    var downX by remember { mutableFloatStateOf(0f) }
    var downY by remember { mutableFloatStateOf(0f) }
    var lastX by remember { mutableFloatStateOf(0f) }
    var lastY by remember { mutableFloatStateOf(0f) }
    var accumulatedX by remember { mutableFloatStateOf(0f) }
    var accumulatedY by remember { mutableFloatStateOf(0f) }
    var hasSegmentMove by remember { mutableStateOf(false) }

    fun resetPointerState() {
        activePointerId = null
        accumulatedX = 0f
        accumulatedY = 0f
        hasSegmentMove = false
    }

    Box(
        modifier =
            modifier
                .pointerInteropFilter { event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_POINTER_DOWN,
                        -> {
                            if (activePointerId == null) {
                                val index = event.actionIndex.coerceIn(0, event.pointerCount - 1)
                                activePointerId = event.getPointerId(index)
                                onActiveChange(true)
                                downX = event.getX(index)
                                downY = event.getY(index)
                                lastX = downX
                                lastY = downY
                                accumulatedX = 0f
                                accumulatedY = 0f
                                hasSegmentMove = false
                            }
                            true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            activePointerId?.let { pointerId ->
                                val pointerIndex = event.findPointerIndex(pointerId)
                                if (pointerIndex >= 0) {
                                    val currentX = event.getX(pointerIndex)
                                    val currentY = event.getY(pointerIndex)
                                    accumulatedX += currentX - lastX
                                    accumulatedY += currentY - lastY
                                    lastX = currentX
                                    lastY = currentY

                                    val segments =
                                        segmentLabDrag(
                                            deltaX = accumulatedX,
                                            deltaY = accumulatedY,
                                            segmentPx = segmentPx,
                                        )
                                    if (segments.gestures.isNotEmpty()) {
                                        hasSegmentMove = true
                                        segments.gestures.forEach(onGesture)
                                        accumulatedX -= segments.consumedX
                                        accumulatedY -= segments.consumedY
                                    }
                                }
                            }
                            true
                        }

                        MotionEvent.ACTION_UP -> {
                            activePointerId?.let { pointerId ->
                                if (!hasSegmentMove) {
                                    event.handleLabTap(pointerId, downX, downY, tapSlopPx, onGesture)
                                }
                            }
                            onActiveChange(false)
                            resetPointerState()
                            true
                        }

                        MotionEvent.ACTION_POINTER_UP -> {
                            if (event.pointerIdAtAction() == activePointerId) {
                                activePointerId?.let { pointerId ->
                                    if (!hasSegmentMove) {
                                        event.handleLabTap(pointerId, downX, downY, tapSlopPx, onGesture)
                                    }
                                }
                                onActiveChange(false)
                                resetPointerState()
                            }
                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            onActiveChange(false)
                            resetPointerState()
                            true
                        }

                        else -> true
                    }
                },
    ) {
        LabMatrixGrid(
            side = side,
            layer = layer,
            candidate = candidate,
            lockedModifiers = lockedModifiers,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun LabMatrixGrid(
    side: LabSide,
    layer: LabLayer,
    candidate: LabCell,
    lockedModifiers: List<String>,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(LAB_CELL_GAP),
        modifier = modifier,
    ) {
        repeat(LAB_MATRIX_SIZE) { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(LAB_CELL_GAP),
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    ) {
                    repeat(LAB_MATRIX_SIZE) { column ->
                        val binding = labBinding(side = side, cell = LabCell(row = row, column = column), layer = layer)
                        LabMatrixCell(
                            label = binding.label,
                            enabled = binding.enabled,
                            selected = candidate.row == row && candidate.column == column,
                            locked = binding.lockableModifierKey in lockedModifiers,
                            center = row == LAB_CENTER_INDEX && column == LAB_CENTER_INDEX,
                        )
                }
            }
        }
    }
}

@Composable
private fun RowScope.LabMatrixCell(
    label: String,
    enabled: Boolean,
    selected: Boolean,
    locked: Boolean,
    center: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val targetBackgroundColor =
            when {
                selected -> colorScheme.primary
                locked -> colorScheme.tertiaryContainer
                center -> colorScheme.secondaryContainer
                !enabled -> colorScheme.surfaceVariant.copy(alpha = 0.44f)
                else -> colorScheme.background
            }
    val targetBorderColor =
        when {
                selected -> colorScheme.primary
                locked -> colorScheme.tertiary
                center -> colorScheme.secondary
                !enabled -> colorScheme.outline.copy(alpha = 0.24f)
                else -> colorScheme.outline.copy(alpha = 0.58f)
            }
    val targetTextColor =
        when {
                selected -> colorScheme.onPrimary
                locked -> colorScheme.onTertiaryContainer
                center -> colorScheme.onSecondaryContainer
                !enabled -> colorScheme.onSurfaceVariant.copy(alpha = 0.46f)
                else -> colorScheme.onSurfaceVariant
            }
    val backgroundColor by animateColorAsState(targetValue = targetBackgroundColor, label = "Lab cell background")
    val borderColor by animateColorAsState(targetValue = targetBorderColor, label = "Lab cell border")
    val textColor by animateColorAsState(targetValue = targetTextColor, label = "Lab cell text")
    val borderWidth by animateDpAsState(
        targetValue = if (selected || locked || center) 2.dp else 1.dp,
        label = "Lab cell border width",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(shape)
                .background(backgroundColor)
                .border(width = borderWidth, color = borderColor, shape = shape)
                .padding(horizontal = 2.dp),
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

private fun MotionEvent.handleLabTap(
    pointerId: Int,
    downX: Float,
    downY: Float,
    tapSlopPx: Float,
    onGesture: (LabGesture) -> Unit,
) {
    val pointerIndex = findPointerIndex(pointerId).takeIf { it >= 0 } ?: actionIndex.coerceIn(0, pointerCount - 1)
    val deltaX = getX(pointerIndex) - downX
    val deltaY = getY(pointerIndex) - downY
    if (detectLabGesture(deltaX = deltaX, deltaY = deltaY, minSwipePx = tapSlopPx) == LabGesture.Tap) {
        onGesture(LabGesture.Tap)
    }
}

private fun MotionEvent.pointerIdAtAction(): Int {
    if (pointerCount <= 0) {
        return -1
    }
    val index = actionIndex.coerceIn(0, pointerCount - 1)
    return getPointerId(index)
}

private val LAB_CELL_GAP: Dp = 6.dp
private const val LAB_REPEAT_INTERVAL_MILLIS = 24L

private enum class LabHapticPattern(
    val constants: List<Int>,
) {
    OrthogonalMove(listOf(HapticFeedbackConstants.CLOCK_TICK)),
    Reset(listOf(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.CLOCK_TICK)),
    LockToggle(listOf(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.VIRTUAL_KEY)),
    LayerSwitch(listOf(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.VIRTUAL_KEY)),
    HoldDown(listOf(HapticFeedbackConstants.LONG_PRESS)),
    HoldUp(listOf(HapticFeedbackConstants.VIRTUAL_KEY)),
}
