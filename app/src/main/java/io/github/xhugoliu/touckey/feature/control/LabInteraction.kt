package io.github.xhugoliu.touckey.feature.control

import io.github.xhugoliu.touckey.hid.HidCapabilityCatalog
import io.github.xhugoliu.touckey.input.InputAction
import kotlin.math.abs
import kotlin.math.hypot

internal const val LAB_MATRIX_SIZE = 5
internal const val LAB_CENTER_INDEX = 2

internal enum class LabSide(
    val prefix: String,
    val displayName: String,
) {
    Left(prefix = "L", displayName = "Left"),
    Right(prefix = "R", displayName = "Right"),
}

internal data class LabCell(
    val row: Int = LAB_CENTER_INDEX,
    val column: Int = LAB_CENTER_INDEX,
) {
    init {
        require(row in 0 until LAB_MATRIX_SIZE) { "Row $row is outside the lab matrix." }
        require(column in 0 until LAB_MATRIX_SIZE) { "Column $column is outside the lab matrix." }
    }

    fun virtualId(side: LabSide): String = "${side.prefix}${row + 1}${column + 1}"
}

internal data class LabKeyBinding(
    val key: String,
    val label: String,
)

internal data class LabTriggerState(
    val lockedModifiers: List<String> = emptyList(),
    val activeHoldKey: String? = null,
)

internal enum class LabGesture(
    val label: String,
    val rowDelta: Int,
    val columnDelta: Int,
) {
    Tap(label = "tap", rowDelta = 0, columnDelta = 0),
    Up(label = "up", rowDelta = -1, columnDelta = 0),
    Down(label = "down", rowDelta = 1, columnDelta = 0),
    Left(label = "left", rowDelta = 0, columnDelta = -1),
    Right(label = "right", rowDelta = 0, columnDelta = 1),
}

internal data class LabCandidateMove(
    val nextCell: LabCell,
    val moved: Boolean,
    val blocked: Boolean,
)

internal data class LabGestureSegments(
    val gestures: List<LabGesture>,
    val consumedX: Float,
    val consumedY: Float,
)

internal fun interruptedLabHoldKey(
    heldKey: String?,
    move: LabCandidateMove,
): String? = if (heldKey != null && move.moved) heldKey else null

internal fun interruptedLockedModifiers(
    lockedModifiers: List<String>,
    move: LabCandidateMove,
): List<String> = lockedModifiers

internal fun detectLabGesture(
    deltaX: Float,
    deltaY: Float,
    minSwipePx: Float,
): LabGesture {
    if (hypot(deltaX, deltaY) < minSwipePx) {
        return LabGesture.Tap
    }

    val absX = abs(deltaX)
    val absY = abs(deltaY)
    return if (absX >= absY) {
        if (deltaX > 0f) LabGesture.Right else LabGesture.Left
    } else {
        if (deltaY > 0f) LabGesture.Down else LabGesture.Up
    }
}

internal fun applyLabGesture(
    cell: LabCell,
    gesture: LabGesture,
): LabCandidateMove {
    if (gesture == LabGesture.Tap) {
        val center = LabCell()
        return LabCandidateMove(
            nextCell = center,
            moved = cell != center,
            blocked = false,
        )
    }

    val nextRow = cell.row + gesture.rowDelta
    val nextColumn = cell.column + gesture.columnDelta
    if (nextRow !in 0 until LAB_MATRIX_SIZE || nextColumn !in 0 until LAB_MATRIX_SIZE) {
        return LabCandidateMove(
            nextCell = cell,
            moved = false,
            blocked = true,
        )
    }

    return LabCandidateMove(
        nextCell = LabCell(row = nextRow, column = nextColumn),
        moved = true,
        blocked = false,
    )
}

internal fun segmentLabDrag(
    deltaX: Float,
    deltaY: Float,
    segmentPx: Float,
): LabGestureSegments {
    if (abs(deltaX) < segmentPx && abs(deltaY) < segmentPx) {
        return LabGestureSegments(
            gestures = emptyList(),
            consumedX = 0f,
            consumedY = 0f,
        )
    }

    val gestures = mutableListOf<LabGesture>()
    var remainingX = deltaX
    var remainingY = deltaY
    var consumedX = 0f
    var consumedY = 0f

    while (abs(remainingX) >= segmentPx || abs(remainingY) >= segmentPx) {
        if (abs(remainingX) >= abs(remainingY)) {
            val step = if (remainingX > 0f) segmentPx else -segmentPx
            gestures += if (step > 0f) LabGesture.Right else LabGesture.Left
            remainingX -= step
            consumedX += step
        } else {
            val step = if (remainingY > 0f) segmentPx else -segmentPx
            gestures += if (step > 0f) LabGesture.Down else LabGesture.Up
            remainingY -= step
            consumedY += step
        }
    }

    return LabGestureSegments(
        gestures = gestures,
        consumedX = consumedX,
        consumedY = consumedY,
    )
}

