/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.IWindowContainerToken;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.transition.TransitionInfoBuilder;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests of {@link BubblesTransitionObserver}.
 */
@SmallTest
public class BubblesTransitionObserverTest {

    @Mock
    private BubbleController mBubbleController;
    @Mock
    private BubbleData mBubbleData;

    @Mock
    private IBinder mTransition;
    @Mock
    private SurfaceControl.Transaction mStartT;
    @Mock
    private SurfaceControl.Transaction mFinishT;

    @Mock
    private Bubble mBubble;

    private BubblesTransitionObserver mTransitionObserver;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mTransitionObserver = new BubblesTransitionObserver(mBubbleController, mBubbleData);
    }

    @Test
    public void testOnTransitionReady_open_collapsesStack() {
        when(mBubbleData.isExpanded()).thenReturn(true);
        when(mBubbleData.getSelectedBubble()).thenReturn(mBubble);
        when(mBubble.getTaskId()).thenReturn(1);
        when(mBubbleController.isStackAnimating()).thenReturn(false);

        TransitionInfo info = createTransitionInfo(TRANSIT_OPEN, createTaskInfo(2));

        mTransitionObserver.onTransitionReady(mTransition, info, mStartT, mFinishT);

        verify(mBubbleData).setExpanded(eq(false));
    }

    @Test
    public void testOnTransitionReady_toFront_collapsesStack() {
        when(mBubbleData.isExpanded()).thenReturn(true);
        when(mBubbleData.getSelectedBubble()).thenReturn(mBubble);
        when(mBubble.getTaskId()).thenReturn(1);
        when(mBubbleController.isStackAnimating()).thenReturn(false);

        TransitionInfo info = createTransitionInfo(TRANSIT_TO_FRONT, createTaskInfo(2));

        mTransitionObserver.onTransitionReady(mTransition, info, mStartT, mFinishT);

        verify(mBubbleData).setExpanded(eq(false));
    }

    @Test
    public void testOnTransitionReady_noTaskInfo_skip() {
        when(mBubbleData.isExpanded()).thenReturn(true);
        when(mBubbleData.getSelectedBubble()).thenReturn(mBubble);
        when(mBubble.getTaskId()).thenReturn(1);
        when(mBubbleController.isStackAnimating()).thenReturn(false);

        // Null task info
        TransitionInfo info = createTransitionInfo(TRANSIT_TO_FRONT, null /* taskInfo */);

        mTransitionObserver.onTransitionReady(mTransition, info, mStartT, mFinishT);

        verify(mBubbleData, never()).setExpanded(eq(false));
    }

    @Test
    public void testOnTransitionReady_noTaskId_skip() {
        when(mBubbleData.isExpanded()).thenReturn(true);
        when(mBubbleData.getSelectedBubble()).thenReturn(mBubble);
        when(mBubble.getTaskId()).thenReturn(1);
        when(mBubbleController.isStackAnimating()).thenReturn(false);

        // Invalid task id
        TransitionInfo info = createTransitionInfo(TRANSIT_TO_FRONT,
                createTaskInfo(INVALID_TASK_ID));

        mTransitionObserver.onTransitionReady(mTransition, info, mStartT, mFinishT);

        verify(mBubbleData, never()).setExpanded(eq(false));
    }

    @Test
    public void testOnTransitionReady_notOpening_skip() {
        when(mBubbleData.isExpanded()).thenReturn(true);
        when(mBubbleData.getSelectedBubble()).thenReturn(mBubble);
        when(mBubble.getTaskId()).thenReturn(1);
        when(mBubbleController.isStackAnimating()).thenReturn(false);

        // Transits that aren't opening
        TransitionInfo info = createTransitionInfo(TRANSIT_CHANGE, createTaskInfo(2));
        mTransitionObserver.onTransitionReady(mTransition, info, mStartT, mFinishT);

        info = createTransitionInfo(TRANSIT_CLOSE, createTaskInfo(3));
        mTransitionObserver.onTransitionReady(mTransition, info, mStartT, mFinishT);

        info = createTransitionInfo(TRANSIT_TO_BACK, createTaskInfo(4));
        mTransitionObserver.onTransitionReady(mTransition, info, mStartT, mFinishT);

        verify(mBubbleData, never()).setExpanded(eq(false));
    }

    @Test
    public void testOnTransitionReady_stackAnimating_skip() {
        when(mBubbleData.isExpanded()).thenReturn(true);
        when(mBubbleData.getSelectedBubble()).thenReturn(mBubble);
        when(mBubble.getTaskId()).thenReturn(1);
        when(mBubbleController.isStackAnimating()).thenReturn(true); // Stack is animating

        TransitionInfo info = createTransitionInfo(TRANSIT_OPEN, createTaskInfo(2));

        mTransitionObserver.onTransitionReady(mTransition, info, mStartT, mFinishT);

        verify(mBubbleData, never()).setExpanded(eq(false));
    }

    @Test
    public void testOnTransitionReady_stackNotExpanded_skip() {
        when(mBubbleData.isExpanded()).thenReturn(false); // Stack is not expanded
        when(mBubbleData.getSelectedBubble()).thenReturn(mBubble);
        when(mBubble.getTaskId()).thenReturn(1);
        when(mBubbleController.isStackAnimating()).thenReturn(false);

        TransitionInfo info = createTransitionInfo(TRANSIT_TO_FRONT, createTaskInfo(2));

        mTransitionObserver.onTransitionReady(mTransition, info, mStartT, mFinishT);

        verify(mBubbleData, never()).setExpanded(eq(false));
    }

    @Test
    public void testOnTransitionReady_noSelectedBubble_skip() {
        when(mBubbleData.isExpanded()).thenReturn(true);
        when(mBubbleData.getSelectedBubble()).thenReturn(null); // No selected bubble
        when(mBubble.getTaskId()).thenReturn(1);
        when(mBubbleController.isStackAnimating()).thenReturn(false);

        TransitionInfo info = createTransitionInfo(TRANSIT_OPEN, createTaskInfo(2));

        mTransitionObserver.onTransitionReady(mTransition, info, mStartT, mFinishT);

        verify(mBubbleData, never()).setExpanded(eq(false));
    }

    @Test
    public void testOnTransitionReady_openingMatchesExpanded_skip() {
        when(mBubbleData.isExpanded()).thenReturn(true);
        when(mBubbleData.getSelectedBubble()).thenReturn(mBubble);
        when(mBubble.getTaskId()).thenReturn(1);
        when(mBubbleController.isStackAnimating()).thenReturn(false);

        // What's moving to front is same as the opened bubble
        TransitionInfo info = createTransitionInfo(TRANSIT_TO_FRONT, createTaskInfo(1));

        mTransitionObserver.onTransitionReady(mTransition, info, mStartT, mFinishT);

        verify(mBubbleData, never()).setExpanded(eq(false));
    }

    private ActivityManager.RunningTaskInfo createTaskInfo(int taskId) {
        final ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        return taskInfo;
    }

    private TransitionInfo createTransitionInfo(int changeType,
            ActivityManager.RunningTaskInfo info) {
        final TransitionInfo.Change change = new TransitionInfo.Change(
                new WindowContainerToken(mock(IWindowContainerToken.class)),
                mock(SurfaceControl.class));
        change.setMode(changeType);
        change.setTaskInfo(info);

        return new TransitionInfoBuilder(TRANSIT_OPEN, 0)
                .addChange(change).build();
    }

}
