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

package com.android.wm.shell.splitscreen;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_UNDEFINED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.view.SurfaceControl;
import android.window.WindowContainerTransaction;

import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.launcher3.icons.IconProvider;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestRunningTaskInfoBuilder;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Tests for {@link StageTaskListener}
 * Build/Install/Run:
 * atest WMShellUnitTests:StageTaskListenerTests
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class StageTaskListenerTests extends ShellTestCase {

    @Mock
    private ShellTaskOrganizer mTaskOrganizer;
    @Mock
    private StageTaskListener.StageListenerCallbacks mCallbacks;
    @Mock
    private SyncTransactionQueue mSyncQueue;
    @Mock
    private IconProvider mIconProvider;
    @Mock
    private WindowDecorViewModel mWindowDecorViewModel;
    @Spy
    private WindowContainerTransaction mWct;
    @Captor
    private ArgumentCaptor<SyncTransactionQueue.TransactionRunnable> mRunnableCaptor;
    private SurfaceControl mSurfaceControl;
    private ActivityManager.RunningTaskInfo mRootTask;
    private StageTaskListener mStageTaskListener;

    @Before
    @UiThreadTest
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mStageTaskListener = new StageTaskListener(
                mContext,
                mTaskOrganizer,
                DEFAULT_DISPLAY,
                mCallbacks,
                mSyncQueue,
                mIconProvider,
                Optional.of(mWindowDecorViewModel),
                STAGE_TYPE_UNDEFINED);
        mRootTask = new TestRunningTaskInfoBuilder().build();
        mRootTask.parentTaskId = INVALID_TASK_ID;
        mSurfaceControl = new SurfaceControl.Builder().setName("test").build();
        mStageTaskListener.onTaskAppeared(mRootTask, mSurfaceControl);
    }

    @Test
    public void testInitsDimLayer() {
        verify(mSyncQueue).runInSync(mRunnableCaptor.capture());
        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        mRunnableCaptor.getValue().runWithTransaction(t);
        t.apply();

        assertThat(mStageTaskListener.mDimLayer).isNotNull();
    }

    @Test
    public void testRootTaskAppeared() {
        assertThat(mStageTaskListener.mRootTaskInfo.taskId).isEqualTo(mRootTask.taskId);
        verify(mCallbacks).onRootTaskAppeared(mRootTask);
        verify(mCallbacks, never()).onStageVisibilityChanged(mStageTaskListener);
    }

    @Test
    public void testRootTaskVisible() {
        mStageTaskListener.onTaskVanished(mRootTask);
        mRootTask = new TestRunningTaskInfoBuilder().setVisible(true).build();
        mRootTask.parentTaskId = INVALID_TASK_ID;
        mSurfaceControl = new SurfaceControl.Builder().setName("test").build();
        mStageTaskListener.onTaskAppeared(mRootTask, mSurfaceControl);

        verify(mCallbacks).onStageVisibilityChanged(mStageTaskListener);

    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownTaskVanished() {
        final ActivityManager.RunningTaskInfo task = new TestRunningTaskInfoBuilder().build();
        mStageTaskListener.onTaskVanished(task);
    }

    @Test
    public void testTaskInfoChanged_notSupportsMultiWindow() {
        final ActivityManager.RunningTaskInfo childTask =
                new TestRunningTaskInfoBuilder().setParentTaskId(mRootTask.taskId).build();
        childTask.supportsMultiWindow = false;

        mStageTaskListener.onTaskInfoChanged(childTask);
        verify(mCallbacks).onNoLongerSupportMultiWindow(mStageTaskListener, childTask);
    }

    @Test
    public void testEvictAllChildren() {
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        mStageTaskListener.evictAllChildren(wct);
        assertTrue(wct.isEmpty());

        final ActivityManager.RunningTaskInfo childTask =
                new TestRunningTaskInfoBuilder().setParentTaskId(mRootTask.taskId).build();
        mStageTaskListener.onTaskAppeared(childTask, mSurfaceControl);

        mStageTaskListener.evictAllChildren(wct);
        assertFalse(wct.isEmpty());
    }

    @Test
    public void testAddTask() {
        final ActivityManager.RunningTaskInfo task = new TestRunningTaskInfoBuilder().build();
        mStageTaskListener.addTask(task, mWct);

        verify(mWct).reparent(eq(task.token), eq(mRootTask.token), eq(true));
    }

    @Test
    public void testRemoveTask() {
        final ActivityManager.RunningTaskInfo task = new TestRunningTaskInfoBuilder().build();
        assertThat(mStageTaskListener.removeTask(task.taskId, null, mWct)).isFalse();

        mStageTaskListener.mChildrenTaskInfo.put(task.taskId, task);
        assertThat(mStageTaskListener.removeTask(task.taskId, null, mWct)).isTrue();
        verify(mWct).reparent(eq(task.token), isNull(), eq(false));
    }

    @Test
    public void testActiveDeactivate() {
        mStageTaskListener.activate(mWct, true /* reparent */);
        assertThat(mStageTaskListener.isActive()).isTrue();

        mStageTaskListener.deactivate(mWct);
        assertThat(mStageTaskListener.isActive()).isFalse();
    }

    @Test
    public void testGetAllVisibleChildTaskIds() {
        final ActivityManager.RunningTaskInfo taskVisible1 =
                new TestRunningTaskInfoBuilder()
                        .setTaskId(1)
                        .setVisible(true)
                        .setVisibleRequested(true)
                        .build();
        final ActivityManager.RunningTaskInfo taskInvisible2 =
                new TestRunningTaskInfoBuilder()
                        .setTaskId(2)
                        .setVisible(false)
                        .build();
        final ActivityManager.RunningTaskInfo taskVisible3 =
                new TestRunningTaskInfoBuilder()
                        .setTaskId(3)
                        .setVisible(true)
                        .setVisibleRequested(true)
                        .build();
        final ActivityManager.RunningTaskInfo taskVisible4 =
                new TestRunningTaskInfoBuilder()
                        .setTaskId(4)
                        .setVisible(true)
                        .setVisibleRequested(true)
                        .build();
        final ActivityManager.RunningTaskInfo taskInvisible5 =
                new TestRunningTaskInfoBuilder()
                        .setTaskId(5)
                        .setVisible(false)
                        .build();
        final List<Integer> visibleTaskIds = Arrays.asList(taskVisible1.taskId, taskVisible3.taskId,
                taskVisible4.taskId);

        mStageTaskListener.mChildrenTaskInfo.clear();
        assertThat(mStageTaskListener.mChildrenTaskInfo.size() == 0).isTrue();

        mStageTaskListener.mChildrenTaskInfo.put(taskVisible1.taskId, taskVisible1);
        mStageTaskListener.mChildrenTaskInfo.put(taskInvisible2.taskId, taskInvisible2);
        mStageTaskListener.mChildrenTaskInfo.put(taskVisible3.taskId, taskVisible3);
        mStageTaskListener.mChildrenTaskInfo.put(taskVisible4.taskId, taskVisible4);
        mStageTaskListener.mChildrenTaskInfo.put(taskInvisible5.taskId, taskInvisible5);

        final List<Integer> ids = mStageTaskListener.getAllVisibleChildTaskIds();
        assertThat(ids.size() == 3).isTrue();
        assertTrue("List should contain all visible taskIds",
                ids.containsAll(visibleTaskIds));
        assertFalse("List should not contain invisible taskId2",
                ids.contains(taskInvisible2.taskId));
        assertFalse("List should not contain invisible taskId5",
                ids.contains(taskInvisible5.taskId));

        // Clear the mChildrenTaskInfo.
        mStageTaskListener.mChildrenTaskInfo.clear();
        assertThat(mStageTaskListener.mChildrenTaskInfo.size() == 0).isTrue();
    }
}
