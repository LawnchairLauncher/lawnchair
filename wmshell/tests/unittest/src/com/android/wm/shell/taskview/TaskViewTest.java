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

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
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
import android.platform.test.flag.junit.FlagsParameterization;
import android.testing.TestableLooper;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.ViewTreeObserver;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.test.filters.SmallTest;

import com.android.wm.shell.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestHandler;
import com.android.wm.shell.common.HandlerExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.SyncTransactionQueue.TransactionRunnable;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;
import com.android.wm.shell.transition.Transitions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

import platform.test.runner.parameterized.ParameterizedAndroidJunit4;
import platform.test.runner.parameterized.Parameters;

import java.util.List;

@SmallTest
@RunWith(ParameterizedAndroidJunit4.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class TaskViewTest extends ShellTestCase {

    @Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_TASK_VIEW_REPOSITORY,
                Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE
        );
    }

    @Mock
    TaskView.Listener mViewListener;
    @Mock
    ActivityManager.RunningTaskInfo mTaskInfo;
    @Mock
    WindowContainerToken mToken;
    @Mock
    ShellTaskOrganizer mOrganizer;
    @Captor
    ArgumentCaptor<WindowContainerTransaction> mWctCaptor;
    @Mock
    HandlerExecutor mExecutor;
    @Mock
    SyncTransactionQueue mSyncQueue;
    @Mock
    Transitions mTransitions;
    @Mock
    Looper mViewLooper;
    TestHandler mViewHandler;

    SurfaceControl mLeash;

    Context mContext;
    TaskView mTaskView;
    TaskViewRepository mTaskViewRepository;
    TaskViewTransitions mTaskViewTransitions;
    TaskViewTaskController mTaskViewTaskController;

    public TaskViewTest(FlagsParameterization flags) {}

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mLeash = new SurfaceControl.Builder()
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

        mTaskViewRepository = new TaskViewRepository();
        mTaskViewTransitions = spy(new TaskViewTransitions(mTransitions, mTaskViewRepository,
                mOrganizer, mSyncQueue));
        mTaskViewTaskController = new TaskViewTaskController(mContext, mOrganizer,
                mTaskViewTransitions, mSyncQueue);
        mTaskView = new TaskView(mContext, mTaskViewTransitions, mTaskViewTaskController);
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
        TaskView taskView = new TaskView(mContext, mTaskViewTransitions,
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
    public void testOnNewTask_noSurface() {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
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
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        verify(mTaskViewTransitions, never()).setTaskViewVisible(any(), anyBoolean());

        verify(mViewListener).onInitialized();
        assertThat(mTaskView.isInitialized()).isTrue();
        // No task, no visibility change
        verify(mViewListener, never()).onTaskVisibilityChanged(anyInt(), anyBoolean());
    }

    @Test
    public void testOnNewTask_withSurface() {
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);

        verify(mViewListener).onTaskCreated(eq(mTaskInfo.taskId), any());
        verify(mViewListener, never()).onTaskVisibilityChanged(anyInt(), anyBoolean());
    }

    @Test
    public void testSurfaceCreated_withTask() {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));

        verify(mViewListener).onInitialized();
        verify(mTaskViewTransitions).setTaskViewVisible(eq(mTaskViewTaskController), eq(true));

        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, false /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);

        verify(mViewListener).onTaskVisibilityChanged(eq(mTaskInfo.taskId), eq(true));
    }

    @Test
    public void testSurfaceDestroyed_noTask() {
        SurfaceHolder sh = mock(SurfaceHolder.class);
        mTaskView.surfaceCreated(sh);
        mTaskView.surfaceDestroyed(sh);

        verify(mViewListener, never()).onTaskVisibilityChanged(anyInt(), anyBoolean());
    }

    @Test
    public void testSurfaceDestroyed_withTask() {
        SurfaceHolder sh = mock(SurfaceHolder.class);
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
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
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        mTaskView.release();

        verify(mOrganizer).removeListener(eq(mTaskViewTaskController));
        verify(mViewListener).onReleased();
        assertThat(mTaskView.isInitialized()).isFalse();
        verify(mTaskViewTransitions).unregisterTaskView(eq(mTaskViewTaskController));
    }

    @Test
    public void testOnTaskVanished() {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        final SurfaceControl taskLeash = mTaskViewTaskController.getTaskLeash();
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        mTaskViewTaskController.prepareCloseAnimation();

        verify(mViewListener).onTaskRemovalStarted(eq(mTaskInfo.taskId));
        assertThat(mTaskViewTaskController.getTaskLeash()).isNull();
        assertThat(taskLeash.isValid()).isFalse();
    }

    @Test
    public void testOnTaskVanished_withTaskInfoUpdate_notifiesTaskRemoval() {
        // Capture task info when onTaskRemovalStarted is triggered on the task view listener.
        final ActivityManager.RunningTaskInfo[] capturedTaskInfo =
                new ActivityManager.RunningTaskInfo[1];
        final int taskId = mTaskInfo.taskId;
        doAnswer(invocation -> {
            capturedTaskInfo[0] = mTaskView.getTaskInfo();
            return null;
        }).when(mViewListener).onTaskRemovalStarted(taskId);

        // Set up a mock TaskViewBase to verify notified task info.
        final TaskViewBase mockTaskViewBase = mock(TaskViewBase.class);
        mTaskViewTaskController.setTaskViewBase(mockTaskViewBase);

        // Prepare and trigger task opening animation with mTaskInfo.
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, new WindowContainerTransaction());
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));

        // Simulate task info change with windowing mode update.
        final ActivityManager.RunningTaskInfo newTaskInfo = new ActivityManager.RunningTaskInfo();
        newTaskInfo.token = mTaskInfo.token;
        newTaskInfo.taskId = taskId;
        newTaskInfo.taskDescription = mTaskInfo.taskDescription;
        newTaskInfo.configuration.windowConfiguration.setWindowingMode(WINDOWING_MODE_FULLSCREEN);

        // Invoke onTaskVanished with updated task info.
        mTaskViewTaskController.onTaskVanished(newTaskInfo);

        verify(mViewListener).onTaskRemovalStarted(taskId);
        if (BubbleAnythingFlagHelper.enableCreateAnyBubble()) {
            // Verify TaskViewBase and listener updates with new task info.
            verify(mockTaskViewBase).onTaskVanished(same(newTaskInfo));
            assertThat(capturedTaskInfo[0]).isSameInstanceAs(newTaskInfo);
        } else {
            // Verify TaskViewBase and listener updates with old task info.
            verify(mockTaskViewBase).onTaskVanished(same(mTaskInfo));
            assertThat(capturedTaskInfo[0]).isSameInstanceAs(mTaskInfo);
        }
    }

    @Test
    public void testOnBackPressedOnTaskRoot() {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        mTaskViewTaskController.onBackPressedOnTaskRoot(mTaskInfo);

        verify(mViewListener).onBackPressedOnTaskRoot(eq(mTaskInfo.taskId));
    }

    @Test
    public void testSetOnBackPressedOnTaskRoot() {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);

        assertThat(wct.getChanges().get(mToken.asBinder()).getInterceptBackPressed()).isTrue();
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
        TaskViewBase taskViewBase = mock(TaskViewBase.class);
        Rect bounds = new Rect(0, 0, 100, 100);
        when(taskViewBase.getCurrentBoundsOnScreen()).thenReturn(bounds);
        mTaskViewTaskController.setTaskViewBase(taskViewBase);

        // Surface created, but task not available so bounds / visibility isn't set
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        if (TaskViewTransitions.useRepo()) {
            assertNotNull(mTaskViewTransitions.getRepository().byTaskView(mTaskViewTaskController));
            assertFalse(mTaskViewTransitions.getRepository().byTaskView(mTaskViewTaskController)
                    .mVisible);
        } else {
            verify(mTaskViewTransitions, never()).updateVisibilityState(
                    eq(mTaskViewTaskController), eq(true));
        }

        // Make the task available
        WindowContainerTransaction wct = mock(WindowContainerTransaction.class);
        mTaskViewTransitions.startRootTask(mTaskViewTaskController, mTaskInfo, mLeash, wct);

        // Bounds got set
        verify(wct).setBounds(any(WindowContainerToken.class), eq(bounds));
        // Visibility & bounds state got set
        if (TaskViewTransitions.useRepo()) {
            assertTrue(mTaskViewTransitions.getRepository().byTaskView(mTaskViewTaskController)
                    .mVisible);
            assertEquals(mTaskViewTransitions.getRepository().byTaskView(mTaskViewTaskController)
                    .mBounds, bounds);
        } else {
            verify(mTaskViewTransitions).updateVisibilityState(eq(mTaskViewTaskController),
                    eq(true));
            verify(mTaskViewTransitions).updateBoundsState(eq(mTaskViewTaskController), eq(bounds));
        }
    }

    @Test
    public void testPrepareOpenAnimation_copiesLeash() {
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, new WindowContainerTransaction());

        assertThat(mTaskViewTaskController.getTaskLeash()).isNotEqualTo(mLeash);
    }

    @Test
    public void testTaskViewPrepareOpenAnimationSetsBoundsAndVisibility() {
        TaskViewBase taskViewBase = mock(TaskViewBase.class);
        Rect bounds = new Rect(0, 0, 100, 100);
        when(taskViewBase.getCurrentBoundsOnScreen()).thenReturn(bounds);
        mTaskViewTaskController.setTaskViewBase(taskViewBase);

        // Surface created, but task not available so bounds / visibility isn't set
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        if (TaskViewTransitions.useRepo()) {
            assertNotNull(mTaskViewTransitions.getRepository().byTaskView(
                    mTaskViewTaskController));
            assertFalse(mTaskViewTransitions.getRepository().byTaskView(mTaskViewTaskController)
                    .mVisible);
        } else {
            verify(mTaskViewTransitions, never()).updateVisibilityState(
                    eq(mTaskViewTaskController), eq(true));
        }

        // Make the task available / start prepareOpen
        WindowContainerTransaction wct = mock(WindowContainerTransaction.class);
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);

        // Bounds got set
        verify(wct).setBounds(any(WindowContainerToken.class), eq(bounds));
        // Visibility & bounds state got set
        if (TaskViewTransitions.useRepo()) {
            assertTrue(mTaskViewTransitions.getRepository().byTaskView(mTaskViewTaskController)
                    .mVisible);
            assertEquals(mTaskViewTransitions.getRepository().byTaskView(mTaskViewTaskController)
                    .mBounds, bounds);
        } else {
            verify(mTaskViewTransitions).updateVisibilityState(eq(mTaskViewTaskController),
                    eq(true));
            verify(mTaskViewTransitions).updateBoundsState(eq(mTaskViewTaskController), eq(bounds));
        }
    }

    @Test
    public void testTaskViewPrepareOpenAnimationSetsVisibilityFalse() {
        TaskViewBase taskViewBase = mock(TaskViewBase.class);
        Rect bounds = new Rect(0, 0, 100, 100);
        when(taskViewBase.getCurrentBoundsOnScreen()).thenReturn(bounds);
        mTaskViewTaskController.setTaskViewBase(taskViewBase);

        // Task is available, but the surface was never created
        WindowContainerTransaction wct = mock(WindowContainerTransaction.class);
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);

        // Bounds do not get set as there is no surface
        verify(wct, never()).setBounds(any(WindowContainerToken.class), any());
        // Visibility is set to false, bounds aren't set
        if (TaskViewTransitions.useRepo()) {
            assertFalse(mTaskViewTransitions.getRepository().byTaskView(mTaskViewTaskController)
                    .mVisible);
            assertTrue(mTaskViewTransitions.getRepository().byTaskView(mTaskViewTaskController)
                    .mBounds.isEmpty());
        } else {
            verify(mTaskViewTransitions).updateVisibilityState(eq(mTaskViewTaskController),
                    eq(false));
            verify(mTaskViewTransitions, never()).updateBoundsState(eq(mTaskViewTaskController),
                    any());
        }
    }

    @Test
    public void testRemoveTaskView_noTask() {
        mTaskView.removeTask();
        assertFalse(mTaskViewTransitions.hasPending());
    }

    @Test
    public void testRemoveTaskView() {
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);

        verify(mViewListener).onTaskCreated(eq(mTaskInfo.taskId), any());

        mTaskView.removeTask();
        verify(mTaskViewTransitions).removeTaskView(eq(mTaskViewTaskController), any());
    }

    @Test
    public void testUnregisterTask() {
        mTaskView.unregisterTask();

        verify(mTaskViewTransitions).unregisterTaskView(mTaskViewTaskController);
    }

    @Test
    public void testOnTaskAppearedWithTaskNotFound() {
        mTaskViewTaskController.setTaskNotFound();
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);

        assertNull(mTaskViewTaskController.getTaskInfo());
        verify(mTaskViewTransitions).removeTaskView(eq(mTaskViewTaskController), any());
    }

    @Test
    public void testOnTaskAppeared_withoutTaskNotFound() {
        mTaskViewTaskController.onTaskAppeared(mTaskInfo, mLeash);
        assertEquals(mTaskInfo, mTaskViewTaskController.getPendingInfo());
        verify(mTaskViewTransitions, never()).removeTaskView(any(), any());
    }

    @Test
    public void testSetCaptionInsets_noTaskInitially() {
        Rect insets = new Rect(0, 400, 0, 0);
        mTaskView.setCaptionInsets(Insets.of(insets));
        mTaskView.onComputeInternalInsets(new ViewTreeObserver.InternalInsetsInfo());

        verify(mOrganizer, never()).applyTransaction(any());

        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        reset(mOrganizer);
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        mTaskView.onComputeInternalInsets(new ViewTreeObserver.InternalInsetsInfo());

        verify(mOrganizer).applyTransaction(mWctCaptor.capture());
        assertTrue(mWctCaptor.getValue().getHierarchyOps().stream().anyMatch(hop ->
                hop.getType() == WindowContainerTransaction.HierarchyOp
                        .HIERARCHY_OP_TYPE_ADD_INSETS_FRAME_PROVIDER));
    }

    @Test
    public void testSetCaptionInsets_withTask() {
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        reset(mOrganizer);

        Rect insets = new Rect(0, 400, 0, 0);
        mTaskView.setCaptionInsets(Insets.of(insets));
        mTaskView.onComputeInternalInsets(new ViewTreeObserver.InternalInsetsInfo());
        verify(mOrganizer).applyTransaction(mWctCaptor.capture());
        assertTrue(mWctCaptor.getValue().getHierarchyOps().stream().anyMatch(hop ->
                hop.getType() == WindowContainerTransaction.HierarchyOp
                        .HIERARCHY_OP_TYPE_ADD_INSETS_FRAME_PROVIDER));
    }

    @Test
    public void testReleaseInOnTaskRemoval_noNPE() {
        mTaskViewTaskController = spy(new TaskViewTaskController(mContext, mOrganizer,
                mTaskViewTransitions, mSyncQueue));
        mTaskView = new TaskView(mContext, mTaskViewTransitions, mTaskViewTaskController);
        mTaskView.setListener(mExecutor, new TaskView.Listener() {
            @Override
            public void onTaskRemovalStarted(int taskId) {
                mTaskView.release();
            }
        });

        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
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

    @Test
    public void testOnAppeared_setsTrimmableTask() {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);

        assertThat(wct.getHierarchyOps().get(0).isTrimmableFromRecents()).isFalse();
    }

    @Test
    public void testMoveToFullscreen_callsTaskRemovalStarted() {
        WindowContainerTransaction wct = new WindowContainerTransaction();
        mTaskViewTransitions.prepareOpenAnimation(mTaskViewTaskController, true /* newTask */,
                new SurfaceControl.Transaction(), new SurfaceControl.Transaction(), mTaskInfo,
                mLeash, wct);
        mTaskView.surfaceCreated(mock(SurfaceHolder.class));
        mTaskViewTransitions.moveTaskViewToFullscreen(mTaskViewTaskController);

        verify(mViewListener).onTaskRemovalStarted(eq(mTaskInfo.taskId));
    }
}
