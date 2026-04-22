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

package com.android.wm.shell.back;

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.TRANSIT_CLOSE;
import static android.view.WindowManager.TRANSIT_CLOSE_PREPARE_BACK_NAVIGATION;
import static android.view.WindowManager.TRANSIT_OPEN;
import static android.view.WindowManager.TRANSIT_PREPARE_BACK_NAVIGATION;
import static android.window.BackNavigationInfo.KEY_NAVIGATION_FINISHED;
import static android.window.TransitionInfo.FLAG_BACK_GESTURE_ANIMATED;
import static android.window.TransitionInfo.FLAG_MOVED_TO_TOP;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.ActivityManager;
import android.app.IActivityTaskManager;
import android.app.WindowConfiguration;
import android.content.pm.ApplicationInfo;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteCallback;
import android.os.RemoteException;
import android.platform.test.annotations.EnableFlags;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.IRemoteAnimationRunner;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.WindowManager;
import android.window.BackEvent;
import android.window.BackMotionEvent;
import android.window.BackNavigationInfo;
import android.window.IBackAnimationFinishedCallback;
import android.window.IOnBackInvokedCallback;
import android.window.IWindowContainerToken;
import android.window.TransitionInfo;
import android.window.TransitionRequestInfo;
import android.window.WindowContainerToken;

import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;

import com.android.wm.shell.RootTaskDisplayAreaOrganizer;
import com.android.wm.shell.ShellTestCase;
import com.android.wm.shell.TestShellExecutor;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.transition.Transitions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * atest WMShellUnitTests:BackAnimationControllerTest
 */
@TestableLooper.RunWithLooper
@SmallTest
@RunWith(AndroidTestingRunner.class)
public class BackAnimationControllerTest extends ShellTestCase {

    private final TestShellExecutor mShellExecutor = new TestShellExecutor();

    private ShellInit mShellInit;
    @Mock
    private IActivityTaskManager mActivityTaskManager;

    @Mock
    private IOnBackInvokedCallback mAppCallback;

    @Mock
    private IOnBackInvokedCallback mAnimatorCallback;

    @Mock
    private IBackAnimationFinishedCallback mBackAnimationFinishedCallback;

    @Mock
    private IRemoteAnimationRunner mBackAnimationRunner;

    @Mock
    private ShellController mShellController;

    @Mock
    private BackAnimationBackground mAnimationBackground;

    @Mock
    private InputManager mInputManager;
    @Mock
    private ShellCommandHandler mShellCommandHandler;
    @Mock
    private Transitions mTransitions;
    @Mock
    private RootTaskDisplayAreaOrganizer mRootTaskDisplayAreaOrganizer;
    @Mock
    private Handler mHandler;
    @Mock
    private Transitions.TransitionHandler mTakeoverHandler;

    private BackAnimationController mController;

    private DefaultCrossActivityBackAnimation mDefaultCrossActivityBackAnimation;
    private CrossTaskBackAnimation mCrossTaskBackAnimation;
    private ShellBackAnimationRegistry mShellBackAnimationRegistry;
    private Rect mTouchableRegion;

