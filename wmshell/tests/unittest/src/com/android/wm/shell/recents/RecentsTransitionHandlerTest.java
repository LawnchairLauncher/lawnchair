/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.wm.shell.recents;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_SLEEP;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX;
import static com.android.window.flags.Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND;
import static com.android.wm.shell.Flags.FLAG_ENABLE_PIP2;
import static com.android.wm.shell.Flags.FLAG_ENABLE_RECENTS_BOOKEND_TRANSITION;
import static com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_ANIMATING;
import static com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_NOT_RUNNING;
import static com.android.wm.shell.recents.RecentsTransitionStateListener.TRANSITION_STATE_REQUESTED;
import static com.android.wm.shell.transition.Transitions.TRANSIT_END_RECENTS_TRANSITION;
import static com.android.wm.shell.transition.Transitions.TRANSIT_REMOVE_PIP;
import static com.android.wm.shell.transition.Transitions.TRANSIT_START_RECENTS_TRANSITION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.app.IApplicationThread;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserManager;
import android.platform.test.annotations.EnableFlags;
import android.view.SurfaceControl;
import android.window.TransitionInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.os.IResultReceiver;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestRunningTaskInfoBuilder;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.desktopmode.DesktopRepository;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer;
import com.android.wm.shell.shared.R;
import com.android.wm.shell.shared.desktopmode.FakeDesktopState;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.HomeTransitionObserver;
import com.android.wm.shell.transition.TransitionInfoBuilder;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.util.StubTransaction;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

