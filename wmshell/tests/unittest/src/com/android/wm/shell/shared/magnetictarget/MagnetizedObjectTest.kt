/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.wm.shell.shared.magnetictarget

import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.shared.animation.PhysicsAnimatorTestUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.Mockito
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

@TestableLooper.RunWithLooper
@RunWith(AndroidTestingRunner::class)
@SmallTest
class MagnetizedObjectTest : ShellTestCase() {
    /** Incrementing value for fake MotionEvent timestamps. */
    private var time = 0L

    /** Value to add to each new MotionEvent's timestamp. */
    private var timeStep = 100

    private val underlyingObject = this

    private lateinit var targetView: View

    private val targetSize = 200
    private val targetCenterX = 500
    private val targetCenterY = 900
    private val magneticFieldRadius = 200

    private var objectX = 0f
    private var objectY = 0f
    private val objectSize = 50f

    private lateinit var magneticTarget: MagnetizedObject.MagneticTarget
    private lateinit var magnetizedObject: MagnetizedObject<*>
    private lateinit var magnetListener: MagnetizedObject.MagnetListener

    private val xProperty = object : FloatPropertyCompat<MagnetizedObjectTest>("") {
        override fun setValue(target: MagnetizedObjectTest?, value: Float) {
            objectX = value
        }
        override fun getValue(target: MagnetizedObjectTest?): Float {
            return objectX
        }
    }

    private val yProperty = object : FloatPropertyCompat<MagnetizedObjectTest>("") {
        override fun setValue(target: MagnetizedObjectTest?, value: Float) {
            objectY = value
        }

        override fun getValue(target: MagnetizedObjectTest?): Float {
            return objectY
        }
    }

    @Before
    fun setup() {
        PhysicsAnimatorTestUtils.prepareForTest()

        // Mock the view since a real view's getLocationOnScreen() won't work unless it's attached
        // to a real window (it'll always return x = 0, y = 0).
        targetView = mock(View::class.java)
        `when`(targetView.context).thenReturn(context)

        // The mock target view will pretend that it's 200x200, and at (400, 800). This means it's
        // occupying the bounds (400, 800, 600, 1000) and it has a center of (500, 900).
        `when`(targetView.width).thenReturn(targetSize) // width = 200
        `when`(targetView.height).thenReturn(targetSize) // height = 200
        doAnswer { invocation ->
            (invocation.arguments[0] as IntArray).also { location ->
                // Return the top left of the target.
                location[0] = targetCenterX - targetSize / 2 // x = 400
                location[1] = targetCenterY - targetSize / 2 // y = 800
            }
        }.`when`(targetView).getLocationOnScreen(ArgumentMatchers.any())
        doAnswer { invocation ->
            (invocation.arguments[0] as Runnable).run()
            true
        }.`when`(targetView).post(ArgumentMatchers.any())
        `when`(targetView.context).thenReturn(context)

        magneticTarget = MagnetizedObject.MagneticTarget(targetView, magneticFieldRadius)

        magnetListener = mock(MagnetizedObject.MagnetListener::class.java)
        magnetizedObject = object : MagnetizedObject<MagnetizedObjectTest>(
                context, underlyingObject, xProperty, yProperty) {
            override fun getWidth(underlyingObject: MagnetizedObjectTest): Float {
                return objectSize
            }

            override fun getHeight(underlyingObject: MagnetizedObjectTest): Float {
                return objectSize
            }

            override fun getLocationOnScreen(
                underlyingObject: MagnetizedObjectTest,
                loc: IntArray
            ) {
                loc[0] = objectX.toInt()
                loc[1] = objectY.toInt() }
        }

        magnetizedObject.magnetListener = magnetListener
        magnetizedObject.addTarget(magneticTarget)

        timeStep = 100
    }