    private BackAnimationController.BackTransitionHandler mBackTransitionHandler;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext.addMockSystemService(InputManager.class, mInputManager);
        mContext.getApplicationInfo().privateFlags |= ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;
        mShellInit = spy(new ShellInit(mShellExecutor));
        mDefaultCrossActivityBackAnimation = new DefaultCrossActivityBackAnimation(mContext,
                mAnimationBackground, mRootTaskDisplayAreaOrganizer, mHandler);
        mCrossTaskBackAnimation = new CrossTaskBackAnimation(mContext, mAnimationBackground,
                mHandler);
        mShellBackAnimationRegistry =
                new ShellBackAnimationRegistry(mDefaultCrossActivityBackAnimation,
                        mCrossTaskBackAnimation, /* dialogCloseAnimation= */ null,
                        new CustomCrossActivityBackAnimation(mContext, mAnimationBackground,
                                mRootTaskDisplayAreaOrganizer, mHandler),
                        /* defaultBackToHomeAnimation= */ null);
        mController =
                new BackAnimationController(
                        mShellInit,
                        mShellController,
                        mShellExecutor,
                        mActivityTaskManager,
                        mContext,
                        mAnimationBackground,
                        mShellBackAnimationRegistry,
                        mShellCommandHandler,
                        mTransitions,
                        mHandler);
        mShellInit.init();
        mShellExecutor.flushAll();
        mTouchableRegion = new Rect(0, 0, 100, 100);
        mController.mTouchableArea.set(mTouchableRegion);
        mBackTransitionHandler = mController.mBackTransitionHandler;
        spyOn(mBackTransitionHandler);
    }

    private void createNavigationInfo(int backType,
            boolean enableAnimation,
            boolean isAnimationCallback) {
        BackNavigationInfo.Builder builder =
                new BackNavigationInfo.Builder()
                        .setType(backType)
                        .setOnBackNavigationDone(new RemoteCallback((bundle) -> {}))
                        .setOnBackInvokedCallback(mAppCallback)
                        .setPrepareRemoteAnimation(enableAnimation)
                        .setAnimationCallback(isAnimationCallback)
                        .setTouchableRegion(mTouchableRegion);

        createNavigationInfo(builder);
    }

    private void createNavigationInfo(BackNavigationInfo.Builder builder) {
        try {
            doReturn(builder.build()).when(mActivityTaskManager)
                    .startBackNavigation(any(), any());
        } catch (RemoteException ex) {
            ex.rethrowFromSystemServer();
        }
    }

    RemoteAnimationTarget createAnimationTarget() {
        SurfaceControl topWindowLeash = new SurfaceControl.Builder()
                .setName("FakeLeash")
                .build();
        return new RemoteAnimationTarget(-1, RemoteAnimationTarget.MODE_CLOSING, topWindowLeash,
                false, new Rect(), new Rect(), -1,
                new Point(0, 0), new Rect(), new Rect(), new WindowConfiguration(),
                true, null, null, null, false, -1);
    }

    private void triggerBackGesture() {
        doStartEvents(0, 0);
        mController.setTriggerBack(true);
    }

    private void releaseBackGesture() {
        doMotionEvent(MotionEvent.ACTION_UP, 0);
    }

    @Test
    public void instantiateController_addInitCallback() {
        verify(mShellInit, times(1)).addInitCallback(any(), any());
    }

    @Test
    public void instantiateController_addExternalInterface() {
        verify(mShellController, times(1)).addExternalInterface(
                eq(IBackAnimation.DESCRIPTOR), any(), any());
    }

    @Test
    public void verifyNavigationFinishes() throws RemoteException {
        final int[] testTypes =
                new int[] {
                    BackNavigationInfo.TYPE_RETURN_TO_HOME,
                    BackNavigationInfo.TYPE_CROSS_TASK,
                    BackNavigationInfo.TYPE_CROSS_ACTIVITY,
                    BackNavigationInfo.TYPE_DIALOG_CLOSE,
                    BackNavigationInfo.TYPE_CALLBACK
                };

        for (int type : testTypes) {
            registerAnimation(type);
        }

        for (int type : testTypes) {
            final ResultListener result = new ResultListener();
            createNavigationInfo(new BackNavigationInfo.Builder()
                    .setType(type)
                    .setOnBackInvokedCallback(mAppCallback)
                    .setPrepareRemoteAnimation(true)
                    .setOnBackNavigationDone(new RemoteCallback(result))
                    .setTouchableRegion(mTouchableRegion));
            triggerBackGesture();
            simulateRemoteAnimationStart();
            mShellExecutor.flushAll();
            releaseBackGesture();
            simulateRemoteAnimationFinished();
            mShellExecutor.flushAll();

            assertTrue("Navigation Done callback not called for "
                    + BackNavigationInfo.typeToString(type), result.mBackNavigationDone);
            assertTrue("TriggerBack should have been true", result.mTriggerBack);
        }
    }

    @Test
    public void backToHome_dispatchesEvents() throws RemoteException {
        registerAnimation(BackNavigationInfo.TYPE_RETURN_TO_HOME);
        createNavigationInfo(BackNavigationInfo.TYPE_RETURN_TO_HOME,
                /* enableAnimation = */ true,
                /* isAnimationCallback = */ false);

        doStartEvents(0, 100);

        simulateRemoteAnimationStart();

        verify(mAnimatorCallback).onBackStarted(any(BackMotionEvent.class));
        verify(mBackAnimationRunner).onAnimationStart(anyInt(), any(), any(), any(), any());
        ArgumentCaptor<BackMotionEvent> backEventCaptor =
                ArgumentCaptor.forClass(BackMotionEvent.class);
        verify(mAnimatorCallback, atLeastOnce()).onBackProgressed(backEventCaptor.capture());

        // Check that back invocation is dispatched.
        mController.setTriggerBack(true);   // Fake trigger back
        doMotionEvent(MotionEvent.ACTION_UP, 0);
        verify(mAnimatorCallback).onBackInvoked();
    }

    @Test
    public void backToHomeWithAnimationCallback_dispatchesEvents() throws RemoteException {
        registerAnimation(BackNavigationInfo.TYPE_RETURN_TO_HOME);
        createNavigationInfo(BackNavigationInfo.TYPE_RETURN_TO_HOME,
                /* enableAnimation = */ true,
                /* isAnimationCallback = */ true);

        // Check that back start and progress is dispatched when first move.
        doStartEvents(0, 100);

        simulateRemoteAnimationStart();

        verify(mAnimatorCallback).onBackStarted(any(BackMotionEvent.class));
        verify(mBackAnimationRunner).onAnimationStart(anyInt(), any(), any(), any(), any());
        ArgumentCaptor<BackMotionEvent> backEventCaptor =
                ArgumentCaptor.forClass(BackMotionEvent.class);
        verify(mAnimatorCallback, atLeastOnce()).onBackProgressed(backEventCaptor.capture());

        // Check that back invocation is dispatched.
        mController.setTriggerBack(true);   // Fake trigger back
        doMotionEvent(MotionEvent.ACTION_UP, 0);
        verify(mAnimatorCallback).onBackInvoked();
    }

    @Test
    public void gestureQueued_WhenPreviousTransitionHasNotYetEnded() throws RemoteException {
        registerAnimation(BackNavigationInfo.TYPE_RETURN_TO_HOME);
        createNavigationInfo(BackNavigationInfo.TYPE_RETURN_TO_HOME,
                /* enableAnimation = */ true,
                /* isAnimationCallback = */ false);

        triggerBackGesture();
        simulateRemoteAnimationStart();
        releaseBackGesture();

        // Check that back invocation is dispatched.
        verify(mAnimatorCallback).onBackInvoked();
        verify(mBackAnimationRunner).onAnimationStart(anyInt(), any(), any(), any(), any());

        reset(mAnimatorCallback);
        reset(mBackAnimationRunner);

        // Verify that we prevent any interaction with the animator callback in case a new gesture
        // starts while the current back animation has not ended, instead the gesture is queued
        triggerBackGesture();
        verify(mAnimatorCallback).setTriggerBack(eq(true));
        verifyNoMoreInteractions(mAnimatorCallback);

        // Finish previous back navigation.
        simulateRemoteAnimationFinished();

        // Verify that releasing the gesture causes back key to be injected
        releaseBackGesture();
        verify(mInputManager, times(2))
                .injectInputEvent(any(KeyEvent.class), any(Integer.class));

        // Verify that we start accepting gestures again once transition finishes.
        doStartEvents(0, 100);

        simulateRemoteAnimationStart();
        verify(mAnimatorCallback).onBackStarted(any());
        verify(mBackAnimationRunner).onAnimationStart(anyInt(), any(), any(), any(), any());
    }

    @Test
    public void queuedFinishedGesture_RunsAfterPreviousTransitionEnded() throws RemoteException {
        registerAnimation(BackNavigationInfo.TYPE_RETURN_TO_HOME);
        createNavigationInfo(BackNavigationInfo.TYPE_RETURN_TO_HOME,
                /* enableAnimation = */ true,
                /* isAnimationCallback = */ false);

        triggerBackGesture();
        simulateRemoteAnimationStart();
        releaseBackGesture();

        // Check that back invocation is dispatched.
        verify(mAnimatorCallback).onBackInvoked();
        verify(mBackAnimationRunner).onAnimationStart(anyInt(), any(), any(), any(), any());

        reset(mAnimatorCallback);
        reset(mBackAnimationRunner);

        // Verify that we prevent any interaction with the animator callback in case a new gesture
        // starts while the current back animation has not ended, instead the gesture is queued
        triggerBackGesture();
        releaseBackGesture();
        verify(mAnimatorCallback).setTriggerBack(eq(true));
        verifyNoMoreInteractions(mAnimatorCallback);

        // Finish previous back navigation.
        simulateRemoteAnimationFinished();

        // Verify that back key press is injected after previous back navigation has ended
        verify(mInputManager, times(2))
                .injectInputEvent(any(KeyEvent.class), any(Integer.class));

        // Verify that we start accepting gestures again once transition finishes.
        doStartEvents(0, 100);

        simulateRemoteAnimationStart();
        verify(mAnimatorCallback).onBackStarted(any());
        verify(mBackAnimationRunner).onAnimationStart(anyInt(), any(), any(), any(), any());
    }

    @Test
    public void gestureNotQueued_WhenPreviousGestureIsPostCommitCancelling()
            throws RemoteException {
        registerAnimation(BackNavigationInfo.TYPE_RETURN_TO_HOME);
        createNavigationInfo(BackNavigationInfo.TYPE_RETURN_TO_HOME,
                /* enableAnimation = */ true,
                /* isAnimationCallback = */ false);

        doStartEvents(0, 100);
        simulateRemoteAnimationStart();
        releaseBackGesture();

        // Check that back cancellation is dispatched.
        verify(mAnimatorCallback).onBackCancelled();
        verify(mBackAnimationRunner).onAnimationStart(anyInt(), any(), any(), any(), any());

        reset(mAnimatorCallback);
        reset(mBackAnimationRunner);

        // Verify that a new start event is dispatched if a new gesture is started during the
        // post-commit cancel phase
        triggerBackGesture();
        verify(mAnimatorCallback).onBackStarted(any());
        verify(mBackAnimationRunner).onAnimationStart(anyInt(), any(), any(), any(), any());
    }

    @Test
    public void acceptsGesture_transitionTimeout() throws RemoteException {
        registerAnimation(BackNavigationInfo.TYPE_RETURN_TO_HOME);
        createNavigationInfo(BackNavigationInfo.TYPE_RETURN_TO_HOME,
                /* enableAnimation = */ true,
                /* isAnimationCallback = */ false);

        // In case it is still running in animation.
        doNothing().when(mAnimatorCallback).onBackInvoked();

        triggerBackGesture();
        simulateRemoteAnimationStart();
        mShellExecutor.flushAll();

        releaseBackGesture();

        // Simulate transition timeout.
        mShellExecutor.flushAll();
        reset(mAnimatorCallback);

        doStartEvents(0, 100);
        simulateRemoteAnimationStart();
        verify(mAnimatorCallback).onBackStarted(any());
    }

    @Test
    public void cancelBackInvokeWhenLostFocus() throws RemoteException {
        registerAnimation(BackNavigationInfo.TYPE_RETURN_TO_HOME);

        createNavigationInfo(BackNavigationInfo.TYPE_RETURN_TO_HOME,
                /* enableAnimation = */ true,
                /* isAnimationCallback = */ false);

        doStartEvents(0, 100);

        simulateRemoteAnimationStart();
        verify(mAnimatorCallback).onBackStarted(any());
        verify(mBackAnimationRunner).onAnimationStart(anyInt(), any(), any(), any(), any());

        // Check that back invocation is dispatched.
        mController.setTriggerBack(true);   // Fake trigger back

        // In case the focus has been changed.
        mController.mNavigationObserver.sendResult(null);
        mShellExecutor.flushAll();
        verify(mAnimatorCallback).onBackCancelled();

        // No more back invoke.
        doMotionEvent(MotionEvent.ACTION_UP, 0);
        verify(mAnimatorCallback, never()).onBackInvoked();
    }

    @Test
    public void animationNotDefined() throws RemoteException {
        final int[] testTypes =
                new int[] {
                    BackNavigationInfo.TYPE_RETURN_TO_HOME,
                    BackNavigationInfo.TYPE_CROSS_TASK,
                    BackNavigationInfo.TYPE_CROSS_ACTIVITY,
                    BackNavigationInfo.TYPE_DIALOG_CLOSE
                };

        for (int type : testTypes) {
            unregisterAnimation(type);
        }

        for (int type : testTypes) {
            final ResultListener result = new ResultListener();
            createNavigationInfo(new BackNavigationInfo.Builder()
                    .setType(type)
                    .setOnBackInvokedCallback(mAppCallback)
                    .setPrepareRemoteAnimation(true)
                    .setOnBackNavigationDone(new RemoteCallback(result))
                    .setTouchableRegion(mTouchableRegion));
            triggerBackGesture();
            simulateRemoteAnimationStart();
            mShellExecutor.flushAll();

            releaseBackGesture();
            mShellExecutor.flushAll();

            assertTrue("Navigation Done callback not called for "
                    + BackNavigationInfo.typeToString(type), result.mBackNavigationDone);
            assertTrue("TriggerBack should have been true", result.mTriggerBack);
        }

        verify(mAppCallback, never()).onBackStarted(any());
        verify(mAppCallback, never()).onBackProgressed(any());
        verify(mAppCallback, times(testTypes.length)).onBackInvoked();

        verify(mAnimatorCallback, never()).onBackStarted(any());
        verify(mAnimatorCallback, never()).onBackProgressed(any());
        verify(mAnimatorCallback, never()).onBackInvoked();
    }

    @Test
    public void appCallback_receivesStartAndInvoke() throws RemoteException {
        registerAnimation(BackNavigationInfo.TYPE_RETURN_TO_HOME);

        final int type = BackNavigationInfo.TYPE_CALLBACK;
        final ResultListener result = new ResultListener();
        createNavigationInfo(new BackNavigationInfo.Builder()
                .setType(type)
                .setOnBackInvokedCallback(mAppCallback)
                .setOnBackNavigationDone(new RemoteCallback(result))
                .setTouchableRegion(mTouchableRegion)
                .setAppProgressAllowed(true));
        triggerBackGesture();
        mShellExecutor.flushAll();
        releaseBackGesture();
        mShellExecutor.flushAll();

        assertTrue("Navigation Done callback not called for "
                + BackNavigationInfo.typeToString(type), result.mBackNavigationDone);
        assertTrue("TriggerBack should have been true", result.mTriggerBack);

        verify(mAppCallback, times(1)).onBackStarted(any());
        verify(mAppCallback, times(1)).onBackInvoked();
        // Progress events should be generated from the app process.
        verify(mAppCallback, never()).onBackProgressed(any());

        verify(mAnimatorCallback, never()).onBackStarted(any());
        verify(mAnimatorCallback, never()).onBackProgressed(any());
        verify(mAnimatorCallback, never()).onBackInvoked();
    }

    @EnableFlags({com.android.systemui.shared.Flags.FLAG_RETURN_ANIMATION_FRAMEWORK_LIBRARY,
            com.android.systemui.shared.Flags.FLAG_RETURN_ANIMATION_FRAMEWORK_LONG_LIVED,
            com.android.window.flags2.Flags.FLAG_UNIFY_BACK_NAVIGATION_TRANSITION})
    @Test
    public void appCallback_receivesTakeoverHandler_whenAvailable() throws RemoteException {
        registerAnimation(BackNavigationInfo.TYPE_CROSS_TASK);
        mBackTransitionHandler.mTakeoverHandler = mTakeoverHandler;

        final int type = BackNavigationInfo.TYPE_CALLBACK;
        final ResultListener result = new ResultListener();
        createNavigationInfo(new BackNavigationInfo.Builder()
                .setType(type)
                .setOnBackInvokedCallback(mAppCallback)
                .setOnBackNavigationDone(new RemoteCallback(result))
                .setTouchableRegion(mTouchableRegion)
                .setAppProgressAllowed(true));

        triggerBackGesture();
        mShellExecutor.flushAll();
        releaseBackGesture();
        mShellExecutor.flushAll();

        verify(mAppCallback).setHandoffHandler(notNull());
    }

    @Test
    public void appCallback_doesNotReceiveTakeoverHandler_whenUnavailable() throws RemoteException {
        registerAnimation(BackNavigationInfo.TYPE_CROSS_TASK);

        final int type = BackNavigationInfo.TYPE_CALLBACK;
        final ResultListener result = new ResultListener();
        createNavigationInfo(new BackNavigationInfo.Builder()
                .setType(type)
                .setOnBackInvokedCallback(mAppCallback)
                .setOnBackNavigationDone(new RemoteCallback(result))
                .setTouchableRegion(mTouchableRegion)
                .setAppProgressAllowed(true));
        triggerBackGesture();
        mShellExecutor.flushAll();
        releaseBackGesture();
        mShellExecutor.flushAll();

        verify(mAppCallback).setHandoffHandler(isNull());
    }

    @Test
    public void skipsCancelWithoutStart() throws RemoteException {
        final int type = BackNavigationInfo.TYPE_CALLBACK;
        final ResultListener result = new ResultListener();
        createNavigationInfo(new BackNavigationInfo.Builder()
                .setType(type)
                .setOnBackInvokedCallback(mAppCallback)
                .setOnBackNavigationDone(new RemoteCallback(result))
                .setTouchableRegion(mTouchableRegion));
        doMotionEvent(MotionEvent.ACTION_CANCEL, 0);
        mShellExecutor.flushAll();

        verify(mAppCallback, never()).onBackStarted(any());
        verify(mAppCallback, never()).onBackProgressed(any());
        verify(mAppCallback, never()).onBackInvoked();
        verify(mAppCallback, never()).onBackCancelled();
    }

    @Test
    public void testBackToActivity() throws RemoteException {
        verifySystemBackBehavior(BackNavigationInfo.TYPE_CROSS_ACTIVITY,
                mDefaultCrossActivityBackAnimation.getRunner());
    }

    @Test
    public void testBackToTask() throws RemoteException {
        verifySystemBackBehavior(BackNavigationInfo.TYPE_CROSS_TASK,
                mCrossTaskBackAnimation.getRunner());
    }

    @Test
    public void testCloseAsExpectTransition() {
        final int openTaskId = 1;
        final int closeTaskId = 2;
        mController.mApps = createAppAnimationTargets(openTaskId, closeTaskId);
        final IBinder mockBinder = mock(IBinder.class);
        final SurfaceControl.Transaction st = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction ft = mock(SurfaceControl.Transaction.class);
        // Single close
        final TransitionInfo.Change open = createAppChange(openTaskId, TRANSIT_OPEN,
                FLAG_BACK_GESTURE_ANIMATED | FLAG_MOVED_TO_TOP);
        final TransitionInfo.Change close = createAppChange(closeTaskId, TRANSIT_CLOSE,
                FLAG_BACK_GESTURE_ANIMATED);

        TransitionInfo tInfo = createTransitionInfo(TRANSIT_CLOSE, open, close);
        mBackTransitionHandler.mCloseTransitionRequested = true;
        Transitions.TransitionFinishCallback callback =
                mock(Transitions.TransitionFinishCallback.class);
        mBackTransitionHandler.startAnimation(mockBinder, tInfo, st, ft, callback);
        verify(mBackTransitionHandler).handleCloseTransition(
                eq(tInfo), eq(st), eq(ft), eq(callback));
        mBackTransitionHandler.onAnimationFinished();
        verify(callback).onTransitionFinished(any());
        mBackTransitionHandler.mCloseTransitionRequested = false;

        // PREPARE + CLOSE
        tInfo = createTransitionInfo(TRANSIT_PREPARE_BACK_NAVIGATION, open);
        callback = mock(Transitions.TransitionFinishCallback.class);
        mBackTransitionHandler.startAnimation(mockBinder, tInfo, st, ft, callback);
        verify(mBackTransitionHandler).handlePrepareTransition(eq(mockBinder),
                eq(tInfo), eq(st), eq(ft), eq(callback));
        mBackTransitionHandler.mCloseTransitionRequested = true;
        TransitionInfo tInfo2 = createTransitionInfo(TRANSIT_CLOSE, close);
        Transitions.TransitionFinishCallback mergeCallback =
                mock(Transitions.TransitionFinishCallback.class);
        mBackTransitionHandler.mergeAnimation(
                mock(IBinder.class), tInfo2, st, ft, mock(IBinder.class), mergeCallback);
        mBackTransitionHandler.onAnimationFinished();
        verify(callback).onTransitionFinished(any());
        verify(mergeCallback).onTransitionFinished(any());
        mBackTransitionHandler.mCloseTransitionRequested = false;

        // PREPARE contains close info
        tInfo = createTransitionInfo(TRANSIT_PREPARE_BACK_NAVIGATION, open, close);
        callback = mock(Transitions.TransitionFinishCallback.class);
        mBackTransitionHandler.mCloseTransitionRequested = true;
        mBackTransitionHandler.startAnimation(mockBinder, tInfo, st, ft, callback);
        verify(mBackTransitionHandler).handleCloseTransition(
                eq(tInfo), eq(st), eq(ft), eq(callback));
        mBackTransitionHandler.onAnimationFinished();
        verify(callback).onTransitionFinished(any());
        mBackTransitionHandler.mCloseTransitionRequested = false;

        // PREPARE then Cancel
        tInfo = createTransitionInfo(TRANSIT_PREPARE_BACK_NAVIGATION, open);
        callback = mock(Transitions.TransitionFinishCallback.class);
        final TransitionRequestInfo requestInfo = new TransitionRequestInfo(
                TRANSIT_PREPARE_BACK_NAVIGATION, null /* triggerTask */,
                null /* remoteTransition */);
        mBackTransitionHandler.handleRequest(mockBinder, requestInfo);
        mBackTransitionHandler.startAnimation(mockBinder, tInfo, st, ft, callback);
        verify(mBackTransitionHandler).handlePrepareTransition(eq(mockBinder),
                eq(tInfo), eq(st), eq(ft), eq(callback));

        mBackTransitionHandler.onAnimationFinished();
        final TransitionInfo.Change openToClose = createAppChangeFromChange(open, TRANSIT_CLOSE,
                FLAG_BACK_GESTURE_ANIMATED);
        tInfo2 = createTransitionInfo(TRANSIT_CLOSE_PREPARE_BACK_NAVIGATION, openToClose);
        mBackTransitionHandler.mClosePrepareTransition = mock(IBinder.class);
        mergeCallback = mock(Transitions.TransitionFinishCallback.class);
        mBackTransitionHandler.mergeAnimation(mBackTransitionHandler.mClosePrepareTransition,
                tInfo2, st, ft, mock(IBinder.class), mergeCallback);
        assertTrue("Change should be consumed", tInfo2.getChanges().isEmpty());
        verify(callback).onTransitionFinished(any());
    }

    @Test
    public void testCancelUnexpectedTransition() {
        final int openTaskId = 1;
        final int closeTaskId = 2;
        mController.mApps = createAppAnimationTargets(openTaskId, closeTaskId);
        final IBinder mockBinder = mock(IBinder.class);
        final SurfaceControl.Transaction st = mock(SurfaceControl.Transaction.class);
        final SurfaceControl.Transaction ft = mock(SurfaceControl.Transaction.class);
        final TransitionInfo.Change open = createAppChange(openTaskId, TRANSIT_OPEN,
                FLAG_BACK_GESTURE_ANIMATED | FLAG_MOVED_TO_TOP);
        final TransitionInfo.Change close = createAppChange(closeTaskId, TRANSIT_CLOSE,
                FLAG_BACK_GESTURE_ANIMATED);

        // Didn't trigger close transition
        mBackTransitionHandler.mCloseTransitionRequested = false;
        TransitionInfo prepareInfo = createTransitionInfo(TRANSIT_PREPARE_BACK_NAVIGATION,
                open, close);
        final Transitions.TransitionFinishCallback callback =
                mock(Transitions.TransitionFinishCallback.class);
        mBackTransitionHandler.handleRequest(mockBinder, mock(TransitionRequestInfo.class));
        boolean canHandle = mBackTransitionHandler.startAnimation(
                mockBinder, prepareInfo, st, ft, callback);
        assertFalse("Should not handle transition", canHandle);
        assertNull(mBackTransitionHandler.mOnAnimationFinishCallback);

        // Didn't trigger close transition, but receive close target.
        final TransitionRequestInfo requestInfo = new TransitionRequestInfo(
                TRANSIT_PREPARE_BACK_NAVIGATION, null /* triggerTask */,
                null /* remoteTransition */);
        prepareInfo = createTransitionInfo(TRANSIT_PREPARE_BACK_NAVIGATION, open);
        final Transitions.TransitionFinishCallback callback2 =
                mock(Transitions.TransitionFinishCallback.class);
        mBackTransitionHandler.handleRequest(mockBinder, requestInfo);
        canHandle = mBackTransitionHandler.startAnimation(mockBinder,
                prepareInfo, st, ft, callback2);
        assertTrue("Handle prepare transition" , canHandle);
        verify(mBackTransitionHandler).handlePrepareTransition(eq(mockBinder),
                eq(prepareInfo), eq(st), eq(ft), eq(callback2));
        final TransitionInfo closeInfo = createTransitionInfo(TRANSIT_CLOSE, close);
        Transitions.TransitionFinishCallback mergeCallback =
                mock(Transitions.TransitionFinishCallback.class);
        mBackTransitionHandler.mergeAnimation(mock(IBinder.class), closeInfo, st, ft,
                mock(IBinder.class), mergeCallback);
        verify(callback2).onTransitionFinished(any());
        verify(mergeCallback, never()).onTransitionFinished(any());

        // Didn't trigger close transition, but contains open target.
        final int openTaskId2 = 3;
        final Transitions.TransitionFinishCallback callback3 =
                mock(Transitions.TransitionFinishCallback.class);
        mBackTransitionHandler.handleRequest(mockBinder, requestInfo);
        canHandle = mBackTransitionHandler.startAnimation(
                mockBinder, prepareInfo, st, ft, callback3);
        assertTrue("Handle prepare transition" , canHandle);
        verify(mBackTransitionHandler).handlePrepareTransition(eq(mockBinder),
                eq(prepareInfo), eq(st), eq(ft), eq(callback3));
        final TransitionInfo.Change open2 = createAppChange(
                openTaskId2, TRANSIT_OPEN, FLAG_MOVED_TO_TOP);
        final TransitionInfo openInfo = createTransitionInfo(TRANSIT_OPEN, open2, close);
        mergeCallback = mock(Transitions.TransitionFinishCallback.class);
        mBackTransitionHandler.mergeAnimation(mock(IBinder.class), openInfo, st, ft,
                mock(IBinder.class), mergeCallback);
        verify(callback3).onTransitionFinished(any());
        verify(mergeCallback, never()).onTransitionFinished(any());
    }

    @Test
    public void testBackAnimationControllersRecoversFromBadState() throws RemoteException {
        // put controller into bad state (initial state but mBackGestureStarted=true)
        mController.mBackGestureStarted = true;
        verifySystemBackBehavior(BackNavigationInfo.TYPE_CROSS_ACTIVITY,
                mDefaultCrossActivityBackAnimation.getRunner());
    }

    private RemoteAnimationTarget[] createAppAnimationTargets(int openTaskId, int closeTaskId) {
        final RemoteAnimationTarget openT = createSingleAnimationTarget(openTaskId,
                RemoteAnimationTarget.MODE_OPENING);
        final RemoteAnimationTarget closeT = createSingleAnimationTarget(closeTaskId,
                RemoteAnimationTarget.MODE_CLOSING);
        return new RemoteAnimationTarget[]{openT, closeT};
    }

    private RemoteAnimationTarget createSingleAnimationTarget(int taskId, int mode) {
        final Rect fakeR = new Rect();
        final Point fakeP = new Point();
        final ActivityManager.RunningTaskInfo openTaskInfo = new ActivityManager.RunningTaskInfo();
        openTaskInfo.taskId = taskId;
        openTaskInfo.token = new WindowContainerToken(mock(IWindowContainerToken.class));
        return new RemoteAnimationTarget(
                taskId, mode, mock(SurfaceControl.class), false, fakeR, fakeR,
                0, fakeP, fakeR, fakeR, new WindowConfiguration(), false,
                mock(SurfaceControl.class), fakeR, openTaskInfo, false);
    }
    private TransitionInfo.Change createAppChange(
            int taskId, @TransitionInfo.TransitionMode int mode,
            @TransitionInfo.ChangeFlags int flags) {
        final TransitionInfo.Change change;
        SurfaceControl.Builder b = new SurfaceControl.Builder()
                .setName("test task");
        if (taskId != INVALID_TASK_ID) {
            final ActivityManager.RunningTaskInfo taskInfo = new ActivityManager.RunningTaskInfo();
            taskInfo.taskId = taskId;
            final IWindowContainerToken mockT = mock(IWindowContainerToken.class);
            Binder binder = new Binder();
            doReturn(binder).when(mockT).asBinder();
            taskInfo.token = new WindowContainerToken(mockT);
            change = new TransitionInfo.Change(
                    taskInfo.token, b.build());
            change.setTaskInfo(taskInfo);
        } else {
            change = new TransitionInfo.Change(
                null, b.build());

        }
        change.setMode(mode);
        change.setFlags(flags);
        return change;
    }

    private TransitionInfo.Change createAppChangeFromChange(
            TransitionInfo.Change originalChange, @TransitionInfo.TransitionMode int mode,
            @TransitionInfo.ChangeFlags int flags) {
        final TransitionInfo.Change change = new TransitionInfo.Change(
                originalChange.getTaskInfo().token, originalChange.getLeash());
        change.setMode(mode);
        change.setFlags(flags);
        return change;
    }

    private static TransitionInfo createTransitionInfo(
            @WindowManager.TransitionType int type, TransitionInfo.Change ... changes) {
        final TransitionInfo info = new TransitionInfo(type, 0);
        for (int i = 0; i < changes.length; ++i) {
            info.addChange(changes[i]);
        }
        return info;
    }

    private void verifySystemBackBehavior(int type, BackAnimationRunner animation)
            throws RemoteException {
        final BackAnimationRunner animationRunner = spy(animation);
        final IRemoteAnimationRunner runner = spy(animationRunner.getRunner());
        final IOnBackInvokedCallback callback = spy(animationRunner.getCallback());

        // Set up the monitoring objects.
        doNothing().when(runner).onAnimationStart(anyInt(), any(), any(), any(), any());
        doReturn(false).when(animationRunner).shouldMonitorCUJ(any());
        doReturn(runner).when(animationRunner).getRunner();
        doReturn(callback).when(animationRunner).getCallback();

        mController.registerAnimation(type, animationRunner);

        createNavigationInfo(type,
                /* enableAnimation = */ true,
                /* isAnimationCallback = */ false);

        // Check that back start and progress is dispatched when first move.
        doStartEvents(0, 100);

        simulateRemoteAnimationStart();

        verify(callback).onBackStarted(any(BackMotionEvent.class));
        verify(animationRunner).startAnimation(any(), any(), any(), any());

        // Check that back invocation is dispatched.
        mController.setTriggerBack(true);   // Fake trigger back
        doMotionEvent(MotionEvent.ACTION_UP, 0);
        verify(callback).onBackInvoked();
    }

    private void doMotionEvent(int actionDown, int coordinate) {
        mController.onMotionEvent(
                /* touchX */ coordinate,
                /* touchY */ coordinate,
                /* keyAction */ actionDown,
                /* swipeEdge */ BackEvent.EDGE_LEFT,
                /* displayId */ DEFAULT_DISPLAY);
    }

    /**
     * Simulate event sequence that starts a back navigation.
     */
    private void doStartEvents(int startX, int moveX) {
        doMotionEvent(MotionEvent.ACTION_DOWN, startX);
        doMotionEvent(MotionEvent.ACTION_MOVE, moveX);
        mController.onThresholdCrossed();
    }

    private void simulateRemoteAnimationStart() throws RemoteException {
        RemoteAnimationTarget animationTarget = createAnimationTarget();
        RemoteAnimationTarget[] targets = new RemoteAnimationTarget[]{animationTarget};
        if (mController.mBackAnimationAdapter != null) {
            mController.mBackAnimationAdapter.getRunner().onAnimationStart(
                    targets, null /* prepareOpenTransition */, mBackAnimationFinishedCallback);
            mShellExecutor.flushAll();
        }
    }

    private void simulateRemoteAnimationFinished() {
        mController.onBackAnimationFinished();
        mController.finishBackNavigation(/*triggerBack*/ true);
    }

    private void registerAnimation(int type) {
        mController.registerAnimation(
                type,
                new BackAnimationRunner(
                        mAnimatorCallback,
                        mBackAnimationRunner,
                        mContext,
                        mHandler));
    }

    private void unregisterAnimation(int type) {
        mController.unregisterAnimation(type);
    }

    private static class ResultListener implements RemoteCallback.OnResultListener {
        boolean mBackNavigationDone = false;
        boolean mTriggerBack = false;

        @Override
        public void onResult(@Nullable Bundle result) {
            mBackNavigationDone = true;
            mTriggerBack = result.getBoolean(KEY_NAVIGATION_FINISHED);
        }
    }
}
