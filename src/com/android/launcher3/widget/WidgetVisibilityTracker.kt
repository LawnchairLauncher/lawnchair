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

package com.android.launcher3.widget

import android.view.View
import androidx.core.util.forEach
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.AbstractFloatingView.TYPE_ALL
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.PagedView
import com.android.launcher3.Workspace
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.views.ActivityContext

/**
 * Updates visibility hints on widget views in response to swiping on the workspace, opening all
 * apps, or a floating view.
 */
class WidgetVisibilityTracker(
    private val activityContext: ActivityContext,
    private val widgetHolder: LauncherWidgetHolder,
    private val workspace: Workspace<*>,
    private val stateManager: StateManager<LauncherState, Launcher>,
) : StateManager.StateListener<LauncherState>, PagedView.PageSwitchListener {
    init {
        workspace.addPageSwitchListener(this)
        stateManager.addStateListener(this)
    }

    override fun onPageSwitch() {
        updateVisibility()
    }

    override fun onStateTransitionComplete(finalState: LauncherState?) {
        updateVisibility()
    }

    fun onDragLayerHierarchyChanged() {
        updateVisibility()
    }

    fun onWidgetAdded() {
        updateVisibility()
    }

    private fun updateVisibility() {
        val inNormalState = stateManager.currentStableState == LauncherState.NORMAL
        val noFloatingViews =
            AbstractFloatingView.getAnyView<AbstractFloatingView>(activityContext, TYPE_ALL) == null
        val visiblePages = workspace.visiblePageIndices
        widgetHolder.views.forEach { _, view ->
            val pageIndex =
                (view.tag as? LauncherAppWidgetInfo)?.screenId?.let {
                    workspace.getPageIndexForScreenId(it)
                } ?: return@forEach
            if (inNormalState && noFloatingViews && pageIndex in visiblePages) {
                view.startVisibilityTracking()
            } else {
                view.stopVisibilityTracking()
            }
        }
    }

    fun destroy() {
        workspace.removePageSwitchListener(this)
        stateManager.removeStateListener(this)
    }
}

private val View.parents: Sequence<View>
    get() = generateSequence(parent as? View) { it.parent as? View }
