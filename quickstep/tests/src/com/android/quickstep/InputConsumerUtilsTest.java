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

import static com.android.quickstep.InputConsumerUtils.newBaseConsumer;
import static com.android.quickstep.InputConsumerUtils.newConsumer;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.StubberKt.doCallRealMethod;

import android.annotation.NonNull;
import android.os.Looper;
import android.view.Choreographer;
import android.view.Display;
import android.view.MotionEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.dagger.LauncherAppComponent;
import com.android.launcher3.dagger.LauncherAppModule;
import com.android.launcher3.dagger.LauncherAppSingleton;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarManager;
import com.android.launcher3.taskbar.bubbles.BubbleBarController;
import com.android.launcher3.taskbar.bubbles.BubbleBarPinController;
import com.android.launcher3.taskbar.bubbles.BubbleBarSwipeController;
import com.android.launcher3.taskbar.bubbles.BubbleBarViewController;
import com.android.launcher3.taskbar.bubbles.BubbleControllers;
import com.android.launcher3.taskbar.bubbles.BubbleCreator;
import com.android.launcher3.taskbar.bubbles.BubbleDismissController;
import com.android.launcher3.taskbar.bubbles.BubbleDragController;
import com.android.launcher3.taskbar.bubbles.BubblePinController;
import com.android.launcher3.taskbar.bubbles.BubbleStashedHandleViewController;
import com.android.launcher3.taskbar.bubbles.stashing.BubbleStashController;
import com.android.launcher3.util.LockedUserState;
import com.android.launcher3.util.SandboxApplication;
import com.android.launcher3.views.BaseDragLayer;
import com.android.quickstep.inputconsumers.AccessibilityInputConsumer;
import com.android.quickstep.inputconsumers.BubbleBarInputConsumer;
import com.android.quickstep.inputconsumers.DeviceLockedInputConsumer;
import com.android.quickstep.inputconsumers.NavHandleLongPressInputConsumer;
import com.android.quickstep.inputconsumers.OneHandedModeInputConsumer;
import com.android.quickstep.inputconsumers.OtherActivityInputConsumer;
import com.android.quickstep.inputconsumers.OverviewInputConsumer;
import com.android.quickstep.inputconsumers.OverviewWithoutFocusInputConsumer;
import com.android.quickstep.inputconsumers.ProgressDelegateInputConsumer;
import com.android.quickstep.inputconsumers.ResetGestureInputConsumer;
import com.android.quickstep.inputconsumers.ScreenPinnedInputConsumer;
import com.android.quickstep.inputconsumers.SysUiOverlayInputConsumer;
import com.android.quickstep.inputconsumers.TrackpadStatusBarInputConsumer;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.NavBarPosition;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.systemui.shared.system.InputChannelCompat;
import com.android.systemui.shared.system.InputMonitorCompat;

import dagger.BindsInstance;
import dagger.Component;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Optional;
import java.util.function.Function;

