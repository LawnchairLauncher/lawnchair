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
package com.android.quickstep;

import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.LauncherPrefs.backedUpItem;
import static com.android.launcher3.MotionEventsUtils.isTrackpadMotionEvent;
import static com.android.launcher3.MotionEventsUtils.isTrackpadMultiFingerSwipe;
import static com.android.launcher3.taskbar.TaskbarDesktopExperienceFlags.enableAltTabKqsOnConnectedDisplays;
import static com.android.launcher3.util.DisplayController.CHANGE_NAVIGATION_MODE;
import static com.android.launcher3.util.DisplayController.CHANGE_NIGHT_MODE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.OnboardingPrefs.HOME_BOUNCE_SEEN;
import static com.android.launcher3.util.window.WindowManagerProxy.MIN_TABLET_WIDTH;
import static com.android.quickstep.GestureState.DEFAULT_STATE;
import static com.android.quickstep.GestureState.TrackpadGestureType.getTrackpadGestureType;
import static com.android.quickstep.InputConsumer.TYPE_CURSOR_HOVER;
import static com.android.quickstep.InputConsumer.createNoOpInputConsumer;
import static com.android.quickstep.InputConsumerUtils.newConsumer;
import static com.android.quickstep.InputConsumerUtils.tryCreateAssistantInputConsumer;
import static com.android.quickstep.fallback.window.RecentsWindowFlags.enableOverviewOnConnectedDisplays;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Region;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.view.Choreographer;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.window.DesktopExperienceFlags.DesktopExperienceFlag;

import androidx.annotation.BinderThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import com.android.app.displaylib.DisplayRepository;
import com.android.app.displaylib.DisplaysWithDecorationsRepositoryCompat;
import com.android.app.displaylib.PerDisplayRepository;
import com.android.launcher3.ConstantItem;
import com.android.launcher3.EncryptionType;
import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.dagger.LauncherComponentProvider;
import com.android.launcher3.desktop.DesktopAppLaunchTransitionManager;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarManager;
import com.android.launcher3.taskbar.TaskbarManagerImpl;
import com.android.launcher3.taskbar.TaskbarManagerImplWrapper;
import com.android.launcher3.taskbar.TaskbarNavButtonController.TaskbarNavButtonCallbacks;
import com.android.launcher3.taskbar.bubbles.BubbleControllers;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.ResourceUtils;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.LockedUserState;
import com.android.launcher3.util.MSDLPlayerWrapper;
import com.android.launcher3.util.NavigationMode;
import com.android.launcher3.util.PluginManagerWrapper;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.ScreenOnTracker;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.coroutines.ProductionDispatchers;
import com.android.quickstep.OverviewCommandHelper.CommandType;
import com.android.quickstep.OverviewComponentObserver.OverviewChangeListener;
import com.android.quickstep.actioncorner.ActionCornerHandler;
import com.android.quickstep.fallback.RecentsState;
import com.android.quickstep.fallback.window.RecentsWindowFlags;
import com.android.quickstep.fallback.window.RecentsWindowManager;
import com.android.quickstep.fallback.window.RecentsWindowSwipeHandler;
import com.android.quickstep.input.QuickstepKeyGestureEventsManager;
import com.android.quickstep.input.QuickstepKeyGestureEventsManager.OverviewGestureHandler;
import com.android.quickstep.inputconsumers.BubbleBarInputConsumer;
import com.android.quickstep.inputconsumers.OneHandedModeInputConsumer;
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.ActiveGestureLog.CompoundString;
import com.android.quickstep.util.ActiveGestureProtoLogProxy;
import com.android.quickstep.util.ActiveTrackpadList;
import com.android.quickstep.util.ActivityPreloadUtil;
import com.android.quickstep.util.ContextualSearchInvoker;
import com.android.quickstep.util.ContextualSearchStateManager;
import com.android.quickstep.views.RecentsViewContainer;
import com.android.systemui.shared.recents.ILauncherProxy;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.statusbar.phone.BarTransitions;
import com.android.systemui.shared.system.InputChannelCompat.InputEventReceiver;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.InputMonitorCompat;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;
import com.android.systemui.shared.system.smartspace.ISysuiUnlockAnimationController;
import com.android.systemui.unfold.progress.IUnfoldAnimation;
import com.android.wm.shell.back.IBackAnimation;
import com.android.wm.shell.bubbles.IBubbles;
import com.android.wm.shell.common.pip.IPip;
import com.android.wm.shell.desktopmode.IDesktopMode;
import com.android.wm.shell.draganddrop.IDragAndDrop;
import com.android.wm.shell.onehanded.IOneHanded;
import com.android.wm.shell.recents.IRecentTasks;
import com.android.wm.shell.shared.IShellTransitions;
import com.android.wm.shell.splitscreen.ISplitScreen;
import com.android.wm.shell.startingsurface.IStartingWindow;

import kotlinx.coroutines.CoroutineDispatcher;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Service connected by system-UI for handling touch interaction.
 */
public class TouchInteractionService extends Service {

    private static final String SUBSTRING_PREFIX = "; ";

    private static final String TAG = "TouchInteractionService";

    private static final ConstantItem<Boolean> HAS_ENABLED_QUICKSTEP_ONCE = backedUpItem(
            "launcher.has_enabled_quickstep_once", false, EncryptionType.ENCRYPTED);

    private static final DesktopExperienceFlag ENABLE_GESTURE_NAV_ON_CONNECTED_DISPLAYS =
            new DesktopExperienceFlag(Flags::enableGestureNavOnConnectedDisplays, true,
                Flags.FLAG_ENABLE_GESTURE_NAV_ON_CONNECTED_DISPLAYS);

    private final TISBinder mTISBinder = new TISBinder(this);

    /**
     * Local ILauncherProxy implementation with some methods for local components
     */
    public static class TISBinder extends ILauncherProxy.Stub {

        private final WeakReference<TouchInteractionService> mTis;

        private TISBinder(TouchInteractionService tis) {
            mTis = new WeakReference<>(tis);
        }

