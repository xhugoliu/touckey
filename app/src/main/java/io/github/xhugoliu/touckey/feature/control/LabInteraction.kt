package io.github.xhugoliu.touckey.feature.control

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
