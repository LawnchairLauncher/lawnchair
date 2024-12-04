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

import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.log.TableLogBufferBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.inject.Inject;

/**
 * {@link Monitor} allows {@link Subscription}s to a set of conditions and monitors whether all of
 * them have been fulfilled.
 * <p>
 * This class should be used as a singleton, to prevent duplicate monitoring of the same conditions.
 */
public class Monitor {
    private final String mTag = getClass().getSimpleName();
    private final Executor mExecutor;
    private final Set<Condition> mPreconditions;
    private final TableLogBufferBase mLogBuffer;

    private final HashMap<Condition, ArraySet<Subscription.Token>> mConditions = new HashMap<>();
    private final HashMap<Subscription.Token, SubscriptionState> mSubscriptions = new HashMap<>();

    private static class SubscriptionState {
        private final Subscription mSubscription;

        // A subscription must maintain a reference to any active nested subscription so that it may
        // be later removed when the current subscription becomes invalid.
        private Subscription.Token mNestedSubscriptionToken;
        private Boolean mAllConditionsMet;
        private boolean mActive;

        SubscriptionState(Subscription subscription) {
            mSubscription = subscription;
        }

        public Set<Condition> getConditions() {
            return mSubscription.mConditions;
        }

        /**
         * Signals that the {@link Subscription} is now being monitored and will receive updates
         * based on its conditions.
         */
        private void setActive(boolean active) {
            if (mActive == active) {
                return;
            }

            mActive = active;

            final Callback callback = mSubscription.getCallback();

            if (callback == null) {
                return;
            }

            callback.onActiveChanged(active);
        }

        public void update(Monitor monitor) {
            final Boolean result = Evaluator.INSTANCE.evaluate(mSubscription.mConditions,
                    Evaluator.OP_AND);
            // Consider unknown (null) as true
            final boolean newAllConditionsMet = result == null || result;

            if (mAllConditionsMet != null && newAllConditionsMet == mAllConditionsMet) {
                return;
            }

            mAllConditionsMet = newAllConditionsMet;

            final Subscription nestedSubscription = mSubscription.getNestedSubscription();

            if (nestedSubscription != null) {
                if (mAllConditionsMet && mNestedSubscriptionToken == null) {
                    // When all conditions are met for a subscription with a nested subscription
                    // that is not currently being monitored, add the nested subscription for
                    // monitor.
                    mNestedSubscriptionToken =
                            monitor.addSubscription(nestedSubscription, null);
                } else if (!mAllConditionsMet && mNestedSubscriptionToken != null) {
                    // When conditions are not met and there is an active nested condition, remove
                    // the nested condition from monitoring.
                    removeNestedSubscription(monitor);
                }
                return;
            }

            mSubscription.getCallback().onConditionsChanged(mAllConditionsMet);
        }

        /**
         * Invoked when the {@link Subscription} has been added to the {@link Monitor}.
         */
        public void onAdded() {
            setActive(true);
        }

        /**
         * Invoked when the {@link Subscription} has been removed from the {@link Monitor},
         * allowing cleanup code to run.
         */
        public void onRemoved(Monitor monitor) {
            setActive(false);
            removeNestedSubscription(monitor);
        }

        private void removeNestedSubscription(Monitor monitor) {
            if (mNestedSubscriptionToken == null) {
                return;
            }

            monitor.removeSubscription(mNestedSubscriptionToken);
            mNestedSubscriptionToken = null;
        }
    }

    // Callback for when each condition has been updated.
    private final Condition.Callback mConditionCallback = new Condition.Callback() {
        @Override
        public void onConditionChanged(Condition condition) {
            mExecutor.execute(() -> updateConditionMetState(condition));
        }
    };

    /**
     * Constructor for injected use-cases. By default, no preconditions are present.
     */
    @Inject
    public Monitor(@Main Executor executor) {
        this(executor, Collections.emptySet());
    }

    /**
     * Main constructor, allowing specifying preconditions.
     */
    public Monitor(Executor executor, Set<Condition> preconditions) {
        this(executor, preconditions, null);
    }

    /**
     * Main constructor, allowing specifying preconditions and a log buffer for logging.
     */
    public Monitor(Executor executor, Set<Condition> preconditions, TableLogBufferBase logBuffer) {
        mExecutor = executor;
        mPreconditions = preconditions;
        mLogBuffer = logBuffer;
    }

    private void updateConditionMetState(Condition condition) {
        if (mLogBuffer != null) {
            mLogBuffer.logChange(/* prefix= */ "", condition.getTag(), condition.getState());
        }

        final ArraySet<Subscription.Token> subscriptions = mConditions.get(condition);

        // It's possible the condition was removed between the time the callback occurred and
        // update was executed on the main thread.
        if (subscriptions == null) {
            return;
        }

        subscriptions.stream().forEach(token -> mSubscriptions.get(token).update(this));
    }

    /**
     * Registers a callback and the set of conditions to trigger it.
     *
     * @param subscription A {@link Subscription} detailing the desired conditions and callback.
     * @return A {@link Subscription.Token} that can be used to remove the subscription.
     */
    public Subscription.Token addSubscription(@NonNull Subscription subscription) {
        return addSubscription(subscription, mPreconditions);
    }

