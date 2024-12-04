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

package com.android.wm.shell.transition;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_ROTATE;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_SEAMLESS;
import static android.view.WindowManager.LayoutParams.ROTATION_ANIMATION_UNSPECIFIED;
import static android.view.WindowManager.TRANSIT_CHANGE;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_FIRST_CUSTOM;
import static android.view.WindowManager.TRANSIT_FLAG_KEYGUARD_GOING_AWAY;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_SLEEP;
import static android.view.WindowManager.TRANSIT_TO_BACK;
import static android.view.WindowManager.TRANSIT_TO_FRONT;
import static android.window.TransitionInfo.FLAG_DISPLAY_HAS_ALERT_WINDOWS;
import static android.window.TransitionInfo.FLAG_IS_DISPLAY;
import static android.window.TransitionInfo.FLAG_SYNC;
import static android.window.TransitionInfo.FLAG_TRANSLUCENT;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.wm.shell.transition.TransitionAnimationHelper.getTransitionTypeFromInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.IApplicationThread;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArraySet;
import android.util.Pair;
import android.view.IRecentsAnimationRunner;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.IRemoteTransition;
import android.window.IRemoteTransitionFinishedCallback;
import android.window.IWindowContainerToken;
import android.window.RemoteTransition;
import android.window.RemoteTransitionStub;
import android.window.TransitionFilter;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowAnimationState;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.R;
import com.android.internal.policy.TransitionAnimation;
import com.android.systemui.shared.Flags;
import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TransactionPool;
import com.android.wm.shell.recents.RecentTasksController;
import com.android.wm.shell.recents.RecentsTransitionHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.sysui.ShellSharedConstants;
import com.android.wm.shell.util.StubTransaction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.function.Function;

