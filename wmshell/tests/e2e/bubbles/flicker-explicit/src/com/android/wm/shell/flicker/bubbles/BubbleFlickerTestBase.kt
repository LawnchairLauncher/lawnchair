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

package com.android.wm.shell.flicker.bubbles

import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.tools.Tag
import android.tools.flicker.assertions.SubjectsParser
import android.tools.flicker.subject.events.EventLogSubject
import android.tools.flicker.subject.layers.LayerTraceEntrySubject
import android.tools.flicker.subject.layers.LayersTraceSubject
import android.tools.flicker.subject.wm.WindowManagerStateSubject
import android.tools.flicker.subject.wm.WindowManagerTraceSubject
import android.tools.io.Reader
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.surfaceflinger.LayerTraceEntry
import android.tools.traces.wm.WindowManagerState
import com.android.launcher3.tapl.LauncherInstrumentation.NavigationModel
import com.android.server.wm.flicker.assertNavBarPosition
import com.android.server.wm.flicker.assertStatusBarLayerPosition
import com.android.wm.shell.flicker.bubbles.utils.BubbleFlickerSubjects
import com.android.wm.shell.flicker.bubbles.utils.FlickerPropertyInitializer
import org.junit.Rule
import org.junit.Test

/**
 * The base class of Bubble flicker tests, which includes:
 * - Generic tests: checks there's no flicker in visible windows/layers
 * - Launcher visibility tests: checks launcher window/layer is always visible
 * - System Bars tests; checks the visibility of navigation and status bar
 */
abstract class BubbleFlickerTestBase : BubbleFlickerSubjects {

    @get:Rule
    val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    /**
     * The reader to read trace from.
     */
    abstract val traceDataReader: Reader

    /**
     * The event log subject.
     */
    override val eventLogSubject = EventLogSubject(
        traceDataReader.readEventLogTrace() ?: error("Failed to read event log"),
        traceDataReader
    )

    /**
     * The WindowManager trace subject, which is equivalent to the data shown in
     * `Window Manager` tab in go/winscope.
     */
    override val wmTraceSubject = WindowManagerTraceSubject(
        traceDataReader.readWmTrace() ?: error("Failed to read WM trace")
    )

    /**
     * The Layer trace subject, which is equivalent to the data shown in
     * `Surface Flinger` tab in go/winscope.
     */
    override val layersTraceSubject = LayersTraceSubject(
        traceDataReader.readLayersTrace() ?: error("Failed to read layer trace")
    )

    /**
     * The first [WindowManagerState] of the WindowManager trace.
     */
    final override val wmStateSubjectAtStart: WindowManagerStateSubject

    /**
     * The last [WindowManagerState] of the WindowManager trace.
     */
    final override val wmStateSubjectAtEnd: WindowManagerStateSubject

    /**
     * The first [LayerTraceEntry] of the Layers trace.
     */
    final override val layerTraceEntrySubjectAtStart: LayerTraceEntrySubject

    /**
     * The last [LayerTraceEntry] of the Layers trace.
     */
    final override val layerTraceEntrySubjectAtEnd: LayerTraceEntrySubject

    // TODO(b/396020056): Verify bubble scenarios in 3-button mode.
    /**
     * Indicates whether the device uses gesture navigation bar or not.
     */
    override val isGesturalNavBar = tapl.navigationModel == NavigationModel.ZERO_BUTTON

    override val testApp
        get() = BubbleFlickerTestBase.testApp

    /**
     * Initialize subjects inherited from [FlickerSubject].
     */
    init {
        val parser = SubjectsParser(traceDataReader)
        wmStateSubjectAtStart = parser.getSubjectOfType(Tag.START)
        wmStateSubjectAtEnd = parser.getSubjectOfType(Tag.END)
        layerTraceEntrySubjectAtStart = parser.getSubjectOfType(Tag.START)
        layerTraceEntrySubjectAtEnd = parser.getSubjectOfType(Tag.END)
    }

// region Generic tests

    /**
     * Verifies there's no flickers among all visible windows.
     *
     * In other words, all visible windows shouldn't be visible -> invisible -> visible in
     * consecutive entries
     */
    @Test
    fun visibleWindowsShownMoreThanOneConsecutiveEntry() {
        wmTraceSubject
            .visibleWindowsShownMoreThanOneConsecutiveEntry()
            .forAllEntries()
    }

    /**
     * Verifies there's no flickers among all visible layers.
     *
     * In other words, all visible layers shouldn't be visible -> invisible -> visible in
     * consecutive entries
     */
    @Test
    fun visibleLayersShownMoreThanOneConsecutiveEntry() {
        layersTraceSubject
            .visibleLayersShownMoreThanOneConsecutiveEntry()
            .forAllEntries()
    }

// endregion

// region Launcher visibility tests

    /**
     * Verifies the launcher window is always visible.
     */
    @Test
    fun launcherWindowIsAlwaysVisible() {
        wmTraceSubject.isAppWindowVisible(ComponentNameMatcher.LAUNCHER).forAllEntries()
    }

    /**
     * Verifies the launcher layer is always visible.
     */
    @Test
    fun launcherLayerIsAlwaysVisible() {
        layersTraceSubject.isVisible(ComponentNameMatcher.LAUNCHER).forAllEntries()
    }

// endregion

// region System bars tests

    /**
     * Verifies navigation bar layer is visible at the start and end of transition.
     */
    @Test
    fun navBarLayerIsVisibleAtStartAndEnd() {
        layerTraceEntrySubjectAtStart.isVisible(ComponentNameMatcher.NAV_BAR)
        layerTraceEntrySubjectAtEnd.isVisible(ComponentNameMatcher.NAV_BAR)
    }

    /**
     * Verifies navigation bar position at the start and end of transition.
     */
    @Test
    fun navBarLayerPositionAtStartAndEnd() {
        assertNavBarPosition(layerTraceEntrySubjectAtStart, isGesturalNavBar)
        assertNavBarPosition(layerTraceEntrySubjectAtEnd, isGesturalNavBar)
    }

    /**
     * Verifies navigation bar window is visible.
     */
    @Test
    fun navBarWindowIsAlwaysVisible() {
        wmTraceSubject
            .isAboveAppWindowVisible(ComponentNameMatcher.NAV_BAR)
            .forAllEntries()
    }

    /**
     * Verifies status bar layer is visible at the start and end of transition.
     */
    @Test
    fun statusBarLayerIsVisibleAtStartAndEnd() {
        layerTraceEntrySubjectAtStart.isVisible(ComponentNameMatcher.STATUS_BAR)
        layerTraceEntrySubjectAtEnd.isVisible(ComponentNameMatcher.STATUS_BAR)
    }

    /**
     * Verifies status bar position at the start and end of transition.
     */
    @Test
    fun statusBarLayerPositionAtStartAndEnd() {
        assertStatusBarLayerPosition(layerTraceEntrySubjectAtStart, wmStateSubjectAtStart.wmState)
        assertStatusBarLayerPosition(layerTraceEntrySubjectAtEnd, wmStateSubjectAtEnd.wmState)
    }

    /**
     * Verifies status bar window is visible.
     */
    @Test
    fun statusBarWindowIsAlwaysVisible() {
        wmTraceSubject
            .isAboveAppWindowVisible(ComponentNameMatcher.STATUS_BAR)
            .forAllEntries()
    }

// endregion

    companion object : FlickerPropertyInitializer()
}