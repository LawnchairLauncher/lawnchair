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

package com.android.wm.shell.taskview;

import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Looper;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceSession;
import android.view.ViewTreeObserver;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestHandler;
import com.android.wm.shell.common.HandlerExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.SyncTransactionQueue.TransactionRunnable;
import com.android.wm.shell.transition.Transitions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

@SmallTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class TaskViewTest extends ShellTestCase {

    @Mock
    TaskView.Listener mViewListener;
    @Mock
    ActivityManager.RunningTaskInfo mTaskInfo;
    @Mock
    WindowContainerToken mToken;
    @Mock
    ShellTaskOrganizer mOrganizer;
    @Mock
    HandlerExecutor mExecutor;
    @Mock
    SyncTransactionQueue mSyncQueue;
    @Mock
    Transitions mTransitions;
    @Mock
    Looper mViewLooper;
    TestHandler mViewHandler;

    SurfaceSession mSession;
    SurfaceControl mLeash;

    Context mContext;
    TaskView mTaskView;
    TaskViewTransitions mTaskViewTransitions;
    TaskViewTaskController mTaskViewTaskController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLeash = new SurfaceControl.Builder(mSession)
                .setName("test")
                .build();

        mContext = getContext();
        doReturn(true).when(mViewLooper).isCurrentThread();
        mViewHandler = spy(new TestHandler(mViewLooper));

        mTaskInfo = new ActivityManager.RunningTaskInfo();
        mTaskInfo.token = mToken;
        mTaskInfo.taskId = 314;
        mTaskInfo.taskDescription = mock(ActivityManager.TaskDescription.class);

        doAnswer((InvocationOnMock invocationOnMock) -> {
            final Runnable r = invocationOnMock.getArgument(0);
            r.run();
            return null;
        }).when(mExecutor).execute(any());

        when(mOrganizer.getExecutor()).thenReturn(mExecutor);

        doAnswer((InvocationOnMock invocationOnMock) -> {
            final TransactionRunnable r = invocationOnMock.getArgument(0);
            r.runWithTransaction(new SurfaceControl.Transaction());
            return null;
        }).when(mSyncQueue).runInSync(any());

        if (Transitions.ENABLE_SHELL_TRANSITIONS) {
            doReturn(true).when(mTransitions).isRegistered();
        }
        mTaskViewTransitions = spy(new TaskViewTransitions(mTransitions));
        mTaskViewTaskController = spy(new TaskViewTaskController(mContext, mOrganizer,
                mTaskViewTransitions, mSyncQueue));
        mTaskView = new TaskView(mContext, mTaskViewTaskController);
        mTaskView.setHandler(mViewHandler);
        mTaskView.setListener(mExecutor, mViewListener);
    }

    @After
    public void tearDown() {
        if (mTaskView != null) {
            mTaskView.release();
        }
    }

    @Test
    public void testSetPendingListener_throwsException() {
        TaskView taskView = new TaskView(mContext,
                new TaskViewTaskController(mContext, mOrganizer, mTaskViewTransitions, mSyncQueue));
        taskView.setListener(mExecutor, mViewListener);
        try {
            taskView.setListener(mExecutor, mViewListener);
        } catch (IllegalStateException e) {
            // pass
            return;
        }
        fail("Expected IllegalStateException");
    }

    @Test
    public void testStartActivity() {
        ActivityOptions options = ActivityOptions.makeBasic();
        mTaskView.startActivity(mock(PendingIntent.class), null, options,
                new Rect(0, 0, 100, 100));

        verify(mOrganizer).setPendingLaunchCookieListener(any(), eq(mTaskViewTaskController));
        assertThat(options.getLaunchWindowingMode()).isEqualTo(WINDOWING_MODE_MULTI_WINDOW);
    }

    @Test
    public void testOnTaskAppeared_noSurface_legacyTransitions() {
        assumeFalse(Transitions.ENABLE_SHELL_TRANSITIONS);
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);

        verify(mViewListener).onTaskCreated(eq(mTaskInfo.taskId), any());
        verify(mViewListener, never()).onInitialized();
        // If there's no surface the task should be made invisible
        verify(mViewListener).onTaskVisibilityChanged(eq(mTaskInfo.taskId), eq(false));
    }

    @Test
    public void testOnTaskAppeared_withSurface_legacyTransitions() {
        assumeFalse(Transitions.ENABLE_SHELL_TRANSITIONS);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);

        verify(mViewListener).onTaskCreated(eq(mTaskInfo.taskId), any());
        assertThat(mTaskView.isInitialized()).isTrue();
        verify(mViewListener, never()).onTaskVisibilityChanged(anyInt(), anyBoolean());
    }

    @Test
    public void testSurfaceCreated_noTask_legacyTransitions() {
        assumeFalse(Transitions.ENABLE_SHELL_TRANSITIONS);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));

        verify(mViewListener).onInitialized();
        assertThat(mTaskView.isInitialized()).isTrue();
        // No task, no visibility change
        verify(mViewListener, never()).onTaskVisibilityChanged(anyInt(), anyBoolean());
    }

    @Test
    public void testSurfaceCreated_withTask_legacyTransitions() {
        assumeFalse(Transitions.ENABLE_SHELL_TRANSITIONS);
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));

        verify(mViewListener).onInitialized();
        assertThat(mTaskView.isInitialized()).isTrue();
        verify(mViewListener).onTaskVisibilityChanged(eq(mTaskInfo.taskId), eq(true));
    }

    @Test
    public void testSurfaceDestroyed_noTask_legacyTransitions() {
        assumeFalse(Transitions.ENABLE_SHELL_TRANSITIONS);
        SurfaceHolder sh = mock(SurfaceHolder.class);
        mTaskView.surfaceCreated(sh);
        mTaskView.surfaceDestroyed(sh);

        verify(mViewListener, never()).onTaskVisibilityChanged(anyInt(), anyBoolean());
    }

    @Test
    public void testSurfaceDestroyed_withTask_shouldNotHideTask_legacyTransitions() {
        assumeFalse(Transitions.ENABLE_SHELL_TRANSITIONS);
        mTaskViewTaskController.setHideTaskWithSurface(false);

        SurfaceHolder sh = mock(SurfaceHolder.class);
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);
        mTaskView.surfaceCreated(sh);
        reset(mViewListener);
        mTaskView.surfaceDestroyed(sh);

        verify(mViewListener, never()).onTaskVisibilityChanged(anyInt(), anyBoolean());
    }

    @Test
    public void testSurfaceDestroyed_withTask_legacyTransitions() {
        assumeFalse(Transitions.ENABLE_SHELL_TRANSITIONS);
        SurfaceHolder sh = mock(SurfaceHolder.class);
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);
        mTaskView.surfaceCreated(sh);
        reset(mViewListener);
        mTaskView.surfaceDestroyed(sh);

        verify(mViewListener).onTaskVisibilityChanged(eq(mTaskInfo.taskId), eq(false));
    }

    @Test
    public void testOnReleased_legacyTransitions() {
        assumeFalse(Transitions.ENABLE_SHELL_TRANSITIONS);
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        mTaskView.release();

        verify(mOrganizer).removeListener(eq(mTaskViewTaskController));
        verify(mViewListener).onReleased();
        assertThat(mTaskView.isInitialized()).isFalse();
    }

    @Test
    public void testOnTaskVanished_legacyTransitions() {
        assumeFalse(Transitions.ENABLE_SHELL_TRANSITIONS);
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        mTaskViewTaskController.onTaskVanished(mTaskInfo);

        verify(mViewListener).onTaskRemovalStarted(eq(mTaskInfo.taskId));
    }

    @Test
    public void testOnBackPressedOnTaskRoot_legacyTransitions() {
        assumeFalse(Transitions.ENABLE_SHELL_TRANSITIONS);
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);
        mTaskViewTaskController.onBackPressedOnTaskRoot(mTaskInfo);

        verify(mViewListener).onBackPressedOnTaskRoot(eq(mTaskInfo.taskId));
    }

    @Test
    public void testSetOnBackPressedOnTaskRoot_legacyTransitions() {
        assumeFalse(Transitions.ENABLE_SHELL_TRANSITIONS);
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);
        verify(mOrganizer).setInterceptBackPressedOnTaskRoot(eq(mTaskInfo.token), eq(true));
    }

    @Test
    public void testUnsetOnBackPressedOnTaskRoot_legacyTransitions() {
        assumeFalse(Transitions.ENABLE_SHELL_TRANSITIONS);
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);
        verify(mOrganizer).setInterceptBackPressedOnTaskRoot(eq(mTaskInfo.token), eq(true));

        mTaskViewTaskController.onTaskVanished(mTaskInfo);
        verify(mOrganizer).setInterceptBackPressedOnTaskRoot(eq(mTaskInfo.token), eq(false));
    }

    @Test
    public void testOnNewTask_noSurface() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);

        verify(mViewListener).onTaskCreated(eq(mTaskInfo.taskId), any());
        verify(mViewListener, never()).onInitialized();
        assertThat(mTaskView.isInitialized()).isFalse();
        // If there's no surface the task should be made invisible
        verify(mViewListener).onTaskVisibilityChanged(eq(mTaskInfo.taskId), eq(false));
    }

    @Test
    public void testSurfaceCreated_noTask() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        verify(mTaskViewTransitions, never()).setTaskViewVisible(any(), anyBoolean());

        verify(mViewListener).onInitialized();
        assertThat(mTaskView.isInitialized()).isTrue();
        // No task, no visibility change
        verify(mViewListener, never()).onTaskVisibilityChanged(anyInt(), anyBoolean());
    }

    @Test
    public void testOnNewTask_withSurface() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);

        verify(mViewListener).onTaskCreated(eq(mTaskInfo.taskId), any());
        verify(mViewListener, never()).onTaskVisibilityChanged(anyInt(), anyBoolean());
    }

    @Test
    public void testSurfaceCreated_withTask() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));

        verify(mViewListener).onInitialized();
        verify(mTaskViewTransitions).setTaskViewVisible(eq(mTaskViewTaskController), eq(true));

        mTaskViewTaskController.prepareOpenAnimation(false /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);

        verify(mViewListener).onTaskVisibilityChanged(eq(mTaskInfo.taskId), eq(true));
    }

    @Test
    public void testSurfaceDestroyed_noTask() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);
        SurfaceHolder sh = mock(SurfaceHolder.class);
        mTaskView.surfaceCreated(sh);
        mTaskView.surfaceDestroyed(sh);

        verify(mViewListener, never()).onTaskVisibilityChanged(anyInt(), anyBoolean());
    }

    @Test
    public void testSurfaceDestroyed_withTask() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);
        SurfaceHolder sh = mock(SurfaceHolder.class);
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        mTaskView.surfaceCreated(sh);
        reset(mViewListener);
        mTaskView.surfaceDestroyed(sh);

        verify(mTaskViewTransitions).setTaskViewVisible(eq(mTaskViewTaskController), eq(false));

        mTaskViewTaskController.prepareHideAnimation(new SurfaceControl.Transaction());

        verify(mViewListener).onTaskVisibilityChanged(eq(mTaskInfo.taskId), eq(false));
    }

    @Test
    public void testOnReleased() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        mTaskView.release();

        verify(mOrganizer).removeListener(eq(mTaskViewTaskController));
        verify(mViewListener).onReleased();
        assertThat(mTaskView.isInitialized()).isFalse();
        verify(mTaskViewTransitions).removeTaskView(eq(mTaskViewTaskController));
    }

    @Test
    public void testOnTaskVanished() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        mTaskViewTaskController.prepareCloseAnimation();

        verify(mViewListener).onTaskRemovalStarted(eq(mTaskInfo.taskId));
    }

    @Test
    public void testOnBackPressedOnTaskRoot() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        mTaskViewTaskController.onBackPressedOnTaskRoot(mTaskInfo);

        verify(mViewListener).onBackPressedOnTaskRoot(eq(mTaskInfo.taskId));
    }

    @Test
    public void testSetOnBackPressedOnTaskRoot() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        verify(mOrganizer).setInterceptBackPressedOnTaskRoot(eq(mTaskInfo.token), eq(true));
    }

    @Test
    public void testUnsetOnBackPressedOnTaskRoot() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        verify(mOrganizer).setInterceptBackPressedOnTaskRoot(eq(mTaskInfo.token), eq(true));

        mTaskViewTaskController.prepareCloseAnimation();
        verify(mOrganizer).setInterceptBackPressedOnTaskRoot(eq(mTaskInfo.token), eq(false));
    }

    @Test
    public void testSetObscuredTouchRect() {
        mTaskView.setObscuredTouchRect(
                new Rect(/* left= */ 0, /* top= */ 10, /* right= */ 100, /* bottom= */ 120));
        ViewTreeObserver.InternalInsetsInfo insetsInfo = new ViewTreeObserver.InternalInsetsInfo();
        mTaskView.onComputeInternalInsets(insetsInfo);

        assertThat(insetsInfo.touchableRegion.contains(0, 10)).isTrue();
        // Region doesn't contain the right/bottom edge.
        assertThat(insetsInfo.touchableRegion.contains(100 - 1, 120 - 1)).isTrue();

        mTaskView.setObscuredTouchRect(null);
        insetsInfo.touchableRegion.setEmpty();
        mTaskView.onComputeInternalInsets(insetsInfo);

        assertThat(insetsInfo.touchableRegion.contains(0, 10)).isFalse();
        assertThat(insetsInfo.touchableRegion.contains(100 - 1, 120 - 1)).isFalse();
    }

    @Test
    public void testSetObscuredTouchRegion() {
        Region obscuredRegion = new Region(10, 10, 19, 19);
        obscuredRegion.union(new Rect(30, 30, 39, 39));

        mTaskView.setObscuredTouchRegion(obscuredRegion);
        ViewTreeObserver.InternalInsetsInfo insetsInfo = new ViewTreeObserver.InternalInsetsInfo();
        mTaskView.onComputeInternalInsets(insetsInfo);

        assertThat(insetsInfo.touchableRegion.contains(10, 10)).isTrue();
        assertThat(insetsInfo.touchableRegion.contains(20, 20)).isFalse();
        assertThat(insetsInfo.touchableRegion.contains(30, 30)).isTrue();

        mTaskView.setObscuredTouchRegion(null);
        insetsInfo.touchableRegion.setEmpty();
        mTaskView.onComputeInternalInsets(insetsInfo);

        assertThat(insetsInfo.touchableRegion.contains(10, 10)).isFalse();
        assertThat(insetsInfo.touchableRegion.contains(20, 20)).isFalse();
        assertThat(insetsInfo.touchableRegion.contains(30, 30)).isFalse();
    }

    @Test
    public void testStartRootTask_setsBoundsAndVisibility() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        TaskViewBase taskViewBase = mock(TaskViewBase.class);
        Rect bounds = new Rect(0, 0, 100, 100);
        when(taskViewBase.getCurrentBoundsOnScreen()).thenReturn(bounds);
        mTaskViewTaskController.setTaskViewBase(taskViewBase);

        // Surface created, but task not available so bounds / visibility isn't set
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        verify(mTaskViewTransitions, never()).updateVisibilityState(
                eq(mTaskViewTaskController), eq(true));

        // Make the task available
        WindowContainerTransaction wct = mock(WindowContainerTransaction.class);
        mTaskViewTaskController.startRootTask(mTaskInfo, mLeash, wct);

        // Bounds got set
        verify(wct).setBounds(any(WindowContainerToken.class), eq(bounds));
        // Visibility & bounds state got set
        verify(mTaskViewTransitions).updateVisibilityState(eq(mTaskViewTaskController), eq(true));
        verify(mTaskViewTransitions).updateBoundsState(eq(mTaskViewTaskController), eq(bounds));
    }

    @Test
    public void testTaskViewPrepareOpenAnimationSetsBoundsAndVisibility() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        TaskViewBase taskViewBase = mock(TaskViewBase.class);
        Rect bounds = new Rect(0, 0, 100, 100);
        when(taskViewBase.getCurrentBoundsOnScreen()).thenReturn(bounds);
        mTaskViewTaskController.setTaskViewBase(taskViewBase);

        // Surface created, but task not available so bounds / visibility isn't set
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        verify(mTaskViewTransitions, never()).updateVisibilityState(
                eq(mTaskViewTaskController), eq(true));

        // Make the task available / start prepareOpen
        WindowContainerTransaction wct = mock(WindowContainerTransaction.class);
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);

        // Bounds got set
        verify(wct).setBounds(any(WindowContainerToken.class), eq(bounds));
        // Visibility & bounds state got set
        verify(mTaskViewTransitions).updateVisibilityState(eq(mTaskViewTaskController), eq(true));
        verify(mTaskViewTransitions).updateBoundsState(eq(mTaskViewTaskController), eq(bounds));
    }

    @Test
    public void testTaskViewPrepareOpenAnimationSetsVisibilityFalse() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        TaskViewBase taskViewBase = mock(TaskViewBase.class);
        Rect bounds = new Rect(0, 0, 100, 100);
        when(taskViewBase.getCurrentBoundsOnScreen()).thenReturn(bounds);
        mTaskViewTaskController.setTaskViewBase(taskViewBase);

        // Task is available, but the surface was never created
        WindowContainerTransaction wct = mock(WindowContainerTransaction.class);
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);

        // Bounds do not get set as there is no surface
        verify(wct, never()).setBounds(any(WindowContainerToken.class), any());
        // Visibility is set to false, bounds aren't set
        verify(mTaskViewTransitions).updateVisibilityState(eq(mTaskViewTaskController), eq(false));
        verify(mTaskViewTransitions, never()).updateBoundsState(eq(mTaskViewTaskController), any());
    }

    @Test
    public void testRemoveTaskView_noTask() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        mTaskView.removeTask();
        verify(mTaskViewTransitions, never()).closeTaskView(any(), any());
    }

    @Test
    public void testRemoveTaskView() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);

        verify(mViewListener).onTaskCreated(eq(mTaskInfo.taskId), any());

        mTaskView.removeTask();
        verify(mTaskViewTransitions).closeTaskView(any(), eq(mTaskViewTaskController));
    }

    @Test
    public void testOnTaskAppearedWithTaskNotFound() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        mTaskViewTaskController.setTaskNotFound();
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);

        verify(mTaskViewTaskController).cleanUpPendingTask();
        verify(mTaskViewTransitions).closeTaskView(any(), eq(mTaskViewTaskController));
    }

    @Test
    public void testOnTaskAppeared_withoutTaskNotFound() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);
        verify(mTaskViewTaskController, never()).cleanUpPendingTask();
    }

    @Test
    public void testSetCaptionInsets_noTaskInitially() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        Rect insets = new Rect(0, 400, 0, 0);
        mTaskView.setCaptionInsets(Insets.of(insets));
        mTaskView.onComputeInternalInsets(new ViewTreeObserver.InternalInsetsInfo());

        verify(mTaskViewTaskController).applyCaptionInsetsIfNeeded();
        verify(mOrganizer, never()).applyTransaction(any());

        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        reset(mOrganizer);
        reset(mTaskViewTaskController);
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        mTaskView.onComputeInternalInsets(new ViewTreeObserver.InternalInsetsInfo());

        verify(mTaskViewTaskController).applyCaptionInsetsIfNeeded();
        verify(mOrganizer).applyTransaction(any());
    }

    @Test
    public void testSetCaptionInsets_withTask() {
        assumeTrue(Transitions.ENABLE_SHELL_TRANSITIONS);

        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        reset(mTaskViewTaskController);
        reset(mOrganizer);

        Rect insets = new Rect(0, 400, 0, 0);
        mTaskView.setCaptionInsets(Insets.of(insets));
        mTaskView.onComputeInternalInsets(new ViewTreeObserver.InternalInsetsInfo());
        verify(mTaskViewTaskController).applyCaptionInsetsIfNeeded();
        verify(mOrganizer).applyTransaction(any());
    }

    @Test
    public void testReleaseInOnTaskRemoval_noNPE() {
        mTaskViewTaskController = spy(new TaskViewTaskController(mContext, mOrganizer,
                mTaskViewTransitions, mSyncQueue));
        mTaskView = new TaskView(mContext, mTaskViewTaskController);
        mTaskView.setListener(mExecutor, new TaskView.Listener() {
            @Override
            public void onTaskRemovalStarted(int taskId) {
                mTaskView.release();
            }
        });

        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTaskController.prepareOpenAnimation(true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));

        assertThat(mTaskViewTaskController.getTaskInfo()).isEqualTo(mTaskInfo);

        mTaskViewTaskController.prepareCloseAnimation();

        assertThat(mTaskViewTaskController.getTaskInfo()).isNull();
    }

    @Test
    public void testOnTaskInfoChangedOnSameUiThread() {
        mTaskViewTaskController.onTaskInfoChanged(mTaskInfo);
        verify(mViewHandler, never()).post(any());
    }

    @Test
    public void testOnTaskInfoChangedOnDifferentUiThread() {
        doReturn(false).when(mViewLooper).isCurrentThread();
        mTaskViewTaskController.onTaskInfoChanged(mTaskInfo);
        verify(mViewHandler).post(any());
    }

    @Test
    public void testSetResizeBgOnSameUiThread_expectUsesTransaction() {
        SurfaceControl.Transaction tx = mock(SurfaceControl.Transaction.class);
        mTaskView = spy(mTaskView);
        mTaskView.setResizeBgColor(tx, Color.BLUE);
        verify(mViewHandler, never()).post(any());
        verify(mTaskView, never()).setResizeBackgroundColor(eq(Color.BLUE));
        verify(mTaskView).setResizeBackgroundColor(eq(tx), eq(Color.BLUE));
    }

    @Test
    public void testSetResizeBgOnDifferentUiThread_expectDoesNotUseTransaction() {
        doReturn(false).when(mViewLooper).isCurrentThread();
        SurfaceControl.Transaction tx = mock(SurfaceControl.Transaction.class);
        mTaskView = spy(mTaskView);
        mTaskView.setResizeBgColor(tx, Color.BLUE);
        verify(mViewHandler).post(any());
        verify(mTaskView).setResizeBackgroundColor(eq(Color.BLUE));
    }
}