        @BinderThread
        public void onInitialize(Bundle bundle) {
            ISystemUiProxy proxy = ISystemUiProxy.Stub.asInterface(
                    bundle.getBinder(ISystemUiProxy.DESCRIPTOR));
            IPip pip = IPip.Stub.asInterface(bundle.getBinder(IPip.DESCRIPTOR));
            IBubbles bubbles = IBubbles.Stub.asInterface(bundle.getBinder(IBubbles.DESCRIPTOR));
            ISplitScreen splitscreen = ISplitScreen.Stub.asInterface(bundle.getBinder(
                    ISplitScreen.DESCRIPTOR));
            IOneHanded onehanded = IOneHanded.Stub.asInterface(
                    bundle.getBinder(IOneHanded.DESCRIPTOR));
            IShellTransitions shellTransitions = IShellTransitions.Stub.asInterface(
                    bundle.getBinder(IShellTransitions.DESCRIPTOR));
            IStartingWindow startingWindow = IStartingWindow.Stub.asInterface(
                    bundle.getBinder(IStartingWindow.DESCRIPTOR));
            ISysuiUnlockAnimationController launcherUnlockAnimationController =
                    ISysuiUnlockAnimationController.Stub.asInterface(
                            bundle.getBinder(ISysuiUnlockAnimationController.DESCRIPTOR));
            IRecentTasks recentTasks = IRecentTasks.Stub.asInterface(
                    bundle.getBinder(IRecentTasks.DESCRIPTOR));
            IBackAnimation backAnimation = IBackAnimation.Stub.asInterface(
                    bundle.getBinder(IBackAnimation.DESCRIPTOR));
            IDesktopMode desktopMode = IDesktopMode.Stub.asInterface(
                    bundle.getBinder(IDesktopMode.DESCRIPTOR));
            IUnfoldAnimation unfoldTransition = IUnfoldAnimation.Stub.asInterface(
                    bundle.getBinder(IUnfoldAnimation.DESCRIPTOR));
            IDragAndDrop dragAndDrop = IDragAndDrop.Stub.asInterface(
                    bundle.getBinder(IDragAndDrop.DESCRIPTOR));
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                SystemUiProxy.INSTANCE.get(tis).setProxy(proxy, pip,
                        bubbles, splitscreen, onehanded, shellTransitions, startingWindow,
                        recentTasks, launcherUnlockAnimationController, backAnimation, desktopMode,
                        unfoldTransition, dragAndDrop);
                tis.initInputMonitor("TISBinder#onInitialize()");
                ActivityPreloadUtil.preloadOverviewForTIS(tis, true /* fromInit */);
            }));
        }

        @BinderThread
        @Override
        public void onTaskbarToggled() {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                TaskbarActivityContext activityContext =
                        tis.mTaskbarManager.getCurrentActivityContext();

                if (activityContext != null) {
                    activityContext.toggleTaskbarStash();
                }
            }));
        }

        @BinderThread
        public void onOverviewToggle() {
            TestLogging.recordEvent(TestProtocol.SEQUENCE_MAIN, "onOverviewToggle");
            executeForTouchInteractionService(tis -> {
                int displayId = tis.focusedDisplayIdForOverviewOnConnectedDisplays();
                RecentsAnimationDeviceState deviceState = tis.mDeviceStateRepository.get(
                        displayId);
                if (deviceState != null) {
                    if (deviceState.isScreenPinningActive()) {
                        return;
                    }
                    if (!deviceState.canStartOverviewCommand()) {
                        Log.d(TAG, "onOverviewShown ignored for display " + displayId
                                + " because the command is blocked");
                        return;
                    }
                }
                TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
                tis.mOverviewCommandHelper.addCommand(CommandType.TOGGLE, displayId);
            });
        }

        @BinderThread
        @Override
        public void onOverviewShown(boolean triggeredFromAltTab) {
            executeForTouchInteractionService(tis -> {
                final int displayId =
                        triggeredFromAltTab
                                ? tis.focusedDisplayIdForAltTabKqsOnConnectedDisplays()
                                : tis.focusedDisplayIdForOverviewOnConnectedDisplays();
                RecentsAnimationDeviceState deviceState = tis.mDeviceStateRepository.get(
                        displayId);
                if (deviceState != null && !deviceState.canStartOverviewCommand()) {
                    Log.d(TAG, "onOverviewShown ignored for display " + displayId
                            + " because the command is blocked");
                    return;
                }

                if (triggeredFromAltTab) {
                    TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
                    tis.mOverviewCommandHelper.addCommand(CommandType.SHOW_ALT_TAB, displayId);
                } else {
                    tis.mOverviewCommandHelper.addCommand(CommandType.SHOW_WITH_FOCUS, displayId);
                }
            });
        }

        @BinderThread
        @Override
        public void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
            executeForTouchInteractionService(tis -> {
                if (triggeredFromAltTab && !triggeredFromHomeKey) {
                    // onOverviewShownFromAltTab hides the overview and ends at the target app
                    int displayId = tis.focusedDisplayIdForAltTabKqsOnConnectedDisplays();
                    RecentsAnimationDeviceState deviceState = tis.mDeviceStateRepository.get(
                            displayId);
                    if (deviceState != null && !deviceState.canStartOverviewCommand()) {
                        Log.d(TAG, "onOverviewHidden ignored for display " + displayId
                                + " because the command is blocked");
                        return;
                    }
                    tis.mOverviewCommandHelper.addCommand(CommandType.HIDE_ALT_TAB, displayId);
                }
            });
        }

        @BinderThread
        @Override
        public void onAssistantAvailable(boolean available, boolean longPressHomeEnabled) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                tis.mDeviceStateRepository.forEach(/* createIfAbsent= */ true, deviceState ->
                        deviceState.setAssistantAvailable(available)
                );
                tis.onAssistantVisibilityChanged();
                executeForTaskbarManager(taskbarManager -> taskbarManager
                        .onLongPressHomeEnabled(longPressHomeEnabled));
            }));
        }

        @BinderThread
        @Override
        public void onAssistantVisibilityChanged(float visibility) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                tis.mDeviceStateRepository.forEach(/* createIfAbsent= */ true, deviceState ->
                        deviceState.setAssistantVisibility(
                                visibility));
                tis.onAssistantVisibilityChanged();
            }));
        }

        /**
         * Sent when the assistant has been invoked with the given type (defined in AssistManager)
         * and should be shown. This method is used if SystemUiProxy#setAssistantOverridesRequested
         * was previously called including this invocation type.
         */
        @Override
        public void onAssistantOverrideInvoked(int invocationType) {
            executeForTouchInteractionService(tis -> {
                if (!new ContextualSearchInvoker(tis).tryStartAssistOverride(invocationType)) {
                    Log.w(TAG, "Failed to invoke Assist override");
                }
            });
        }

        @BinderThread
        public void onSystemUiStateChanged(@SystemUiStateFlags long stateFlags, int displayId) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                // Last flags is only used for the default display case.
                RecentsAnimationDeviceState deviceState = tis.mDeviceStateRepository.get(displayId);
                if (deviceState != null) {
                    long lastFlags = deviceState.getSysuiStateFlags();
                    deviceState.setSysUIStateFlags(stateFlags);
                    tis.onSystemUiFlagsChanged(lastFlags, displayId);
                }
            }));
        }

        @BinderThread
        public void onActiveNavBarRegionChanges(Region region) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(
                    tis ->
                            tis.mDeviceStateRepository.forEach(/* createIfAbsent= */ true,
                                    deviceState ->
                                            deviceState.setDeferredGestureRegion(region))
            ));
        }

        @BinderThread
        @Override
        public void enterStageSplitFromRunningApp(int displayId, boolean leftOrTop) {
            executeForTouchInteractionService(tis -> {
                BaseContainerInterface<?, ?> containerInterface = tis.mOverviewComponentObserver
                        .getContainerInterface(displayId);
                if (containerInterface != null) {
                    RecentsViewContainer container = containerInterface.getCreatedContainer();
                    if (container != null) {
                        container.enterStageSplitFromRunningApp(leftOrTop, displayId);
                    }
                }
            });
        }

        @BinderThread
        @Override
        public void onDisplayAddSystemDecorations(int displayId) {
            executeForTouchInteractionService(tis ->
                    tis.mSystemDecorationChangeObserver.notifyAddSystemDecorations(displayId));
        }

        @BinderThread
        @Override
        public void onDisplayRemoved(int displayId) {
            executeForTouchInteractionService(tis -> {
                tis.mSystemDecorationChangeObserver.notifyOnDisplayRemoved(displayId);
            });
        }

        @BinderThread
        @Override
        public void onDisplayRemoveSystemDecorations(int displayId) {
            executeForTouchInteractionService(tis -> {
                tis.mSystemDecorationChangeObserver.notifyDisplayRemoveSystemDecorations(displayId);
            });
        }

        @BinderThread
        @Override
        public void updateWallpaperVisibility(int displayId, boolean visible) {
            executeForTaskbarManager(taskbarManager ->
                    taskbarManager.setWallpaperVisible(displayId, visible));
        }

        @BinderThread
        @Override
        public void checkNavBarModes(int displayId) {
            executeForTaskbarManager(taskbarManager ->
                    taskbarManager.checkNavBarModes(displayId));
        }

        @BinderThread
        @Override
        public void finishBarAnimations(int displayId) {
            executeForTaskbarManager(taskbarManager ->
                    taskbarManager.finishBarAnimations(displayId));
        }

        @BinderThread
        @Override
        public void touchAutoDim(int displayId, boolean reset) {
            executeForTaskbarManager(taskbarManager ->
                    taskbarManager.touchAutoDim(displayId, reset));
        }

        @BinderThread
        @Override
        public void transitionTo(int displayId, @BarTransitions.TransitionMode int barMode,
                boolean animate) {
            executeForTaskbarManager(taskbarManager ->
                    taskbarManager.transitionTo(displayId, barMode, animate));
        }

        @BinderThread
        @Override
        public void appTransitionPending(boolean pending) {
            executeForTaskbarManager(taskbarManager ->
                    taskbarManager.appTransitionPending(pending));
        }

        @Override
        public void onRotationProposal(int rotation, boolean isValid) {
            executeForTaskbarManager(taskbarManager ->
                    taskbarManager.onRotationProposal(rotation, isValid));
        }

        @Override
        public void disable(int displayId, int state1, int state2, boolean animate) {
            executeForTaskbarManager(taskbarManager ->
                    taskbarManager.disableNavBarElements(displayId, state1, state2, animate));
        }

        @Override
        public void onSystemBarAttributesChanged(int displayId, int behavior) {
            executeForTaskbarManager(taskbarManager ->
                    taskbarManager.onSystemBarAttributesChanged(displayId, behavior));
        }

        @Override
        public void onTransitionModeUpdated(int barMode, boolean checkBarModes) {
            executeForTaskbarManager(taskbarManager ->
                    taskbarManager.onTransitionModeUpdated(barMode, checkBarModes));
        }

        @Override
        public void onNavButtonsDarkIntensityChanged(float darkIntensity) {
            executeForTaskbarManager(taskbarManager ->
                    taskbarManager.onNavButtonsDarkIntensityChanged(darkIntensity));
        }

        @Override
        public void onNavigationBarLumaSamplingEnabled(int displayId, boolean enable) {
            executeForTaskbarManager(taskbarManager ->
                    taskbarManager.onNavigationBarLumaSamplingEnabled(displayId, enable));
        }

        @Override
        public void onUnbind(IRemoteCallback reply) {
            // Run everything in the same main thread block to ensure the cleanup happens before
            // sending the reply.
            MAIN_EXECUTOR.execute(() -> {
                executeForTaskbarManager(TaskbarManager::destroy);
                try {
                    reply.sendResult(null);
                } catch (RemoteException e) {
                    Log.w(TAG, "onUnbind: Failed to reply to LauncherProxyService", e);
                }
            });
        }

        @Override
        public void onActionCornerActivated(int action, int displayId) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                ActionCornerHandler actionCornerHandler = tis.mActionCornerHandler;
                if (actionCornerHandler == null) {
                    return;
                }
                actionCornerHandler.handleAction(action, displayId);
            }));
        }

        private void executeForTouchInteractionService(
                @NonNull Consumer<TouchInteractionService> tisConsumer) {
            TouchInteractionService tis = mTis.get();
            if (tis == null) return;
            tisConsumer.accept(tis);
        }

        private void executeForTaskbarManager(
                @NonNull Consumer<TaskbarManager> taskbarManagerConsumer) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                TaskbarManager taskbarManager = tis.mTaskbarManager;
                if (taskbarManager == null) return;
                taskbarManagerConsumer.accept(taskbarManager);
            }));
        }

        /**
         * Returns the {@link TaskbarManager}.
         * <p>
         * Returns {@code null} if TouchInteractionService is not connected
         */
        @Nullable
        public TaskbarManager getTaskbarManager() {
            TouchInteractionService tis = mTis.get();
            if (tis == null) return null;
            return tis.mTaskbarManager;
        }

        @VisibleForTesting
        public void injectFakeTrackpadForTesting() {
            TouchInteractionService tis = mTis.get();
            if (tis == null) return;
            tis.mTrackpadsConnected.add(1000);
            tis.initInputMonitor("tapl testing");
        }

        @VisibleForTesting
        public void ejectFakeTrackpadForTesting() {
            TouchInteractionService tis = mTis.get();
            if (tis == null) return;
            tis.mTrackpadsConnected.clear();
            // This method destroys the current input monitor if set up, and only init a new one
            // in 3-button mode if {@code mTrackpadsConnected} is not empty. So in other words,
            // it will destroy the input monitor.
            tis.initInputMonitor("tapl testing");
        }

        /**
         * Sets whether a predictive back-to-home animation is in progress in the device state
         */
        public void setPredictiveBackToHomeInProgress(boolean isInProgress) {
            executeForTouchInteractionService(tis ->
                    tis.mDeviceStateRepository.forEach(/* createIfAbsent= */ true, deviceState ->
                            deviceState.setPredictiveBackToHomeInProgress(isInProgress)));
        }

        /**
         * Returns the {@link OverviewCommandHelper}.
         * <p>
         * Returns {@code null} if TouchInteractionService is not connected
         */
        @Nullable
        public OverviewCommandHelper getOverviewCommandHelper() {
            TouchInteractionService tis = mTis.get();
            if (tis == null) return null;
            return tis.mOverviewCommandHelper;
        }

        /**
         * Sets a proxy to bypass swipe up behavior
         */
        public void setSwipeUpProxy(Function<GestureState, AnimatedFloat> proxy) {
            executeForTouchInteractionService(
                    tis -> tis.mSwipeUpProxyProvider = proxy != null ? proxy : (i -> null));
        }

        /**
         * Sets the task id where gestures should be blocked
         */
        public void setGestureBlockedTaskId(int taskId) {
            executeForTouchInteractionService(
                    tis ->
                            tis.mDeviceStateRepository.forEach(/* createIfAbsent= */ true,
                                    deviceState ->
                                            deviceState.setGestureBlockingTaskId(taskId))
            );
        }

        /** Refreshes the current overview target. */
        @VisibleForTesting
        public void refreshOverviewTargetForTest() {
            executeForTouchInteractionService(tis -> {
                tis.mAllAppsActionManager.onDestroy();
                tis.onOverviewTargetChanged(tis.mOverviewComponentObserver.isHomeAndOverviewSame());
                if (RecentsWindowFlags.getEnableOverviewInWindow()) {
                    Launcher launcher = Launcher.ACTIVITY_TRACKER.getCreatedContext();
                    if (launcher != null) {
                        tis.mTaskbarManager.setActivity(launcher);
                    }
                }
            });
        }
    }

    private PerDisplayRepository<RotationTouchHelper> mRotationTouchHelperRepository;

    private final AbsSwipeUpHandler.Factory mLauncherSwipeHandlerFactory =
            this::createLauncherSwipeHandler;
    private final AbsSwipeUpHandler.Factory mFallbackSwipeHandlerFactory =
            this::createFallbackSwipeHandler;
    private final AbsSwipeUpHandler.Factory mRecentsWindowSwipeHandlerFactory =
            this::createRecentsWindowSwipeHandler;
    // This needs to be a member to be queued and potentially removed later if the service is
    // destroyed before the user is unlocked
    private final Runnable mUserUnlockedRunnable = this::onUserUnlocked;

    private final ScreenOnTracker.ScreenOnListener mScreenOnListener = this::onScreenOnChanged;
    private final OverviewChangeListener mOverviewChangeListener = this::onOverviewTargetChanged;

    private final TaskbarNavButtonCallbacks mNavCallbacks = new TaskbarNavButtonCallbacks() {
        @Override
        public void onNavigateHome(int displayId) {
            if (enableOverviewOnConnectedDisplays()) {
                mOverviewCommandHelper.addCommand(CommandType.HOME, displayId);
            } else {
                mOverviewCommandHelper.addCommand(CommandType.HOME, DEFAULT_DISPLAY);
            }
        }

        @Override
        public void onToggleOverview(int displayId) {
            if (enableOverviewOnConnectedDisplays()) {
                mOverviewCommandHelper.addCommand(CommandType.TOGGLE, displayId);
            } else {
                mOverviewCommandHelper.addCommand(CommandType.TOGGLE, DEFAULT_DISPLAY);
            }
        }

        @Override
        public void onHideOverview(int displayId) {
            if (enableOverviewOnConnectedDisplays()) {
                mOverviewCommandHelper.addCommand(CommandType.HIDE_ALT_TAB, displayId);
            } else {
                mOverviewCommandHelper.addCommand(CommandType.HIDE_ALT_TAB, DEFAULT_DISPLAY);
            }
        }
    };

    // We should clean up the recents window on the primary display on home intent start, however we
    // have no other way of listening to this event in the 3P launcher case.
    private final TaskStackChangeListener mHomeIntentStartedListener =
            new TaskStackChangeListener() {
                @Override
                public void onActivityRestartAttempt(ActivityManager.RunningTaskInfo task,
                        boolean homeTaskVisible, boolean clearedTask, boolean wasVisible) {
                    TaskStackChangeListener.super.onActivityRestartAttempt(task, homeTaskVisible,
                            clearedTask, wasVisible);
                    if (task.configuration.windowConfiguration.getActivityType()
                            != ACTIVITY_TYPE_HOME
                            || task.displayId != DEFAULT_DISPLAY) {
                        // We only want to handle home intent starts, and only on the primary
                        // display.
                        return;
                    }
                    if (mGestureState != DEFAULT_STATE) {
                        // If there's an ongoing gesture, we shouldn't clean up the recents window
                        // since gestures will clean up the recents window when needed.
                        return;
                    }
                    RecentsWindowManager recentsWindowManager =
                            mRecentsWindowManagerRepository.get(DEFAULT_DISPLAY);
                    TaskAnimationManager taskAnimationManager =
                            mTaskAnimationManagerRepository.get(DEFAULT_DISPLAY);
                    if (recentsWindowManager == null || taskAnimationManager == null) {
                        return;
                    }
                    if (taskAnimationManager.isRecentsAnimationRunning()) {
                        RecentsState recentsState =
                                recentsWindowManager.getStateManager().getState();
                        if (!recentsState.isRecentsViewVisible()) {
                            // If we're in a state where the recents view is visible, we can ignore
                            // the recents animation running check, otherwise we should wait for
                            // the recents animation to end.
                            return;
                        }
                    }
                    if (recentsWindowManager.isStarted()) {
                        recentsWindowManager.getStateManager().goToState(RecentsState.HOME, true);
                    }
                }
            };

    private OverviewCommandHelper mOverviewCommandHelper;
    private OverviewComponentObserver mOverviewComponentObserver;
    private InputConsumerController mInputConsumer;
    private PerDisplayRepository<RecentsAnimationDeviceState> mDeviceStateRepository;
    private PerDisplayRepository<TaskAnimationManager> mTaskAnimationManagerRepository;

    private @NonNull InputConsumer mUncheckedConsumer = InputConsumer.DEFAULT_NO_OP;

    private @NonNull InputConsumer mConsumer = InputConsumer.DEFAULT_NO_OP;
    private Choreographer mMainChoreographer;
    private boolean mUserUnlocked = false;
    private GestureState mGestureState = DEFAULT_STATE;

    private InputMonitorDisplayModel mInputMonitorDisplayModel;
    private InputMonitorCompat mInputMonitorCompat;
    private InputEventReceiver mInputEventReceiver;

    private TaskbarManager mTaskbarManager;
    private ActionCornerHandler mActionCornerHandler;
    private Function<GestureState, AnimatedFloat> mSwipeUpProxyProvider = i -> null;
    private AllAppsActionManager mAllAppsActionManager;
    private ActiveTrackpadList mTrackpadsConnected;

    private final SparseArray<NavigationMode> mGestureStartNavMode = new SparseArray<>();

    private DesktopAppLaunchTransitionManager mDesktopAppLaunchTransitionManager;

    private DisplayController.DisplayInfoChangeListener mNavigationModeChangeListener;
    private DisplayController.DisplayInfoChangeListener mNightModeChangeListener;

    PerDisplayRepository<RecentsWindowManager> mRecentsWindowManagerRepository;

    private SystemDecorationChangeObserver mSystemDecorationChangeObserver;

    private DisplayRepository mDisplayRepository;

    private QuickstepKeyGestureEventsManager mQuickstepKeyGestureEventsHandler;
    private DisplaysWithDecorationsRepositoryCompat mDisplaysWithDecorationsRepositoryCompat;
    private CoroutineDispatcher mCoroutineDispatcher;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: user=" + getUserId()
                + " instance=" + System.identityHashCode(this));
        // Initialize anything here that is needed in direct boot mode.
        // Everything else should be initialized in onUserUnlocked() below.
        mDisplayRepository = LauncherDisplayRepository.getINSTANCE().get(this);
        mDeviceStateRepository = RecentsAnimationDeviceState.REPOSITORY_INSTANCE.get(this);
        mTaskAnimationManagerRepository = TaskAnimationManager.REPOSITORY_INSTANCE.get(this);
        mMainChoreographer = Choreographer.getInstance();
        mRotationTouchHelperRepository = RotationTouchHelper.REPOSITORY_INSTANCE.get(this);
        mRecentsWindowManagerRepository = RecentsWindowManager.REPOSITORY_INSTANCE.get(this);
        mSystemDecorationChangeObserver = SystemDecorationChangeObserver.getINSTANCE().get(this);
        mQuickstepKeyGestureEventsHandler = new QuickstepKeyGestureEventsManager(this);
        mCoroutineDispatcher = ProductionDispatchers.INSTANCE.getMain();
        mDisplaysWithDecorationsRepositoryCompat =
                LauncherDisplaysWithDecorationsRepositoryCompat.getINSTANCE().get(this);
        mAllAppsActionManager = new AllAppsActionManager(this, UI_HELPER_EXECUTOR,
                mQuickstepKeyGestureEventsHandler,
                () -> mTaskbarManager.createAllAppsPendingIntent());
        mTrackpadsConnected = new ActiveTrackpadList(this, () -> {
            if (mInputMonitorCompat != null && !mTrackpadsConnected.isEmpty()) {
                // Don't destroy and reinitialize input monitor due to trackpad
                // connecting when it's already set up.
                return;
            }
            initInputMonitor("onTrackpadConnected()");
        });

        mTaskbarManager = new TaskbarManagerImplWrapper(
            new TaskbarManagerImpl(this, mAllAppsActionManager, mNavCallbacks,
                mRecentsWindowManagerRepository, mDisplaysWithDecorationsRepositoryCompat,
                    mCoroutineDispatcher));
        mDesktopAppLaunchTransitionManager =
                new DesktopAppLaunchTransitionManager(this, SystemUiProxy.INSTANCE.get(this));
        mDesktopAppLaunchTransitionManager.registerTransitions();
        mInputConsumer = InputConsumerController.getRecentsAnimationInputConsumer();

        // Call runOnUserUnlocked() before any other callbacks to ensure everything is initialized.
        LockedUserState.get(this).runOnUserUnlocked(mUserUnlockedRunnable);
        // Assume that the navigation mode changes for all displays at once.
        mNavigationModeChangeListener =
                mDeviceStateRepository.get(DEFAULT_DISPLAY).addDisplayInfoChangeCallback(
                        CHANGE_NAVIGATION_MODE, this::onNavigationModeChanged);
        // Assume that the night mode changes for all displays at once.
        mNightModeChangeListener =
                mDeviceStateRepository.get(DEFAULT_DISPLAY).addDisplayInfoChangeCallback(
                        CHANGE_NIGHT_MODE, this::onNightModeChanged);
        ScreenOnTracker.INSTANCE.get(this).addListener(mScreenOnListener);
    }

    @Nullable
    private InputEventReceiver getInputEventReceiver(int displayId) {
        if (ENABLE_GESTURE_NAV_ON_CONNECTED_DISPLAYS.isTrue()) {
            InputMonitorResource inputMonitorResource = mInputMonitorDisplayModel == null
                    ? null : mInputMonitorDisplayModel.getDisplayResource(displayId);
            return inputMonitorResource == null ? null : inputMonitorResource.inputEventReceiver;
        }
        return mInputEventReceiver;
    }

    @Nullable
    private InputMonitorCompat getInputMonitorCompat(int displayId) {
        if (ENABLE_GESTURE_NAV_ON_CONNECTED_DISPLAYS.isTrue()) {
            InputMonitorResource inputMonitorResource = mInputMonitorDisplayModel == null
                    ? null : mInputMonitorDisplayModel.getDisplayResource(displayId);
            return inputMonitorResource == null ? null : inputMonitorResource.inputMonitorCompat;
        }
        return mInputMonitorCompat;
    }

    private void disposeEventHandlers(String reason) {
        Log.d(TAG, "disposeEventHandlers: Reason: " + reason
                + " instance=" + System.identityHashCode(this));
        if (ENABLE_GESTURE_NAV_ON_CONNECTED_DISPLAYS.isTrue()) {
            if (mInputMonitorDisplayModel == null) return;
            mInputMonitorDisplayModel.destroy();
            return;
        }
        if (mInputEventReceiver != null) {
            mInputEventReceiver.dispose();
            mInputEventReceiver = null;
        }
        if (mInputMonitorCompat != null) {
            mInputMonitorCompat.dispose();
            mInputMonitorCompat = null;
        }
    }

    private void initInputMonitor(String reason) {
        disposeEventHandlers("Initializing input monitor due to: " + reason);
        RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(DEFAULT_DISPLAY);
        if (deviceState.isButtonNavMode()
                && !deviceState.supportsAssistantGestureInButtonNav()
                && (mTrackpadsConnected.isEmpty())) {
            return;
        }
        if (ENABLE_GESTURE_NAV_ON_CONNECTED_DISPLAYS.isTrue()) {
            mInputMonitorDisplayModel = new InputMonitorDisplayModel(
                    this, mSystemDecorationChangeObserver);
        } else {
            mInputMonitorCompat = new InputMonitorCompat("swipe-up", DEFAULT_DISPLAY);
            mInputEventReceiver = mInputMonitorCompat.getInputReceiver(Looper.getMainLooper(),
                    mMainChoreographer, this::onInputEvent);
        }

        mRotationTouchHelperRepository.get(DEFAULT_DISPLAY).updateGestureTouchRegions();
    }

    /**
     * Called when the navigation mode changes, guaranteed to be after the device state has updated.
     */
    private void onNavigationModeChanged() {
        initInputMonitor("onNavigationModeChanged()");
        resetHomeBounceSeenOnQuickstepEnabledFirstTime();
    }
    private void onNightModeChanged() {
        ActivityPreloadUtil.preloadOverviewForTIS(this, false /* fromInit */);
    }

    @UiThread
    public void onUserUnlocked() {
        Log.d(TAG, "onUserUnlocked: userId=" + getUserId()
                + " instance=" + System.identityHashCode(this));
        mOverviewComponentObserver = OverviewComponentObserver.INSTANCE.get(this);
        mOverviewCommandHelper = new OverviewCommandHelper(this,
                mOverviewComponentObserver, mDisplayRepository, mTaskbarManager,
                mTaskAnimationManagerRepository);
        mActionCornerHandler = LauncherComponentProvider.get(
                this).getActionCornerHandlerFactory().create(mOverviewCommandHelper);
        mUserUnlocked = true;
        mInputConsumer.registerInputConsumer();
        mDeviceStateRepository.forEach(/* createIfAbsent= */ true, deviceState ->
                onSystemUiFlagsChanged(deviceState.getSysuiStateFlags(),
                        deviceState.getDisplayId()));
        onAssistantVisibilityChanged();

        // Initialize the task tracker
        TopTaskTracker.INSTANCE.get(this);

        // Temporarily disable model preload
        // new ModelPreload().start(this);
        resetHomeBounceSeenOnQuickstepEnabledFirstTime();

        mOverviewComponentObserver.addOverviewChangeListener(mOverviewChangeListener);
        onOverviewTargetChanged(mOverviewComponentObserver.isHomeAndOverviewSame());

        mTaskbarManager.onUserUnlocked();
        mQuickstepKeyGestureEventsHandler.registerOverviewKeyGestureEvent(
                createOverviewGestureHandler());
    }

    public OverviewCommandHelper getOverviewCommandHelper() {
        return mOverviewCommandHelper;
    }

    private void resetHomeBounceSeenOnQuickstepEnabledFirstTime() {
        if (!LockedUserState.get(this).isUserUnlocked() || mDeviceStateRepository.get(
                DEFAULT_DISPLAY).isButtonNavMode()) {
            // Skip if not yet unlocked (can't read user shared prefs) or if the current navigation
            // mode doesn't have gestures
            return;
        }

        // Reset home bounce seen on quick step enabled for first time
        LauncherPrefs prefs = LauncherPrefs.get(this);
        if (!prefs.get(HAS_ENABLED_QUICKSTEP_ONCE)) {
            prefs.put(
                    HAS_ENABLED_QUICKSTEP_ONCE.to(true),
                    HOME_BOUNCE_SEEN.to(false));
        }
    }

    private void onOverviewTargetChanged(boolean isHomeAndOverviewSame) {
        mAllAppsActionManager.setHomeAndOverviewSame(isHomeAndOverviewSame);
        RecentsViewContainer newOverviewContainer =
                mOverviewComponentObserver.getContainerInterface(
                        DEFAULT_DISPLAY).getCreatedContainer();
        if (newOverviewContainer != null) {
            if (newOverviewContainer instanceof StatefulActivity activity) {
                // This will also call setRecentsViewContainer() internally.
                mTaskbarManager.setActivity(activity);
            } else {
                mTaskbarManager.setRecentsViewContainer(newOverviewContainer);
            }
        }
        if (RecentsWindowFlags.getEnableOverviewInWindow()) {
            mRecentsWindowManagerRepository.forEach(
                    /* createIfAbsent= */ false, RecentsWindowManager::cleanupRecentsWindow);
            if (isHomeAndOverviewSame) {
                TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                        mHomeIntentStartedListener);
            } else {
                TaskStackChangeListeners.getInstance().registerTaskStackListener(
                        mHomeIntentStartedListener);
            }
        }
    }

    @UiThread
    private void onSystemUiFlagsChanged(@SystemUiStateFlags long lastSysUIFlags, int displayId) {
        if (LockedUserState.get(this).isUserUnlocked()) {
            RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
            TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(
                    displayId);
            if (deviceState != null && taskAnimationManager != null) {
                long systemUiStateFlags = deviceState.getSysuiStateFlags();
                mTaskbarManager.onSystemUiFlagsChanged(systemUiStateFlags, displayId);
                if (displayId == DEFAULT_DISPLAY) {
                    // The following don't care about non-default displays, at least for now. If
                    // they
                    // ever will, they should be taken care of.
                    SystemUiProxy.INSTANCE.get(this).setLastSystemUiStateFlags(systemUiStateFlags);
                    mOverviewComponentObserver.setHomeDisabled(deviceState.isHomeDisabled());
                    taskAnimationManager.onSystemUiFlagsChanged(lastSysUIFlags, systemUiStateFlags);
                }
            }
        }
    }

    @UiThread
    private void onAssistantVisibilityChanged() {
        if (LockedUserState.get(this).isUserUnlocked()) {
            mOverviewComponentObserver.getContainerInterface(
                    DEFAULT_DISPLAY).onAssistantVisibilityChanged(
                    mDeviceStateRepository.get(DEFAULT_DISPLAY).getAssistantVisibility());
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: user=" + getUserId()
                + " instance=" + System.identityHashCode(this));
        if (LockedUserState.get(this).isUserUnlocked()) {
            mInputConsumer.unregisterInputConsumer();
            mQuickstepKeyGestureEventsHandler.onDestroy();
            mOverviewComponentObserver.setHomeDisabled(false);
            mOverviewComponentObserver.removeOverviewChangeListener(mOverviewChangeListener);
        }
        disposeEventHandlers("TouchInteractionService onDestroy()");
        SystemUiProxy.INSTANCE.get(this).clearProxy();

        mAllAppsActionManager.onDestroy();

        mTrackpadsConnected.destroy();
        mTaskbarManager.destroy();
        if (mDesktopAppLaunchTransitionManager != null) {
            mDesktopAppLaunchTransitionManager.unregisterTransitions();
        }
        mDesktopAppLaunchTransitionManager = null;
        mDeviceStateRepository.get(DEFAULT_DISPLAY).removeDisplayInfoChangeListener(
                mNavigationModeChangeListener);
        mDeviceStateRepository.get(DEFAULT_DISPLAY).removeDisplayInfoChangeListener(
                mNightModeChangeListener);
        LockedUserState.get(this).removeOnUserUnlockedRunnable(mUserUnlockedRunnable);
        ScreenOnTracker.INSTANCE.get(this).removeListener(mScreenOnListener);
        if (RecentsWindowFlags.getEnableOverviewInWindow()) {
            TaskStackChangeListeners.getInstance().unregisterTaskStackListener(
                    mHomeIntentStartedListener);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind: user=" + getUserId()
                + " instance=" + System.identityHashCode(this));
        return mTISBinder;
    }

    protected void onScreenOnChanged(boolean isOn) {
        if (isOn) {
            return;
        }
        long currentTime = SystemClock.uptimeMillis();
        MotionEvent cancelEvent = MotionEvent.obtain(
                currentTime, currentTime, ACTION_CANCEL, 0f, 0f, 0);
        onInputEvent(cancelEvent);
        cancelEvent.recycle();
    }

    private void onInputEvent(InputEvent ev) {
        int displayId = ev.getDisplayId();
        if (!(ev instanceof MotionEvent)) {
            ActiveGestureProtoLogProxy.logUnknownInputEvent(displayId, ev.toString());
            return;
        }
        MotionEvent event = (MotionEvent) ev;

        TestLogging.recordMotionEvent(
                TestProtocol.SEQUENCE_TIS, "TouchInteractionService.onInputEvent", event);

        if (!LockedUserState.get(this).isUserUnlocked()) {
            ActiveGestureProtoLogProxy.logOnInputEventUserLocked(displayId);
            return;
        }

        RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
        if (deviceState == null) {
            Log.d(TAG, "RecentsAnimationDeviceState not available for displayId " + displayId);
            return;
        }

        RotationTouchHelper rotationTouchHelper = mRotationTouchHelperRepository.get(displayId);
        if (rotationTouchHelper == null) {
            Log.d(TAG, "RotationTouchHelper not available for displayId " + displayId);
            return;
        }

        NavigationMode currentNavMode = deviceState.getMode();
        NavigationMode gestureStartNavMode = mGestureStartNavMode.get(displayId);
        if (gestureStartNavMode != null && gestureStartNavMode != currentNavMode) {
            ActiveGestureProtoLogProxy.logOnInputEventNavModeSwitched(
                    displayId, gestureStartNavMode.name(), currentNavMode.name());
            event.setAction(ACTION_CANCEL);
        } else if (deviceState.isButtonNavMode()
                && !deviceState.supportsAssistantGestureInButtonNav()
                && !isTrackpadMotionEvent(event)) {
            ActiveGestureProtoLogProxy.logOnInputEventThreeButtonNav(displayId);
            return;
        }

        final int action = event.getActionMasked();
        // Note this will create a new consumer every mouse click, as after ACTION_UP from the click
        // an ACTION_HOVER_ENTER will fire as well.
        boolean isHoverActionWithoutConsumer = enableCursorHoverStates()
                && isHoverActionWithoutConsumer(event);

        TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(displayId);
        if (taskAnimationManager == null) {
            Log.e(TAG, "TaskAnimationManager not available for displayId " + displayId);
            ActiveGestureProtoLogProxy.logOnTaskAnimationManagerNotAvailable(displayId);
            return;
        }
        if (action == ACTION_DOWN || isHoverActionWithoutConsumer) {
            taskAnimationManager.notifyNewGestureStart();
        }
        if (taskAnimationManager.shouldIgnoreMotionEvents()) {
            if (action == ACTION_DOWN || isHoverActionWithoutConsumer) {
                ActiveGestureProtoLogProxy.logOnInputIgnoringFollowingEvents(displayId);
            }
            return;
        }

        InputMonitorCompat inputMonitorCompat = getInputMonitorCompat(displayId);
        InputEventReceiver inputEventReceiver = getInputEventReceiver(displayId);

        if (action == ACTION_DOWN || isHoverActionWithoutConsumer) {
            mGestureStartNavMode.set(displayId, currentNavMode);
        } else if (action == ACTION_UP || action == ACTION_CANCEL) {
            mGestureStartNavMode.delete(displayId);
        }

        SafeCloseable traceToken = TraceHelper.INSTANCE.allowIpcs("TIS.onInputEvent");

        CompoundString reasonString = action == ACTION_DOWN
                ? CompoundString.newEmptyString() : CompoundString.NO_OP;
        if (action == ACTION_DOWN || isHoverActionWithoutConsumer) {
            rotationTouchHelper.setOrientationTransformIfNeeded(event);

            boolean isOneHandedModeActive = deviceState.isOneHandedModeActive();
            boolean isInSwipeUpTouchRegion = rotationTouchHelper.isInSwipeUpTouchRegion(event);
            TaskbarActivityContext tac = mTaskbarManager.getCurrentActivityContext();
            BubbleControllers bubbleControllers = tac != null ? tac.getBubbleControllers() : null;
            boolean isOnBubbles = bubbleControllers != null
                    && BubbleBarInputConsumer.isEventOnBubbles(tac, event);
            if (deviceState.isButtonNavMode()
                    && deviceState.supportsAssistantGestureInButtonNav()) {
                reasonString.append("in three button mode which supports Assistant gesture");
                // Consume gesture event for Assistant (all other gestures should do nothing).
                if (deviceState.canTriggerAssistantAction(event)) {
                    reasonString.append(" and event can trigger assistant action, "
                            + "consuming gesture for assistant action");
                    mGestureState = createGestureState(
                            displayId, mGestureState, getTrackpadGestureType(event));
                    mUncheckedConsumer = tryCreateAssistantInputConsumer(
                            this,
                            deviceState,
                            inputMonitorCompat,
                            mGestureState,
                            event);
                } else {
                    reasonString.append(" but event cannot trigger Assistant, "
                            + "consuming gesture as no-op");
                    mUncheckedConsumer = createNoOpInputConsumer(displayId);
                }
            } else if ((!isOneHandedModeActive && isInSwipeUpTouchRegion)
                    || isHoverActionWithoutConsumer || isOnBubbles) {
                reasonString.append(!isOneHandedModeActive && isInSwipeUpTouchRegion
                        ? "one handed mode is not active and event is in swipe up region, "
                                + "creating new input consumer"
                        : "isHoverActionWithoutConsumer == true, creating new input consumer");
                // Clone the previous gesture state since onConsumerAboutToBeSwitched might trigger
                // onConsumerInactive and wipe the previous gesture state
                GestureState prevGestureState = new GestureState(mGestureState);
                GestureState newGestureState = createGestureState(
                        displayId, mGestureState, getTrackpadGestureType(event));
                mConsumer.onConsumerAboutToBeSwitched();
                mGestureState = newGestureState;
                mConsumer = newConsumer(
                        this,
                        mUserUnlocked,
                        mOverviewComponentObserver,
                        deviceState,
                        prevGestureState,
                        mGestureState,
                        taskAnimationManager,
                        inputMonitorCompat,
                        getSwipeUpHandlerFactory(displayId),
                        this::onConsumerInactive,
                        inputEventReceiver,
                        mTaskbarManager,
                        mSwipeUpProxyProvider,
                        mOverviewCommandHelper,
                        event,
                        rotationTouchHelper);
                mUncheckedConsumer = mConsumer;
            } else if ((deviceState.isFullyGesturalNavMode() || isTrackpadMultiFingerSwipe(event))
                    && deviceState.canTriggerAssistantAction(event)) {
                reasonString.append(deviceState.isFullyGesturalNavMode()
                        ? "using fully gestural nav and event can trigger assistant action, "
                                + "consuming gesture for assistant action"
                        : "event is a trackpad multi-finger swipe and event can trigger assistant "
                                + "action, consuming gesture for assistant action");
                mGestureState = createGestureState(
                        displayId, mGestureState, getTrackpadGestureType(event));
                // Do not change mConsumer as if there is an ongoing QuickSwitch gesture, we
                // should not interrupt it. QuickSwitch assumes that interruption can only
                // happen if the next gesture is also quick switch.
                mUncheckedConsumer = tryCreateAssistantInputConsumer(
                        this, deviceState, inputMonitorCompat, mGestureState, event);
            } else if (deviceState.canTriggerOneHandedAction(event)) {
                reasonString.append("event can trigger one-handed action, "
                        + "consuming gesture for one-handed action");
                // Consume gesture event for triggering one handed feature.
                mUncheckedConsumer = new OneHandedModeInputConsumer(
                        this,
                        displayId,
                        deviceState,
                        InputConsumer.createNoOpInputConsumer(displayId), inputMonitorCompat);
            } else {
                mUncheckedConsumer = InputConsumer.createNoOpInputConsumer(displayId);
            }
        } else {
            // Other events
            if (mUncheckedConsumer.getType() != InputConsumer.TYPE_NO_OP) {
                // Only transform the event if we are handling it in a proper consumer
                rotationTouchHelper.setOrientationTransformIfNeeded(event);
            }
        }

        if (mUncheckedConsumer.getType() != InputConsumer.TYPE_NO_OP) {
            switch (action) {
                case ACTION_DOWN:
                    ActiveGestureProtoLogProxy.logOnInputEventActionDown(displayId, reasonString);
                    // fall through
                case ACTION_UP:
                    ActiveGestureProtoLogProxy.logOnInputEventActionUp(
                            (int) event.getRawX(),
                            (int) event.getRawY(),
                            action,
                            MotionEvent.classificationToString(event.getClassification()),
                            displayId);
                    break;
                case ACTION_MOVE:
                    ActiveGestureProtoLogProxy.logOnInputEventActionMove(
                            MotionEvent.actionToString(action),
                            MotionEvent.classificationToString(event.getClassification()),
                            event.getPointerCount(),
                            displayId);
                    break;
                default: {
                    ActiveGestureProtoLogProxy.logOnInputEventGenericAction(
                            MotionEvent.actionToString(action),
                            MotionEvent.classificationToString(event.getClassification()),
                            displayId);
                }
            }
        }

        boolean cancelGesture = mGestureState.getContainerInterface() != null
                && mGestureState.getContainerInterface().shouldCancelCurrentGesture();
        boolean cleanUpConsumer = (action == ACTION_UP || action == ACTION_CANCEL || cancelGesture)
                && mConsumer != null
                && !mConsumer.getActiveConsumerInHierarchy().isConsumerDetachedFromGesture();
        if (cancelGesture) {
            event.setAction(ACTION_CANCEL);
        }

        if (mGestureState.isTrackpadGesture() && (action == ACTION_POINTER_DOWN
                || action == ACTION_POINTER_UP)) {
            // Skip ACTION_POINTER_DOWN and ACTION_POINTER_UP events from trackpad.
        } else if (isCursorHoverEvent(event)) {
            mUncheckedConsumer.onHoverEvent(event);
        } else {
            mUncheckedConsumer.onMotionEvent(event);
        }

        if (cleanUpConsumer) {
            reset(displayId);
        }
        traceToken.close();
    }

    private boolean isHoverActionWithoutConsumer(MotionEvent event) {
        // Only process these events when taskbar is present.
        int displayId = event.getDisplayId();
        TaskbarActivityContext tac = mTaskbarManager.getTaskbarForDisplay(displayId);
        boolean isTaskbarPresent = tac != null && tac.getDeviceProfile().isTaskbarPresent
                && !tac.isPhoneMode();
        return event.isHoverEvent() && (mUncheckedConsumer.getType() & TYPE_CURSOR_HOVER) == 0
                && isTaskbarPresent;
    }

    // Talkback generates hover events on touch, which we do not want to consume.
    private boolean isCursorHoverEvent(MotionEvent event) {
        return event.isHoverEvent() && event.getSource() == InputDevice.SOURCE_MOUSE;
    }

    public GestureState createGestureState(
            int displayId,
            GestureState previousGestureState,
            GestureState.TrackpadGestureType trackpadGestureType) {
        final GestureState gestureState;
        TopTaskTracker.CachedTaskInfo taskInfo;
        TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(displayId);
        if (taskAnimationManager != null && taskAnimationManager.isRecentsAnimationRunning()) {
            gestureState = new GestureState(
                    mOverviewComponentObserver, displayId, ActiveGestureLog.INSTANCE.getLogId());
            TopTaskTracker.CachedTaskInfo previousTaskInfo = previousGestureState.getRunningTask();
            // previousTaskInfo can be null iff previousGestureState == GestureState.DEFAULT_STATE
            taskInfo = previousTaskInfo != null
                    ? previousTaskInfo
                    : TopTaskTracker.INSTANCE.get(this).getCachedTopTask(false, displayId);
            gestureState.updateRunningTask(taskInfo);
            gestureState.updateLastStartedTaskIds(previousGestureState.getLastStartedTaskIds());
            gestureState.updatePreviouslyAppearedTaskIds(
                    previousGestureState.getPreviouslyAppearedTaskIds());
        } else {
            gestureState = new GestureState(
                    mOverviewComponentObserver,
                    displayId,
                    ActiveGestureLog.INSTANCE.incrementLogId());
            taskInfo = TopTaskTracker.INSTANCE.get(this).getCachedTopTask(false, displayId);
            gestureState.updateRunningTask(taskInfo);
        }
        gestureState.setTrackpadGestureType(trackpadGestureType);

        // Log initial state for the gesture.
        ActiveGestureProtoLogProxy.logRunningTaskPackage(taskInfo.getPackageName());
        RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
        if (deviceState != null) {
            ActiveGestureProtoLogProxy.logSysuiStateFlags(deviceState.getSystemUiStateString());
        }
        return gestureState;
    }

    /**
     * Returns a AbsSwipeUpHandler.Factory, used to instantiate AbsSwipeUpHandler later.
     * @param displayId The displayId of the display this handler will be used on.
     */
    public AbsSwipeUpHandler.Factory getSwipeUpHandlerFactory(int displayId) {
        BaseContainerInterface<?, ?> containerInterface =
                mOverviewComponentObserver.getContainerInterface(displayId);
        if (containerInterface instanceof FallbackWindowInterface) {
            return mRecentsWindowSwipeHandlerFactory;
        } else if (containerInterface instanceof LauncherActivityInterface) {
            return mLauncherSwipeHandlerFactory;
        } else {
            return mFallbackSwipeHandlerFactory;
        }
    }

    /**
     * To be called by the consumer when it's no longer active. This can be called by any consumer
     * in the hierarchy at any point during the gesture (ie. if a delegate consumer starts
     * intercepting touches, the base consumer can try to call this).
     */
    private void onConsumerInactive(InputConsumer caller) {
        if (mConsumer != null && mConsumer.getActiveConsumerInHierarchy() == caller) {
            reset(caller.getDisplayId());
        }
    }

    private void reset(int displayId) {
        mConsumer = mUncheckedConsumer = InputConsumerUtils.getDefaultInputConsumer(
                displayId,
                mUserUnlocked,
                mTaskAnimationManagerRepository.get(displayId),
                mTaskbarManager,
                CompoundString.NO_OP);
        mGestureState = DEFAULT_STATE;
        // By default, use batching of the input events, but check receiver before using in the rare
        // case that the monitor was disposed before the swipe settled
        InputEventReceiver inputEventReceiver = getInputEventReceiver(displayId);
        if (inputEventReceiver != null) {
            inputEventReceiver.setBatchingEnabled(true);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (!LockedUserState.get(this).isUserUnlocked()) {
            return;
        }
        // TODO (b/399094853): handle config updates for all connected displays (relevant only for
        // gestures on external displays)
        final BaseContainerInterface containerInterface =
                mOverviewComponentObserver.getContainerInterface(DEFAULT_DISPLAY);
        final RecentsViewContainer container = containerInterface.getCreatedContainer();
        if (container == null || container.isStarted()) {
            // We only care about the existing background activity.
            return;
        }
        Configuration oldConfig = container.asContext().getResources().getConfiguration();
        boolean isFoldUnfold = isTablet(oldConfig) != isTablet(newConfig);
        if (!isFoldUnfold && mOverviewComponentObserver.canHandleConfigChanges(
                container.getComponentName(),
                container.asContext().getResources().getConfiguration().diff(newConfig))) {
            // Since navBar gestural height are different between portrait and landscape,
            // can handle orientation changes and refresh navigation gestural region through
            // onOneHandedModeChanged()
            int newGesturalHeight = ResourceUtils.getNavbarSize(
                    ResourceUtils.NAVBAR_BOTTOM_GESTURE_SIZE,
                    getApplicationContext().getResources());
            mDeviceStateRepository.forEach(/* createIfAbsent= */ true, deviceState ->
                    deviceState.onOneHandedModeChanged(newGesturalHeight));
            return;
        }

        ActivityPreloadUtil.preloadOverviewForTIS(this, false /* fromInit */);
    }

    private static boolean isTablet(Configuration config) {
        return config.smallestScreenWidthDp >= MIN_TABLET_WIDTH;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] rawArgs) {
        // Dump everything
        if (LockedUserState.get(this).isUserUnlocked()) {
            PluginManagerWrapper.INSTANCE.get(getBaseContext()).dump(pw);
        }
        if (mOverviewComponentObserver != null) {
            mOverviewComponentObserver.dump(pw);
        }
        if (mOverviewCommandHelper != null) {
            mOverviewCommandHelper.dump(pw);
        }
        if (mGestureState != null) {
            mGestureState.dump("", pw);
        }
        pw.println("Input state:");
        pw.println("\tmInputMonitorCompat=" + mInputMonitorCompat);
        pw.println("\tmInputEventReceiver=" + mInputEventReceiver);
        if (mInputMonitorDisplayModel == null) {
            pw.println("\tmInputMonitorDisplayModel=null");
        } else {
            mInputMonitorDisplayModel.dump("\t", pw);
        }
        DisplayController.INSTANCE.get(this).dump(pw);
        mDisplayRepository.getDisplayIds().getValue().forEach(displayId -> {
            pw.println(String.format(Locale.ENGLISH, "TouchState (displayId %d):", displayId));
            RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
            if (deviceState != null) {
                deviceState.dump(pw);
            }
            BaseContainerInterface<?, ?> containerInterface =
                    mOverviewComponentObserver == null ? null
                            : mOverviewComponentObserver.getContainerInterface(
                                    displayId);
            RecentsViewContainer createdOverviewContainer = containerInterface == null ? null :
                    containerInterface.getCreatedContainer();
            boolean resumed = containerInterface != null && containerInterface.isResumed();
            pw.println("\tcreatedOverviewActivity=" + createdOverviewContainer);
            pw.println("\tresumed=" + resumed);
            if (createdOverviewContainer != null) {
                createdOverviewContainer.getDeviceProfile().dump(this, "", pw);
            }
            TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(
                    displayId);
            if (taskAnimationManager != null) {
                taskAnimationManager.dump("\t", pw);
            }
        });
        pw.println("\tmConsumer=" + mConsumer.getName());
        ActiveGestureLog.INSTANCE.dump("", pw);
        RecentsModel.INSTANCE.get(this).dump("", pw);
        mTaskbarManager.dumpLogs("", pw);
        DesktopVisibilityController.INSTANCE.get(this).dumpLogs("", pw);
        pw.println("ContextualSearchStateManager:");
        ContextualSearchStateManager.INSTANCE.get(this).dump("\t", pw);
        SystemUiProxy.INSTANCE.get(this).dump(pw);
        DeviceConfigWrapper.get().dump("   ", pw);
        TopTaskTracker.INSTANCE.get(this).dump(pw);
    }

    private @Nullable AbsSwipeUpHandler<?, ?, ?> createLauncherSwipeHandler(
            GestureState gestureState, long touchTimeMs) {
        int displayId = gestureState.getDisplayId();
        TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(displayId);
        RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
        RotationTouchHelper rotationTouchHelper = mRotationTouchHelperRepository.get(displayId);
        if (taskAnimationManager == null || deviceState == null || rotationTouchHelper == null) {
            Log.d(TAG, "displayId " + displayId + " not valid");
            return null;
        }
        return new LauncherSwipeHandlerV2(this, taskAnimationManager, deviceState,
                rotationTouchHelper, gestureState, touchTimeMs,
                taskAnimationManager.isRecentsAnimationRunning(),
                mInputConsumer, MSDLPlayerWrapper.INSTANCE.get(this));
    }

    private @Nullable AbsSwipeUpHandler<?, ?, ?> createFallbackSwipeHandler(
            GestureState gestureState, long touchTimeMs) {
        int displayId = gestureState.getDisplayId();
        TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(displayId);
        RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
        RotationTouchHelper rotationTouchHelper = mRotationTouchHelperRepository.get(displayId);
        if (taskAnimationManager == null || deviceState == null || rotationTouchHelper == null) {
            Log.d(TAG, "displayId " + displayId + " not valid");
            return null;
        }
        return new FallbackSwipeHandler(this, taskAnimationManager, deviceState,
                rotationTouchHelper, gestureState, touchTimeMs,
                taskAnimationManager.isRecentsAnimationRunning(),
                mInputConsumer, MSDLPlayerWrapper.INSTANCE.get(this));
    }

    private @Nullable AbsSwipeUpHandler<?, ?, ?> createRecentsWindowSwipeHandler(
            GestureState gestureState, long touchTimeMs) {
        int displayId = gestureState.getDisplayId();
        TaskAnimationManager taskAnimationManager = mTaskAnimationManagerRepository.get(displayId);
        RecentsAnimationDeviceState deviceState = mDeviceStateRepository.get(displayId);
        RotationTouchHelper rotationTouchHelper = mRotationTouchHelperRepository.get(displayId);
        RecentsWindowManager recentsWindowManager = mRecentsWindowManagerRepository.get(displayId);
        if (taskAnimationManager == null || deviceState == null || rotationTouchHelper == null
                || recentsWindowManager == null) {
            Log.d(TAG, "displayId " + displayId + " not valid");
            return null;
        }
        return new RecentsWindowSwipeHandler(recentsWindowManager,
                taskAnimationManager, deviceState,
                rotationTouchHelper, recentsWindowManager, gestureState, touchTimeMs,
                taskAnimationManager.isRecentsAnimationRunning(),
                mInputConsumer, MSDLPlayerWrapper.INSTANCE.get(this));
    }

    private int focusedDisplayIdForOverviewOnConnectedDisplays() {
        return enableOverviewOnConnectedDisplays()
                ? SystemUiProxy.INSTANCE.get(this).getFocusState().getFocusedDisplayId()
                : DEFAULT_DISPLAY;
    }

    private int focusedDisplayIdForAltTabKqsOnConnectedDisplays() {
        return enableAltTabKqsOnConnectedDisplays.isTrue()
                ? SystemUiProxy.INSTANCE.get(this).getFocusState().getFocusedDisplayId()
                : DEFAULT_DISPLAY;
    }


    private OverviewGestureHandler createOverviewGestureHandler() {
        return new OverviewGestureHandler() {
            @Override
            public void showOverview(@NonNull OverviewType type) {
                mTISBinder.onOverviewShown(/* triggeredFromAltTab= */ type == OverviewType.ALT_TAB);
            }

            @Override
            public void hideOverview(@NonNull OverviewType type) {
                mTISBinder.onOverviewHidden(
                        /* triggeredFromAltTab= */ type == OverviewType.ALT_TAB,
                        /* triggeredFromHomeKey= */ type == OverviewType.HOME);
            }
        };
    }

    /**
     * Helper class that keeps track of external displays and prepares input monitors for each.
     */
    private class InputMonitorDisplayModel extends DisplayModel<InputMonitorResource> {

        private InputMonitorDisplayModel(
                Context context, SystemDecorationChangeObserver systemDecorationChangeObserver) {
            super(context, systemDecorationChangeObserver, mDisplaysWithDecorationsRepositoryCompat,
                    mCoroutineDispatcher);
            initializeDisplays();
        }

        @NonNull
        @Override
        public InputMonitorResource createDisplayResource(@NonNull Display display) {
            return new InputMonitorResource(display.getDisplayId());
        }
    }

    private class InputMonitorResource extends DisplayModel.DisplayResource {

        private final int displayId;

        private final InputMonitorCompat inputMonitorCompat;
        private final InputEventReceiver inputEventReceiver;

        private InputMonitorResource(int displayId) {
            this.displayId = displayId;
            inputMonitorCompat = new InputMonitorCompat("swipe-up", displayId);
            inputEventReceiver = inputMonitorCompat.getInputReceiver(
                    Looper.getMainLooper(),
                    TouchInteractionService.this.mMainChoreographer,
                    TouchInteractionService.this::onInputEvent);
        }

        @Override
        public void cleanup() {
            inputEventReceiver.dispose();
            inputMonitorCompat.dispose();
        }

        @Override
        public void dump(String prefix , PrintWriter writer) {
            writer.println(prefix + "InputMonitorResource:");

            writer.println(prefix + "\tdisplayId=" + displayId);
            writer.println(prefix + "\tinputMonitorCompat=" + inputMonitorCompat);
            writer.println(prefix + "\tinputEventReceiver=" + inputEventReceiver);
        }
    }
}
