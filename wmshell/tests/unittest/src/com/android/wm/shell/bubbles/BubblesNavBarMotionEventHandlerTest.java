/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.bubbles;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.floatThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.os.SystemClock;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.bubbles.BubblesNavBarMotionEventHandler.MotionEventListener;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test {@link MotionEvent} handling in {@link BubblesNavBarMotionEventHandler}.
 * Verifies that swipe events
 */
@SmallTest
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@RunWith(AndroidTestingRunner.class)
public class BubblesNavBarMotionEventHandlerTest extends ShellTestCase {

    private BubblesNavBarMotionEventHandler mMotionEventHandler;
    @Mock
    private Runnable mInterceptTouchRunnable;
    @Mock
    private MotionEventListener mMotionEventListener;
    private long mMotionEventTime;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        TestableBubblePositioner positioner = new TestableBubblePositioner(getContext(),
                getContext().getSystemService(WindowManager.class));
        mMotionEventHandler = new BubblesNavBarMotionEventHandler(getContext(), positioner,
                mInterceptTouchRunnable, mMotionEventListener);
        mMotionEventTime = SystemClock.uptimeMillis();
    }

    @Test
    public void testMotionEvent_swipeUpInGestureZone_handled() {
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_DOWN, 0, 990));
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_MOVE, 0, 690));
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_MOVE, 0, 490));
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_MOVE, 0, 390));
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_UP, 0, 390));

        verify(mMotionEventListener).onDown(0, 990);
        verify(mMotionEventListener).onMove(0, -300);
        verify(mMotionEventListener).onMove(0, -500);
        verify(mMotionEventListener).onMove(0, -600);
        // Check that velocity up is about 5000
        verify(mMotionEventListener).onUp(eq(0f), floatThat(f -> Math.round(f) == -5000));
        verifyZeroInteractions(mMotionEventListener);
        verify(mInterceptTouchRunnable).run();
    }

    @Test
    public void testMotionEvent_swipeUpOutsideGestureZone_ignored() {
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_DOWN, 0, 500));
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_MOVE, 0, 100));
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_UP, 0, 100));

        verifyZeroInteractions(mMotionEventListener);
        verifyZeroInteractions(mInterceptTouchRunnable);
    }

    @Test
    public void testMotionEvent_horizontalMoveMoreThanTouchSlop_handled() {
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_DOWN, 0, 990));
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_MOVE, 100, 990));
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_UP, 100, 990));

        verify(mMotionEventListener).onDown(0, 990);
        verify(mMotionEventListener).onMove(100, 0);
        verify(mMotionEventListener).onUp(0, 0);
        verifyZeroInteractions(mMotionEventListener);
        verify(mInterceptTouchRunnable).run();
    }

    @Test
    public void testMotionEvent_moveLessThanTouchSlop_ignored() {
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_DOWN, 0, 990));
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_MOVE, 0, 989));
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_UP, 0, 989));

        verify(mMotionEventListener).onDown(0, 990);
        verifyNoMoreInteractions(mMotionEventListener);
        verifyZeroInteractions(mInterceptTouchRunnable);
    }

    @Test
    public void testMotionEvent_actionCancel_listenerNotified() {
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_DOWN, 0, 990));
        mMotionEventHandler.onMotionEvent(newEvent(ACTION_CANCEL, 0, 990));
        verify(mMotionEventListener).onDown(0, 990);
        verify(mMotionEventListener).onCancel();
        verifyNoMoreInteractions(mMotionEventListener);
        verifyZeroInteractions(mInterceptTouchRunnable);
    }

    private MotionEvent newEvent(int actionDown, float x, float y) {
        MotionEvent event = MotionEvent.obtain(0L, mMotionEventTime, actionDown, x, y, 0);
        mMotionEventTime += 10;
        return event;
    }
}
