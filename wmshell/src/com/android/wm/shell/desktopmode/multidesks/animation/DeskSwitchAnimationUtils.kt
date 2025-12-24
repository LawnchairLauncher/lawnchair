/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.wm.shell.desktopmode.multidesks.animation

import android.graphics.Rect
import android.os.SystemProperties
import android.view.SurfaceControl
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringForce
import com.android.internal.annotations.VisibleForTesting
import com.android.wm.shell.shared.animation.PhysicsAnimator

object DeskSwitchAnimationUtils {
    /** The percentage of the screen that tasks will slide before/after fading. */
    const val LATERAL_MOTION_SCREEN_PCT = 0.25f

    private val LATERAL_MOVEMENT_SPRING_STIFFNESS =
        propertyValue("lateral_stiffness", scale = 1000f, default = 800f)
    private val LATERAL_MOVEMENT_SPRING_DAMPING_RATIO =
        propertyValue("lateral_damping_ratio", default = SpringForce.DAMPING_RATIO_NO_BOUNCY)
    /** The spring to use for lateral movement of desk windows. */
    val LATERAL_MOVEMENT_SPRING_CONFIG =
        PhysicsAnimator.SpringConfig(
            stiffness = LATERAL_MOVEMENT_SPRING_STIFFNESS,
            dampingRatio = LATERAL_MOVEMENT_SPRING_DAMPING_RATIO,
        )

    private val FADE_OUT_SPRING_STIFFNESS =
        propertyValue("fade_out_stiffness", scale = 1000f, default = 3800f)
    private val FADE_OUT_SPRING_DAMPING_RATIO =
        propertyValue("fade_out_damping_ratio", default = SpringForce.DAMPING_RATIO_NO_BOUNCY)
    /** The spring to use for fading out desk windows. */
    val FADE_OUT_SPRING_CONFIG =
        PhysicsAnimator.SpringConfig(
            stiffness = FADE_OUT_SPRING_STIFFNESS,
            dampingRatio = FADE_OUT_SPRING_DAMPING_RATIO,
        )
    /** The start point of the fade out animation relative to the start of the lateral animation. */
    val FADE_OUT_START_FRACTION = propertyValue("fade_out_start_fraction", default = 0.1f)
    /** The point at which the fade out animation "jumps" to the end value. */
    val FADE_OUT_VISIBILITY_THRESHOLD = propertyValue("fade_out_start_fraction", default = 0.1f)

    private val FADE_IN_SPRING_STIFFNESS =
        propertyValue("fade_in_stiffness", scale = 1000f, default = 800f)
    private val FADE_IN_SPRING_DAMPING_RATIO =
        propertyValue("fade_in_damping_ratio", default = SpringForce.DAMPING_RATIO_NO_BOUNCY)
    /** The spring to use for fading in desk windows. */
    val FADE_IN_SPRING_CONFIG =
        PhysicsAnimator.SpringConfig(
            stiffness = FADE_IN_SPRING_STIFFNESS,
            dampingRatio = FADE_IN_SPRING_DAMPING_RATIO,
        )
    /** The start point of the fade in animation relative to the start of the lateral animation. */
    val FADE_IN_START_FRACTION = propertyValue("fade_in_start_fraction", default = 0.4f)
    /** The point at which the fade in animation "jumps" to the end value. */
    val FADE_IN_VISIBILITY_THRESHOLD = propertyValue("fade_in_start_fraction", default = 0.1f)

    /** The animation duration of the wallpaper behind the desks. */
    val WALLPAPER_TRANSLATION_DURATION =
        propertyValue("wallpaper_translation_duration", default = 250L)

    /** Whether debug logs are enabled. */
    val DEBUG_ANIMATION =
        SystemProperties.getBoolean("$SYSTEM_PROPERTIES_GROUP.debug_animation_steps", false)

    /** Desk switch transition system properties group. */
    @VisibleForTesting
    const val SYSTEM_PROPERTIES_GROUP = "persist.wm.debug.desktop_transitions.desk_switch"

    /**
     * Desk switch transition system property value with [name].
     *
     * @param scale an optional scale to apply to the value read from the system property.
     * @param default a default value to return if the system property isn't set.
     */
    @VisibleForTesting
    fun propertyValue(name: String, scale: Float = 1f, default: Float = 0f): Float =
        SystemProperties.getInt(
            /* key= */ "$SYSTEM_PROPERTIES_GROUP.$name",
            /* def= */ (default * scale).toInt(),
        ) / scale

    /**
     * Desk switch transition system property value with [name].
     *
     * @param scale an optional scale to apply to the value read from the system property.
     * @param default a default value to return if the system property isn't set.
     */
    @VisibleForTesting
    fun propertyValue(name: String, scale: Long = 1L, default: Long = 0L): Long =
        SystemProperties.getLong(
            /* key= */ "$SYSTEM_PROPERTIES_GROUP.$name",
            /* def= */ default * scale,
        ) / scale

    /** Describes a desk changing bounds. */
    class DeskBoundsChange(val fromDeskBounds: Rect, val toDeskBounds: Rect)

    /** A property to animate the |from| bounds of a [DeskBoundsChange]. */
    val DESK_BOUNDS_FROM_X =
        object : FloatPropertyCompat<DeskBoundsChange>("DeskBoundsChangeFromX") {
            override fun setValue(change: DeskBoundsChange, value: Float) {
                change.fromDeskBounds.offsetTo(value.toInt(), change.fromDeskBounds.top)
            }

            override fun getValue(change: DeskBoundsChange?): Float {
                return change?.fromDeskBounds?.left?.toFloat() ?: -Float.MAX_VALUE
            }
        }

    /** A property to animate the |to| bounds of a [DeskBoundsChange]. */
    val DESK_BOUNDS_TO_X =
        object : FloatPropertyCompat<DeskBoundsChange>("DeskBoundsChangeToX") {
            override fun setValue(change: DeskBoundsChange, value: Float) {
                change.toDeskBounds.offsetTo(value.toInt(), change.toDeskBounds.top)
            }

            override fun getValue(change: DeskBoundsChange?): Float {
                return change?.toDeskBounds?.left?.toFloat() ?: -Float.MAX_VALUE
            }
        }

    /** Describes a desk changing opacity. */
    class DeskOpacityChange(val leashes: List<SurfaceControl>, var alpha: Float)

    /** A property to animate the |alpha| of a [DeskOpacityChange]. */
    class DeskAlphaProperty(private val tx: SurfaceControl.Transaction) :
        FloatPropertyCompat<DeskOpacityChange>("DeskAlpha") {

        override fun getValue(aw: DeskOpacityChange?): Float {
            return aw?.alpha ?: 0f
        }

        override fun setValue(aw: DeskOpacityChange, value: Float) {
            aw.alpha = value
            aw.leashes.forEach { l -> tx.setAlpha(l, value) }
            tx.apply()
        }
    }
}
