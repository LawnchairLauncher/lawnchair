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

package com.android.wm.shell.bubbles;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.window.flags2.Flags.FLAG_ROOT_TASK_FOR_BUBBLE;
import static com.android.wm.shell.Flags.FLAG_ENABLE_BUBBLE_BAR;
import static com.android.wm.shell.Flags.FLAG_ENABLE_CREATE_ANY_BUBBLE;
import static com.android.wm.shell.bubbles.util.BubbleTestUtils.verifyEnterBubbleTransaction;
import static com.android.wm.shell.transition.Transitions.TRANSIT_BUBBLE_CONVERT_FLOATING_TO_BAR;
import static com.android.wm.shell.transition.Transitions.TRANSIT_CONVERT_TO_BUBBLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.view.SurfaceControl;
import android.view.ViewRootImpl;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.core.animation.AnimatorTestRule;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.icons.BubbleIconFactory;
import com.android.wm.shell.MockToken;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestSyncExecutor;
import com.android.wm.shell.bubbles.BubbleTransitions.DraggedBubbleIconToFullscreen;
import com.android.wm.shell.bubbles.appinfo.PackageManagerBubbleAppInfoProvider;
import com.android.wm.shell.bubbles.bar.BubbleBarExpandedView;
import com.android.wm.shell.bubbles.bar.BubbleBarLayerView;
import com.android.wm.shell.common.HomeIntentProvider;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.SyncTransactionQueue;
import com.android.wm.shell.shared.bubbles.BubbleBarLocation;
import com.android.wm.shell.taskview.TaskView;
import com.android.wm.shell.taskview.TaskViewRepository;
import com.android.wm.shell.taskview.TaskViewTaskController;
import com.android.wm.shell.taskview.TaskViewTransitions;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Tests of {@link BubbleTransitions}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:BubbleTransitionsTest
 */
@SmallTest
public class BubbleTransitionsTest extends ShellTestCase {

    @Rule public final AnimatorTestRule mAnimatorTestRule = new AnimatorTestRule();

    private static final int FULLSCREEN_TASK_WIDTH = 200;
    private static final int FULLSCREEN_TASK_HEIGHT = 100;

    @Mock
    private BubbleData mBubbleData;
    @Mock
    private Bubble mBubble;
    @Mock
    private TaskView mTaskView;
    @Mock
    private TaskViewTaskController mTaskViewTaskController;
    @Mock
    private Transitions mTransitions;
    @Mock
    private SyncTransactionQueue mSyncQueue;
    @Mock
    private BubbleExpandedViewManager mExpandedViewManager;
    @Mock
    private BubblePositioner mBubblePositioner;
    @Mock
    private BubbleStackView mStackView;
    @Mock
    private BubbleBarLayerView mLayerView;
    @Mock
    private BubbleIconFactory mIconFactory;
    @Mock
    private HomeIntentProvider mHomeIntentProvider;
    @Mock
    private ShellTaskOrganizer mTaskOrganizer;
    @Mock
    private BubbleController mBubbleController;

    private TaskViewTransitions mTaskViewTransitions;
    private TaskViewRepository mRepository;
    private BubbleTransitions mBubbleTransitions;
    private BubbleTaskViewFactory mTaskViewFactory;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mRepository = new TaskViewRepository();
        final ShellExecutor syncExecutor = new TestSyncExecutor();

