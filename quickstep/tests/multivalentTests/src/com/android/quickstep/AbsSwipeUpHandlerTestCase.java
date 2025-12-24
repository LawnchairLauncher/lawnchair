/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.quickstep;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.launcher3.BaseActivity.EVENT_DESTROYED;
import static com.android.launcher3.statehandlers.DesktopVisibilityController.INACTIVE_DESK_ID;
import static com.android.quickstep.AbsSwipeUpHandler.STATE_GESTURE_CANCELLED;
import static com.android.quickstep.AbsSwipeUpHandler.STATE_HANDLER_INVALIDATED;
import static com.android.quickstep.AbsSwipeUpHandler.STATE_LAUNCHER_PRESENT;
import static com.android.wm.shell.shared.ShellSharedConstants.KEY_EXTRA_SHELL_CAN_HAND_OFF_ANIMATION;
import static com.android.wm.shell.shared.split.SplitBounds.KEY_EXTRA_SPLIT_BOUNDS;
import static com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_50_50;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.app.displaylib.fakes.FakePerDisplayRepository;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.LauncherRootView;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StatefulContainer;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.MSDLPlayerWrapper;
import com.android.launcher3.util.SandboxApplication;
import com.android.launcher3.util.SystemUiController;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.ContextInitListener;
import com.android.quickstep.util.MotionPauseDetector;
import com.android.quickstep.util.RecentsOrientedState;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.wm.shell.shared.split.SplitBounds;

