package io.github.xhugoliu.touckey.feature.control

import io.github.xhugoliu.touckey.hid.HidCapabilityCatalog
import io.github.xhugoliu.touckey.input.InputAction
import io.github.xhugoliu.touckey.input.MouseButton
import kotlin.math.abs
import kotlin.math.hypot

internal const val LAB_MATRIX_SIZE = 5
internal const val LAB_CENTER_INDEX = 2

private const val LAB_POINTER_CARDINAL_STEP = 14f
private const val LAB_POINTER_DIAGONAL_STEP = 10f
private const val LAB_SCROLL_STEP = 6
private val LOCKABLE_MODIFIERS: Set<String> = setOf("Ctrl", "Shift", "Alt", "Cmd")

internal enum class LabLayer(
    val displayName: String,
) {
    Default(displayName = "Base"),
    FnNumber(displayName = "Fn"),
    MouseNav(displayName = "Mouse"),
}

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

internal sealed interface LabBinding {
    val label: String
    val enabled: Boolean
        get() = true
    val activeKeyboardKeys: List<String>
        get() = emptyList()
    val activeMouseButton: MouseButton?
        get() = null
    val repeatAction: InputAction?
        get() = null
    val lockableModifierKey: String?
        get() = null
}

internal data object LabEmptyBinding : LabBinding {
    override val label: String = "none"
    override val enabled: Boolean = false
}

internal data class LabKeyBinding(
    val key: String,
    override val label: String = key.labDisplayLabel(),
) : LabBinding {
    init {
        require(HidCapabilityCatalog.supportsKeyboardInput(key)) {
            "Lab key binding $key is not supported by HID capability catalog."
        }
    }

    override val activeKeyboardKeys: List<String> = listOf(key)
    override val lockableModifierKey: String? = key.takeIf { candidate -> candidate in LOCKABLE_MODIFIERS }
}

internal data class LabMouseButtonBinding(
    val button: MouseButton,
    override val label: String,
) : LabBinding {
    override val activeMouseButton: MouseButton = button
}

internal data class LabPointerMoveBinding(
    val deltaX: Float,
    val deltaY: Float,
    override val label: String,
) : LabBinding {
    override val repeatAction: InputAction = InputAction.PointerMoveAction(deltaX = deltaX, deltaY = deltaY)
}

internal data class LabScrollBinding(
    val vertical: Int = 0,
    val horizontal: Int = 0,
    override val label: String,
) : LabBinding {
    override val repeatAction: InputAction = InputAction.ScrollAction(vertical = vertical, horizontal = horizontal)
}

