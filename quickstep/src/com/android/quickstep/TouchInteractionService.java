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

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.Flags.enableCursorHoverStates;
import static com.android.launcher3.Flags.enableHandleDelayedGestureCallbacks;
import static com.android.launcher3.LauncherPrefs.backedUpItem;
import static com.android.launcher3.MotionEventsUtils.isTrackpadMotionEvent;
import static com.android.launcher3.MotionEventsUtils.isTrackpadMultiFingerSwipe;
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
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
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
import android.view.Choreographer;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.window.DesktopModeFlags;

import androidx.annotation.BinderThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.ConstantItem;
import com.android.launcher3.EncryptionType;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.desktop.DesktopAppLaunchTransitionManager;
import com.android.launcher3.statehandlers.DesktopVisibilityController;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.taskbar.TaskbarActivityContext;
import com.android.launcher3.taskbar.TaskbarManager;
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
import com.android.quickstep.OverviewCommandHelper.CommandType;
import com.android.quickstep.OverviewComponentObserver.OverviewChangeListener;
import com.android.quickstep.fallback.window.RecentsDisplayModel;
import com.android.quickstep.fallback.window.RecentsDisplayModel.RecentsDisplayResource;
import com.android.quickstep.fallback.window.RecentsWindowSwipeHandler;
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

    private static final DesktopModeFlags.DesktopModeFlag ENABLE_GESTURE_NAV_ON_CONNECTED_DISPLAYS =
            new DesktopModeFlags.DesktopModeFlag(Flags::enableGestureNavOnConnectedDisplays, false);

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
                // If currently screen pinning, do not enter overview
                if (tis.mDeviceState.isScreenPinningActive()) {
                    return;
                }
                TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
                tis.mOverviewCommandHelper.addCommand(CommandType.TOGGLE);
            });
        }

        @BinderThread
        @Override
        public void onOverviewShown(boolean triggeredFromAltTab) {
            executeForTouchInteractionService(tis -> {
                if (triggeredFromAltTab) {
                    TaskUtils.closeSystemWindowsAsync(CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);
                    tis.mOverviewCommandHelper.addCommand(CommandType.KEYBOARD_INPUT);
                } else {
                    tis.mOverviewCommandHelper.addCommand(CommandType.SHOW);
                }
            });
        }

        @BinderThread
        @Override
        public void onOverviewHidden(boolean triggeredFromAltTab, boolean triggeredFromHomeKey) {
            executeForTouchInteractionService(tis -> {
                if (triggeredFromAltTab && !triggeredFromHomeKey) {
                    // onOverviewShownFromAltTab hides the overview and ends at the target app
                    tis.mOverviewCommandHelper.addCommand(CommandType.HIDE);
                }
            });
        }

        @BinderThread
        @Override
        public void onAssistantAvailable(boolean available, boolean longPressHomeEnabled) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                tis.mDeviceState.setAssistantAvailable(available);
                tis.onAssistantVisibilityChanged();
                executeForTaskbarManager(taskbarManager -> taskbarManager
                        .onLongPressHomeEnabled(longPressHomeEnabled));
            }));
        }

        @BinderThread
        @Override
        public void onAssistantVisibilityChanged(float visibility) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(tis -> {
                tis.mDeviceState.setAssistantVisibility(visibility);
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
                long lastFlags = tis.mDeviceState.getSysuiStateFlag();
                tis.mDeviceState.setSysUIStateFlagsForDisplay(stateFlags, displayId);
                tis.onSystemUiFlagsChanged(lastFlags, displayId);
            }));
        }

        @BinderThread
        public void onActiveNavBarRegionChanges(Region region) {
            MAIN_EXECUTOR.execute(() -> executeForTouchInteractionService(
                    tis -> tis.mDeviceState.setDeferredGestureRegion(region)));
        }

        @BinderThread
        @Override
        public void enterStageSplitFromRunningApp(boolean leftOrTop) {
            executeForTouchInteractionService(tis -> {
                // TODO (b/397942185): support external displays
                RecentsViewContainer container = tis.mOverviewComponentObserver
                        .getContainerInterface(DEFAULT_DISPLAY).getCreatedContainer();
                if (container != null) {
                    container.enterStageSplitFromRunningApp(leftOrTop);
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
                tis.mDeviceState.clearSysUIStateFlagsForDisplay(displayId);
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
                    tis.mDeviceState.setPredictiveBackToHomeInProgress(isInProgress));
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
                    tis -> tis.mDeviceState.setGestureBlockingTaskId(taskId));
        }

        /** Refreshes the current overview target. */
        public void refreshOverviewTarget() {
            executeForTouchInteractionService(tis -> {
                tis.mAllAppsActionManager.onDestroy();
                tis.onOverviewTargetChanged(tis.mOverviewComponentObserver.isHomeAndOverviewSame());
            });
        }
    }

    private RotationTouchHelper mRotationTouchHelper;

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
        public void onNavigateHome() {
            mOverviewCommandHelper.addCommand(CommandType.HOME);
        }

        @Override
        public void onToggleOverview() {
            mOverviewCommandHelper.addCommand(CommandType.TOGGLE);
        }

        @Override
        public void onHideOverview() {
            mOverviewCommandHelper.addCommand(CommandType.HIDE);
        }
    };

    private OverviewCommandHelper mOverviewCommandHelper;
    private OverviewComponentObserver mOverviewComponentObserver;
    private InputConsumerController mInputConsumer;
    private RecentsAnimationDeviceState mDeviceState;

    private @NonNull InputConsumer mUncheckedConsumer = InputConsumer.DEFAULT_NO_OP;
    private @NonNull InputConsumer mConsumer = InputConsumer.DEFAULT_NO_OP;
    private Choreographer mMainChoreographer;
    private boolean mUserUnlocked = false;
    private GestureState mGestureState = DEFAULT_STATE;

    private InputMonitorDisplayModel mInputMonitorDisplayModel;
    private InputMonitorCompat mInputMonitorCompat;
    private InputEventReceiver mInputEventReceiver;

    private TaskbarManager mTaskbarManager;
    private Function<GestureState, AnimatedFloat> mSwipeUpProxyProvider = i -> null;
    private AllAppsActionManager mAllAppsActionManager;
    private ActiveTrackpadList mTrackpadsConnected;

    private NavigationMode mGestureStartNavMode = null;

    private DesktopAppLaunchTransitionManager mDesktopAppLaunchTransitionManager;

    private DisplayController.DisplayInfoChangeListener mDisplayInfoChangeListener;

    private RecentsDisplayModel mRecentsDisplayModel;

    private SystemDecorationChangeObserver mSystemDecorationChangeObserver;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: user=" + getUserId()
                + " instance=" + System.identityHashCode(this));
        // Initialize anything here that is needed in direct boot mode.
        // Everything else should be initialized in onUserUnlocked() below.
        mMainChoreographer = Choreographer.getInstance();
        mDeviceState = RecentsAnimationDeviceState.INSTANCE.get(this);
        mRotationTouchHelper = RotationTouchHelper.INSTANCE.get(this);
        mRecentsDisplayModel = RecentsDisplayModel.getINSTANCE().get(this);
        mSystemDecorationChangeObserver = SystemDecorationChangeObserver.getINSTANCE().get(this);
        mAllAppsActionManager = new AllAppsActionManager(
                this, UI_HELPER_EXECUTOR, this::createAllAppsPendingIntent);
        mTrackpadsConnected = new ActiveTrackpadList(this, () -> {
            if (mInputMonitorCompat != null && !mTrackpadsConnected.isEmpty()) {
                // Don't destroy and reinitialize input monitor due to trackpad
                // connecting when it's already set up.
                return;
            }
            initInputMonitor("onTrackpadConnected()");
        });

        mTaskbarManager = new TaskbarManager(this, mAllAppsActionManager, mNavCallbacks,
                mRecentsDisplayModel);
        mDesktopAppLaunchTransitionManager =
                new DesktopAppLaunchTransitionManager(this, SystemUiProxy.INSTANCE.get(this));
        mDesktopAppLaunchTransitionManager.registerTransitions();
        mInputConsumer = InputConsumerController.getRecentsAnimationInputConsumer();

        // Call runOnUserUnlocked() before any other callbacks to ensure everything is initialized.
        LockedUserState.get(this).runOnUserUnlocked(mUserUnlockedRunnable);
        mDisplayInfoChangeListener =
                mDeviceState.addNavigationModeChangedCallback(this::onNavigationModeChanged);
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

        if (mDeviceState.isButtonNavMode()
                && !mDeviceState.supportsAssistantGestureInButtonNav()
                && (mTrackpadsConnected.isEmpty())) {
            return;
        }
        if (ENABLE_GESTURE_NAV_ON_CONNECTED_DISPLAYS.isTrue()) {
            mInputMonitorDisplayModel = new InputMonitorDisplayModel(this);
        } else {
            mInputMonitorCompat = new InputMonitorCompat("swipe-up", DEFAULT_DISPLAY);
            mInputEventReceiver = mInputMonitorCompat.getInputReceiver(Looper.getMainLooper(),
                    mMainChoreographer, this::onInputEvent);
        }

        mRotationTouchHelper.updateGestureTouchRegions();
    }

    /**
     * Called when the navigation mode changes, guaranteed to be after the device state has updated.
     */
    private void onNavigationModeChanged() {
        initInputMonitor("onNavigationModeChanged()");
        resetHomeBounceSeenOnQuickstepEnabledFirstTime();
    }

    @UiThread
    public void onUserUnlocked() {
        Log.d(TAG, "onUserUnlocked: userId=" + getUserId()
                + " instance=" + System.identityHashCode(this));
        mOverviewComponentObserver = OverviewComponentObserver.INSTANCE.get(this);
        mOverviewCommandHelper = new OverviewCommandHelper(this,
                mOverviewComponentObserver, mRecentsDisplayModel,
                SystemUiProxy.INSTANCE.get(this).getFocusState(), mTaskbarManager);
        mUserUnlocked = true;
        mInputConsumer.registerInputConsumer();
        for (int displayId : mDeviceState.getDisplaysWithSysUIState()) {
            onSystemUiFlagsChanged(mDeviceState.getSystemUiStateFlags(displayId), displayId);
        }
        onAssistantVisibilityChanged();

        // Initialize the task tracker
        TopTaskTracker.INSTANCE.get(this);

        // Temporarily disable model preload
        // new ModelPreload().start(this);
        resetHomeBounceSeenOnQuickstepEnabledFirstTime();

        mOverviewComponentObserver.addOverviewChangeListener(mOverviewChangeListener);
        onOverviewTargetChanged(mOverviewComponentObserver.isHomeAndOverviewSame());

        mTaskbarManager.onUserUnlocked();
    }

    public OverviewCommandHelper getOverviewCommandHelper() {
        return mOverviewCommandHelper;
    }

    private void resetHomeBounceSeenOnQuickstepEnabledFirstTime() {
        if (!LockedUserState.get(this).isUserUnlocked() || mDeviceState.isButtonNavMode()) {
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
        // TODO (b/399089118): how will this work with per-display Taskbars? Is using the
        //  default-display container ok?
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
    }

    private PendingIntent createAllAppsPendingIntent() {
        return new PendingIntent(new IIntentSender.Stub() {
            @Override
            public void send(int code, Intent intent, String resolvedType,
                    IBinder allowlistToken, IIntentReceiver finishedReceiver,
                    String requiredPermission, Bundle options) {
                MAIN_EXECUTOR.execute(() -> mTaskbarManager.toggleAllAppsSearch());
            }
        });
    }

    @UiThread
    private void onSystemUiFlagsChanged(@SystemUiStateFlags long lastSysUIFlags, int displayId) {
        if (LockedUserState.get(this).isUserUnlocked()) {
            long systemUiStateFlags = mDeviceState.getSystemUiStateFlags(displayId);
            mTaskbarManager.onSystemUiFlagsChanged(systemUiStateFlags, displayId);
            if (displayId == DEFAULT_DISPLAY) {
                // The following don't care about non-default displays, at least for now. If they
                // ever will, they should be taken care of.
                SystemUiProxy.INSTANCE.get(this).setLastSystemUiStateFlags(systemUiStateFlags);
                mOverviewComponentObserver.setHomeDisabled(mDeviceState.isHomeDisabled());
                // TODO b/399371607 - Propagate to taskAnimationManager once overview is multi
                //  display.
                TaskAnimationManager taskAnimationManager =
                        mRecentsDisplayModel.getTaskAnimationManager(displayId);
                if (taskAnimationManager != null) {
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
                    mDeviceState.getAssistantVisibility());
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: user=" + getUserId()
                + " instance=" + System.identityHashCode(this));
        if (LockedUserState.get(this).isUserUnlocked()) {
            mInputConsumer.unregisterInputConsumer();
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
        mDeviceState.removeDisplayInfoChangeListener(mDisplayInfoChangeListener);
        LockedUserState.get(this).removeOnUserUnlockedRunnable(mUserUnlockedRunnable);
        ScreenOnTracker.INSTANCE.get(this).removeListener(mScreenOnListener);
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

        NavigationMode currentNavMode = mDeviceState.getMode();
        if (mGestureStartNavMode != null && mGestureStartNavMode != currentNavMode) {
            ActiveGestureProtoLogProxy.logOnInputEventNavModeSwitched(
                    displayId, mGestureStartNavMode.name(), currentNavMode.name());
            event.setAction(ACTION_CANCEL);
        } else if (mDeviceState.isButtonNavMode()
                && !mDeviceState.supportsAssistantGestureInButtonNav()
                && !isTrackpadMotionEvent(event)) {
            ActiveGestureProtoLogProxy.logOnInputEventThreeButtonNav(displayId);
            return;
        }

        final int action = event.getActionMasked();
        // Note this will create a new consumer every mouse click, as after ACTION_UP from the click
        // an ACTION_HOVER_ENTER will fire as well.
        boolean isHoverActionWithoutConsumer = enableCursorHoverStates()
                && isHoverActionWithoutConsumer(event);

        TaskAnimationManager taskAnimationManager = mRecentsDisplayModel.getTaskAnimationManager(
                displayId);
        if (taskAnimationManager == null) {
            Log.e(TAG, "TaskAnimationManager not available for displayId " + displayId);
            ActiveGestureProtoLogProxy.logOnTaskAnimationManagerNotAvailable(displayId);
            return;
        }
        if (enableHandleDelayedGestureCallbacks()) {
            if (action == ACTION_DOWN || isHoverActionWithoutConsumer) {
                taskAnimationManager.notifyNewGestureStart();
            }
            if (taskAnimationManager.shouldIgnoreMotionEvents()) {
                if (action == ACTION_DOWN || isHoverActionWithoutConsumer) {
                    ActiveGestureProtoLogProxy.logOnInputIgnoringFollowingEvents(displayId);
                }
                return;
            }
        }

        InputMonitorCompat inputMonitorCompat = getInputMonitorCompat(displayId);
        InputEventReceiver inputEventReceiver = getInputEventReceiver(displayId);

        if (action == ACTION_DOWN || isHoverActionWithoutConsumer) {
            mGestureStartNavMode = currentNavMode;
        } else if (action == ACTION_UP || action == ACTION_CANCEL) {
            mGestureStartNavMode = null;
        }

        SafeCloseable traceToken = TraceHelper.INSTANCE.allowIpcs("TIS.onInputEvent");

        CompoundString reasonString = action == ACTION_DOWN
                ? CompoundString.newEmptyString() : CompoundString.NO_OP;
        if (action == ACTION_DOWN || isHoverActionWithoutConsumer) {
            mRotationTouchHelper.setOrientationTransformIfNeeded(event);

            boolean isOneHandedModeActive = mDeviceState.isOneHandedModeActive();
            boolean isInSwipeUpTouchRegion = mRotationTouchHelper.isInSwipeUpTouchRegion(event);
            TaskbarActivityContext tac = mTaskbarManager.getCurrentActivityContext();
            BubbleControllers bubbleControllers = tac != null ? tac.getBubbleControllers() : null;
            boolean isOnBubbles = bubbleControllers != null
                    && BubbleBarInputConsumer.isEventOnBubbles(tac, event);
            if (mDeviceState.isButtonNavMode()
                    && mDeviceState.supportsAssistantGestureInButtonNav()) {
                reasonString.append("in three button mode which supports Assistant gesture");
                // Consume gesture event for Assistant (all other gestures should do nothing).
                if (mDeviceState.canTriggerAssistantAction(event)) {
                    reasonString.append(" and event can trigger assistant action, "
                            + "consuming gesture for assistant action");
                    mGestureState = createGestureState(
                            displayId, mGestureState, getTrackpadGestureType(event));
                    mUncheckedConsumer = tryCreateAssistantInputConsumer(
                            this,
                            mDeviceState,
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
                        mDeviceState,
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
                        event);
                mUncheckedConsumer = mConsumer;
            } else if ((mDeviceState.isFullyGesturalNavMode() || isTrackpadMultiFingerSwipe(event))
                    && mDeviceState.canTriggerAssistantAction(event)) {
                reasonString.append(mDeviceState.isFullyGesturalNavMode()
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
                        this, mDeviceState, inputMonitorCompat, mGestureState, event);
            } else if (mDeviceState.canTriggerOneHandedAction(event)) {
                reasonString.append("event can trigger one-handed action, "
                        + "consuming gesture for one-handed action");
                // Consume gesture event for triggering one handed feature.
                mUncheckedConsumer = new OneHandedModeInputConsumer(
                        this,
                        displayId,
                        mDeviceState,
                        InputConsumer.createNoOpInputConsumer(displayId), inputMonitorCompat);
            } else {
                mUncheckedConsumer = InputConsumer.createNoOpInputConsumer(displayId);
            }
        } else {
            // Other events
            if (mUncheckedConsumer.getType() != InputConsumer.TYPE_NO_OP) {
                // Only transform the event if we are handling it in a proper consumer
                mRotationTouchHelper.setOrientationTransformIfNeeded(event);
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
        TaskAnimationManager taskAnimationManager = mRecentsDisplayModel.getTaskAnimationManager(
                displayId);
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
        ActiveGestureProtoLogProxy.logSysuiStateFlags(mDeviceState.getSystemUiStateString());
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
                mRecentsDisplayModel.getTaskAnimationManager(displayId),
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
            mDeviceState.onOneHandedModeChanged(newGesturalHeight);
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
        mDeviceState.dump(pw);
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
        for (RecentsDisplayResource resource : mRecentsDisplayModel.getActiveDisplayResources()) {
            int displayId = resource.getDisplayId();
            pw.println(String.format(Locale.ENGLISH, "TouchState (displayId %d):", displayId));
            RecentsViewContainer createdOverviewContainer =
                    mOverviewComponentObserver == null ? null
                            : mOverviewComponentObserver.getContainerInterface(
                                    displayId).getCreatedContainer();
            boolean resumed = mOverviewComponentObserver != null
                    && mOverviewComponentObserver.getContainerInterface(displayId).isResumed();
            pw.println("\tcreatedOverviewActivity=" + createdOverviewContainer);
            pw.println("\tresumed=" + resumed);
            if (createdOverviewContainer != null) {
                createdOverviewContainer.getDeviceProfile().dump(this, "", pw);
            }
            resource.getTaskAnimationManager().dump("\t", pw);
        }
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

    private AbsSwipeUpHandler createLauncherSwipeHandler(
            GestureState gestureState, long touchTimeMs) {
        TaskAnimationManager taskAnimationManager = mRecentsDisplayModel.getTaskAnimationManager(
                gestureState.getDisplayId());
        return new LauncherSwipeHandlerV2(this, taskAnimationManager,
                gestureState, touchTimeMs, taskAnimationManager.isRecentsAnimationRunning(),
                mInputConsumer, MSDLPlayerWrapper.INSTANCE.get(this));
    }

    private AbsSwipeUpHandler createFallbackSwipeHandler(
            GestureState gestureState, long touchTimeMs) {
        TaskAnimationManager taskAnimationManager = mRecentsDisplayModel.getTaskAnimationManager(
                gestureState.getDisplayId());
        return new FallbackSwipeHandler(this, taskAnimationManager,
                gestureState, touchTimeMs, taskAnimationManager.isRecentsAnimationRunning(),
                mInputConsumer, MSDLPlayerWrapper.INSTANCE.get(this));
    }

    private AbsSwipeUpHandler createRecentsWindowSwipeHandler(
            GestureState gestureState, long touchTimeMs) {
        TaskAnimationManager taskAnimationManager = mRecentsDisplayModel.getTaskAnimationManager(
                gestureState.getDisplayId());
        return new RecentsWindowSwipeHandler(this, taskAnimationManager,
                gestureState, touchTimeMs, taskAnimationManager.isRecentsAnimationRunning(),
                mInputConsumer, MSDLPlayerWrapper.INSTANCE.get(this));
    }

    /**
     * Helper class that keeps track of external displays and prepares input monitors for each.
     */
    private class InputMonitorDisplayModel extends DisplayModel<InputMonitorResource> {

        private InputMonitorDisplayModel(Context context) {
            super(context);
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
