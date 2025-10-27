/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.systemui.shared.rotation;

import static android.content.pm.PackageManager.FEATURE_PC;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.internal.view.RotationPolicy.NATURAL_ROTATION;
import static com.android.systemui.shared.system.QuickStepContract.isGesturalMode;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.ColorInt;
import android.annotation.DrawableRes;
import android.annotation.SuppressLint;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.IRotationWatcher;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManagerGlobal;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.logging.UiEvent;
import com.android.internal.logging.UiEventLogger;
import com.android.internal.logging.UiEventLoggerImpl;
import com.android.internal.view.RotationPolicy;
import com.android.systemui.shared.recents.utilities.Utilities;
import com.android.systemui.shared.recents.utilities.ViewRippler;
import com.android.systemui.shared.rotation.RotationButton.RotationButtonUpdatesCallback;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.TaskStackChangeListener;
import com.android.systemui.shared.system.TaskStackChangeListeners;

import java.io.PrintWriter;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Supplier;

/**
 * Contains logic that deals with showing a rotate suggestion button with animation.
 */
public class RotationButtonController {
    public static final boolean DEBUG_ROTATION = false;

    private static final String TAG = "RotationButtonController";
    private static final int BUTTON_FADE_IN_OUT_DURATION_MS = 100;
    private static final int NAVBAR_HIDDEN_PENDING_ICON_TIMEOUT_MS = 20000;
    private static final boolean OEM_DISALLOW_ROTATION_IN_SUW =
            SystemProperties.getBoolean("ro.setupwizard.rotation_locked", false);
    private static final Interpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

    private static final int NUM_ACCEPTED_ROTATION_SUGGESTIONS_FOR_INTRODUCTION = 3;

