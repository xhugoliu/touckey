package io.github.xhugoliu.touckey.feature.control

import android.view.MotionEvent

enum class ModifierMode(
    val title: String,
    val detail: String,
) {
    Preset(
        title = "Preset",
        detail = "Tap a modifier to arm the next key stroke once.",
    ),
    Hold(
        title = "Hold",
        detail = "Touch down presses a key, lift releases it like a real keyboard.",
    ),
}

fun toggleArmedModifier(
    armedModifiers: List<String>,
    modifierName: String,
): List<String> =
    if (armedModifiers.contains(modifierName)) {
        armedModifiers - modifierName
    } else {
        armedModifiers + modifierName
    }

fun modifiersAfterKeyTap(
    mode: ModifierMode,
    armedModifiers: List<String>,
): List<String> =
    when (mode) {
        ModifierMode.Preset -> emptyList()
        ModifierMode.Hold -> armedModifiers
    }

data class HoldPointerTransition(
    val nextPointerId: Int?,
    val shouldPress: Boolean,
    val shouldRelease: Boolean,
)

fun reduceHoldPointerEvent(
    activePointerId: Int?,
    actionMasked: Int,
    actionPointerId: Int,
): HoldPointerTransition =
    when (actionMasked) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_POINTER_DOWN,
        -> {
            if (activePointerId == null) {
                HoldPointerTransition(
                    nextPointerId = actionPointerId,
                    shouldPress = true,
                    shouldRelease = false,
                )
            } else {
                HoldPointerTransition(
                    nextPointerId = activePointerId,
                    shouldPress = false,
                    shouldRelease = false,
                )
            }
        }

        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_POINTER_UP,
        -> {
            if (activePointerId == actionPointerId) {
                HoldPointerTransition(
                    nextPointerId = null,
                    shouldPress = false,
                    shouldRelease = true,
                )
            } else {
                HoldPointerTransition(
                    nextPointerId = activePointerId,
                    shouldPress = false,
                    shouldRelease = false,
                )
            }
        }

        MotionEvent.ACTION_CANCEL -> {
            if (activePointerId != null) {
                HoldPointerTransition(
                    nextPointerId = null,
                    shouldPress = false,
                    shouldRelease = true,
                )
            } else {
                HoldPointerTransition(
                    nextPointerId = null,
                    shouldPress = false,
                    shouldRelease = false,
                )
            }
        }

        else ->
            HoldPointerTransition(
                nextPointerId = activePointerId,
                shouldPress = false,
                shouldRelease = false,
            )
    }
