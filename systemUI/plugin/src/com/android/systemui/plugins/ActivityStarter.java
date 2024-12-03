/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.plugins;

import android.annotation.Nullable;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.view.View;

import com.android.systemui.animation.ActivityTransitionAnimator;
import com.android.systemui.plugins.annotations.ProvidesInterface;

/**
 * An interface to start activities. This is used as a callback from the views to
 * {@link PhoneStatusBar} to allow custom handling for starting the activity, i.e. dismissing the
 * Keyguard.
 */
@ProvidesInterface(version = ActivityStarter.VERSION)
public interface ActivityStarter {
    int VERSION = 2;

    void startPendingIntentDismissingKeyguard(PendingIntent intent);

    /**
     * Similar to {@link #startPendingIntentDismissingKeyguard(PendingIntent)}, but allows
     * you to specify the callback that is executed on the UI thread after the intent is sent.
     */
    void startPendingIntentDismissingKeyguard(PendingIntent intent,
            Runnable intentSentUiThreadCallback);

    /**
     * Similar to {@link #startPendingIntentDismissingKeyguard(PendingIntent, Runnable)}, but also
     * specifies an associated view that should be used for the activity launch animation.
     */
    void startPendingIntentDismissingKeyguard(PendingIntent intent,
            Runnable intentSentUiThreadCallback, @Nullable View associatedView);

    /**
     * Similar to {@link #startPendingIntentDismissingKeyguard(PendingIntent, Runnable)}, but also
     * specifies an animation controller that should be used for the activity launch animation.
     */
    void startPendingIntentDismissingKeyguard(PendingIntent intent,
            Runnable intentSentUiThreadCallback,
            @Nullable ActivityTransitionAnimator.Controller animationController);

    /**
     * Similar to {@link #startPendingIntentMaybeDismissingKeyguard(PendingIntent, Runnable,
     * ActivityTransitionAnimator.Controller)} but will always not dismiss the keyguard when
     * launching activities. This should be avoided and other alternatives should be used.
     */
    void startPendingIntentWithoutDismissing(
            PendingIntent intent,
            boolean dismissShade,
            Runnable intentSentUiThreadCallback,
            @Nullable ActivityTransitionAnimator.Controller animationController,
            @Nullable Intent fillInIntent,
            @Nullable Bundle extraOptions);

    /**
     * Similar to {@link #startPendingIntentDismissingKeyguard}, except that it supports launching
     * activities on top of the keyguard. If the activity supports {@code showOverLockscreen}, it
     * will show over keyguard without first dimissing it. If it doesn't support it, calling this
     * method is exactly the same as calling {@link #startPendingIntentDismissingKeyguard}.
     */
    void startPendingIntentMaybeDismissingKeyguard(PendingIntent intent,
            @Nullable Runnable intentSentUiThreadCallback,
            @Nullable ActivityTransitionAnimator.Controller animationController);

    /**
     * Similar to {@link #startPendingIntentMaybeDismissingKeyguard(PendingIntent, Runnable,
     * ActivityTransitionAnimator.Controller)}, but also specifies a fill-in intent and extra
     * option that could be used to populate the pending intent and launch the activity. This also
     * allows the caller to avoid dismissing the shade.
     */
    void startPendingIntentMaybeDismissingKeyguard(PendingIntent intent,
            boolean dismissShade,
            @Nullable Runnable intentSentUiThreadCallback,
            @Nullable ActivityTransitionAnimator.Controller animationController,
            @Nullable Intent fillInIntent,
            @Nullable Bundle extraOptions);

    /**
     * The intent flag can be specified in startActivity().
     */
    void startActivity(Intent intent, boolean onlyProvisioned, boolean dismissShade, int flags);
    void startActivity(Intent intent, boolean dismissShade);
    default void startActivity(Intent intent, boolean dismissShade,
            @Nullable ActivityTransitionAnimator.Controller animationController) {
        startActivity(intent, dismissShade, animationController,
                false /* showOverLockscreenWhenLocked */);
    }

    void startActivity(Intent intent, boolean dismissShade,
            @Nullable ActivityTransitionAnimator.Controller animationController,
            boolean showOverLockscreenWhenLocked);
    void startActivity(Intent intent, boolean dismissShade,
            @Nullable ActivityTransitionAnimator.Controller animationController,
            boolean showOverLockscreenWhenLocked, UserHandle userHandle);
    void startActivity(Intent intent, boolean onlyProvisioned, boolean dismissShade);
    void startActivity(Intent intent, boolean dismissShade, Callback callback);
    void postStartActivityDismissingKeyguard(Intent intent, int delay);
    void postStartActivityDismissingKeyguard(Intent intent, int delay,
            @Nullable ActivityTransitionAnimator.Controller animationController);

    /** Posts a start activity intent that dismisses keyguard. */
    void postStartActivityDismissingKeyguard(Intent intent, int delay,
            @Nullable ActivityTransitionAnimator.Controller animationController,
            @Nullable String customMessage);
    void postStartActivityDismissingKeyguard(PendingIntent intent);

    /**
     * Similar to {@link #postStartActivityDismissingKeyguard(PendingIntent)}, but also specifies an
     * animation controller that should be used for the activity launch animation.
     */
    void postStartActivityDismissingKeyguard(PendingIntent intent,
            @Nullable ActivityTransitionAnimator.Controller animationController);

    void postQSRunnableDismissingKeyguard(Runnable runnable);

    void dismissKeyguardThenExecute(OnDismissAction action, @Nullable Runnable cancel,
            boolean afterKeyguardGone);

    /** Authenticates if needed and dismisses keyguard to execute an action. */
    void dismissKeyguardThenExecute(OnDismissAction action, @Nullable Runnable cancel,
            boolean afterKeyguardGone, @Nullable String customMessage);

    /** Starts an activity and dismisses keyguard. */
    void startActivityDismissingKeyguard(Intent intent,
            boolean onlyProvisioned,
            boolean dismissShade);

    /** Starts an activity and dismisses keyguard. */
    void startActivityDismissingKeyguard(Intent intent,
            boolean onlyProvisioned,
            boolean dismissShade,
            boolean disallowEnterPictureInPictureWhileLaunching,
            Callback callback,
            int flags,
            @Nullable ActivityTransitionAnimator.Controller animationController,
            UserHandle userHandle);

    /** Execute a runnable after dismissing keyguard. */
    void executeRunnableDismissingKeyguard(Runnable runnable,
            Runnable cancelAction,
            boolean dismissShade,
            boolean afterKeyguardGone,
            boolean deferred);

    /** Whether we should animate an activity launch. */
    boolean shouldAnimateLaunch(boolean isActivityIntent);

    interface Callback {
        void onActivityStarted(int resultCode);
    }

    interface OnDismissAction {
        /**
         * @return {@code true} if the dismiss should be deferred. When returning true, make sure to
         *         call {@link com.android.keyguard.ViewMediatorCallback#readyForKeyguardDone()}
         *         *after* returning to start hiding the keyguard.
         */
        boolean onDismiss();

        /**
         * Whether running this action when we are locked will start an animation on the keyguard.
         */
        default boolean willRunAnimationOnKeyguard() {
            return false;
        }
    }
}