    @Test
    fun testMotionEventConsumption() {
        // Start at (0, 0). No magnetic field here.
        assertFalse(magnetizedObject.maybeConsumeMotionEvent(getMotionEvent(
                x = 0, y = 0, action = MotionEvent.ACTION_DOWN)))

        // Move to (400, 400), which is solidly outside the magnetic field.
        assertFalse(magnetizedObject.maybeConsumeMotionEvent(getMotionEvent(
                x = 200, y = 200)))

        // Move to (305, 705). This would be in the magnetic field radius if magnetic fields were
        // square. It's not, because they're not.
        assertFalse(magnetizedObject.maybeConsumeMotionEvent(getMotionEvent(
                x = targetCenterX - magneticFieldRadius + 5,
                y = targetCenterY - magneticFieldRadius + 5)))

        // Move to (400, 800). That's solidly in the radius so the magnetic target should begin
        // consuming events.
        assertTrue(magnetizedObject.maybeConsumeMotionEvent(getMotionEvent(
                x = targetCenterX - 100,
                y = targetCenterY - 100)))

        // Release at (400, 800). Since we're in the magnetic target, it should return true and
        // consume the ACTION_UP.
        assertTrue(magnetizedObject.maybeConsumeMotionEvent(getMotionEvent(
                x = 400, y = 800, action = MotionEvent.ACTION_UP)))

        // ACTION_DOWN outside the field.
        assertFalse(magnetizedObject.maybeConsumeMotionEvent(getMotionEvent(
                x = 200, y = 200, action = MotionEvent.ACTION_DOWN)))

        // Move to the center. We absolutely should consume events there.
        assertTrue(magnetizedObject.maybeConsumeMotionEvent(getMotionEvent(
                x = targetCenterX,
                y = targetCenterY)))

        // Drag out to (0, 0) and we should be returning false again.
        assertFalse(magnetizedObject.maybeConsumeMotionEvent(getMotionEvent(
                x = 0, y = 0)))

        // The ACTION_UP event shouldn't be consumed either since it's outside the field.
        assertFalse(magnetizedObject.maybeConsumeMotionEvent(getMotionEvent(
                x = 0, y = 0, action = MotionEvent.ACTION_UP)))
    }

    @Test
    fun testMotionEventConsumption_downInMagneticField() {
        // We should not consume DOWN events even if they occur in the field.
        assertFalse(magnetizedObject.maybeConsumeMotionEvent(getMotionEvent(
                x = targetCenterX, y = targetCenterY, action = MotionEvent.ACTION_DOWN)))
    }

    @Test
    fun testMoveIntoAroundAndOutOfMagneticField() {
        // Move around but don't touch the magnetic field.
        dispatchMotionEvents(
                getMotionEvent(x = 0, y = 0, action = MotionEvent.ACTION_DOWN),
                getMotionEvent(x = 100, y = 100),
                getMotionEvent(x = 200, y = 200))

        // You can't become unstuck if you were never stuck in the first place.
        verify(magnetListener, never()).onStuckToTarget(magneticTarget,
                magnetizedObject)
        verify(magnetListener, never()).onUnstuckFromTarget(
                eq(magneticTarget), eq(magnetizedObject),
                ArgumentMatchers.anyFloat(), ArgumentMatchers.anyFloat(),
                eq(false))

        // Move into and then around inside the magnetic field.
        dispatchMotionEvents(
                getMotionEvent(x = targetCenterX - 100, y = targetCenterY - 100),
                getMotionEvent(x = targetCenterX, y = targetCenterY),
                getMotionEvent(x = targetCenterX + 100, y = targetCenterY + 100))

        // We should only have received one call to onStuckToTarget and none to unstuck.
        verify(magnetListener, times(1)).onStuckToTarget(magneticTarget, magnetizedObject)
        verify(magnetListener, never()).onUnstuckFromTarget(
                eq(magneticTarget), eq(magnetizedObject),
                ArgumentMatchers.anyFloat(), ArgumentMatchers.anyFloat(),
                eq(false))

        // Move out of the field and then release.
        dispatchMotionEvents(
                getMotionEvent(x = 100, y = 100),
                getMotionEvent(x = 100, y = 100, action = MotionEvent.ACTION_UP))

        // We should have received one unstuck call and no more stuck calls. We also should never
        // have received an onReleasedInTarget call.
        verify(magnetListener, times(1)).onUnstuckFromTarget(
                eq(magneticTarget), eq(magnetizedObject),
                ArgumentMatchers.anyFloat(), ArgumentMatchers.anyFloat(),
                eq(false))
        verifyNoMoreInteractions(magnetListener)
    }

