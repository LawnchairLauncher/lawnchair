/*
 * Copyright (C) 2026 The Lawnchair Authors
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
package com.android.launcher3;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.launcher3.dragndrop.DragLayer;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests pointer tracking for directional icon gestures. */
@RunWith(AndroidJUnit4.class)
public class IconGestureTouchTrackerTest {

    @Test
    public void originalPointerLifted_doesNotHandleRemainingPointerMove() {
        Launcher launcher = mock(Launcher.class);
        Workspace<?> workspace = mock(Workspace.class);
        DragLayer dragLayer = mock(DragLayer.class);
        when(launcher.getWorkspace()).thenReturn(workspace);
        when(launcher.getDragLayer()).thenReturn(dragLayer);
        when(workspace.isTouchOnIconWithSwipeGesture(anyFloat(), anyFloat(), eq(true)))
                .thenReturn(true);

        IconGestureTouchTracker tracker = new IconGestureTouchTracker(launcher);
        MotionEvent down = motionEvent(MotionEvent.ACTION_DOWN, new int[]{0},
                new float[]{10}, new float[]{10});
        MotionEvent pointerDown = motionEvent(
                MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                new int[]{0, 1}, new float[]{10, 20}, new float[]{10, 20});
        MotionEvent pointerUp = motionEvent(MotionEvent.ACTION_POINTER_UP, new int[]{0, 1},
                new float[]{10, 20}, new float[]{10, 20});
        MotionEvent move = motionEvent(MotionEvent.ACTION_MOVE, new int[]{1},
                new float[]{20}, new float[]{40});

        try {
            tracker.onTouchDown(down);

            // Pointer 1 joins, then the original pointer 0 lifts before pointer 1 moves.
            assertEquals(0, pointerDown.getPointerId(0));
            assertEquals(0, pointerUp.getPointerId(pointerUp.getActionIndex()));
            assertEquals(-1, move.findPointerIndex(down.getPointerId(0)));

            assertFalse(tracker.isMovingTowardConfiguredIconGesture(move));
            verify(workspace, never()).isTouchOnIconWithSwipeGestureInDirection(
                    anyFloat(), anyFloat(), anyFloat(), anyFloat());
        } finally {
            down.recycle();
            pointerDown.recycle();
            pointerUp.recycle();
            move.recycle();
        }
    }

    private static MotionEvent motionEvent(int action, int[] pointerIds, float[] xs, float[] ys) {
        MotionEvent.PointerProperties[] pointerProperties =
                new MotionEvent.PointerProperties[pointerIds.length];
        MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[pointerIds.length];
        for (int i = 0; i < pointerIds.length; i++) {
            pointerProperties[i] = new MotionEvent.PointerProperties();
            pointerProperties[i].id = pointerIds[i];
            pointerProperties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            pointerCoords[i] = new MotionEvent.PointerCoords();
            pointerCoords[i].x = xs[i];
            pointerCoords[i].y = ys[i];
            pointerCoords[i].pressure = 1;
            pointerCoords[i].size = 1;
        }
        return MotionEvent.obtain(0, 0, action, pointerIds.length, pointerProperties, pointerCoords,
                0, 0, 1, 1, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
    }
}