    private Subscription.Token addSubscription(@NonNull Subscription subscription,
            Set<Condition> preconditions) {
        // If preconditions are set on the monitor, set up as a nested condition.
        final Subscription normalizedCondition = preconditions != null
                ? new Subscription.Builder(subscription).addConditions(preconditions).build()
                : subscription;

        final Subscription.Token token = new Subscription.Token();
        final SubscriptionState state = new SubscriptionState(normalizedCondition);

        mExecutor.execute(() -> {
            if (shouldLog()) Log.d(mTag, "adding subscription");
            mSubscriptions.put(token, state);

            // Add and associate conditions.
            normalizedCondition.getConditions().forEach(condition -> {
                if (!mConditions.containsKey(condition)) {
                    mConditions.put(condition, new ArraySet<>());
                    condition.addCallback(mConditionCallback);
                }

                mConditions.get(condition).add(token);
            });

            state.onAdded();

            // Update subscription state.
            state.update(this);

        });
        return token;
    }

    /**
     * Removes a subscription from participating in future callbacks.
     *
     * @param token The {@link Subscription.Token} returned when the {@link Subscription} was
     *              originally added.
     */
    public void removeSubscription(@NonNull Subscription.Token token) {
        mExecutor.execute(() -> {
            if (shouldLog()) Log.d(mTag, "removing subscription");
            if (!mSubscriptions.containsKey(token)) {
                Log.e(mTag, "subscription not present:" + token);
                return;
            }

            final SubscriptionState removedSubscription = mSubscriptions.remove(token);

            removedSubscription.getConditions().forEach(condition -> {
                if (!mConditions.containsKey(condition)) {
                    Log.e(mTag, "condition not present:" + condition);
                    return;

                }
                final Set<Subscription.Token> conditionSubscriptions = mConditions.get(condition);

                conditionSubscriptions.remove(token);
                if (conditionSubscriptions.isEmpty()) {
                    condition.removeCallback(mConditionCallback);
                    mConditions.remove(condition);
                }
            });

            removedSubscription.onRemoved(this);
        });
    }

    private boolean shouldLog() {
        return Log.isLoggable(mTag, Log.DEBUG);
    }

    /**
     * A {@link Subscription} represents a set of conditions and a callback that is informed when
     * these conditions change.
     */
    public static class Subscription {
        private final Set<Condition> mConditions;
        private final Callback mCallback;

        // A nested {@link Subscription} is a special callback where the specified condition's
        // active state is dependent on the conditions of the parent {@link Subscription} being met.
        // Once active, the nested subscription's conditions are registered as normal with the
        // monitor and its callback (which could also be a nested condition) is triggered based on
        // those conditions. The nested condition will be removed from monitor if the outer
        // subscription's conditions ever become invalid.
        private final Subscription mNestedSubscription;

        private Subscription(Set<Condition> conditions, Callback callback,
                Subscription nestedSubscription) {
            this.mConditions = Collections.unmodifiableSet(conditions);
            this.mCallback = callback;
            this.mNestedSubscription = nestedSubscription;
        }

        public Set<Condition> getConditions() {
            return mConditions;
        }

        public Callback getCallback() {
            return mCallback;
        }

        public Subscription getNestedSubscription() {
            return mNestedSubscription;
        }

        /**
         * A {@link Token} is an identifier that is associated with a {@link Subscription} which is
         * registered with a {@link Monitor}.
         */
        public static class Token {
        }

        /**
         * {@link Builder} is a helper class for constructing a {@link Subscription}.
         */
        public static class Builder {
            private final Callback mCallback;
            private final Subscription mNestedSubscription;
            private final ArraySet<Condition> mConditions;

            /**
             * Default constructor specifying the {@link Callback} for the {@link Subscription}.
             */
            public Builder(Callback callback) {
                this(null, callback);
            }

            public Builder(Subscription nestedSubscription) {
                this(nestedSubscription, null);
            }

            private Builder(Subscription nestedSubscription, Callback callback) {
                mNestedSubscription = nestedSubscription;
                mCallback = callback;
                mConditions = new ArraySet<>();
            }

            /**
             * Adds a {@link Condition} to be associated with the {@link Subscription}.
             *
             * @return The updated {@link Builder}.
             */
            public Builder addCondition(Condition condition) {
                mConditions.add(condition);
                return this;
            }

            /**
             * Adds a set of {@link Condition} to be associated with the {@link Subscription}.
             *
             * @return The updated {@link Builder}.
             */
            public Builder addConditions(Set<Condition> condition) {
                if (condition == null) {
                    return this;
                }

                mConditions.addAll(condition);
                return this;
            }

            /**
             * Builds the {@link Subscription}.
             *
             * @return The resulting {@link Subscription}.
             */
            public Subscription build() {
                return new Subscription(mConditions, mCallback, mNestedSubscription);
            }
        }
    }

    /**
     * Callback that receives updates of whether all conditions have been fulfilled.
     */
    public interface Callback {
        /**
         * Returns the conditions associated with this callback.
         */
        default ArrayList<Condition> getConditions() {
            return new ArrayList<>();
        }

        /**
         * Triggered when the fulfillment of all conditions have been met.
         *
         * @param allConditionsMet True if all conditions have been fulfilled. False if none or
         *                         only partial conditions have been fulfilled.
         */
        void onConditionsChanged(boolean allConditionsMet);

        /**
         * Called when the active state of the {@link Subscription} changes.
         * @param active {@code true} when changes to the conditions will affect the
         *               {@link Subscription}, {@code false} otherwise.
         */
        default void onActiveChanged(boolean active) {
        }
    }
}
