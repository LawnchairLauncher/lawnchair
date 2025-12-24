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

package com.android.wm.shell.flicker.bubbles.samples

import android.tools.flicker.subject.events.EventLogSubject
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.flicker.subject.wm.WindowManagerTraceSubject
import com.android.wm.shell.flicker.bubbles.utils.RecordTraceWithTransitionRule

/**
 * Sample to illustrate how to use [RecordTraceWithTransitionRule].
 */
fun recordTraceWithTransitionRuleSample() {
    val rule = RecordTraceWithTransitionRule(
        setUpBeforeTransition = { clearAllTasksAndGoToHomeScreen() },
        transition = { launchActivityViaClickIcon() },
        tearDownAfterTransition = { finishActivity() },
    )

    // In test constructor ...
    val reader = rule.reader

    // Extract [WindowManagerTraceSubject]
    val wmTraceSubject = WindowManagerTraceSubject(
        reader.readWmTrace() ?: error("Failed to read WM trace")
    )

    // Extract [LayerTraceSubject]
    val layersTraceSubject = LayersTraceSubject(
        reader.readLayersTrace() ?: error("Failed to read Layers trace")
    )

    // Extract [EventLogSubject]
    val eventLogSubject = EventLogSubject(
        reader.readEventLogTrace() ?: error("Failed to read event log trace"),
        reader,
    )

    // Read CUJ Trace
    val cujTrace = reader.readCujTrace()
}

private fun clearAllTasksAndGoToHomeScreen() {}

private fun launchActivityViaClickIcon() {}

private fun finishActivity() {}