    @Test
    fun testMoveIntoOutOfAndBackIntoMagneticField() {
        // Move into the field
        dispatchMotionEvents(
                getMotionEvent(
                        x = targetCenterX - magneticFieldRadius,
                        y = targetCenterY - magneticFieldRadius,
                        action = MotionEvent.ACTION_DOWN),
                getMotionEvent(
                        x = targetCenterX, y = targetCenterY))

        verify(magnetListener, times(1)).onStuckToTarget(magneticTarget, magnetizedObject)
        verify(magnetListener, never()).onReleasedInTarget(magneticTarget, magnetizedObject)

        // Move back out.
        dispatchMotionEvents(
                getMotionEvent(
                        x = targetCenterX - magneticFieldRadius,
                        y = targetCenterY - magneticFieldRadius))

        verify(magnetListener, times(1)).onUnstuckFromTarget(
                eq(magneticTarget),
                eq(magnetizedObject),
                ArgumentMatchers.anyFloat(), ArgumentMatchers.anyFloat(),
                eq(false))
        verify(magnetListener, never()).onReleasedInTarget(magneticTarget, magnetizedObject)

        // Move in again and release in the magnetic field.
        dispatchMotionEvents(
                getMotionEvent(x = targetCenterX - 100, y = targetCenterY - 100),
                getMotionEvent(x = targetCenterX + 50, y = targetCenterY + 50),
                getMotionEvent(x = targetCenterX, y = targetCenterY),
                getMotionEvent(
                        x = targetCenterX, y = targetCenterY, action = MotionEvent.ACTION_UP))

        verify(magnetListener, times(2)).onStuckToTarget(magneticTarget, magnetizedObject)
        verify(magnetListener).onReleasedInTarget(magneticTarget, magnetizedObject)
        verifyNoMoreInteractions(magnetListener)
    }

    @Test
    fun testFlingTowardsTarget_towardsTarget() {
        timeStep = 10

        // Forcefully fling the object towards the target (but never touch the magnetic field).
        dispatchMotionEvents(
                getMotionEvent(
                        x = 0,
                        y = 0,
                        action = MotionEvent.ACTION_DOWN),
                getMotionEvent(
                        x = targetCenterX / 2,
                        y = targetCenterY / 2),
                getMotionEvent(
                        x = targetCenterX,
                        y = targetCenterY - magneticFieldRadius * 2,
                        action = MotionEvent.ACTION_UP))

        // Nevertheless it should have ended up stuck to the target.
        verify(magnetListener, times(1)).onStuckToTarget(magneticTarget, magnetizedObject)
    }

    @Test
    fun testFlingTowardsTarget_towardsButTooSlow() {
        // Very, very slowly fling the object towards the target (but never touch the magnetic
        // field). This value is only used to create MotionEvent timestamps, it will not block the
        // test for 10 seconds.
        timeStep = 10000
        dispatchMotionEvents(
                getMotionEvent(
                        x = targetCenterX,
                        y = 0,
                        action = MotionEvent.ACTION_DOWN),
                getMotionEvent(
                        x = targetCenterX,
                        y = targetCenterY / 2),
                getMotionEvent(
                        x = targetCenterX,
                        y = targetCenterY - magneticFieldRadius * 2,
                        action = MotionEvent.ACTION_UP))

        // No sticking should have occurred.
        verifyNoMoreInteractions(magnetListener)
    }

    @Test
    fun testFlingTowardsTarget_missTarget() {
        timeStep = 10
        // Forcefully fling the object down, but not towards the target.
        dispatchMotionEvents(
                getMotionEvent(
                        x = 0,
                        y = 0,
                        action = MotionEvent.ACTION_DOWN),
                getMotionEvent(
                        x = 0,
                        y = targetCenterY / 2),
                getMotionEvent(
                        x = 0,
                        y = targetCenterY - magneticFieldRadius * 2,
                        action = MotionEvent.ACTION_UP))

        verifyNoMoreInteractions(magnetListener)
    }