/**
 * Tests for the shell transitions.
 *
 * Build/Install/Run:
 * atest WMShellUnitTests:ShellTransitionTests
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ShellTransitionTests extends ShellTestCase {

    private final ShellTaskOrganizer mOrganizer = mock(ShellTaskOrganizer.class);
    private final TransactionPool mTransactionPool = mock(TransactionPool.class);
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final TestShellExecutor mMainExecutor = new TestShellExecutor();
    private final ShellExecutor mAnimExecutor = new TestShellExecutor();
    private final TestTransitionHandler mDefaultHandler = new TestTransitionHandler();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    @Rule
    public final SetFlagsRule setFlagsRule = new SetFlagsRule();

    @Before
    public void setUp() {
        doAnswer(invocation -> new Binder())
                .when(mOrganizer).startNewTransition(anyInt(), any());
    }

    @Test
    public void instantiate_addInitCallback() {
        ShellInit shellInit = mock(ShellInit.class);
        final Transitions t = new Transitions(mContext, shellInit, mock(ShellController.class),
                mOrganizer, mTransactionPool, createTestDisplayController(), mMainExecutor,
                mMainHandler, mAnimExecutor, mock(HomeTransitionObserver.class));
        // One from Transitions, one from RootTaskDisplayAreaOrganizer
        verify(shellInit).addInitCallback(any(), eq(t));
        verify(shellInit).addInitCallback(any(), isA(RootTaskDisplayAreaOrganizer.class));
    }

    @Test
    public void instantiateController_addExternalInterface() {
        ShellInit shellInit = new ShellInit(mMainExecutor);
        ShellController shellController = mock(ShellController.class);
        final Transitions t = new Transitions(mContext, shellInit, shellController,
                mOrganizer, mTransactionPool, createTestDisplayController(), mMainExecutor,
                mMainHandler, mAnimExecutor, mock(HomeTransitionObserver.class));
        shellInit.init();
        verify(shellController, times(1)).addExternalInterface(
                eq(ShellSharedConstants.KEY_EXTRA_SHELL_SHELL_TRANSITIONS), any(), any());
    }

    @Test
    public void testBasicTransitionFlow() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        IBinder transitToken = new Binder();
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        verify(mOrganizer, times(1)).startTransition(eq(transitToken), any());
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        transitions.onTransitionReady(transitToken, info, new StubTransaction(),
                new StubTransaction());
        assertEquals(1, mDefaultHandler.activeCount());
        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        verify(mOrganizer, times(1)).finishTransition(eq(transitToken), any());
    }

    @Test
    public void testNonDefaultHandler() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        final WindowContainerTransaction handlerWCT = new WindowContainerTransaction();
        // Make a test handler that only responds to multi-window triggers AND only animates
        // Change transitions.
        TestTransitionHandler testHandler = new TestTransitionHandler() {
            @Override
            public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
                    @NonNull SurfaceControl.Transaction startTransaction,
                    @NonNull SurfaceControl.Transaction finishTransaction,
                    @NonNull Transitions.TransitionFinishCallback finishCallback) {
                for (TransitionInfo.Change chg : info.getChanges()) {
                    if (chg.getMode() == TRANSIT_CHANGE) {
                        return super.startAnimation(transition, info, startTransaction,
                                finishTransaction, finishCallback);
                    }
                }
                return false;
            }

            @Nullable
            @Override
            public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
                    @NonNull TransitionRequestInfo request) {
                final RunningTaskInfo task = request.getTriggerTask();
                return (task != null && task.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW)
                        ? handlerWCT : null;
            }
        };
        transitions.addHandler(testHandler);

        IBinder transitToken = new Binder();
        TransitionInfo open = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();

        // Make a request that will be rejected by the testhandler.
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        verify(mOrganizer, times(1)).startTransition(eq(transitToken), isNull());
        transitions.onTransitionReady(transitToken, open, new StubTransaction(),
                new StubTransaction());
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(0, testHandler.activeCount());
        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();

        // Make a request that will be handled by testhandler but not animated by it.
        RunningTaskInfo mwTaskInfo =
                createTaskInfo(1, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        // Make the wct non-empty.
        handlerWCT.setFocusable(new WindowContainerToken(mock(IWindowContainerToken.class)), true);
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_OPEN, mwTaskInfo, null /* remote */));
        verify(mOrganizer, times(1)).startTransition(
                eq(transitToken), eq(handlerWCT));
        transitions.onTransitionReady(transitToken, open, new StubTransaction(),
                new StubTransaction());
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(0, testHandler.activeCount());
        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();

        // Make a request that will be handled AND animated by testhandler.
        // Add an aggressive handler (doesn't handle but always animates) on top to make sure that
        // the test handler gets first shot at animating since it claimed to handle it.
        TestTransitionHandler topHandler = new TestTransitionHandler();
        transitions.addHandler(topHandler);
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_CHANGE, mwTaskInfo, null /* remote */));
        verify(mOrganizer, times(2)).startTransition(
                eq(transitToken), eq(handlerWCT));
        TransitionInfo change = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(TRANSIT_CHANGE).build();
        transitions.onTransitionReady(transitToken, change, new StubTransaction(),
                new StubTransaction());
        assertEquals(0, mDefaultHandler.activeCount());
        assertEquals(1, testHandler.activeCount());
        assertEquals(0, topHandler.activeCount());
        testHandler.finishAll();
        mMainExecutor.flushAll();
    }

    @Test
    public void testRequestRemoteTransition() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        final boolean[] remoteCalled = new boolean[]{false};
        final WindowContainerTransaction remoteFinishWCT = new WindowContainerTransaction();
        IRemoteTransition testRemote = new RemoteTransitionStub() {
            @Override
            public void startAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t,
                    IRemoteTransitionFinishedCallback finishCallback) throws RemoteException {
                remoteCalled[0] = true;
                finishCallback.onTransitionFinished(remoteFinishWCT, null /* sct */);
            }
        };
        IBinder transitToken = new Binder();
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */,
                        new RemoteTransition(testRemote, "Test")));
        verify(mOrganizer, times(1)).startTransition(eq(transitToken), any());
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        transitions.onTransitionReady(transitToken, info, new StubTransaction(),
                new StubTransaction());
        assertEquals(0, mDefaultHandler.activeCount());
        assertTrue(remoteCalled[0]);
        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        verify(mOrganizer, times(1)).finishTransition(eq(transitToken), eq(remoteFinishWCT));
    }

    @Test
    public void testTransitionFilterActivityType() {
        TransitionFilter filter = new TransitionFilter();
        filter.mRequirements =
                new TransitionFilter.Requirement[]{new TransitionFilter.Requirement()};
        filter.mRequirements[0].mActivityType = ACTIVITY_TYPE_HOME;
        filter.mRequirements[0].mModes = new int[]{TRANSIT_OPEN, TRANSIT_TO_FRONT};

        final TransitionInfo openHome = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN,
                        createTaskInfo(1, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME)).build();
        assertTrue(filter.matches(openHome));

        final TransitionInfo openStd = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, createTaskInfo(
                        1, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD)).build();
        assertFalse(filter.matches(openStd));
    }

    @Test
    public void testTransitionFilterMultiRequirement() {
        // filter that requires at-least one opening and one closing app
        TransitionFilter filter = new TransitionFilter();
        filter.mRequirements = new TransitionFilter.Requirement[]{
                new TransitionFilter.Requirement(), new TransitionFilter.Requirement()};
        filter.mRequirements[0].mModes = new int[]{TRANSIT_OPEN, TRANSIT_TO_FRONT};
        filter.mRequirements[1].mModes = new int[]{TRANSIT_CLOSE, TRANSIT_TO_BACK};

        final TransitionInfo openOnly = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        assertFalse(filter.matches(openOnly));

        final TransitionInfo openClose = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        assertTrue(filter.matches(openClose));
    }

    @Test
    public void testTransitionFilterNotRequirement() {
        // filter that requires one opening and NO translucent apps
        TransitionFilter filter = new TransitionFilter();
        filter.mRequirements = new TransitionFilter.Requirement[]{
                new TransitionFilter.Requirement(), new TransitionFilter.Requirement()};
        filter.mRequirements[0].mModes = new int[]{TRANSIT_OPEN, TRANSIT_TO_FRONT};
        filter.mRequirements[1].mFlags = FLAG_TRANSLUCENT;
        filter.mRequirements[1].mNot = true;

        final TransitionInfo openOnly = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        assertTrue(filter.matches(openOnly));

        final TransitionInfo openAndTranslucent = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        openAndTranslucent.getChanges().get(1).setFlags(FLAG_TRANSLUCENT);
        assertFalse(filter.matches(openAndTranslucent));
    }

    @Test
    public void testTransitionFilterChecksTypeSet() {
        TransitionFilter filter = new TransitionFilter();
        filter.mTypeSet = new int[]{TRANSIT_OPEN, TRANSIT_TO_FRONT};

        final TransitionInfo openOnly = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        assertTrue(filter.matches(openOnly));

        final TransitionInfo toFrontOnly = new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                .addChange(TRANSIT_TO_FRONT).build();
        assertTrue(filter.matches(toFrontOnly));

        final TransitionInfo closeOnly = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_CLOSE).build();
        assertFalse(filter.matches(closeOnly));
    }

    @Test
    public void testTransitionFilterChecksFlags() {
        TransitionFilter filter = new TransitionFilter();
        filter.mFlags = TRANSIT_FLAG_KEYGUARD_GOING_AWAY;

        final TransitionInfo withFlag = new TransitionInfoBuilder(TRANSIT_TO_BACK,
                TRANSIT_FLAG_KEYGUARD_GOING_AWAY)
                .addChange(TRANSIT_TO_BACK).build();
        assertTrue(filter.matches(withFlag));

        final TransitionInfo withoutFlag = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        assertFalse(filter.matches(withoutFlag));
    }

    @Test
    public void testTransitionFilterChecksNotFlags() {
        TransitionFilter filter = new TransitionFilter();
        filter.mNotFlags = TRANSIT_FLAG_KEYGUARD_GOING_AWAY;

        final TransitionInfo withFlag = new TransitionInfoBuilder(TRANSIT_TO_BACK,
                TRANSIT_FLAG_KEYGUARD_GOING_AWAY)
                .addChange(TRANSIT_TO_BACK).build();
        assertFalse(filter.matches(withFlag));

        final TransitionInfo withoutFlag = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        assertTrue(filter.matches(withoutFlag));
    }

    @Test
    public void testTransitionFilterActivityComponent() {
        TransitionFilter filter = new TransitionFilter();
        ComponentName cmpt = new ComponentName("testpak", "testcls");
        filter.mRequirements =
                new TransitionFilter.Requirement[]{new TransitionFilter.Requirement()};
        filter.mRequirements[0].mTopActivity = cmpt;
        filter.mRequirements[0].mModes = new int[]{TRANSIT_OPEN, TRANSIT_TO_FRONT};

        final RunningTaskInfo taskInf = createTaskInfo(1);
        final TransitionInfo openTask = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, taskInf).build();
        assertFalse(filter.matches(openTask));

        taskInf.topActivity = cmpt;
        final TransitionInfo openTaskCmpt = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, taskInf).build();
        assertTrue(filter.matches(openTaskCmpt));

        final TransitionInfo openAct = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN, cmpt).build();
        assertTrue(filter.matches(openAct));
    }

    @Test
    public void testRegisteredRemoteTransition() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        final boolean[] remoteCalled = new boolean[]{false};
        IRemoteTransition testRemote = new RemoteTransitionStub() {
            @Override
            public void startAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t,
                    IRemoteTransitionFinishedCallback finishCallback) throws RemoteException {
                remoteCalled[0] = true;
                finishCallback.onTransitionFinished(null /* wct */, null /* sct */);
            }
        };

        TransitionFilter filter = new TransitionFilter();
        filter.mRequirements =
                new TransitionFilter.Requirement[]{new TransitionFilter.Requirement()};
        filter.mRequirements[0].mModes = new int[]{TRANSIT_OPEN, TRANSIT_TO_FRONT};

        transitions.registerRemote(filter, new RemoteTransition(testRemote, "Test"));
        mMainExecutor.flushAll();

        IBinder transitToken = new Binder();
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        verify(mOrganizer, times(1)).startTransition(eq(transitToken), any());
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        transitions.onTransitionReady(transitToken, info, new StubTransaction(),
                new StubTransaction());
        assertEquals(0, mDefaultHandler.activeCount());
        assertTrue(remoteCalled[0]);
        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        verify(mOrganizer, times(1)).finishTransition(eq(transitToken), any());
    }

    @Test
    public void testRegisteredRemoteTransitionTakeover() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        IRemoteTransition testRemote = new RemoteTransitionStub() {
            @Override
            public void startAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t,
                    IRemoteTransitionFinishedCallback finishCallback) throws RemoteException {
                final Transitions.TransitionHandler takeoverHandler =
                        transitions.getHandlerForTakeover(token, info);

                if (takeoverHandler == null) {
                    finishCallback.onTransitionFinished(null /* wct */, null /* sct */);
                    return;
                }

                takeoverHandler.takeOverAnimation(token, info, new SurfaceControl.Transaction(),
                        wct -> {
                            try {
                                finishCallback.onTransitionFinished(wct, null /* sct */);
                            } catch (RemoteException e) {
                                // Fail
                            }
                        }, new WindowAnimationState[info.getChanges().size()]);
            }
        };
        final boolean[] takeoverRemoteCalled = new boolean[]{false};
        IRemoteTransition testTakeoverRemote = new RemoteTransitionStub() {
            @Override
            public void startAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t,
                    IRemoteTransitionFinishedCallback finishCallback) {}

            @Override
            public void takeOverAnimation(IBinder transition, TransitionInfo info,
                    SurfaceControl.Transaction startTransaction,
                    IRemoteTransitionFinishedCallback finishCallback, WindowAnimationState[] states)
                    throws RemoteException {
                takeoverRemoteCalled[0] = true;
                finishCallback.onTransitionFinished(null /* wct */, null /* sct */);
            }
        };

        TransitionFilter filter = new TransitionFilter();
        filter.mRequirements =
                new TransitionFilter.Requirement[]{new TransitionFilter.Requirement()};
        filter.mRequirements[0].mModes = new int[]{TRANSIT_OPEN, TRANSIT_TO_FRONT};

        transitions.registerRemote(filter, new RemoteTransition(testRemote, "Test"));
        transitions.registerRemoteForTakeover(
                filter, new RemoteTransition(testTakeoverRemote, "Test"));
        mMainExecutor.flushAll();

        // Takeover shouldn't happen when the flag is disabled.
        setFlagsRule.disableFlags(Flags.FLAG_RETURN_ANIMATION_FRAMEWORK_LIBRARY);
        IBinder transitToken = new Binder();
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        transitions.onTransitionReady(transitToken, info, new StubTransaction(),
                new StubTransaction());
        assertEquals(0, mDefaultHandler.activeCount());
        assertFalse(takeoverRemoteCalled[0]);
        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        verify(mOrganizer, times(1)).finishTransition(eq(transitToken), any());

        // Takeover should happen when the flag is enabled.
        setFlagsRule.enableFlags(Flags.FLAG_RETURN_ANIMATION_FRAMEWORK_LIBRARY);
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        transitions.onTransitionReady(transitToken, info, new StubTransaction(),
                new StubTransaction());
        assertEquals(0, mDefaultHandler.activeCount());
        assertTrue(takeoverRemoteCalled[0]);
        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        verify(mOrganizer, times(2)).finishTransition(eq(transitToken), any());
    }

    @Test
    public void testOneShotRemoteHandler() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        final boolean[] remoteCalled = new boolean[]{false};
        final boolean[] takeoverRemoteCalled = new boolean[]{false};
        final WindowContainerTransaction remoteFinishWCT = new WindowContainerTransaction();
        IRemoteTransition testRemote = new RemoteTransitionStub() {
            @Override
            public void startAnimation(IBinder token, TransitionInfo info,
                    SurfaceControl.Transaction t,
                    IRemoteTransitionFinishedCallback finishCallback) throws RemoteException {
                remoteCalled[0] = true;
                finishCallback.onTransitionFinished(remoteFinishWCT, null /* sct */);
            }

            @Override
            public void takeOverAnimation(IBinder transition, TransitionInfo info,
                    SurfaceControl.Transaction startTransaction,
                    IRemoteTransitionFinishedCallback finishCallback, WindowAnimationState[] states)
                    throws RemoteException {
                takeoverRemoteCalled[0] = true;
                finishCallback.onTransitionFinished(remoteFinishWCT, null /* sct */);
            }
        };

        final int transitType = TRANSIT_FIRST_CUSTOM + 1;

        OneShotRemoteHandler oneShot = new OneShotRemoteHandler(mMainExecutor,
                new RemoteTransition(testRemote, "Test"));

        // Verify that it responds to the remote but not other things.
        IBinder transitToken = new Binder();
        assertNotNull(oneShot.handleRequest(transitToken,
                new TransitionRequestInfo(transitType, null,
                        new RemoteTransition(testRemote, "Test"))));
        assertNull(oneShot.handleRequest(transitToken,
                new TransitionRequestInfo(transitType, null, null)));

        Transitions.TransitionFinishCallback testFinish =
                mock(Transitions.TransitionFinishCallback.class);

        // Verify that it responds to animation properly
        oneShot.setTransition(transitToken);
        IBinder anotherToken = new Binder();
        assertFalse(oneShot.startAnimation(anotherToken, new TransitionInfo(transitType, 0),
                new StubTransaction(), new StubTransaction(),
                testFinish));
        assertTrue(oneShot.startAnimation(transitToken, new TransitionInfo(transitType, 0),
                new StubTransaction(), new StubTransaction(),
                testFinish));
        assertTrue(remoteCalled[0]);

        // Verify that it handles takeovers properly
        IBinder newToken = new Binder();
        oneShot.setTransition(newToken);
        assertFalse(oneShot.takeOverAnimation(transitToken, new TransitionInfo(transitType, 0),
                new StubTransaction(), testFinish, new WindowAnimationState[0]));
        assertTrue(oneShot.takeOverAnimation(newToken, new TransitionInfo(transitType, 0),
                new StubTransaction(), testFinish, new WindowAnimationState[0]));
        assertTrue(takeoverRemoteCalled[0]);
    }

    @Test
    public void testTransitionQueueing() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        IBinder transitToken1 = new Binder();
        transitions.requestStartTransition(transitToken1,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        TransitionInfo info1 = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        transitions.onTransitionReady(transitToken1, info1, new StubTransaction(),
                new StubTransaction());
        assertEquals(1, mDefaultHandler.activeCount());

        IBinder transitToken2 = new Binder();
        transitions.requestStartTransition(transitToken2,
                new TransitionRequestInfo(TRANSIT_CLOSE, null /* trigger */, null /* remote */));
        TransitionInfo info2 = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        transitions.onTransitionReady(transitToken2, info2, new StubTransaction(),
                new StubTransaction());
        // default handler doesn't merge by default, so it shouldn't increment active count.
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(0, mDefaultHandler.mergeCount());
        verify(mOrganizer, times(0)).finishTransition(eq(transitToken1), any());
        verify(mOrganizer, times(0)).finishTransition(eq(transitToken2), any());

        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        // first transition finished
        verify(mOrganizer, times(1)).finishTransition(eq(transitToken1), any());
        verify(mOrganizer, times(0)).finishTransition(eq(transitToken2), any());
        // But now the "queued" transition is running
        assertEquals(1, mDefaultHandler.activeCount());

        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        verify(mOrganizer, times(1)).finishTransition(eq(transitToken2), any());
    }

    @Test
    public void testTransitionMerging() {
        Transitions transitions = createTestTransitions();
        mDefaultHandler.setSimulateMerge(true);
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        IBinder transitToken1 = new Binder();
        transitions.requestStartTransition(transitToken1,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        TransitionInfo info1 = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        transitions.onTransitionReady(transitToken1, info1, new StubTransaction(),
                new StubTransaction());
        assertEquals(1, mDefaultHandler.activeCount());

        IBinder transitToken2 = new Binder();
        transitions.requestStartTransition(transitToken2,
                new TransitionRequestInfo(TRANSIT_CLOSE, null /* trigger */, null /* remote */));
        TransitionInfo info2 = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        transitions.onTransitionReady(transitToken2, info2, new StubTransaction(),
                new StubTransaction());
        // it should still only have 1 active, but then show 1 merged
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(1, mDefaultHandler.mergeCount());
        verify(mOrganizer, times(0)).finishTransition(eq(transitToken1), any());
        // We don't tell organizer it is finished yet (since we still want to maintain ordering)
        verify(mOrganizer, times(0)).finishTransition(eq(transitToken2), any());

        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        // transition + merged all finished.
        verify(mOrganizer, times(1)).finishTransition(eq(transitToken1), any());
        verify(mOrganizer, times(1)).finishTransition(eq(transitToken2), any());
        // Make sure nothing was queued
        assertEquals(0, mDefaultHandler.activeCount());
    }


    @Test
    public void testTransitionMergingOnFinish() {
        final Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        // The current transition.
        final IBinder transitToken1 = new Binder();
        requestStartTransition(transitions, transitToken1);
        onTransitionReady(transitions, transitToken1);

        // The next ready transition.
        final IBinder transitToken2 = new Binder();
        requestStartTransition(transitions, transitToken2);
        onTransitionReady(transitions, transitToken2);

        // The non-ready merge candidate.
        final IBinder transitTokenNotReady = new Binder();
        requestStartTransition(transitions, transitTokenNotReady);

        mDefaultHandler.setSimulateMerge(true);
        mDefaultHandler.mFinishes.get(0).second.onTransitionFinished(null /* wct */);

        // Make sure that the non-ready transition is not merged.
        assertEquals(0, mDefaultHandler.mergeCount());
    }

    @Test
    public void testInterleavedMerging() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        Function<Boolean, IBinder> startATransition = (doMerge) -> {
            IBinder token = new Binder();
            if (doMerge) {
                mDefaultHandler.setShouldMerge(token);
            }
            transitions.requestStartTransition(token,
                    new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
            TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                    .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
            transitions.onTransitionReady(token, info, new StubTransaction(),
                    new StubTransaction());
            return token;
        };

        IBinder transitToken1 = startATransition.apply(false);
        // merge first one
        IBinder transitToken2 = startATransition.apply(true);
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(1, mDefaultHandler.mergeCount());

        // don't merge next one
        IBinder transitToken3 = startATransition.apply(false);
        // make sure nothing happened (since it wasn't merged)
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(1, mDefaultHandler.mergeCount());

        // make a mergable
        IBinder transitToken4 = startATransition.apply(true);
        // make sure nothing happened since there is a non-mergable pending.
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(1, mDefaultHandler.mergeCount());

        // Queue up another mergable
        IBinder transitToken5 = startATransition.apply(true);

        // Queue up a non-mergable
        IBinder transitToken6 = startATransition.apply(false);

        // Our active now looks like: [playing, merged]
        //           and ready queue: [non-mergable, mergable, mergable, non-mergable]
        // finish the playing one
        mDefaultHandler.finishOne();
        mMainExecutor.flushAll();
        // Now we should have the non-mergable playing now with 2 merged:
        //    active: [playing, merged, merged]   queue: [non-mergable]
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(2, mDefaultHandler.mergeCount());

        mDefaultHandler.finishOne();
        mMainExecutor.flushAll();
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(0, mDefaultHandler.mergeCount());

        mDefaultHandler.finishOne();
        mMainExecutor.flushAll();
    }

    @Test
    public void testTransitionOrderMatchesCore() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        IBinder transitToken = new Binder();
        IBinder shellInit = transitions.startTransition(TRANSIT_CLOSE,
                new WindowContainerTransaction(), null /* handler */);
        // make sure we are testing the "New" API.
        verify(mOrganizer, times(1)).startNewTransition(eq(TRANSIT_CLOSE), any());
        // WMCore may not receive the new transition before requesting its own.
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        verify(mOrganizer, times(1)).startTransition(eq(transitToken), any());

        // At this point, WM is working on its transition (the shell-initialized one is still
        // queued), so continue the transition lifecycle for that.
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        transitions.onTransitionReady(transitToken, info, new StubTransaction(),
                new StubTransaction());
        // At this point, if things are not working, we'd get an NPE due to attempting to merge
        // into the shellInit transition which hasn't started yet.
        assertEquals(1, mDefaultHandler.activeCount());
    }

    @Test
    public void testShouldRotateSeamlessly() throws Exception {
        final RunningTaskInfo taskInfo =
                createTaskInfo(1, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final RunningTaskInfo taskInfoPip =
                createTaskInfo(1, WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);

        final DisplayController displays = createTestDisplayController();
        final DisplayLayout displayLayout = displays.getDisplayLayout(DEFAULT_DISPLAY);
        final @Surface.Rotation int upsideDown = displayLayout.getUpsideDownRotation();

        TransitionInfo.Change displayChange = new ChangeBuilder(TRANSIT_CHANGE)
                .setFlags(FLAG_IS_DISPLAY).setRotate().build();
        // Set non-square display so nav bar won't be allowed to move.
        displayChange.getStartAbsBounds().set(0, 0, 1000, 2000);
        final TransitionInfo normalDispRotate = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(displayChange)
                .addChange(new ChangeBuilder(TRANSIT_CHANGE).setTask(taskInfo).setRotate().build())
                .build();
        assertEquals(ROTATION_ANIMATION_ROTATE, DefaultTransitionHandler.getRotationAnimationHint(
                displayChange, normalDispRotate, displays));

        // Seamless if all tasks are seamless
        final TransitionInfo rotateSeamless = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(displayChange)
                .addChange(new ChangeBuilder(TRANSIT_CHANGE).setTask(taskInfo)
                        .setRotate(ROTATION_ANIMATION_SEAMLESS).build())
                .build();
        assertEquals(ROTATION_ANIMATION_SEAMLESS, DefaultTransitionHandler.getRotationAnimationHint(
                displayChange, rotateSeamless, displays));

        // Not seamless if there is PiP (or any other non-seamless task)
        final TransitionInfo pipDispRotate = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(displayChange)
                .addChange(new ChangeBuilder(TRANSIT_CHANGE).setTask(taskInfo)
                        .setRotate(ROTATION_ANIMATION_SEAMLESS).build())
                .addChange(new ChangeBuilder(TRANSIT_CHANGE).setTask(taskInfoPip)
                        .setRotate().build())
                .build();
        assertEquals(ROTATION_ANIMATION_ROTATE, DefaultTransitionHandler.getRotationAnimationHint(
                displayChange, pipDispRotate, displays));

        // Not seamless if there is no changed task.
        final TransitionInfo noTask = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(displayChange)
                .build();
        assertEquals(ROTATION_ANIMATION_ROTATE, DefaultTransitionHandler.getRotationAnimationHint(
                displayChange, noTask, displays));

        // Not seamless if the nav bar cares rotation and one of rotations is upside-down.
        doReturn(false).when(displayLayout).allowSeamlessRotationDespiteNavBarMoving();
        displayChange = new ChangeBuilder(TRANSIT_CHANGE).setFlags(FLAG_IS_DISPLAY)
                .setRotate(upsideDown, ROTATION_ANIMATION_UNSPECIFIED).build();
        final TransitionInfo seamlessUpsideDown = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(displayChange)
                .addChange(new ChangeBuilder(TRANSIT_CHANGE).setTask(taskInfo)
                        .setRotate(upsideDown, ROTATION_ANIMATION_SEAMLESS).build())
                .build();
        assertEquals(ROTATION_ANIMATION_ROTATE, DefaultTransitionHandler.getRotationAnimationHint(
                displayChange, seamlessUpsideDown, displays));

        // Not seamless if system alert windows
        displayChange = new ChangeBuilder(TRANSIT_CHANGE)
                .setFlags(FLAG_IS_DISPLAY | FLAG_DISPLAY_HAS_ALERT_WINDOWS).setRotate().build();
        final TransitionInfo seamlessButAlert = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(displayChange)
                .addChange(new ChangeBuilder(TRANSIT_CHANGE).setTask(taskInfo)
                        .setRotate(ROTATION_ANIMATION_SEAMLESS).build())
                .build();
        assertEquals(ROTATION_ANIMATION_ROTATE, DefaultTransitionHandler.getRotationAnimationHint(
                displayChange, seamlessButAlert, displays));

        // Seamless if display is explicitly seamless.
        displayChange = new ChangeBuilder(TRANSIT_CHANGE).setFlags(FLAG_IS_DISPLAY)
                .setRotate(ROTATION_ANIMATION_SEAMLESS).build();
        final TransitionInfo seamlessDisplay = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(displayChange)
                // The animation hint of task will be ignored.
                .addChange(new ChangeBuilder(TRANSIT_CHANGE).setTask(taskInfo)
                        .setRotate(ROTATION_ANIMATION_ROTATE).build())
                .build();
        assertEquals(ROTATION_ANIMATION_SEAMLESS, DefaultTransitionHandler.getRotationAnimationHint(
                displayChange, seamlessDisplay, displays));
    }

    @Test
    public void testRunWhenIdle() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        Runnable runnable1 = mock(Runnable.class);
        Runnable runnable2 = mock(Runnable.class);
        Runnable runnable3 = mock(Runnable.class);
        Runnable runnable4 = mock(Runnable.class);

        transitions.runOnIdle(runnable1);

        // runnable1 is executed immediately because there are no active transitions.
        verify(runnable1, times(1)).run();

        clearInvocations(runnable1);

        IBinder transitToken1 = new Binder();
        transitions.requestStartTransition(transitToken1,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        TransitionInfo info1 = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        transitions.onTransitionReady(transitToken1, info1, new StubTransaction(),
                new StubTransaction());
        assertEquals(1, mDefaultHandler.activeCount());

        transitions.runOnIdle(runnable2);
        transitions.runOnIdle(runnable3);

        // runnable2 and runnable3 aren't executed immediately because there is an active
        // transaction.

        IBinder transitToken2 = new Binder();
        transitions.requestStartTransition(transitToken2,
                new TransitionRequestInfo(TRANSIT_CLOSE, null /* trigger */, null /* remote */));
        TransitionInfo info2 = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        transitions.onTransitionReady(transitToken2, info2, new StubTransaction(),
                new StubTransaction());
        assertEquals(1, mDefaultHandler.activeCount());

        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        // first transition finished
        verify(mOrganizer, times(1)).finishTransition(eq(transitToken1), any());
        verify(mOrganizer, times(0)).finishTransition(eq(transitToken2), any());
        // But now the "queued" transition is running
        assertEquals(1, mDefaultHandler.activeCount());

        // runnable2 and runnable3 are still not executed because the second transition is still
        // active.
        verify(runnable2, times(0)).run();
        verify(runnable3, times(0)).run();

        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        verify(mOrganizer, times(1)).finishTransition(eq(transitToken2), any());

        // runnable2 and runnable3 are executed after the second transition finishes because there
        // are no other active transitions, runnable1 isn't executed again.
        verify(runnable1, times(0)).run();
        verify(runnable2, times(1)).run();
        verify(runnable3, times(1)).run();

        clearInvocations(runnable2);
        clearInvocations(runnable3);

        transitions.runOnIdle(runnable4);

        // runnable4 is executed immediately because there are no active transitions, all other
        // runnables aren't executed again.
        verify(runnable1, times(0)).run();
        verify(runnable2, times(0)).run();
        verify(runnable3, times(0)).run();
        verify(runnable4, times(1)).run();
    }

    @Test
    public void testObserverLifecycle_basicTransitionFlow() {
        Transitions transitions = createTestTransitions();
        Transitions.TransitionObserver observer = mock(Transitions.TransitionObserver.class);
        transitions.registerObserver(observer);
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        IBinder transitToken = new Binder();
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        SurfaceControl.Transaction startT = new StubTransaction();
        SurfaceControl.Transaction finishT = new StubTransaction();
        transitions.onTransitionReady(transitToken, info, startT, finishT);

        InOrder observerOrder = inOrder(observer);
        observerOrder.verify(observer).onTransitionReady(transitToken, info, startT, finishT);
        observerOrder.verify(observer).onTransitionStarting(transitToken);
        verify(observer, times(0)).onTransitionFinished(eq(transitToken), anyBoolean());
        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        verify(observer).onTransitionFinished(transitToken, false);
    }

    @Test
    public void testObserverLifecycle_queueing() {
        Transitions transitions = createTestTransitions();
        Transitions.TransitionObserver observer = mock(Transitions.TransitionObserver.class);
        transitions.registerObserver(observer);
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        IBinder transitToken1 = new Binder();
        transitions.requestStartTransition(transitToken1,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        TransitionInfo info1 = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        SurfaceControl.Transaction startT1 = new StubTransaction();
        SurfaceControl.Transaction finishT1 = new StubTransaction();
        transitions.onTransitionReady(transitToken1, info1, startT1, finishT1);
        verify(observer).onTransitionReady(transitToken1, info1, startT1, finishT1);

        IBinder transitToken2 = new Binder();
        transitions.requestStartTransition(transitToken2,
                new TransitionRequestInfo(TRANSIT_CLOSE, null /* trigger */, null /* remote */));
        TransitionInfo info2 = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        SurfaceControl.Transaction startT2 = new StubTransaction();
        SurfaceControl.Transaction finishT2 = new StubTransaction();
        transitions.onTransitionReady(transitToken2, info2, startT2, finishT2);
        verify(observer, times(1)).onTransitionReady(transitToken2, info2, startT2, finishT2);
        verify(observer, times(0)).onTransitionStarting(transitToken2);
        verify(observer, times(0)).onTransitionFinished(eq(transitToken1), anyBoolean());
        verify(observer, times(0)).onTransitionFinished(eq(transitToken2), anyBoolean());

        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        // first transition finished
        verify(observer, times(1)).onTransitionFinished(transitToken1, false);
        verify(observer, times(1)).onTransitionStarting(transitToken2);
        verify(observer, times(0)).onTransitionFinished(eq(transitToken2), anyBoolean());

        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        verify(observer, times(1)).onTransitionFinished(transitToken2, false);
    }


    @Test
    public void testObserverLifecycle_merging() {
        Transitions transitions = createTestTransitions();
        Transitions.TransitionObserver observer = mock(Transitions.TransitionObserver.class);
        transitions.registerObserver(observer);
        mDefaultHandler.setSimulateMerge(true);
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        IBinder transitToken1 = new Binder();
        transitions.requestStartTransition(transitToken1,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        TransitionInfo info1 = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        SurfaceControl.Transaction startT1 = new StubTransaction();
        SurfaceControl.Transaction finishT1 = new StubTransaction();
        transitions.onTransitionReady(transitToken1, info1, startT1, finishT1);

        IBinder transitToken2 = new Binder();
        transitions.requestStartTransition(transitToken2,
                new TransitionRequestInfo(TRANSIT_CLOSE, null /* trigger */, null /* remote */));
        TransitionInfo info2 = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        SurfaceControl.Transaction startT2 = new StubTransaction();
        SurfaceControl.Transaction finishT2 = new StubTransaction();
        transitions.onTransitionReady(transitToken2, info2, startT2, finishT2);

        InOrder observerOrder = inOrder(observer);
        observerOrder.verify(observer).onTransitionReady(transitToken2, info2, startT2, finishT2);
        observerOrder.verify(observer).onTransitionMerged(transitToken2, transitToken1);
        verify(observer, times(0)).onTransitionFinished(eq(transitToken1), anyBoolean());

        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();
        // transition + merged all finished.
        verify(observer, times(1)).onTransitionFinished(transitToken1, false);
        // Merged transition won't receive any lifecycle calls beyond ready
        verify(observer, times(0)).onTransitionStarting(transitToken2);
        verify(observer, times(0)).onTransitionFinished(eq(transitToken2), anyBoolean());
    }

    @Test
    public void testObserverLifecycle_mergingAfterQueueing() {
        Transitions transitions = createTestTransitions();
        Transitions.TransitionObserver observer = mock(Transitions.TransitionObserver.class);
        transitions.registerObserver(observer);
        mDefaultHandler.setSimulateMerge(true);
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        // Make a test handler that only responds to multi-window triggers AND only animates
        // Change transitions.
        final WindowContainerTransaction handlerWCT = new WindowContainerTransaction();
        TestTransitionHandler testHandler = new TestTransitionHandler() {
            @Override
            public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
                    @NonNull SurfaceControl.Transaction startTransaction,
                    @NonNull SurfaceControl.Transaction finishTransaction,
                    @NonNull Transitions.TransitionFinishCallback finishCallback) {
                for (TransitionInfo.Change chg : info.getChanges()) {
                    if (chg.getMode() == TRANSIT_CHANGE) {
                        return super.startAnimation(transition, info, startTransaction,
                                finishTransaction, finishCallback);
                    }
                }
                return false;
            }

            @Nullable
            @Override
            public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
                    @NonNull TransitionRequestInfo request) {
                final RunningTaskInfo task = request.getTriggerTask();
                return (task != null && task.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW)
                        ? handlerWCT : null;
            }
        };
        transitions.addHandler(testHandler);

        // Use test handler to play an animation
        IBinder transitToken1 = new Binder();
        RunningTaskInfo mwTaskInfo =
                createTaskInfo(1, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        transitions.requestStartTransition(transitToken1,
                new TransitionRequestInfo(TRANSIT_CHANGE, mwTaskInfo, null /* remote */));
        TransitionInfo change = new TransitionInfoBuilder(TRANSIT_CHANGE)
                .addChange(TRANSIT_CHANGE).build();
        SurfaceControl.Transaction startT1 = new StubTransaction();
        SurfaceControl.Transaction finishT1 = new StubTransaction();
        transitions.onTransitionReady(transitToken1, change, startT1, finishT1);

        // Request the second transition that should be handled by the default handler
        IBinder transitToken2 = new Binder();
        TransitionInfo open = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        transitions.requestStartTransition(transitToken2,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        SurfaceControl.Transaction startT2 = new StubTransaction();
        SurfaceControl.Transaction finishT2 = new StubTransaction();
        transitions.onTransitionReady(transitToken2, open, startT2, finishT2);
        verify(observer).onTransitionReady(transitToken2, open, startT2, finishT2);
        verify(observer, times(0)).onTransitionStarting(transitToken2);

        // Request the third transition that should be merged into the second one
        IBinder transitToken3 = new Binder();
        transitions.requestStartTransition(transitToken3,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        SurfaceControl.Transaction startT3 = new StubTransaction();
        SurfaceControl.Transaction finishT3 = new StubTransaction();
        transitions.onTransitionReady(transitToken3, open, startT3, finishT3);
        verify(observer, times(0)).onTransitionStarting(transitToken2);
        verify(observer).onTransitionReady(transitToken3, open, startT3, finishT3);
        verify(observer, times(0)).onTransitionStarting(transitToken3);

        testHandler.finishAll();
        mMainExecutor.flushAll();

        verify(observer).onTransitionFinished(transitToken1, false);

        mDefaultHandler.finishAll();
        mMainExecutor.flushAll();

        InOrder observerOrder = inOrder(observer);
        observerOrder.verify(observer).onTransitionStarting(transitToken2);
        observerOrder.verify(observer).onTransitionMerged(transitToken3, transitToken2);
        observerOrder.verify(observer).onTransitionFinished(transitToken2, false);

        // Merged transition won't receive any lifecycle calls beyond ready
        verify(observer, times(0)).onTransitionStarting(transitToken3);
        verify(observer, times(0)).onTransitionFinished(eq(transitToken3), anyBoolean());
    }

    @Test
    public void testTransitSleep_squashesRecents() {
        ShellInit shellInit = new ShellInit(mMainExecutor);
        final Transitions transitions =
                new Transitions(mContext, shellInit, mock(ShellController.class), mOrganizer,
                        mTransactionPool, createTestDisplayController(), mMainExecutor,
                        mMainHandler, mAnimExecutor, mock(HomeTransitionObserver.class));
        final RecentsTransitionHandler recentsHandler =
                new RecentsTransitionHandler(shellInit, transitions,
                        mock(RecentTasksController.class), mock(HomeTransitionObserver.class));
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);
        shellInit.init();

        Transitions.TransitionObserver observer = mock(Transitions.TransitionObserver.class);
        transitions.registerObserver(observer);

        RunningTaskInfo task1 = createTaskInfo(1, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_RECENTS);
        RunningTaskInfo task2 = createTaskInfo(2);

        // Start an open transition for the purpose of occupying the ready queue
        final IBinder transitOpen1 = new Binder("transitOpen1");
        final TransitionInfo infoOpen1 =
                new TransitionInfoBuilder(TRANSIT_OPEN)
                        .addChange(TRANSIT_OPEN, task1)
                        .build();
        mMainExecutor.execute(() -> {
            transitions.requestStartTransition(transitOpen1, new TransitionRequestInfo(
                        TRANSIT_OPEN, task1 /* trigger */, null /* remote */));
            onTransitionReady(transitions, transitOpen1, infoOpen1);
        });

        // First transition on the queue should start immediately.
        mMainExecutor.flushAll();
        verify(observer).onTransitionReady(eq(transitOpen1), any(), any(), any());
        verify(observer).onTransitionStarting(eq(transitOpen1));

        // Start recents
        final IRecentsAnimationRunner recentsListener =
                mock(IRecentsAnimationRunner.class, Answers.RETURNS_DEEP_STUBS);
        final IBinder transitRecents = recentsHandler.startRecentsTransition(
                mock(PendingIntent.class) /* intent */,
                mock(Intent.class) /* fillIn */,
                new Bundle() /* options */,
                mock(IApplicationThread.class) /* appThread */,
                recentsListener);
        final TransitionInfo infoRecents =
                new TransitionInfoBuilder(TRANSIT_TO_FRONT)
                        .addChange(TRANSIT_TO_FRONT, task1)
                        .addChange(TRANSIT_CLOSE, task2)
                        .build();
        onTransitionReady(transitions, transitRecents, infoRecents);

        // Start another open transition during recents
        final IBinder transitOpen2 = new Binder("transitOpen2");
        final TransitionInfo infoOpen2 =
                new TransitionInfoBuilder(TRANSIT_OPEN)
                        .addChange(TRANSIT_OPEN, task2)
                        .addChange(TRANSIT_TO_BACK, task1)
                        .build();
        mMainExecutor.execute(() -> {
            transitions.requestStartTransition(transitOpen2,  new TransitionRequestInfo(
                        TRANSIT_OPEN, task2 /* trigger */, null /* remote */));
            onTransitionReady(transitions, transitOpen2, infoOpen2);
        });

        // Finish testOpen1 to start processing the other transitions
        mMainExecutor.execute(() -> {
            mDefaultHandler.finishOne();
        });
        mMainExecutor.flushAll();

        // Recents transition SHOULD start, and merge the open transition, which should NOT start.
        verify(observer).onTransitionFinished(eq(transitOpen1), eq(false) /* aborted */);
        verify(observer).onTransitionReady(eq(transitRecents), any(), any(), any());
        verify(observer).onTransitionStarting(eq(transitRecents));
        verify(observer).onTransitionReady(eq(transitOpen2), any(), any(), any());
        verify(observer).onTransitionMerged(eq(transitOpen2), eq(transitRecents));
        // verify(observer).onTransitionFinished(eq(transitOpen2), eq(true) /* aborted */);

        // Go to sleep
        final IBinder transitSleep = new Binder("transitSleep");
        final TransitionInfo infoSleep = new TransitionInfoBuilder(TRANSIT_SLEEP).build();
        mMainExecutor.execute(() -> {
            transitions.requestStartTransition(transitSleep, new TransitionRequestInfo(
                        TRANSIT_SLEEP, null /* trigger */, null /* remote */));
            onTransitionReady(transitions, transitSleep, infoSleep);
        });
        mMainExecutor.flushAll();

        // Recents transition should finish itself when it sees the sleep transition coming.
        verify(observer).onTransitionFinished(eq(transitRecents), eq(false));
        verify(observer).onTransitionFinished(eq(transitSleep), eq(false));
    }

    private void onTransitionReady(Transitions transitions, IBinder token, TransitionInfo info) {
        transitions.onTransitionReady(token, info, new StubTransaction(),
                new StubTransaction());
    }

    @Test
    public void testEmptyTransition_withKeyguardGoingAway_plays() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        IBinder transitToken = new Binder();
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));

        // Make a no-op transition
        TransitionInfo info = new TransitionInfoBuilder(
                TRANSIT_OPEN, TRANSIT_FLAG_KEYGUARD_GOING_AWAY, true /* noOp */).build();
        transitions.onTransitionReady(transitToken, info, new StubTransaction(),
                new StubTransaction());

        // If keyguard-going-away flag set, then it shouldn't be aborted.
        assertEquals(1, mDefaultHandler.activeCount());
    }

    @Test
    public void testSleepTransition_withKeyguardGoingAway_plays(){
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        IBinder transitToken = new Binder();
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_SLEEP, null /* trigger */, null /* remote */));

        // Make a no-op transition
        TransitionInfo info = new TransitionInfoBuilder(
                TRANSIT_SLEEP, TRANSIT_FLAG_KEYGUARD_GOING_AWAY, true /* noOp */).build();
        transitions.onTransitionReady(transitToken, info, new StubTransaction(),
                new StubTransaction());

        // If keyguard-going-away flag set, then it shouldn't be aborted.
        assertEquals(1, mDefaultHandler.activeCount());
    }

    @Test
    public void testSleepTransition_withChanges_plays(){
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        IBinder transitToken = new Binder();
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_SLEEP, null /* trigger */, null /* remote */));

        // Make a transition with some changes
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_SLEEP)
                .addChange(TRANSIT_OPEN).build();
        info.setTrack(0);
        transitions.onTransitionReady(transitToken, info, new StubTransaction(),
                new StubTransaction());

        // If there is an actual change, then it shouldn't be aborted.
        assertEquals(1, mDefaultHandler.activeCount());
    }


    @Test
    public void testSleepTransition_empty_SyncBySleepHandler() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);

        IBinder transitToken = new Binder();
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_SLEEP, null /* trigger */, null /* remote */));

        // Make a no-op transition
        TransitionInfo info = new TransitionInfoBuilder(
                TRANSIT_SLEEP, 0x0, true /* noOp */).build();
        transitions.onTransitionReady(transitToken, info, new StubTransaction(),
                new StubTransaction());

        // If there is nothing to actually play, it should not be offered to handlers.
        assertEquals(0, mDefaultHandler.activeCount());
    }

    @Test
    public void testMultipleTracks() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);
        TestTransitionHandler alwaysMergeHandler = new TestTransitionHandler();
        alwaysMergeHandler.setSimulateMerge(true);

        final boolean[] becameIdle = new boolean[]{false};

        final WindowContainerTransaction emptyWCT = new WindowContainerTransaction();
        final SurfaceControl.Transaction mockSCT = mock(SurfaceControl.Transaction.class);

        // Make this always merge so we can ensure that it does NOT get a merge-attempt for a
        // different track.
        IBinder transitA = transitions.startTransition(TRANSIT_OPEN, emptyWCT, alwaysMergeHandler);
        // start tracking idle
        transitions.runOnIdle(() -> becameIdle[0] = true);

        IBinder transitB = transitions.startTransition(TRANSIT_OPEN, emptyWCT, mDefaultHandler);
        IBinder transitC = transitions.startTransition(TRANSIT_CLOSE, emptyWCT, mDefaultHandler);

        TransitionInfo infoA = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        infoA.setTrack(0);
        TransitionInfo infoB = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        infoB.setTrack(1);
        TransitionInfo infoC = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        infoC.setTrack(1);

        transitions.onTransitionReady(transitA, infoA, mockSCT, mockSCT);
        assertEquals(1, alwaysMergeHandler.activeCount());
        transitions.onTransitionReady(transitB, infoB, mockSCT, mockSCT);
        // should now be running in parallel
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(1, alwaysMergeHandler.activeCount());
        // make sure we didn't try to merge into a different track.
        assertEquals(0, alwaysMergeHandler.mergeCount());

        // This should be queued-up since it is on track 1 (same as B)
        transitions.onTransitionReady(transitC, infoC, mockSCT, mockSCT);
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(1, alwaysMergeHandler.activeCount());

        // Now finish B and make sure C starts
        mDefaultHandler.finishOne();
        mMainExecutor.flushAll();

        // Now C and A running in parallel
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(1, alwaysMergeHandler.activeCount());
        assertEquals(0, alwaysMergeHandler.mergeCount());

        // Finish A
        alwaysMergeHandler.finishOne();
        mMainExecutor.flushAll();

        // C still running
        assertEquals(0, alwaysMergeHandler.activeCount());
        assertEquals(1, mDefaultHandler.activeCount());
        assertFalse(becameIdle[0]);

        mDefaultHandler.finishOne();
        mMainExecutor.flushAll();

        assertEquals(0, mDefaultHandler.activeCount());
        assertTrue(becameIdle[0]);
    }

    @Test
    public void testSyncMultipleTracks() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);
        TestTransitionHandler secondHandler = new TestTransitionHandler();

        // Disable the forced early-sync-finish so that we can test the ordering mechanics.
        transitions.setDisableForceSyncForTest(true);
        mDefaultHandler.mFinishOnSync = false;
        secondHandler.mFinishOnSync = false;

        final WindowContainerTransaction emptyWCT = new WindowContainerTransaction();
        final SurfaceControl.Transaction mockSCT = mock(SurfaceControl.Transaction.class);

        // Make this always merge so we can ensure that it does NOT get a merge-attempt for a
        // different track.
        IBinder transitA = transitions.startTransition(TRANSIT_OPEN, emptyWCT, mDefaultHandler);
        IBinder transitB = transitions.startTransition(TRANSIT_OPEN, emptyWCT, secondHandler);
        IBinder transitC = transitions.startTransition(TRANSIT_CLOSE, emptyWCT, secondHandler);
        IBinder transitSync = transitions.startTransition(TRANSIT_CLOSE, emptyWCT, mDefaultHandler);
        IBinder transitD = transitions.startTransition(TRANSIT_OPEN, emptyWCT, secondHandler);
        IBinder transitE = transitions.startTransition(TRANSIT_OPEN, emptyWCT, mDefaultHandler);

        TransitionInfo infoA = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        infoA.setTrack(0);
        TransitionInfo infoB = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        infoB.setTrack(1);
        TransitionInfo infoC = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        infoC.setTrack(1);
        TransitionInfo infoSync = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        infoSync.setTrack(0);
        infoSync.setFlags(FLAG_SYNC);
        TransitionInfo infoD = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        infoD.setTrack(1);
        TransitionInfo infoE = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        infoE.setTrack(0);

        // Start A B and C where A is track 0, B and C are track 1 (C should be queued)
        transitions.onTransitionReady(transitA, infoA, mockSCT, mockSCT);
        transitions.onTransitionReady(transitB, infoB, mockSCT, mockSCT);
        transitions.onTransitionReady(transitC, infoC, mockSCT, mockSCT);
        // should now be running in parallel (with one queued)
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(1, secondHandler.activeCount());

        // Make the sync ready and the following (D, E) ready.
        transitions.onTransitionReady(transitSync, infoSync, mockSCT, mockSCT);
        transitions.onTransitionReady(transitD, infoD, mockSCT, mockSCT);
        transitions.onTransitionReady(transitE, infoE, mockSCT, mockSCT);

        // nothing should have happened yet since the sync is queued and blocking everything.
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(1, secondHandler.activeCount());

        // Finish A (which is track 0 like the sync).
        mDefaultHandler.finishOne();
        mMainExecutor.flushAll();

        // Even though the sync is on track 0 and track 0 became idle, it should NOT be started yet
        // because it must wait for everything. Additionally, D/E shouldn't start yet either.
        assertEquals(0, mDefaultHandler.activeCount());
        assertEquals(1, secondHandler.activeCount());

        // Now finish B and C -- this should then allow the sync to start and D to run (in parallel)
        secondHandler.finishOne();
        secondHandler.finishOne();
        mMainExecutor.flushAll();

        // Now the sync and D (on track 1) should be running
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(1, secondHandler.activeCount());

        // finish the sync. track 0 still has E
        mDefaultHandler.finishOne();
        mMainExecutor.flushAll();
        assertEquals(1, mDefaultHandler.activeCount());

        mDefaultHandler.finishOne();
        secondHandler.finishOne();
        mMainExecutor.flushAll();

        assertEquals(0, mDefaultHandler.activeCount());
        assertEquals(0, secondHandler.activeCount());
    }

    @Test
    public void testForceSyncTracks() {
        Transitions transitions = createTestTransitions();
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);
        TestTransitionHandler secondHandler = new TestTransitionHandler();

        final WindowContainerTransaction emptyWCT = new WindowContainerTransaction();
        final SurfaceControl.Transaction mockSCT = mock(SurfaceControl.Transaction.class);

        // Make this always merge so we can ensure that it does NOT get a merge-attempt for a
        // different track.
        IBinder transitA = transitions.startTransition(TRANSIT_OPEN, emptyWCT, mDefaultHandler);
        IBinder transitB = transitions.startTransition(TRANSIT_OPEN, emptyWCT, mDefaultHandler);
        IBinder transitC = transitions.startTransition(TRANSIT_CLOSE, emptyWCT, secondHandler);
        IBinder transitD = transitions.startTransition(TRANSIT_OPEN, emptyWCT, secondHandler);
        IBinder transitSync = transitions.startTransition(TRANSIT_CLOSE, emptyWCT, mDefaultHandler);

        TransitionInfo infoA = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        infoA.setTrack(0);
        TransitionInfo infoB = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        infoB.setTrack(0);
        TransitionInfo infoC = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        infoC.setTrack(1);
        TransitionInfo infoD = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).build();
        infoD.setTrack(1);
        TransitionInfo infoSync = new TransitionInfoBuilder(TRANSIT_CLOSE)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
        infoSync.setTrack(0);
        infoSync.setFlags(FLAG_SYNC);

        transitions.onTransitionReady(transitA, infoA, mockSCT, mockSCT);
        transitions.onTransitionReady(transitB, infoB, mockSCT, mockSCT);
        transitions.onTransitionReady(transitC, infoC, mockSCT, mockSCT);
        transitions.onTransitionReady(transitD, infoD, mockSCT, mockSCT);
        // should now be running in parallel (with one queued in each)
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(1, secondHandler.activeCount());

        // Make the sync ready.
        transitions.onTransitionReady(transitSync, infoSync, mockSCT, mockSCT);
        mMainExecutor.flushAll();

        // Everything should be forced-finish now except the sync
        assertEquals(1, mDefaultHandler.activeCount());
        assertEquals(0, secondHandler.activeCount());

        mDefaultHandler.finishOne();
        mMainExecutor.flushAll();

        assertEquals(0, mDefaultHandler.activeCount());
    }

    @Test
    public void testCloseTransitAnimationWhenClosingChangesExists() {
        Transitions transitions = createTestTransitions();
        Transitions.TransitionObserver observer = mock(Transitions.TransitionObserver.class);
        transitions.registerObserver(observer);
        transitions.replaceDefaultHandlerForTest(mDefaultHandler);
        final TransitionAnimation transitionAnimation = new TransitionAnimation(mContext, false,
                Transitions.TAG);
        spyOn(transitionAnimation);

        // Creating a transition by the app hooking the back key event to start the
        // previous activity with FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        // flags in order to clear the top activity and bring the exist previous activity to front.
        // Expects the activity transition should playing the close animation instead the initiated
        // open animation made by startActivity.
        IBinder transitToken = new Binder();
        transitions.requestStartTransition(transitToken,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
        TransitionInfo info = new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_CLOSE).addChange(TRANSIT_TO_FRONT).build();
        transitions.onTransitionReady(transitToken, info, new StubTransaction(),
                new StubTransaction());

        final int type = getTransitionTypeFromInfo(info);
        assertEquals(TRANSIT_CLOSE, type);

        TransitionAnimationHelper.loadAttributeAnimation(type, info, info.getChanges().get(0), 0,
                transitionAnimation, false);
        verify(transitionAnimation).loadDefaultAnimationAttr(
                eq(R.styleable.WindowAnimation_activityCloseExitAnimation), anyBoolean());

        TransitionAnimationHelper.loadAttributeAnimation(type, info, info.getChanges().get(1), 0,
                transitionAnimation, false);
        verify(transitionAnimation).loadDefaultAnimationAttr(
                eq(R.styleable.WindowAnimation_activityCloseEnterAnimation), anyBoolean());
    }

    class ChangeBuilder {
        final TransitionInfo.Change mChange;

        ChangeBuilder(@WindowManager.TransitionType int mode) {
            mChange = new TransitionInfo.Change(null /* token */, createMockSurface(true));
            mChange.setMode(mode);
        }

        ChangeBuilder setFlags(@TransitionInfo.ChangeFlags int flags) {
            mChange.setFlags(flags);
            return this;
        }

        ChangeBuilder setTask(RunningTaskInfo taskInfo) {
            mChange.setTaskInfo(taskInfo);
            return this;
        }

        ChangeBuilder setRotate(int anim) {
            return setRotate(Surface.ROTATION_90, anim);
        }

        ChangeBuilder setRotate() {
            return setRotate(ROTATION_ANIMATION_UNSPECIFIED);
        }

        ChangeBuilder setRotate(@Surface.Rotation int target, int anim) {
            mChange.setRotation(Surface.ROTATION_0, target);
            mChange.setRotationAnimation(anim);
            return this;
        }

        TransitionInfo.Change build() {
            return mChange;
        }
    }

    class TestTransitionHandler implements Transitions.TransitionHandler {
        ArrayList<Pair<IBinder, Transitions.TransitionFinishCallback>> mFinishes =
                new ArrayList<>();
        final ArrayList<IBinder> mMerged = new ArrayList<>();
        boolean mSimulateMerge = false;
        boolean mFinishOnSync = true;
        final ArraySet<IBinder> mShouldMerge = new ArraySet<>();

        @Override
        public boolean startAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction startTransaction,
                @NonNull SurfaceControl.Transaction finishTransaction,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
            mFinishes.add(new Pair<>(transition, finishCallback));
            return true;
        }

        @Override
        public void mergeAnimation(@NonNull IBinder transition, @NonNull TransitionInfo info,
                @NonNull SurfaceControl.Transaction t, @NonNull IBinder mergeTarget,
                @NonNull Transitions.TransitionFinishCallback finishCallback) {
            if (mFinishOnSync && info.getType() == TRANSIT_SLEEP) {
                for (int i = 0; i < mFinishes.size(); ++i) {
                    if (mFinishes.get(i).first != mergeTarget) continue;
                    mFinishes.remove(i).second.onTransitionFinished(null);
                    return;
                }
            }
            if (!(mSimulateMerge || mShouldMerge.contains(transition))) return;
            mMerged.add(transition);
            finishCallback.onTransitionFinished(null /* wct */);
        }

        @Nullable
        @Override
        public WindowContainerTransaction handleRequest(@NonNull IBinder transition,
                @NonNull TransitionRequestInfo request) {
            return null;
        }

        void setSimulateMerge(boolean sim) {
            mSimulateMerge = sim;
        }

        void setShouldMerge(IBinder toMerge) {
            mShouldMerge.add(toMerge);
        }

        void finishAll() {
            final ArrayList<Pair<IBinder, Transitions.TransitionFinishCallback>> finishes =
                    mFinishes;
            mFinishes = new ArrayList<>();
            for (int i = finishes.size() - 1; i >= 0; --i) {
                finishes.get(i).second.onTransitionFinished(null /* wct */);
            }
            mShouldMerge.clear();
        }

        void finishOne() {
            Pair<IBinder, Transitions.TransitionFinishCallback> fin = mFinishes.remove(0);
            mMerged.clear();
            fin.second.onTransitionFinished(null /* wct */);
        }

        int activeCount() {
            return mFinishes.size();
        }

        int mergeCount() {
            return mMerged.size();
        }
    }

    private static void requestStartTransition(Transitions transitions, IBinder token) {
        transitions.requestStartTransition(token,
                new TransitionRequestInfo(TRANSIT_OPEN, null /* trigger */, null /* remote */));
    }

    private static void onTransitionReady(Transitions transitions, IBinder token) {
        transitions.onTransitionReady(token, createTransitionInfo(),
                new StubTransaction(), new StubTransaction());
    }

    private static TransitionInfo createTransitionInfo() {
        return new TransitionInfoBuilder(TRANSIT_OPEN)
                .addChange(TRANSIT_OPEN).addChange(TRANSIT_CLOSE).build();
    }

    private static SurfaceControl createMockSurface(boolean valid) {
        SurfaceControl sc = mock(SurfaceControl.class);
        doReturn(valid).when(sc).isValid();
        return sc;
    }

    private static RunningTaskInfo createTaskInfo(int taskId, int windowingMode, int activityType) {
        RunningTaskInfo taskInfo = new RunningTaskInfo();
        taskInfo.taskId = taskId;
        taskInfo.topActivityType = activityType;
        taskInfo.configuration.windowConfiguration.setWindowingMode(windowingMode);
        taskInfo.configuration.windowConfiguration.setActivityType(activityType);
        taskInfo.token = mock(WindowContainerToken.class);
        return taskInfo;
    }

    private static RunningTaskInfo createTaskInfo(int taskId) {
        return createTaskInfo(taskId, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
    }

    private DisplayController createTestDisplayController() {
        DisplayLayout displayLayout = mock(DisplayLayout.class);
        doReturn(Surface.ROTATION_180).when(displayLayout).getUpsideDownRotation();
        // By default we ignore nav bar in deciding if a seamless rotation is allowed.
        doReturn(true).when(displayLayout).allowSeamlessRotationDespiteNavBarMoving();

        DisplayController out = mock(DisplayController.class);
        doReturn(displayLayout).when(out).getDisplayLayout(DEFAULT_DISPLAY);
        return out;
    }

    private Transitions createTestTransitions() {
        ShellInit shellInit = new ShellInit(mMainExecutor);
        final Transitions t = new Transitions(mContext, shellInit, mock(ShellController.class),
                mOrganizer, mTransactionPool, createTestDisplayController(), mMainExecutor,
                mMainHandler, mAnimExecutor, mock(HomeTransitionObserver.class));
        shellInit.init();
        return t;
    }
}
