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
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_DISALLOW_OVERRIDE_BOUNDS_FOR_CHILDREN;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_LAUNCH_TASK;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_PENDING_INTENT;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_START_SHORTCUT;

import static com.android.wm.shell.Flags.FLAG_ENABLE_FLEXIBLE_TWO_APP_SPLIT;
import static com.android.wm.shell.Flags.FLAG_SPLIT_DISABLE_CHILD_TASK_BOUNDS;
import static com.android.wm.shell.Flags.FLAG_SPLIT_TO_FULL_SET_WINDOW_MODE;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_INDEX_UNDEFINED;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_BOTTOM_OR_RIGHT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_TOP_OR_LEFT;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SPLIT_POSITION_UNDEFINED;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_MAIN;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_SIDE;
import static com.android.wm.shell.splitscreen.SplitScreen.STAGE_TYPE_UNDEFINED;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_DESKTOP_MODE;
import static com.android.wm.shell.splitscreen.SplitScreenController.EXIT_REASON_DRAG_DIVIDER;
import static com.android.wm.shell.transition.Transitions.TRANSIT_SPLIT_DISMISS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.IActivityTaskManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.SurfaceControl;
import android.window.DisplayAreaInfo;
import android.window.RemoteTransition;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;
import android.window.WindowContainerTransaction.HierarchyOp;
import android.window.WindowContainerTransaction.HierarchyOp.HierarchyOpType;

import androidx.annotation.Nullable;
import androidx.test.annotation.UiThreadTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.logging.InstanceId;
import com.android.window.flags2.Flags;
import com.android.wm.shell.MockToken;
import com.android.wm.shell.RootDisplayAreaOrganizer;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestRunningTaskInfoBuilder;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.bubbles.BubbleController;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.LaunchAdjacentController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.common.split.SplitDecorManager;
import com.android.wm.shell.common.split.SplitLayout;
import com.android.wm.shell.common.split.SplitState;
import com.android.wm.shell.desktopmode.DesktopUserRepositories;
import com.android.wm.shell.desktopmode.data.DesktopRepository;
import com.android.wm.shell.shared.TransactionPool;
import com.android.wm.shell.shared.desktopmode.FakeDesktopState;
import com.android.wm.shell.splitscreen.SplitScreen.SplitScreenListener;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.DefaultMixedHandler;
import com.android.wm.shell.transition.FocusTransitionObserver;
import com.android.wm.shell.transition.HomeTransitionObserver;
import com.android.wm.shell.transition.Transitions;