    @Test
    fun testMagnetAnimation() {
        // Make sure the object starts at (0, 0).
        assertEquals(0f, objectX)
        assertEquals(0f, objectY)

        // Trigger the magnet animation, and block the test until it ends.
        PhysicsAnimatorTestUtils.setAllAnimationsBlock(true)
        magnetizedObject.maybeConsumeMotionEvent(getMotionEvent(
                x = targetCenterX - 250,
                y = targetCenterY - 250,
                action = MotionEvent.ACTION_DOWN))

        magnetizedObject.maybeConsumeMotionEvent(getMotionEvent(
                x = targetCenterX,
                y = targetCenterY))

        // The object's (top-left) position should now position it centered over the target.
        assertEquals(targetCenterX - objectSize / 2, objectX)
        assertEquals(targetCenterY - objectSize / 2, objectY)
    }

    @Test
    fun testMultipleTargets() {
        val secondMagneticTarget = getSecondMagneticTarget()

        // Drag into the second target.
        dispatchMotionEvents(
                getMotionEvent(x = 0, y = 0, action = MotionEvent.ACTION_DOWN),
                getMotionEvent(x = 100, y = 900))

        // Verify that we received an onStuck for the second target, and no others.
        verify(magnetListener).onStuckToTarget(secondMagneticTarget, magnetizedObject)
        verifyNoMoreInteractions(magnetListener)

        // Drag into the original target.
        dispatchMotionEvents(
                getMotionEvent(x = 0, y = 0),
                getMotionEvent(x = 500, y = 900))

        // We should have unstuck from the second one and stuck into the original one.
        verify(magnetListener).onUnstuckFromTarget(
                eq(secondMagneticTarget), eq(magnetizedObject),
                anyFloat(), anyFloat(), eq(false))
        verify(magnetListener).onStuckToTarget(magneticTarget, magnetizedObject)
        verifyNoMoreInteractions(magnetListener)
    }

    @Test
    fun testMultipleTargets_flingIntoSecond() {
        val secondMagneticTarget = getSecondMagneticTarget()

        timeStep = 10

        // Fling towards the second target.
        dispatchMotionEvents(
                getMotionEvent(x = 100, y = 0, action = MotionEvent.ACTION_DOWN),
                getMotionEvent(x = 100, y = 350),
                getMotionEvent(x = 100, y = 650, action = MotionEvent.ACTION_UP))

        // Verify that we received an onStuck for the second target.
        verify(magnetListener).onStuckToTarget(secondMagneticTarget, magnetizedObject)

        // Fling towards the first target.
        dispatchMotionEvents(
                getMotionEvent(x = 300, y = 0, action = MotionEvent.ACTION_DOWN),
                getMotionEvent(x = 400, y = 350),
                getMotionEvent(x = 500, y = 650, action = MotionEvent.ACTION_UP))

        // Verify that we received onStuck for the original target.
        verify(magnetListener).onStuckToTarget(magneticTarget, magnetizedObject)
    }

    @Test
    fun testMagneticTargetHasScreenOffset_moveIntoAndReleaseInTarget() {
        magneticTarget.screenVerticalOffset = 500

        dispatchMotionEvents(getMotionEvent(x = targetCenterX, y = targetCenterY))
        // Moved into the target location, but it should be shifted due to screen offset.
        // Should not get stuck.
        verify(magnetListener, never()).onStuckToTarget(magneticTarget, magnetizedObject)

        dispatchMotionEvents(getMotionEvent(x = targetCenterX, y = targetCenterY + 500))
        verify(magnetListener).onStuckToTarget(magneticTarget, magnetizedObject)

        dispatchMotionEvents(
            getMotionEvent(
                x = targetCenterX,
                y = targetCenterY + 500,
                action = MotionEvent.ACTION_UP
            )
        )

        verify(magnetListener).onReleasedInTarget(magneticTarget, magnetizedObject)
        verifyNoMoreInteractions(magnetListener)
    }

