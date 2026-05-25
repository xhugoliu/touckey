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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun LabInteractionPage(
    onInputAction: (InputAction, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val touchSlop = remember { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }
    val segmentPx = remember(touchSlop) { touchSlop * 1.9f }
    val triggerLockDelayMillis = 2_000L

    var leftCandidate by remember { mutableStateOf(LabCell()) }
    var rightCandidate by remember { mutableStateOf(LabCell()) }
    var leftTriggerState by remember { mutableStateOf(LabTriggerState()) }
    var rightTriggerState by remember { mutableStateOf(LabTriggerState()) }

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
        if (currentState.activeHoldKey == null) {
            return
        }
        dispatchTransition {
            setTriggerState(
                side,
                currentState.copy(
                    activeHoldKey = null,
                    lockedModifiers = if (clearLocks) emptyList() else currentState.lockedModifiers,
                ),
            )
        }
        if (pulseFeedback) {
            pulse(LabHapticPattern.HoldUp)
        }
    }

    fun onGesture(
        side: LabSide,
        gesture: LabGesture,
    ) {
        val current = candidateFor(side)
        val move = applyLabGesture(current, gesture)
        val triggerState = triggerStateFor(side)
        val interruptedKey = interruptedLabHoldKey(triggerState.activeHoldKey, move)
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
                interruptedKey?.let { key ->
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

    fun onHoldDown(
        side: LabSide,
        binding: LabKeyBinding,
    ) {
        if (triggerStateFor(side).activeHoldKey != null) {
            return
        }
        dispatchTransition {
            setTriggerState(
                side,
                triggerStateFor(side).copy(activeHoldKey = binding.key),
            )
        }
        pulse(LabHapticPattern.HoldDown)
    }

    fun onToggleLock(
        side: LabSide,
        binding: LabKeyBinding,
    ) {
        if (!isLockableModifier(binding.key)) {
            return
        }
        dispatchTransition {
            val currentState = triggerStateFor(side)
            val nextLocked = toggleLockedModifier(currentState.lockedModifiers, binding.key)
            setTriggerState(
                side,
                currentState.copy(
                    lockedModifiers = nextLocked,
                    activeHoldKey =
                        if (currentState.activeHoldKey == binding.key && binding.key in nextLocked) {
                            null
                        } else {
                            currentState.activeHoldKey
                        },
                ),
            )
        }
        pulse(LabHapticPattern.LockToggle)
    }

    fun onHoldUp(side: LabSide) {
        if (triggerStateFor(side).activeHoldKey == null) {
            return
        }
        releaseHold(side, pulseFeedback = true, clearLocks = true)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        LabSidePane(
            side = LabSide.Left,
            candidate = leftCandidate,
            activeHoldKey = leftTriggerState.activeHoldKey,
            lockedModifiers = leftTriggerState.lockedModifiers,
            triggerLockDelayMillis = triggerLockDelayMillis,
            tapSlopPx = touchSlop * 1.25f,
            segmentPx = segmentPx,
            onGesture = { onGesture(LabSide.Left, it) },
            onHoldDown = { binding -> onHoldDown(LabSide.Left, binding) },
            onToggleLock = { binding -> onToggleLock(LabSide.Left, binding) },
            onHoldUp = { onHoldUp(LabSide.Left) },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        )
        LabSidePane(
            side = LabSide.Right,
            candidate = rightCandidate,
            activeHoldKey = rightTriggerState.activeHoldKey,
            lockedModifiers = rightTriggerState.lockedModifiers,
            triggerLockDelayMillis = triggerLockDelayMillis,
            tapSlopPx = touchSlop * 1.25f,
            segmentPx = segmentPx,
            onGesture = { onGesture(LabSide.Right, it) },
            onHoldDown = { binding -> onHoldDown(LabSide.Right, binding) },
            onToggleLock = { binding -> onToggleLock(LabSide.Right, binding) },
            onHoldUp = { onHoldUp(LabSide.Right) },
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
    candidate: LabCell,
    activeHoldKey: String?,
    lockedModifiers: List<String>,
    triggerLockDelayMillis: Long,
    tapSlopPx: Float,
    segmentPx: Float,
    onGesture: (LabGesture) -> Unit,
    onHoldDown: (LabKeyBinding) -> Unit,
    onToggleLock: (LabKeyBinding) -> Unit,
    onHoldUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        LabHoldZone(
            side = side,
            candidateBinding = labKeyBinding(side, candidate),
            activeHoldKey = activeHoldKey,
            lockedModifiers = lockedModifiers,
            triggerLockDelayMillis = triggerLockDelayMillis,
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
            candidate = candidate,
            lockedModifiers = lockedModifiers,
            tapSlopPx = tapSlopPx,
            segmentPx = segmentPx,
            onGesture = onGesture,
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
    candidateBinding: LabKeyBinding,
    activeHoldKey: String?,
    lockedModifiers: List<String>,
    triggerLockDelayMillis: Long,
    onHoldDown: (LabKeyBinding) -> Unit,
    onToggleLock: (LabKeyBinding) -> Unit,
    onHoldUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activePointerId by remember { mutableStateOf<Int?>(null) }
    var activeHoldTriggered by remember { mutableStateOf(false) }
    var lockTriggered by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()
    var lockJob by remember { mutableStateOf<Job?>(null) }
    val active = activeHoldKey != null

    fun cancelLockJob() {
        lockJob?.cancel()
        lockJob = null
    }

    fun resetPointerState() {
        activePointerId = null
        activeHoldTriggered = false
        lockTriggered = false
        cancelLockJob()
    }

    Surface(
        color = if (active) colorScheme.primary else colorScheme.surface.copy(alpha = 0.94f),
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
                                cancelLockJob()
                                onHoldDown(candidateBinding)
                                activeHoldTriggered = true
                                lockJob =
                                    coroutineScope.launch {
                                        delay(triggerLockDelayMillis)
                                        if (activePointerId != null && !lockTriggered && isLockableModifier(candidateBinding.key)) {
                                            onToggleLock(candidateBinding)
                                            lockTriggered = true
                                            activeHoldTriggered = false
                                        }
                                    }
                            }
                            true
                        }

                        MotionEvent.ACTION_MOVE -> true

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL,
                        -> {
                            if (activePointerId != null) {
                                if (activeHoldTriggered && !lockTriggered) {
                                    onHoldUp()
                                }
                                resetPointerState()
                            }
                            true
                        }

                        MotionEvent.ACTION_POINTER_UP -> {
                            if (event.pointerIdAtAction() == activePointerId) {
                                if (activeHoldTriggered && !lockTriggered) {
                                    onHoldUp()
                                }
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
                        "HOLD ${activeHoldKey.orEmpty()}"
                    } else if (lockedModifiers.isNotEmpty()) {
                        "LOCK ${lockedModifiers.joinToString("+")}"
                    } else {
                        "HOLD ${candidateBinding.label}"
                    },
                color = if (active) colorScheme.onPrimary else colorScheme.onSurface,
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
    candidate: LabCell,
    lockedModifiers: List<String>,
    tapSlopPx: Float,
    segmentPx: Float,
    onGesture: (LabGesture) -> Unit,
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
                                resetPointerState()
                            }
                            true
                        }

                        MotionEvent.ACTION_CANCEL -> {
                            resetPointerState()
                            true
                        }

                        else -> true
                    }
                },
    ) {
        LabMatrixGrid(
            side = side,
            candidate = candidate,
            lockedModifiers = lockedModifiers,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun LabMatrixGrid(
    side: LabSide,
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
                        val binding = labKeyBinding(side, LabCell(row = row, column = column))
                        LabMatrixCell(
                            label = binding.label,
                            selected = candidate.row == row && candidate.column == column,
                            locked = binding.key in lockedModifiers,
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
            else -> colorScheme.background
        }
    val targetBorderColor =
        when {
            selected -> colorScheme.primary
            locked -> colorScheme.tertiary
            center -> colorScheme.secondary
            else -> colorScheme.outline.copy(alpha = 0.58f)
        }
    val targetTextColor =
        when {
            selected -> colorScheme.onPrimary
            locked -> colorScheme.onTertiaryContainer
            center -> colorScheme.onSecondaryContainer
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

private enum class LabHapticPattern(
    val constants: List<Int>,
) {
    OrthogonalMove(listOf(HapticFeedbackConstants.CLOCK_TICK)),
    Reset(listOf(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.CLOCK_TICK)),
    TriggerTap(listOf(HapticFeedbackConstants.KEYBOARD_TAP)),
    LockToggle(listOf(HapticFeedbackConstants.CLOCK_TICK, HapticFeedbackConstants.VIRTUAL_KEY)),
    HoldDown(listOf(HapticFeedbackConstants.LONG_PRESS)),
    HoldUp(listOf(HapticFeedbackConstants.VIRTUAL_KEY)),
}
