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

package com.android.systemui.shared.condition;

import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import kotlinx.coroutines.CoroutineScope;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Base class for a condition that needs to be fulfilled in order for {@link Monitor} to inform
 * its callbacks.
 */
public abstract class Condition {
    private final String mTag = getClass().getSimpleName();

    private final ArrayList<WeakReference<Callback>> mCallbacks = new ArrayList<>();
    private final boolean mOverriding;
    private final CoroutineScope mScope;
    private Boolean mIsConditionMet;
    private boolean mStarted = false;

    /**
     * By default, conditions have an initial value of false and are not overriding.
     */
    public Condition(CoroutineScope scope) {
        this(scope, false, false);
    }

    /**
     * Constructor for specifying initial state and overriding condition attribute.
     *
     * @param initialConditionMet Initial state of the condition.
     * @param overriding          Whether this condition overrides others.
     */
    protected Condition(CoroutineScope scope, Boolean initialConditionMet, boolean overriding) {
        mIsConditionMet = initialConditionMet;
        mOverriding = overriding;
        mScope = scope;
    }

    /**
     * Starts monitoring the condition.
     */
    protected abstract void start();

    /**
     * Stops monitoring the condition.
     */
    protected abstract void stop();

    /**
     * Condition should be started as soon as there is an active subscription.
     */
    public static final int START_EAGERLY = 0;
    /**
     * Condition should be started lazily only if needed. But once started, it will not be cancelled
     * unless there are no more active subscriptions.
     */
    public static final int START_LAZILY = 1;
    /**
     * Condition should be started lazily only if needed, and can be stopped when not needed. This
     * should be used for conditions which are expensive to keep running.
     */
    public static final int START_WHEN_NEEDED = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({START_EAGERLY, START_LAZILY, START_WHEN_NEEDED})
    @interface StartStrategy {
    }

    @StartStrategy
    protected abstract int getStartStrategy();

    /**
     * Returns whether the current condition overrides
     */
    public boolean isOverridingCondition() {
        return mOverriding;
    }

    /**
     * Registers a callback to receive updates once started. This should be called before
     * {@link #start()}. Also triggers the callback immediately if already started.
     */
    public void addCallback(@NonNull Callback callback) {
        if (shouldLog()) Log.d(mTag, "adding callback");
        mCallbacks.add(new WeakReference<>(callback));

        if (mStarted) {
            callback.onConditionChanged(this);
            return;
        }

        start();
        mStarted = true;
    }

    /**
     * Removes the provided callback from further receiving updates.
     */
    public void removeCallback(@NonNull Callback callback) {
        if (shouldLog()) Log.d(mTag, "removing callback");
        final Iterator<WeakReference<Callback>> iterator = mCallbacks.iterator();
        while (iterator.hasNext()) {
            final Callback cb = iterator.next().get();
            if (cb == null || cb == callback) {
                iterator.remove();
            }
        }

        if (!mCallbacks.isEmpty() || !mStarted) {
            return;
        }

        stop();
        mStarted = false;
    }

    /**
     * Wrapper to {@link #addCallback(Callback)} when a lifecycle is in the resumed state
     * and {@link #removeCallback(Callback)} when not resumed automatically.
     */
    public Callback observe(LifecycleOwner owner, Callback listener) {
        return observe(owner.getLifecycle(), listener);
    }

    /**
     * Wrapper to {@link #addCallback(Callback)} when a lifecycle is in the resumed state
     * and {@link #removeCallback(Condition.Callback)} when not resumed automatically.
     */
    public Callback observe(Lifecycle lifecycle, Callback listener) {
        lifecycle.addObserver((LifecycleEventObserver) (lifecycleOwner, event) -> {
            if (event == Lifecycle.Event.ON_RESUME) {
                addCallback(listener);
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                removeCallback(listener);
            }
        });
        return listener;
    }

    /**
     * Updates the value for whether the condition has been fulfilled, and sends an update if the
     * value changes and any callback is registered.
     *
     * @param isConditionMet True if the condition has been fulfilled. False otherwise.
     */
    protected void updateCondition(boolean isConditionMet) {
        if (mIsConditionMet != null && mIsConditionMet == isConditionMet) {
            return;
        }

        if (shouldLog()) Log.d(mTag, "updating condition to " + isConditionMet);
        mIsConditionMet = isConditionMet;
        sendUpdate();
    }

    /**
     * Clears the set condition value. This is purposefully separate from
     * {@link #updateCondition(boolean)} to avoid confusion around {@code null} values.
     */
    protected void clearCondition() {
        if (mIsConditionMet == null) {
            return;
        }

        if (shouldLog()) Log.d(mTag, "clearing condition");

        mIsConditionMet = null;
        sendUpdate();
    }

    private void sendUpdate() {
        final Iterator<WeakReference<Callback>> iterator = mCallbacks.iterator();
        while (iterator.hasNext()) {
            final Callback cb = iterator.next().get();
            if (cb == null) {
                iterator.remove();
            } else {
                cb.onConditionChanged(this);
            }
        }
    }

    /**
     * Returns whether the condition is set. This method should be consulted to understand the
     * value of {@link #isConditionMet()}.
     *
     * @return {@code true} if value is present, {@code false} otherwise.
     */
    public boolean isConditionSet() {
        return mIsConditionMet != null;
    }

    /**
     * Returns whether the condition has been met. Note that this method will return {@code false}
     * if the condition is not set as well.
     */
    public boolean isConditionMet() {
        return Boolean.TRUE.equals(mIsConditionMet);
    }

    protected final boolean shouldLog() {
        return Log.isLoggable(mTag, Log.DEBUG);
    }

    protected final String getTag() {
        if (isOverridingCondition()) {
            return mTag + "[OVRD]";
        }

        return mTag;
    }

    /**
     * Returns the state of the condition.
     * - "Invalid", condition hasn't been set / not monitored
     * - "True", condition has been met
     * - "False", condition has not been met
     */
    protected final String getState() {
        if (!isConditionSet()) {
            return "Invalid";
        }
        return isConditionMet() ? "True" : "False";
    }

    /**
     * Creates a new condition which will only be true when both this condition and all the provided
     * conditions are true.
     */
    public Condition and(@NonNull Collection<Condition> others) {
        final List<Condition> conditions = new ArrayList<>();
        conditions.add(this);
        conditions.addAll(others);
        return new CombinedCondition(mScope, conditions, Evaluator.OP_AND);
    }

    /**
     * Creates a new condition which will only be true when both this condition and the provided
     * condition is true.
     */
    public Condition and(@NonNull Condition... others) {
        return and(Arrays.asList(others));
    }

    /**
     * Creates a new condition which will only be true when either this condition or any of the
     * provided conditions are true.
     */
    public Condition or(@NonNull Collection<Condition> others) {
        final List<Condition> conditions = new ArrayList<>();
        conditions.add(this);
        conditions.addAll(others);
        return new CombinedCondition(mScope, conditions, Evaluator.OP_OR);
    }

    /**
     * Creates a new condition which will only be true when either this condition or the provided
     * condition is true.
     */
    public Condition or(@NonNull Condition... others) {
        return or(Arrays.asList(others));
    }

    /**
     * Callback that receives updates about whether the condition has been fulfilled.
     */
    public interface Callback {
        /**
         * Called when the fulfillment of the condition changes.
         *
         * @param condition The condition in question.
         */
        void onConditionChanged(Condition condition);
    }
}
