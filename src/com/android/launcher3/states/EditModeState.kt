/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.states

import android.content.Context
import com.android.launcher3.Flags.enableScalingRevealHomeAnimation
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.logging.StatsLogManager
import com.android.launcher3.views.ActivityContext
import kotlin.math.min

/** Definition for Edit Mode state used for home gardening multi-select */
class EditModeState(id: Int) : LauncherState(id, StatsLogManager.LAUNCHER_STATE_HOME, STATE_FLAGS) {

    companion object {
        const val DEPTH_15_PERCENT = 0.15f

        private val STATE_FLAGS =
            (FLAG_MULTI_PAGE or
                FLAG_WORKSPACE_INACCESSIBLE or
                FLAG_DISABLE_RESTORE or
                FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED or
                FLAG_WORKSPACE_HAS_BACKGROUNDS)

        private fun getEditModePageStripHeightPx(launcher: Launcher): Int =
            launcher.resources.getDimensionPixelSize(R.dimen.edit_mode_page_strip_height)

        private fun getEditModeWorkspaceVerticalBounds(launcher: Launcher): Pair<Float, Float> {
            val grid = launcher.deviceProfile
            val top = (grid.getInsets().top + grid.workspacePadding.top).toFloat()

            val stripHeightPx = getEditModePageStripHeightPx(launcher)
            val bottomGap =
                launcher.resources.getDimensionPixelSize(
                    R.dimen.edit_mode_workspace_bottom_gap,
                )
            val hotseat = launcher.hotseat
            val bottom =
                if (hotseat.top > 0) {
                    hotseat.top + hotseat.translationY - bottomGap
                } else {
                    grid.getCellLayoutSpringLoadShrunkBottom(launcher) - stripHeightPx
                }
            return top to bottom
        }

        private fun getEditModeWorkspaceScale(
            launcher: Launcher,
            top: Float,
            bottom: Float,
        ): Float {
            val grid = launcher.deviceProfile
            var scale = (bottom - top) / grid.cellLayoutHeight.toFloat()
            scale = min(scale, 1f)

            val workspaceWidth = grid.deviceProperties.availableWidthPx
            val scaledWorkspaceWidth = workspaceWidth * scale
            val maxAvailableWidth =
                workspaceWidth - (2 * grid.workspaceSpringLoadedMinNextPageVisiblePx)
            if (scaledWorkspaceWidth > maxAvailableWidth) {
                scale *= maxAvailableWidth / scaledWorkspaceWidth
            }
            return scale
        }

        private fun getEditModeWorkspaceScaleAndTranslation(
            launcher: Launcher,
        ): ScaleAndTranslation {
            val ws = launcher.workspace
            if (ws.childCount == 0) {
                return ScaleAndTranslation(1f, 0f, 0f)
            }

            val (top, bottom) = getEditModeWorkspaceVerticalBounds(launcher)
            val scale = getEditModeWorkspaceScale(launcher, top, bottom)

            val halfHeight = ws.height / 2f
            val myCenter = ws.top + halfHeight
            val cellTopFromCenter = halfHeight - ws.getChildAt(0).top
            val actualCellTop = myCenter - cellTopFromCenter * scale
            return ScaleAndTranslation(scale, 0f, top - actualCellTop)
        }
    }

    override fun getTransitionDuration(context: ActivityContext, isToState: Boolean) = 150

    override fun <T> getDepthUnchecked(context: T): Float where T : Context?, T : ActivityContext? {
        if (enableScalingRevealHomeAnimation()) {
            return DEPTH_15_PERCENT
        } else {
            return 0.5f
        }
    }

    override fun getWorkspaceScaleAndTranslation(launcher: Launcher): ScaleAndTranslation =
        getEditModeWorkspaceScaleAndTranslation(launcher)

    override fun getHotseatScaleAndTranslation(launcher: Launcher): ScaleAndTranslation {
        val offset = getEditModePageStripHeightPx(launcher).toFloat()
        return ScaleAndTranslation(1f, 0f, -offset)
    }

    override fun getWorkspaceBackgroundAlpha(launcher: Launcher): Float {
        return 0.2f
    }

    override fun onLeavingState(launcher: Launcher?, toState: LauncherState?) {
        // cleanup any changes to workspace
    }
}
