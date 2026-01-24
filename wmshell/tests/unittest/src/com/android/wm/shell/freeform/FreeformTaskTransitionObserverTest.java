/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.freeform;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;

import static com.android.wm.shell.transition.Transitions.TRANSIT_START_RECENTS_TRANSITION;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.view.SurfaceControl;
import android.window.IWindowContainerToken;
import android.window.TransitionInfo;
import android.window.WindowContainerToken;

import androidx.test.filters.SmallTest;

import com.android.window.flags2.Flags;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.desktopmode.DesktopBackNavTransitionObserver;
import com.android.wm.shell.desktopmode.DesktopImeHandler;
import com.android.wm.shell.desktopmode.DesktopImmersiveController;
import com.android.wm.shell.desktopmode.DesktopInOrderTransitionObserver;
import com.android.wm.shell.desktopmode.multidesks.DesksOrganizer;
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver;
import com.android.wm.shell.shared.desktopmode.FakeDesktopState;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.FocusTransitionObserver;
import com.android.wm.shell.transition.TransitionInfoBuilder;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.util.StubTransaction;
import com.android.wm.shell.windowdecor.WindowDecorViewModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

/** Tests for {@link FreeformTaskTransitionObserver}. */
@SmallTest
public class FreeformTaskTransitionObserverTest extends ShellTestCase {

    @Mock private ShellInit mShellInit;
    @Mock private Transitions mTransitions;
    @Mock private DesktopImmersiveController mDesktopImmersiveController;
    @Mock private WindowDecorViewModel mWindowDecorViewModel;
    @Mock private TaskChangeListener mTaskChangeListener;
    @Mock private FocusTransitionObserver mFocusTransitionObserver;
    @Mock private DesksOrganizer mDesksOrganizer;
    @Mock private DesksTransitionObserver mDesksTransitionObserver;
    @Mock private DesktopImeHandler mDesktopImeHandler;
    @Mock private DesktopBackNavTransitionObserver mDesktopBackNavTransitionObserver;
    @Mock private DesktopInOrderTransitionObserver mDesktopInOrderTransitionObserver;
    private FakeDesktopState mDesktopState;
    private FreeformTaskTransitionObserver mTransitionObserver;
    private AutoCloseable mMocksInits = null;

    @Before
    public void setUp() {
        mMocksInits = MockitoAnnotations.openMocks(this);

        mDesktopState = new FakeDesktopState();
        mDesktopState.setFreeformEnabled(true);

        PackageManager pm = mock(PackageManager.class);
        doReturn(true).when(pm).hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT);
        doReturn(false).when(mDesksOrganizer).isDeskChange(any());
        final Context context = mock(Context.class);
        doReturn(pm).when(context).getPackageManager();

        mTransitionObserver =
                new FreeformTaskTransitionObserver(
                        mShellInit,
                        mTransitions,
                        Optional.of(mDesktopImmersiveController),
                        mWindowDecorViewModel,
                        Optional.of(mTaskChangeListener),
                        mFocusTransitionObserver,
                        mDesksOrganizer,
                        Optional.of(mDesksTransitionObserver),
                        mDesktopState,
                        Optional.of(mDesktopImeHandler),
                        Optional.of(mDesktopBackNavTransitionObserver),
                        Optional.of(mDesktopInOrderTransitionObserver));