    @Test
    fun testMagneticTargetHasScreenOffset_screenOffsetUpdates() {
        magneticTarget.screenVerticalOffset = 500
        val adjustedTargetCenter = targetCenterY + 500

        dispatchMotionEvents(getMotionEvent(x = targetCenterX, y = adjustedTargetCenter))
        dispatchMotionEvents(getMotionEvent(x = 0, y = 0))
        verify(magnetListener).onStuckToTarget(magneticTarget, magnetizedObject)
        verify(magnetListener)
                .onUnstuckFromTarget(eq(magneticTarget), eq(magnetizedObject),
                        anyFloat(), anyFloat(), anyBoolean())

        // Offset if removed, we should now get stuck at the target location
        magneticTarget.screenVerticalOffset = 0
        dispatchMotionEvents(getMotionEvent(x = targetCenterX, y = targetCenterY))
        verify(magnetListener, times(2)).onStuckToTarget(magneticTarget, magnetizedObject)
    }

    @Test
    fun testMagneticTargetHasScreenOffset_flingTowardsTarget() {
        timeStep = 10

        magneticTarget.screenVerticalOffset = 500
        val adjustedTargetCenter = targetCenterY + 500

        // Forcefully fling the object towards the target (but never touch the magnetic field).
        dispatchMotionEvents(
            getMotionEvent(x = 0, y = 0, action = MotionEvent.ACTION_DOWN),
            getMotionEvent(x = targetCenterX / 2, y = adjustedTargetCenter / 2),
            getMotionEvent(
                x = targetCenterX,
                y = adjustedTargetCenter - magneticFieldRadius * 2,
                action = MotionEvent.ACTION_UP
            )
        )

        // Nevertheless it should have ended up stuck to the target.
        verify(magnetListener, times(1)).onStuckToTarget(magneticTarget, magnetizedObject)
    }

    private fun getSecondMagneticTarget(): MagnetizedObject.MagneticTarget {
        // The first target view is at bounds (400, 800, 600, 1000) and it has a center of
        // (500, 900). We'll add a second one at bounds (0, 800, 200, 1000) with center (100, 900).
        val secondTargetView = mock(View::class.java)
        val secondTargetCenterX = 100
        val secondTargetCenterY = 900

        `when`(secondTargetView.context).thenReturn(context)
        `when`(secondTargetView.width).thenReturn(targetSize) // width = 200
        `when`(secondTargetView.height).thenReturn(targetSize) // height = 200
        doAnswer { invocation ->
            (invocation.arguments[0] as Runnable).run()
            true
        }.`when`(secondTargetView).post(ArgumentMatchers.any())
        doAnswer { invocation ->
            (invocation.arguments[0] as IntArray).also { location ->
                // Return the top left of the target.
                location[0] = secondTargetCenterX - targetSize / 2 // x = 0
                location[1] = secondTargetCenterY - targetSize / 2 // y = 800
            }
        }.`when`(secondTargetView).getLocationOnScreen(ArgumentMatchers.any())

        return magnetizedObject.addTarget(secondTargetView, magneticFieldRadius)
    }

    /**
     * Return a MotionEvent at the given coordinates, with the given action (or MOVE by default).
     * The event's time fields will be incremented by 10ms each time this is called, so tha
     * VelocityTracker works.
     */
    private fun getMotionEvent(
        x: Int,
        y: Int,
        action: Int = MotionEvent.ACTION_MOVE
    ): MotionEvent {
        return MotionEvent.obtain(time, time, action, x.toFloat(), y.toFloat(), 0)
                .also { time += timeStep }
    }

    /** Dispatch all of the provided events to the target view. */
    private fun dispatchMotionEvents(vararg events: MotionEvent) {
        events.forEach { magnetizedObject.maybeConsumeMotionEvent(it) }
    }

    /** Prevents Kotlin from being mad that eq() is nullable. */
    private fun <T> eq(value: T): T = Mockito.eq(value) ?: value
}