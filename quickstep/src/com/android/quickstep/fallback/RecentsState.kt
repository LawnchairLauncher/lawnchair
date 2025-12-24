/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.fallback

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import androidx.annotation.FloatRange
import com.android.app.animation.Interpolators
import com.android.launcher3.DeviceProfile
import com.android.launcher3.Flags
import com.android.launcher3.LauncherState
import com.android.launcher3.LauncherState.FLAG_CLOSE_POPUPS
import com.android.launcher3.R
import com.android.launcher3.anim.AnimatorPlaybackController
import com.android.launcher3.anim.PendingAnimation
import com.android.launcher3.statemanager.BaseState
import com.android.launcher3.statemanager.BaseState.FLAG_DISABLE_RESTORE
import com.android.launcher3.util.OverviewReleaseFlags.enableGridOnlyOverview
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.ScrimColors
import com.android.quickstep.views.RecentsView
import com.android.quickstep.views.RecentsViewContainer
import kotlin.math.min

/** State definition for Fallback recents */
open class RecentsState(@JvmField val ordinal: Int, private val mFlags: Int) :
    BaseState<RecentsState> {
    init {
        sAllStates[ordinal] = this
    }

    protected var backAnimationController: AnimatorPlaybackController? = null

    override fun toString() =
        when (ordinal) {
            DEFAULT_STATE_ORDINAL -> "RECENTS_DEFAULT"
            MODAL_TASK_ORDINAL -> "RECENTS_MODAL_TASK"
            BACKGROUND_APP_ORDINAL -> "RECENTS_BACKGROUND_APP"
            HOME_STATE_ORDINAL -> "RECENTS_HOME"
            BG_LAUNCHER_ORDINAL -> "RECENTS_BG_LAUNCHER"
            OVERVIEW_SPLIT_SELECT_ORDINAL -> "RECENTS_SPLIT_SELECT"
            else -> "RECENTS Unknown Ordinal-$ordinal"
        }

    override fun hasFlag(mask: Int) = (mFlags and mask) != 0

    override fun getTransitionDuration(context: ActivityContext, isToState: Boolean) = 250

    override fun getHistoryForState(previousState: RecentsState) = DEFAULT

    /**
     * For this state, how modal should over view been shown. 0 modalness means all tasks drawn, 1
     * modalness means the current task is show on its own.
     */
    fun getOverviewModalness() = if (hasFlag(FLAG_MODAL)) 1f else 0f

    fun isFullScreen() = hasFlag(FLAG_FULL_SCREEN)

    /** For this state, whether clear all button should be shown. */
    fun hasClearAllButton() = hasFlag(FLAG_CLEAR_ALL_BUTTON)

    /** For this state, whether add desk button should be shown. */
    fun hasAddDeskButton() = hasFlag(FLAG_ADD_DESK_BUTTON)

    /** For this state, whether overview actions should be shown. */
    fun hasOverviewActions() = hasFlag(FLAG_OVERVIEW_ACTIONS)

    /** For this state, whether live tile should be shown. */
    fun hasLiveTile() = hasFlag(FLAG_LIVE_TILE)

    /** For this state, what color scrim should be drawn behind overview. */
    fun getScrimColor(context: Context) =
        ScrimColors(
            /* backgroundColor= */ if (hasFlag(FLAG_SCRIM))
                Themes.getAttrColor(context, R.attr.overviewScrimColor)
            else Color.TRANSPARENT,
            /* foregroundColor= */ Color.TRANSPARENT,
        )

    open fun getOverviewScaleAndOffset(container: RecentsViewContainer) =
        floatArrayOf(NO_SCALE, NO_OFFSET)

    /** For this state, whether tasks should layout as a grid rather than a list. */
    override fun displayOverviewTasksAsGrid(deviceProfile: DeviceProfile) =
        hasFlag(FLAG_SHOW_AS_GRID) && deviceProfile.deviceProperties.isTablet

    override fun showTaskThumbnailSplash() = hasFlag(FLAG_TASK_THUMBNAIL_SPLASH)

    override fun showExplodedDesktopView() =
        hasFlag(FLAG_SHOW_EXPLODED_DESKTOP_VIEW) && Flags.enableDesktopExplodedView()

    /** True if the state has overview panel visible. */
    fun isRecentsViewVisible() = hasFlag(FLAG_RECENTS_VIEW_VISIBLE)

    /**
     * Handles the back invocation. If a live task is present and visible, launch it; if present but
     * not fully visible, snap scroll to it; otherwise, return home.
     *
     * <p>Ignore the invocation if a state transition is still in progress.
     *
     * @param container The RecentsViewContainer.
     */
    open fun onBackInvoked(container: RecentsViewContainer) {
        val recentsView = container.getOverviewPanel<RecentsView<*, *>>()
        val runningTaskView = recentsView.runningTaskView
        when {
            recentsView.stateManager.isInTransition -> {} // NO-OP.
            runningTaskView == null || runningTaskView.isBeingDismissed -> recentsView.startHome()
            recentsView.isTaskViewFullyVisible(runningTaskView) ->
                runningTaskView.launchWithAnimation()
            else -> {
                backAnimationController?.reverse()
                recentsView.snapToPage(recentsView.indexOfChild(runningTaskView))
            }
        }
    }

    /**
     * Creates the back animation.
     *
     * @param container The RecentsViewContainer.
     */
    open fun onBackStarted(container: RecentsViewContainer) {
        val recentsView = container.getOverviewPanel<RecentsView<*, *>>()
        val runningTaskView = recentsView.runningTaskView

        val targetScale: Float =
            when {
                runningTaskView == null -> PREDICTIVE_BACK_MIN_RECENTS_SCALE_HOME
                recentsView.isTaskViewFullyVisible(runningTaskView) ->
                    PREDICTIVE_BACK_MAX_RECENTS_SCALE_LAUNCH
                else -> PREDICTIVE_BACK_MIN_RECENTS_SCALE_SCROLL
            }
        backAnimationController =
            PendingAnimation(PREDICTIVE_BACK_DURATION)
                .apply {
                    addFloat(
                        recentsView,
                        RecentsView.RECENTS_SCALE_PROPERTY,
                        1f,
                        targetScale,
                        Interpolators.LINEAR,
                    )
                    addFloat(
                        recentsView,
                        RecentsView.FULLSCREEN_PROGRESS,
                        0f,
                        PREDICTIVE_BACK_MAX_FULLSCREEN_PROGRESS_LAUNCH,
                        Interpolators.LINEAR,
                    )
                }
                .createPlaybackController()
    }

    /**
     * Updates the back animation progress.
     *
     * @param container The RecentsViewContainer.
     * @param backProgress The current back progress (0.0 to 1.0).
     */
    open fun onBackProgressed(
        container: RecentsViewContainer?,
        @FloatRange(from = 0.0, to = 1.0) backProgress: Float,
    ) {
        backAnimationController?.setPlayFraction(backProgress)
    }

    private class ModalState(id: Int, flags: Int) : RecentsState(id, flags) {
        override fun getOverviewScaleAndOffset(container: RecentsViewContainer): FloatArray =
            if (enableGridOnlyOverview()) {
                super.getOverviewScaleAndOffset(container)
            } else getOverviewScaleAndOffsetForModalState(container.getOverviewPanel())

        private fun getOverviewScaleAndOffsetForModalState(
            recentsView: RecentsView<*, *>
        ): FloatArray {
            val taskSize = recentsView.selectedTaskBounds
            val modalTaskSize = Rect().apply { recentsView.getModalTaskSize(this) }
            val scale =
                min(
                    modalTaskSize.height().toFloat() / taskSize.height(),
                    modalTaskSize.width().toFloat() / taskSize.width(),
                )
            return floatArrayOf(scale, LauncherState.NO_OFFSET)
        }

        override fun onBackInvoked(container: RecentsViewContainer) {
            container.goToRecentsState(DEFAULT, true)
        }

        override fun onBackStarted(container: RecentsViewContainer) {
            val recentsView = container.getOverviewPanel<RecentsView<*, *>>()
            backAnimationController =
                PendingAnimation(PREDICTIVE_BACK_DURATION)
                    .apply {
                        addFloat(
                            recentsView,
                            RecentsView.TASK_MODALNESS,
                            1f,
                            PREDICTIVE_BACK_MIN_TASK_MODALNESS,
                            Interpolators.LINEAR,
                        )
                    }
                    .createPlaybackController()
        }
    }

    private class BackgroundAppState(id: Int, flags: Int) : RecentsState(id, flags) {
        override fun getOverviewScaleAndOffset(container: RecentsViewContainer) =
            floatArrayOf(
                container.getOverviewPanel<RecentsView<*, *>>().maxScaleForFullScreen,
                LauncherState.NO_OFFSET,
            )
    }

    private class BgLauncherState(id: Int, flags: Int) : RecentsState(id, flags) {
        override fun getOverviewScaleAndOffset(container: RecentsViewContainer) =
            floatArrayOf(NO_SCALE, 1f)
    }

    override fun equals(other: Any?) =
        when (other) {
            is RecentsState -> other === this
            is LauncherState -> other === this.toLauncherState()
            else -> false
        }

    companion object {
        private val FLAG_MODAL = BaseState.getFlag(0)
        private val FLAG_CLEAR_ALL_BUTTON = BaseState.getFlag(1)
        private val FLAG_FULL_SCREEN = BaseState.getFlag(2)
        private val FLAG_OVERVIEW_ACTIONS = BaseState.getFlag(3)
        private val FLAG_SHOW_AS_GRID = BaseState.getFlag(4)
        private val FLAG_SCRIM = BaseState.getFlag(5)
        private val FLAG_LIVE_TILE = BaseState.getFlag(6)
        private val FLAG_RECENTS_VIEW_VISIBLE = BaseState.getFlag(7)
        private val FLAG_TASK_THUMBNAIL_SPLASH = BaseState.getFlag(8)
        private val FLAG_ADD_DESK_BUTTON = BaseState.getFlag(9)
        private val FLAG_SHOW_EXPLODED_DESKTOP_VIEW = BaseState.getFlag(10)

        private const val PREDICTIVE_BACK_DURATION = 1000L
        private const val PREDICTIVE_BACK_MAX_RECENTS_SCALE_LAUNCH = 1.1f
        private const val PREDICTIVE_BACK_MIN_RECENTS_SCALE_HOME = 0.9f
        private const val PREDICTIVE_BACK_MAX_FULLSCREEN_PROGRESS_LAUNCH = 0.1f
        private const val PREDICTIVE_BACK_MIN_RECENTS_SCALE_SCROLL = 0.95f
        private const val PREDICTIVE_BACK_MIN_TASK_MODALNESS = 0.8f

        const val DEFAULT_STATE_ORDINAL = 0
        const val MODAL_TASK_ORDINAL = 1
        const val BACKGROUND_APP_ORDINAL = 2
        const val HOME_STATE_ORDINAL = 3
        const val BG_LAUNCHER_ORDINAL = 4
        const val OVERVIEW_SPLIT_SELECT_ORDINAL = 5

        private val sAllStates = arrayOfNulls<RecentsState>(6)

        @JvmField
        val DEFAULT: RecentsState =
            RecentsState(
                DEFAULT_STATE_ORDINAL,
                (FLAG_DISABLE_RESTORE or
                    FLAG_CLEAR_ALL_BUTTON or
                    FLAG_OVERVIEW_ACTIONS or
                    FLAG_SHOW_AS_GRID or
                    FLAG_SCRIM or
                    FLAG_LIVE_TILE or
                    FLAG_RECENTS_VIEW_VISIBLE or
                    FLAG_ADD_DESK_BUTTON or
                    FLAG_SHOW_EXPLODED_DESKTOP_VIEW),
            )
        @JvmField
        val MODAL_TASK: RecentsState =
            ModalState(
                MODAL_TASK_ORDINAL,
                (FLAG_DISABLE_RESTORE or
                    FLAG_OVERVIEW_ACTIONS or
                    FLAG_MODAL or
                    FLAG_SHOW_AS_GRID or
                    FLAG_SCRIM or
                    FLAG_LIVE_TILE or
                    FLAG_RECENTS_VIEW_VISIBLE or
                    FLAG_SHOW_EXPLODED_DESKTOP_VIEW),
            )
        @JvmField
        val BACKGROUND_APP: RecentsState =
            BackgroundAppState(
                BACKGROUND_APP_ORDINAL,
                (FLAG_DISABLE_RESTORE or
                    BaseState.FLAG_NON_INTERACTIVE or
                    FLAG_FULL_SCREEN or
                    FLAG_RECENTS_VIEW_VISIBLE or
                    FLAG_TASK_THUMBNAIL_SPLASH),
            )
        @JvmField val HOME: RecentsState = RecentsState(HOME_STATE_ORDINAL, 0)
        @JvmField val BG_LAUNCHER: RecentsState = BgLauncherState(BG_LAUNCHER_ORDINAL, 0)
        @JvmField
        val OVERVIEW_SPLIT_SELECT: RecentsState =
            RecentsState(
                OVERVIEW_SPLIT_SELECT_ORDINAL,
                (FLAG_SHOW_AS_GRID or
                    FLAG_SCRIM or
                    FLAG_RECENTS_VIEW_VISIBLE or
                    FLAG_CLOSE_POPUPS or
                    FLAG_DISABLE_RESTORE or
                    FLAG_SHOW_EXPLODED_DESKTOP_VIEW),
            )

        /** Returns the corresponding RecentsState from ordinal provided */
        @JvmStatic fun stateFromOrdinal(ordinal: Int) = sAllStates[ordinal]!!

        private const val NO_OFFSET = 0f
        private const val NO_SCALE = 1f
    }
}
