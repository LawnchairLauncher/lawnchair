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

package com.android.wm.shell.flicker.bubbles.utils

import android.tools.io.Reader
import android.tools.traces.monitors.PerfettoTraceMonitor
import android.tools.traces.monitors.ScreenRecorder
import android.tools.traces.monitors.events.EventLogMonitor
import android.tools.traces.monitors.withTracing
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.MultipleFailureException
import org.junit.runners.model.Statement

/**
 * A [org.junit.ClassRule] to record trace with transition.
 *
 * @sample com.android.wm.shell.flicker.bubbles.samples.recordTraceWithTransitionRuleSample
 *
 * @property setUpBeforeTransition the operation to initialize the environment before transition
 *                                   if specified
 * @property transition the transition to execute
 * @property tearDownAfterTransition the operation to clean up after transition if specified
 */
class RecordTraceWithTransitionRule(
    private val setUpBeforeTransition: () -> Unit = {},
    private val transition: () -> Unit,
    private val tearDownAfterTransition: () -> Unit = {},
) : TestRule {

    /**
     * The reader to read trace from.
     */
    lateinit var reader: Reader

    override fun apply(base: Statement, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                val errors = ArrayList<Throwable>()
                try {
                    recordTraceWithTransition()
                } catch (e: Throwable) {
                    errors.add(e)
                } finally {
                    // In case the crash during transition and test App is not removed.
                    tearDownAfterTransition()
                }

                try {
                    // Ensure the base is executed even if #recordTraceWithTransition crashes.
                    base.evaluate()
                } catch (e: Throwable) {
                    errors.add(e)
                }
                // If the tests should be skipped, don't need to throw exceptions.
                if (!errors.any {e -> e is AssumptionViolatedException}) {
                    MultipleFailureException.assertEmpty(errors)
                }
            }
        }
    }

    private fun recordTraceWithTransition() {
        setUpBeforeTransition()
        reader = runTransitionWithTrace {
            try {
                transition()
            } catch (e: Throwable) {
                Log.e(TAG, "Transition is aborted due to the exception:\n $e", e)
            }
        }
    }

    /**
     * A helper method to record the trace while [transition] is running.
     *
     * @sample com.android.wm.shell.flicker.bubbles.samples.runTransitionWithTraceSample
     *
     * @param transition the transition to verify.
     * @return a [Reader] that can read the trace data from.
     */
    private fun runTransitionWithTrace(transition: () -> Unit): Reader =
        withTracing(
            traceMonitors = listOf(
                ScreenRecorder(InstrumentationRegistry.getInstrumentation().targetContext),
                PerfettoTraceMonitor.newBuilder()
                    .enableTransitionsTrace()
                    .enableLayersTrace()
                    .enableWindowManagerTrace()
                    .enableViewCaptureTrace()
                    .build(),
                EventLogMonitor()
            ),
            predicate = transition
        )

    companion object {
        private const val TAG = "TransitionRule"
    }
}