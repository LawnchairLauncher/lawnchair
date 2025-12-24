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

package com.android.systemui.animation;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityOptions;
import android.app.ActivityOptions.LaunchCookie;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.window.IRemoteTransition;
import android.window.RemoteTransition;
import android.window.TransitionFilter;

import com.android.systemui.animation.OriginRemoteTransition.TransitionPlayer;
import com.android.systemui.animation.shared.IOriginTransitions;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A session object that holds origin transition states for starting an activity from an on-screen
 * UI component and smoothly transitioning back from the activity to the same UI component.
 *
 * @hide
 */
public class OriginTransitionSession {
    private static final String TAG = "OriginTransitionSession";
    static final boolean DEBUG = Build.IS_USERDEBUG || Log.isLoggable(TAG, Log.DEBUG);

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {NOT_STARTED, STARTED, CANCELLED})
    private @interface State {}

    @State private static final int NOT_STARTED = 0;
    @State private static final int STARTED = 1;
    @State private static final int CANCELLED = 5;

    private final String mName;
    @Nullable private final IOriginTransitions mOriginTransitions;
    private final Predicate<RemoteTransition> mIntentStarter;
    @Nullable private final IRemoteTransition mEntryTransition;
    @NonNull private final Map<IRemoteTransition, TransitionFilter> mExitTransitionMap;
    private final AtomicInteger mState = new AtomicInteger(NOT_STARTED);
    @Nullable private RemoteTransition mOriginTransition;

    private OriginTransitionSession(
            String name,
            @Nullable IOriginTransitions originTransitions,
            Predicate<RemoteTransition> intentStarter,
            @Nullable IRemoteTransition entryTransition,
            @NonNull Map<IRemoteTransition, TransitionFilter> exitTransitionMap) {
        mName = name;
        mOriginTransitions = originTransitions;
        mIntentStarter = intentStarter;
        mEntryTransition = entryTransition;
        mExitTransitionMap = exitTransitionMap;
        if (hasExitTransition() && !hasEntryTransition()) {
            throw new IllegalArgumentException(
                    "Entry transition must be supplied if you want to play an exit transition!");
        }
    }

    /**
     * Launch the target intent with the supplied entry transition. After this method, the entry
     * transition is expected to receive callbacks. The exit transition will be registered and
     * triggered when the system detects a return from the launched activity to the launching
     * activity.
     */
    public boolean start() {
        if (!mState.compareAndSet(NOT_STARTED, STARTED)) {
            logE("start: illegal state - " + stateToString(mState.get()));
            return false;
        }

        RemoteTransition remoteTransition = null;
        if (hasEntryTransition() && hasExitTransition()) {
            logD("start: starting with entry and exit transition.");
            try {
                if (!mExitTransitionMap.isEmpty()) {
                    int index = 0;
                    final List<RemoteTransition> transitions = new ArrayList<>();
                    final List<TransitionFilter> filters = new ArrayList<>();
                    for (Map.Entry<IRemoteTransition, TransitionFilter> entry
                            : mExitTransitionMap.entrySet()) {
                        transitions.add(new RemoteTransition(
                                entry.getKey(), mName + "-exit:" + index));
                        filters.add(entry.getValue());
                        logD("mapping exit transition[" + index + "]: "
                                + entry.getKey() + " and filter: " + entry.getValue());
                        index++;
                    }
                    remoteTransition =
                            mOriginTransition =
                                    mOriginTransitions.makeOriginTransitionWithReturnFilters(
                                        new RemoteTransition(mEntryTransition, mName + "-entry"),
                                        transitions,
                                        filters);
                }
            } catch (Exception e) {
                logE("Unable to create origin transition!", e);
            }
        }
        if (remoteTransition == null && hasEntryTransition()) {
            // If we failed to create a full origin transition (entry + exit), fallback to use the
            // entry transition only.
            logD("start: starting with entry transition.");
            remoteTransition = new RemoteTransition(mEntryTransition, mName + "-entry");
        }

        if (remoteTransition == null) {
            // If both entry and exit transitions are not provided, we will fallback to start the
            // activity without transition.
            logD("start: starting without transition.");
        }

        try {
            if (mIntentStarter.test(remoteTransition)) {
                logD("start: intent launched!");
                if (remoteTransition != null) {
                    // If the intent is successfully launched with a remote transition, setup the
                    // transaction queues for the entry and exit transitions.
                    setupTransactionQueues();
                }
                return true;
            } else {
                // Animation is cancelled by intent starter.
                logD("start: cancelled by intent starter!");
            }
        } catch (Exception e) {
            logE("Unable to launch intent!", e);
        }
        // Cancel the session since the intent was not launched.
        cancel();
        return false;
    }

    /**
     * Cancel the current transition and the registered exit transition if it exists. After this
     * method, this session object can no longer be used. Clients need to create a new session
     * object if they want to launch another intent with origin transition.
     */
    public void cancel() {
        final int lastState = mState.getAndSet(CANCELLED);
        if (lastState == CANCELLED || lastState == NOT_STARTED) {
            return;
        }
        logD("cancel: cancelled transition. Last state: " + stateToString(lastState));
        if (mOriginTransition != null) {
            try {
                mOriginTransitions.cancelOriginTransition(mOriginTransition);
                mOriginTransition = null;
            } catch (RemoteException e) {
                logE("Unable to cancel origin transition!", e);
            }
        }
        if (mEntryTransition instanceof OriginRemoteTransition) {
            ((OriginRemoteTransition) mEntryTransition).cancel();
        }
        for (IRemoteTransition transition : mExitTransitionMap.keySet()) {
            if (transition instanceof OriginRemoteTransition) {
                ((OriginRemoteTransition) transition).cancel();
            }
        }
    }

    private boolean hasEntryTransition() {
        return mEntryTransition != null;
    }

    private boolean hasExitTransition() {
        return mOriginTransitions != null && !mExitTransitionMap.isEmpty();
    }

    private void setupTransactionQueues() {
        final IBinder shellApplyToken;
        try {
            shellApplyToken = mOriginTransitions.getDefaultTransactionApplyToken();
        } catch (Exception e) {
            Log.e(TAG, "Error getting server side (shell) apply token", e);
            return;
        }
        if (mEntryTransition != null && mEntryTransition instanceof OriginRemoteTransition) {
            ((OriginRemoteTransition) mEntryTransition).setShellTransactionToken(shellApplyToken);
        }
        for (IRemoteTransition transition : mExitTransitionMap.keySet()) {
            if (transition instanceof OriginRemoteTransition) {
                ((OriginRemoteTransition) transition).setShellTransactionToken(shellApplyToken);
            }
        }
    }

    private void logD(String msg) {
        if (DEBUG) {
            Log.d(TAG, "Session[" + mName + "] - " + msg);
        }
    }

    private void logE(String msg) {
        Log.e(TAG, "Session[" + mName + "] - " + msg);
    }

    private void logE(String msg, Throwable e) {
        Log.e(TAG, "Session[" + mName + "] - " + msg, e);
    }

    private static String stateToString(@State int state) {
        switch (state) {
            case NOT_STARTED:
                return "NOT_STARTED";
            case STARTED:
                return "STARTED";
            case CANCELLED:
                return "CANCELLED";
            default:
                return "UNKNOWN(" + state + ")";
        }
    }

    /**
     * A builder to build a {@link OriginTransitionSession}.
     *
     * @hide
     */
    public static class Builder {
        private final Context mContext;
        @Nullable private final IOriginTransitions mOriginTransitions;
        @Nullable private Supplier<IRemoteTransition> mEntryTransitionSupplier;
        private final Map<IRemoteTransition, TransitionFilter> mReturnTransitionMap =
                new HashMap<>();
        private Handler mHandler = new Handler(Looper.getMainLooper());
        private String mName;
        @Nullable private Predicate<RemoteTransition> mIntentStarter;

        /** Create a builder that only supports entry transition. */
        public Builder(Context context) {
            this(context, /* originTransitions= */ null);
        }

        /** Create a builder that supports both entry and return transition. */
        public Builder(Context context, @Nullable IOriginTransitions originTransitions) {
            mContext = context;
            mOriginTransitions = originTransitions;
            mName = context.getPackageName();
        }

        /** Specify a name that is used in logging. */
        public Builder withName(String name) {
            mName = name;
            return this;
        }

        /** Specify an intent that will be launched when the session started. */
        public Builder withIntent(Intent intent) {
            return withIntentStarter(
                    transition -> {
                        mContext.startActivity(
                                intent, createDefaultActivityOptions(transition).toBundle());
                        return true;
                    });
        }

        /** Specify a pending intent that will be launched when the session started. */
        public Builder withPendingIntent(PendingIntent pendingIntent) {
            return withIntentStarter(
                    transition -> {
                        try {
                            pendingIntent.send(createDefaultActivityOptions(transition).toBundle());
                            return true;
                        } catch (PendingIntent.CanceledException e) {
                            Log.e(TAG, "Failed to launch pending intent!", e);
                            return false;
                        }
                    });
        }

        private static ActivityOptions createDefaultActivityOptions(
                @Nullable RemoteTransition transition) {
            ActivityOptions options =
                    transition == null
                            ? ActivityOptions.makeBasic()
                            : ActivityOptions.makeRemoteTransition(transition);
            LaunchCookie cookie = new LaunchCookie();
            options.setLaunchCookie(cookie);
            return options;
        }

        /**
         * Specify an intent starter function that will be called to start an activity. The function
         * accepts an optional {@link RemoteTransition} object which can be used to create an {@link
         * ActivityOptions} for the activity launch. The function can also return a {@code false}
         * result to cancel the session.
         *
         * <p>Note: it's encouraged to use {@link #withIntent(Intent)} or {@link
         * #withPendingIntent(PendingIntent)} instead of this method. Use it only if the default
         * activity launch code doesn't satisfy your requirement.
         */
        public Builder withIntentStarter(Predicate<RemoteTransition> intentStarter) {
            mIntentStarter = intentStarter;
            return this;
        }

        /** Add an entry transition to the builder. */
        public Builder withEntryTransition(IRemoteTransition transition) {
            mEntryTransitionSupplier = () -> transition;
            return this;
        }

        /** Add an origin entry transition to the builder. */
        public Builder withEntryTransition(
                UIComponent entryOrigin, TransitionPlayer entryPlayer) {
            mEntryTransitionSupplier =
                    () ->
                            new OriginRemoteTransition(
                                    mContext,
                                    /* isEntry= */ true,
                                    entryOrigin,
                                    entryPlayer,
                                    mHandler);
            return this;
        }

        /** Add an exit transition to the builder. */
        public Builder withExitTransition(IRemoteTransition transition) {
            mReturnTransitionMap.clear();
            mReturnTransitionMap.put(transition, null);
            return this;
        }

        /** Add an origin exit transition to the builder. */
        public Builder withExitTransition(
                UIComponent exitTarget, TransitionPlayer exitPlayer) {
            mReturnTransitionMap.clear();
            mReturnTransitionMap.put(new OriginRemoteTransition(
                                        mContext,
                                        /* isEntry= */ false,
                                        exitTarget,
                                        exitPlayer,
                                        mHandler), null);
            return this;
        }

        /** Add an exit transition/filter to the builder. */
        public Builder addExitTransitionWithFilter(
                IRemoteTransition transition,
                TransitionFilter filter) {
            if (mReturnTransitionMap.size() == 1
                    && mReturnTransitionMap.entrySet().contains(null)) {
                Log.w(TAG, "Exit transition already set using `withExitTransition()` "
                        + "- ignoring list addition");
                return this;
            }
            mReturnTransitionMap.put(transition, filter);
            return this;
        }

        /** Add an exit transition/filter to the builder. */
        public Builder addExitTransitionWithFilter(
                UIComponent exitTarget,
                TransitionPlayer exitPlayer,
                TransitionFilter filter) {
            if (mReturnTransitionMap.size() == 1
                    && mReturnTransitionMap.entrySet().contains(null)) {
                Log.w(TAG, "Exit transition already set using `withExitTransition()` "
                        + "- ignoring list addition");
                return this;
            }
            mReturnTransitionMap.put(new OriginRemoteTransition(
                    mContext,
                    /* isEntry= */ false,
                    exitTarget,
                    exitPlayer,
                    mHandler), filter);
            return this;
        }

        /** Supply a handler where transition callbacks will run. */
        public Builder withHandler(Handler handler) {
            mHandler = handler;
            return this;
        }

        /** Build an {@link OriginTransitionSession}. */
        public OriginTransitionSession build() {
            if (mIntentStarter == null) {
                throw new IllegalArgumentException("No intent, pending intent, or intent starter!");
            }

            final Map<IRemoteTransition, TransitionFilter> returnTransitionMap =
                    new HashMap<>(mReturnTransitionMap);
            mReturnTransitionMap.clear();

            return new OriginTransitionSession(
                    mName,
                    mOriginTransitions,
                    mIntentStarter,
                    mEntryTransitionSupplier == null ? null : mEntryTransitionSupplier.get(),
                    returnTransitionMap);
        }
    }
}