import com.google.android.msdl.domain.MSDLPlayer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Tests for {@link StageCoordinator}
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class StageCoordinatorTests extends ShellTestCase {
    @Rule
    public final SetFlagsRule setFlagsRule = new SetFlagsRule();

    @Mock
    private ShellTaskOrganizer mTaskOrganizer;
    @Mock
    private SyncTransactionQueue mSyncQueue;
    @Mock
    private StageTaskListener mMainStage;
    @Mock
    private StageTaskListener mSideStage;
    @Mock
    private SplitLayout mSplitLayout;
    @Mock
    private DisplayController mDisplayController;
    @Mock
    private DisplayImeController mDisplayImeController;
    @Mock
    private DisplayInsetsController mDisplayInsetsController;
    @Mock
    private TransactionPool mTransactionPool;
    @Mock
    private LaunchAdjacentController mLaunchAdjacentController;
    @Mock
    private DefaultMixedHandler mDefaultMixedHandler;
    @Mock
    private SplitState mSplitState;
    @Mock
    private RootTaskDisplayAreaOrganizer mRootTDAOrganizer;
    @Mock
    private RootDisplayAreaOrganizer mRootDisplayAreaOrganizer;
    @Mock
    private MSDLPlayer mMSDLPlayer;
    @Mock
    private DesktopUserRepositories mDesktopUserRepositories;
    @Mock
    private DesktopRepository mDesktopRepository;
    @Mock
    private BubbleController mBubbleController;
    private FakeDesktopState mDesktopState;
    private IActivityTaskManager mIActivityTaskManager;

    private final Rect mBounds1 = new Rect(10, 20, 30, 40);
    private final Rect mBounds2 = new Rect(5, 10, 15, 20);
    private final Rect mRootBounds = new Rect(0, 0, 45, 60);
    private final int mTaskId = 18;

    private SplitMultiDisplayHelper mSplitMultiDisplayHelper;
    private StageCoordinator mStageCoordinator;
    private SplitScreenTransitions mSplitScreenTransitions;
    private SplitScreenListener mSplitScreenListener;
    private IBinder mBinder;
    private ActivityManager.RunningTaskInfo mRunningTaskInfo;
    private RemoteTransition mRemoteTransition;
    private final TestShellExecutor mMainExecutor = new TestShellExecutor();
    private final ShellExecutor mAnimExecutor = new TestShellExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final DisplayAreaInfo mDisplayAreaInfo = new DisplayAreaInfo(new MockToken().token(),
            DEFAULT_DISPLAY, 0);
    private final ActivityManager.RunningTaskInfo mMainChildTaskInfo =
            new TestRunningTaskInfoBuilder().setVisible(true).build();
    private final ArgumentCaptor<WindowContainerTransaction> mWctCaptor =
            ArgumentCaptor.forClass(WindowContainerTransaction.class);
    private final WindowContainerTransaction mWct = spy(new WindowContainerTransaction());

    @Before
    @UiThreadTest
    public void setup() {
        MockitoAnnotations.initMocks(this);
        Transitions transitions = createTestTransitions();
        WindowContainerToken token = mock(WindowContainerToken.class);
        SurfaceControl dividerLeash = new SurfaceControl.Builder().setName("fakeDivider").build();
        when(mRootDisplayAreaOrganizer.getDisplayTokenForDisplay(anyInt()))
                .thenReturn(mock(WindowContainerToken.class));
        mDesktopState = new FakeDesktopState();

        mStageCoordinator = spy(new StageCoordinator(mContext, DEFAULT_DISPLAY, mSyncQueue,
                mTaskOrganizer, mMainStage, mSideStage, mDisplayController, mDisplayImeController,
                mDisplayInsetsController, mSplitLayout, transitions, mTransactionPool,
                mMainExecutor, mMainHandler, Optional.empty(), mLaunchAdjacentController,
                Optional.empty(), mSplitState, Optional.empty(),
                Optional.of(mDesktopUserRepositories),
                mRootTDAOrganizer,
                mRootDisplayAreaOrganizer, mDesktopState, mIActivityTaskManager, mMSDLPlayer,
                Optional.of(mBubbleController)));
        mSplitScreenTransitions = spy(mStageCoordinator.getSplitTransitions());
        mSplitScreenListener = mock(SplitScreenListener.class);
        mStageCoordinator.setSplitTransitions(mSplitScreenTransitions);
        mBinder = mock(IBinder.class);
        mRunningTaskInfo = mock(ActivityManager.RunningTaskInfo.class);
        mRemoteTransition = mock(RemoteTransition.class);
        mRunningTaskInfo.token = token;

        when(mRemoteTransition.getDebugName()).thenReturn("");
        when(token.asBinder()).thenReturn(mBinder);
        when(mRunningTaskInfo.getToken()).thenReturn(token);
        when(mTaskOrganizer.getRunningTaskInfo(mTaskId)).thenReturn(mRunningTaskInfo);
        when(mTaskOrganizer.startNewTransition(anyInt(), any())).thenReturn(new Binder());
        when(mRootTDAOrganizer.getDisplayAreaInfo(DEFAULT_DISPLAY)).thenReturn(mDisplayAreaInfo);
        mDesktopState.setCanEnterDesktopMode(false);
        when(mDesktopUserRepositories.getCurrent()).thenReturn(mDesktopRepository);

        when(mSplitLayout.getTopLeftBounds()).thenReturn(mBounds1);
        when(mSplitLayout.getBottomRightBounds()).thenReturn(mBounds2);
        when(mSplitLayout.getRootBounds()).thenReturn(mRootBounds);
        when(mSplitLayout.isLeftRightSplit()).thenReturn(false);
        when(mSplitLayout.applyTaskChanges(any(), any(), any())).thenReturn(true);
        when(mSplitLayout.getDividerLeash()).thenReturn(dividerLeash);

        when(mBubbleController.hasBubbles()).thenReturn(false);

        mSplitMultiDisplayHelper = new SplitMultiDisplayHelper(
                mContext.getSystemService(DisplayManager.class));
        mSplitMultiDisplayHelper.setDisplayRootTaskInfo(
                DEFAULT_DISPLAY, new TestRunningTaskInfoBuilder().build());
        SurfaceControl rootLeash = new SurfaceControl.Builder().setName("splitRoot").build();
        mStageCoordinator.onTaskAppeared(mSplitMultiDisplayHelper.getDisplayRootTaskInfo(
                DEFAULT_DISPLAY), rootLeash);

        mSideStage.mRootTaskInfo = new TestRunningTaskInfoBuilder().build();
        mMainStage.mRootTaskInfo = new TestRunningTaskInfoBuilder().build();
        SurfaceControl mainRootLeash = new SurfaceControl.Builder().setName("mainRoot").build();
        SurfaceControl sideRootLeash = new SurfaceControl.Builder().setName("sideRoot").build();
        mMainStage.mRootLeash = mainRootLeash;
        mSideStage.mRootLeash = sideRootLeash;
        SurfaceControl mainDimLayer = new SurfaceControl.Builder().setName("mainDim").build();
        SurfaceControl sideDimLayer = new SurfaceControl.Builder().setName("sideDim").build();
        mMainStage.mDimLayer = mainDimLayer;
        mSideStage.mDimLayer = sideDimLayer;
        doReturn(mock(SplitDecorManager.class)).when(mMainStage).getSplitDecorManager();
        doReturn(mock(SplitDecorManager.class)).when(mSideStage).getSplitDecorManager();

        doAnswer(invocation -> {
            Consumer<ActivityManager.RunningTaskInfo> consumer = invocation.getArgument(0);
            consumer.accept(mMainChildTaskInfo);
            return null;
        }).when(mMainStage).doForAllChildTaskInfos(any());
    }

    @Test
    public void testMoveToStage_splitActiveBackground() {
        when(mStageCoordinator.isSplitActive()).thenReturn(true);
        ActivityManager.RunningTaskInfo rootTaskInfo =
                mSplitMultiDisplayHelper.getDisplayRootTaskInfo(DEFAULT_DISPLAY);

        mStageCoordinator.moveToStage(rootTaskInfo, SPLIT_POSITION_BOTTOM_OR_RIGHT, mWct);

        // TODO(b/349828130) Address this once we remove index_undefined called
        verify(mStageCoordinator).prepareEnterSplitScreen(eq(mWct), eq(rootTaskInfo),
                eq(SPLIT_POSITION_BOTTOM_OR_RIGHT), eq(false), eq(SPLIT_INDEX_UNDEFINED));
        verify(mMainStage).reparentTopTask(eq(mWct));
        assertEquals(SPLIT_POSITION_BOTTOM_OR_RIGHT, mStageCoordinator.getSideStagePosition());
        assertEquals(SPLIT_POSITION_TOP_OR_LEFT, mStageCoordinator.getMainStagePosition());
    }

    @Test
    public void testMoveToStage_splitActiveForeground() {
        when(mStageCoordinator.isSplitActive()).thenReturn(true);
        when(mStageCoordinator.isSplitScreenVisible()).thenReturn(true);
        // Assume current side stage is top or left.
        mStageCoordinator.setSideStagePosition(SPLIT_POSITION_TOP_OR_LEFT, null);

        ActivityManager.RunningTaskInfo rootTaskInfo =
                mSplitMultiDisplayHelper.getDisplayRootTaskInfo(DEFAULT_DISPLAY);
        mStageCoordinator.moveToStage(rootTaskInfo, SPLIT_POSITION_BOTTOM_OR_RIGHT, mWct);

        // TODO(b/349828130) Address this once we remove index_undefined called
        verify(mStageCoordinator).prepareEnterSplitScreen(eq(mWct), eq(rootTaskInfo),
                eq(SPLIT_POSITION_BOTTOM_OR_RIGHT), eq(false), eq(SPLIT_INDEX_UNDEFINED));
        assertEquals(SPLIT_POSITION_BOTTOM_OR_RIGHT, mStageCoordinator.getMainStagePosition());
        assertEquals(SPLIT_POSITION_TOP_OR_LEFT, mStageCoordinator.getSideStagePosition());
    }

    @Test
    public void testMoveToStage_splitInactive() {
        ActivityManager.RunningTaskInfo rootTaskInfo =
                mSplitMultiDisplayHelper.getDisplayRootTaskInfo(DEFAULT_DISPLAY);
        mStageCoordinator.moveToStage(rootTaskInfo, SPLIT_POSITION_BOTTOM_OR_RIGHT, mWct);

        // TODO(b/349828130) Address this once we remove index_undefined called
        verify(mStageCoordinator).prepareEnterSplitScreen(eq(mWct), eq(rootTaskInfo),
                eq(SPLIT_POSITION_BOTTOM_OR_RIGHT), eq(false), eq(SPLIT_INDEX_UNDEFINED));
        assertEquals(SPLIT_POSITION_BOTTOM_OR_RIGHT, mStageCoordinator.getSideStagePosition());
    }

    @Test
    public void testRootTaskInfoChanged_updatesSplitLayout() {
        mStageCoordinator.onTaskInfoChanged(mSplitMultiDisplayHelper.getDisplayRootTaskInfo(
                DEFAULT_DISPLAY));

        verify(mSplitLayout).updateConfiguration(any(Configuration.class), eq(DEFAULT_DISPLAY));
    }

    @Test
    public void testLayoutChanged_topLeftSplitPosition_updatesUnfoldStageBounds() {
        mStageCoordinator.setSideStagePosition(SPLIT_POSITION_TOP_OR_LEFT, null);
        mStageCoordinator.registerSplitScreenListener(mSplitScreenListener);
        clearInvocations(mSplitScreenListener);

        mStageCoordinator.onLayoutSizeChanged(mSplitLayout);

        verify(mSplitScreenListener).onSplitBoundsChanged(mRootBounds, mBounds2, mBounds1);
    }

    @Test
    public void testLayoutChanged_bottomRightSplitPosition_updatesUnfoldStageBounds() {
        mStageCoordinator.setSideStagePosition(SPLIT_POSITION_BOTTOM_OR_RIGHT, null);
        mStageCoordinator.registerSplitScreenListener(mSplitScreenListener);
        clearInvocations(mSplitScreenListener);

        mStageCoordinator.onLayoutSizeChanged(mSplitLayout);

        verify(mSplitScreenListener).onSplitBoundsChanged(mRootBounds, mBounds1, mBounds2);
    }

    @Test
    public void testResolveStartStage_beforeSplitActivated_setsStagePosition() {
        mStageCoordinator.setSideStagePosition(SPLIT_POSITION_TOP_OR_LEFT, null /* wct */);

        mStageCoordinator.resolveStartStage(STAGE_TYPE_UNDEFINED, SPLIT_POSITION_BOTTOM_OR_RIGHT,
                null /* options */, null /* wct */);
        assertEquals(mStageCoordinator.getSideStagePosition(), SPLIT_POSITION_BOTTOM_OR_RIGHT);
        verify(mStageCoordinator).updateActivityOptions(any(), eq(SPLIT_POSITION_BOTTOM_OR_RIGHT));

        mStageCoordinator.resolveStartStage(STAGE_TYPE_UNDEFINED, SPLIT_POSITION_TOP_OR_LEFT,
                null /* options */, null /* wct */);
        assertEquals(mStageCoordinator.getSideStagePosition(), SPLIT_POSITION_TOP_OR_LEFT);
        verify(mStageCoordinator).updateActivityOptions(any(), eq(SPLIT_POSITION_TOP_OR_LEFT));
    }

    @Test
    public void testResolveStartStage_afterSplitActivated_retrievesStagePosition() {
        when(mStageCoordinator.isSplitScreenVisible()).thenReturn(true);
        mStageCoordinator.setSideStagePosition(SPLIT_POSITION_TOP_OR_LEFT, null /* wct */);

        mStageCoordinator.resolveStartStage(STAGE_TYPE_UNDEFINED, SPLIT_POSITION_TOP_OR_LEFT,
                null /* options */, null /* wct */);
        assertEquals(mStageCoordinator.getSideStagePosition(), SPLIT_POSITION_TOP_OR_LEFT);
        verify(mStageCoordinator).updateActivityOptions(any(), eq(SPLIT_POSITION_TOP_OR_LEFT));

        mStageCoordinator.resolveStartStage(STAGE_TYPE_UNDEFINED, SPLIT_POSITION_BOTTOM_OR_RIGHT,
                null /* options */, null /* wct */);
        assertEquals(mStageCoordinator.getMainStagePosition(), SPLIT_POSITION_BOTTOM_OR_RIGHT);
        verify(mStageCoordinator).updateActivityOptions(any(), eq(SPLIT_POSITION_BOTTOM_OR_RIGHT));
    }

    @Test
    public void testResolveStartStage_setsSideStagePosition() {
        mStageCoordinator.setSideStagePosition(SPLIT_POSITION_TOP_OR_LEFT, null /* wct */);

        mStageCoordinator.resolveStartStage(STAGE_TYPE_SIDE, SPLIT_POSITION_BOTTOM_OR_RIGHT,
                null /* options */, null /* wct */);
        assertEquals(mStageCoordinator.getSideStagePosition(), SPLIT_POSITION_BOTTOM_OR_RIGHT);

        mStageCoordinator.resolveStartStage(STAGE_TYPE_MAIN, SPLIT_POSITION_BOTTOM_OR_RIGHT,
                null /* options */, null /* wct */);
        assertEquals(mStageCoordinator.getMainStagePosition(), SPLIT_POSITION_BOTTOM_OR_RIGHT);
    }

    @Test
    public void testResolveStartStage_retrievesStagePosition() {
        mStageCoordinator.setSideStagePosition(SPLIT_POSITION_TOP_OR_LEFT, null /* wct */);

        mStageCoordinator.resolveStartStage(STAGE_TYPE_SIDE, SPLIT_POSITION_UNDEFINED,
                null /* options */, null /* wct */);
        assertEquals(mStageCoordinator.getSideStagePosition(), SPLIT_POSITION_TOP_OR_LEFT);

        mStageCoordinator.resolveStartStage(STAGE_TYPE_MAIN, SPLIT_POSITION_UNDEFINED,
                null /* options */, null /* wct */);
        assertEquals(mStageCoordinator.getMainStagePosition(), SPLIT_POSITION_BOTTOM_OR_RIGHT);
    }

    @Test
    public void testFinishEnterSplitScreen_applySurfaceLayout() {
        mStageCoordinator.finishEnterSplitScreen(new SurfaceControl.Transaction());

        verify(mSplitLayout, atLeastOnce())
                .applySurfaceChanges(any(), any(), any(), any(), any(), eq(false));
    }

    @Test
    public void testAddActivityOptions_addsBackgroundActivitiesFlags() {
        Bundle bundle = mStageCoordinator.resolveStartStage(STAGE_TYPE_MAIN,
                SPLIT_POSITION_UNDEFINED, null /* options */, null /* wct */);
        ActivityOptions options = ActivityOptions.fromBundle(bundle);

        assertThat(options.getLaunchRootTask()).isEqualTo(mMainStage.mRootTaskInfo.token);
        assertThat(options.getPendingIntentBackgroundActivityStartMode())
                .isEqualTo(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS);
    }

    @Test
    public void testExitSplitScreenAfterFoldedAndWakeUp() {
        when(mMainStage.isFocused()).thenReturn(true);
        when(mMainStage.getTopVisibleChildTaskId()).thenReturn(INVALID_TASK_ID);
        mSideStage.mRootTaskInfo = new TestRunningTaskInfoBuilder().setVisible(true).build();
        mMainStage.mRootTaskInfo = new TestRunningTaskInfoBuilder().setVisible(true).build();
        when(mStageCoordinator.isSplitActive()).thenReturn(true);
        when(mStageCoordinator.isSplitScreenVisible()).thenReturn(true);
        when(mStageCoordinator.willSleepOnFold()).thenReturn(true);

        mStageCoordinator.onFoldedStateChanged(true);

        assertEquals(mStageCoordinator.getLastActiveStage(), STAGE_TYPE_MAIN);

        mStageCoordinator.onStartedWakingUp();

        verify(mTaskOrganizer).startNewTransition(eq(TRANSIT_SPLIT_DISMISS), notNull());
    }

    @Test
    @EnableFlags(FLAG_SPLIT_TO_FULL_SET_WINDOW_MODE)
    public void testSplitIntentAndTaskWithPippedApp_launchFullscreen() {
        int taskId = 9;
        mStageCoordinator.setMixedHandler(mDefaultMixedHandler);
        PendingIntent pendingIntent = mock(PendingIntent.class);
        // Test launching second task full screen
        when(mDefaultMixedHandler.isIntentInPip(pendingIntent)).thenReturn(true);
        mStageCoordinator.startIntentAndTask(
                pendingIntent,
                null /*fillInIntent*/,
                null /*option1*/,
                taskId,
                null /*option2*/,
                0 /*splitPosition*/,
                1 /*snapPosition*/,
                mRemoteTransition /*remoteTransition*/,
                null /*instanceId*/);
        verify(mSplitScreenTransitions, times(1)).startFullscreenTransition(mWctCaptor.capture(),
                any());
        WindowContainerTransaction wct1 = mWctCaptor.getValue();
        HierarchyOp op1 = getHierarchyOpForType(wct1, HIERARCHY_OP_TYPE_LAUNCH_TASK);
        assertThat(op1).isNotNull();
        Bundle options1 = op1.getLaunchOptions();
        assertThat(options1).isNotNull();
        assertThat(getLaunchWindowingMode(options1)).isEqualTo(WINDOWING_MODE_FULLSCREEN);

        // Test launching first intent fullscreen
        when(mDefaultMixedHandler.isIntentInPip(pendingIntent)).thenReturn(false);
        when(mDefaultMixedHandler.isTaskInPip(taskId, mTaskOrganizer)).thenReturn(true);
        mStageCoordinator.startIntentAndTask(
                pendingIntent,
                null /*fillInIntent*/,
                null /*option1*/,
                taskId,
                null /*option2*/,
                0 /*splitPosition*/,
                1 /*snapPosition*/,
                mRemoteTransition /*remoteTransition*/,
                null /*instanceId*/);
        verify(mSplitScreenTransitions, times(2)).startFullscreenTransition(mWctCaptor.capture(),
                any());
        WindowContainerTransaction wct2 = mWctCaptor.getValue();
        HierarchyOp op2 = getHierarchyOpForType(wct2, HIERARCHY_OP_TYPE_PENDING_INTENT);
        assertThat(op2).isNotNull();
        Bundle options2 = op2.getLaunchOptions();
        assertThat(options2).isNotNull();
        assertThat(getLaunchWindowingMode(options2)).isEqualTo(WINDOWING_MODE_FULLSCREEN);
    }

    @Test
    @EnableFlags(FLAG_SPLIT_TO_FULL_SET_WINDOW_MODE)
    public void testSplitIntentsWithPippedApp_launchFullscreen() {
        mStageCoordinator.setMixedHandler(mDefaultMixedHandler);
        PendingIntent pendingIntent = mock(PendingIntent.class);
        PendingIntent pendingIntent2 = mock(PendingIntent.class);
        // Test launching second task full screen
        when(mDefaultMixedHandler.isIntentInPip(pendingIntent)).thenReturn(true);
        mStageCoordinator.startIntents(
                pendingIntent,
                null /*fillInIntent*/,
                null /*shortcutInfo1*/,
                new Bundle(),
                pendingIntent2,
                null /*fillInIntent2*/,
                null /*shortcutInfo1*/,
                new Bundle(),
                0 /*splitPosition*/,
                1 /*snapPosition*/,
                mRemoteTransition /*remoteTransition*/,
                null /*instanceId*/);
        verify(mSplitScreenTransitions, times(1)).startFullscreenTransition(mWctCaptor.capture(),
                any());
        WindowContainerTransaction wct1 = mWctCaptor.getValue();
        HierarchyOp op1 = getHierarchyOpForType(wct1, HIERARCHY_OP_TYPE_PENDING_INTENT);
        assertThat(op1).isNotNull();
        Bundle options1 = op1.getLaunchOptions();
        assertThat(options1).isNotNull();
        assertThat(getLaunchWindowingMode(options1)).isEqualTo(WINDOWING_MODE_FULLSCREEN);

        // Test launching first intent fullscreen
        when(mDefaultMixedHandler.isIntentInPip(pendingIntent)).thenReturn(false);
        when(mDefaultMixedHandler.isIntentInPip(pendingIntent2)).thenReturn(true);
        mStageCoordinator.startIntents(
                pendingIntent,
                null /*fillInIntent*/,
                null /*shortcutInfo1*/,
                new Bundle(),
                pendingIntent2,
                null /*fillInIntent2*/,
                null /*shortcutInfo1*/,
                new Bundle(),
                0 /*splitPosition*/,
                1 /*snapPosition*/,
                mRemoteTransition /*remoteTransition*/,
                null /*instanceId*/);
        verify(mSplitScreenTransitions, times(2)).startFullscreenTransition(mWctCaptor.capture(),
                any());
        WindowContainerTransaction wct2 = mWctCaptor.getValue();
        HierarchyOp op2 = getHierarchyOpForType(wct2, HIERARCHY_OP_TYPE_PENDING_INTENT);
        assertThat(op2).isNotNull();
        Bundle options2 = op2.getLaunchOptions();
        assertThat(options2).isNotNull();
        assertThat(getLaunchWindowingMode(options2)).isEqualTo(WINDOWING_MODE_FULLSCREEN);
    }

    @Test
    public void startTask_ensureWindowingModeCleared() {
        mStageCoordinator.startTask(mTaskId, SPLIT_POSITION_TOP_OR_LEFT, null /*options*/,
                null, SPLIT_INDEX_UNDEFINED);
        verify(mSplitScreenTransitions).startEnterTransition(anyInt(),
                mWctCaptor.capture(), any(), any(), anyInt(), anyBoolean(), anyInt());

        int windowingMode = mWctCaptor.getValue().getChanges().get(mBinder).getWindowingMode();
        assertEquals(windowingMode, WINDOWING_MODE_UNDEFINED);
    }

    @Test
    @EnableFlags({Flags.FLAG_ENABLE_FULL_SCREEN_WINDOW_ON_REMOVING_SPLIT_SCREEN_STAGE_BUGFIX,
            Flags.FLAG_ENABLE_MULTIPLE_DESKTOPS_BACKEND})
    public void startTasksOnSingleFreeformWindow_ensureWindowingModeClearedAndLaunchFullScreen() {
        mDisplayAreaInfo.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_FREEFORM);
        when(mRunningTaskInfo.getWindowingMode()).thenReturn(WINDOWING_MODE_FREEFORM);
        mDesktopRepository.addTask(DEFAULT_DISPLAY, mTaskId, false, mBounds1);
        when(mDesktopRepository.isActiveTask(mTaskId)).thenReturn(true);

        mStageCoordinator.startTasks(mTaskId, null, INVALID_TASK_ID, null,
                SPLIT_POSITION_TOP_OR_LEFT, SNAP_TO_2_50_50, mRemoteTransition,
                InstanceId.fakeInstanceId(0));

        verify(mSplitScreenTransitions).startFullscreenTransition(mWctCaptor.capture(), any());
        int windowingMode = mWctCaptor.getValue().getChanges().get(mBinder).getWindowingMode();
        assertEquals(windowingMode, WINDOWING_MODE_UNDEFINED);
        assertThat(mWctCaptor.getValue().getHierarchyOps().stream().filter(
                        HierarchyOp::isReparent).findFirst().get()
                .getNewParent()).isNull();
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_FULL_SCREEN_WINDOW_ON_REMOVING_SPLIT_SCREEN_STAGE_BUGFIX)
    public void startTasksOnSingleFreeformWindow_flagDisabled_noChangeToWindowingModeInWct() {
        mDisplayAreaInfo.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_FREEFORM);
        when(mRunningTaskInfo.getWindowingMode()).thenReturn(WINDOWING_MODE_FREEFORM);

        mStageCoordinator.startTasks(mTaskId, null, INVALID_TASK_ID, null,
                SPLIT_POSITION_TOP_OR_LEFT, SNAP_TO_2_50_50, mRemoteTransition,
                InstanceId.fakeInstanceId(0));

        verify(mSplitScreenTransitions).startFullscreenTransition(mWctCaptor.capture(), any());
        assertThat(mWctCaptor.getValue().getChanges()).isEmpty();
    }

    @Test
    public void testDismiss_freeformDisplay() {
        mDisplayAreaInfo.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_FREEFORM);
        when(mStageCoordinator.isSplitActive()).thenReturn(true);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        mStageCoordinator.prepareExitSplitScreen(STAGE_TYPE_MAIN, wct, EXIT_REASON_DRAG_DIVIDER);

        assertEquals(wct.getChanges().get(mMainChildTaskInfo.token.asBinder()).getWindowingMode(),
                WINDOWING_MODE_FULLSCREEN);
    }

    @Test
    public void testDismiss_freeformDisplayToDesktop() {
        mDisplayAreaInfo.configuration.windowConfiguration.setWindowingMode(
                WINDOWING_MODE_FREEFORM);
        when(mStageCoordinator.isSplitActive()).thenReturn(true);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        mStageCoordinator.prepareExitSplitScreen(STAGE_TYPE_MAIN, wct, EXIT_REASON_DESKTOP_MODE);

        WindowContainerTransaction.Change c =
                wct.getChanges().get(mMainChildTaskInfo.token.asBinder());
        assertFalse(c != null && c.getWindowingMode() == WINDOWING_MODE_FULLSCREEN);
    }

    @Test
    public void testDismiss_fullscreenDisplay() {
        when(mStageCoordinator.isSplitActive()).thenReturn(true);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        mStageCoordinator.prepareExitSplitScreen(STAGE_TYPE_MAIN, wct, EXIT_REASON_DRAG_DIVIDER);

        assertEquals(wct.getChanges().get(mMainChildTaskInfo.token.asBinder()).getWindowingMode(),
                WINDOWING_MODE_UNDEFINED);
    }

    @Test
    public void testDismiss_fullscreenDisplayToDesktop() {
        when(mStageCoordinator.isSplitActive()).thenReturn(true);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        mStageCoordinator.prepareExitSplitScreen(STAGE_TYPE_MAIN, wct, EXIT_REASON_DESKTOP_MODE);

        WindowContainerTransaction.Change c =
                wct.getChanges().get(mMainChildTaskInfo.token.asBinder());
        assertFalse(c != null && c.getWindowingMode() == WINDOWING_MODE_FULLSCREEN);
    }

    @Test
    public void updateActivityOptions_withBubbles_setsLaunchBounds() {
        when(mBubbleController.hasBubbles()).thenReturn(true);
        Bundle bundle = new Bundle();

        mStageCoordinator.updateActivityOptions(bundle, SPLIT_POSITION_TOP_OR_LEFT);
        ActivityOptions options = ActivityOptions.fromBundle(bundle);

        assertThat(options.getLaunchBounds()).isEqualTo(new Rect());
    }

    @Test
    public void updateActivityOptions_noBubbles_doesNotSetLaunchBounds() {
        when(mBubbleController.hasBubbles()).thenReturn(false);
        Bundle bundle = new Bundle();

        mStageCoordinator.updateActivityOptions(bundle, SPLIT_POSITION_TOP_OR_LEFT);
        ActivityOptions options = ActivityOptions.fromBundle(bundle);

        assertThat(options.getLaunchBounds()).isNull();
    }

    @Test
    @EnableFlags(FLAG_SPLIT_DISABLE_CHILD_TASK_BOUNDS)
    public void onRootTaskAppeared_disableChildTaskBounds() {
        // root tasks for stages are created in setUp, mark them set
        mMainStage.mHasRootTask = true;
        mSideStage.mHasRootTask = true;
        ActivityManager.RunningTaskInfo rootTaskInfo =
                mSplitMultiDisplayHelper.getDisplayRootTaskInfo(DEFAULT_DISPLAY);
        mStageCoordinator.onRootTaskAppeared(rootTaskInfo);

        ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mSyncQueue).queue(wctCaptor.capture());

        WindowContainerTransaction capturedWct = wctCaptor.getValue();
        List<HierarchyOp> disableChildBoundsOps = capturedWct.getHierarchyOps().stream()
                .filter(op -> op.getType()
                        == HIERARCHY_OP_TYPE_DISALLOW_OVERRIDE_BOUNDS_FOR_CHILDREN)
                .toList();
        assertThat(disableChildBoundsOps).hasSize(2);
        HierarchyOp op = disableChildBoundsOps.getFirst();
        assertThat(op.getContainer()).isEqualTo(mMainStage.mRootTaskInfo.token.asBinder());
        assertThat(op.getDisallowOverrideBoundsForChildren()).isTrue();
        op = disableChildBoundsOps.get(1);
        assertThat(op.getContainer()).isEqualTo(mSideStage.mRootTaskInfo.token.asBinder());
        assertThat(op.getDisallowOverrideBoundsForChildren()).isTrue();
    }

    @Test
    @EnableFlags(com.android.window.flags2.Flags.FLAG_ENABLE_MULTI_DISPLAY_SPLIT)
    public void moveSplitScreenRoot_whenFlagEnabled_doesNothing() {
        SplitMultiDisplayHelper mockHelper = mock(SplitMultiDisplayHelper.class);
        mStageCoordinator.setSplitMultiDisplayHelper(mockHelper);

        mStageCoordinator.prepareMovingSplitScreenRoot(mWct, DEFAULT_DISPLAY + 1);

        verify(mockHelper, never()).getCachedOrSystemDisplayIds();
        verify(mRootTDAOrganizer, never()).getDisplayAreaInfo(anyInt());
        verify(mWct, never()).reparent(any(), any(), anyBoolean());
    }

    @Test(expected = IllegalStateException.class)
    @DisableFlags(com.android.window.flags2.Flags.FLAG_ENABLE_MULTI_DISPLAY_SPLIT)
    public void moveSplitScreenRoot_whenRootNotFound_throwsException() {
        SplitMultiDisplayHelper mockHelper = mock(SplitMultiDisplayHelper.class);
        mStageCoordinator.setSplitMultiDisplayHelper(mockHelper);
        when(mockHelper.getCachedOrSystemDisplayIds()).thenReturn(
                new ArrayList<>(List.of(DEFAULT_DISPLAY)));
        when(mockHelper.getDisplayRootTaskInfo(anyInt())).thenReturn(null);

        mStageCoordinator.prepareMovingSplitScreenRoot(mWct, DEFAULT_DISPLAY + 1);
    }

    @Test
    @DisableFlags(com.android.window.flags2.Flags.FLAG_ENABLE_MULTI_DISPLAY_SPLIT)
    public void moveSplitScreenRoot_whenTargetIsSameDisplay_doesNothing() {
        SplitMultiDisplayHelper mockHelper = mock(SplitMultiDisplayHelper.class);
        mStageCoordinator.setSplitMultiDisplayHelper(mockHelper);
        final int targetDisplayId = DEFAULT_DISPLAY;
        ActivityManager.RunningTaskInfo currentRootTaskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(targetDisplayId)
                .build();
        when(mockHelper.getCachedOrSystemDisplayIds()).thenReturn(
                new ArrayList<>(List.of(targetDisplayId)));
        when(mockHelper.getDisplayRootTaskInfo(targetDisplayId))
                .thenReturn(currentRootTaskInfo);

        mStageCoordinator.prepareMovingSplitScreenRoot(mWct, targetDisplayId);

        verify(mWct, never()).reparent(any(), any(), anyBoolean());
    }

    @Test
    @DisableFlags(com.android.window.flags2.Flags.FLAG_ENABLE_MULTI_DISPLAY_SPLIT)
    public void moveSplitScreenRoot_whenTargetIsDifferentDisplay_reparentsRoot() {
        SplitMultiDisplayHelper mockHelper = mock(SplitMultiDisplayHelper.class);
        mStageCoordinator.setSplitMultiDisplayHelper(mockHelper);
        final int currentDisplayId = DEFAULT_DISPLAY;
        final int targetDisplayId = DEFAULT_DISPLAY + 1;

        WindowContainerToken currentRootToken = mock(WindowContainerToken.class);
        when(mRootDisplayAreaOrganizer.getDisplayTokenForDisplay(anyInt()))
                .thenReturn(mock(WindowContainerToken.class));
        ActivityManager.RunningTaskInfo currentRootTaskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(currentDisplayId)
                .setToken(currentRootToken)
                .build();
        when(mockHelper.getCachedOrSystemDisplayIds())
                .thenReturn(new ArrayList<>(List.of(currentDisplayId, targetDisplayId)));
        when(mockHelper.getDisplayRootTaskInfo(currentDisplayId))
                .thenReturn(currentRootTaskInfo);

        WindowContainerToken targetDisplayAreaToken = new MockToken().token();
        DisplayAreaInfo targetDisplayAreaInfo = new DisplayAreaInfo(targetDisplayAreaToken,
                targetDisplayId, 0);
        when(mRootTDAOrganizer.getDisplayAreaInfo(targetDisplayId))
                .thenReturn(targetDisplayAreaInfo);

        mStageCoordinator.prepareMovingSplitScreenRoot(mWct, targetDisplayId);

        verify(mWct).reparent(eq(currentRootToken), eq(targetDisplayAreaToken), eq(true));
    }

    @Test
    @DisableFlags(com.android.window.flags2.Flags.FLAG_ENABLE_MULTI_DISPLAY_SPLIT)
    public void moveSplitScreenRoot_whenTargetDisplayAreaNotFound_doesNothing() {
        SplitMultiDisplayHelper mockHelper = mock(SplitMultiDisplayHelper.class);
        mStageCoordinator.setSplitMultiDisplayHelper(mockHelper);

        final int currentDisplayId = DEFAULT_DISPLAY;
        final int targetDisplayId = DEFAULT_DISPLAY + 1;

        // Setup current root, but no target display area
        WindowContainerToken currentRootToken = mock(WindowContainerToken.class);
        ActivityManager.RunningTaskInfo currentRootTaskInfo = new TestRunningTaskInfoBuilder()
                .setDisplayId(currentDisplayId)
                .setToken(currentRootToken)
                .build();
        when(mockHelper.getCachedOrSystemDisplayIds())
                .thenReturn(new ArrayList<>(List.of(currentDisplayId, targetDisplayId)));
        when(mockHelper.getDisplayRootTaskInfo(currentDisplayId))
                .thenReturn(currentRootTaskInfo);

        when(mRootTDAOrganizer.getDisplayAreaInfo(targetDisplayId)).thenReturn(null);

        mStageCoordinator.prepareMovingSplitScreenRoot(mWct, targetDisplayId);

        verify(mWct, never()).reparent(any(), any(), anyBoolean());
    }

    @Test
    public void testOnChildTaskMovedToBubble_mainStageHasTask_dismissesSplitWithMainOnTop() {
        when(mMainStage.isActive()).thenReturn(true);
        when(mMainStage.getChildCount()).thenReturn(1);
        when(mSideStage.getChildCount()).thenReturn(0);
        when(mStageCoordinator.isSplitScreenVisible()).thenReturn(true);
        mStageCoordinator.onChildTaskMovedToBubble(mSideStage, /* taskId= */ 8);
        verify(mSplitScreenTransitions).startDismissTransition(any(), any(), eq(STAGE_TYPE_MAIN),
                eq(SplitScreenController.EXIT_REASON_CHILD_TASK_ENTER_BUBBLE));
    }

    @Test
    public void testOnChildTaskMovedToBubble_noTasksInSplit_dismissesSplit() {
        when(mMainStage.isActive()).thenReturn(true);
        when(mMainStage.getChildCount()).thenReturn(0);
        when(mSideStage.getChildCount()).thenReturn(0);
        when(mStageCoordinator.isSplitScreenVisible()).thenReturn(true);
        mStageCoordinator.onChildTaskMovedToBubble(mSideStage, /* taskId= */ 8);
        verify(mSplitScreenTransitions).startDismissTransition(any(), any(),
                eq(STAGE_TYPE_UNDEFINED),
                eq(SplitScreenController.EXIT_REASON_CHILD_TASK_ENTER_BUBBLE));
    }

    @Test
    public void testOnChildTaskMovedToBubble_stageHasMoreTasks_doesNothing() {
        when(mMainStage.isActive()).thenReturn(true);
        when(mSideStage.getChildCount()).thenReturn(1);
        mStageCoordinator.onChildTaskMovedToBubble(mSideStage, /* taskId= */ 8);
        verify(mSplitScreenTransitions, never()).startDismissTransition(any(), any(), anyInt(),
                anyInt());
    }

    @Test
    public void testOnChildTaskMovedToBubble_splitNotVisible_dismissesSplitWithUndefinedTopStage() {
        when(mMainStage.isActive()).thenReturn(true);
        when(mMainStage.getChildCount()).thenReturn(1);
        when(mSideStage.getChildCount()).thenReturn(0);
        when(mStageCoordinator.isSplitScreenVisible()).thenReturn(false);

        mStageCoordinator.onChildTaskMovedToBubble(mSideStage, /* taskId= */ 8);

        verify(mSplitScreenTransitions).startDismissTransition(any(), any(),
                eq(STAGE_TYPE_UNDEFINED),
                eq(SplitScreenController.EXIT_REASON_CHILD_TASK_ENTER_BUBBLE));
    }

    @Test
    @EnableFlags(FLAG_ENABLE_FLEXIBLE_TWO_APP_SPLIT)
    public void startTasks_withFlexibleTwoAppSplit_hidesDividerWhenStagesInactive() {
        // Setup: Main stage is inactive, which should trigger the condition.
        when(mMainStage.isActive()).thenReturn(false);
        when(mSideStage.isActive()).thenReturn(true);
        doReturn(true).when(mStageCoordinator).isSplitScreenVisible();

        // Action: Start two tasks.
        mStageCoordinator.startTasks(1 /* taskId1 */, null /* options1 */, 2 /* taskId2 */,
                null /* options2 */, SPLIT_POSITION_TOP_OR_LEFT, SNAP_TO_2_50_50,
                null /* remoteTransition */, null /* instanceId */);

        // Verification: The divider should be hidden because a stage is inactive.
        verify(mStageCoordinator).setDividerVisibility(eq(false), eq(null));
    }

    @Test
    @EnableFlags(FLAG_ENABLE_FLEXIBLE_TWO_APP_SPLIT)
    public void startTasks_withFlexibleTwoAppSplit_hidesDividerWhenNotVisible() {
        // Setup: Both stages are active, but split screen is not visible.
        when(mMainStage.isActive()).thenReturn(true);
        when(mSideStage.isActive()).thenReturn(true);
        doReturn(false).when(mStageCoordinator).isSplitScreenVisible();

        // Action: Start two tasks.
        mStageCoordinator.startTasks(1 /* taskId1 */, null /* options1 */, 2 /* taskId2 */,
                null /* options2 */, SPLIT_POSITION_TOP_OR_LEFT, SNAP_TO_2_50_50,
                null /* remoteTransition */, null /* instanceId */);

        // Verification: The divider should be hidden because split screen is not visible.
        verify(mStageCoordinator).setDividerVisibility(eq(false), eq(null));
    }

    @Test
    @EnableFlags(FLAG_ENABLE_FLEXIBLE_TWO_APP_SPLIT)
    public void startTasks_withFlexibleTwoAppSplit_doesNotHideDividerWhenActiveAndVisible() {
        // Setup: Both stages are active and split screen is visible.
        when(mMainStage.isActive()).thenReturn(true);
        when(mSideStage.isActive()).thenReturn(true);
        doReturn(true).when(mStageCoordinator).isSplitScreenVisible();

        // Action: Start two tasks.
        mStageCoordinator.startTasks(1 /* taskId1 */, null /* options1 */, 2 /* taskId2 */,
                null /* options2 */, SPLIT_POSITION_TOP_OR_LEFT, SNAP_TO_2_50_50,
                null /* remoteTransition */, null /* instanceId */);

        // Verification: The divider should not be hidden.
        verify(mStageCoordinator, never()).setDividerVisibility(eq(false), any());
    }

    @Test
    @DisableFlags(FLAG_ENABLE_FLEXIBLE_TWO_APP_SPLIT)
    public void startTasks_withoutFlexibleTwoAppSplit_doesNotHideDivider() {
        // Setup: Flag is disabled, and conditions for hiding are met.
        when(mMainStage.isActive()).thenReturn(false);
        doReturn(false).when(mStageCoordinator).isSplitScreenVisible();

        // Action: Start two tasks.
        mStageCoordinator.startTasks(1 /* taskId1 */, null /* options1 */, 2 /* taskId2 */,
                null /* options2 */, SPLIT_POSITION_TOP_OR_LEFT, SNAP_TO_2_50_50,
                null /* remoteTransition */, null /* instanceId */);

        // Verification: The divider should not be hidden because the flag is disabled.
        verify(mStageCoordinator, never()).setDividerVisibility(eq(false), any());
    }

    @Test
    public void testAddExitForBubblesIfNeeded_splitVisible_hasStageToTop() {
        when(mStageCoordinator.isSplitActive()).thenReturn(true);
        when(mStageCoordinator.isSplitScreenVisible()).thenReturn(true);
        doReturn(STAGE_TYPE_MAIN).when(mStageCoordinator).getStageOfTask(anyInt());

        android.window.TransitionRequestInfo request =
                mock(android.window.TransitionRequestInfo.class);
        ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder().build();
        when(request.getTriggerTask()).thenReturn(taskInfo);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        mStageCoordinator.addExitForBubblesIfNeeded(request, wct);

        verify(mStageCoordinator).prepareExitSplitScreen(eq(STAGE_TYPE_SIDE),
                eq(wct), anyInt());
    }

    @Test
    public void testAddExitForBubblesIfNeeded_splitNotVisible_noStageToTop() {
        when(mStageCoordinator.isSplitActive()).thenReturn(true);
        when(mStageCoordinator.isSplitScreenVisible()).thenReturn(false);
        doReturn(STAGE_TYPE_MAIN).when(mStageCoordinator).getStageOfTask(anyInt());

        android.window.TransitionRequestInfo request =
                mock(android.window.TransitionRequestInfo.class);
        ActivityManager.RunningTaskInfo taskInfo = new TestRunningTaskInfoBuilder().build();
        when(request.getTriggerTask()).thenReturn(taskInfo);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        mStageCoordinator.addExitForBubblesIfNeeded(request, wct);

        verify(mStageCoordinator).prepareExitSplitScreen(eq(STAGE_TYPE_UNDEFINED),
                eq(wct), anyInt());
    }

    @Test
    @EnableFlags(FLAG_SPLIT_TO_FULL_SET_WINDOW_MODE)
    public void startTasks_withOneTask_setsFullscreenWindowingMode() {
        mStageCoordinator.startTasks(mTaskId, null /* options1 */, INVALID_TASK_ID,
                null /* options2 */, SPLIT_POSITION_TOP_OR_LEFT, SNAP_TO_2_50_50,
                mRemoteTransition, null /* instanceId */);

        verify(mSplitScreenTransitions).startFullscreenTransition(mWctCaptor.capture(), any());
        WindowContainerTransaction wct = mWctCaptor.getValue();
        HierarchyOp op = getHierarchyOpForType(wct, HIERARCHY_OP_TYPE_LAUNCH_TASK);
        assertThat(op).isNotNull();
        Bundle options = op.getLaunchOptions();
        assertThat(options).isNotNull();
        assertThat(getLaunchWindowingMode(options)).isEqualTo(WINDOWING_MODE_FULLSCREEN);
    }

    @Test
    @EnableFlags(FLAG_SPLIT_TO_FULL_SET_WINDOW_MODE)
    public void startShortcutAndTask_withOnlyShortcut_setsFullscreenWindowingMode() {
        ShortcutInfo shortcutInfo = new ShortcutInfo.Builder(mContext, "test").build();
        mStageCoordinator.startShortcutAndTask(shortcutInfo, null /* options1 */, INVALID_TASK_ID,
                null /* options2 */, SPLIT_POSITION_TOP_OR_LEFT, SNAP_TO_2_50_50, mRemoteTransition,
                null /* instanceId */);

        verify(mSplitScreenTransitions).startFullscreenTransition(mWctCaptor.capture(), any());
        WindowContainerTransaction wct = mWctCaptor.getValue();
        HierarchyOp op = getHierarchyOpForType(wct, HIERARCHY_OP_TYPE_START_SHORTCUT);
        assertThat(op).isNotNull();
        Bundle options = op.getLaunchOptions();
        assertThat(options).isNotNull();
        assertThat(getLaunchWindowingMode(options)).isEqualTo(WINDOWING_MODE_FULLSCREEN);

    }

    @Test
    @EnableFlags(FLAG_SPLIT_TO_FULL_SET_WINDOW_MODE)
    public void startIntents_withOneIntent_setsFullscreenWindowingMode() {
        PendingIntent pendingIntent = mock(PendingIntent.class);
        mStageCoordinator.startIntents(pendingIntent, new Intent(), null /* shortcutInfo1 */,
                null /* options1 */, null /* pendingIntent2 */, null /* fillInIntent2 */,
                null /* shortcutInfo2 */, null /* options2 */, SPLIT_POSITION_TOP_OR_LEFT,
                SNAP_TO_2_50_50, mRemoteTransition, null /* instanceId */);

        verify(mSplitScreenTransitions).startFullscreenTransition(mWctCaptor.capture(), any());
        WindowContainerTransaction wct = mWctCaptor.getValue();
        HierarchyOp op = getHierarchyOpForType(wct, HIERARCHY_OP_TYPE_PENDING_INTENT);
        assertThat(op).isNotNull();
        Bundle options = op.getLaunchOptions();
        assertThat(options).isNotNull();
        assertThat(getLaunchWindowingMode(options)).isEqualTo(WINDOWING_MODE_FULLSCREEN);
    }

    private Transitions createTestTransitions() {
        ShellInit shellInit = new ShellInit(mMainExecutor);
        final Transitions t = new Transitions(mContext, shellInit, mock(ShellController.class),
                mTaskOrganizer, mTransactionPool, mock(DisplayController.class),
                mDisplayInsetsController, mMainExecutor, mMainHandler, mAnimExecutor,
                mock(HomeTransitionObserver.class), mock(FocusTransitionObserver.class));
        shellInit.init();
        return t;
    }

    @Nullable
    private static HierarchyOp getHierarchyOpForType(WindowContainerTransaction wct,
            @HierarchyOpType int type) {
        return wct.getHierarchyOps().stream()
                .filter(o -> o.getType() == type)
                .findFirst().orElse(null);
    }

    private static int getLaunchWindowingMode(Bundle options) {
        return options.getInt("android.activity.windowingMode", 0);
    }

    private static class TestSplitSelectListener implements SplitScreen.SplitSelectListener {
        private final boolean mAlwaysEnter;

        TestSplitSelectListener(boolean alwaysEnter) {
            mAlwaysEnter = alwaysEnter;
        }

        @Override
        public boolean onRequestEnterSplitSelect(ActivityManager.RunningTaskInfo taskInfo,
                int splitPosition, Rect taskBounds, boolean startRecents,
                @Nullable WindowContainerTransaction withRecentsWct) {
            return mAlwaysEnter;
        }
    }
}
