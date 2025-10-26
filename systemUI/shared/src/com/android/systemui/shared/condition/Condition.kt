/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.systemui.shared.condition

import android.util.Log
import androidx.annotation.IntDef
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Base class for a condition that needs to be fulfilled in order for [Monitor] to inform its
 * callbacks.
 */
abstract class Condition
/**
 * Constructor for specifying initial state and overriding condition attribute.
 *
 * @param initialConditionMet Initial state of the condition.
 * @param overriding Whether this condition overrides others.
 */
@JvmOverloads
protected constructor(
    private val _scope: CoroutineScope,
    private var _isConditionMet: Boolean? = false,
    /** Returns whether the current condition overrides */
    val isOverridingCondition: Boolean = false,
) {
    private val mTag: String = javaClass.simpleName

    private val callbacks = mutableListOf<WeakReference<Callback>>()
    private var started = false
    private var currentJob: Job? = null

    /** Starts monitoring the condition. */
    protected abstract suspend fun start()

    /** Stops monitoring the condition. */
    protected abstract fun stop()

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(START_EAGERLY, START_LAZILY, START_WHEN_NEEDED)
    annotation class StartStrategy

    @get:StartStrategy abstract val startStrategy: Int

    /**
     * Registers a callback to receive updates once started. This should be called before [.start].
     * Also triggers the callback immediately if already started.
     */
    fun addCallback(callback: Callback) {
        if (shouldLog()) Log.d(mTag, "adding callback")
        callbacks.add(WeakReference(callback))

        if (started) {
            callback.onConditionChanged(this)
            return
        }

        currentJob = _scope.launch { start() }
        started = true
    }

    /** Removes the provided callback from further receiving updates. */
    fun removeCallback(callback: Callback) {
        if (shouldLog()) Log.d(mTag, "removing callback")
        val iterator = callbacks.iterator()
        while (iterator.hasNext()) {
            val cb = iterator.next().get()
            if (cb == null || cb === callback) {
                iterator.remove()
            }
        }

        if (callbacks.isNotEmpty() || !started) {
            return
        }

        stop()
        currentJob?.cancel()
        currentJob = null

        started = false
    }

    /**
     * Wrapper to [.addCallback] when a lifecycle is in the resumed state and [.removeCallback] when
     * not resumed automatically.
     */
    fun observe(owner: LifecycleOwner, listener: Callback): Callback {
        return observe(owner.lifecycle, listener)
    }

    /**
     * Wrapper to [.addCallback] when a lifecycle is in the resumed state and [.removeCallback] when
     * not resumed automatically.
     */
    fun observe(lifecycle: Lifecycle, listener: Callback): Callback {
        lifecycle.addObserver(
            LifecycleEventObserver { lifecycleOwner: LifecycleOwner?, event: Lifecycle.Event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    addCallback(listener)
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    removeCallback(listener)
                }
            }
        )
        return listener
    }

    /**
     * Updates the value for whether the condition has been fulfilled, and sends an update if the
     * value changes and any callback is registered.
     *
     * @param isConditionMet True if the condition has been fulfilled. False otherwise.
     */
    protected fun updateCondition(isConditionMet: Boolean) {
        if (_isConditionMet != null && _isConditionMet == isConditionMet) {
            return
        }

        if (shouldLog()) Log.d(mTag, "updating condition to $isConditionMet")
        _isConditionMet = isConditionMet
        sendUpdate()
    }

    /**
     * Clears the set condition value. This is purposefully separate from [.updateCondition] to
     * avoid confusion around `null` values.
     */
    fun clearCondition() {
        if (_isConditionMet == null) {
            return
        }

        if (shouldLog()) Log.d(mTag, "clearing condition")

        _isConditionMet = null
        sendUpdate()
    }

    private fun sendUpdate() {
        val iterator = callbacks.iterator()
        while (iterator.hasNext()) {
            val cb = iterator.next().get()
            if (cb == null) {
                iterator.remove()
            } else {
                cb.onConditionChanged(this)
            }
        }
    }

    val isConditionSet: Boolean
        /**
         * Returns whether the condition is set. This method should be consulted to understand the
         * value of [.isConditionMet].
         *
         * @return `true` if value is present, `false` otherwise.
         */
        get() = _isConditionMet != null

    val isConditionMet: Boolean
        /**
         * Returns whether the condition has been met. Note that this method will return `false` if
         * the condition is not set as well.
         */
        get() = true == _isConditionMet

    protected fun shouldLog(): Boolean {
        return Log.isLoggable(mTag, Log.DEBUG)
    }

    val tag: String
        get() {
            if (isOverridingCondition) {
                return "$mTag[OVRD]"
            }

            return mTag
        }

    val state: String
        /**
         * Returns the state of the condition.
         * - "Invalid", condition hasn't been set / not monitored
         * - "True", condition has been met
         * - "False", condition has not been met
         */
        get() {
            if (!isConditionSet) {
                return "Invalid"
            }
            return if (isConditionMet) "True" else "False"
        }

    /**
     * Creates a new condition which will only be true when both this condition and all the provided
     * conditions are true.
     */
    fun and(others: Collection<Condition>): Condition {
        val conditions: List<Condition> = listOf(this, *others.toTypedArray())
        return CombinedCondition(_scope, conditions, Evaluator.OP_AND)
    }

    /**
     * Creates a new condition which will only be true when both this condition and the provided
     * condition is true.
     */
    fun and(vararg others: Condition): Condition {
        return and(listOf(*others))
    }

    /**
     * Creates a new condition which will only be true when either this condition or any of the
     * provided conditions are true.
     */
    fun or(others: Collection<Condition>): Condition {
        val conditions: MutableList<Condition> = ArrayList()
        conditions.add(this)
        conditions.addAll(others)
        return CombinedCondition(_scope, conditions, Evaluator.OP_OR)
    }

    /**
     * Creates a new condition which will only be true when either this condition or the provided
     * condition is true.
     */
    fun or(vararg others: Condition): Condition {
        return or(listOf(*others))
    }

    /** Callback that receives updates about whether the condition has been fulfilled. */
    fun interface Callback {
        /**
         * Called when the fulfillment of the condition changes.
         *
         * @param condition The condition in question.
         */
        fun onConditionChanged(condition: Condition)
    }

    companion object {
        /** Condition should be started as soon as there is an active subscription. */
        const val START_EAGERLY: Int = 0

        /**
         * Condition should be started lazily only if needed. But once started, it will not be
         * cancelled unless there are no more active subscriptions.
         */
        const val START_LAZILY: Int = 1

        /**
         * Condition should be started lazily only if needed, and can be stopped when not needed.
         * This should be used for conditions which are expensive to keep running.
         */
        const val START_WHEN_NEEDED: Int = 2
    }
}
