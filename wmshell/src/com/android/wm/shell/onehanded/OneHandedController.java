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

package com.android.wm.shell.onehanded;

import static android.os.UserHandle.myUserId;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.wm.shell.onehanded.OneHandedState.STATE_ACTIVE;
import static com.android.wm.shell.onehanded.OneHandedState.STATE_ENTERING;
import static com.android.wm.shell.onehanded.OneHandedState.STATE_EXITING;
import static com.android.wm.shell.onehanded.OneHandedState.STATE_NONE;
import static com.android.wm.shell.sysui.ShellSharedConstants.KEY_EXTRA_SHELL_ONE_HANDED;

import android.annotation.BinderThread;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Slog;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.window.DisplayAreaInfo;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.internal.jank.InteractionJankMonitor;
import com.android.internal.logging.UiEventLogger;
import com.android.wm.shell.R;
import com.android.wm.shell.common.DisplayChangeController;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayLayout;
import com.android.wm.shell.common.ExternalInterfaceBinder;
import com.android.wm.shell.common.RemoteCallable;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.TaskStackListenerCallback;
import com.android.wm.shell.common.TaskStackListenerImpl;
import com.android.wm.shell.shared.annotations.ExternalThread;
import com.android.wm.shell.sysui.ConfigurationChangeListener;
import com.android.wm.shell.sysui.KeyguardChangeListener;
import com.android.wm.shell.sysui.ShellCommandHandler;
import com.android.wm.shell.sysui.ShellController;
import com.android.wm.shell.sysui.ShellInit;
import com.android.wm.shell.sysui.UserChangeListener;

import java.io.PrintWriter;

/**
 * Manages and manipulates the one handed states, transitions, and gesture for phones.
 */
