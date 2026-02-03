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

package com.android.wm.shell.taskview;

import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.window.flags.Flags.enableHandlersDebuggingMode;
import static com.android.wm.shell.Flags.FLAG_TASK_VIEW_TRANSITIONS_REFACTOR;
import static com.android.wm.shell.Flags.taskViewTransitionsRefactor;
import static com.android.wm.shell.transition.TransitionDispatchState.CAPTURED_UNRELATED_CHANGE;
import static com.android.wm.shell.transition.TransitionDispatchState.LOST_RELEVANT_CHANGE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.graphics.Rect;
import android.os.IBinder;
import android.platform.test.annotations.UsesFlags;
import android.platform.test.flag.junit.FlagsParameterization;
import android.testing.TestableLooper;
import android.util.Slog;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.transition.TransitionDispatchState;
import com.android.wm.shell.transition.TransitionInfoBuilder;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.util.StubTransaction;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;

/**
 * Class to verify the behavior of startAnimation.
 * 1. Verifies that startAnimation populates TransitionDispatchState correctly.
 * 2. Verifies that changes behind FLAG_ENABLE_HANDLERS_DEBUGGING_MODE don't change the behavior
 *    of startAnimation. Refactor test's life span matches the flag's. Permanent tests are meant to
 *    be added to TaskViewTransitionsTest.
 *    Test failures that manifest only when the flag is on mean that the behavior diverged.
 */
