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

import static android.app.ActivityManager.RECENT_IGNORE_UNAVAILABLE;
import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;

import static com.android.window.flags2.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE;
import static com.android.wm.shell.shared.GroupedTaskInfo.TYPE_DESK;
import static com.android.wm.shell.shared.GroupedTaskInfo.TYPE_FULLSCREEN;
import static com.android.wm.shell.shared.GroupedTaskInfo.TYPE_SPLIT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static java.lang.Integer.MAX_VALUE;
import static java.util.stream.Collectors.joining;

import android.app.ActivityManager.RecentTaskInfo;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.view.SurfaceControl;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.window.flags2.Flags;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.desktopmode.DesktopRepository;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.desktopmode.DesktopWallpaperActivity;
import com.android.wm.shell.shared.GroupedTaskInfo;
import com.android.wm.shell.shared.desktopmode.FakeDesktopState;
import com.android.wm.shell.shared.split.SplitBounds;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Tests for {@link RecentTasksController}
 *
 * Usage: atest WMShellUnitTests:RecentTasksControllerTest
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class RecentTasksControllerTest extends ShellTestCase {
    private static final String SYSTEM_UI_PACKAGE_NAME = "com.android.systemui";

    @Mock
    private Context mContext;
    @Mock
    private TaskStackListenerImpl mTaskStackListener;
    @Mock
    private ShellCommandHandler mShellCommandHandler;
    @Mock
    private ActivityTaskManager mActivityTaskManager;
    @Mock
    private DisplayInsetsController mDisplayInsetsController;
    @Mock
    private IRecentTasksListener mRecentTasksListener;
    @Mock
    private TaskStackTransitionObserver mTaskStackTransitionObserver;
    @Mock
    private DesktopUserRepositories mDesktopUserRepositories;
    @Mock
    private DesktopRepository mDesktopRepository;
    @Mock
    private UserManager mUserManager;
    private FakeDesktopState mDesktopState;

    private ShellTaskOrganizer mShellTaskOrganizer;
    private RecentTasksController mRecentTasksController;
    private RecentTasksController mRecentTasksControllerReal;
    private ShellInit mShellInit;
    private ShellController mShellController;
    private TestShellExecutor mMainExecutor;
    private AutoCloseable mMocksInit = null;

    @Before
    public void setUp() {
        mDesktopState = new FakeDesktopState();
        mDesktopState.setCanEnterDesktopMode(true);

        mMocksInit = MockitoAnnotations.openMocks(this);

        mMainExecutor = new TestShellExecutor();
        when(mDesktopUserRepositories.getCurrent()).thenReturn(mDesktopRepository);
        when(mDesktopUserRepositories.getProfile(anyInt())).thenReturn(mDesktopRepository);
        when(mContext.getPackageManager()).thenReturn(mock(PackageManager.class));
        when(mContext.getSystemService(KeyguardManager.class))
                .thenReturn(mock(KeyguardManager.class));
        mShellInit = spy(new ShellInit(mMainExecutor));
        mShellController = spy(new ShellController(mContext, mShellInit, mShellCommandHandler,
                mDisplayInsetsController, mUserManager, mMainExecutor));
        mRecentTasksControllerReal = new RecentTasksController(mContext, mShellInit,
                mShellController, mShellCommandHandler, mTaskStackListener, mActivityTaskManager,
                Optional.of(mDesktopUserRepositories), mTaskStackTransitionObserver,
                mMainExecutor, mDesktopState);
        mRecentTasksController = spy(mRecentTasksControllerReal);
        mShellTaskOrganizer = new ShellTaskOrganizer(mShellInit, mShellCommandHandler,
                null /* sizeCompatUI */, Optional.empty(), Optional.of(mRecentTasksController),
                mMainExecutor);
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
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), isA(RecentTasksController.class));
    }

    @Test
    public void instantiateController_addDumpCallback() {
        verify(mShellCommandHandler, times(1)).addDumpCallback(any(),
                isA(RecentTasksController.class));
    }

    @Test
    public void instantiateController_addExternalInterface() {
        verify(mShellController, times(1)).addExternalInterface(
                eq(IRecentTasks.DESCRIPTOR), any(), any());
    }

    @Test
    public void instantiateController_initializesRepository() {
        verify(mDesktopUserRepositories, times(1)).getCurrent();
        verify(mDesktopRepository, times(1)).addActiveTaskListener(any());
    }

    @Test
    public void testInvalidateExternalInterface_unregistersListener() {
        // Note: We have to use the real instance of the controller here since that is the instance
        // that is passed to ShellController internally, and the instance that the listener will be
        // unregistered from
        mRecentTasksControllerReal.registerRecentTasksListener(new IRecentTasksListener.Default());
        assertTrue(mRecentTasksControllerReal.hasRecentTasksListener());
        // Create initial interface
        mShellController.createExternalInterfaces(new Bundle());
        // Recreate the interface to trigger invalidation of the previous instance
        mShellController.createExternalInterfaces(new Bundle());
        assertFalse(mRecentTasksControllerReal.hasRecentTasksListener());
    }

    @Test
    public void testAddRemoveSplitNotifyChange() {
        reset(mRecentTasksController);
        RecentTaskInfo t1 = makeTaskInfo(1);
        RecentTaskInfo t2 = makeTaskInfo(2);
        setRawList(t1, t2);

        mRecentTasksController.addSplitPair(t1.taskId, t2.taskId, mock(SplitBounds.class));
        verify(mRecentTasksController).notifyRecentTasksChanged();

        reset(mRecentTasksController);
        mRecentTasksController.removeSplitPair(t1.taskId);
        verify(mRecentTasksController).notifyRecentTasksChanged();
    }

    @Test
    public void testAddSameSplitBoundsInfoSkipNotifyChange() {
        reset(mRecentTasksController);
        RecentTaskInfo t1 = makeTaskInfo(1);
        RecentTaskInfo t2 = makeTaskInfo(2);
        setRawList(t1, t2);

        // Verify only one update if the split info is the same
        SplitBounds bounds1 = new SplitBounds(new Rect(0, 0, 50, 50),
                new Rect(50, 50, 100, 100), t1.taskId, t2.taskId, SNAP_TO_2_50_50);
        mRecentTasksController.addSplitPair(t1.taskId, t2.taskId, bounds1);
        SplitBounds bounds2 = new SplitBounds(new Rect(0, 0, 50, 50),
                new Rect(50, 50, 100, 100), t1.taskId, t2.taskId, SNAP_TO_2_50_50);
        mRecentTasksController.addSplitPair(t1.taskId, t2.taskId, bounds2);
        verify(mRecentTasksController, times(1)).notifyRecentTasksChanged();
    }

    @Test
    public void testGetRecentTasks() {
        RecentTaskInfo t1 = makeTaskInfo(1);
        RecentTaskInfo t2 = makeTaskInfo(2);
        RecentTaskInfo t3 = makeTaskInfo(3);
        setRawList(t1, t2, t3);

        ArrayList<GroupedTaskInfo> recentTasks =
                mRecentTasksController.getRecentTasks(MAX_VALUE, RECENT_IGNORE_UNAVAILABLE, 0);
        assertGroupedTasksListEquals(recentTasks, List.of(
                List.of(t1.taskId),
                List.of(t2.taskId),
                List.of(t3.taskId)));
    }

    @Test
    public void testGetRecentTasks_removesDesktopWallpaperActivity() {
        RecentTaskInfo t1 = makeTaskInfo(1);
        RecentTaskInfo desktopWallpaperTaskInfo = makeDesktopWallpaperTaskInfo(2);
        RecentTaskInfo t3 = makeTaskInfo(3);
        setRawList(t1, desktopWallpaperTaskInfo, t3);

        ArrayList<GroupedTaskInfo> recentTasks =
                mRecentTasksController.getRecentTasks(MAX_VALUE, RECENT_IGNORE_UNAVAILABLE, 0);
        assertGroupedTasksListEquals(recentTasks, List.of(
                List.of(t1.taskId),
                List.of(t3.taskId)));
    }

    @Test
    public void testGetRecentTasks_withPairs() {
        RecentTaskInfo t1 = makeTaskInfo(1);
        RecentTaskInfo t2 = makeTaskInfo(2);
        RecentTaskInfo t3 = makeTaskInfo(3);
        RecentTaskInfo t4 = makeTaskInfo(4);
        RecentTaskInfo t5 = makeTaskInfo(5);
        RecentTaskInfo t6 = makeTaskInfo(6);
        setRawList(t1, t2, t3, t4, t5, t6);

        // Mark a couple pairs [t2, t4], [t3, t5]
        SplitBounds pair1Bounds =
                new SplitBounds(new Rect(), new Rect(), 2, 4, SNAP_TO_2_50_50);
        SplitBounds pair2Bounds =
                new SplitBounds(new Rect(), new Rect(), 3, 5, SNAP_TO_2_50_50);

        mRecentTasksController.addSplitPair(t2.taskId, t4.taskId, pair1Bounds);
        mRecentTasksController.addSplitPair(t3.taskId, t5.taskId, pair2Bounds);

        ArrayList<GroupedTaskInfo> recentTasks =
                mRecentTasksController.getRecentTasks(MAX_VALUE, RECENT_IGNORE_UNAVAILABLE, 0);
        assertGroupedTasksListEquals(recentTasks, List.of(
                List.of(t1.taskId),
                List.of(t2.taskId, t4.taskId),
                List.of(t3.taskId, t5.taskId),
                List.of(t6.taskId)));
    }

    @Test
    public void testGetRecentTasks_ReturnsRecentTasksAsynchronously() {
        @SuppressWarnings("unchecked")
        final List<GroupedTaskInfo>[] recentTasks = new List[1];
        Consumer<List<GroupedTaskInfo>> consumer = argument -> recentTasks[0] = argument;
        RecentTaskInfo t1 = makeTaskInfo(1);
        RecentTaskInfo t2 = makeTaskInfo(2);
        RecentTaskInfo t3 = makeTaskInfo(3);
        RecentTaskInfo t4 = makeTaskInfo(4);
        RecentTaskInfo t5 = makeTaskInfo(5);
        RecentTaskInfo t6 = makeTaskInfo(6);
        setRawList(t1, t2, t3, t4, t5, t6);

        // Mark a couple pairs [t2, t4], [t3, t5]
        SplitBounds pair1Bounds =
                new SplitBounds(new Rect(), new Rect(), 2, 4, SNAP_TO_2_50_50);
        SplitBounds pair2Bounds =
                new SplitBounds(new Rect(), new Rect(), 3, 5, SNAP_TO_2_50_50);

        mRecentTasksController.addSplitPair(t2.taskId, t4.taskId, pair1Bounds);
        mRecentTasksController.addSplitPair(t3.taskId, t5.taskId, pair2Bounds);

        mRecentTasksController.asRecentTasks()
                .getRecentTasks(MAX_VALUE, RECENT_IGNORE_UNAVAILABLE, 0, Runnable::run,
                        consumer);
        mMainExecutor.flushAll();

        assertGroupedTasksListEquals(recentTasks[0], List.of(
                List.of(t1.taskId),
                List.of(t2.taskId, t4.taskId),
                List.of(t3.taskId, t5.taskId),
                List.of(t6.taskId)));
    }

    @Test
    public void testGetRecentTasks_hasActiveDesktopTasks_proto2Enabled_groupFreeformTasks() {
        RecentTaskInfo t1 = makeTaskInfo(1);
        RecentTaskInfo t2 = makeTaskInfo(2);
        RecentTaskInfo t3 = makeTaskInfo(3);
        RecentTaskInfo t4 = makeTaskInfo(4);
        setRawList(t1, t2, t3, t4);

        when(mDesktopUserRepositories.getCurrent().isActiveTask(1)).thenReturn(true);
        when(mDesktopUserRepositories.getCurrent().isActiveTask(3)).thenReturn(true);

        ArrayList<GroupedTaskInfo> recentTasks =
                mRecentTasksController.getRecentTasks(MAX_VALUE, RECENT_IGNORE_UNAVAILABLE, 0);

        // 2 freeform tasks should be grouped into one, 3 total recents entries
        assertEquals(3, recentTasks.size());
        GroupedTaskInfo singleGroup1 = recentTasks.get(0);
        GroupedTaskInfo singleGroup2 = recentTasks.get(1);
        GroupedTaskInfo freeformGroup = recentTasks.get(2);

        // Check that groups have expected types
        assertTrue(freeformGroup.isBaseType(TYPE_DESK));
        assertTrue(singleGroup1.isBaseType(TYPE_FULLSCREEN));
        assertTrue(singleGroup2.isBaseType(TYPE_FULLSCREEN));

        // Check freeform group entries
        assertEquals(t1, freeformGroup.getTaskInfoList().get(0));
        assertEquals(t3, freeformGroup.getTaskInfoList().get(1));

        // Check single entries
        assertEquals(t2, singleGroup1.getTaskInfo1());
        assertEquals(t4, singleGroup2.getTaskInfo1());
    }

    @Test
    public void testGetRecentTasks_hasActiveDesktopTasks_proto2Enabled_freeformTaskOrder() {
        RecentTaskInfo t1 = makeTaskInfo(1);
        RecentTaskInfo t2 = makeTaskInfo(2);
        RecentTaskInfo t3 = makeTaskInfo(3);
        RecentTaskInfo t4 = makeTaskInfo(4);
        RecentTaskInfo t5 = makeTaskInfo(5);
        setRawList(t1, t2, t3, t4, t5);

        SplitBounds pair1Bounds =
                new SplitBounds(new Rect(), new Rect(), 1, 2, SNAP_TO_2_50_50);
        mRecentTasksController.addSplitPair(t1.taskId, t2.taskId, pair1Bounds);

        when(mDesktopUserRepositories.getCurrent().isActiveTask(3)).thenReturn(true);
        when(mDesktopUserRepositories.getCurrent().isActiveTask(5)).thenReturn(true);

        ArrayList<GroupedTaskInfo> recentTasks =
                mRecentTasksController.getRecentTasks(MAX_VALUE, RECENT_IGNORE_UNAVAILABLE, 0);

        // 2 split screen tasks grouped, 2 freeform tasks grouped, 3 total recents entries
        assertEquals(3, recentTasks.size());
        GroupedTaskInfo splitGroup = recentTasks.get(0);
        GroupedTaskInfo singleGroup = recentTasks.get(1);
        GroupedTaskInfo freeformGroup = recentTasks.get(2);

        // Check that groups have expected types
        assertTrue(splitGroup.isBaseType(TYPE_SPLIT));
        assertTrue(freeformGroup.isBaseType(TYPE_DESK));
        assertTrue(singleGroup.isBaseType(TYPE_FULLSCREEN));

        // Check freeform group entries
        assertEquals(t3, freeformGroup.getTaskInfoList().get(0));
        assertEquals(t5, freeformGroup.getTaskInfoList().get(1));

        // Check split group entries
        assertEquals(t1, splitGroup.getTaskInfoList().get(0));
        assertEquals(t2, splitGroup.getTaskInfoList().get(1));

        // Check single entry
        assertEquals(t4, singleGroup.getTaskInfo1());
    }

    @Test
    public void testGetRecentTasks_hasActiveDesktopTasks_proto2Disabled_doNotGroupFreeformTasks() {
        mDesktopState.setCanEnterDesktopMode(false);

        RecentTaskInfo t1 = makeTaskInfo(1);
        RecentTaskInfo t2 = makeTaskInfo(2);
        RecentTaskInfo t3 = makeTaskInfo(3);
        RecentTaskInfo t4 = makeTaskInfo(4);
        setRawList(t1, t2, t3, t4);

        when(mDesktopUserRepositories.getCurrent().isActiveTask(1)).thenReturn(true);
        when(mDesktopUserRepositories.getCurrent().isActiveTask(3)).thenReturn(true);

        ArrayList<GroupedTaskInfo> recentTasks =
                mRecentTasksController.getRecentTasks(MAX_VALUE, RECENT_IGNORE_UNAVAILABLE, 0);

        // Expect no grouping of tasks
        assertEquals(4, recentTasks.size());
        assertTrue(recentTasks.get(0).isBaseType(TYPE_FULLSCREEN));
        assertTrue(recentTasks.get(1).isBaseType(TYPE_FULLSCREEN));
        assertTrue(recentTasks.get(2).isBaseType(TYPE_FULLSCREEN));
        assertTrue(recentTasks.get(3).isBaseType(TYPE_FULLSCREEN));

        assertEquals(t1, recentTasks.get(0).getTaskInfo1());
        assertEquals(t2, recentTasks.get(1).getTaskInfo1());
        assertEquals(t3, recentTasks.get(2).getTaskInfo1());
        assertEquals(t4, recentTasks.get(3).getTaskInfo1());
    }

    @Test
    public void testGetRecentTasks_proto2Enabled_includesMinimizedFreeformTasks() {
        RecentTaskInfo t1 = makeTaskInfo(1);
        RecentTaskInfo t2 = makeTaskInfo(2);
        RecentTaskInfo t3 = makeTaskInfo(3);
        RecentTaskInfo t4 = makeTaskInfo(4);
        RecentTaskInfo t5 = makeTaskInfo(5);
        setRawList(t1, t2, t3, t4, t5);

        when(mDesktopRepository.isActiveTask(1)).thenReturn(true);
        when(mDesktopRepository.isActiveTask(2)).thenReturn(false);
        when(mDesktopRepository.isActiveTask(3)).thenReturn(true);
        when(mDesktopRepository.isActiveTask(4)).thenReturn(false);
        when(mDesktopRepository.isActiveTask(5)).thenReturn(true);
        when(mDesktopRepository.isMinimizedTask(3)).thenReturn(true);

        ArrayList<GroupedTaskInfo> recentTasks =
                mRecentTasksController.getRecentTasks(MAX_VALUE, RECENT_IGNORE_UNAVAILABLE, 0);

        // 3 freeform tasks should be grouped into one, 2 single tasks, 3 total recents entries
        assertEquals(3, recentTasks.size());
        GroupedTaskInfo singleGroup1 = recentTasks.get(0);
        GroupedTaskInfo singleGroup2 = recentTasks.get(1);
        GroupedTaskInfo freeformGroup = recentTasks.get(2);

        // Check that groups have expected types
        assertTrue(freeformGroup.isBaseType(TYPE_DESK));
        assertTrue(singleGroup1.isBaseType(TYPE_FULLSCREEN));
        assertTrue(singleGroup2.isBaseType(TYPE_FULLSCREEN));

        // Check freeform group entries
        assertEquals(3, freeformGroup.getTaskInfoList().size());
        assertEquals(t1, freeformGroup.getTaskInfoList().get(0));
        assertEquals(t3, freeformGroup.getTaskInfoList().get(1));
        assertEquals(t5, freeformGroup.getTaskInfoList().get(2));

        // Check single entries
        assertEquals(t2, singleGroup1.getTaskInfo1());
        assertEquals(t4, singleGroup2.getTaskInfo1());
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    public void testGetRecentTasks_hasDesktopTasks_persistenceEnabled_freeformTaskHaveBoundsSet() {
        RecentTaskInfo t1 = makeTaskInfo(1);
        RecentTaskInfo t2 = makeTaskInfo(2);

        t1.lastNonFullscreenBounds = new Rect(100, 200, 300, 400);
        t2.lastNonFullscreenBounds = new Rect(150, 250, 350, 450);
        setRawList(t1, t2);

        when(mDesktopUserRepositories.getCurrent().isActiveTask(1)).thenReturn(true);
        when(mDesktopUserRepositories.getCurrent().isActiveTask(2)).thenReturn(true);

        ArrayList<GroupedTaskInfo> recentTasks =
                mRecentTasksController.getRecentTasks(MAX_VALUE, RECENT_IGNORE_UNAVAILABLE, 0);

        assertEquals(1, recentTasks.size());
        GroupedTaskInfo freeformGroup = recentTasks.get(0);

        // Check bounds
        assertEquals(t1.lastNonFullscreenBounds, freeformGroup.getTaskInfoList().get(
                0).configuration.windowConfiguration.getAppBounds());
        assertEquals(t2.lastNonFullscreenBounds, freeformGroup.getTaskInfoList().get(
                1).configuration.windowConfiguration.getAppBounds());

        // Check position in parent
        assertEquals(new Point(t1.lastNonFullscreenBounds.left,
                        t1.lastNonFullscreenBounds.top),
                freeformGroup.getTaskInfoList().get(0).positionInParent);
        assertEquals(new Point(t2.lastNonFullscreenBounds.left,
                        t2.lastNonFullscreenBounds.top),
                freeformGroup.getTaskInfoList().get(1).positionInParent);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DESKTOP_WINDOWING_PERSISTENCE)
    public void testGetRecentTasks_lastNonFullscreenBoundsNull_doesNotThrow() {
        RecentTaskInfo t1 = makeTaskInfo(1);
        t1.lastNonFullscreenBounds = null;
        setRawList(t1);
        when(mDesktopUserRepositories.getCurrent().isActiveTask(1)).thenReturn(true);

        mRecentTasksController.getRecentTasks(MAX_VALUE, RECENT_IGNORE_UNAVAILABLE, 0);
    }
    @Test
    public void testRemovedTaskRemovesSplit() {
        RecentTaskInfo t1 = makeTaskInfo(1);
        RecentTaskInfo t2 = makeTaskInfo(2);
        RecentTaskInfo t3 = makeTaskInfo(3);
        setRawList(t1, t2, t3);

        // Add a pair
        SplitBounds pair1Bounds =
                new SplitBounds(new Rect(), new Rect(), 2, 3, SNAP_TO_2_50_50);
        mRecentTasksController.addSplitPair(t2.taskId, t3.taskId, pair1Bounds);
        reset(mRecentTasksController);

        // Remove one of the tasks and ensure the pair is removed
        SurfaceControl mockLeash = mock(SurfaceControl.class);
        RunningTaskInfo rt2 = makeRunningTaskInfo(2);
        mShellTaskOrganizer.onTaskAppeared(rt2, mockLeash);
        mShellTaskOrganizer.onTaskVanished(rt2);

        verify(mRecentTasksController).removeSplitPair(t2.taskId);
    }

    @Test
    public void testTaskWindowingModeChangedNotifiesChange() {
        reset(mRecentTasksController);
        RecentTaskInfo t1 = makeTaskInfo(1);
        setRawList(t1);

        // Remove one of the tasks and ensure the pair is removed
        SurfaceControl mockLeash = mock(SurfaceControl.class);
        RunningTaskInfo rt2Fullscreen = makeRunningTaskInfo(2);
        rt2Fullscreen.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_FULLSCREEN);
        mShellTaskOrganizer.onTaskAppeared(rt2Fullscreen, mockLeash);

        // Change the windowing mode and ensure the recent tasks change is notified
        RunningTaskInfo rt2MultiWIndow = makeRunningTaskInfo(2);
        rt2MultiWIndow.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_MULTI_WINDOW);
        mShellTaskOrganizer.onTaskInfoChanged(rt2MultiWIndow);

        // One for onTaskAppeared and one for onTaskInfoChanged
        verify(mRecentTasksController, times(2)).notifyRecentTasksChanged();
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS})
    public void onTaskAdded_desktopModeRunningAppsEnabled_triggersOnRunningTaskAppeared()
            throws Exception {
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);
        RunningTaskInfo taskInfo = makeRunningTaskInfo(/* taskId= */10);

        mRecentTasksControllerReal.onTaskAdded(taskInfo);

        verify(mRecentTasksListener).onRunningTaskAppeared(taskInfo);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS)
    public void onTaskAdded_desktopModeRunningAppsDisabled_doesNotTriggerOnRunningTaskAppeared()
            throws Exception {
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);
        RunningTaskInfo taskInfo = makeRunningTaskInfo(/* taskId= */10);

        mRecentTasksControllerReal.onTaskAdded(taskInfo);

        verify(mRecentTasksListener, never()).onRunningTaskAppeared(any());
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS})
    public void onTaskAdded_orDesktopWallpaperActivity_doesNotTriggerOnRunningTaskAppeared()
            throws Exception {
        RunningTaskInfo taskInfo = makeDesktopWallpaperActivityTaskInfo(/* taskId= */10);
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);

        mRecentTasksControllerReal.onTaskAdded(taskInfo);

        verify(mRecentTasksListener, never()).onRunningTaskAppeared(any());
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS})
    public void taskWindowingModeChanged_desktopRunningAppsEnabled_triggersOnRunningTaskChanged()
            throws Exception {
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);
        RunningTaskInfo taskInfo = makeRunningTaskInfo(/* taskId= */10);

        mRecentTasksControllerReal.onTaskRunningInfoChanged(taskInfo);

        verify(mRecentTasksListener).onRunningTaskChanged(taskInfo);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS})
    public void taskInfoChanged_forDesktopWallpaperActivity_doesNotTriggerOnRunningTaskChanged()
            throws Exception {
        RunningTaskInfo taskInfo = makeDesktopWallpaperActivityTaskInfo(/* taskId= */10);
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);

        mRecentTasksControllerReal.onTaskRunningInfoChanged(taskInfo);

        verify(mRecentTasksListener, never()).onRunningTaskChanged(any());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS)
    public void
            taskWindowingModeChanged_desktopRunningAppsDisabled_doesNotTriggerOnRunningTaskChanged()
            throws Exception {
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);
        RunningTaskInfo taskInfo = makeRunningTaskInfo(/* taskId= */10);

        mRecentTasksControllerReal.onTaskRunningInfoChanged(taskInfo);

        verify(mRecentTasksListener, never()).onRunningTaskChanged(any());
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS})
    public void onTaskRemoved_desktopModeRunningAppsEnabled_triggersOnRunningTaskVanished()
            throws Exception {
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);
        RunningTaskInfo taskInfo = makeRunningTaskInfo(/* taskId= */10);

        mRecentTasksControllerReal.onTaskRemoved(taskInfo);

        verify(mRecentTasksListener).onRunningTaskVanished(taskInfo);
    }


    @Test
    @EnableFlags({Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
            Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS})
    public void onTaskRemoved_forDesktopWallpaperActivity_doesNotTriggerOnRunningTaskVanished()
            throws Exception {
        RunningTaskInfo taskInfo = makeDesktopWallpaperActivityTaskInfo(/* taskId= */10);
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);

        mRecentTasksControllerReal.onTaskRemoved(taskInfo);

        verify(mRecentTasksListener, never()).onRunningTaskVanished(any());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
    @DisableFlags(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_TASKBAR_RUNNING_APPS)
    public void onTaskRemoved_desktopModeRunningAppsDisabled_doesNotTriggerOnRunningTaskVanished()
            throws Exception {
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);
        RunningTaskInfo taskInfo = makeRunningTaskInfo(/* taskId= */10);

        mRecentTasksControllerReal.onTaskRemoved(taskInfo);

        verify(mRecentTasksListener, never()).onRunningTaskVanished(any());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    @DisableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    public void onTaskMovedToFront_TaskStackObserverEnabled_triggersOnTaskMovedToFront()
            throws Exception {
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);
        RunningTaskInfo taskInfo = makeRunningTaskInfo(/* taskId= */10);

        mRecentTasksControllerReal.onTaskMovedToFrontThroughTransition(taskInfo);

        GroupedTaskInfo runningTask = GroupedTaskInfo.forFullscreenTasks(taskInfo);
        verify(mRecentTasksListener).onTaskMovedToFront(eq(runningTask));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    public void onTaskMovedToFront_TaskStackObserverEnabled_doesNotTriggersOnTaskMovedToFront()
            throws Exception {
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);
        RunningTaskInfo taskInfo = makeRunningTaskInfo(/* taskId= */10);

        mRecentTasksControllerReal.onTaskMovedToFront(taskInfo);

        verify(mRecentTasksListener, never()).onTaskMovedToFront(any());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    public void onDesktopWallpaperActivityMovedToFront_doesNotTriggerOnTaskMovedToFront()
            throws Exception {
        RunningTaskInfo taskInfo = makeDesktopWallpaperActivityTaskInfo(/* taskId= */10);
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);

        mRecentTasksControllerReal.onTaskMovedToFrontThroughTransition(taskInfo);

        verify(mRecentTasksListener, never()).onTaskMovedToFront(any());
    }

    @Test
    public void getNullSplitBoundsNonSplitTask() {
        SplitBounds sb = mRecentTasksController.getSplitBoundsForTaskId(3);
        assertNull("splitBounds should be null for non-split task", sb);
    }

    @Test
    public void getNullSplitBoundsInvalidTask() {
        SplitBounds sb = mRecentTasksController.getSplitBoundsForTaskId(INVALID_TASK_ID);
        assertNull("splitBounds should be null for invalid taskID", sb);
    }

    @Test
    public void getSplitBoundsForSplitTask() {
        SplitBounds pair1Bounds = mock(SplitBounds.class);
        SplitBounds pair2Bounds = mock(SplitBounds.class);

        mRecentTasksController.addSplitPair(1, 2, pair1Bounds);
        mRecentTasksController.addSplitPair(4, 3, pair2Bounds);

        SplitBounds splitBounds2 = mRecentTasksController.getSplitBoundsForTaskId(2);
        SplitBounds splitBounds1 = mRecentTasksController.getSplitBoundsForTaskId(1);
        assertEquals("Different splitBounds for same pair", splitBounds1, splitBounds2);
        assertEquals(splitBounds1, pair1Bounds);

        SplitBounds splitBounds3 = mRecentTasksController.getSplitBoundsForTaskId(3);
        SplitBounds splitBounds4 = mRecentTasksController.getSplitBoundsForTaskId(4);
        assertEquals("Different splitBounds for same pair", splitBounds3, splitBounds4);
        assertEquals(splitBounds4, pair2Bounds);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    public void shellTopTaskTracker_onTaskStackChanged_expectNoRecentsChanged() throws Exception {
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);
        mRecentTasksControllerReal.onTaskStackChanged();
        verify(mRecentTasksListener, never()).onRecentTasksChanged();
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    public void shellTopTaskTracker_onTaskRemoved_expectNoRecentsChanged() throws Exception {
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);
        RunningTaskInfo taskInfo = makeRunningTaskInfo(/* taskId= */10);
        mRecentTasksControllerReal.onTaskRemoved(taskInfo);
        verify(mRecentTasksListener, never()).onRecentTasksChanged();
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    public void shellTopTaskTracker_onVisibleTasksChanged() throws Exception {
        mRecentTasksControllerReal.registerRecentTasksListener(mRecentTasksListener);
        RunningTaskInfo taskInfo = makeRunningTaskInfo(/* taskId= */10);
        mRecentTasksControllerReal.onVisibleTasksChanged(List.of(taskInfo));
        verify(mRecentTasksListener, never()).onVisibleTasksChanged(any());
    }

    @Test
    public void generateList_emptyTaskList_expectNoGroupedTasks() throws Exception {
        assertTrue(mRecentTasksControllerReal.generateList(List.of(), "test").isEmpty());
    }

    @Test
    public void generateList_excludePipTask() throws Exception {
        RunningTaskInfo task1 = makeRunningTaskInfo(1);
        RunningTaskInfo pipTask = makeRunningTaskInfo(2);
        pipTask.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_PINNED);

        ArrayList<GroupedTaskInfo> groupedTasks = mRecentTasksControllerReal.generateList(
                List.of(task1, pipTask),
                "test");

        assertGroupedTasksListEquals(groupedTasks, List.of(List.of(task1.taskId)));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    public void generateList_fullscreen_noVisibleTasks_expectNoGrouping() throws Exception {
        RunningTaskInfo task1 = makeRunningTaskInfo(1);
        RunningTaskInfo task2 = makeRunningTaskInfo(2);
        RunningTaskInfo task3 = makeRunningTaskInfo(3);
        // Reset visible tasks list
        mRecentTasksControllerReal.onVisibleTasksChanged(List.of());

        // Generate a list with a number of fullscreen tasks
        ArrayList<GroupedTaskInfo> groupedTasks = mRecentTasksControllerReal.generateList(
                List.of(task1, task2, task3),
                "test");

        assertGroupedTasksListEquals(groupedTasks, List.of(
                List.of(task1.taskId),
                List.of(task2.taskId),
                List.of(task3.taskId)));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    public void generateList_fullscreen_singleVisibleTask_expectNoGrouping() throws Exception {
        RunningTaskInfo task1 = makeRunningTaskInfo(1);
        RunningTaskInfo task2 = makeRunningTaskInfo(2);
        RunningTaskInfo task3 = makeRunningTaskInfo(3);

        // Reset visible tasks list
        mRecentTasksControllerReal.onVisibleTasksChanged(List.of(task1));

        // Generate a list with a number of fullscreen tasks
        ArrayList<GroupedTaskInfo> groupedTasks = mRecentTasksControllerReal.generateList(
                List.of(task1, task2, task3),
                "test");

        assertGroupedTasksListEquals(groupedTasks, List.of(
                List.of(task1.taskId),
                List.of(task2.taskId),
                List.of(task3.taskId)));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_TASK_STACK_OBSERVER_IN_SHELL)
    @EnableFlags(com.android.wm.shell.Flags.FLAG_ENABLE_SHELL_TOP_TASK_TRACKING)
    public void generateList_fullscreen_multipleVisibleTasks_expectGrouping() throws Exception {
        RunningTaskInfo task1 = makeRunningTaskInfo(1);
        RunningTaskInfo task2 = makeRunningTaskInfo(2);
        RunningTaskInfo task3 = makeRunningTaskInfo(3);

        // Reset visible tasks list
        mRecentTasksControllerReal.onVisibleTasksChanged(List.of(task1, task2));

        // Generate a list with a number of fullscreen tasks
        ArrayList<GroupedTaskInfo> groupedTasks = mRecentTasksControllerReal.generateList(
                List.of(task1, task2, task3),
                "test");

        assertGroupedTasksListEquals(groupedTasks, List.of(
                List.of(task1.taskId),
                List.of(task2.taskId),
                List.of(task3.taskId)));
    }

    /**
     * Helper to create a task with a given task id.
     */
    private RecentTaskInfo makeTaskInfo(int taskId) {
        RecentTaskInfo info = new RecentTaskInfo();
        info.taskId = taskId;
        info.realActivity = new ComponentName("testPackage", "testClass");
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("com." + taskId, "Activity" + taskId));
        info.baseIntent = intent;
        info.lastNonFullscreenBounds = new Rect();
        return info;
    }

    /**
     * Helper to create a desktop wallpaper activity with a given task id.
     */
    private RecentTaskInfo makeDesktopWallpaperTaskInfo(int taskId) {
        RecentTaskInfo info = makeTaskInfo(taskId);
        info.baseIntent.setComponent(DesktopWallpaperActivity.getWallpaperActivityComponent());
        return info;
    }

    /**
     * Helper to create a running task with a given task id.
     */
    private RunningTaskInfo makeRunningTaskInfo(int taskId) {
        return makeRunningTaskInfo(taskId, new ComponentName("com." + taskId, "Activity" + taskId));
    }

    private RunningTaskInfo makeRunningTaskInfo(int taskId, ComponentName intentComponent) {
        RunningTaskInfo info = new RunningTaskInfo();
        info.taskId = taskId;
        info.realActivity = new ComponentName("testPackage", "testClass");
        Intent intent = new Intent();
        intent.setComponent(intentComponent);
        info.baseIntent = intent;
        info.lastNonFullscreenBounds = new Rect();
        return info;
    }

    private RunningTaskInfo makeDesktopWallpaperActivityTaskInfo(int taskId) {
        return makeRunningTaskInfo(taskId, new ComponentName(SYSTEM_UI_PACKAGE_NAME,
                DesktopWallpaperActivity.class.getName()));
    }

    /**
     * Helper to set the raw task list on the controller.
     */
    private void setRawList(RecentTaskInfo... tasks) {
        doReturn(Arrays.asList(tasks)).when(mActivityTaskManager).getRecentTasks(anyInt(), anyInt(),
                anyInt());
    }

    /**
     * Asserts that the recent tasks matches the given task ids.
     * TODO(346588978): Separate out specific split verification during the iteration below
     *
     * @param expectedTaskIds a list of expected grouped task ids (itself a list of ints)
     */
    private void assertGroupedTasksListEquals(List<GroupedTaskInfo> groupedTasks,
            List<List<Integer>> expectedTaskIds) {
        List<List<Integer>> foundTaskIds = new ArrayList<>();
        for (int i = 0; i < groupedTasks.size(); i++) {
            GroupedTaskInfo groupedTask = groupedTasks.get(i);
            List<Integer> groupedTaskIds = groupedTask.getTaskInfoList().stream()
                    .map(taskInfo -> taskInfo.taskId)
                    .toList();
            foundTaskIds.add(groupedTaskIds);

            if (groupedTask.isBaseType(TYPE_SPLIT)) {
                assertNotNull(groupedTask.getSplitBounds());
                int leftTopTaskId = groupedTask.getSplitBounds().leftTopTaskId;
                int bottomRightTaskId = groupedTask.getSplitBounds().rightBottomTaskId;
                // Unclear if pairs are ordered by split position, most likely not.
                assertTrue(leftTopTaskId == groupedTaskIds.getFirst()
                        || leftTopTaskId == groupedTaskIds.getLast());
                assertTrue(bottomRightTaskId == groupedTaskIds.getFirst()
                        || bottomRightTaskId == groupedTaskIds.getLast());
            }
        }
        List<Integer> flattenedExpectedTaskIds = expectedTaskIds.stream()
                .flatMap(List::stream)
                .toList();
        List<Integer> flattenedFoundTaskIds = foundTaskIds.stream()
                .flatMap(List::stream)
                .toList();
        assertEquals("Expected: "
                        + flattenedExpectedTaskIds.stream().map(String::valueOf).collect(joining())
                        + " Received: "
                        + flattenedFoundTaskIds.stream().map(String::valueOf).collect(joining()),
                flattenedExpectedTaskIds,
                flattenedFoundTaskIds);
    }
}
