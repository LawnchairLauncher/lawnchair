package com.android.systemui.unfold.updates.hinge

import androidx.core.util.Consumer
import com.android.systemui.statusbar.policy.CallbackController

/**
 * Emits device hinge angle values (angle between two integral parts of the device).
 * The hinge angle could be from 0 to 360 degrees inclusive.
 * For foldable devices usually 0 corresponds to fully closed (folded) state and
 * 180 degrees corresponds to fully open (flat) state
 */
interface HingeAngleProvider : CallbackController<Consumer<Float>> {
    fun start()
    fun stop()
}

const val FULLY_OPEN_DEGREES = 180f
const val FULLY_CLOSED_DEGREES = 0f