@SmallTest
@RunWith(ParameterizedAndroidJunit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@UsesFlags(com.android.wm.shell.Flags.class)
public class TaskViewTransitionStartAnimationTest extends ShellTestCase {
    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                FLAG_TASK_VIEW_TRANSITIONS_REFACTOR);
    }

    @Mock
    Transitions mTransitions;
    TaskViewRepository mTaskViewRepository;
    TaskViewTransitions mTaskViewTransitions;

    @Mock
    TaskViewTaskController mTaskViewTaskController;
    @Mock
    ActivityManager.RunningTaskInfo mTaskInfo;
    @Mock
    ActivityManager.RunningTaskInfo mUnregisteredTaskInfo;
    @Mock
    WindowContainerToken mToken;
    @Mock
    IBinder mTokenBinder;
    @Mock
    WindowContainerToken mUnregisteredToken;
    @Mock
    IBinder mUnregisteredTokenBinder;
    @Mock
    IBinder mLaunchCookie;
    Rect mBounds;
    @Mock
    SurfaceControl mTaskLeash;
    @Mock
    SurfaceControl mSurfaceControl;
    StubTransaction mStartTransaction;
    StubTransaction mFinishTransaction;
    @Mock
    Transitions.TransitionFinishCallback mFinishCallback;

    TaskViewTransitions.PendingTransition mPendingFront;
    TaskViewTransitions.PendingTransition mPendingBack;

    static final String TAG = "TVstartAnimTest";

    public TaskViewTransitionStartAnimationTest(FlagsParameterization flags) {
        mSetFlagsRule.setFlagsParameterization(flags);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mToken.asBinder()).thenReturn(mTokenBinder);
        when(mUnregisteredToken.asBinder()).thenReturn(mUnregisteredTokenBinder);
        mTaskInfo = new ActivityManager.RunningTaskInfo();
        mTaskInfo.token = mToken;
        mTaskInfo.taskId = 314;
        mTaskInfo.taskDescription = mock(ActivityManager.TaskDescription.class);
        mTaskInfo.launchCookies.add(mLaunchCookie);

        // Task not registered in mTaskViewTaskController
        mUnregisteredTaskInfo =  new ActivityManager.RunningTaskInfo();
        mUnregisteredTaskInfo.token = mUnregisteredToken;
        // Same id as the other to match pending info id
        mUnregisteredTaskInfo.taskId = 314;
        mUnregisteredTaskInfo.launchCookies.add(mock(IBinder.class));
        mTaskInfo.taskDescription = mock(ActivityManager.TaskDescription.class);

        mBounds = new Rect(0, 0, 100, 100);

        mTaskViewRepository = new TaskViewRepository();
        mTaskViewTransitions = new TaskViewTransitions(mTransitions, mTaskViewRepository,
                mock(ShellTaskOrganizer.class), mock(SyncTransactionQueue.class));
        mTaskViewTransitions.registerTaskView(mTaskViewTaskController);
        when(mTaskViewTaskController.getTaskInfo()).thenReturn(mTaskInfo);
        when(mTaskViewTaskController.getPendingInfo()).thenReturn(mTaskInfo);
        when(mTaskViewTaskController.getTaskToken()).thenReturn(mToken);
        when(mTaskViewTaskController.getSurfaceControl()).thenReturn(mSurfaceControl);
        when(mTaskViewTaskController.prepareOpen(any(), any())).thenReturn(mBounds);

        mPendingFront =
                new TaskViewTransitions.PendingTransition(
                        TRANSIT_TO_FRONT, mock(WindowContainerTransaction.class),
                        mTaskViewTaskController, mLaunchCookie);
        mPendingBack =
                new TaskViewTransitions.PendingTransition(
                        TRANSIT_TO_BACK, mock(WindowContainerTransaction.class),
                        mTaskViewTaskController, mLaunchCookie);

        mFinishCallback = mock(Transitions.TransitionFinishCallback.class);

        mStartTransaction = spy(new StubTransaction());
        mFinishTransaction = spy(new StubTransaction());
    }

    TransitionInfo.Change getTaskView(@TransitionInfo.TransitionMode int type) {
        return getTask(type, true);
    }

    TransitionInfo.Change getTask(@TransitionInfo.TransitionMode int type, boolean registered) {
        TransitionInfo.Change change = mock(TransitionInfo.Change.class);
        when(change.getLeash()).thenReturn(mTaskLeash);
        when(change.getTaskInfo()).thenReturn(registered ? mTaskInfo : mUnregisteredTaskInfo);
        when(change.getMode()).thenReturn(type);
        return change;
    }

    TaskViewTransitions.PendingTransition setPendingTransaction(boolean visible, boolean opening) {
        TaskViewRepository.TaskViewState state =
                mTaskViewRepository.byTaskView(mTaskViewTaskController);
        assertWithMessage("state can't be null here").that(state).isNotNull();
        state.mVisible = !visible;
        state.mBounds = mBounds;
        TaskViewTransitions.PendingTransition pending;
        if (opening) {
            mTaskViewTransitions.startTaskView(mock(WindowContainerTransaction.class),
                    mTaskViewTaskController, mLaunchCookie);
            pending = mTaskViewTransitions.findPending(mTaskViewTaskController, TRANSIT_OPEN);
        } else {
            mTaskViewTransitions.setTaskViewVisible(mTaskViewTaskController, visible);
            pending = mTaskViewTransitions.findPending(
                            mTaskViewTaskController, visible ? TRANSIT_TO_FRONT : TRANSIT_TO_BACK);
        }
        assertWithMessage("pending can't be null").that(pending).isNotNull();
        return pending;
    }

    /**
     * Tests on TransitionDispatchState
     */
    @Test
    public void taskView_dispatchStateFindsIncompatible_animationMode() {
        assumeTrue(enableHandlersDebuggingMode());
        assumeTrue(taskViewTransitionsRefactor()); // To avoid running twice

        TransitionInfo.Change showingTV = getTaskView(TRANSIT_TO_FRONT);
        TransitionInfo.Change nonTV = getTask(TRANSIT_TO_BACK, false /* registered */);
        TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        // Showing taskView + normal task.
        // TaskView is accepted, but normal task is detected as error
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(showingTV)
                .addChange(nonTV)
                .build();

        TransitionDispatchState dispatchState =
                spy(new TransitionDispatchState(pending.mClaimed, info));

        boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                dispatchState, mStartTransaction, mFinishTransaction, mFinishCallback);

        Slog.v(TAG, "DispatchState:\n" + dispatchState.getDebugInfo());
        // Has animated the taskView
        assertWithMessage("Handler should play the transition")
                .that(handled).isTrue();
        ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        assertWithMessage("Expected wct to be created and sent to callback")
                .that(wctCaptor.getValue()).isNotNull();
        verify(pending.mTaskView).notifyAppeared(eq(false));

        // Non task-view spotted as intruder
        verify(dispatchState).addError(eq(mTaskViewTransitions), eq(nonTV),
                eq(CAPTURED_UNRELATED_CHANGE));
        assertThat(dispatchState.hasErrors(mTaskViewTransitions)).isTrue();
    }

    @Test
    public void taskView_dispatchStateFindsCompatible_dataCollectionMode() {
        assumeTrue(enableHandlersDebuggingMode());
        assumeTrue(taskViewTransitionsRefactor()); // To avoid running twice

        TransitionInfo.Change showingTV = getTaskView(TRANSIT_TO_FRONT);
        TransitionInfo.Change nonTV = getTask(TRANSIT_TO_BACK, false /* registered */);
        TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        // Showing taskView + normal task.
        // TaskView is detected as change that could have played
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(showingTV)
                .addChange(nonTV)
                .build();

        TransitionDispatchState dispatchState =
                spy(new TransitionDispatchState(pending.mClaimed, info));

        boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, null,
                dispatchState, mStartTransaction, mFinishTransaction, mFinishCallback);

        Slog.v(TAG, "DispatchState:\n" + dispatchState.getDebugInfo());
        // Has not animated the taskView
        assertWithMessage("Handler should not play the transition")
                .that(handled).isFalse();
        verify(mFinishCallback, never()).onTransitionFinished(any());
        verify(pending.mTaskView, never()).notifyAppeared(anyBoolean());

        // Non task-view spotted as intruder
        verify(dispatchState)
                .addError(eq(mTaskViewTransitions), eq(showingTV), eq(LOST_RELEVANT_CHANGE));
        assertThat(dispatchState.hasErrors(mTaskViewTransitions)).isTrue();
    }

    /**
     * Refactor tests on taskViews
     */
    @Test
    public void hideTaskViewHandled() {
        TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(false /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_BACK)
                .addChange(getTaskView(TRANSIT_TO_BACK)).build();

        boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        verify(mStartTransaction).hide(mTaskLeash);
        verify(mTaskViewTaskController).prepareHideAnimation(mFinishTransaction);
    }

    @Test
    public void removeTaskViewHandled() {
        TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(false /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_BACK)
                .addChange(getTaskView(TRANSIT_CLOSE)).build();

        boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        verify(mTaskViewTaskController).prepareCloseAnimation();
    }

    @Test
    public void openTaskViewHandled() {
        TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, true /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(getTaskView(TRANSIT_OPEN)).build();

        boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        WindowContainerTransaction wct = wctCaptor.getValue();
        prepareOpenAnimationAssertions(pending, wct, true /* newTask */, mTokenBinder);
    }

    @Test
    public void showTaskViewHandled() {
        TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(getTaskView(TRANSIT_TO_FRONT)).build();

        boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        WindowContainerTransaction wct = wctCaptor.getValue();
        prepareOpenAnimationAssertions(pending, wct, false /* newTask */, mTokenBinder);
    }

    @Test
    public void taskToTaskViewHandled() {
        assumeTrue(BubbleAnythingFlagHelper.enableCreateAnyBubble());

        TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(getTask(TRANSIT_TO_FRONT, false /* register */)).build();

        boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        WindowContainerTransaction wct = wctCaptor.getValue();
        prepareOpenAnimationAssertions(pending, wct, true /* newTask */, mUnregisteredTokenBinder);
    }

    @Test
    public void changingTaskViewHandled() {
        TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(getTaskView(TRANSIT_CHANGE)).build();

        boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        WindowContainerTransaction wct = wctCaptor.getValue();
        wctSetBoundsAssertions(wct, mTokenBinder);
    }

    @Test
    public void closingTaskHandled() {
        TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(false /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_BACK)
                .addChange(getTask(TRANSIT_CLOSE, false /* registered */)).build();

        boolean handled = mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        assertWithMessage("Handler should have consumed transition")
                .that(handled).isTrue();
        verify(mTaskViewTaskController, never()).prepareCloseAnimation();
    }

    /**
     * Refactor tests on non-taskViews
     */
    @Test
    public void hidingTaskNotHandled() {
        TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(false /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_BACK)
                .addChange(getTask(TRANSIT_TO_BACK, false /* registered */)).build();

        mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        verify(mStartTransaction, never()).hide(any());
        verify(pending.mTaskView, never()).prepareHideAnimation(any());

        ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        assertWithMessage("No wct should have been created")
                .that(wctCaptor.getValue()).isNull();
    }

    @Test
    public void showingTaskNotHandled() {
        TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                        .addChange(getTask(TRANSIT_TO_FRONT, false /* registered */))
                        .build();
        // Change id to avoid matching pending info, or this would be task->taskView
        mUnregisteredTaskInfo.taskId = 222;

        mTaskViewTransitions.startAnimation(
                pending.mClaimed, info, mStartTransaction, mFinishTransaction, mFinishCallback);
        verify(mTaskViewTaskController, never()).notifyAppeared(anyBoolean());
        ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        assertWithMessage("No wct should have been created")
                .that(wctCaptor.getValue()).isNull();
    }

    @Test
    public void changingTaskNotHandled() {
        TaskViewTransitions.PendingTransition pending =
                setPendingTransaction(true /* visible*/, false /* opening */);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(getTask(TRANSIT_CHANGE, false /* registered */)).build();
        mUnregisteredTaskInfo.taskId = 222;

        mTaskViewTransitions.startAnimation(pending.mClaimed, info,
                mStartTransaction, mFinishTransaction, mFinishCallback);

        verify(mTaskViewTaskController, never()).prepareOpen(any(), any());
        ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mFinishCallback).onTransitionFinished(wctCaptor.capture());
        assertWithMessage("No wct should have been created")
                .that(wctCaptor.getValue()).isNull();
    }

    // assertions to verify TaskViewTransitions.prepareOpenAnimation is called
    private void prepareOpenAnimationAssertions(TaskViewTransitions.PendingTransition pending,
            WindowContainerTransaction wct, boolean newTask, IBinder binder) {
        wctSetBoundsAssertions(wct, binder);
        assertWithMessage("no hierarchyOp set")
                .that(wct.getHierarchyOps()).isNotEmpty();
        assertWithMessage("isTrimmableFromRecents should be set to false")
                .that(wct.getHierarchyOps().getLast().isTrimmableFromRecents()).isFalse();
        verify(pending.mTaskView).notifyAppeared(eq(newTask));
    }

    // assertions to verify wct.setBounds(mToken, mBounds) is called
    private void wctSetBoundsAssertions(WindowContainerTransaction wct, IBinder binder) {
        WindowContainerTransaction.Change change = wct.getChanges().get(binder);
        assertThat(change).isNotNull();
        assertThat(change.getConfiguration().windowConfiguration.getBounds()).isEqualTo(mBounds);

        verify(mStartTransaction).reparent(eq(mTaskLeash), eq(mSurfaceControl));
        verify(mStartTransaction).show(eq(mTaskLeash));
        verify(mFinishTransaction).reparent(eq(mTaskLeash), eq(mSurfaceControl));
        verify(mFinishTransaction).setPosition(eq(mTaskLeash), eq(0.0f), eq(0.0f));
        verify(mFinishTransaction).setWindowCrop(eq(mTaskLeash), eq(100), eq(100));
        verify(mTaskViewTaskController).applyCaptionInsetsIfNeeded();
    }
}
