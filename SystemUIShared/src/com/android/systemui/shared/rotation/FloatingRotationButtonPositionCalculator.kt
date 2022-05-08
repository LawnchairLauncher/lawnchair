package com.android.systemui.shared.rotation

import android.view.Gravity
import android.view.Surface

/**
 * Calculates gravity and translation that is necessary to display
 * the button in the correct position based on the current state
 */
class FloatingRotationButtonPositionCalculator(
    private val defaultMargin: Int,
    private val taskbarMarginLeft: Int,
    private val taskbarMarginBottom: Int
) {

    fun calculatePosition(
        currentRotation: Int,
        taskbarVisible: Boolean,
        taskbarStashed: Boolean
    ): Position {

        val isTaskbarSide = currentRotation == Surface.ROTATION_0
            || currentRotation == Surface.ROTATION_90
        val useTaskbarMargin = isTaskbarSide && taskbarVisible && !taskbarStashed

        val gravity = resolveGravity(currentRotation)

        val marginLeft = if (useTaskbarMargin) taskbarMarginLeft else defaultMargin
        val marginBottom = if (useTaskbarMargin) taskbarMarginBottom else defaultMargin

        val translationX =
            if (gravity and Gravity.RIGHT == Gravity.RIGHT) {
                -marginLeft
            } else {
                marginLeft
            }
        val translationY =
            if (gravity and Gravity.BOTTOM == Gravity.BOTTOM) {
                -marginBottom
            } else {
                marginBottom
            }

        return Position(
            gravity = gravity,
            translationX = translationX,
            translationY = translationY
        )
    }

    data class Position(
        val gravity: Int,
        val translationX: Int,
        val translationY: Int
    )

    private fun resolveGravity(rotation: Int): Int =
        when (rotation) {
            Surface.ROTATION_0 -> Gravity.BOTTOM or Gravity.LEFT
            Surface.ROTATION_90 -> Gravity.BOTTOM or Gravity.RIGHT
            Surface.ROTATION_180 -> Gravity.TOP or Gravity.RIGHT
            Surface.ROTATION_270 -> Gravity.TOP or Gravity.LEFT
            else -> throw IllegalArgumentException("Invalid rotation $rotation")
        }
}
