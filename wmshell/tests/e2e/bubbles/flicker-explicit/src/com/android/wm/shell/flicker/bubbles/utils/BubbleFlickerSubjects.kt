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

import android.tools.device.apphelpers.StandardAppHelper
import android.tools.flicker.subject.events.EventLogSubject
import android.tools.flicker.subject.layers.LayerTraceEntrySubject
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.flicker.subject.wm.WindowManagerStateSubject
import android.tools.flicker.subject.wm.WindowManagerTraceSubject
import android.tools.traces.surfaceflinger.LayerTraceEntry
import android.tools.traces.wm.WindowManagerState

/**
 * The interface that contains [FlickerSubject] used in explicit flicker tests,
 * which should be inherited for test subsets or test base.
 *
 * @see com.android.wm.shell.flicker.bubbles.BubbleFlickerTestBase
 * @see com.android.wm.shell.flicker.bubbles.testcase.BubbleStackAlwaysVisibleTestCases
 */
interface BubbleFlickerSubjects {

    /**
     * The event log subject.
     */
    val eventLogSubject: EventLogSubject

    /**
     * The WindowManager trace subject, which is equivalent to the data shown in
     * `Window Manager` tab in go/winscope.
     */
    val wmTraceSubject: WindowManagerTraceSubject

    /**
     * The Layer trace subject, which is equivalent to the data shown in
     * `Surface Flinger` tab in go/winscope.
     */
    val layersTraceSubject: LayersTraceSubject

    /**
     * The first [WindowManagerState] of the WindowManager trace.
     */
    val wmStateSubjectAtStart: WindowManagerStateSubject

    /**
     * The last [WindowManagerState] of the WindowManager trace.
     */
    val wmStateSubjectAtEnd: WindowManagerStateSubject

    /**
     * The first [LayerTraceEntry] of the Layers trace.
     */
    val layerTraceEntrySubjectAtStart: LayerTraceEntrySubject

    /**
     * The last [LayerTraceEntry] of the Layers trace.
     */
    val layerTraceEntrySubjectAtEnd: LayerTraceEntrySubject

    /**
     * Indicates whether the device uses gesture navigation bar or not.
     */
    val isGesturalNavBar: Boolean

    /**
     * The app used in flicker tests to verify with.
     */
    val testApp: StandardAppHelper
}