/**
 * Tests for {@link RecentTasksController}
 *
 * Usage: atest WMShellUnitTests:RecentsTransitionHandlerTest
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class RecentsTransitionHandlerTest extends ShellTestCase {

    private static final int FREEFORM_TASK_CORNER_RADIUS = 32;
    private static final int FREEFORM_TASK_CORNER_RADIUS_ON_CD = 24;
    private static final int CONNECTED_DISPLAY_ID = 1;

    @Mock
    private Context mContext;
    @Mock
    private Resources mResources;
    @Mock
    private TaskStackListenerImpl mTaskStackListener;
    @Mock
    private ShellCommandHandler mShellCommandHandler;
    @Mock
    private DesktopUserRepositories mDesktopUserRepositories;
    @Mock
    private ActivityTaskManager mActivityTaskManager;
    @Mock
    private DisplayInsetsController mDisplayInsetsController;
    @Mock
    private TaskStackTransitionObserver mTaskStackTransitionObserver;
    @Mock
    private Transitions mTransitions;
    @Mock
    private UserManager mUserManager;
    @Mock
    private DesksOrganizer mDesksOrganizer;

    @Mock private DesktopRepository mDesktopRepository;
    @Mock private DisplayController mDisplayController;
    @Mock private Context mConnectedDisplayContext;
    @Mock private Resources mConnectedDisplayResources;

    private ShellTaskOrganizer mShellTaskOrganizer;
    private RecentTasksController mRecentTasksController;
    private RecentTasksController mRecentTasksControllerReal;
    private RecentsTransitionHandler mRecentsTransitionHandler;
    private ShellInit mShellInit;
    private ShellController mShellController;
    private TestShellExecutor mMainExecutor;
    private AutoCloseable mMocksInit = null;

    @Before
    public void setUp() {
        var desktopState = new FakeDesktopState();
        desktopState.setCanEnterDesktopMode(true);

        mMocksInit = MockitoAnnotations.openMocks(this);

        when(mDesktopUserRepositories.getCurrent()).thenReturn(mDesktopRepository);
        mMainExecutor = new TestShellExecutor();
        when(mContext.getPackageManager()).thenReturn(mock(PackageManager.class));
        when(mContext.getSystemService(KeyguardManager.class))
                .thenReturn(mock(KeyguardManager.class));
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getDimensionPixelSize(
                R.dimen.desktop_windowing_freeform_rounded_corner_radius)
        ).thenReturn(FREEFORM_TASK_CORNER_RADIUS);
        when(mDisplayController.getDisplayContext(CONNECTED_DISPLAY_ID)).thenReturn(
                mConnectedDisplayContext);
        when(mConnectedDisplayContext.getResources()).thenReturn(mConnectedDisplayResources);
        when(mConnectedDisplayResources.getDimensionPixelSize(
                R.dimen.desktop_windowing_freeform_rounded_corner_radius)
        ).thenReturn(FREEFORM_TASK_CORNER_RADIUS_ON_CD);
        mShellInit = spy(new ShellInit(mMainExecutor));
        mShellController = spy(new ShellController(mContext, mShellInit, mShellCommandHandler,
                mDisplayInsetsController, mUserManager, mMainExecutor));
        mRecentTasksControllerReal = new RecentTasksController(mContext, mShellInit,
                mShellController, mShellCommandHandler, mTaskStackListener, mActivityTaskManager,
                Optional.of(mDesktopUserRepositories), mTaskStackTransitionObserver,
                mMainExecutor, desktopState);
        mRecentTasksController = spy(mRecentTasksControllerReal);
        mShellTaskOrganizer = new ShellTaskOrganizer(mShellInit, mShellCommandHandler,
                null /* sizeCompatUI */, Optional.empty(), Optional.of(mRecentTasksController),
                mMainExecutor);

        doReturn(mMainExecutor).when(mTransitions).getMainExecutor();
        mRecentsTransitionHandler = new RecentsTransitionHandler(mShellInit, mShellTaskOrganizer,
                mTransitions, mRecentTasksController, mock(HomeTransitionObserver.class),
                mDisplayController, mDesksOrganizer);
        // By default use a mock finish transaction since we are sending transitions that don't have
        // real surface controls
        mRecentsTransitionHandler.setFinishTransactionSupplier(
                () -> mock(SurfaceControl.Transaction.class));

        mShellInit.init();
    }

    @After
    public void tearDown() throws Exception {
        if (mMocksInit != null) {
            mMocksInit.close();
            mMocksInit = null;
        }
    }

    @Test
    public void testStartSyntheticRecentsTransition_callsOnAnimationStartAndFinishCallback() throws Exception {
        final IRecentsAnimationRunner runner = mock(IRecentsAnimationRunner.class);
        final IResultReceiver finishCallback = mock(IResultReceiver.class);

        final IBinder transition = startRecentsTransition(/* synthetic= */ true, runner);
        verify(runner).onAnimationStart(any(), any(), any(), any(), any(), any(), any());

        // Finish and verify no transition remains and that the provided finish callback is called
        mRecentsTransitionHandler.findController(transition).finish(true /* toHome */,
                false /* sendUserLeaveHint */, finishCallback);
        mMainExecutor.flushAll();
        verify(finishCallback).send(anyInt(), any());
        assertNull(mRecentsTransitionHandler.findController(transition));
    }

    @Test
    public void testStartSyntheticRecentsTransition_callsOnAnimationCancel() throws Exception {
        final IRecentsAnimationRunner runner = mock(IRecentsAnimationRunner.class);

        final IBinder transition = startRecentsTransition(/* synthetic= */ true, runner);
        verify(runner).onAnimationStart(any(), any(), any(), any(), any(), any(), any());

        mRecentsTransitionHandler.findController(transition).cancel("test");
        mMainExecutor.flushAll();
        verify(runner).onAnimationCanceled(any(), any());
        assertNull(mRecentsTransitionHandler.findController(transition));
    }

    @Test
    public void testStartTransition_updatesStateListeners() {
        final TestTransitionStateListener listener = new TestTransitionStateListener();
        mRecentsTransitionHandler.addTransitionStateListener(listener);

        startRecentsTransition(/* synthetic= */ false);
        mMainExecutor.flushAll();

        assertThat(listener.getState()).isEqualTo(TRANSITION_STATE_REQUESTED);
    }

    @Test
    public void testStartAnimation_updatesStateListeners() {
        final TestTransitionStateListener listener = new TestTransitionStateListener();
        mRecentsTransitionHandler.addTransitionStateListener(listener);

        final IBinder transition = startRecentsTransition(/* synthetic= */ false);
        mRecentsTransitionHandler.startAnimation(
                transition, createTransitionInfo(), new StubTransaction(), new StubTransaction(),
                mock(Transitions.TransitionFinishCallback.class));
        mMainExecutor.flushAll();

        assertThat(listener.getState()).isEqualTo(TRANSITION_STATE_ANIMATING);
    }

    @Test
    public void testFinishTransition_updatesStateListeners() {
        final TestTransitionStateListener listener = new TestTransitionStateListener();
        mRecentsTransitionHandler.addTransitionStateListener(listener);

        final IBinder transition = startRecentsTransition(/* synthetic= */ false);
        mRecentsTransitionHandler.startAnimation(
                transition, createTransitionInfo(), new StubTransaction(), new StubTransaction(),
                mock(Transitions.TransitionFinishCallback.class));
        mRecentsTransitionHandler.findController(transition).finish(true /* toHome */,
                false /* sendUserLeaveHint */, mock(IResultReceiver.class));
        mMainExecutor.flushAll();

        assertThat(listener.getState()).isEqualTo(TRANSITION_STATE_NOT_RUNNING);
    }

    @Test
    public void testCancelTransition_updatesStateListeners() {
        final TestTransitionStateListener listener = new TestTransitionStateListener();
        mRecentsTransitionHandler.addTransitionStateListener(listener);

        final IBinder transition = startRecentsTransition(/* synthetic= */ false);
        mRecentsTransitionHandler.findController(transition).cancel("test");
        mMainExecutor.flushAll();

        assertThat(listener.getState()).isEqualTo(TRANSITION_STATE_NOT_RUNNING);
    }

    @Test
    public void testStartAnimation_synthetic_updatesStateListeners() {
        final TestTransitionStateListener listener = new TestTransitionStateListener();
        mRecentsTransitionHandler.addTransitionStateListener(listener);

        startRecentsTransition(/* synthetic= */ true);
        mMainExecutor.flushAll();

        assertThat(listener.getState()).isEqualTo(TRANSITION_STATE_ANIMATING);
    }

    @Test
    public void testFinishTransition_synthetic_updatesStateListeners() {
        final TestTransitionStateListener listener = new TestTransitionStateListener();
        mRecentsTransitionHandler.addTransitionStateListener(listener);

        final IBinder transition = startRecentsTransition(/* synthetic= */ true);
        mRecentsTransitionHandler.findController(transition).finish(true /* toHome */,
                false /* sendUserLeaveHint */, mock(IResultReceiver.class));
        mMainExecutor.flushAll();

        assertThat(listener.getState()).isEqualTo(TRANSITION_STATE_NOT_RUNNING);
    }

    @Test
    public void testCancelTransition_synthetic_updatesStateListeners() {
        final TestTransitionStateListener listener = new TestTransitionStateListener();
        mRecentsTransitionHandler.addTransitionStateListener(listener);

        final IBinder transition = startRecentsTransition(/* synthetic= */ true);
        mRecentsTransitionHandler.findController(transition).cancel("test");
        mMainExecutor.flushAll();

        assertThat(listener.getState()).isEqualTo(TRANSITION_STATE_NOT_RUNNING);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX)
    public void testMerge_openingTasks_callsOnTasksAppeared() throws Exception {
        final IRecentsAnimationRunner animationRunner = mock(IRecentsAnimationRunner.class);
        TransitionInfo mergeTransitionInfo = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, new TestRunningTaskInfoBuilder().build())
                .build();
        final IBinder transition = startRecentsTransition(/* synthetic= */ false, animationRunner);
        mRecentsTransitionHandler.startAnimation(
                transition, createTransitionInfo(), new StubTransaction(), new StubTransaction(),
                mock(Transitions.TransitionFinishCallback.class));

        mRecentsTransitionHandler.findController(transition).merge(
                mergeTransitionInfo,
                new StubTransaction(),
                new StubTransaction(),
                mock(Transitions.TransitionFinishCallback.class));
        mMainExecutor.flushAll();

        verify(animationRunner).onTasksAppeared(
                /* appearedTargets= */ any(), eq(mergeTransitionInfo));
    }

    @Test
    @EnableFlags(FLAG_ENABLE_RECENTS_BOOKEND_TRANSITION)
    public void testMerge_consumeBookendTransition() throws Exception {
        // Start and finish the transition
        final IRecentsAnimationRunner animationRunner = mock(IRecentsAnimationRunner.class);
        final IBinder transition = startRecentsTransition(/* synthetic= */ false, animationRunner);
        mRecentsTransitionHandler.startAnimation(
                transition, createTransitionInfo(), new StubTransaction(), new StubTransaction(),
                mock(Transitions.TransitionFinishCallback.class));
        mRecentsTransitionHandler.findController(transition).finish(/* toHome= */ false,
                false /* sendUserLeaveHint */, mock(IResultReceiver.class));
        mMainExecutor.flushAll();

        // Merge the bookend transition
        TransitionInfo mergeTransitionInfo =
                new TransitionInfoBuilder(TRANSIT_END_RECENTS_TRANSITION)
                        .addChange(TRANSIT_OPEN, new TestRunningTaskInfoBuilder().build())
                        .build();
        SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        Transitions.TransitionFinishCallback finishCallback
                = mock(Transitions.TransitionFinishCallback.class);
        mRecentsTransitionHandler.findController(transition).merge(
                mergeTransitionInfo,
                new StubTransaction(),
                finishT,
                finishCallback);
        mMainExecutor.flushAll();

        // Verify that we've merged
        verify(finishCallback).onTransitionFinished(any());
    }

    @Test
    @EnableFlags(FLAG_ENABLE_RECENTS_BOOKEND_TRANSITION)
    public void testMerge_pendingBookendTransition_mergesTransition() throws Exception {
        // Start and finish the transition
        final IRecentsAnimationRunner animationRunner = mock(IRecentsAnimationRunner.class);
        final IBinder transition = startRecentsTransition(/* synthetic= */ false, animationRunner);
        mRecentsTransitionHandler.startAnimation(
                transition, createTransitionInfo(), new StubTransaction(), new StubTransaction(),
                mock(Transitions.TransitionFinishCallback.class));
        mRecentsTransitionHandler.findController(transition).finish(/* toHome= */ false,
                false /* sendUserLeaveHint */, mock(IResultReceiver.class));
        mMainExecutor.flushAll();

        // Merge a new transition while we have a pending finish
        TransitionInfo mergeTransitionInfo = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, new TestRunningTaskInfoBuilder().build())
                .build();
        SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        Transitions.TransitionFinishCallback finishCallback
                = mock(Transitions.TransitionFinishCallback.class);
        mRecentsTransitionHandler.findController(transition).merge(
                mergeTransitionInfo,
                new StubTransaction(),
                finishT,
                finishCallback);
        mMainExecutor.flushAll();

        // Verify that we've cleaned up the original transition
        assertNull(mRecentsTransitionHandler.findController(transition));
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX)
    public void testMergeAndFinish_openingFreeformTasks_setsCornerRadius() {
        ActivityManager.RunningTaskInfo freeformTask =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        TransitionInfo mergeTransitionInfo = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, freeformTask)
                .build();
        SurfaceControl leash = mergeTransitionInfo.getChanges().get(0).getLeash();
        final IBinder transition = startRecentsTransition(/* synthetic= */ false);
        SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mRecentsTransitionHandler.setFinishTransactionSupplier(() -> finishT);
        mRecentsTransitionHandler.startAnimation(
                transition, createTransitionInfo(), new StubTransaction(), new StubTransaction(),
                mock(Transitions.TransitionFinishCallback.class));

        mRecentsTransitionHandler.findController(transition).merge(
                mergeTransitionInfo,
                new StubTransaction(),
                finishT,
                mock(Transitions.TransitionFinishCallback.class));
        mRecentsTransitionHandler.findController(transition).finish(/* toHome= */ false,
                false /* sendUserLeaveHint */, mock(IResultReceiver.class));
        mMainExecutor.flushAll();

        verify(finishT).setCornerRadius(leash, FREEFORM_TASK_CORNER_RADIUS);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX)
    public void testFinish_returningToFreeformTasks_setsCornerRadius() {
        ActivityManager.RunningTaskInfo freeformTask =
                new TestRunningTaskInfoBuilder().setWindowingMode(WINDOWING_MODE_FREEFORM).build();
        TransitionInfo transitionInfo = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_CLOSE, freeformTask)
                .build();
        SurfaceControl leash = transitionInfo.getChanges().get(0).getLeash();
        final IBinder transition = startRecentsTransition(/* synthetic= */ false);
        SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mRecentsTransitionHandler.setFinishTransactionSupplier(() -> finishT);
        mRecentsTransitionHandler.startAnimation(
                transition, transitionInfo, new StubTransaction(),
                new StubTransaction(),
                mock(Transitions.TransitionFinishCallback.class));

        mRecentsTransitionHandler.findController(transition).finish(/* toHome= */ false,
                false /* sendUserLeaveHint */, mock(IResultReceiver.class));
        mMainExecutor.flushAll();


        verify(finishT).setCornerRadius(leash, FREEFORM_TASK_CORNER_RADIUS);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_RECENTS_TRANSITIONS_CORNERS_BUGFIX)
    public void testFinish_returningToFreeformTasks_setsCornerRadiusOnConnectedDisplay() {
        ActivityManager.RunningTaskInfo freeformTask =
                new TestRunningTaskInfoBuilder().setWindowingMode(
                        WINDOWING_MODE_FREEFORM).setDisplayId(CONNECTED_DISPLAY_ID).build();
        TransitionInfo transitionInfo = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_CLOSE, freeformTask)
                .build();
        SurfaceControl leash = transitionInfo.getChanges().get(0).getLeash();
        final IBinder transition = startRecentsTransition(/* synthetic= */ false);
        SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mRecentsTransitionHandler.setFinishTransactionSupplier(() -> finishT);
        mRecentsTransitionHandler.startAnimation(
                transition, transitionInfo, new StubTransaction(),
                new StubTransaction(),
                mock(Transitions.TransitionFinishCallback.class));

        mRecentsTransitionHandler.findController(transition).finish(/* toHome= */ false,
                false /* sendUserLeaveHint */, mock(IResultReceiver.class));
        mMainExecutor.flushAll();


        verify(finishT).setCornerRadius(leash, FREEFORM_TASK_CORNER_RADIUS_ON_CD);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_RECENTS_BOOKEND_TRANSITION)
    public void testMerge_cancelToHome_onTransitSleep() throws Exception {
        TransitionInfo mergeTransitionInfo = new TransitionInfoBuilder(TRANSIT_SLEEP)
                .build();
        startTransitionAndMergeThenVerifyCanceled(mergeTransitionInfo);
    }

    @Test
    @EnableFlags({FLAG_ENABLE_RECENTS_BOOKEND_TRANSITION, FLAG_ENABLE_PIP2})
    public void testMerge_cancelToHome_onTransitRemovePip() throws Exception {
        TransitionInfo mergeTransitionInfo = new TransitionInfoBuilder(TRANSIT_REMOVE_PIP)
                .build();
        startTransitionAndMergeThenVerifyCanceled(mergeTransitionInfo);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    public void testMergeAndFinish_openingTaskInDesk_setsPositionOfChild() {
        ActivityManager.RunningTaskInfo deskRootTask =
                new TestRunningTaskInfoBuilder()
                        .setWindowingMode(WINDOWING_MODE_FREEFORM)
                        .build();
        ActivityManager.RunningTaskInfo deskChildTask =
                new TestRunningTaskInfoBuilder()
                        .setWindowingMode(WINDOWING_MODE_FREEFORM)
                        .setParentTaskId(deskRootTask.taskId)
                        .build();
        TransitionInfo mergeTransitionInfo = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, deskChildTask)
                .addChange(TRANSIT_OPEN, deskRootTask)
                .build();
        SurfaceControl deskChildLeash = mergeTransitionInfo.getChanges().get(0).getLeash();
        final IBinder transition = startRecentsTransition(/* synthetic= */ false);
        SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mRecentsTransitionHandler.setFinishTransactionSupplier(() -> finishT);
        mRecentsTransitionHandler.startAnimation(
                transition, createTransitionInfo(), new StubTransaction(), new StubTransaction(),
                mock(Transitions.TransitionFinishCallback.class));

        mRecentsTransitionHandler.findController(transition).merge(
                mergeTransitionInfo,
                startT,
                finishT,
                mock(Transitions.TransitionFinishCallback.class));
        mRecentsTransitionHandler.findController(transition).finish(/* toHome= */ false,
                false /* sendUserLeaveHint */, mock(IResultReceiver.class));
        mMainExecutor.flushAll();

        verify(startT).setPosition(deskChildLeash, /* x= */ 0, /* y= */0);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND)
    public void testMergeAndFinish_openingTaskInDeskWithSiblings_reordersAllToTop() {
        ActivityManager.RunningTaskInfo deskRootTask =
                new TestRunningTaskInfoBuilder()
                        .setWindowingMode(WINDOWING_MODE_FREEFORM)
                        .build();
        ActivityManager.RunningTaskInfo deskChildTask1 =
                new TestRunningTaskInfoBuilder()
                        .setWindowingMode(WINDOWING_MODE_FREEFORM)
                        .setParentTaskId(deskRootTask.taskId)
                        .build();
        ActivityManager.RunningTaskInfo deskChildTask2 =
                new TestRunningTaskInfoBuilder()
                        .setWindowingMode(WINDOWING_MODE_FREEFORM)
                        .setParentTaskId(deskRootTask.taskId)
                        .build();
        TransitionInfo startTransitionInfo = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_TO_BACK, deskChildTask1)
                .addChange(TRANSIT_TO_BACK, deskChildTask2)
                .addChange(TRANSIT_TO_BACK, deskRootTask)
                .build();
        TransitionInfo mergeTransitionInfo = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, deskChildTask2)
                .addChange(TRANSIT_OPEN, deskRootTask)
                .build();
        final IBinder transition = startRecentsTransition(/* synthetic= */ false);
        SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mRecentsTransitionHandler.setFinishTransactionSupplier(() -> finishT);
        mRecentsTransitionHandler.startAnimation(
                transition, startTransitionInfo, new StubTransaction(), new StubTransaction(),
                mock(Transitions.TransitionFinishCallback.class));

        mRecentsTransitionHandler.findController(transition).merge(
                mergeTransitionInfo,
                new StubTransaction(),
                finishT,
                mock(Transitions.TransitionFinishCallback.class));
        mRecentsTransitionHandler.findController(transition).finish(/* toHome= */ false,
                false /* sendUserLeaveHint */, mock(IResultReceiver.class));
        mMainExecutor.flushAll();

        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mTransitions)
                .startTransition(eq(TRANSIT_END_RECENTS_TRANSITION), wctCaptor.capture(), any());
        final WindowContainerTransaction wct = wctCaptor.getValue();
        assertNotNull(wct);
        // Task 2 was opened, so it should be on top.
        assertReorderInOrder(wct, new ArrayList<>(Arrays.asList(deskChildTask1, deskChildTask2)));
        // Both should be shown.
        SurfaceControl deskChild1Leash = startTransitionInfo.getChanges().get(0).getLeash();
        SurfaceControl deskChild2Leash = startTransitionInfo.getChanges().get(1).getLeash();
        verify(finishT).show(deskChild1Leash);
        verify(finishT).show(deskChild2Leash);
    }

    private void startTransitionAndMergeThenVerifyCanceled(TransitionInfo mergeTransition)
            throws Exception {
        final IRecentsAnimationRunner animationRunner = mock(IRecentsAnimationRunner.class);
        final IBinder transition = startRecentsTransition(/* synthetic= */ false, animationRunner);
        mRecentsTransitionHandler.startAnimation(
                transition, createTransitionInfo(), new StubTransaction(), new StubTransaction(),
                mock(Transitions.TransitionFinishCallback.class));

        mRecentsTransitionHandler.findController(transition).merge(
                mergeTransition,
                new StubTransaction(),
                new StubTransaction(),
                mock(Transitions.TransitionFinishCallback.class));
        mMainExecutor.flushAll();

        // Verify that the runner was notified and that the cancel immediately took effect (and the
        // transition is finished)
        verify(animationRunner).onAnimationCanceled(any(), any());
        assertThat(mRecentsTransitionHandler.findController(transition)).isNull();
    }

    private IBinder startRecentsTransition(boolean synthetic) {
        return startRecentsTransition(synthetic, mock(IRecentsAnimationRunner.class));
    }

    private IBinder startRecentsTransition(boolean synthetic,
            @NonNull IRecentsAnimationRunner runner) {
        doReturn(new Binder()).when(runner).asBinder();
        final Bundle options = new Bundle();
        options.putBoolean("is_synthetic_recents_transition", synthetic);
        final IBinder transition = new Binder();
        when(mTransitions.startTransition(anyInt(), any(), any())).thenReturn(transition);
        return mRecentsTransitionHandler.startRecentsTransition(
                mock(PendingIntent.class), new Intent(), options, null /* wct */,
                mock(IApplicationThread.class), runner);
    }

    private TransitionInfo createTransitionInfo() {
        final ActivityManager.RunningTaskInfo homeTask = new TestRunningTaskInfoBuilder()
                .setTopActivityType(ACTIVITY_TYPE_HOME)
                .build();
        final ActivityManager.RunningTaskInfo appTask = new TestRunningTaskInfoBuilder()
                .setTopActivityType(ACTIVITY_TYPE_STANDARD)
                .build();
        final TransitionInfo.Change homeChange = new TransitionInfo.Change(
                homeTask.token, new SurfaceControl());
        homeChange.setMode(TRANSIT_TO_FRONT);
        homeChange.setTaskInfo(homeTask);
        final TransitionInfo.Change appChange = new TransitionInfo.Change(
                appTask.token, new SurfaceControl());
        appChange.setMode(TRANSIT_TO_FRONT);
        appChange.setTaskInfo(appTask);
        return new TransitionInfoBuilder(TRANSIT_START_RECENTS_TRANSITION)
                .addChange(homeChange)
                .addChange(appChange)
                .build();
    }

    private void assertReorderInOrder(@NonNull WindowContainerTransaction wct,
            ArrayList<ActivityManager.RunningTaskInfo> tasks) {
        for (WindowContainerTransaction.HierarchyOp op : wct.getHierarchyOps()) {
            if (tasks.isEmpty()) break;
            if (op.getType() == WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REORDER
                    && op.getToTop() && op.getContainer().equals(tasks.get(0).token.asBinder())) {
                tasks.removeFirst();
            }
        }
        assertTrue("Not all tasks were reordered to front in order", tasks.isEmpty());
    }

    private static class TestTransitionStateListener implements RecentsTransitionStateListener {
        @RecentsTransitionState
        private int mState = TRANSITION_STATE_NOT_RUNNING;

        @Override
        public void onTransitionStateChanged(int state) {
            mState = state;
        }

        @RecentsTransitionState
        int getState() {
            return mState;
        }
    }
}