internal data class LabTriggerState(
    val lockedModifiers: List<String> = emptyList(),
    val activeHoldBinding: LabBinding? = null,
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

internal fun interruptedLabHoldBinding(
    heldBinding: LabBinding?,
    move: LabCandidateMove,
): LabBinding? = if (heldBinding != null && move.moved) heldBinding else null

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

internal fun labBinding(
    side: LabSide,
    cell: LabCell,
    layer: LabLayer = LabLayer.Default,
): LabBinding = labBindingRows(layer, side)[cell.row][cell.column]

internal fun isLockableModifier(key: String): Boolean = key in LOCKABLE_MODIFIERS

internal fun isLockableModifier(binding: LabBinding): Boolean = binding.lockableModifierKey != null

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

internal fun LabTriggerState.activeKeyboardKeys(): List<String> =
    (lockedModifiers + (activeHoldBinding?.activeKeyboardKeys ?: emptyList())).distinct()

internal fun LabTriggerState.activeMouseButtons(): List<MouseButton> =
    listOfNotNull(activeHoldBinding?.activeMouseButton).distinct()

internal fun aggregateLabActiveKeys(states: List<LabTriggerState>): List<String> =
    states.flatMap { state -> state.activeKeyboardKeys() }.distinct()

internal fun aggregateLabActiveMouseButtons(states: List<LabTriggerState>): List<MouseButton> =
    states.flatMap { state -> state.activeMouseButtons() }.distinct()

internal fun labStateTransitionActions(
    previousStates: List<LabTriggerState>,
    nextStates: List<LabTriggerState>,
): List<InputAction> {
    val previousKeys = aggregateLabActiveKeys(previousStates)
    val nextKeys = aggregateLabActiveKeys(nextStates)
    val previousButtons = aggregateLabActiveMouseButtons(previousStates)
    val nextButtons = aggregateLabActiveMouseButtons(nextStates)

    val keyPresses =
        nextKeys
            .filterNot { key -> key in previousKeys }
            .map { key -> InputAction.KeyPressAction(key) }
    val buttonPresses =
        nextButtons
            .filterNot { button -> button in previousButtons }
            .map { button -> InputAction.MouseButtonPressAction(button) }
    val buttonReleases =
        previousButtons
            .asReversed()
            .filterNot { button -> button in nextButtons }
            .map { button -> InputAction.MouseButtonReleaseAction(button) }
    val keyReleases =
        previousKeys
            .asReversed()
            .filterNot { key -> key in nextKeys }
            .map { key -> InputAction.KeyReleaseAction(key) }
    return keyPresses + buttonPresses + buttonReleases + keyReleases
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

private fun labBindingRows(
    layer: LabLayer,
    side: LabSide,
): List<List<LabBinding>> =
    LAB_BINDING_ROWS.getValue(layer).getValue(side)

private val LAB_BINDING_ROWS: Map<LabLayer, Map<LabSide, List<List<LabBinding>>>> =
    mapOf(
        LabLayer.Default to
            mapOf(
                LabSide.Left to
                    sideRows(
                        keyRow("Alt", "Esc", "Space", "Tab", "Shift"),
                        keyRow("Q", "W", "E", "R", "T"),
                        keyRow("A", "S", "D", "F", "G"),
                        keyRow("Z", "X", "C", "V", "B"),
                        keyRow("Cmd", "`", "-", "=", "Ctrl"),
                    ),
                LabSide.Right to
                    sideRows(
                        keyRow("Shift", "Backspace", "Enter", "Delete", "Alt"),
                        keyRow("Y", "U", "I", "O", "P"),
                        keyRow("H", "J", "K", "L", ";"),
                        keyRow("N", "M", ",", ".", "/"),
                        keyRow("Ctrl", "[", "]", "\\", "Cmd"),
                    ),
            ),
        LabLayer.FnNumber to
            mapOf(
                LabSide.Left to
                    sideRows(
                        keyRow("Alt", "F1", "F2", "F3", "Shift"),
                        keyRow("F4", "F5", "F6", "F7", "F8"),
                        keyRow("F9", "F10", "F11", "F12", "F13"),
                        keyRow("F14", "F15", "F16", "F17", "F18"),
                        keyRow("Cmd", "F19", "F20", "F21", "Ctrl"),
                    ),
                LabSide.Right to
                    sideRows(
                        row(key("Shift"), none(), none(), none(), key("Alt")),
                        row(key("KeypadPlus", "+"), key("7"), key("8"), key("9"), key("KeypadMultiply", "*")),
                        row(none(), key("4"), key("5"), key("6"), none()),
                        row(key("KeypadMinus", "-"), key("1"), key("2"), key("3"), key("KeypadDivide", "/")),
                        row(key("Ctrl"), key("0"), key("KeypadPeriod", "."), key("'"), key("Cmd")),
                    ),
            ),
        LabLayer.MouseNav to
            mapOf(
                LabSide.Left to
                    sideRows(
                        row(key("Alt"), none(), scroll(vertical = LAB_SCROLL_STEP, label = "Scr U"), none(), key("Shift")),
                        row(none(), pointer(-LAB_POINTER_DIAGONAL_STEP, -LAB_POINTER_DIAGONAL_STEP, "Ptr UL"), pointer(0f, -LAB_POINTER_CARDINAL_STEP, "Ptr U"), pointer(LAB_POINTER_DIAGONAL_STEP, -LAB_POINTER_DIAGONAL_STEP, "Ptr UR"), none()),
                        row(scroll(horizontal = -LAB_SCROLL_STEP, label = "Scr L"), pointer(-LAB_POINTER_CARDINAL_STEP, 0f, "Ptr L"), none(), pointer(LAB_POINTER_CARDINAL_STEP, 0f, "Ptr R"), scroll(horizontal = LAB_SCROLL_STEP, label = "Scr R")),
                        row(none(), pointer(-LAB_POINTER_DIAGONAL_STEP, LAB_POINTER_DIAGONAL_STEP, "Ptr DL"), pointer(0f, LAB_POINTER_CARDINAL_STEP, "Ptr D"), pointer(LAB_POINTER_DIAGONAL_STEP, LAB_POINTER_DIAGONAL_STEP, "Ptr DR"), none()),
                        row(key("Cmd"), none(), scroll(vertical = -LAB_SCROLL_STEP, label = "Scr D"), none(), key("Ctrl")),
                    ),
                LabSide.Right to
                    sideRows(
                        row(key("Shift"), none(), key("PageUp", "PgUp"), none(), key("Alt")),
                        row(none(), mouse(MouseButton.Right, "M2"), key("Up"), mouse(MouseButton.Middle, "M3"), none()),
                        row(key("Home"), key("Left"), mouse(MouseButton.Left, "M1"), key("Right"), key("End")),
                        row(none(), mouse(MouseButton.Back, "M4"), key("Down"), mouse(MouseButton.Forward, "M5"), none()),
                        row(key("Ctrl"), none(), key("PageDown", "PgDn"), none(), key("Cmd")),
                    ),
            ),
    )

private fun sideRows(vararg rows: List<LabBinding>): List<List<LabBinding>> {
    require(rows.size == LAB_MATRIX_SIZE) { "Lab layer must have $LAB_MATRIX_SIZE rows." }
    rows.forEach { row ->
        require(row.size == LAB_MATRIX_SIZE) { "Lab layer rows must have $LAB_MATRIX_SIZE columns." }
    }
    return rows.toList()
}

private fun row(vararg bindings: LabBinding): List<LabBinding> = bindings.toList()

private fun keyRow(vararg keys: String): List<LabBinding> = keys.map { key -> key(key) }

private fun key(
    key: String,
    label: String = key.labDisplayLabel(),
): LabKeyBinding = LabKeyBinding(key = key, label = label)

private fun none(): LabBinding = LabEmptyBinding

private fun mouse(
    button: MouseButton,
    label: String,
): LabBinding = LabMouseButtonBinding(button = button, label = label)

private fun pointer(
    deltaX: Float,
    deltaY: Float,
    label: String,
): LabBinding = LabPointerMoveBinding(deltaX = deltaX, deltaY = deltaY, label = label)

private fun scroll(
    vertical: Int = 0,
    horizontal: Int = 0,
    label: String,
): LabBinding = LabScrollBinding(vertical = vertical, horizontal = horizontal, label = label)

private fun String.labDisplayLabel(): String =
    when (this) {
        "Cmd" -> "GUI"
        "Backspace" -> "Back"
        "Delete" -> "Del"
        "Control" -> "Ctrl"
        else -> this
    }