        final ArgumentCaptor<Runnable> initRunnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mShellInit).addInitCallback(initRunnableCaptor.capture(), same(mTransitionObserver));
        initRunnableCaptor.getValue().run();
    }

    @After
    public void tearDown() throws Exception {
        if (mMocksInits != null) {
            mMocksInits.close();
            mMocksInits = null;
        }
    }

    @Test
    public void init_registersObserver() {
        verify(mTransitions).registerObserver(same(mTransitionObserver));
    }

    @Test
    public void openTransition_createsWindowDecor() {
        final TransitionInfo.Change change = createChange(TRANSIT_OPEN, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build();

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mWindowDecorViewModel)
                .onTaskOpening(change.getTaskInfo(), change.getLeash(), startT, finishT);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_NO_WINDOW_DECORATION_FOR_DESKS)
    public void desksChange_windowDecorNotCreatedForDesksTask() {
        final TransitionInfo.Change change = createChange(TRANSIT_OPEN, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build();
        doReturn(true).when(mDesksOrganizer).isDeskChange(change);

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mWindowDecorViewModel, never())
                .onTaskOpening(change.getTaskInfo(), change.getLeash(), startT, finishT);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_NO_WINDOW_DECORATION_FOR_DESKS)
    public void desksChange_listenerNotNotifiedOfTaskChange() {
        final TransitionInfo.Change change =
                createChange(TRANSIT_CHANGE, /* taskId= */ 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_CHANGE, /* flags= */ 0).addChange(change).build();
        doReturn(true).when(mDesksOrganizer).isDeskChange(change);

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mTaskChangeListener, never()).onTaskChanging(change.getTaskInfo());
    }

    @Test
    public void openTransition_notifiesOnTaskOpening() {
        final TransitionInfo.Change change = createChange(TRANSIT_OPEN, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change).build();

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mTaskChangeListener).onTaskOpening(change.getTaskInfo());
    }

    @Test
    public void toFrontTransition_notifiesOnTaskMovingToFront() {
        final TransitionInfo.Change change =
                createChange(TRANSIT_TO_FRONT, /* taskId= */ 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_TO_FRONT, /* flags= */ 0)
                        .addChange(change)
                        .build();

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mTaskChangeListener).onTaskMovingToFront(change.getTaskInfo());
    }

    @Test
    public void toBackTransition_notifiesOnTaskMovingToBack() {
        final TransitionInfo.Change change =
                createChange(TRANSIT_TO_BACK, /* taskId= */ 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_TO_BACK, /* flags= */ 0)
                        .addChange(change)
                        .build();

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mTaskChangeListener).onTaskMovingToBack(change.getTaskInfo());
    }

    @Test
    public void recentsTransition_onTransitionFinished_notifiesOnTaskMovingToBack() {
        final TransitionInfo.Change change =
                createChange(TRANSIT_TO_BACK, /* taskId= */ 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo.Change homeChange =
                createChange(TRANSIT_TO_FRONT, /* taskId= */ 2, WINDOWING_MODE_FULLSCREEN);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_START_RECENTS_TRANSITION, /* flags= */ 0)
                        .addChange(homeChange)
                        .addChange(change)
                        .build();

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mTaskChangeListener, never()).onTaskMovingToBack(change.getTaskInfo());

        mTransitionObserver.onTransitionFinished(transition, false);
        verify(mTaskChangeListener).onTaskMovingToBack(change.getTaskInfo());
    }

    @Test
    public void changeTransition_notifiesOnTaskChange() {
        final TransitionInfo.Change change =
                createChange(TRANSIT_CHANGE, /* taskId= */ 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_CHANGE, /* flags= */ 0).addChange(change).build();

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mTaskChangeListener).onTaskChanging(change.getTaskInfo());
    }

    @Test
    public void closeTransition_preparesWindowDecor() {
        final TransitionInfo.Change change =
                createChange(TRANSIT_CLOSE, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_CLOSE, 0).addChange(change).build();

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mWindowDecorViewModel).onTaskClosing(change.getTaskInfo(), startT, finishT);
    }

    @Test
    public void closeTransition_notifiesOnTaskClosing() {
        final TransitionInfo.Change change =
                createChange(TRANSIT_CLOSE, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_CLOSE, 0).addChange(change).build();

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mTaskChangeListener).onTaskClosing(change.getTaskInfo());
    }

    @Test
    public void closeTransition_doesntCloseWindowDecorDuringTransition() throws Exception {
        final TransitionInfo.Change change =
                createChange(TRANSIT_CLOSE, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_CLOSE, 0).addChange(change).build();

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);

        verify(mWindowDecorViewModel, never()).destroyWindowDecoration(change.getTaskInfo());
    }

    @Test
    public void closeTransition_closesWindowDecorAfterTransition() throws Exception {
        final TransitionInfo.Change change =
                createChange(TRANSIT_CLOSE, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info =
                new TransitionInfoBuilder(TRANSIT_CLOSE, 0).addChange(change).build();

        final AutoCloseable windowDecor = mock(AutoCloseable.class);

        final IBinder transition = mock(IBinder.class);
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);
        mTransitionObserver.onTransitionStarting(transition);
        mTransitionObserver.onTransitionFinished(transition, false);

        verify(mWindowDecorViewModel).destroyWindowDecoration(change.getTaskInfo());
    }

    @Test
    public void transitionFinished_closesMergedWindowDecoration() throws Exception {
        // The playing transition
        final TransitionInfo.Change change1 =
                createChange(TRANSIT_OPEN, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info1 =
                new TransitionInfoBuilder(TRANSIT_OPEN, 0).addChange(change1).build();

        final IBinder transition1 = mock(IBinder.class);
        final SurfaceControl.Transaction startT1 = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT1 = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition1, info1, startT1, finishT1);
        mTransitionObserver.onTransitionStarting(transition1);

        // The merged transition
        final TransitionInfo.Change change2 =
                createChange(TRANSIT_CLOSE, 2, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info2 =
                new TransitionInfoBuilder(TRANSIT_CLOSE, 0).addChange(change2).build();

        final IBinder transition2 = mock(IBinder.class);
        final SurfaceControl.Transaction startT2 = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT2 = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition2, info2, startT2, finishT2);
        mTransitionObserver.onTransitionMerged(transition2, transition1);

        mTransitionObserver.onTransitionFinished(transition1, false);

        verify(mWindowDecorViewModel).destroyWindowDecoration(change2.getTaskInfo());
    }

    @Test
    public void closeTransition_closesWindowDecorsOnTransitionMerge() throws Exception {
        // The playing transition
        final TransitionInfo.Change change1 =
                createChange(TRANSIT_CLOSE, 1, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info1 =
                new TransitionInfoBuilder(TRANSIT_CLOSE, 0).addChange(change1).build();

        final IBinder transition1 = mock(IBinder.class);
        final SurfaceControl.Transaction startT1 = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT1 = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition1, info1, startT1, finishT1);
        mTransitionObserver.onTransitionStarting(transition1);

        // The merged transition
        final TransitionInfo.Change change2 =
                createChange(TRANSIT_CLOSE, 2, WINDOWING_MODE_FREEFORM);
        final TransitionInfo info2 =
                new TransitionInfoBuilder(TRANSIT_CLOSE, 0).addChange(change2).build();

        final IBinder transition2 = mock(IBinder.class);
        final SurfaceControl.Transaction startT2 = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT2 = mock(SurfaceControl.Transaction.class);
        mTransitionObserver.onTransitionReady(transition2, info2, startT2, finishT2);
        mTransitionObserver.onTransitionMerged(transition2, transition1);

        mTransitionObserver.onTransitionFinished(transition1, false);

        verify(mWindowDecorViewModel).destroyWindowDecoration(change1.getTaskInfo());
        verify(mWindowDecorViewModel).destroyWindowDecoration(change2.getTaskInfo());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    @DisableFlags(Flags.FLAG_ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP)
    public void onTransitionReady_forwardsToDesktopImmersiveController() {
        final IBinder transition = mock(IBinder.class);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CHANGE, 0).build();
        final SurfaceControl.Transaction startT = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction finishT = mock(SurfaceControl.Transaction.class);

        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);

        verify(mDesktopImmersiveController).onTransitionReady(transition, info, startT, finishT);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    @DisableFlags(Flags.FLAG_ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP)
    public void onTransitionMerged_forwardsToDesktopImmersiveController() {
        final IBinder merged = mock(IBinder.class);
        final IBinder playing = mock(IBinder.class);

        mTransitionObserver.onTransitionMerged(merged, playing);

        verify(mDesktopImmersiveController).onTransitionMerged(merged, playing);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    @DisableFlags(Flags.FLAG_ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP)
    public void onTransitionStarting_forwardsToDesktopImmersiveController() {
        final IBinder transition = mock(IBinder.class);

        mTransitionObserver.onTransitionStarting(transition);

        verify(mDesktopImmersiveController).onTransitionStarting(transition);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_FULLY_IMMERSIVE_IN_DESKTOP)
    @DisableFlags(Flags.FLAG_ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP)
    public void onTransitionFinished_forwardsToDesktopImmersiveController() {
        final IBinder transition = mock(IBinder.class);

        mTransitionObserver.onTransitionFinished(transition, /* aborted= */ false);

        verify(mDesktopImmersiveController).onTransitionFinished(transition, /* aborted= */ false);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP)
    public void onTransitionReady_forwardsToDesksTransitionObserver() {
        final IBinder transition = mock(IBinder.class);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CLOSE, /* flags= */ 0)
                .build();

        mTransitionObserver.onTransitionReady(transition, info, new StubTransaction(),
                new StubTransaction());

        verify(mDesksTransitionObserver).onTransitionReady(transition, info);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP)
    public void onTransitionMerged_forwardsToDesksTransitionObserver() {
        final IBinder merged = mock(IBinder.class);
        final IBinder playing = mock(IBinder.class);

        mTransitionObserver.onTransitionMerged(merged, playing);

        verify(mDesksTransitionObserver).onTransitionMerged(merged, playing);
    }

    @Test
    @DisableFlags(Flags.FLAG_ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP)
    public void onTransitionFinished_forwardsToDesksTransitionObserver() {
        final IBinder transition = mock(IBinder.class);

        mTransitionObserver.onTransitionFinished(transition, /* aborted = */ false);

        verify(mDesksTransitionObserver).onTransitionFinished(transition);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP)
    public void onTransitionReady_forwardsToDesktopInOrderTransitionObserver() {
        final IBinder transition = mock(IBinder.class);
        final TransitionInfo info = new TransitionInfoBuilder(TRANSIT_CLOSE, /* flags= */ 0)
                .build();
        final SurfaceControl.Transaction startT = new StubTransaction();
        final SurfaceControl.Transaction finishT = new StubTransaction();


        mTransitionObserver.onTransitionReady(transition, info, startT, finishT);

        verify(mDesktopInOrderTransitionObserver).onTransitionReady(transition, info, startT,
                finishT);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP)
    public void onTransitionMerged_forwardsToDesktopInOrderTransitionObserver() {
        final IBinder merged = mock(IBinder.class);
        final IBinder playing = mock(IBinder.class);

        mTransitionObserver.onTransitionMerged(merged, playing);

        verify(mDesktopInOrderTransitionObserver).onTransitionMerged(merged, playing);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_INORDER_TRANSITION_CALLBACKS_FOR_DESKTOP)
    public void onTransitionFinished_forwardsToDesktopInOrderTransitionObserver() {
        final IBinder transition = mock(IBinder.class);

        mTransitionObserver.onTransitionFinished(transition, /* aborted = */ false);

        verify(mDesktopInOrderTransitionObserver).onTransitionFinished(transition, false);
    }

    private static TransitionInfo.Change createChange(int mode, int taskId, int windowingMode) {
        final ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.configuration.windowConfiguration.setWindowingMode(windowingMode);

        final TransitionInfo.Change change =
                new TransitionInfo.Change(
                        new WindowContainerToken(mock(IWindowContainerToken.class)),
                        mock(SurfaceControl.class));
        change.setMode(mode);
        change.setTaskInfo(taskInfo);
        return change;
    }
}