import javax.inject.Provider;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InputConsumerUtilsTest {

    @Rule public final SandboxApplication mContext = new SandboxApplication();

    private final int mDisplayId = Display.DEFAULT_DISPLAY;
    @NonNull private final InputMonitorCompat mInputMonitorCompat =
            new InputMonitorCompat("", mDisplayId);

    private TaskAnimationManager mTaskAnimationManager;
    private InputChannelCompat.InputEventReceiver mInputEventReceiver;
    private boolean mUserUnlocked = true;
    @NonNull private Function<GestureState, AnimatedFloat> mSwipeUpProxyProvider = (state) -> null;

    @NonNull @Mock private TaskbarActivityContext mTaskbarActivityContext;
    @NonNull @Mock private OverviewComponentObserver mOverviewComponentObserver;
    @NonNull @Mock private RecentsAnimationDeviceState mDeviceState;
    @NonNull @Mock private AbsSwipeUpHandler.Factory mSwipeUpHandlerFactory;
    @NonNull @Mock private TaskbarManager mTaskbarManager;
    @NonNull @Mock private OverviewCommandHelper mOverviewCommandHelper;
    @NonNull @Mock private GestureState mPreviousGestureState;
    @NonNull @Mock private GestureState mCurrentGestureState;
    @NonNull @Mock private LockedUserState mLockedUserState;
    @NonNull @Mock private TopTaskTracker.CachedTaskInfo mRunningTask;
    @NonNull @Mock private BaseContainerInterface<?, ?> mContainerInterface;
    @NonNull @Mock private BaseDragLayer<?> mBaseDragLayer;

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Before
    public void setupTaskAnimationManager() {
        mTaskAnimationManager = new TaskAnimationManager(mContext, mDeviceState, mDisplayId);
    }

    @Before
    public void setupDaggerGraphOverrides() {
        mContext.initDaggerComponent(DaggerInputConsumerUtilsTest_TestComponent
                .builder()
                .bindLockedState(mLockedUserState)
                .bindRotationHelper(mock(RotationTouchHelper.class))
                .bindRecentsState(mDeviceState));
    }

    @Before
    public void setUpInputEventReceiver() {
        runOnMainSync(() ->
                mInputEventReceiver = mInputMonitorCompat.getInputReceiver(
                        Looper.getMainLooper(),
                        Choreographer.getInstance(),
                        event -> {}));
    }

    @Before
    public void setUpTaskbarActivityContext() {
        NavHandle navHandle = mock(NavHandle.class);

        when(navHandle.canNavHandleBeLongPressed()).thenReturn(true);

        when(mTaskbarActivityContext.getDeviceProfile()).thenReturn(new DeviceProfile());
        when(mTaskbarActivityContext.getNavHandle()).thenReturn(navHandle);
    }

    @Before
    public void setUpTaskbarManager() {
        when(mTaskbarManager.getCurrentActivityContext()).thenReturn(mTaskbarActivityContext);
    }

    @Before
    public void setupLockedUserState() {
        when(mLockedUserState.isUserUnlocked()).thenReturn(true);
    }

    @Before
    public void setupGestureStates() {
        when(mCurrentGestureState.getRunningTask()).thenReturn(mRunningTask);
        doReturn(mContainerInterface).when(mCurrentGestureState).getContainerInterface();
    }

    @Before
    public void setUpContainerInterface() {
        RecentsViewContainer recentsViewContainer = mock(RecentsViewContainer.class);

        when(recentsViewContainer.getDragLayer()).thenReturn(mBaseDragLayer);
        when(recentsViewContainer.getRootView()).thenReturn(mBaseDragLayer);
        when(recentsViewContainer.asContext()).thenReturn(mContext);

        doReturn(recentsViewContainer).when(mContainerInterface).getCreatedContainer();
    }

    @Before
    public void setupBaseDragLayer() {
        when(mBaseDragLayer.hasWindowFocus()).thenReturn(true);
    }

    @Before
    public void setupDeviceState() {
        when(mDeviceState.canStartTrackpadGesture()).thenReturn(true);
        when(mDeviceState.canStartSystemGesture()).thenReturn(true);
        when(mDeviceState.isFullyGesturalNavMode()).thenReturn(true);
        when(mDeviceState.getNavBarPosition()).thenReturn(mock(NavBarPosition.class));
    }

    @After
    public void cleanUp() {
        mInputMonitorCompat.dispose();
        mInputEventReceiver.dispose();
    }

    @Test
    public void testNewBaseConsumer_onKeyguard_returnsDeviceLockedInputConsumer() {
        when(mDeviceState.isKeyguardShowingOccluded()).thenReturn(true);

        assertCorrectInputConsumer(
                this::createBaseInputConsumer,
                DeviceLockedInputConsumer.class,
                InputConsumer.TYPE_DEVICE_LOCKED);
    }

    @Test
    public void testNewBaseConsumer_onLiveTileModeWithNoContainer_returnsDefaultInputConsumer() {
        when(mContainerInterface.isInLiveTileMode()).thenReturn(true);
        when(mContainerInterface.getCreatedContainer()).thenReturn(null);

        assertEqualsDefaultInputConsumer(this::createBaseInputConsumer);
    }

    @Test
    public void testNewBaseConsumer_onLiveTileMode_returnsOverviewInputConsumer() {
        when(mContainerInterface.isInLiveTileMode()).thenReturn(true);

        assertCorrectInputConsumer(
                this::createBaseInputConsumer,
                OverviewInputConsumer.class,
                InputConsumer.TYPE_OVERVIEW);
    }

    @Test
    public void testNewBaseConsumer_withNoRunningTask_returnsDefaultInputConsumer() {
        when(mCurrentGestureState.getRunningTask()).thenReturn(null);

        assertEqualsDefaultInputConsumer(this::createBaseInputConsumer);
    }

    @Test
    public void testNewBaseConsumer_prevGestureAnimatingToLauncher_returnsOverviewInputConsumer() {
        when(mPreviousGestureState.isRunningAnimationToLauncher()).thenReturn(true);

        assertCorrectInputConsumer(
                this::createBaseInputConsumer,
                OverviewInputConsumer.class,
                InputConsumer.TYPE_OVERVIEW);
    }

    @Test
    public void testNewBaseConsumer_predictiveBackToHomeInProgress_returnsOverviewInputConsumer() {
        when(mDeviceState.isPredictiveBackToHomeInProgress()).thenReturn(true);

        assertCorrectInputConsumer(
                this::createBaseInputConsumer,
                OverviewInputConsumer.class,
                InputConsumer.TYPE_OVERVIEW);
    }

    @Test
    public void testNewBaseConsumer_resumedThroughShellTransition_returnsOverviewInputConsumer() {
        when(mContainerInterface.isResumed()).thenReturn(true);

        assertCorrectInputConsumer(
                this::createBaseInputConsumer,
                OverviewInputConsumer.class,
                InputConsumer.TYPE_OVERVIEW);
    }

    @Test
    public void testNewBaseConsumer_shellNoWindowFocus_returnsOverviewWithoutFocusInputConsumer() {
        when(mContainerInterface.isResumed()).thenReturn(true);
        when(mBaseDragLayer.hasWindowFocus()).thenReturn(false);

        assertCorrectInputConsumer(
                this::createBaseInputConsumer,
                OverviewWithoutFocusInputConsumer.class,
                InputConsumer.TYPE_OVERVIEW_WITHOUT_FOCUS);
    }

    @Test
    public void testNewBaseConsumer_forceOverviewInputConsumer_returnsOverviewInputConsumer() {
        when(mContainerInterface.isResumed()).thenReturn(true);
        when(mRunningTask.isRootChooseActivity()).thenReturn(true);

        assertCorrectInputConsumer(
                this::createBaseInputConsumer,
                OverviewInputConsumer.class,
                InputConsumer.TYPE_OVERVIEW);
    }

    @Test
    public void testNewBaseConsumer_launcherChildActivityResumed_returnsDefaultInputConsumer() {
        when(mRunningTask.isHomeTask()).thenReturn(true);
        when(mOverviewComponentObserver.isHomeAndOverviewSameActivity()).thenReturn(true);

        assertEqualsDefaultInputConsumer(this::createBaseInputConsumer);
    }

    @Test
    public void testNewBaseConsumer_onGestureBlockedTask_returnsDefaultInputConsumer() {
        when(mDeviceState.isGestureBlockedTask(any())).thenReturn(true);

        assertEqualsDefaultInputConsumer(this::createBaseInputConsumer);
    }

    @Test
    public void testNewBaseConsumer_noGestureBlockedTask_returnsOtherActivityInputConsumer() {
        doCallRealMethod().when(mDeviceState).setGestureBlockingTaskId(anyInt());
        mDeviceState.setGestureBlockingTaskId(-1);
        when(mDeviceState.isGestureBlockedTask(any())).thenCallRealMethod();

        assertCorrectInputConsumer(this::createBaseInputConsumer, OtherActivityInputConsumer.class,
                InputConsumer.TYPE_OTHER_ACTIVITY);
    }

    @Test
    public void testNewBaseConsumer_containsOtherActivityInputConsumer() {
        assertCorrectInputConsumer(
                this::createBaseInputConsumer,
                OtherActivityInputConsumer.class,
                InputConsumer.TYPE_OTHER_ACTIVITY);
    }

    @Test
    public void testNewConsumer_containsOtherActivityInputConsumer() {
        assertCorrectInputConsumer(
                this::createInputConsumer,
                NavHandleLongPressInputConsumer.class,
                OtherActivityInputConsumer.class,
                InputConsumer.TYPE_OTHER_ACTIVITY | InputConsumer.TYPE_NAV_HANDLE_LONG_PRESS);
    }

    @Test
    public void testNewConsumer_eventCanTriggerAssistantAction_containsAssistantInputConsumer() {
        when(mDeviceState.canTriggerAssistantAction(any())).thenReturn(true);

        assertCorrectInputConsumer(
                this::createInputConsumer,
                NavHandleLongPressInputConsumer.class,
                OtherActivityInputConsumer.class,
                InputConsumer.TYPE_OTHER_ACTIVITY
                        | InputConsumer.TYPE_NAV_HANDLE_LONG_PRESS
                        | InputConsumer.TYPE_ASSISTANT);
    }

    @Test
    public void testNewConsumer_taskbarIsPresent_containsTaskbarUnstashInputConsumer() {
        DeviceProfile deviceProfile = new DeviceProfile();
        deviceProfile.isTaskbarPresent = true;
        when(mTaskbarActivityContext.getDeviceProfile()).thenReturn(deviceProfile);

        assertCorrectInputConsumer(
                this::createInputConsumer,
                NavHandleLongPressInputConsumer.class,
                OtherActivityInputConsumer.class,
                InputConsumer.TYPE_OTHER_ACTIVITY
                        | InputConsumer.TYPE_TASKBAR_STASH
                        | InputConsumer.TYPE_NAV_HANDLE_LONG_PRESS
                        | InputConsumer.TYPE_CURSOR_HOVER);
    }

    @Test
    public void testNewConsumer_whileSystemUiDialogShowing_returnsSysUiOverlayInputConsumer() {
        when(mDeviceState.isSystemUiDialogShowing()).thenReturn(true);

        assertCorrectInputConsumer(
                this::createInputConsumer,
                SysUiOverlayInputConsumer.class,
                InputConsumer.TYPE_SYSUI_OVERLAY);
    }

    @Test
    public void testNewConsumer_onTrackpadGesture_returnsTrackpadStatusBarInputConsumer() {
        when(mCurrentGestureState.isTrackpadGesture()).thenReturn(true);

        assertCorrectInputConsumer(
                this::createInputConsumer,
                TrackpadStatusBarInputConsumer.class,
                OtherActivityInputConsumer.class,
                InputConsumer.TYPE_OTHER_ACTIVITY
                        | InputConsumer.TYPE_NAV_HANDLE_LONG_PRESS
                        | InputConsumer.TYPE_STATUS_BAR);
    }

    @Test
    public void testNewConsumer_whileScreenPinningActive_returnsScreenPinnedInputConsumer() {
        when(mDeviceState.isScreenPinningActive()).thenReturn(true);

        assertCorrectInputConsumer(
                this::createInputConsumer,
                ScreenPinnedInputConsumer.class,
                InputConsumer.TYPE_SCREEN_PINNED);
    }

    @Test
    public void testNewConsumer_canTriggerOneHandedAction_returnsOneHandedModeInputConsumer() {
        when(mDeviceState.canTriggerOneHandedAction(any())).thenReturn(true);

        assertCorrectInputConsumer(
                this::createInputConsumer,
                OneHandedModeInputConsumer.class,
                OtherActivityInputConsumer.class,
                InputConsumer.TYPE_OTHER_ACTIVITY
                        | InputConsumer.TYPE_NAV_HANDLE_LONG_PRESS
                        | InputConsumer.TYPE_ONE_HANDED);
    }

    @Test
    public void testNewConsumer_accessibilityMenuAvailable_returnsAccessibilityInputConsumer() {
        when(mDeviceState.isAccessibilityMenuAvailable()).thenReturn(true);

        assertCorrectInputConsumer(
                this::createInputConsumer,
                AccessibilityInputConsumer.class,
                OtherActivityInputConsumer.class,
                InputConsumer.TYPE_OTHER_ACTIVITY
                        | InputConsumer.TYPE_NAV_HANDLE_LONG_PRESS
                        | InputConsumer.TYPE_ACCESSIBILITY);
    }

    @Test
    public void testNewConsumer_onStashedBubbleBar_returnsBubbleBarInputConsumer() {
        BubbleControllers bubbleControllers = createBubbleControllers(/* isStashed= */ true);

        when(mTaskbarActivityContext.isBubbleBarEnabled()).thenReturn(true);
        when(mTaskbarActivityContext.getBubbleControllers()).thenReturn(bubbleControllers);

        assertCorrectInputConsumer(
                this::createInputConsumer,
                BubbleBarInputConsumer.class,
                InputConsumer.TYPE_BUBBLE_BAR);
    }

    @Test
    public void testNewConsumer_onVisibleBubbleBar_returnsBubbleBarInputConsumer() {
        BubbleControllers bubbleControllers = createBubbleControllers(/* isStashed= */ false);

        when(mTaskbarActivityContext.isBubbleBarEnabled()).thenReturn(true);
        when(mTaskbarActivityContext.getBubbleControllers()).thenReturn(bubbleControllers);

        assertCorrectInputConsumer(
                this::createInputConsumer,
                BubbleBarInputConsumer.class,
                InputConsumer.TYPE_BUBBLE_BAR);
    }

    @Test
    public void testNewConsumer_withSwipeUpProxyProvider_returnsProgressDelegateInputConsumer() {
        mSwipeUpProxyProvider = (state) -> new AnimatedFloat();

        assertCorrectInputConsumer(
                this::createInputConsumer,
                ProgressDelegateInputConsumer.class,
                InputConsumer.TYPE_PROGRESS_DELEGATE);
    }

    @Test
    public void testNewConsumer_onLockedState_returnsDeviceLockedInputConsumer() {
        when(mLockedUserState.isUserUnlocked()).thenReturn(false);

        assertCorrectInputConsumer(
                this::createInputConsumer,
                DeviceLockedInputConsumer.class,
                InputConsumer.TYPE_DEVICE_LOCKED);
    }

    @Test
    public void testNewConsumer_cannotStartSysGestureOnLockedState_returnsDefaultInputConsumer() {
        when(mLockedUserState.isUserUnlocked()).thenReturn(false);
        when(mDeviceState.canStartSystemGesture()).thenReturn(false);

        assertEqualsDefaultInputConsumer(this::createInputConsumer);
    }

    @Test
    public void testNewConsumer_cannotStartTrackGestureOnLockedState_returnsDefaultInputConsumer() {
        when(mLockedUserState.isUserUnlocked()).thenReturn(false);
        when(mCurrentGestureState.isTrackpadGesture()).thenReturn(true);
        when(mDeviceState.canStartTrackpadGesture()).thenReturn(false);

        assertEqualsDefaultInputConsumer(this::createInputConsumer);
    }

    private InputConsumer createInputConsumer() {
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        InputConsumer inputConsumer = newConsumer(
                mContext,
                mUserUnlocked,
                mOverviewComponentObserver,
                mDeviceState,
                mPreviousGestureState,
                mCurrentGestureState,
                mTaskAnimationManager,
                mInputMonitorCompat,
                mSwipeUpHandlerFactory,
                otherActivityInputConsumer -> {},
                mInputEventReceiver,
                mTaskbarManager,
                mSwipeUpProxyProvider,
                mOverviewCommandHelper,
                event);

        event.recycle();

        return inputConsumer;
    }

    private InputConsumer createBaseInputConsumer() {
        MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        InputConsumer inputConsumer = newBaseConsumer(
                mContext,
                mUserUnlocked,
                mTaskbarManager,
                mOverviewComponentObserver,
                mDeviceState,
                mPreviousGestureState,
                mCurrentGestureState,
                mTaskAnimationManager,
                mInputMonitorCompat,
                mSwipeUpHandlerFactory,
                otherActivityInputConsumer -> {},
                mInputEventReceiver,
                event,
                ActiveGestureLog.CompoundString.NO_OP);

        event.recycle();

        return inputConsumer;
    }

    private void assertEqualsDefaultInputConsumer(
            @NonNull Provider<InputConsumer> inputConsumerProvider) {
        assertCorrectInputConsumer(
                inputConsumerProvider,
                ResetGestureInputConsumer.class,
                InputConsumer.TYPE_RESET_GESTURE);

        mUserUnlocked = false;

        assertCorrectInputConsumer(
                inputConsumerProvider,
                InputConsumer.class,
                InputConsumer.TYPE_NO_OP);
    }

    private void assertCorrectInputConsumer(
            @NonNull Provider<InputConsumer> inputConsumerProvider,
            @NonNull Class<? extends InputConsumer> expectedOutputConsumer,
            int expectedType) {
        assertCorrectInputConsumer(
                inputConsumerProvider,
                expectedOutputConsumer,
                expectedOutputConsumer,
                expectedType);
    }

    private void assertCorrectInputConsumer(
            @NonNull Provider<InputConsumer> inputConsumerProvider,
            @NonNull Class<? extends InputConsumer> expectedOutputConsumer,
            @NonNull Class<? extends InputConsumer> expectedActiveConsumer,
            int expectedType) {
        when(mCurrentGestureState.getDisplayId()).thenReturn(mDisplayId);

        runOnMainSync(() -> {
            InputConsumer inputConsumer = inputConsumerProvider.get();

            assertThat(inputConsumer).isInstanceOf(expectedOutputConsumer);
            assertThat(inputConsumer.getActiveConsumerInHierarchy())
                    .isInstanceOf(expectedActiveConsumer);
            assertThat(inputConsumer.getType()).isEqualTo(expectedType);
            assertThat(inputConsumer.getDisplayId()).isEqualTo(mDisplayId);
        });
        int expectedDisplayId = mDisplayId + 1;

        when(mCurrentGestureState.getDisplayId()).thenReturn(expectedDisplayId);

        runOnMainSync(() -> assertThat(inputConsumerProvider.get().getDisplayId())
                .isEqualTo(expectedDisplayId));
    }

    private static void runOnMainSync(@NonNull Runnable runnable) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
    }

    private static BubbleControllers createBubbleControllers(boolean isStashed) {
        BubbleBarController bubbleBarController = mock(BubbleBarController.class);
        BubbleBarViewController bubbleBarViewController = mock(BubbleBarViewController.class);
        BubbleStashController bubbleStashController = mock(BubbleStashController.class);
        BubbleStashedHandleViewController bubbleStashedHandleViewController =
                mock(BubbleStashedHandleViewController.class);
        BubbleDragController bubbleDragController = mock(BubbleDragController.class);
        BubbleDismissController bubbleDismissController = mock(BubbleDismissController.class);
        BubbleBarPinController bubbleBarPinController = mock(BubbleBarPinController.class);
        BubblePinController bubblePinController = mock(BubblePinController.class);
        BubbleBarSwipeController bubbleBarSwipeController = mock(BubbleBarSwipeController.class);
        BubbleCreator bubbleCreator = mock(BubbleCreator.class);
        BubbleControllers bubbleControllers = new BubbleControllers(
                bubbleBarController,
                bubbleBarViewController,
                bubbleStashController,
                Optional.of(bubbleStashedHandleViewController),
                bubbleDragController,
                bubbleDismissController,
                bubbleBarPinController,
                bubblePinController,
                Optional.of(bubbleBarSwipeController),
                bubbleCreator);

        when(bubbleBarViewController.hasBubbles()).thenReturn(true);
        when(bubbleStashController.isStashed()).thenReturn(isStashed);
        when(bubbleStashedHandleViewController.isEventOverHandle(any())).thenReturn(true);
        when(bubbleBarViewController.isBubbleBarVisible()).thenReturn(!isStashed);
        when(bubbleBarViewController.isEventOverBubbleBar(any())).thenReturn(true);

        return bubbleControllers;
    }

    @LauncherAppSingleton
    @Component(modules = {LauncherAppModule.class})
    interface TestComponent extends LauncherAppComponent {
        @Component.Builder
        interface Builder extends LauncherAppComponent.Builder {
            @BindsInstance Builder bindLockedState(LockedUserState state);
            @BindsInstance Builder bindRotationHelper(RotationTouchHelper helper);
            @BindsInstance Builder bindRecentsState(RecentsAnimationDeviceState state);

            @Override
            TestComponent build();
        }
    }
}