import com.google.android.msdl.data.model.MSDLToken;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public abstract class AbsSwipeUpHandlerTestCase<
        STATE_TYPE extends BaseState<STATE_TYPE>,
        RECENTS_CONTAINER extends Context & RecentsViewContainer & StatefulContainer<STATE_TYPE>,
        RECENTS_VIEW extends RecentsView<RECENTS_CONTAINER, STATE_TYPE>,
        SWIPE_HANDLER extends AbsSwipeUpHandler<RECENTS_CONTAINER, RECENTS_VIEW, STATE_TYPE>,
        CONTAINER_INTERFACE extends BaseContainerInterface<STATE_TYPE, RECENTS_CONTAINER>> {

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public final SandboxApplication mContext = new SandboxApplication();

    protected int mDisplayId = DEFAULT_DISPLAY;

    protected final InputConsumerController mInputConsumerController =
            InputConsumerController.getRecentsAnimationInputConsumer();
    protected final ActivityManager.RunningTaskInfo mRunningTaskInfo =
            new ActivityManager.RunningTaskInfo();

    protected final RemoteAnimationTarget mRemoteAnimationTarget = new RemoteAnimationTarget(
            /* taskId= */ 0,
            /* mode= */ RemoteAnimationTarget.MODE_CLOSING,
            /* leash= */ new SurfaceControl(),
            /* isTranslucent= */ false,
            /* clipRect= */ null,
            /* contentInsets= */ null,
            /* prefixOrderIndex= */ 0,
            /* position= */ null,
            /* localBounds= */ null,
            /* screenSpaceBounds= */ null,
            new Configuration().windowConfiguration,
            /* isNotInRecents= */ false,
            /* startLeash= */ null,
            /* startBounds= */ null,
            /* taskInfo= */ mRunningTaskInfo,
            /* allowEnterPip= */ false);

    protected final RemoteAnimationTarget mRemoteAnimationLeftTop = new RemoteAnimationTarget(
            /* taskId= */ 1,
            /* mode= */ RemoteAnimationTarget.MODE_CLOSING,
            /* leash= */ new SurfaceControl(),
            /* isTranslucent= */ false,
            /* clipRect= */ null,
            /* contentInsets= */ null,
            /* prefixOrderIndex= */ 0,
            /* position= */ null,
            /* localBounds= */ null,
            /* screenSpaceBounds= */ null,
            new Configuration().windowConfiguration,
            /* isNotInRecents= */ false,
            /* startLeash= */ null,
            /* startBounds= */ null,
            /* taskInfo= */ mRunningTaskInfo,
            /* allowEnterPip= */ false);

    protected final RemoteAnimationTarget mRemoteAnimationRightBottom = new RemoteAnimationTarget(
            /* taskId= */ 2,
            /* mode= */ RemoteAnimationTarget.MODE_CLOSING,
            /* leash= */ new SurfaceControl(),
            /* isTranslucent= */ false,
            /* clipRect= */ null,
            /* contentInsets= */ null,
            /* prefixOrderIndex= */ 0,
            /* position= */ null,
            /* localBounds= */ null,
            /* screenSpaceBounds= */ null,
            new Configuration().windowConfiguration,
            /* isNotInRecents= */ false,
            /* startLeash= */ null,
            /* startBounds= */ null,
            /* taskInfo= */ mRunningTaskInfo,
            /* allowEnterPip= */ false);

    protected RecentsAnimationTargets mRecentsAnimationTargets;
    protected TaskAnimationManager mTaskAnimationManager;
    protected StateManager<STATE_TYPE, RECENTS_CONTAINER> mStateManager;
    protected int[] mCurrentPageTaskIds = new int[] { 0 };
    protected int[] mNextPageTaskIds = new int[] { 1 };

    @Mock protected CONTAINER_INTERFACE mActivityInterface;
    @Mock protected ContextInitListener<?> mContextInitListener;
    @Mock protected RecentsAnimationController mRecentsAnimationController;
    @Mock protected STATE_TYPE mState;
    @Mock protected ViewTreeObserver mViewTreeObserver;
    @Mock protected DragLayer mDragLayer;
    @Mock protected LauncherRootView mRootView;
    @Mock protected SystemUiController mSystemUiController;
    @Mock protected GestureState mGestureState;
    @Mock protected MSDLPlayerWrapper mMSDLPlayerWrapper;
    @Mock protected RecentsAnimationDeviceState mDeviceState;
    @Mock protected RotationTouchHelper mRotationTouchHelper;
    @Mock protected StateManager.AtomicAnimationFactory<STATE_TYPE> mAtomicAnimationFactory;
    @Mock protected TaskView mCurrentPageTaskView;
    @Mock protected TaskView mNextPageTaskView;
    @Mock protected BaseContainerInterface.AnimationFactory mAnimationFactory;

    @Before
    public void setUpAnimationTargets() {
        Bundle extras = new Bundle();
        extras.putBoolean(KEY_EXTRA_SHELL_CAN_HAND_OFF_ANIMATION, true);
        extras.putParcelable(KEY_EXTRA_SPLIT_BOUNDS, new SplitBounds(
                /* leftTopBounds = */ new Rect(),
                /* rightBottomBounds = */ new Rect(),
                /* leftTopTaskId = */ mRemoteAnimationLeftTop.taskId,
                /* rightBottomTaskId = */ mRemoteAnimationRightBottom.taskId,
                /* snapPosition = */ SNAP_TO_2_50_50));
        mRecentsAnimationTargets = new RecentsAnimationTargets(
                new RemoteAnimationTarget[] {mRemoteAnimationLeftTop},
                new RemoteAnimationTarget[] {mRemoteAnimationRightBottom},
                new RemoteAnimationTarget[] {mRemoteAnimationTarget},
                /* homeContentInsets= */ new Rect(),
                extras);
    }

    @Before
    public void setUpRunningTaskInfo() {
        mRunningTaskInfo.baseIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mRunningTaskInfo.baseIntent.setComponent(new ComponentName("test.package", "test.class"));
    }

    @Before
    public void setUpGestureState() {
        when(mGestureState.getRunningTask()).thenReturn(getTaskInfo());
        when(mGestureState.getLastAppearedTaskIds()).thenReturn(new int[0]);
        when(mGestureState.getLastStartedTaskIds()).thenReturn(mNextPageTaskIds);
        when(mGestureState.getRunningTaskIds(anyBoolean())).thenReturn(mCurrentPageTaskIds);
        when(mGestureState.getHomeIntent()).thenReturn(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        doReturn(mActivityInterface).when(mGestureState).getContainerInterface();
        when(mGestureState.getDisplayId()).thenAnswer(invocation -> mDisplayId);
        when(mGestureState.getPreviouslyAppearedTaskIds()).thenReturn(Set.of(0));
        when(mGestureState.getLastStartedTaskIdPredicate()).thenReturn(
                appearedTarget -> appearedTarget.taskId == mRemoteAnimationTarget.taskId);
    }

    @Before
    public void setupTaskViews() {
        when(mCurrentPageTaskView.getTaskIds()).thenReturn(mCurrentPageTaskIds);
        when(mNextPageTaskView.getTaskIds()).thenReturn(mNextPageTaskIds);
    }

    @Before
    public void setUpRecentsView() {
        RECENTS_VIEW recentsView = getRecentsView();
        when(recentsView.getViewTreeObserver()).thenReturn(mViewTreeObserver);
        doAnswer(answer -> {
            runOnMainSync(() -> answer.<Runnable>getArgument(0).run());
            return this;
        }).when(recentsView).runOnPageScrollsInitialized(any());
        when(recentsView.getCurrentPage()).thenReturn(0);
        when(recentsView.getNextPage()).thenReturn(1);
        when(recentsView.getRunningTaskView()).thenReturn(mCurrentPageTaskView);
        when(recentsView.getCurrentPageTaskView()).thenReturn(mCurrentPageTaskView);
        when(recentsView.getNextPageTaskView()).thenReturn(mNextPageTaskView);
        when(recentsView.getTaskViewAt(anyInt())).thenAnswer(
                invocation -> switch ((int) invocation.getArgument(0)) {
                    case 0 -> mCurrentPageTaskView;
                    case 1 -> mNextPageTaskView;
                    default -> null;
                });
    }

    @Before
    public void setUpRecentsContainer() {
        DisplayController displayController = DisplayController.INSTANCE.get(mContext);
        FakePerDisplayRepository<TaskAnimationManager> fakePerDisplayRepository =
                new FakePerDisplayRepository<>();
        TaskAnimationManager taskAnimationManager = new TaskAnimationManager(mContext, mDisplayId,
                displayController, fakePerDisplayRepository);
        fakePerDisplayRepository.add(mDisplayId, taskAnimationManager);
        mTaskAnimationManager = spy(taskAnimationManager);
        RECENTS_CONTAINER recentsContainer = getRecentsContainer();
        RECENTS_VIEW recentsView = getRecentsView();

        when(recentsContainer.getDeviceProfile()).thenReturn(new DeviceProfile());
        when(recentsContainer.getOverviewPanel()).thenReturn(recentsView);
        when(recentsContainer.getDragLayer()).thenReturn(mDragLayer);
        when(recentsContainer.getRootView()).thenReturn(mRootView);
        when(recentsContainer.getSystemUiController()).thenReturn(mSystemUiController);
        when(recentsContainer.createAtomicAnimationFactory()).thenReturn(mAtomicAnimationFactory);
        when(mActivityInterface.createActivityInitListener(any()))
                .thenReturn(mContextInitListener);
        when(mActivityInterface.prepareRecentsUI(anyBoolean(), any()))
                .thenReturn(mAnimationFactory);
        doReturn(recentsContainer).when(mActivityInterface).getCreatedContainer();
        doAnswer(answer -> {
            answer.<Runnable>getArgument(0).run();
            return this;
        }).when(recentsContainer).runOnBindToTouchInteractionService(any());

        mStateManager = spy(new StateManager<>(recentsContainer, getBaseState()));

        doReturn(mStateManager).when(recentsContainer).getStateManager();
    }

    @Test
    public void testInitWhenReady_registersActivityInitListener() {
        String reasonString = "because i said so";

        createSwipeHandler().initWhenReady(reasonString);
        verify(mContextInitListener).register(eq(reasonString));
    }

    @Test
    public void testOnRecentsAnimationCanceled_unregistersActivityInitListener() {
        SWIPE_HANDLER swipeHandler = createSwipeHandler();

        swipeHandler.onRecentsAnimationCanceled(new HashMap<>());

        runOnMainSync(() -> {
            verify(mContextInitListener)
                    .unregister(eq("AbsSwipeUpHandler.onRecentsAnimationCanceled"));
            assertTrue(swipeHandler.mStateCallback.hasStates(
                    STATE_GESTURE_CANCELLED | STATE_HANDLER_INVALIDATED));
        });
    }

    @Test
    public void testOnConsumerAboutToBeSwitched_unregistersActivityInitListener() {
        createSwipeHandler().onConsumerAboutToBeSwitched();

        runOnMainSync(() -> verify(mContextInitListener)
                .unregister("AbsSwipeUpHandler.invalidateHandler"));
    }

    @Test
    public void testOnConsumerAboutToBeSwitched_midQuickSwitch_unregistersActivityInitListener() {
        createSwipeUpHandlerForGesture(GestureState.GestureEndTarget.NEW_TASK)
                .onConsumerAboutToBeSwitched();

        runOnMainSync(() -> verify(mContextInitListener)
                .unregister(eq("AbsSwipeUpHandler.cancelCurrentAnimation")));
    }

    @Test
    public void testStartNewTask_withNullTask_finishesRecentsAnimationController() {
        SWIPE_HANDLER swipeHandler = createSwipeHandler();

        onRecentsAnimationStart(swipeHandler);

        runOnMainSync(() -> {
            swipeHandler.startNewTask(null, unused -> {});
            verifyRecentsAnimationFinishedAndCallCallback();
        });
    }

    @Test
    public void testOnTasksAppeared_withUnexpectedTaskAppeared_launchesCorrectTask() {
        SWIPE_HANDLER swipeHandler = createSwipeHandler();
        RemoteAnimationTarget unexpectedRemoteAnimationTarget = new RemoteAnimationTarget(
                /* taskId= */ 2, // unexpected task id
                /* mode= */ RemoteAnimationTarget.MODE_CLOSING,
                /* leash= */ new SurfaceControl(),
                /* isTranslucent= */ false,
                /* clipRect= */ null,
                /* contentInsets= */ null,
                /* prefixOrderIndex= */ 0,
                /* position= */ null,
                /* localBounds= */ null,
                /* screenSpaceBounds= */ null,
                new Configuration().windowConfiguration,
                /* isNotInRecents= */ false,
                /* startLeash= */ null,
                /* startBounds= */ null,
                /* taskInfo= */ mRunningTaskInfo,
                /* allowEnterPip= */ false);

        when(mGestureState.getEndTarget()).thenReturn(GestureState.GestureEndTarget.NEW_TASK);

        swipeHandler.onActivityInit(/* alreadyOnHome= */ false);
        onRecentsAnimationStart(swipeHandler);
        onTasksAppeared(
                swipeHandler, new RemoteAnimationTarget[] { unexpectedRemoteAnimationTarget });

        runOnMainSync(() -> {
            verifyRecentsAnimationFinishedAndCallCallback();
            verify(mCurrentPageTaskView, never()).launchWithoutAnimation(anyBoolean(), any());
            verify(mNextPageTaskView).launchWithoutAnimation(anyBoolean(), any());
        });
    }

    @Test
    public void testOnRecentsAnimationCanceled_midQuickSwitch_launchesCorrectTask() {
        SWIPE_HANDLER swipeHandler = createSwipeHandler();

        when(mGestureState.getEndTarget()).thenReturn(GestureState.GestureEndTarget.NEW_TASK);

        swipeHandler.onActivityInit(/* alreadyOnHome= */ false);
        onRecentsAnimationStart(swipeHandler);
        swipeHandler.onRecentsAnimationCanceled(new HashMap<>());

        runOnMainSync(() -> {
            verify(mCurrentPageTaskView, never()).launchWithoutAnimation(anyBoolean(), any());
            verify(mNextPageTaskView).launchWithoutAnimation(anyBoolean(), any());
        });
    }

    @Test
    public void testHomeGesture_finishesRecentsAnimationController() {
        createSwipeUpHandlerForGesture(GestureState.GestureEndTarget.HOME);

        runOnMainSync(() -> {
            verify(mRecentsAnimationController).detachNavigationBarFromApp(true);
            verifyRecentsAnimationFinishedAndCallCallback();
        });
    }

    @Test
    public void testRejectHomeGesture_finishesCorrectly() {
        createSwipeUpHandlerForGesture(GestureState.GestureEndTarget.REJECT_HOME);

        runOnMainSync(() -> {
            verify(mRecentsAnimationController, never()).detachNavigationBarFromApp(anyBoolean());
            verify(mRecentsAnimationController, never()).handOffAnimation(any(), any());
            verifyRecentsAnimationFinishedAndCallCallback();
            assertEquals("Scroll offset should be 0 after animation to REJECT_HOME is done",
                    0, getRecentsView().getScrollOffset());
        });
    }

    @Test
    @EnableFlags({com.android.window.flags.Flags.FLAG_ENABLE_REJECT_HOME_TRANSITION})
    public void getHomeTarget_onSecondaryDisplay_withFlagEnabled() {
        mDisplayId = DEFAULT_DISPLAY + 1; // Simulate a secondary display
        SWIPE_HANDLER handler = createSwipeHandler();

        GestureState.GestureEndTarget target = handler.getHomeTarget();
        assertEquals("Expected REJECT_HOME on secondary display when reject home is enabled",
                GestureState.GestureEndTarget.REJECT_HOME, target);
    }

    @Test
    @EnableFlags({com.android.window.flags.Flags.FLAG_ENABLE_REJECT_HOME_TRANSITION})
    public void getHomeTarget_onDefaultDisplay_withFlagEnabled() {
        mDisplayId = DEFAULT_DISPLAY;
        SWIPE_HANDLER handler = createSwipeHandler();

        GestureState.GestureEndTarget target = handler.getHomeTarget();
        assertEquals("Expected HOME on default display",
                GestureState.GestureEndTarget.HOME, target);
    }

    @Test
    @DisableFlags({com.android.window.flags.Flags.FLAG_ENABLE_REJECT_HOME_TRANSITION})
    public void getHomeTarget_onSecondaryDisplay_withFlagDisabled() {
        mDisplayId = DEFAULT_DISPLAY + 1; // Simulate a secondary display
        SWIPE_HANDLER handler = createSwipeHandler();

        GestureState.GestureEndTarget target = handler.getHomeTarget();
        assertEquals("Expected HOME on secondary display when reject home is disabled",
                GestureState.GestureEndTarget.HOME, target);
    }

    @Test
    public void testHomeGesture_handsOffAnimation() {
        createSwipeUpHandlerForGesture(GestureState.GestureEndTarget.HOME);

        runOnMainSync(() -> {
            verify(mRecentsAnimationController).handOffAnimation(any(), any());
            verifyRecentsAnimationFinishedAndCallCallback();
        });
    }

    @Test
    public void testHomeGesture_invalidatesHandlerAfterParallelAnim() {
        ValueAnimator parallelAnim = new ValueAnimator();
        parallelAnim.setRepeatCount(ValueAnimator.INFINITE);
        when(mActivityInterface.getParallelAnimationToGestureEndTarget(any(), anyLong(), any()))
                .thenReturn(parallelAnim);
        SWIPE_HANDLER handler = createSwipeUpHandlerForGesture(GestureState.GestureEndTarget.HOME);
        runOnMainSync(() -> {
            parallelAnim.start();
            verifyRecentsAnimationFinishedAndCallCallback();
            assertFalse(handler.mStateCallback.hasStates(STATE_HANDLER_INVALIDATED));
            parallelAnim.end();
            assertTrue(handler.mStateCallback.hasStates(STATE_HANDLER_INVALIDATED));
        });
    }

    @Test
    public void testHomeGesture_invalidatesHandlerIfNoParallelAnim() {
        when(mActivityInterface.getParallelAnimationToGestureEndTarget(any(), anyLong(), any()))
                .thenReturn(null);
        SWIPE_HANDLER handler = createSwipeUpHandlerForGesture(GestureState.GestureEndTarget.HOME);
        runOnMainSync(() -> {
            verifyRecentsAnimationFinishedAndCallCallback();
            assertTrue(handler.mStateCallback.hasStates(STATE_HANDLER_INVALIDATED));
        });
    }

    @Test
    public void invalidateHandlerWithLauncher_runsGestureAnimationEndCallback() {
        SWIPE_HANDLER handler = createSwipeHandler();
        Runnable onGestureAnimationEndCallback = mock(Runnable.class);
        handler.setGestureAnimationEndCallback(onGestureAnimationEndCallback);

        handler.mStateCallback.setState(STATE_HANDLER_INVALIDATED | STATE_LAUNCHER_PRESENT);

        verify(onGestureAnimationEndCallback).run();
    }

    @Test
    @EnableFlags(com.android.launcher3.Flags.FLAG_MSDL_FEEDBACK)
    public void onMotionPauseDetected_playsSwipeThresholdToken() {
        SWIPE_HANDLER handler = createSwipeHandler();
        MotionPauseDetector.OnMotionPauseListener listener = handler.getMotionPauseListener();
        listener.onMotionPauseDetected();

        verify(mMSDLPlayerWrapper, times(1)).playToken(eq(MSDLToken.SWIPE_THRESHOLD_INDICATOR));
        verifyNoMoreInteractions(mMSDLPlayerWrapper);
    }

    @Test
    public void testOnContainerDestroy_cleansUpSwipeHandler() {
        SWIPE_HANDLER swipeHandler = createSwipeHandler();

        swipeHandler.onActivityInit(true);

        RECENTS_CONTAINER container = getRecentsContainer();
        ArgumentCaptor<Runnable> onContainerDestroyCallbackCaptor =
                ArgumentCaptor.forClass(Runnable.class);

        verify(container)
                .addEventCallback(eq(EVENT_DESTROYED), onContainerDestroyCallbackCaptor.capture());

        assertNotNull(swipeHandler.mRecentsView);
        assertNotNull(swipeHandler.mContainer);

        onContainerDestroyCallbackCaptor.getValue().run();

        assertNull(swipeHandler.mRecentsView);
        assertNull(swipeHandler.mContainer);
        verify(mTaskAnimationManager).onLauncherDestroyed();
        runOnMainSync(() -> {
            verify(mContextInitListener).unregister(any());
            assertTrue(
                    "Swipe handler wasn't invalidated on container destroyed",
                    swipeHandler.mStateCallback.hasStates(STATE_HANDLER_INVALIDATED));
        });
    }

    @Test
    public void test_noActivityInit_doesNotThrowException() {
        // Do not trigger onActivityInit to ensure AbsSwipeUpHandler.mRecentsView and
        // AbsSwipeUpHandler.mContainer are null
        createSwipeUpHandlerForGesture(
                GestureState.GestureEndTarget.HOME, /* triggerOnActivityInit= */ false);
    }

    @Test
    public void testWindowAnimationToHome_setsAndResetsTaskViewClickableState_whenSuccessful() {
        SWIPE_HANDLER swipeHandler = createSwipeHandler();
        ArgumentCaptor<Runnable> callbackCaptor = ArgumentCaptor.forClass(Runnable.class);

        // Call onActivityInit to set RecentsView
        swipeHandler.onActivityInit(/* isHomeStarted= */ false);

        AnimationSuccessListener listener = swipeHandler.getWindowAnimationToHomeListener();

        runOnMainSync(() -> {
            listener.onAnimationStart(new AnimatorSet());

            verify(mCurrentPageTaskView, times(1)).setClickable(eq(false));
            verify(mCurrentPageTaskView, never()).setClickable(eq(true));

            listener.onAnimationEnd(new AnimatorSet());

            verify(getRecentsView()).post(callbackCaptor.capture());

            callbackCaptor.getValue().run();

            verify(mCurrentPageTaskView, times(1)).setClickable(eq(false));
            verify(mCurrentPageTaskView, times(1)).setClickable(eq(true));
        });
    }

    @Test
    public void testWindowAnimationToHome_afterContainerDestroyed_doesNotThrowException() {
        SWIPE_HANDLER swipeHandler = createSwipeHandler();
        ArgumentCaptor<Runnable> callbackCaptor = ArgumentCaptor.forClass(Runnable.class);

        // Call onActivityInit to set RecentsView
        swipeHandler.onActivityInit(/* isHomeStarted= */ false);

        AnimationSuccessListener listener = swipeHandler.getWindowAnimationToHomeListener();

        runOnMainSync(() -> {
            listener.onAnimationStart(new AnimatorSet());
            listener.onAnimationEnd(new AnimatorSet());

            verify(getRecentsView()).post(callbackCaptor.capture());

            // onContainerDestroyed to set RecentsView to null
            onContainerDestroyed();

            callbackCaptor.getValue().run();
        });
    }

    @Test
    public void testWindowAnimationToHome_setsAndResetsTaskViewClickableState_whenCanceled() {
        SWIPE_HANDLER swipeHandler = createSwipeHandler();
        ArgumentCaptor<Runnable> callbackCaptor = ArgumentCaptor.forClass(Runnable.class);

        // Call onActivityInit to set RecentsView
        swipeHandler.onActivityInit(/* isHomeStarted= */ false);

        AnimationSuccessListener listener = swipeHandler.getWindowAnimationToHomeListener();

        runOnMainSync(() -> {
            listener.onAnimationStart(new AnimatorSet());

            verify(mCurrentPageTaskView, times(1)).setClickable(eq(false));
            verify(mCurrentPageTaskView, never()).setClickable(eq(true));

            listener.onAnimationCancel(new AnimatorSet());
            listener.onAnimationEnd(new AnimatorSet());

            verify(getRecentsView()).post(callbackCaptor.capture());

            callbackCaptor.getValue().run();

            verify(mCurrentPageTaskView, times(1)).setClickable(eq(false));
            verify(mCurrentPageTaskView, times(1)).setClickable(eq(true));
        });
    }

    @Test
    public void testWindowAnimationToHome_neverSetsTaskViewClickableState_ifNeverStarted() {
        SWIPE_HANDLER swipeHandler = createSwipeHandler();
        ArgumentCaptor<Runnable> callbackCaptor = ArgumentCaptor.forClass(Runnable.class);

        // Call onActivityInit to set RecentsView
        swipeHandler.onActivityInit(/* isHomeStarted= */ false);

        AnimationSuccessListener listener = swipeHandler.getWindowAnimationToHomeListener();

        runOnMainSync(() -> {
            listener.onAnimationEnd(new AnimatorSet());

            verify(getRecentsView()).post(callbackCaptor.capture());

            callbackCaptor.getValue().run();

            verify(mCurrentPageTaskView, never()).setClickable(eq(false));
            verify(mCurrentPageTaskView, never()).setClickable(eq(true));
        });
    }

    @Test
    public void test3ButtonMode_shouldUpdateBackgroundAlphaForRunningTask_UpdatesBackgroundAlpha() {
        SWIPE_HANDLER swipeHandler = createSwipeHandler();
        when(mDeviceState.isFullyGesturalNavMode()).thenReturn(false);
        when(mGestureState.isTrackpadGesture()).thenReturn(false);
        when(getRecentsView().getPagedViewOrientedState()).thenReturn(
                new RecentsOrientedState(mContext, swipeHandler.mContainerInterface, (r) -> {
                }));
        when(getRecentsView().shouldUpdateRunningTaskAlpha()).thenReturn(true);
        swipeHandler.onActivityInit(/*isHomeStarted= */ true);
        swipeHandler.onGestureStarted(/*isLikelyToStartNewTask =*/ true);

        onRecentsAnimationStart(swipeHandler);

        verify(mAnimationFactory).setRecentsAttachedToAppWindow(
                /* attached= */ true,
                /* isTrackpadGesture= */ false,
                /* updateBackgroundAlpha= */ true);
        verify(getRecentsView(), never()).moveRunningTaskToExpectedPosition();
    }

    @Test
    public void test3ButtonMode_runningTask_doesNotUpdateBackgroundAlpha() {
        SWIPE_HANDLER swipeHandler = createSwipeHandler();
        when(mDeviceState.isFullyGesturalNavMode()).thenReturn(false);
        when(mGestureState.isTrackpadGesture()).thenReturn(false);
        when(getRecentsView().getPagedViewOrientedState()).thenReturn(
                new RecentsOrientedState(mContext, swipeHandler.mContainerInterface, (r) -> {
                }));
        when(getRecentsView().shouldUpdateRunningTaskAlpha()).thenReturn(false);
        swipeHandler.onActivityInit(/*isHomeStarted= */ true);
        swipeHandler.onGestureStarted(/*isLikelyToStartNewTask =*/ true);

        onRecentsAnimationStart(swipeHandler);

        verify(mAnimationFactory, never()).setRecentsAttachedToAppWindow(
                /* attached= */ true,
                /* isTrackpadGesture= */ false,
                /* updateBackgroundAlpha= */ true);
        verify(getRecentsView(), never()).moveRunningTaskToExpectedPosition();
    }

    /**
     * Verifies that RecentsAnimationController#finish() is called, and captures and runs any
     * callback that was passed to it. This ensures that STATE_CURRENT_TASK_FINISHED is correctly
     * set for example.
     */
    private void verifyRecentsAnimationFinishedAndCallCallback() {
        ArgumentCaptor<Runnable> finishCallback = ArgumentCaptor.forClass(Runnable.class);
        // Check if the 2 parameter method is called.
        verify(mRecentsAnimationController, atLeast(0)).finish(
                anyBoolean(), finishCallback.capture(), any(ActiveGestureLog.CompoundString.class));
        if (finishCallback.getAllValues().isEmpty()) {
            // Check if the 3 parameter method is called.
            verify(mRecentsAnimationController).finish(
                    anyBoolean(),
                    finishCallback.capture(),
                    anyBoolean(),
                    any(ActiveGestureLog.CompoundString.class));
        }
        if (finishCallback.getValue() != null) {
            finishCallback.getValue().run();
        }
    }

    private SWIPE_HANDLER createSwipeUpHandlerForGesture(GestureState.GestureEndTarget endTarget) {
        return createSwipeUpHandlerForGesture(endTarget, true);
    }

    private SWIPE_HANDLER createSwipeUpHandlerForGesture(
            GestureState.GestureEndTarget endTarget, boolean triggerOnActivityInit) {
        boolean isQuickSwitch = endTarget == GestureState.GestureEndTarget.NEW_TASK;

        doReturn(mState).when(mActivityInterface).stateFromGestureEndTarget(any());
        when(mGestureState.getEndTarget()).thenReturn(endTarget);
        when(mGestureState.isRecentsAnimationRunning()).thenReturn(isQuickSwitch);

        SWIPE_HANDLER swipeHandler = createSwipeHandler(SystemClock.uptimeMillis(), isQuickSwitch);

        if (triggerOnActivityInit) {
            swipeHandler.onActivityInit(/* alreadyOnHome= */ false);
        }
        swipeHandler.onGestureStarted(isQuickSwitch);
        onRecentsAnimationStart(swipeHandler);

        runOnMainSync(swipeHandler::switchToScreenshot);

        float xVelocityPxPerMs = isQuickSwitch ? 100 : 0;
        float yVelocityPxPerMs = isQuickSwitch ? 0 : -100;
        swipeHandler.onGestureEnded(
                yVelocityPxPerMs, new PointF(xVelocityPxPerMs, yVelocityPxPerMs), isQuickSwitch);
        swipeHandler.onCalculateEndTarget();
        runOnMainSync(swipeHandler::onSettledOnEndTarget);

        return swipeHandler;
    }

    private void onRecentsAnimationStart(@NonNull SWIPE_HANDLER absSwipeUpHandler) {
        runOnMainSync(() -> absSwipeUpHandler.onRecentsAnimationStart(
                mRecentsAnimationController, mRecentsAnimationTargets, /* transitionInfo= */ null));
    }

    private void onTasksAppeared(
            @NonNull SWIPE_HANDLER absSwipeUpHandler,
            @NonNull RemoteAnimationTarget[] remoteAnimationTargets) {
        runOnMainSync(() -> absSwipeUpHandler.onTasksAppeared(
                remoteAnimationTargets, /* transitionInfo= */ null));
    }

    private void onContainerDestroyed() {
        RECENTS_CONTAINER container = getRecentsContainer();
        ArgumentCaptor<Runnable> onContainerDestroyCallbackCaptor =
                ArgumentCaptor.forClass(Runnable.class);

        verify(container)
                .addEventCallback(eq(EVENT_DESTROYED), onContainerDestroyCallbackCaptor.capture());

        onContainerDestroyCallbackCaptor.getValue().run();
    }

    protected static void runOnMainSync(Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
        try {
            Executors.MAIN_EXECUTOR.submit(() -> null).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @NonNull
    private SWIPE_HANDLER createSwipeHandler() {
        return createSwipeHandler(SystemClock.uptimeMillis(), false);
    }

    @NonNull
    protected abstract SWIPE_HANDLER createSwipeHandler(
            long touchTimeMs, boolean continuingLastGesture);

    @NonNull
    protected abstract RECENTS_CONTAINER getRecentsContainer();

    @NonNull
    protected abstract RECENTS_VIEW getRecentsView();

    @NonNull
    protected abstract STATE_TYPE getBaseState();

    protected TopTaskTracker.CachedTaskInfo getTaskInfo() {
        return new TopTaskTracker.CachedTaskInfo(
                Collections.singletonList(mRunningTaskInfo),
                mContext,
                mDisplayId,
                INACTIVE_DESK_ID);
    }
}