public class OneHandedController implements RemoteCallable<OneHandedController>,
        DisplayChangeController.OnDisplayChangingListener, ConfigurationChangeListener,
        KeyguardChangeListener, UserChangeListener {
    private static final String TAG = "OneHandedController";

    private static final String ONE_HANDED_MODE_OFFSET_PERCENTAGE =
            "persist.debug.one_handed_offset_percentage";
    private static final int DISPLAY_AREA_READY_RETRY_MS = 10;

    public static final String SUPPORT_ONE_HANDED_MODE = "ro.support_one_handed_mode";

    private boolean mIsOneHandedEnabled;
    private boolean mIsSwipeToNotificationEnabled;
    private boolean mIsShortcutEnabled;
    private boolean mTaskChangeToExit;
    private boolean mLockedDisabled;
    private boolean mKeyguardShowing;
    private int mUserId;
    private float mOffSetFraction;

    private Context mContext;

    private final ShellCommandHandler mShellCommandHandler;
    private final ShellController mShellController;
    private final AccessibilityManager mAccessibilityManager;
    private final DisplayController mDisplayController;
    private final OneHandedSettingsUtil mOneHandedSettingsUtil;
    private final OneHandedAccessibilityUtil mOneHandedAccessibilityUtil;
    private final OneHandedTimeoutHandler mTimeoutHandler;
    private final OneHandedTouchHandler mTouchHandler;
    private final OneHandedState mState;
    private final OneHandedTutorialHandler mTutorialHandler;
    private final TaskStackListenerImpl mTaskStackListener;
    private final ShellExecutor mMainExecutor;
    private final Handler mMainHandler;
    private final OneHandedImpl mImpl = new OneHandedImpl();

    private OneHandedEventCallback mEventCallback;
    private OneHandedDisplayAreaOrganizer mDisplayAreaOrganizer;
    private OneHandedUiEventLogger mOneHandedUiEventLogger;

    private final DisplayController.OnDisplaysChangedListener mDisplaysChangedListener =
            new DisplayController.OnDisplaysChangedListener() {
                @Override
                public void onDisplayConfigurationChanged(int displayId, Configuration newConfig) {
                    if (displayId != DEFAULT_DISPLAY || !isInitialized()) {
                        return;
                    }
                    updateDisplayLayout(displayId);
                }

                @Override
                public void onDisplayAdded(int displayId) {
                    if (displayId != DEFAULT_DISPLAY || !isInitialized()) {
                        return;
                    }
                    updateDisplayLayout(displayId);
                }
            };

    private final ContentObserver mActivatedObserver;
    private final ContentObserver mEnabledObserver;
    private final ContentObserver mSwipeToNotificationEnabledObserver;
    private final ContentObserver mShortcutEnabledObserver;

    private AccessibilityManager.AccessibilityStateChangeListener
            mAccessibilityStateChangeListener =
            new AccessibilityManager.AccessibilityStateChangeListener() {
                @Override
                public void onAccessibilityStateChanged(boolean enabled) {
                    if (!isInitialized()) {
                        return;
                    }
                    if (enabled) {
                        final int mOneHandedTimeout = mOneHandedSettingsUtil
                                .getSettingsOneHandedModeTimeout(
                                        mContext.getContentResolver(), mUserId);
                        final int timeout = mAccessibilityManager
                                .getRecommendedTimeoutMillis(mOneHandedTimeout * 1000
                                        /* align with A11y timeout millis */,
                                        AccessibilityManager.FLAG_CONTENT_CONTROLS);
                        mTimeoutHandler.setTimeout(timeout / 1000);
                    } else {
                        mTimeoutHandler.setTimeout(mOneHandedSettingsUtil
                                .getSettingsOneHandedModeTimeout(
                                        mContext.getContentResolver(), mUserId));
                    }
                }
            };

    private final OneHandedTransitionCallback mTransitionCallBack =
            new OneHandedTransitionCallback() {
                @Override
                public void onStartFinished(Rect bounds) {
                    mState.setState(STATE_ACTIVE);
                    notifyShortcutStateChanged(STATE_ACTIVE);
                }

                @Override
                public void onStopFinished(Rect bounds) {
                    mState.setState(STATE_NONE);
                    notifyShortcutStateChanged(STATE_NONE);
                }
            };

    private final TaskStackListenerCallback mTaskStackListenerCallback =
            new TaskStackListenerCallback() {
                @Override
                public void onTaskCreated(int taskId, ComponentName componentName) {
                    stopOneHanded(OneHandedUiEventLogger.EVENT_ONE_HANDED_TRIGGER_APP_TAPS_OUT);
                }

                @Override
                public void onTaskMovedToFront(int taskId) {
                    stopOneHanded(OneHandedUiEventLogger.EVENT_ONE_HANDED_TRIGGER_APP_TAPS_OUT);
                }
            };

    private boolean isInitialized() {
        if (mDisplayAreaOrganizer == null || mDisplayController == null
                || mOneHandedSettingsUtil == null) {
            Slog.w(TAG, "Components may not initialized yet!");
            return false;
        }
        return true;
    }

    /**
     * Creates {@link OneHandedController}, returns {@code null} if the feature is not supported.
     */
    public static OneHandedController create(Context context,
            ShellInit shellInit, ShellCommandHandler shellCommandHandler,
            ShellController shellController, WindowManager windowManager,
            DisplayController displayController, DisplayLayout displayLayout,
            TaskStackListenerImpl taskStackListener,
            InteractionJankMonitor jankMonitor, UiEventLogger uiEventLogger,
            ShellExecutor mainExecutor, Handler mainHandler) {
        OneHandedSettingsUtil settingsUtil = new OneHandedSettingsUtil();
        OneHandedAccessibilityUtil accessibilityUtil = new OneHandedAccessibilityUtil(context);
        OneHandedTimeoutHandler timeoutHandler = new OneHandedTimeoutHandler(mainExecutor);
        OneHandedState oneHandedState = new OneHandedState();
        BackgroundWindowManager backgroundWindowManager = new BackgroundWindowManager(context);
        OneHandedTutorialHandler tutorialHandler = new OneHandedTutorialHandler(context,
                settingsUtil, windowManager, backgroundWindowManager);
        OneHandedAnimationController animationController =
                new OneHandedAnimationController(context);
        OneHandedTouchHandler touchHandler = new OneHandedTouchHandler(timeoutHandler,
                mainExecutor);
        OneHandedDisplayAreaOrganizer organizer = new OneHandedDisplayAreaOrganizer(
                context, displayLayout, settingsUtil, animationController, tutorialHandler,
                jankMonitor, mainExecutor);
        OneHandedUiEventLogger oneHandedUiEventsLogger = new OneHandedUiEventLogger(uiEventLogger);
        return new OneHandedController(context, shellInit, shellCommandHandler, shellController,
                displayController, organizer, touchHandler, tutorialHandler, settingsUtil,
                accessibilityUtil, timeoutHandler, oneHandedState, oneHandedUiEventsLogger,
                taskStackListener, mainExecutor, mainHandler);
    }

    @VisibleForTesting
    OneHandedController(Context context,
            ShellInit shellInit,
            ShellCommandHandler shellCommandHandler,
            ShellController shellController,
            DisplayController displayController,
            OneHandedDisplayAreaOrganizer displayAreaOrganizer,
            OneHandedTouchHandler touchHandler,
            OneHandedTutorialHandler tutorialHandler,
            OneHandedSettingsUtil settingsUtil,
            OneHandedAccessibilityUtil oneHandedAccessibilityUtil,
            OneHandedTimeoutHandler timeoutHandler,
            OneHandedState state,
            OneHandedUiEventLogger uiEventsLogger,
            TaskStackListenerImpl taskStackListener,
            ShellExecutor mainExecutor,
            Handler mainHandler) {
        mContext = context;
        mShellCommandHandler = shellCommandHandler;
        mShellController = shellController;
        mOneHandedSettingsUtil = settingsUtil;
        mOneHandedAccessibilityUtil = oneHandedAccessibilityUtil;
        mDisplayAreaOrganizer = displayAreaOrganizer;
        mDisplayController = displayController;
        mTouchHandler = touchHandler;
        mState = state;
        mTutorialHandler = tutorialHandler;
        mMainExecutor = mainExecutor;
        mMainHandler = mainHandler;
        mOneHandedUiEventLogger = uiEventsLogger;
        mTaskStackListener = taskStackListener;
        mAccessibilityManager = AccessibilityManager.getInstance(mContext);

        final float offsetPercentageConfig = context.getResources().getFraction(
                R.fraction.config_one_handed_offset, 1, 1);
        final int sysPropPercentageConfig = SystemProperties.getInt(
                ONE_HANDED_MODE_OFFSET_PERCENTAGE, Math.round(offsetPercentageConfig * 100.0f));
        mUserId = myUserId();
        mOffSetFraction = sysPropPercentageConfig / 100.0f;
        mIsOneHandedEnabled = mOneHandedSettingsUtil.getSettingsOneHandedModeEnabled(
                context.getContentResolver(), mUserId);
        mIsSwipeToNotificationEnabled =
                mOneHandedSettingsUtil.getSettingsSwipeToNotificationEnabled(
                        context.getContentResolver(), mUserId);
        mTimeoutHandler = timeoutHandler;

        mActivatedObserver = getObserver(this::onActivatedActionChanged);
        mEnabledObserver = getObserver(this::onEnabledSettingChanged);
        mSwipeToNotificationEnabledObserver =
                getObserver(this::onSwipeToNotificationEnabledChanged);
        mShortcutEnabledObserver = getObserver(this::onShortcutEnabledChanged);

        shellInit.addInitCallback(this::onInit, this);
    }

    private void onInit() {
        mShellCommandHandler.addDumpCallback(this::dump, this);
        mDisplayController.addDisplayWindowListener(mDisplaysChangedListener);
        mDisplayController.addDisplayChangingController(this);
        setupCallback();
        registerSettingObservers(mUserId);
        setupTimeoutListener();
        updateSettings();
        updateDisplayLayout(mContext.getDisplayId());

        mAccessibilityManager.addAccessibilityStateChangeListener(
                mAccessibilityStateChangeListener);

        mState.addSListeners(mTutorialHandler);
        mShellController.addConfigurationChangeListener(this);
        mShellController.addKeyguardChangeListener(this);
        mShellController.addUserChangeListener(this);
        mShellController.addExternalInterface(KEY_EXTRA_SHELL_ONE_HANDED,
                this::createExternalInterface, this);
    }

    public OneHanded asOneHanded() {
        return mImpl;
    }

    private ExternalInterfaceBinder createExternalInterface() {
        return new IOneHandedImpl(this);
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public ShellExecutor getRemoteCallExecutor() {
        return mMainExecutor;
    }

    /**
     * Set one handed enabled or disabled when user update settings
     */
    void setOneHandedEnabled(boolean enabled) {
        mIsOneHandedEnabled = enabled;
        updateOneHandedEnabled();
    }

    /**
     * Set one handed enabled or disabled by when user update settings
     */
    void setTaskChangeToExit(boolean enabled) {
        if (enabled) {
            mTaskStackListener.addListener(mTaskStackListenerCallback);
        } else {
            mTaskStackListener.removeListener(mTaskStackListenerCallback);
        }
        mTaskChangeToExit = enabled;
    }

    /**
     * Sets whether to enable swipe bottom to notification gesture when user update settings.
     */
    void setSwipeToNotificationEnabled(boolean enabled) {
        mIsSwipeToNotificationEnabled = enabled;
    }

    @VisibleForTesting
    void notifyShortcutStateChanged(@OneHandedState.State int state) {
        if (!isShortcutEnabled()) {
            return;
        }
        mOneHandedSettingsUtil.setOneHandedModeActivated(
                mContext.getContentResolver(), state == STATE_ACTIVE ? 1 : 0, mUserId);
    }

    @VisibleForTesting
    void startOneHanded() {
        if (isLockedDisabled() || mKeyguardShowing) {
            Slog.d(TAG, "Temporary lock disabled");
            return;
        }

        if (!mDisplayAreaOrganizer.isReady()) {
            // Must wait until DisplayAreaOrganizer is ready for transitioning.
            mMainExecutor.executeDelayed(this::startOneHanded, DISPLAY_AREA_READY_RETRY_MS);
            return;
        }

        if (mState.isTransitioning() || mState.isInOneHanded()) {
            return;
        }

        if (mDisplayAreaOrganizer.getDisplayLayout().isLandscape()) {
            Slog.w(TAG, "One handed mode only support portrait mode");
            return;
        }

        mState.setState(STATE_ENTERING);
        final int yOffSet = Math.round(
                mDisplayAreaOrganizer.getDisplayLayout().height() * mOffSetFraction);
        mOneHandedAccessibilityUtil.announcementForScreenReader(
                mOneHandedAccessibilityUtil.getOneHandedStartDescription());
        mDisplayAreaOrganizer.scheduleOffset(0, yOffSet);
        mTimeoutHandler.resetTimer();
        mOneHandedUiEventLogger.writeEvent(
                OneHandedUiEventLogger.EVENT_ONE_HANDED_TRIGGER_GESTURE_IN);
    }

    @VisibleForTesting
    void stopOneHanded() {
        stopOneHanded(OneHandedUiEventLogger.EVENT_ONE_HANDED_TRIGGER_GESTURE_OUT);
    }

    private void stopOneHanded(int uiEvent) {
        if (mState.isTransitioning() || mState.getState() == STATE_NONE) {
            return;
        }
        mState.setState(STATE_EXITING);
        mOneHandedAccessibilityUtil.announcementForScreenReader(
                mOneHandedAccessibilityUtil.getOneHandedStopDescription());
        mDisplayAreaOrganizer.scheduleOffset(0, 0);
        mTimeoutHandler.removeTimer();
        mOneHandedUiEventLogger.writeEvent(uiEvent);
    }

    void registerEventCallback(OneHandedEventCallback callback) {
        mEventCallback = callback;
    }

    /**
     * Registers {@link OneHandedTransitionCallback} to monitor the transition status
     */
    public void registerTransitionCallback(OneHandedTransitionCallback callback) {
        mDisplayAreaOrganizer.registerTransitionCallback(callback);
    }

    private void setupCallback() {
        mTouchHandler.registerTouchEventListener(() ->
                stopOneHanded(OneHandedUiEventLogger.EVENT_ONE_HANDED_TRIGGER_OVERSPACE_OUT));
        mDisplayAreaOrganizer.registerTransitionCallback(mTouchHandler);
        mDisplayAreaOrganizer.registerTransitionCallback(mTutorialHandler);
        mDisplayAreaOrganizer.registerTransitionCallback(mTransitionCallBack);
        if (mTaskChangeToExit) {
            mTaskStackListener.addListener(mTaskStackListenerCallback);
        }
    }

    private void registerSettingObservers(int newUserId) {
        mOneHandedSettingsUtil.registerSettingsKeyObserver(
                Settings.Secure.ONE_HANDED_MODE_ACTIVATED,
                mContext.getContentResolver(), mActivatedObserver, newUserId);
        mOneHandedSettingsUtil.registerSettingsKeyObserver(Settings.Secure.ONE_HANDED_MODE_ENABLED,
                mContext.getContentResolver(), mEnabledObserver, newUserId);
        mOneHandedSettingsUtil.registerSettingsKeyObserver(
                Settings.Secure.SWIPE_BOTTOM_TO_NOTIFICATION_ENABLED,
                mContext.getContentResolver(), mSwipeToNotificationEnabledObserver, newUserId);
        mOneHandedSettingsUtil.registerSettingsKeyObserver(
                Settings.Secure.ACCESSIBILITY_BUTTON_TARGETS,
                mContext.getContentResolver(), mShortcutEnabledObserver, newUserId);
        mOneHandedSettingsUtil.registerSettingsKeyObserver(
                Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE,
                mContext.getContentResolver(), mShortcutEnabledObserver, newUserId);
    }

    private void unregisterSettingObservers() {
        mOneHandedSettingsUtil.unregisterSettingsKeyObserver(mContext.getContentResolver(),
                mEnabledObserver);
        mOneHandedSettingsUtil.unregisterSettingsKeyObserver(mContext.getContentResolver(),
                mSwipeToNotificationEnabledObserver);
        mOneHandedSettingsUtil.unregisterSettingsKeyObserver(mContext.getContentResolver(),
                mShortcutEnabledObserver);
    }

    private void updateSettings() {
        setOneHandedEnabled(mOneHandedSettingsUtil
                .getSettingsOneHandedModeEnabled(mContext.getContentResolver(), mUserId));
        mTimeoutHandler.setTimeout(mOneHandedSettingsUtil
                .getSettingsOneHandedModeTimeout(mContext.getContentResolver(), mUserId));
        setTaskChangeToExit(mOneHandedSettingsUtil
                .getSettingsTapsAppToExit(mContext.getContentResolver(), mUserId));
        setSwipeToNotificationEnabled(mOneHandedSettingsUtil
                .getSettingsSwipeToNotificationEnabled(mContext.getContentResolver(), mUserId));
        onShortcutEnabledChanged();
    }

    @VisibleForTesting
    void updateDisplayLayout(int displayId) {
        final DisplayLayout newDisplayLayout = mDisplayController.getDisplayLayout(displayId);
        if (newDisplayLayout == null) {
            Slog.w(TAG, "Failed to get new DisplayLayout.");
            return;
        }
        mDisplayAreaOrganizer.setDisplayLayout(newDisplayLayout);
        mTutorialHandler.onDisplayChanged(newDisplayLayout);
    }

    private ContentObserver getObserver(Runnable onChangeRunnable) {
        return new ContentObserver(mMainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                onChangeRunnable.run();
            }
        };
    }

    @VisibleForTesting
    void notifyExpandNotification() {
        if (mEventCallback != null) {
            mMainExecutor.execute(() -> mEventCallback.notifyExpandNotification());
        }
    }

    @VisibleForTesting
    void onActivatedActionChanged() {
        if (!isShortcutEnabled()) {
            Slog.w(TAG, "Shortcut not enabled, skip onActivatedActionChanged()");
            return;
        }

        if (!isOneHandedEnabled()) {
            final boolean success = mOneHandedSettingsUtil.setOneHandedModeEnabled(
                    mContext.getContentResolver(), 1 /* Enabled for shortcut */, mUserId);
            Slog.d(TAG, "Auto enabled One-handed mode by shortcut trigger, success=" + success);
        }

        if (isSwipeToNotificationEnabled()) {
            notifyExpandNotification();
            return;
        }

        final boolean isActivated = mState.getState() == STATE_ACTIVE;
        final boolean requestActivated = mOneHandedSettingsUtil.getOneHandedModeActivated(
                mContext.getContentResolver(), mUserId);
        // When gesture trigger action, we will update settings and introduce observer callback
        // again, then the following logic will just ignore the second redundant callback.
        if (isActivated ^ requestActivated) {
            if (requestActivated) {
                startOneHanded();
            } else {
                stopOneHanded();
            }
        }
    }

    @VisibleForTesting
    void onEnabledSettingChanged() {
        final boolean enabled = mOneHandedSettingsUtil.getSettingsOneHandedModeEnabled(
                mContext.getContentResolver(), mUserId);
        mOneHandedUiEventLogger.writeEvent(enabled
                ? OneHandedUiEventLogger.EVENT_ONE_HANDED_SETTINGS_ENABLED_ON
                : OneHandedUiEventLogger.EVENT_ONE_HANDED_SETTINGS_ENABLED_OFF);

        setOneHandedEnabled(enabled);
    }

    @VisibleForTesting
    void onSwipeToNotificationEnabledChanged() {
        final boolean enabled =
                mOneHandedSettingsUtil.getSettingsSwipeToNotificationEnabled(
                        mContext.getContentResolver(), mUserId);
        setSwipeToNotificationEnabled(enabled);
        notifyShortcutStateChanged(mState.getState());

        mOneHandedUiEventLogger.writeEvent(enabled
                ? OneHandedUiEventLogger.EVENT_ONE_HANDED_SETTINGS_SHOW_NOTIFICATION_ENABLED_ON
                : OneHandedUiEventLogger.EVENT_ONE_HANDED_SETTINGS_SHOW_NOTIFICATION_ENABLED_OFF);
    }

    void onShortcutEnabledChanged() {
        mIsShortcutEnabled = mOneHandedSettingsUtil.getShortcutEnabled(
                mContext.getContentResolver(), mUserId);

        mOneHandedUiEventLogger.writeEvent(mIsShortcutEnabled
                ? OneHandedUiEventLogger.EVENT_ONE_HANDED_SETTINGS_SHORTCUT_ENABLED_ON
                : OneHandedUiEventLogger.EVENT_ONE_HANDED_SETTINGS_SHORTCUT_ENABLED_OFF);
    }

    private void setupTimeoutListener() {
        mTimeoutHandler.registerTimeoutListener(timeoutTime -> stopOneHanded(
                OneHandedUiEventLogger.EVENT_ONE_HANDED_TRIGGER_TIMEOUT_OUT));
    }

    @VisibleForTesting
    boolean isLockedDisabled() {
        return mLockedDisabled;
    }

    @VisibleForTesting
    boolean isOneHandedEnabled() {
        return mIsOneHandedEnabled;
    }

    @VisibleForTesting
    boolean isShortcutEnabled() {
        return mIsShortcutEnabled;
    }

    @VisibleForTesting
    boolean isSwipeToNotificationEnabled() {
        return mIsSwipeToNotificationEnabled;
    }

    private void updateOneHandedEnabled() {
        if (mState.getState() == STATE_ENTERING || mState.getState() == STATE_ACTIVE) {
            mMainExecutor.execute(() -> stopOneHanded());
        }

        // If setting is pull screen, notify shortcut one_handed_mode_activated to reset
        // and align status with current mState when one-handed gesture enabled.
        if (isOneHandedEnabled() && !isSwipeToNotificationEnabled()) {
            notifyShortcutStateChanged(mState.getState());
        }

        mTouchHandler.onOneHandedEnabled(mIsOneHandedEnabled);

        if (!mIsOneHandedEnabled) {
            mDisplayAreaOrganizer.unregisterOrganizer();
            // Do NOT register + unRegister DA in the same call
            return;
        }

        if (mDisplayAreaOrganizer.getDisplayAreaTokenMap().isEmpty()) {
            mDisplayAreaOrganizer.registerOrganizer(
                    OneHandedDisplayAreaOrganizer.FEATURE_ONE_HANDED);
        }
    }

    @VisibleForTesting
    void setLockedDisabled(boolean locked, boolean enabled) {
        final boolean isFeatureEnabled = mIsOneHandedEnabled || mIsSwipeToNotificationEnabled;

        if (enabled == isFeatureEnabled) {
            return;
        }

        mLockedDisabled = locked && !enabled;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (mTutorialHandler == null) {
            return;
        }
        if (!mIsOneHandedEnabled || newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            return;
        }
        mTutorialHandler.onConfigurationChanged();
    }

    @Override
    public void onKeyguardVisibilityChanged(boolean visible, boolean occluded,
            boolean animatingDismiss) {
        mKeyguardShowing = visible;
        stopOneHanded();
    }

    @Override
    public void onUserChanged(int newUserId, @NonNull Context userContext) {
        unregisterSettingObservers();
        mUserId = newUserId;
        registerSettingObservers(newUserId);
        updateSettings();
        updateOneHandedEnabled();
    }

    public void dump(@NonNull PrintWriter pw, String prefix) {
        final String innerPrefix = "  ";
        pw.println();
        pw.println(TAG);
        pw.print(innerPrefix + "mOffSetFraction=");
        pw.println(mOffSetFraction);
        pw.print(innerPrefix + "mLockedDisabled=");
        pw.println(mLockedDisabled);
        pw.print(innerPrefix + "mUserId=");
        pw.println(mUserId);
        pw.print(innerPrefix + "isShortcutEnabled=");
        pw.println(isShortcutEnabled());
        pw.print(innerPrefix + "mIsSwipeToNotificationEnabled=");
        pw.println(mIsSwipeToNotificationEnabled);

        if (mDisplayAreaOrganizer != null) {
            mDisplayAreaOrganizer.dump(pw);
        }

        if (mTouchHandler != null) {
            mTouchHandler.dump(pw);
        }

        if (mTimeoutHandler != null) {
            mTimeoutHandler.dump(pw);
        }

        if (mState != null) {
            mState.dump(pw);
        }

        if (mTutorialHandler != null) {
            mTutorialHandler.dump(pw);
        }

        if (mOneHandedAccessibilityUtil != null) {
            mOneHandedAccessibilityUtil.dump(pw);
        }

        mOneHandedSettingsUtil.dump(pw, innerPrefix, mContext.getContentResolver(), mUserId);
    }

    /**
     * Handles display change based on OnDisplayChangingListener callback
     */
    @Override
    public void onDisplayChange(int displayId, int fromRotation, int toRotation,
            DisplayAreaInfo newDisplayAreaInfo, WindowContainerTransaction wct) {
        if (!isInitialized()) {
            return;
        }

        if (!mOneHandedSettingsUtil.getSettingsOneHandedModeEnabled(mContext.getContentResolver(),
                mUserId) || mOneHandedSettingsUtil.getSettingsSwipeToNotificationEnabled(
                mContext.getContentResolver(), mUserId)) {
            return;
        }

        if (mState.getState() == STATE_ACTIVE) {
            mOneHandedUiEventLogger.writeEvent(
                    OneHandedUiEventLogger.EVENT_ONE_HANDED_TRIGGER_ROTATION_OUT);
        }

        mDisplayAreaOrganizer.onRotateDisplay(mContext, toRotation, wct);
    }

    /**
     * The interface for calls from outside the Shell, within the host process.
     */
    @ExternalThread
    private class OneHandedImpl implements OneHanded {
        @Override
        public void startOneHanded() {
            mMainExecutor.execute(() -> {
                OneHandedController.this.startOneHanded();
            });
        }

        @Override
        public void stopOneHanded() {
            mMainExecutor.execute(() -> {
                OneHandedController.this.stopOneHanded();
            });
        }

        @Override
        public void stopOneHanded(int event) {
            mMainExecutor.execute(() -> {
                OneHandedController.this.stopOneHanded(event);
            });
        }

        @Override
        public void setLockedDisabled(boolean locked, boolean enabled) {
            mMainExecutor.execute(() -> {
                OneHandedController.this.setLockedDisabled(locked, enabled);
            });
        }

        @Override
        public void registerEventCallback(OneHandedEventCallback callback) {
            mMainExecutor.execute(() -> {
                OneHandedController.this.registerEventCallback(callback);
            });
        }

        @Override
        public void registerTransitionCallback(OneHandedTransitionCallback callback) {
            mMainExecutor.execute(() -> {
                OneHandedController.this.registerTransitionCallback(callback);
            });
        }
    }

    /**
     * The interface for calls from outside the host process.
     */
    @BinderThread
    private static class IOneHandedImpl extends IOneHanded.Stub implements ExternalInterfaceBinder {
        private OneHandedController mController;

        IOneHandedImpl(OneHandedController controller) {
            mController = controller;
        }

        /**
         * Invalidates this instance, preventing future calls from updating the controller.
         */
        @Override
        public void invalidate() {
            mController = null;
        }

        @Override
        public void startOneHanded() {
            executeRemoteCallWithTaskPermission(mController, "startOneHanded",
                    (controller) -> {
                        controller.startOneHanded();
                    });
        }

        @Override
        public void stopOneHanded() {
            executeRemoteCallWithTaskPermission(mController, "stopOneHanded",
                    (controller) -> {
                        controller.stopOneHanded();
                    });
        }
    }
}
