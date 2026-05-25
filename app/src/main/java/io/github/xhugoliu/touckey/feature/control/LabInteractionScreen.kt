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

@Composable
internal fun LabInteractionPage(
    onInputAction: (InputAction, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val touchSlop = remember { ViewConfiguration.get(context).scaledTouchSlop.toFloat() }
    val tapSlopPx = remember(touchSlop) { touchSlop * 1.25f }
    val segmentPx = remember(touchSlop) { touchSlop * 1.9f }

    var leftCandidate by remember { mutableStateOf(LabCell()) }
    var rightCandidate by remember { mutableStateOf(LabCell()) }
    var leftHeldId by remember { mutableStateOf<String?>(null) }
    var rightHeldId by remember { mutableStateOf<String?>(null) }

    fun pulse(pattern: LabHapticPattern) {
        pattern.constants.forEach { constant ->
            view.performHapticFeedback(constant)
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

    fun heldIdFor(side: LabSide): String? =
        when (side) {
            LabSide.Left -> leftHeldId
            LabSide.Right -> rightHeldId
        }

    fun setHeldId(
        side: LabSide,
        id: String?,
    ) {
        when (side) {
            LabSide.Left -> leftHeldId = id
            LabSide.Right -> rightHeldId = id
        }
    }

    fun onGesture(
        side: LabSide,
        gesture: LabGesture,
    ) {
        val current = candidateFor(side)
        val move = applyLabGesture(current, gesture)
        val interruptedKey = interruptedLabHoldKey(heldIdFor(side), move)

        when {
            gesture == LabGesture.Tap -> {
                setCandidate(side, move.nextCell)
                pulse(LabHapticPattern.Reset)
            }

            move.blocked -> {
            }

            move.moved -> {
                interruptedKey?.let { key ->
                    setHeldId(side, null)
                    onInputAction(InputAction.KeyReleaseAction(key), false)
                    pulse(LabHapticPattern.HoldUp)
                }
                setCandidate(side, move.nextCell)
                pulse(LabHapticPattern.OrthogonalMove)
            }
        }
    }

    fun onHoldDown(side: LabSide) {
        if (heldIdFor(side) != null) {
            return
        }
        val binding = labKeyBinding(side, candidateFor(side))
        setHeldId(side, binding.key)
        onInputAction(InputAction.KeyPressAction(binding.key), false)
        pulse(LabHapticPattern.HoldDown)
    }

    fun onHoldUp(side: LabSide) {
        val key = heldIdFor(side) ?: return
        setHeldId(side, null)
        onInputAction(InputAction.KeyReleaseAction(key), false)
        pulse(LabHapticPattern.HoldUp)
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        LabSidePane(
            side = LabSide.Left,
            candidate = leftCandidate,
            heldId = leftHeldId,
            tapSlopPx = tapSlopPx,
            segmentPx = segmentPx,
            onGesture = { onGesture(LabSide.Left, it) },
            onHoldDown = { onHoldDown(LabSide.Left) },
            onHoldUp = { onHoldUp(LabSide.Left) },
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        )
        LabSidePane(
            side = LabSide.Right,
            candidate = rightCandidate,
            heldId = rightHeldId,
            tapSlopPx = tapSlopPx,
            segmentPx = segmentPx,
            onGesture = { onGesture(LabSide.Right, it) },
            onHoldDown = { onHoldDown(LabSide.Right) },
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
    heldId: String?,
    tapSlopPx: Float,
    segmentPx: Float,
    onGesture: (LabGesture) -> Unit,
    onHoldDown: () -> Unit,
    onHoldUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        LabHoldZone(
            side = side,
            candidateLabel = labKeyBinding(side, candidate).label,
            heldId = heldId,
            onHoldDown = onHoldDown,
            onHoldUp = onHoldUp,
            modifier =
                Modifier
                    .weight(2f)
                    .fillMaxWidth(),
        )
        LabMatrixZone(
            side = side,
            candidate = candidate,
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
    candidateLabel: String,
    heldId: String?,
    onHoldDown: () -> Unit,
    onHoldUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activePointerId by remember { mutableStateOf<Int?>(null) }
    val colorScheme = MaterialTheme.colorScheme
    val active = heldId != null

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
                                onHoldDown()
                            }
                            true
                        }

                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL,
                        -> {
                            if (activePointerId != null) {
                                activePointerId = null
                                onHoldUp()
                            }
                            true
                        }

                        MotionEvent.ACTION_POINTER_UP -> {
                            if (event.pointerIdAtAction() == activePointerId) {
                                activePointerId = null
                                onHoldUp()
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
                text = if (active) "HOLD ${heldId.orEmpty()}" else "HOLD $candidateLabel",
                color = if (active) colorScheme.onPrimary else colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${side.displayName} trigger zone",
                color =
                    if (active) {
                        colorScheme.onPrimary.copy(alpha = 0.72f)
                    } else {
                        colorScheme.onSurfaceVariant
                    },
                style = MaterialTheme.typography.labelMedium,
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
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun LabMatrixGrid(
    side: LabSide,
    candidate: LabCell,
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
    center: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val targetBackgroundColor =
        when {
            selected -> colorScheme.primary
            center -> colorScheme.secondaryContainer
            else -> colorScheme.background
        }
    val targetBorderColor =
        when {
            selected -> colorScheme.primary
            center -> colorScheme.secondary
            else -> colorScheme.outline.copy(alpha = 0.58f)
        }
    val targetTextColor =
        when {
            selected -> colorScheme.onPrimary
            center -> colorScheme.onSecondaryContainer
            else -> colorScheme.onSurfaceVariant
        }
    val backgroundColor by animateColorAsState(targetValue = targetBackgroundColor, label = "Lab cell background")
    val borderColor by animateColorAsState(targetValue = targetBorderColor, label = "Lab cell border")
    val textColor by animateColorAsState(targetValue = targetTextColor, label = "Lab cell text")
    val borderWidth by animateDpAsState(
        targetValue = if (selected || center) 2.dp else 1.dp,
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
    HoldDown(listOf(HapticFeedbackConstants.LONG_PRESS)),
    HoldUp(listOf(HapticFeedbackConstants.VIRTUAL_KEY)),
}
