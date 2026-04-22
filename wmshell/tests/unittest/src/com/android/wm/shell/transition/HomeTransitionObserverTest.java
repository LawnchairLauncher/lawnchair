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

package com.android.wm.shell.transition;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PREPARE_BACK_NAVIGATION;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.TransitionInfo.FLAG_BACK_GESTURE_ANIMATED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.window.flags.Flags.FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX;
import static com.android.wm.shell.desktopmode.DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_CONVERT_TO_BUBBLE;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.WindowConfiguration.ActivityType;
import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.TransitionInfo.TransitionMode;
import android.window.WindowContainerToken;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.wm.shell.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.IHomeTransitionListener;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for the home transition observer.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class HomeTransitionObserverTest extends ShellTestCase {
    private final ShellTaskOrganizer mOrganizer = mock(ShellTaskOrganizer.class);
    private final TransactionPool mTransactionPool = mock(TransactionPool.class);
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final ShellExecutor mAnimExecutor = new TestShellExecutor();
    private final TestShellExecutor mMainExecutor = new TestShellExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final DisplayController mDisplayController = mock(DisplayController.class);
    private final DisplayInsetsController mDisplayInsetsController =
            mock(DisplayInsetsController.class);

    private IHomeTransitionListener mListener;
    private Transitions mTransition;
    private HomeTransitionObserver mHomeTransitionObserver;

    @Before
    public void setUp() {
        mListener = mock(IHomeTransitionListener.class);
        when(mListener.asBinder()).thenReturn(mock(IBinder.class));

        mHomeTransitionObserver = new HomeTransitionObserver(mContext, mMainExecutor,
                mDisplayInsetsController, mock(ShellInit.class));
        mTransition = new Transitions(mContext, mock(ShellInit.class), mock(ShellController.class),
                mOrganizer, mTransactionPool, mDisplayController, mDisplayInsetsController,
                mMainExecutor, mMainHandler, mAnimExecutor, mHomeTransitionObserver,
                mock(FocusTransitionObserver.class));
        mHomeTransitionObserver.setHomeTransitionListener(mTransition, mListener);
    }

    @Test
    public void testHomeActivityWithOpenModeNotifiesHomeIsVisible() throws RemoteException {
        TransitionInfo info = mock(TransitionInfo.class);
        TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        ActivityManager.RunningTaskInfo taskInfo = mock(ActivityManager.RunningTaskInfo.class);
        when(change.getTaskInfo()).thenReturn(taskInfo);
        when(info.getChanges()).thenReturn(new ArrayList<>(List.of(change)));

        setupTransitionInfo(taskInfo, change, ACTIVITY_TYPE_HOME, TRANSIT_OPEN, true);

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(1)).onHomeVisibilityChanged(true);
    }

    @Test
    public void testHomeActivityWithCloseModeNotifiesHomeIsNotVisible() throws RemoteException {
        TransitionInfo info = mock(TransitionInfo.class);
        TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        ActivityManager.RunningTaskInfo taskInfo = mock(ActivityManager.RunningTaskInfo.class);
        when(change.getTaskInfo()).thenReturn(taskInfo);
        when(info.getChanges()).thenReturn(new ArrayList<>(List.of(change)));

        setupTransitionInfo(taskInfo, change, ACTIVITY_TYPE_HOME, TRANSIT_TO_BACK, true);

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(1)).onHomeVisibilityChanged(false);
    }

    @Test
    public void testNonHomeActivityDoesNotTriggerCallback() throws RemoteException {
        TransitionInfo info = mock(TransitionInfo.class);
        TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        ActivityManager.RunningTaskInfo taskInfo = mock(ActivityManager.RunningTaskInfo.class);
        when(change.getTaskInfo()).thenReturn(taskInfo);
        when(info.getChanges()).thenReturn(new ArrayList<>(List.of(change)));

        setupTransitionInfo(taskInfo, change, ACTIVITY_TYPE_UNDEFINED, TRANSIT_TO_BACK, true);

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(0)).onHomeVisibilityChanged(anyBoolean());
    }

    @Test
    public void testNonRunningHomeActivityDoesNotTriggerCallback() throws RemoteException {
        TransitionInfo info = mock(TransitionInfo.class);
        TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        ActivityManager.RunningTaskInfo taskInfo = mock(ActivityManager.RunningTaskInfo.class);
        when(change.getTaskInfo()).thenReturn(taskInfo);
        when(info.getChanges()).thenReturn(new ArrayList<>(List.of(change)));

        setupTransitionInfo(taskInfo, change, ACTIVITY_TYPE_UNDEFINED, TRANSIT_TO_BACK, false);

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(0)).onHomeVisibilityChanged(anyBoolean());
    }

    @Test
    public void testStartDragToDesktopDoesNotTriggerCallback() throws RemoteException {
        TransitionInfo info = mock(TransitionInfo.class);
        TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        ActivityManager.RunningTaskInfo taskInfo = mock(ActivityManager.RunningTaskInfo.class);
        when(change.getTaskInfo()).thenReturn(taskInfo);
        when(info.getChanges()).thenReturn(new ArrayList<>(List.of(change)));
        when(info.getType()).thenReturn(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP);

        setupTransitionInfo(taskInfo, change, ACTIVITY_TYPE_HOME, TRANSIT_OPEN, true);

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        verify(mListener, times(0)).onHomeVisibilityChanged(anyBoolean());
    }

    @Test
    @DisableFlags({FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX})
    public void startDragToDesktopFinished_flagDisabled_doesNotTriggerCallback()
            throws RemoteException {
        TransitionInfo info = mock(TransitionInfo.class);
        TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        ActivityManager.RunningTaskInfo taskInfo = mock(ActivityManager.RunningTaskInfo.class);
        when(change.getTaskInfo()).thenReturn(taskInfo);
        when(info.getChanges()).thenReturn(new ArrayList<>(List.of(change)));
        when(info.getType()).thenReturn(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP);
        setupTransitionInfo(taskInfo, change, ACTIVITY_TYPE_HOME, TRANSIT_OPEN, true);
        IBinder transition = mock(IBinder.class);
        mHomeTransitionObserver.onTransitionReady(
                transition,
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        mHomeTransitionObserver.onTransitionFinished(transition, /* aborted= */ false);

        verify(mListener, never()).onHomeVisibilityChanged(/* isVisible= */ anyBoolean());
    }

    @Test
    @EnableFlags({FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX})
    public void startDragToDesktopAborted_triggersCallback() throws RemoteException {
        TransitionInfo info = mock(TransitionInfo.class);
        TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        ActivityManager.RunningTaskInfo taskInfo = mock(ActivityManager.RunningTaskInfo.class);
        when(change.getTaskInfo()).thenReturn(taskInfo);
        when(info.getChanges()).thenReturn(new ArrayList<>(List.of(change)));
        when(info.getType()).thenReturn(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP);
        setupTransitionInfo(taskInfo, change, ACTIVITY_TYPE_HOME, TRANSIT_OPEN, true);
        IBinder transition = mock(IBinder.class);
        mHomeTransitionObserver.onTransitionReady(
                transition,
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        mHomeTransitionObserver.onTransitionFinished(transition, /* aborted= */ true);

        verify(mListener).onHomeVisibilityChanged(/* isVisible= */ true);
    }

    @Test
    @EnableFlags({FLAG_ENABLE_DRAG_TO_DESKTOP_INCOMING_TRANSITIONS_BUGFIX})
    public void startDragToDesktopFinished_triggersCallback() throws RemoteException {
        TransitionInfo info = mock(TransitionInfo.class);
        TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        ActivityManager.RunningTaskInfo taskInfo = mock(ActivityManager.RunningTaskInfo.class);
        when(change.getTaskInfo()).thenReturn(taskInfo);
        when(info.getChanges()).thenReturn(new ArrayList<>(List.of(change)));
        when(info.getType()).thenReturn(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP);
        setupTransitionInfo(taskInfo, change, ACTIVITY_TYPE_HOME, TRANSIT_OPEN, true);
        IBinder transition = mock(IBinder.class);
        mHomeTransitionObserver.onTransitionReady(
                transition,
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));

        mHomeTransitionObserver.onTransitionFinished(transition, /* aborted= */ false);

        verify(mListener).onHomeVisibilityChanged(/* isVisible= */ true);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN, Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE})
    public void testDragTaskToBubbleOverHome_notifiesHomeIsVisible() throws RemoteException {
        ActivityManager.RunningTaskInfo homeTask = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        ActivityManager.RunningTaskInfo bubbleTask = createTaskInfo(2, ACTIVITY_TYPE_STANDARD);

        TransitionInfo startDragTransition =
                new TransitionInfoBuilder(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP)
                        .addChange(TRANSIT_TO_FRONT, homeTask)
                        .addChange(TRANSIT_TO_BACK, bubbleTask)
                        .build();

        // Start drag to desktop which brings home to front
        mHomeTransitionObserver.onTransitionReady(new Binder(), startDragTransition,
                MockTransactionPool.create(), MockTransactionPool.create());
        // Does not notify home visibility yet
        verify(mListener, never()).onHomeVisibilityChanged(anyBoolean());

        TransitionInfo convertToBubbleTransition =
                new TransitionInfoBuilder(TRANSIT_CONVERT_TO_BUBBLE)
                        .addChange(TRANSIT_TO_FRONT, bubbleTask)
                        .build();

        // Convert to bubble. Transition does not include changes for home task
        mHomeTransitionObserver.onTransitionReady(new Binder(), convertToBubbleTransition,
                MockTransactionPool.create(), MockTransactionPool.create());

        // Notifies home visibility change that was pending from the start of drag
        verify(mListener).onHomeVisibilityChanged(true);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_BUBBLE_TO_FULLSCREEN, Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE})
    public void testDragTaskToBubbleOverOtherTask_notifiesHomeIsNotVisible()
            throws RemoteException {
        ActivityManager.RunningTaskInfo homeTask = createTaskInfo(1, ACTIVITY_TYPE_HOME);
        ActivityManager.RunningTaskInfo bubbleTask = createTaskInfo(2, ACTIVITY_TYPE_STANDARD);
        ActivityManager.RunningTaskInfo otherTask = createTaskInfo(3, ACTIVITY_TYPE_STANDARD);

        TransitionInfo startDragTransition =
                new TransitionInfoBuilder(TRANSIT_DESKTOP_MODE_START_DRAG_TO_DESKTOP)
                        .addChange(TRANSIT_TO_FRONT, homeTask)
                        .addChange(TRANSIT_TO_BACK, bubbleTask)
                        .build();

        // Start drag to desktop which brings home to front
        mHomeTransitionObserver.onTransitionReady(new Binder(), startDragTransition,
                MockTransactionPool.create(), MockTransactionPool.create());
        // Does not notify home visibility yet
        verify(mListener, never()).onHomeVisibilityChanged(anyBoolean());

        TransitionInfo convertToBubbleTransition =
                new TransitionInfoBuilder(TRANSIT_CONVERT_TO_BUBBLE)
                        .addChange(TRANSIT_TO_FRONT, bubbleTask)
                        .addChange(TRANSIT_TO_FRONT, otherTask)
                        .addChange(TRANSIT_TO_BACK, homeTask)
                        .build();

        // Convert to bubble. Transition includes home task to back which updates home visibility
        mHomeTransitionObserver.onTransitionReady(new Binder(), convertToBubbleTransition,
                MockTransactionPool.create(), MockTransactionPool.create());

        // Notifies home visibility change due to home moving to back in the second transition
        verify(mListener).onHomeVisibilityChanged(false);
    }

    @Test
    public void testHomeActivityWithBackGestureNotifiesHomeIsVisibleAfterClose()
            throws RemoteException {
        TransitionInfo info = mock(TransitionInfo.class);
        TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        ActivityManager.RunningTaskInfo taskInfo = mock(ActivityManager.RunningTaskInfo.class);
        when(change.getTaskInfo()).thenReturn(taskInfo);
        when(info.getChanges()).thenReturn(new ArrayList<>(List.of(change)));
        when(info.getType()).thenReturn(TRANSIT_PREPARE_BACK_NAVIGATION);

        when(change.hasFlags(FLAG_BACK_GESTURE_ANIMATED)).thenReturn(true);
        setupTransitionInfo(taskInfo, change, ACTIVITY_TYPE_HOME, TRANSIT_OPEN, true);

        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));
        verify(mListener, times(0)).onHomeVisibilityChanged(anyBoolean());

        when(info.getType()).thenReturn(TRANSIT_TO_BACK);
        setupTransitionInfo(taskInfo, change, ACTIVITY_TYPE_HOME, TRANSIT_CHANGE, true);
        mHomeTransitionObserver.onTransitionReady(mock(IBinder.class),
                info,
                mock(SurfaceControl.Transaction.class),
                mock(SurfaceControl.Transaction.class));
        verify(mListener, times(1)).onHomeVisibilityChanged(true);
    }

    /**
     * Helper class to initialize variables for the rest.
     */
    private void setupTransitionInfo(ActivityManager.RunningTaskInfo taskInfo,
            TransitionInfo.Change change,
            @ActivityType int activityType,
            @TransitionMode int mode,
            boolean isRunning) {
        when(taskInfo.getActivityType()).thenReturn(activityType);
        when(change.getMode()).thenReturn(mode);
        taskInfo.isRunning = isRunning;
    }

    private static ActivityManager.RunningTaskInfo createTaskInfo(int taskId, int activityType) {
        ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.topActivityType = activityType;
        taskInfo.configuration.windowConfiguration.setActivityType(activityType);
        taskInfo.token = mock(WindowContainerToken.class);
        taskInfo.isRunning = true;
        return taskInfo;
    }
}