internal fun labKeyBinding(
    side: LabSide,
    cell: LabCell,
): LabKeyBinding = labKeyRows(side)[cell.row][cell.column]

internal fun isLockableModifier(key: String): Boolean = key in LOCKABLE_MODIFIERS

internal fun toggleLockedModifier(
    lockedModifiers: List<String>,
    key: String,
): List<String> =
    if (key in lockedModifiers) {
        lockedModifiers - key
    } else {
        lockedModifiers + key
    }

internal fun labHoldPressActions(
    activeKey: String,
    lockedModifiers: List<String>,
): List<InputAction.KeyPressAction> =
    (lockedModifiers + activeKey)
        .distinct()
        .map { key -> InputAction.KeyPressAction(key) }

internal fun labHoldReleaseActions(
    activeKey: String,
    lockedModifiers: List<String>,
): List<InputAction.KeyReleaseAction> =
    (lockedModifiers + activeKey)
        .distinct()
        .asReversed()
        .map { key -> InputAction.KeyReleaseAction(key) }

internal fun labTapActions(
    activeKey: String,
    lockedModifiers: List<String>,
): List<InputAction> =
    labHoldPressActions(activeKey, lockedModifiers) + labHoldReleaseActions(activeKey, lockedModifiers)

internal fun LabTriggerState.activeKeys(): List<String> =
    (lockedModifiers + listOfNotNull(activeHoldKey)).distinct()

internal fun aggregateLabActiveKeys(states: List<LabTriggerState>): List<String> =
    states.flatMap { state -> state.activeKeys() }.distinct()

internal fun labStateTransitionActions(
    previousStates: List<LabTriggerState>,
    nextStates: List<LabTriggerState>,
): List<InputAction> {
    val previousKeys = aggregateLabActiveKeys(previousStates)
    val nextKeys = aggregateLabActiveKeys(nextStates)
    val presses =
        nextKeys
            .filterNot { key -> key in previousKeys }
            .map { key -> InputAction.KeyPressAction(key) }
    val releases =
        previousKeys
            .asReversed()
            .filterNot { key -> key in nextKeys }
            .map { key -> InputAction.KeyReleaseAction(key) }
    return presses + releases
}

internal fun labTapActionsAgainstState(
    activeKey: String,
    lockedModifiers: List<String>,
    currentlyPressedKeys: List<String>,
): List<InputAction> {
    val tapKeys = (lockedModifiers + activeKey).distinct()
    val freshKeys = tapKeys.filterNot { key -> key in currentlyPressedKeys }
    val presses = freshKeys.map { key -> InputAction.KeyPressAction(key) }
    val releases = freshKeys.asReversed().map { key -> InputAction.KeyReleaseAction(key) }
    return presses + releases
}

private fun labKeyRows(side: LabSide): List<List<LabKeyBinding>> =
    when (side) {
        LabSide.Left -> LEFT_LAB_KEY_ROWS
        LabSide.Right -> RIGHT_LAB_KEY_ROWS
    }

private val LEFT_LAB_KEY_ROWS: List<List<LabKeyBinding>> =
    listOf(
        bindings("Alt", "Esc", "Space", "Tab", "Shift"),
        bindings("Q", "W", "E", "R", "T"),
        bindings("A", "S", "D", "F", "G"),
        bindings("Z", "X", "C", "V", "B"),
        bindings("Cmd", "`", "-", "=", "Ctrl"),
    )

private val RIGHT_LAB_KEY_ROWS: List<List<LabKeyBinding>> =
    listOf(
        bindings("Shift", "Backspace", "Enter", "Delete", "Alt"),
        bindings("Y", "U", "I", "O", "P"),
        bindings("H", "J", "K", "L", ";"),
        bindings("N", "M", ",", ".", "/"),
        bindings("Ctrl", "[", "]", "\\", "Cmd"),
    )

private fun bindings(vararg keys: String): List<LabKeyBinding> =
    keys.map { key ->
        require(HidCapabilityCatalog.supportsKeyboardInput(key)) {
            "Lab key binding $key is not supported by HID capability catalog."
        }
        LabKeyBinding(key = key, label = key.labDisplayLabel())
    }

private fun String.labDisplayLabel(): String =
    when (this) {
        "Cmd" -> "GUI"
        "Backspace" -> "Back"
        "Delete" -> "Del"
        "Control" -> "Ctrl"
        else -> this
    }

private val LOCKABLE_MODIFIERS: Set<String> = setOf("Ctrl", "Shift", "Alt", "Cmd")
