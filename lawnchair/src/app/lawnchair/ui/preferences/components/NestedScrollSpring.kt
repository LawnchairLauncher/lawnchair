/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs

@Composable
fun NestedScrollSpring(content: @Composable () -> Unit) {
    val dampedScrollShift = remember { mutableStateOf(0f) }
    val nestedScrollConnection = remember { NestedScrollSpringConnection(dampedScrollShift) }
    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .graphicsLayer {
                translationY = dampedScrollShift.value
            },
    ) {
        content()
    }
}

private const val STIFFNESS = (SpringForce.STIFFNESS_MEDIUM + SpringForce.STIFFNESS_LOW) / 2
private const val DAMPING_RATIO = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
private const val VELOCITY_MULTIPLIER = 0.3f

class NestedScrollSpringConnection(
    dampedScrollShiftState: MutableState<Float>
) : NestedScrollConnection {

    private val springAnim = SpringAnimation(this, DAMPED_SCROLL, 0f).apply {
        spring = SpringForce(0f).apply {
            stiffness = STIFFNESS
            dampingRatio = DAMPING_RATIO
        }
    }
    private var dampedScrollShift by dampedScrollShiftState
    private var isFlinging = false

    private fun finishScrollWithVelocity(velocity: Float) {
        springAnim.setStartVelocity(velocity)
        springAnim.setStartValue(dampedScrollShift)
        springAnim.start()
    }

    private fun onAbsorb(velocity: Float) {
        finishScrollWithVelocity(velocity * VELOCITY_MULTIPLIER)
    }

    private fun onPull(deltaDistance: Float) {
        dampedScrollShift += deltaDistance
        springAnim.cancel()
    }

    private fun onRelease() {
        finishScrollWithVelocity(0f)
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val scrollOffset = available.y
        if (isFlinging || dampedScrollShift == 0f || dampedScrollShift > 0f == scrollOffset > 0f) {
            return Offset.Zero
        }
        val shiftAmount = abs(dampedScrollShift)
        val scrollAmount = abs(scrollOffset)
        return when {
            shiftAmount > scrollAmount -> {
                onPull(scrollOffset)
                Offset(0f, scrollOffset)
            }
            shiftAmount < scrollAmount -> {
                onPull(-dampedScrollShift)
                Offset(0f, dampedScrollShift)
            }
            else -> Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        if (isFlinging) return Offset.Zero
        onPull(available.y * (VELOCITY_MULTIPLIER / 3f))
        return available
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        onRelease()
        isFlinging = true
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        isFlinging = false
        onAbsorb(available.y)
        return Velocity(0f, available.y)
    }

    companion object {
        private val DAMPED_SCROLL = object : FloatPropertyCompat<NestedScrollSpringConnection>("value") {
            override fun getValue(obj: NestedScrollSpringConnection): Float {
                return obj.dampedScrollShift
            }

            override fun setValue(obj: NestedScrollSpringConnection, value: Float) {
                obj.dampedScrollShift = value
            }
        }
    }
}