    private final Context mContext;
    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());
    private final UiEventLogger mUiEventLogger = new UiEventLoggerImpl();
    private final ViewRippler mViewRippler = new ViewRippler();
    private final Supplier<Integer> mWindowRotationProvider;
    private RotationButton mRotationButton;

    private boolean mIsRecentsAnimationRunning;
    private boolean mDocked;
    private boolean mHomeRotationEnabled;
    private int mLastRotationSuggestion;
    private boolean mPendingRotationSuggestion;
    private boolean mHoveringRotationSuggestion;
    private final AccessibilityManager mAccessibilityManager;
    private final TaskStackListenerImpl mTaskStackListener;

    private boolean mListenersRegistered = false;
    private boolean mRotationWatcherRegistered = false;
    private boolean mIsNavigationBarShowing;
    @SuppressLint("InlinedApi")
    private @WindowInsetsController.Behavior
    int mBehavior = WindowInsetsController.BEHAVIOR_DEFAULT;
    private int mNavBarMode;
    private boolean mTaskBarVisible = false;
    private boolean mSkipOverrideUserLockPrefsOnce;
    private final int mLightIconColor;
    private final int mDarkIconColor;

    @DrawableRes
    private final int mIconCcwStart0ResId;
    @DrawableRes
    private final int mIconCcwStart90ResId;
    @DrawableRes
    private final int mIconCwStart0ResId;
    @DrawableRes
    private final int mIconCwStart90ResId;
    /** Defaults to mainExecutor if not set via {@link #setBgExecutor(Executor)}. */
    private Executor mBgExecutor;

    @DrawableRes
    private int mIconResId;

    private final Runnable mRemoveRotationProposal =
            () -> setRotateSuggestionButtonState(false /* visible */);
    private final Runnable mCancelPendingRotationProposal =
            () -> mPendingRotationSuggestion = false;
    private Animator mRotateHideAnimator;

    private final BroadcastReceiver mDockedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateDockedState(intent);
        }
    };

    private final IRotationWatcher.Stub mRotationWatcher = new IRotationWatcher.Stub() {
        @WorkerThread
        @Override
        public void onRotationChanged(final int rotation) {
            @Nullable Boolean rotationLocked = RotationPolicyUtil.isRotationLocked(mContext);
            // We need this to be scheduled as early as possible to beat the redrawing of
            // window in response to the orientation change.
            mMainThreadHandler.postAtFrontOfQueue(() -> {
                onRotationWatcherChanged(rotation, rotationLocked);
            });
        }
    };

    /**
     * Determines if rotation suggestions disabled2 flag exists in flag
     *
     * @param disable2Flags see if rotation suggestion flag exists in this flag
     * @return whether flag exists
     */
    public static boolean hasDisable2RotateSuggestionFlag(int disable2Flags) {
        return (disable2Flags & StatusBarManager.DISABLE2_ROTATE_SUGGESTIONS) != 0;
    }

    public RotationButtonController(Context context,
        @ColorInt int lightIconColor, @ColorInt int darkIconColor,
        @DrawableRes int iconCcwStart0ResId,
        @DrawableRes int iconCcwStart90ResId,
        @DrawableRes int iconCwStart0ResId,
        @DrawableRes int iconCwStart90ResId,
        Supplier<Integer> windowRotationProvider) {

        mContext = context;
        mLightIconColor = lightIconColor;
        mDarkIconColor = darkIconColor;

        mIconCcwStart0ResId = iconCcwStart0ResId;
        mIconCcwStart90ResId = iconCcwStart90ResId;
        mIconCwStart0ResId = iconCwStart0ResId;
        mIconCwStart90ResId = iconCwStart90ResId;
        mIconResId = mIconCcwStart90ResId;

        mAccessibilityManager = AccessibilityManager.getInstance(context);
        mTaskStackListener = new TaskStackListenerImpl();
        mWindowRotationProvider = windowRotationProvider;

        mBgExecutor = context.getMainExecutor();
    }

    public void setRotationButton(RotationButton rotationButton,
                                  RotationButtonUpdatesCallback updatesCallback) {
        mRotationButton = rotationButton;
        mRotationButton.setRotationButtonController(this);
        mRotationButton.setOnClickListener(this::onRotateSuggestionClick);
        mRotationButton.setOnHoverListener(this::onRotateSuggestionHover);
        mRotationButton.setUpdatesCallback(updatesCallback);
    }

    public Context getContext() {
        return mContext;
    }

    /**
     * We should pass single threaded executor (rather than {@link ThreadPoolExecutor}) as we will
     * make binder calls on that executor and ordering is vital.
     */
    public void setBgExecutor(Executor bgExecutor) {
        mBgExecutor = bgExecutor;
    }

    /**
     * Called during Taskbar initialization.
     */
    public void init() {
        registerListeners(true /* registerRotationWatcher */);
        if (mContext.getDisplay().getDisplayId() != DEFAULT_DISPLAY) {
            // Currently there is no accelerometer sensor on non-default display, disable fixed
            // rotation for non-default display
            onDisable2FlagChanged(StatusBarManager.DISABLE2_ROTATE_SUGGESTIONS);
        }
    }

    /**
     * Called during Taskbar uninitialization.
     */
    public void onDestroy() {
        unregisterListeners();
    }

    public void registerListeners(boolean registerRotationWatcher) {
        if (mListenersRegistered || getContext().getPackageManager().hasSystemFeature(FEATURE_PC)) {
            return;
        }

        mListenersRegistered = true;

        mBgExecutor.execute(() -> {
            if (registerRotationWatcher) {
                try {
                    WindowManagerGlobal.getWindowManagerService()
                            .watchRotation(mRotationWatcher, DEFAULT_DISPLAY);
                    mRotationWatcherRegistered = true;
                } catch (IllegalArgumentException e) {
                    Log.w(TAG, "RegisterListeners for the display failed", e);
                } catch (RemoteException e) {
                    Log.e(TAG, "RegisterListeners caught a RemoteException", e);
                }
            }
            final Intent intent = mContext.registerReceiver(mDockedReceiver,
                    new IntentFilter(Intent.ACTION_DOCK_EVENT));
            mContext.getMainExecutor().execute(() -> updateDockedState(intent));
        });

        TaskStackChangeListeners.getInstance().registerTaskStackListener(mTaskStackListener);
    }

    public void unregisterListeners() {
        if (!mListenersRegistered) {
            return;
        }

        mListenersRegistered = false;

        mBgExecutor.execute(() -> {
            try {
                mContext.unregisterReceiver(mDockedReceiver);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Docked receiver already unregistered", e);
            }

            if (mRotationWatcherRegistered) {
                try {
                    WindowManagerGlobal.getWindowManagerService().removeRotationWatcher(
                            mRotationWatcher);
                } catch (RemoteException e) {
                    Log.e(TAG, "UnregisterListeners caught a RemoteException", e);
                }
            }
        });

        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(mTaskStackListener);
    }

    public void setRotationLockedAtAngle(
            @Nullable Boolean isLocked, int rotationSuggestion, String caller) {
        if (isLocked == null) {
            // Ignore if we can't read the setting for the current user
            return;
        }
        RotationPolicy.setRotationLockAtAngle(mContext, /* enabled= */ isLocked,
                /* rotation= */ rotationSuggestion, caller);
    }

    public void setRotateSuggestionButtonState(boolean visible) {
        setRotateSuggestionButtonState(visible, false /* force */);
    }

    void setRotateSuggestionButtonState(final boolean visible, final boolean force) {
        // At any point the button can become invisible because an a11y service became active.
        // Similarly, a call to make the button visible may be rejected because an a11y service is
        // active. Must account for this.
        // Rerun a show animation to indicate change but don't rerun a hide animation
        if (!visible && !mRotationButton.isVisible()) return;

        final View view = mRotationButton.getCurrentView();
        if (view == null) return;

        final Drawable currentDrawable = mRotationButton.getImageDrawable();
        if (currentDrawable == null) return;

        // Clear any pending suggestion flag as it has either been nullified or is being shown
        mPendingRotationSuggestion = false;
        mMainThreadHandler.removeCallbacks(mCancelPendingRotationProposal);

        // Handle the visibility change and animation
        if (visible) { // Appear and change (cannot force)
            // Stop and clear any currently running hide animations
            if (mRotateHideAnimator != null && mRotateHideAnimator.isRunning()) {
                mRotateHideAnimator.cancel();
            }
            mRotateHideAnimator = null;

            // Reset the alpha if any has changed due to hide animation
            view.setAlpha(1f);

            // Run the rotate icon's animation if it has one
            if (currentDrawable instanceof AnimatedVectorDrawable) {
                ((AnimatedVectorDrawable) currentDrawable).reset();
                ((AnimatedVectorDrawable) currentDrawable).start();
            }

            // TODO(b/187754252): No idea why this doesn't work. If we remove the "false"
            //  we see the animation show the pressed state... but it only shows the first time.
            if (!isRotateSuggestionIntroduced()) mViewRippler.start(view);

            // Set visibility unless a11y service is active.
            mRotationButton.show();
        } else { // Hide
            mViewRippler.stop(); // Prevent any pending ripples, force hide or not

            if (force) {
                // If a hide animator is running stop it and make invisible
                if (mRotateHideAnimator != null && mRotateHideAnimator.isRunning()) {
                    mRotateHideAnimator.pause();
                }
                mRotationButton.hide();
                return;
            }

            // Don't start any new hide animations if one is running
            if (mRotateHideAnimator != null && mRotateHideAnimator.isRunning()) return;

            ObjectAnimator fadeOut = ObjectAnimator.ofFloat(view, "alpha", 0f);
            fadeOut.setDuration(BUTTON_FADE_IN_OUT_DURATION_MS);
            fadeOut.setInterpolator(LINEAR_INTERPOLATOR);
            fadeOut.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mRotationButton.hide();
                }
            });

            mRotateHideAnimator = fadeOut;
            fadeOut.start();
        }
    }

    public void setDarkIntensity(float darkIntensity) {
        mRotationButton.setDarkIntensity(darkIntensity);
    }

    public void setRecentsAnimationRunning(boolean running) {
        mIsRecentsAnimationRunning = running;
        updateRotationButtonStateInOverview();
    }

    public void setHomeRotationEnabled(boolean enabled) {
        mHomeRotationEnabled = enabled;
        updateRotationButtonStateInOverview();
    }

    private void updateDockedState(Intent intent) {
        if (intent == null) {
            return;
        }

        mDocked = intent.getIntExtra(Intent.EXTRA_DOCK_STATE, Intent.EXTRA_DOCK_STATE_UNDOCKED)
                != Intent.EXTRA_DOCK_STATE_UNDOCKED;
    }

    private void updateRotationButtonStateInOverview() {
        if (mIsRecentsAnimationRunning && !mHomeRotationEnabled) {
            setRotateSuggestionButtonState(false, true /* hideImmediately */);
        }
    }

    public void onRotationProposal(int rotation, boolean isValid) {
        boolean isUserSetupComplete = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0) != 0;
        if (!isUserSetupComplete && OEM_DISALLOW_ROTATION_IN_SUW) {
            return;
        }

        int windowRotation = mWindowRotationProvider.get();

        if (!mRotationButton.acceptRotationProposal()) {
            return;
        }

        if (!mHomeRotationEnabled && mIsRecentsAnimationRunning) {
            return;
        }

        // This method will be called on rotation suggestion changes even if the proposed rotation
        // is not valid for the top app. Use invalid rotation choices as a signal to remove the
        // rotate button if shown.
        if (!isValid) {
            setRotateSuggestionButtonState(false /* visible */);
            return;
        }

        // If window rotation matches suggested rotation, remove any current suggestions
        if (rotation == windowRotation) {
            mMainThreadHandler.removeCallbacks(mRemoveRotationProposal);
            setRotateSuggestionButtonState(false /* visible */);
            return;
        }

        // Prepare to show the navbar icon by updating the icon style to change anim params
        Log.i(TAG, "onRotationProposal(rotation=" + rotation + ")");
        mLastRotationSuggestion = rotation; // Remember rotation for click
        final boolean rotationCCW = Utilities.isRotationAnimationCCW(windowRotation, rotation);
        if (windowRotation == Surface.ROTATION_0 || windowRotation == Surface.ROTATION_180) {
            mIconResId = rotationCCW ? mIconCcwStart0ResId : mIconCwStart0ResId;
        } else { // 90 or 270
            mIconResId = rotationCCW ? mIconCcwStart90ResId : mIconCwStart90ResId;
        }
        mRotationButton.updateIcon(mLightIconColor, mDarkIconColor);

        if (canShowRotationButton()) {
            // The navbar is visible / it's in visual immersive mode, so show the icon right away
            showAndLogRotationSuggestion();
        } else {
            // If the navbar isn't shown, flag the rotate icon to be shown should the navbar become
            // visible given some time limit.
            mPendingRotationSuggestion = true;
            mMainThreadHandler.removeCallbacks(mCancelPendingRotationProposal);
            mMainThreadHandler.postDelayed(mCancelPendingRotationProposal,
                    NAVBAR_HIDDEN_PENDING_ICON_TIMEOUT_MS);
        }
    }

    /**
     * Called when the rotation watcher rotation changes, either from the watcher registered
     * internally in this class, or a signal propagated from NavBarHelper.
     */
    public void onRotationWatcherChanged(int rotation, @Nullable Boolean isRotationLocked) {
        if (!mListenersRegistered) {
            // Ignore if not registered
            return;
        }

        // If the screen rotation changes while locked, potentially update lock to flow with
        // new screen rotation and hide any showing suggestions.
        if (isRotationLocked == null) {
            // Ignore if we can't read the setting for the current user
            return;
        }
        // The isVisible check makes the rotation button disappear when we are not locked
        // (e.g. for tabletop auto-rotate).
        if (isRotationLocked || mRotationButton.isVisible()) {
            // Do not allow a change in rotation to set user rotation when docked.
            if (shouldOverrideUserLockPrefs(rotation) && isRotationLocked && !mDocked) {
                setRotationLockedAtAngle(true, rotation, /* caller= */
                        "RotationButtonController#onRotationWatcherChanged");
            }
            setRotateSuggestionButtonState(false /* visible */, true /* forced */);
        }
    }

    public void onDisable2FlagChanged(int state2) {
        final boolean rotateSuggestionsDisabled = hasDisable2RotateSuggestionFlag(state2);
        if (rotateSuggestionsDisabled) onRotationSuggestionsDisabled();
    }

    public void onNavigationModeChanged(int mode) {
        mNavBarMode = mode;
    }

    public void onBehaviorChanged(int displayId, @WindowInsetsController.Behavior int behavior) {
        if (DEFAULT_DISPLAY != displayId) {
            return;
        }

        if (mBehavior != behavior) {
            mBehavior = behavior;
            showPendingRotationButtonIfNeeded();
        }
    }

    public void onNavigationBarWindowVisibilityChange(boolean showing) {
        if (mIsNavigationBarShowing != showing) {
            mIsNavigationBarShowing = showing;
            showPendingRotationButtonIfNeeded();
        }
    }

    public void onTaskbarStateChange(boolean visible, boolean stashed) {
        mTaskBarVisible = visible;
        if (getRotationButton() == null) {
            return;
        }
        getRotationButton().onTaskbarStateChanged(visible, stashed);
    }

    private void showPendingRotationButtonIfNeeded() {
        if (canShowRotationButton() && mPendingRotationSuggestion) {
            showAndLogRotationSuggestion();
        }
    }

    /**
     * Return true when either the task bar is visible or it's in visual immersive mode.
     */
    @SuppressLint("InlinedApi")
    @VisibleForTesting
    boolean canShowRotationButton() {
        return mIsNavigationBarShowing
            || mBehavior == WindowInsetsController.BEHAVIOR_DEFAULT
            || isGesturalMode(mNavBarMode);
    }

    @DrawableRes
    public int getIconResId() {
        return mIconResId;
    }

    @ColorInt
    public int getLightIconColor() {
        return mLightIconColor;
    }

    @ColorInt
    public int getDarkIconColor() {
        return mDarkIconColor;
    }

    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "RotationButtonController:");

        pw.println(String.format(
                "%s\tmIsRecentsAnimationRunning=%b", prefix, mIsRecentsAnimationRunning));
        pw.println(String.format("%s\tmHomeRotationEnabled=%b", prefix, mHomeRotationEnabled));
        pw.println(String.format(
                "%s\tmLastRotationSuggestion=%d", prefix, mLastRotationSuggestion));
        pw.println(String.format(
                "%s\tmPendingRotationSuggestion=%b", prefix, mPendingRotationSuggestion));
        pw.println(String.format(
                "%s\tmHoveringRotationSuggestion=%b", prefix, mHoveringRotationSuggestion));
        pw.println(String.format("%s\tmListenersRegistered=%b", prefix, mListenersRegistered));
        pw.println(String.format(
                "%s\tmIsNavigationBarShowing=%b", prefix, mIsNavigationBarShowing));
        pw.println(String.format("%s\tmBehavior=%d", prefix, mBehavior));
        pw.println(String.format(
                "%s\tmSkipOverrideUserLockPrefsOnce=%b", prefix, mSkipOverrideUserLockPrefsOnce));
        pw.println(String.format(
                "%s\tmLightIconColor=0x%s", prefix, Integer.toHexString(mLightIconColor)));
        pw.println(String.format(
                "%s\tmDarkIconColor=0x%s", prefix, Integer.toHexString(mDarkIconColor)));
    }

    public RotationButton getRotationButton() {
        return mRotationButton;
    }

    private void onRotateSuggestionClick(View v) {
        mUiEventLogger.log(RotationButtonEvent.ROTATION_SUGGESTION_ACCEPTED);
        incrementNumAcceptedRotationSuggestionsIfNeeded();
        setRotationLockedAtAngle(
                RotationPolicyUtil.isRotationLocked(mContext), mLastRotationSuggestion,
                /* caller= */ "RotationButtonController#onRotateSuggestionClick");
        Log.i(TAG, "onRotateSuggestionClick() mLastRotationSuggestion=" + mLastRotationSuggestion);
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private boolean onRotateSuggestionHover(View v, MotionEvent event) {
        final int action = event.getActionMasked();
        mHoveringRotationSuggestion = (action == MotionEvent.ACTION_HOVER_ENTER)
                || (action == MotionEvent.ACTION_HOVER_MOVE);
        rescheduleRotationTimeout(true /* reasonHover */);
        return false; // Must return false so a11y hover events are dispatched correctly.
    }

    private void onRotationSuggestionsDisabled() {
        // Immediately hide the rotate button and clear any planned removal
        setRotateSuggestionButtonState(false /* visible */, true /* force */);
        mMainThreadHandler.removeCallbacks(mRemoveRotationProposal);
    }

    private void showAndLogRotationSuggestion() {
        setRotateSuggestionButtonState(true /* visible */);
        rescheduleRotationTimeout(false /* reasonHover */);
        mUiEventLogger.log(RotationButtonEvent.ROTATION_SUGGESTION_SHOWN);
    }

    /**
     * Makes {@link #shouldOverrideUserLockPrefs} always return {@code false} once. It is used to
     * avoid losing original user rotation when display rotation is changed by entering the fixed
     * orientation overview.
     */
    public void setSkipOverrideUserLockPrefsOnce() {
        // If live-tile is enabled (recents animation keeps running in overview), there is no
        // activity switch so the display rotation is not changed, then it is no need to skip.
        mSkipOverrideUserLockPrefsOnce = !mIsRecentsAnimationRunning;
    }

    private boolean shouldOverrideUserLockPrefs(final int rotation) {
        if (mSkipOverrideUserLockPrefsOnce) {
            mSkipOverrideUserLockPrefsOnce = false;
            return false;
        }
        // Only override user prefs when returning to the natural rotation (normally portrait).
        // Don't let apps that force landscape or 180 alter user lock.
        return rotation == NATURAL_ROTATION;
    }

    private void rescheduleRotationTimeout(final boolean reasonHover) {
        // May be called due to a new rotation proposal or a change in hover state
        if (reasonHover) {
            // Don't reschedule if a hide animator is running
            if (mRotateHideAnimator != null && mRotateHideAnimator.isRunning()) return;
            // Don't reschedule if not visible
            if (!mRotationButton.isVisible()) return;
        }

        // Stop any pending removal
        mMainThreadHandler.removeCallbacks(mRemoveRotationProposal);
        // Schedule timeout
        mMainThreadHandler.postDelayed(mRemoveRotationProposal,
                computeRotationProposalTimeout());
    }

    private int computeRotationProposalTimeout() {
        return mAccessibilityManager.getRecommendedTimeoutMillis(
                mHoveringRotationSuggestion ? 16000 : 5000,
                AccessibilityManager.FLAG_CONTENT_CONTROLS);
    }

    private boolean isRotateSuggestionIntroduced() {
        ContentResolver cr = mContext.getContentResolver();
        return Settings.Secure.getInt(cr, Settings.Secure.NUM_ROTATION_SUGGESTIONS_ACCEPTED, 0)
                >= NUM_ACCEPTED_ROTATION_SUGGESTIONS_FOR_INTRODUCTION;
    }

    private void incrementNumAcceptedRotationSuggestionsIfNeeded() {
        // Get the number of accepted suggestions
        ContentResolver cr = mContext.getContentResolver();
        final int numSuggestions = Settings.Secure.getInt(cr,
                Settings.Secure.NUM_ROTATION_SUGGESTIONS_ACCEPTED, 0);

        // Increment the number of accepted suggestions only if it would change intro mode
        if (numSuggestions < NUM_ACCEPTED_ROTATION_SUGGESTIONS_FOR_INTRODUCTION) {
            Settings.Secure.putInt(cr, Settings.Secure.NUM_ROTATION_SUGGESTIONS_ACCEPTED,
                    numSuggestions + 1);
        }
    }

    private class TaskStackListenerImpl implements TaskStackChangeListener {
        // Invalidate any rotation suggestion on task change or activity orientation change
        // Note: all callbacks happen on main thread

        @Override
        public void onTaskStackChanged() {
            setRotateSuggestionButtonState(false /* visible */);
        }

        @Override
        public void onTaskRemoved(int taskId) {
            setRotateSuggestionButtonState(false /* visible */);
        }

        @Override
        public void onTaskMovedToFront(int taskId) {
            setRotateSuggestionButtonState(false /* visible */);
        }

        @Override
        public void onActivityRequestedOrientationChanged(int taskId, int requestedOrientation) {
            mBgExecutor.execute(() -> {
                // Only hide the icon if the top task changes its requestedOrientation Launcher can
                // alter its requestedOrientation while it's not on top, don't hide on this
                Optional.ofNullable(ActivityManagerWrapper.getInstance())
                        .map(ActivityManagerWrapper::getRunningTask)
                        .ifPresent(a -> {
                            if (a.id == taskId) {
                                mMainThreadHandler.post(() ->
                                        setRotateSuggestionButtonState(false /* visible */));
                            }
                        });
            });
        }
    }

    enum RotationButtonEvent implements UiEventLogger.UiEventEnum {
        @UiEvent(doc = "The rotation button was shown")
        ROTATION_SUGGESTION_SHOWN(206),
        @UiEvent(doc = "The rotation button was clicked")
        ROTATION_SUGGESTION_ACCEPTED(207);

        private final int mId;

        RotationButtonEvent(int id) {
            mId = id;
        }

        @Override
        public int getId() {
            return mId;
        }
    }
}