        when(mTransitions.getMainExecutor()).thenReturn(syncExecutor);
        mTaskViewTransitions = new TaskViewTransitions(mTransitions, mRepository, mTaskOrganizer,
                mSyncQueue);
        mBubbleTransitions = new BubbleTransitions(mContext, mTransitions, mTaskOrganizer,
                mRepository, mBubbleData, mTaskViewTransitions,
                new PackageManagerBubbleAppInfoProvider());
        mBubbleTransitions.setBubbleController(mBubbleController);
        mTaskViewFactory = () -> {
            TaskViewTaskController taskViewTaskController = new TaskViewTaskController(
                    mContext, mTaskOrganizer, mTaskViewTransitions, mSyncQueue);
            TaskView taskView = new TaskView(mContext, mTaskViewTransitions,
                    taskViewTaskController);
            return new BubbleTaskView(taskView, syncExecutor);
        };
        setUpBubbleBarExpandedView(mBubble);
    }

    private ActivityManager.RunningTaskInfo setupBubble() {
        return setupBubble(mBubble, mTaskView, mTaskViewTaskController);
    }

    private ActivityManager.RunningTaskInfo setupBubble(Bubble bubble, TaskView taskView,
            TaskViewTaskController taskViewTaskController) {
        final ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        final WindowContainerToken token = new MockToken().token();
        taskInfo.token = token;
        taskInfo.configuration.windowConfiguration.setActivityType(ACTIVITY_TYPE_STANDARD);
        doReturn(taskInfo).when(taskViewTaskController).getTaskInfo();
        doReturn(taskViewTaskController).when(taskView).getController();
        doReturn(taskView).when(bubble).getTaskView();
        doReturn(taskInfo).when(taskView).getTaskInfo();
        mRepository.add(taskViewTaskController);
        return taskInfo;
    }

    private ActivityManager.RunningTaskInfo setupAppBubble() {
        return setupAppBubble(mBubble, mTaskView, mTaskViewTaskController);
    }

    private ActivityManager.RunningTaskInfo setupAppBubble(Bubble bubble, TaskView taskView,
            TaskViewTaskController taskViewTaskController) {
        doReturn(true).when(bubble).isApp();
        doReturn(new Intent()).when(bubble).getIntent();
        doReturn(new UserHandle(0)).when(bubble).getUser();
        final ActivityManager.RunningTaskInfo taskInfo = setupBubble(
                bubble, taskView, taskViewTaskController);
        doReturn(true).when(mBubbleController).shouldBeAppBubble(taskInfo);
        return taskInfo;
    }

    private TaskView setUpBubbleTaskView(Bubble bubble) {
        final SurfaceControl taskViewLeash = new SurfaceControl.Builder().setName("taskViewLeash")
                .build();
        final TaskView taskView = mock(TaskView.class);
        doReturn(taskViewLeash).when(taskView).getSurfaceControl();
        doReturn(taskView).when(bubble).getTaskView();
        return taskView;
    }

    private BubbleBarExpandedView setUpBubbleBarExpandedView(Bubble bubble) {
        final BubbleBarExpandedView bbev = mock(BubbleBarExpandedView.class);
        final ViewRootImpl vri = mock(ViewRootImpl.class);
        doReturn(vri).when(bbev).getViewRootImpl();
        doReturn(bbev).when(bubble).getBubbleBarExpandedView();
        return bbev;
    }

    private TransitionInfo setupFullscreenTaskTransition(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl taskLeash, SurfaceControl snapshot) {
        final TransitionInfo info = new TransitionInfo(TRANSIT_CONVERT_TO_BUBBLE, 0);
        final TransitionInfo.Change chg = new TransitionInfo.Change(taskInfo.token, taskLeash);
        chg.setTaskInfo(taskInfo);
        chg.setMode(TRANSIT_CHANGE);
        chg.setStartAbsBounds(new Rect(0, 0, FULLSCREEN_TASK_WIDTH, FULLSCREEN_TASK_HEIGHT));
        chg.setSnapshot(snapshot, /* luma= */ 0f);
        info.addChange(chg);
        info.addRoot(new TransitionInfo.Root(0, mock(SurfaceControl.class), 0, 0));
        return info;
    }

    private TransitionInfo setupConvertTransition(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl taskLeash, SurfaceControl snapshot, IBinder launchCookieBinder) {
        final TransitionInfo info = new TransitionInfo(TRANSIT_OPEN, 0);
        final TransitionInfo.Change chg = new TransitionInfo.Change(taskInfo.token, taskLeash);
        chg.setTaskInfo(taskInfo);
        chg.setMode(TRANSIT_CHANGE);
        chg.setStartAbsBounds(new Rect(0, 0, FULLSCREEN_TASK_WIDTH, FULLSCREEN_TASK_HEIGHT));
        if (snapshot != null) {
            chg.setSnapshot(snapshot, /* luma= */ 0f);
        }
        // Add the launch cookie to the task info
        taskInfo.launchCookies.add(launchCookieBinder);
        info.addChange(chg);
        info.addRoot(new TransitionInfo.Root(0, mock(SurfaceControl.class), 0, 0));
        return info;
    }

    @Test
    public void testConvertToBubble() {
        // Basic walk-through of convert-to-bubble transition stages
        when(mTransitions.startTransition(anyInt(), any(), any())).thenReturn(mock(IBinder.class));
        final ActivityManager.RunningTaskInfo taskInfo = setupBubble();
        final BubbleTransitions.BubbleTransition bt = mBubbleTransitions.startConvertToBubble(
                mBubble, taskInfo, mExpandedViewManager, mTaskViewFactory, mBubblePositioner,
                mStackView, mLayerView, mIconFactory, mHomeIntentProvider, null /* dragData */,
                false /* inflateSync */);
        final BubbleTransitions.ConvertToBubble ctb = (BubbleTransitions.ConvertToBubble) bt;
        ctb.onInflated(mBubble);
        when(mLayerView.canExpandView(any())).thenReturn(true);
        // Check that home task is launched as part of the transition
        verify(mHomeIntentProvider).addLaunchHomePendingIntent(any(), anyInt(), anyInt());
        verify(mTransitions).startTransition(anyInt(), any(), eq(ctb));
        verify(mBubble).setPreparingTransition(eq(bt));
        // Ensure we are communicating with the taskviewtransitions queue
        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        final SurfaceControl taskLeash = new SurfaceControl.Builder().setName("taskLeash").build();
        final SurfaceControl snapshot = new SurfaceControl.Builder().setName("snapshot").build();
        final TransitionInfo info = setupFullscreenTaskTransition(taskInfo, taskLeash, snapshot);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        final boolean[] finishCalled = new boolean[]{false};
        final Transitions.TransitionFinishCallback finishCb = wct -> {
            assertThat(finishCalled[0]).isFalse();
            finishCalled[0] = true;
        };
        ctb.startAnimation(ctb.mTransition, info, startT, finishT, finishCb);
        assertThat(mTaskViewTransitions.hasPending()).isFalse();

        verify(startT).setPosition(taskLeash, 0, 0);
        verify(startT).setPosition(snapshot, 0, 0);

        verify(mBubbleData).notificationEntryUpdated(eq(mBubble), anyBoolean(), anyBoolean());

        clearInvocations(mBubble);
        verify(mBubble, never()).setPreparingTransition(any());

        ctb.surfaceCreated();
        // Check that preparing transition is not reset before continueExpand is called
        verify(mBubble, never()).setPreparingTransition(any());
        ArgumentCaptor<Runnable> animCb = ArgumentCaptor.forClass(Runnable.class);
        verify(mLayerView).animateConvert(any(), any(), anyFloat(), any(), any(), animCb.capture());

        // continueExpand is now called, check that preparing transition is cleared
        ctb.continueExpand();
        verify(mBubble).setPreparingTransition(isNull());

        assertThat(finishCalled[0]).isFalse();
        animCb.getValue().run();
        assertThat(finishCalled[0]).isTrue();
    }

    @Test
    public void testConvertToBubble_excludesTaskFromRecents() {
        final ActivityManager.RunningTaskInfo taskInfo = setupBubble();
        final BubbleTransitions.BubbleTransition bt = mBubbleTransitions.startConvertToBubble(
                mBubble, taskInfo, mExpandedViewManager, mTaskViewFactory, mBubblePositioner,
                mStackView, mLayerView, mIconFactory, mHomeIntentProvider, null /* dragData */,
                true /* inflateSync */);
        final BubbleTransitions.ConvertToBubble ctb = (BubbleTransitions.ConvertToBubble) bt;

        ctb.onInflated(mBubble);
        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mTransitions).startTransition(anyInt(), wctCaptor.capture(), eq(ctb));

        // Verify that the WCT has the task force exclude from recents.
        final WindowContainerTransaction wct = wctCaptor.getValue();
        final Map<IBinder, WindowContainerTransaction.Change> chgs = wct.getChanges();
        final boolean hasForceExcludedFromRecents = chgs.entrySet().stream()
                .filter((entry) -> entry.getKey().equals(taskInfo.token.asBinder()))
                .anyMatch((entry) -> entry.getValue().getForceExcludedFromRecents());
        assertThat(hasForceExcludedFromRecents).isTrue();
    }

    @Test
    public void testConvertToBubble_disallowFlagLaunchAdjacent() {
        final ActivityManager.RunningTaskInfo taskInfo = setupBubble();
        final BubbleTransitions.BubbleTransition bt = mBubbleTransitions.startConvertToBubble(
                mBubble, taskInfo, mExpandedViewManager, mTaskViewFactory, mBubblePositioner,
                mStackView, mLayerView, mIconFactory, mHomeIntentProvider, null /* dragData */,
                true /* inflateSync */);
        final BubbleTransitions.ConvertToBubble ctb = (BubbleTransitions.ConvertToBubble) bt;

        ctb.onInflated(mBubble);
        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mTransitions).startTransition(anyInt(), wctCaptor.capture(), eq(ctb));

        // Verify that the WCT has the disallow-launch-adjacent hierarchy op
        final WindowContainerTransaction wct = wctCaptor.getValue();
        verifyEnterBubbleTransaction(wct, taskInfo.token.asBinder(), true /* isAppBubble */);
    }

    @Test
    public void testConvertToBubble_drag() {
        final ActivityManager.RunningTaskInfo taskInfo = setupBubble();

        final PointF dragPosition = new PointF(10f, 20f);
        final BubbleTransitions.DragData dragData = new BubbleTransitions.DragData(
                /* releasedOnLeft= */ false, /* taskScale= */ 0.5f, /* cornerRadius= */ 10f,
                dragPosition);

        final BubbleTransitions.BubbleTransition bt = mBubbleTransitions.startConvertToBubble(
                mBubble, taskInfo, mExpandedViewManager, mTaskViewFactory, mBubblePositioner,
                mStackView, mLayerView, mIconFactory, mHomeIntentProvider, dragData,
                false /* inflateSync */);
        final BubbleTransitions.ConvertToBubble ctb = (BubbleTransitions.ConvertToBubble) bt;

        ctb.onInflated(mBubble);
        verify(mHomeIntentProvider).addLaunchHomePendingIntent(any(), anyInt(), anyInt());
        verify(mTransitions).startTransition(anyInt(), any(), eq(ctb));

        final SurfaceControl taskLeash = new SurfaceControl.Builder().setName("taskLeash").build();
        final SurfaceControl snapshot = new SurfaceControl.Builder().setName("snapshot").build();
        final TransitionInfo info = setupFullscreenTaskTransition(taskInfo, taskLeash, snapshot);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        final Transitions.TransitionFinishCallback finishCb = wct -> {};
        ctb.startAnimation(ctb.mTransition, info, startT, finishT, finishCb);

        // Verify that snapshot and task are placed at where the drag ended
        verify(startT).setPosition(taskLeash, dragPosition.x, dragPosition.y);
        verify(startT).setPosition(snapshot, dragPosition.x, dragPosition.y);
        // Snapshot has the scale of the dragged task
        verify(startT).setScale(snapshot, dragData.getTaskScale(), dragData.getTaskScale());
        // Snapshot has dragged task corner radius
        verify(startT).setCornerRadius(snapshot, dragData.getCornerRadius());
    }

    @Test
    public void testConvertFromBubble() {
        when(mTransitions.startTransition(anyInt(), any(), any())).thenReturn(mock(IBinder.class));
        final ActivityManager.RunningTaskInfo taskInfo = setupBubble();
        final BubbleTransitions.BubbleTransition bt = mBubbleTransitions.startConvertFromBubble(
                mBubble, taskInfo);
        final BubbleTransitions.ConvertFromBubble cfb = (BubbleTransitions.ConvertFromBubble) bt;
        verify(mTransitions).startTransition(anyInt(), any(), eq(cfb));
        verify(mBubble).setPreparingTransition(eq(bt));
        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        final TransitionInfo info = new TransitionInfo(TRANSIT_CHANGE, 0);
        final TransitionInfo.Change chg = new TransitionInfo.Change(taskInfo.token,
                mock(SurfaceControl.class));
        chg.setMode(TRANSIT_CHANGE);
        chg.setTaskInfo(taskInfo);
        info.addChange(chg);
        info.addRoot(new TransitionInfo.Root(0, mock(SurfaceControl.class), 0, 0));
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        final Transitions.TransitionFinishCallback finishCb = wct -> {};
        cfb.startAnimation(cfb.mTransition, info, startT, finishT, finishCb);

        // Can really only verify that it interfaces with the taskViewTransitions queue.
        // The actual functioning of this is tightly-coupled with SurfaceFlinger and renderthread
        // in order to properly synchronize surface manipulation with drawing and thus can't be
        // directly tested.
        assertThat(mTaskViewTransitions.hasPending()).isFalse();
    }

    @Test
    public void testConvertFromBubble_resetsExcludeTaskFromRecents() {
        final ActivityManager.RunningTaskInfo taskInfo = setupBubble();
        final BubbleTransitions.BubbleTransition bt = mBubbleTransitions.startConvertFromBubble(
                mBubble, taskInfo);

        final BubbleTransitions.ConvertFromBubble cfb = (BubbleTransitions.ConvertFromBubble) bt;
        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mTransitions).startTransition(anyInt(), wctCaptor.capture(), eq(cfb));

        // Verify that the WCT has the task force exclude from recents
        final WindowContainerTransaction wct = wctCaptor.getValue();
        final Map<IBinder, WindowContainerTransaction.Change> chgs = wct.getChanges();
        assertThat(chgs).hasSize(1);
        final WindowContainerTransaction.Change chg = chgs.get(taskInfo.token.asBinder());
        assertThat(chg).isNotNull();
        assertThat(chg.getForceExcludedFromRecents()).isFalse();
    }

    @Test
    public void convertDraggedBubbleToFullscreen() {
        final ActivityManager.RunningTaskInfo taskInfo = setupBubble();
        final SurfaceControl.Transaction animT = mock(SurfaceControl.Transaction.class);
        final BubbleTransitions.TransactionProvider transactionProvider = () -> animT;
        final DraggedBubbleIconToFullscreen bt =
                mBubbleTransitions.new DraggedBubbleIconToFullscreen(
                        mBubble, new Point(100, 50), transactionProvider);
        verify(mTransitions).startTransition(anyInt(), any(), eq(bt));

        final SurfaceControl taskLeash = new SurfaceControl.Builder().setName("taskLeash").build();
        final TransitionInfo info = new TransitionInfo(TRANSIT_TO_FRONT, 0);
        final TransitionInfo.Change chg = new TransitionInfo.Change(taskInfo.token, taskLeash);
        chg.setMode(TRANSIT_TO_FRONT);
        chg.setTaskInfo(taskInfo);
        info.addChange(chg);
        info.addRoot(new TransitionInfo.Root(0, mock(SurfaceControl.class), 0, 0));
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        final boolean[] transitionFinished = {false};
        final Transitions.TransitionFinishCallback finishCb = wct -> transitionFinished[0] = true;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            bt.startAnimation(bt.mTransition, info, startT, finishT, finishCb);
            mAnimatorTestRule.advanceTimeBy(250);
        });
        verify(startT).setScale(taskLeash, 0, 0);
        verify(startT).setPosition(taskLeash, 100, 50);
        verify(startT).apply();
        verify(animT).setScale(taskLeash, 1, 1);
        verify(animT).setPosition(taskLeash, 0, 0);
        verify(animT, atLeastOnce()).apply();
        verify(animT).close();
        assertThat(mTaskViewTransitions.hasPending()).isFalse();
        assertThat(transitionFinished[0]).isTrue();
    }

    @Test
    public void convertDraggedBubbleToFullscreen_resetsExcludeTaskFromRecents() {
        final ActivityManager.RunningTaskInfo taskInfo = setupBubble();
        final SurfaceControl.Transaction animT = mock(SurfaceControl.Transaction.class);
        final BubbleTransitions.TransactionProvider transactionProvider = () -> animT;

        final DraggedBubbleIconToFullscreen bt =
                mBubbleTransitions.new DraggedBubbleIconToFullscreen(
                        mBubble, new Point(100, 50), transactionProvider);
        final ArgumentCaptor<WindowContainerTransaction> wctCaptor =
                ArgumentCaptor.forClass(WindowContainerTransaction.class);
        verify(mTransitions).startTransition(anyInt(), wctCaptor.capture(), eq(bt));

        // Verify that the WCT has the task force exclude from recents
        final WindowContainerTransaction wct = wctCaptor.getValue();
        final Map<IBinder, WindowContainerTransaction.Change> chgs = wct.getChanges();
        assertThat(chgs).hasSize(1);
        final WindowContainerTransaction.Change chg = chgs.get(taskInfo.token.asBinder());
        assertThat(chg).isNotNull();
        assertThat(chg.getForceExcludedFromRecents()).isFalse();
    }

    @Test
    public void convertFloatingBubbleToFullscreen() {
        final BubbleExpandedView bev = mock(BubbleExpandedView.class);
        final ViewRootImpl vri = mock(ViewRootImpl.class);
        when(bev.getViewRootImpl()).thenReturn(vri);
        when(mBubble.getBubbleBarExpandedView()).thenReturn(null);
        when(mBubble.getExpandedView()).thenReturn(bev);
        when(mTransitions.startTransition(anyInt(), any(), any())).thenReturn(mock(IBinder.class));

        final ActivityManager.RunningTaskInfo taskInfo = setupBubble();
        final BubbleTransitions.BubbleTransition bt = mBubbleTransitions.startConvertFromBubble(
                mBubble, taskInfo);
        final BubbleTransitions.ConvertFromBubble cfb = (BubbleTransitions.ConvertFromBubble) bt;
        verify(mTransitions).startTransition(anyInt(), any(), eq(cfb));
        verify(mBubble).setPreparingTransition(eq(bt));
        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        final TransitionInfo info = new TransitionInfo(TRANSIT_CHANGE, 0);
        final TransitionInfo.Change chg = new TransitionInfo.Change(taskInfo.token,
                mock(SurfaceControl.class));
        chg.setMode(TRANSIT_CHANGE);
        chg.setTaskInfo(taskInfo);
        info.addChange(chg);
        info.addRoot(new TransitionInfo.Root(0, mock(SurfaceControl.class), 0, 0));
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        final Transitions.TransitionFinishCallback finishCb = wct -> {};
        cfb.startAnimation(cfb.mTransition, info, startT, finishT, finishCb);

        // Can really only verify that it interfaces with the taskViewTransitions queue.
        // The actual functioning of this is tightly-coupled with SurfaceFlinger and renderthread
        // in order to properly synchronize surface manipulation with drawing and thus can't be
        // directly tested.
        assertThat(mTaskViewTransitions.hasPending()).isFalse();
    }

    @Test
    public void convertFloatingBubbleToBarBubble() {
        final ActivityManager.RunningTaskInfo taskInfo = setupBubble();

        final BubbleBarExpandedView expandedView = setUpBubbleBarExpandedView(mBubble);
        final ViewRootImpl viewRoot = expandedView.getViewRootImpl();
        final SurfaceControl bubblesWindowSurface = mock(SurfaceControl.class);
        when(viewRoot.updateAndGetBoundsLayer(any(SurfaceControl.Transaction.class)))
                .thenReturn(bubblesWindowSurface);
        when(expandedView.getRestingCornerRadius()).thenReturn(6f);

        final SurfaceControl taskViewSurface = mock(SurfaceControl.class);
        when(mTaskView.getSurfaceControl()).thenReturn(taskViewSurface);

        doAnswer(invocation -> {
            Rect r = invocation.getArgument(0);
            r.set(0, 0, 50, 100);
            return null;
        }).when(mBubblePositioner).getTaskViewRestBounds(any());

        final SurfaceControl.Transaction transaction = mock(SurfaceControl.Transaction.class);
        final BubbleTransitions.TransactionProvider transactionProvider = () -> transaction;

        final BubbleTransitions.FloatingToBarConversion bt =
                mBubbleTransitions.new FloatingToBarConversion(mBubble, transactionProvider,
                        mBubblePositioner);

        verify(mBubble).setPreparingTransition(bt);
        verify(mTransitions, never()).startTransition(anyInt(), any(), eq(bt));

        final IBinder transition = mock(IBinder.class);
        when(mTransitions
                .startTransition(eq(TRANSIT_BUBBLE_CONVERT_FLOATING_TO_BAR), any(), eq(bt)))
                .thenReturn(transition);

        bt.continueExpand();
        bt.continueConvert(mLayerView);

        verify(mTransitions)
                .startTransition(eq(TRANSIT_BUBBLE_CONVERT_FLOATING_TO_BAR), any(), eq(bt));
        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        final SurfaceControl taskLeash = new SurfaceControl.Builder().setName("taskLeash").build();
        final TransitionInfo info = new TransitionInfo(TRANSIT_CHANGE, 0);
        final TransitionInfo.Change chg = new TransitionInfo.Change(taskInfo.token, taskLeash);
        chg.setMode(TRANSIT_CHANGE);
        chg.setTaskInfo(taskInfo);
        info.addChange(chg);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> bt.startAnimation(bt.mTransition, info, startT, finishT, wct -> {}));

        verify(transaction).reparent(taskViewSurface, bubblesWindowSurface);
        verify(transaction).reparent(taskLeash, taskViewSurface);
        verify(transaction).setPosition(taskLeash, 0, 0);
        verify(transaction).setCornerRadius(taskLeash, 6f);
        verify(transaction).setWindowCrop(taskLeash, 50, 100);
        verify(transaction).apply();
        verify(finishT).reparent(taskLeash, taskViewSurface);
        verify(finishT).setPosition(taskLeash, 0, 0);
        verify(finishT).setWindowCrop(taskLeash, 50, 100);

        TaskViewRepository.TaskViewState state = mRepository.byTaskView(mTaskViewTaskController);
        assertThat(state).isNotNull();
        assertThat(state.mVisible).isTrue();
        assertThat(state.mBounds).isEqualTo(new Rect(0, 0, 50, 100));

        verify(mBubble).setPreparingTransition(null);
        assertThat(mTaskViewTransitions.hasPending()).isFalse();
    }

    @Test
    public void convertFloatingBubbleToBarBubble_mergeWithUnfold() {
        setupBubble();
        final BubbleBarExpandedView expandedView = setUpBubbleBarExpandedView(mBubble);
        final ViewRootImpl viewRoot = expandedView.getViewRootImpl();
        final SurfaceControl bubblesWindowSurface = mock(SurfaceControl.class);
        when(viewRoot.updateAndGetBoundsLayer(any(SurfaceControl.Transaction.class)))
                .thenReturn(bubblesWindowSurface);
        when(expandedView.getRestingCornerRadius()).thenReturn(6f);

        final SurfaceControl taskViewSurface = mock(SurfaceControl.class);
        when(mTaskView.getSurfaceControl()).thenReturn(taskViewSurface);

        doAnswer(invocation -> {
            Rect r = invocation.getArgument(0);
            r.set(0, 0, 50, 100);
            return null;
        }).when(mBubblePositioner).getTaskViewRestBounds(any());

        final SurfaceControl.Transaction transaction = mock(SurfaceControl.Transaction.class);
        final BubbleTransitions.TransactionProvider transactionProvider = () -> transaction;

        final BubbleTransitions.FloatingToBarConversion bt =
                mBubbleTransitions.new FloatingToBarConversion(mBubble, transactionProvider,
                        mBubblePositioner);

        verify(mBubble).setPreparingTransition(bt);
        verify(mTransitions, never()).startTransition(anyInt(), any(), eq(bt));

        final IBinder transition = mock(IBinder.class);
        when(mTransitions
                .startTransition(eq(TRANSIT_BUBBLE_CONVERT_FLOATING_TO_BAR), any(), eq(bt)))
                .thenReturn(transition);

        bt.continueExpand();
        bt.continueConvert(mLayerView);

        verify(mTransitions)
                .startTransition(eq(TRANSIT_BUBBLE_CONVERT_FLOATING_TO_BAR), any(), eq(bt));
        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        final SurfaceControl taskLeash = new SurfaceControl.Builder().setName("taskLeash").build();
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        bt.mergeWithUnfold(taskLeash, finishT);

        verify(transaction).reparent(taskViewSurface, bubblesWindowSurface);
        verify(transaction).reparent(taskLeash, taskViewSurface);
        verify(transaction).setPosition(taskLeash, 0, 0);
        verify(transaction).setCornerRadius(taskLeash, 6f);
        verify(transaction).setWindowCrop(taskLeash, 50, 100);
        verify(transaction).apply();
        verify(finishT).reparent(taskLeash, taskViewSurface);
        verify(finishT).setPosition(taskLeash, 0, 0);
        verify(finishT).setWindowCrop(taskLeash, 50, 100);

        TaskViewRepository.TaskViewState state = mRepository.byTaskView(mTaskViewTaskController);
        assertThat(state).isNotNull();
        assertThat(state.mVisible).isTrue();
        assertThat(state.mBounds).isEqualTo(new Rect(0, 0, 50, 100));

        verify(mBubble).setPreparingTransition(null);
        assertThat(mTaskViewTransitions.hasPending()).isFalse();
    }

    @Test
    public void convertFloatingBubbleToBarBubble_continueConvertCalledMultipleTimes() {
        setupBubble();

        final BubbleTransitions.FloatingToBarConversion bt =
                mBubbleTransitions.new FloatingToBarConversion(mBubble, mBubblePositioner);

        verify(mTransitions, never()).startTransition(anyInt(), any(), eq(bt));
        bt.continueExpand();

        bt.continueConvert(mLayerView);
        // call continue convert again
        bt.continueConvert(mLayerView);

        // verify we only started the transition once
        verify(mTransitions, times(1))
                .startTransition(eq(TRANSIT_BUBBLE_CONVERT_FLOATING_TO_BAR), any(), eq(bt));
    }

    @Test
    public void convertFloatingBubbleToBarBubble_mustContinueExpand() {
        setupBubble();

        final BubbleTransitions.FloatingToBarConversion bt =
                mBubbleTransitions.new FloatingToBarConversion(mBubble, mBubblePositioner);
        bt.continueConvert(mLayerView);

        verify(mTransitions, never()).startTransition(anyInt(), any(), eq(bt));

        bt.continueExpand();

        // verify we only started the transition once
        verify(mTransitions, times(1))
                .startTransition(eq(TRANSIT_BUBBLE_CONVERT_FLOATING_TO_BAR), any(), eq(bt));
    }

    @Test
    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    public void notifyUnfoldTransitionStarting_bubbleBarEnabled_enqueuesExternal() {
        setupBubble();
        final IBinder unfoldTransition = mock(IBinder.class);
        when(mBubbleData.getSelectedBubble()).thenReturn(mBubble);
        when(mBubbleData.isExpanded()).thenReturn(true);
        mBubbleTransitions.notifyUnfoldTransitionStarting(unfoldTransition);

        assertThat(mTaskViewTransitions.hasPending()).isTrue();
    }

    @Test
    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    public void notifyUnfoldTransitionStarting_bubbleBarEnabled_noSelectedBubble() {
        final IBinder unfoldTransition = mock(IBinder.class);
        when(mBubbleData.getSelectedBubble()).thenReturn(null);
        mBubbleTransitions.notifyUnfoldTransitionStarting(unfoldTransition);

        assertThat(mTaskViewTransitions.hasPending()).isFalse();
    }

    @Test
    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    public void notifyUnfoldTransitionStarting_bubblesCollapsed_doesNotEnqueueExternal() {
        setupBubble();
        final IBinder unfoldTransition = mock(IBinder.class);
        when(mBubbleData.getSelectedBubble()).thenReturn(mBubble);
        when(mBubbleData.isExpanded()).thenReturn(false);
        mBubbleTransitions.notifyUnfoldTransitionStarting(unfoldTransition);

        assertThat(mTaskViewTransitions.hasPending()).isFalse();
    }

    @Test
    @DisableFlags(FLAG_ENABLE_BUBBLE_BAR)
    public void notifyUnfoldTransitionStarting_bubbleBarDisabled() {
        setupBubble();
        final IBinder unfoldTransition = mock(IBinder.class);
        when(mBubbleData.getSelectedBubble()).thenReturn(mBubble);
        mBubbleTransitions.notifyUnfoldTransitionStarting(unfoldTransition);

        assertThat(mTaskViewTransitions.hasPending()).isFalse();
    }

    @Test
    @EnableFlags(FLAG_ENABLE_BUBBLE_BAR)
    public void notifyUnfoldTransitionFinished_removesExternal() {
        setupBubble();
        final IBinder unfoldTransition = mock(IBinder.class);
        when(mBubbleData.getSelectedBubble()).thenReturn(mBubble);
        when(mBubbleData.isExpanded()).thenReturn(true);
        mBubbleTransitions.notifyUnfoldTransitionStarting(unfoldTransition);

        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        mBubbleTransitions.notifyUnfoldTransitionFinished(unfoldTransition);
        assertThat(mTaskViewTransitions.hasPending()).isFalse();
    }

    @Test
    public void testLaunchOrConvert_convertTaskToBubble() {
        final ActivityManager.RunningTaskInfo taskInfo = setupAppBubble();

        when(mLayerView.canExpandView(mBubble)).thenReturn(true);

        final BubbleTransitions.LaunchOrConvertToBubble bt =
                (BubbleTransitions.LaunchOrConvertToBubble) mBubbleTransitions
                        .startLaunchIntoOrConvertToBubble(
                                mBubble, mExpandedViewManager, mTaskViewFactory, mBubblePositioner,
                                mStackView, mLayerView, mIconFactory, false /* inflateSync */,
                                BubbleBarLocation.RIGHT);

        bt.onInflated(mBubble);

        verify(mBubble).setPreparingTransition(bt);

        // Check that an external transition was enqueued, and a launch cookie was set.
        assertThat(mTaskViewTransitions.hasPending()).isTrue();
        assertThat(bt.mLaunchCookie).isNotNull();

        // Prepare for startAnimation call
        final SurfaceControl taskLeash = new SurfaceControl.Builder().setName("taskLeash").build();
        final SurfaceControl snapshot = new SurfaceControl.Builder().setName("snapshot").build();
        final TransitionInfo info = setupConvertTransition(taskInfo, taskLeash, snapshot,
                bt.mLaunchCookie.binder);

        final IBinder transitionToken = mock(IBinder.class);
        bt.mPlayingTransition = transitionToken;

        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        final boolean[] finishCalled = new boolean[] { false };
        final Transitions.TransitionFinishCallback finishCb = wct -> {
            assertThat(finishCalled[0]).isFalse();
            finishCalled[0] = true;
        };

        // Start playing the transition
        bt.startAnimation(transitionToken, info, startT, finishT, finishCb);

        assertThat(mTaskViewTransitions.hasPending()).isFalse();
        // Verify startT modifications (position, snapshot handling)
        verify(startT).setPosition(taskLeash, 0, 0);
        verify(startT).show(snapshot);
        verify(startT).reparent(eq(snapshot), any(SurfaceControl.class));
        verify(startT).setPosition(snapshot, 0 , 0);
        verify(startT).setLayer(snapshot, Integer.MAX_VALUE);

        // Bubble data gets updated with the correct bubble bar location
        verify(mBubbleData).notificationEntryUpdated(eq(mBubble), anyBoolean(), anyBoolean(),
                eq(BubbleBarLocation.RIGHT));

        // Verify preparingTransition is not cleared yet
        verify(mBubble, never()).setPreparingTransition(null);

        // Simulate surfaceCreated and continueExpand so the animation can start
        bt.surfaceCreated();
        bt.continueExpand();

        // Verify preparingTransition is cleared now
        verify(mBubble).setPreparingTransition(null);

        // Verify animateConvert is called due to TRANSIT_CHANGE and snapshot exists
        ArgumentCaptor<Runnable> animCb = ArgumentCaptor.forClass(Runnable.class);
        verify(mLayerView).animateConvert(
                any(),
                // Check that task bounds are passed in as the initial bounds
                eq(new Rect(0, 0, FULLSCREEN_TASK_WIDTH, FULLSCREEN_TASK_HEIGHT)),
                eq(1f),
                eq(snapshot),
                eq(taskLeash),
                animCb.capture()
        );

        // Trigger animation callback to finish
        assertThat(finishCalled[0]).isFalse();
        animCb.getValue().run();
        assertThat(finishCalled[0]).isTrue();

        // Verify that the playing transition and pending cookie are removed
        assertThat(mBubbleTransitions.mEnterTransitions).doesNotContainKey(transitionToken);
        assertThat(mBubbleTransitions.mPendingEnterTransitions).doesNotContainKey(
                bt.mLaunchCookie.binder);
    }

    @Test
    public void testLaunchOrConvert_convertTaskToBubble_collapseWhileExpanding() {
        final ActivityManager.RunningTaskInfo taskInfo = setupAppBubble();

        when(mLayerView.canExpandView(mBubble)).thenReturn(true);

        final BubbleTransitions.LaunchOrConvertToBubble bt =
                (BubbleTransitions.LaunchOrConvertToBubble) mBubbleTransitions
                        .startLaunchIntoOrConvertToBubble(
                                mBubble, mExpandedViewManager, mTaskViewFactory, mBubblePositioner,
                                mStackView, mLayerView, mIconFactory, false /* inflateSync */,
                                BubbleBarLocation.RIGHT);

        bt.onInflated(mBubble);

        verify(mBubble).setPreparingTransition(bt);

        // Check that an external transition was enqueued, and a launch cookie was set.
        assertThat(mTaskViewTransitions.hasPending()).isTrue();
        assertThat(bt.mLaunchCookie).isNotNull();

        // Prepare for startAnimation call
        final SurfaceControl taskLeash = new SurfaceControl.Builder().setName("taskLeash").build();
        final SurfaceControl snapshot = new SurfaceControl.Builder().setName("snapshot").build();
        final TransitionInfo info = setupConvertTransition(taskInfo, taskLeash, snapshot,
                bt.mLaunchCookie.binder);

        final IBinder transitionToken = mock(IBinder.class);
        bt.mPlayingTransition = transitionToken;

        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        final boolean[] finishCalled = new boolean[] { false };
        final Transitions.TransitionFinishCallback finishCb = wct -> {
            assertThat(finishCalled[0]).isFalse();
            finishCalled[0] = true;
        };

        // Start playing the transition
        bt.startAnimation(transitionToken, info, startT, finishT, finishCb);

        assertThat(mTaskViewTransitions.hasPending()).isFalse();
        // Verify startT modifications (position, snapshot handling)
        verify(startT).setPosition(taskLeash, 0, 0);
        verify(startT).show(snapshot);
        verify(startT).reparent(eq(snapshot), any(SurfaceControl.class));
        verify(startT).setPosition(snapshot, 0 , 0);
        verify(startT).setLayer(snapshot, Integer.MAX_VALUE);

        // Bubble data gets updated with the correct bubble bar location
        verify(mBubbleData).notificationEntryUpdated(eq(mBubble), anyBoolean(), anyBoolean(),
                eq(BubbleBarLocation.RIGHT));

        // Simulate surfaceCreated so the animation can start but don't call continueExpand
        bt.surfaceCreated();

        // Verify preparingTransition is not cleared yet
        verify(mBubble, never()).setPreparingTransition(null);

        // Verify animateConvert is called due to TRANSIT_CHANGE and snapshot exists
        ArgumentCaptor<Runnable> animCb = ArgumentCaptor.forClass(Runnable.class);
        verify(mLayerView).animateConvert(
                any(),
                // Check that task bounds are passed in as the initial bounds
                eq(new Rect(0, 0, FULLSCREEN_TASK_WIDTH, FULLSCREEN_TASK_HEIGHT)),
                eq(1f),
                eq(snapshot),
                eq(taskLeash),
                animCb.capture()
        );

        // Trigger animation callback to finish
        assertThat(finishCalled[0]).isFalse();
        animCb.getValue().run();
        assertThat(finishCalled[0]).isTrue();

        // Verify that the playing transition and pending cookie are removed
        assertThat(mBubbleTransitions.mEnterTransitions).doesNotContainKey(transitionToken);
        assertThat(mBubbleTransitions.mPendingEnterTransitions).doesNotContainKey(
                bt.mLaunchCookie.binder);
        // Verify we cleared the preparing transition after the animation finished
        verify(mBubble).setPreparingTransition(null);
    }

    @Test
    public void testLaunchOrConvert_convertTaskToBubble_noSnapshot() {
        final ActivityManager.RunningTaskInfo taskInfo = setupAppBubble();

        when(mLayerView.canExpandView(mBubble)).thenReturn(true);

        final BubbleTransitions.LaunchOrConvertToBubble bt =
                (BubbleTransitions.LaunchOrConvertToBubble) mBubbleTransitions
                        .startLaunchIntoOrConvertToBubble(
                                mBubble, mExpandedViewManager, mTaskViewFactory, mBubblePositioner,
                                mStackView, mLayerView, mIconFactory, false /* inflateSync */,
                                BubbleBarLocation.RIGHT);

        bt.onInflated(mBubble);

        // Prepare for startAnimation call
        final SurfaceControl taskLeash = new SurfaceControl.Builder().setName("taskLeash").build();
        // Snapshot is not available
        final SurfaceControl snapshot = null;
        final TransitionInfo info = setupConvertTransition(taskInfo, taskLeash, snapshot,
                bt.mLaunchCookie.binder);

        final IBinder transitionToken = mock(IBinder.class);
        bt.mPlayingTransition = transitionToken;

        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        final boolean[] finishCalled = new boolean[] { false };
        final Transitions.TransitionFinishCallback finishCb = wct -> {
            assertThat(finishCalled[0]).isFalse();
            finishCalled[0] = true;
        };

        // Start playing the transition
        bt.startAnimation(transitionToken, info, startT, finishT, finishCb);

        // Verify startT modifications (position only)
        verify(startT).setPosition(taskLeash, 0, 0);

        // Simulate surfaceCreated and continueExpand so the animation can start
        bt.surfaceCreated();
        bt.continueExpand();

        // Verify animateExpand is called due to TRANSIT_CHANGE and but no snapshot
        ArgumentCaptor<Runnable> animCb = ArgumentCaptor.forClass(Runnable.class);
        verify(mLayerView).animateExpand(isNull(), animCb.capture());

        // Trigger animation callback to finish
        assertThat(finishCalled[0]).isFalse();
        animCb.getValue().run();
        assertThat(finishCalled[0]).isTrue();

        // Verify that the playing transition and pending cookie are removed
        assertThat(mBubbleTransitions.mEnterTransitions).doesNotContainKey(transitionToken);
        assertThat(mBubbleTransitions.mPendingEnterTransitions).doesNotContainKey(
                bt.mLaunchCookie.binder);
    }

    @Test
    public void launchNewTaskBubbleForExistingTransition_startTransitionBeforeBubbleInflated() {
        final ActivityManager.RunningTaskInfo taskInfo = setupAppBubble();

        when(mLayerView.canExpandView(mBubble)).thenReturn(true);

        final IBinder transition = mock(IBinder.class);
        final BubbleTransitions.LaunchNewTaskBubbleForExistingTransition bt =
                (BubbleTransitions.LaunchNewTaskBubbleForExistingTransition) mBubbleTransitions
                        .startLaunchNewTaskBubbleForExistingTransition(
                                mBubble, mExpandedViewManager, mTaskViewFactory, mBubblePositioner,
                                mStackView, mLayerView, mIconFactory, false /* inflateSync */,
                                transition, transitionHandler -> {});

        verify(mBubble).setPreparingTransition(bt);

        // Prepare for startAnimation call
        final SurfaceControl taskLeash = new SurfaceControl.Builder().setName("taskLeash").build();
        final TransitionInfo info = new TransitionInfo(TRANSIT_OPEN, 0);
        final TransitionInfo.Change chg = new TransitionInfo.Change(taskInfo.token, taskLeash);
        chg.setTaskInfo(taskInfo);
        chg.setMode(TRANSIT_CHANGE);
        info.addChange(chg);
        info.addRoot(new TransitionInfo.Root(0, mock(SurfaceControl.class), 0, 0));

        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        final Transitions.TransitionFinishCallback finishCb = wct -> {};

        // Start playing the transition
        bt.startAnimation(transition, info, startT, finishT, finishCb);

        verify(mBubble, never()).setPreparingTransition(null);

        // Simulate inflating the bubble
        bt.onInflated(mBubble);

        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        // Simulate surfaceCreated so the animation can start
        bt.surfaceCreated();
        bt.continueExpand();

        verify(mBubble).setPreparingTransition(null);
        assertThat(mTaskViewTransitions.hasPending()).isFalse();
    }

    @Test
    public void testJumpcutBubbleSwitch_bubbleBar() {
        // Setup open Bubble
        final TaskView openingTaskView = setUpBubbleTaskView(mBubble);
        final ActivityManager.RunningTaskInfo openingTaskInfo = setupAppBubble(
                mBubble, openingTaskView, mTaskViewTaskController);
        // Setup close Bubble
        final Bubble closingBubble = mock(Bubble.class);
        setUpBubbleBarExpandedView(closingBubble);
        final TaskView closingTaskView = setUpBubbleTaskView(closingBubble);
        final SurfaceControl closingTaskViewLeash = closingTaskView.getSurfaceControl();
        final ActivityManager.RunningTaskInfo closingTaskInfo = setupAppBubble(
                closingBubble, closingTaskView, mTaskViewTaskController);
        // Setup Transition
        final IBinder transitionToken = mock(IBinder.class);
        final Consumer<Transitions.TransitionHandler> onInflatedCallback = mock(Consumer.class);
        final SurfaceControl openingTaskLeash =
                new SurfaceControl.Builder().setName("openingTaskLeash").build();
        final TransitionInfo.Change openingChg = new TransitionInfo.Change(openingTaskInfo.token,
                openingTaskLeash);
        openingChg.setTaskInfo(openingTaskInfo);
        openingChg.setMode(TRANSIT_OPEN);
        final SurfaceControl closingTaskLeash =
                new SurfaceControl.Builder().setName("closingTaskLeash").build();
        final TransitionInfo.Change closingChg = new TransitionInfo.Change(closingTaskInfo.token,
                closingTaskLeash);
        closingChg.setTaskInfo(openingTaskInfo);
        closingChg.setMode(TRANSIT_CLOSE);
        final TransitionInfo info = new TransitionInfo(TRANSIT_OPEN, 0);
        info.addChange(openingChg);
        info.addChange(closingChg);
        final SurfaceControl.Transaction startT = spy(new SurfaceControl.Transaction());
        final SurfaceControl.Transaction finishT = spy(new SurfaceControl.Transaction());
        doNothing().when(startT).apply();
        doNothing().when(finishT).apply();
        final Transitions.TransitionFinishCallback finishCb =
                mock(Transitions.TransitionFinishCallback.class);

        final BubbleTransitions.JumpcutBubbleSwitchTransition bt =
                (BubbleTransitions.JumpcutBubbleSwitchTransition) mBubbleTransitions
                        .startJumpcutBubbleSwitchTransition(mBubble, closingBubble,
                                mExpandedViewManager, mTaskViewFactory, mBubblePositioner,
                                mStackView, mLayerView, mIconFactory, false /* inflateSync */,
                                transitionToken, onInflatedCallback);

        verify(mBubble).setPreparingTransition(bt);

        bt.onInflated(mBubble);

        // Start playing the transition
        bt.startAnimation(transitionToken, info, startT, finishT, finishCb);

        // Verify startT modifications
        verify(startT).setAlpha(closingTaskLeash, 1f);
        verify(startT).setPosition(closingTaskLeash, 0, 0);
        verify(startT).reparent(closingTaskLeash, closingTaskViewLeash);
        verify(startT).show(closingTaskLeash);
        verify(startT).apply();

        // Bubble data gets updated
        verify(mBubbleData).jumpcutBubbleSwitch(mBubble, closingBubble);

        // Simulate surfaceCreated and continueExpand so the animation can start
        bt.continueExpand();
        bt.surfaceCreated();

        // Verify animateExpand is called
        ArgumentCaptor<Runnable> animCb = ArgumentCaptor.forClass(Runnable.class);
        verify(mLayerView).animateExpand(eq(closingBubble), animCb.capture());

        // Trigger animation callback to finish
        clearInvocations(mBubble);
        verify(finishCb, never()).onTransitionFinished(any());
        animCb.getValue().run();
        verify(finishCb).onTransitionFinished(any());

        // Verify cleanup
        verify(mBubble).setPreparingTransition(null);
    }

    /**
     * Test a scenario where the TaskViewTransitions queue has a pending TaskView transition. And
     * a new transition for launching a different bubble comes in during it. Once both transitions
     * are handled, the TaskViewTransitions pending queue should be empty.
     */
    @Test
    public void launchNewTaskBubbleForExistingTransition_withExistingTransitionInQueue() {
        // Set up a bubble and have it queue a transition in the queue that will remain pending
        TaskView existingTaskView = mock(TaskView.class);
        TaskViewTaskController existingTvc = mock(TaskViewTaskController.class);
        setupAppBubble(mBubble, existingTaskView, existingTvc);
        final IBinder existingTransition = mock(IBinder.class);
        when(mTransitions.startTransition(anyInt(), any(), any())).thenReturn(existingTransition);
        mTaskViewTransitions.setTaskViewVisible(existingTvc, true);

        // Check that there is a pending transition before we create the new bubble
        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        when(mLayerView.canExpandView(mBubble)).thenReturn(true);

        final ActivityManager.RunningTaskInfo bubbleTask = setupAppBubble();
        final IBinder transition = mock(IBinder.class);
        final BubbleTransitions.LaunchNewTaskBubbleForExistingTransition bt =
                (BubbleTransitions.LaunchNewTaskBubbleForExistingTransition) mBubbleTransitions
                        .startLaunchNewTaskBubbleForExistingTransition(
                                mBubble, mExpandedViewManager, mTaskViewFactory, mBubblePositioner,
                                mStackView, mLayerView, mIconFactory, false /* inflateSync */,
                                transition, transitionHandler -> {});

        verify(mBubble).setPreparingTransition(bt);

        // Prepare for startAnimation call
        final SurfaceControl taskLeash = new SurfaceControl.Builder().setName("taskLeash").build();
        final TransitionInfo info = new TransitionInfo(TRANSIT_OPEN, 0);
        final TransitionInfo.Change chg = new TransitionInfo.Change(bubbleTask.token, taskLeash);
        chg.setTaskInfo(bubbleTask);
        chg.setMode(TRANSIT_CHANGE);
        info.addChange(chg);
        info.addRoot(new TransitionInfo.Root(0, mock(SurfaceControl.class), 0, 0));

        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        final Transitions.TransitionFinishCallback finishCb = wct -> {};

        // Start playing the new bubble transition
        bt.startAnimation(transition, info, startT, finishT, finishCb);
        verify(mBubble, never()).setPreparingTransition(null);
        bt.onInflated(mBubble);
        bt.surfaceCreated();
        bt.continueExpand();

        // The pending queue should still have the transition from the existing bubble
        assertThat(mTaskViewTransitions.hasPending()).isTrue();

        // Now start the existing bubble transition
        mTaskViewTransitions.startAnimation(existingTransition,
                new TransitionInfo(TRANSIT_CHANGE, 0), startT, finishT, wct -> {
                });

        // Now the queue should be empty
        assertThat(mTaskViewTransitions.hasPending()).isFalse();
    }

    @Test
    public void testGetEnterBubbleTask() {
        final SurfaceControl leash = new SurfaceControl.Builder().setName("testLeash").build();
        final ActivityManager.RunningTaskInfo taskInfo0 = setupAppBubble();
        final ActivityManager.RunningTaskInfo taskInfo1 = setupAppBubble();

        final TransitionInfo info = new TransitionInfo(TRANSIT_OPEN, 0);
        final TransitionInfo.Change openingBubble = new TransitionInfo.Change(
                taskInfo0.token, leash);
        openingBubble.setTaskInfo(taskInfo0);
        openingBubble.setMode(TRANSIT_OPEN);
        final TransitionInfo.Change closingBubble = new TransitionInfo.Change(
                taskInfo1.token, leash);
        closingBubble.setTaskInfo(taskInfo1);
        closingBubble.setMode(TRANSIT_CLOSE);
        info.addChange(closingBubble);
        info.addChange(openingBubble);
        info.addRoot(new TransitionInfo.Root(0, mock(SurfaceControl.class), 0, 0));

        assertThat(mBubbleTransitions.getEnterBubbleTask(info)).isEqualTo(openingBubble);
    }

    @Test
    public void testGetClosingBubbleTask() {
        final SurfaceControl leash = new SurfaceControl.Builder().setName("testLeash").build();
        final ActivityManager.RunningTaskInfo taskInfo0 = setupAppBubble();
        final ActivityManager.RunningTaskInfo taskInfo1 = setupAppBubble();

        final TransitionInfo info = new TransitionInfo(TRANSIT_OPEN, 0);
        final TransitionInfo.Change openingBubble = new TransitionInfo.Change(
                taskInfo0.token, leash);
        openingBubble.setTaskInfo(taskInfo0);
        openingBubble.setMode(TRANSIT_OPEN);
        final TransitionInfo.Change closingBubble = new TransitionInfo.Change(
                taskInfo1.token, leash);
        closingBubble.setTaskInfo(taskInfo1);
        closingBubble.setMode(TRANSIT_CLOSE);
        info.addChange(openingBubble);
        info.addChange(closingBubble);
        info.addRoot(new TransitionInfo.Root(0, mock(SurfaceControl.class), 0, 0));

        assertThat(mBubbleTransitions.getClosingBubbleTask(info)).isEqualTo(closingBubble);
    }

    @Test
    public void testGetClosingBubbleTask_excludeChangeAndToBack() {
        final SurfaceControl leash = new SurfaceControl.Builder().setName("testLeash").build();
        final ActivityManager.RunningTaskInfo taskInfo0 = setupAppBubble();
        final ActivityManager.RunningTaskInfo taskInfo1 = setupAppBubble();

        final TransitionInfo info = new TransitionInfo(TRANSIT_OPEN, 0);
        final TransitionInfo.Change openingBubble = new TransitionInfo.Change(
                taskInfo0.token, leash);
        openingBubble.setTaskInfo(taskInfo0);
        openingBubble.setMode(TRANSIT_CHANGE);
        final TransitionInfo.Change closingBubble = new TransitionInfo.Change(
                taskInfo1.token, leash);
        closingBubble.setTaskInfo(taskInfo1);
        closingBubble.setMode(TRANSIT_TO_BACK);
        info.addChange(openingBubble);
        info.addChange(closingBubble);
        info.addRoot(new TransitionInfo.Root(0, mock(SurfaceControl.class), 0, 0));

        assertThat(mBubbleTransitions.getClosingBubbleTask(info)).isNull();
    }

    @Test
    @EnableFlags({FLAG_ENABLE_CREATE_ANY_BUBBLE, FLAG_ROOT_TASK_FOR_BUBBLE})
    public void testStartNewBubbleTaskFromExistingBubble() {
        // Setup a Bubble that's currently expanded
        final Bubble expandedBubble = mock(Bubble.class);
        setUpBubbleBarExpandedView(expandedBubble);
        final TaskView expandedTaskView = setUpBubbleTaskView(expandedBubble);
        final SurfaceControl expandedTaskViewLeash = expandedTaskView.getSurfaceControl();
        final ActivityManager.RunningTaskInfo expandedTaskInfo = setupAppBubble(
                expandedBubble, expandedTaskView, mTaskViewTaskController);
        expandedTaskInfo.taskId = 10;
        // Setup a new open Bubble
        final TaskView openingTaskView = setUpBubbleTaskView(mBubble);
        final ActivityManager.RunningTaskInfo openingTaskInfo = setupAppBubble(
                mBubble, openingTaskView, mTaskViewTaskController);
        // Setup Transition
        final IBinder transitionToken = mock(IBinder.class);
        final Consumer<Transitions.TransitionHandler> onInflatedCallback = mock(Consumer.class);
        final SurfaceControl openingTaskLeash =
                new SurfaceControl.Builder().setName("openingTaskLeash").build();
        final TransitionInfo.Change openingChg = new TransitionInfo.Change(openingTaskInfo.token,
                openingTaskLeash);
        openingChg.setTaskInfo(openingTaskInfo);
        openingChg.setMode(TRANSIT_OPEN);
        final SurfaceControl expandedTaskLeash =
                new SurfaceControl.Builder().setName("expandedTaskLeash").build();
        final TransitionInfo.Change expandedChg = new TransitionInfo.Change(expandedTaskInfo.token,
                expandedTaskLeash);
        expandedChg.setTaskInfo(expandedTaskInfo);
        expandedChg.setMode(TRANSIT_TO_BACK);
        doReturn(expandedBubble).when(mBubbleData).getBubbleInStackWithTaskId(
                expandedTaskInfo.taskId);
        final TransitionInfo info = new TransitionInfo(TRANSIT_OPEN, 0);
        info.addChange(openingChg);
        info.addChange(expandedChg);
        info.addRoot(new TransitionInfo.Root(openingTaskInfo.displayId, openingTaskLeash, 0, 0));
        final SurfaceControl.Transaction startT = spy(new SurfaceControl.Transaction());
        final SurfaceControl.Transaction finishT = spy(new SurfaceControl.Transaction());
        doNothing().when(startT).apply();
        doNothing().when(finishT).apply();
        final Transitions.TransitionFinishCallback finishCb =
                mock(Transitions.TransitionFinishCallback.class);

        final BubbleTransitions.LaunchNewTaskBubbleForExistingTransition bt =
                (BubbleTransitions.LaunchNewTaskBubbleForExistingTransition) mBubbleTransitions
                        .startLaunchNewTaskBubbleForExistingTransition(mBubble,
                                mExpandedViewManager, mTaskViewFactory, mBubblePositioner,
                                mStackView, mLayerView, mIconFactory, false /* inflateSync */,
                                transitionToken, onInflatedCallback);

        verify(mBubble).setPreparingTransition(bt);

        bt.onInflated(mBubble);

        // Start playing the transition
        bt.startAnimation(transitionToken, info, startT, finishT, finishCb);

        // Verify that the to-back leash is in TaskView
        verify(startT).reparent(expandedTaskLeash, expandedTaskViewLeash);
        verify(startT).apply();
    }
}
