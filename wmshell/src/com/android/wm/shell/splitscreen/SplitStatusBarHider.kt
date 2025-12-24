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

package com.android.wm.shell.splitscreen

import android.graphics.Insets
import android.graphics.Rect
import android.os.Binder
import android.view.Display.DEFAULT_DISPLAY
import android.view.InsetsSource.FLAG_FORCE_CONSUMING
import android.view.InsetsState
import android.view.WindowInsets.Type.captionBar
import android.view.WindowInsets.Type.navigationBars
import android.view.WindowInsets.Type.statusBars
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.Flags.enableFlexibleTwoAppSplit
import com.android.wm.shell.RootDisplayAreaOrganizer
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.common.split.SplitState
import com.android.wm.shell.protolog.ShellProtoLogGroup
import com.android.wm.shell.shared.split.SplitScreenConstants.ANIMATING_OFFSCREEN_TAP
import com.android.wm.shell.shared.split.SplitScreenConstants.NOT_IN_SPLIT
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_10_90
import com.android.wm.shell.shared.split.SplitScreenConstants.SNAP_TO_2_90_10

/**
 * Determines when to request immersive mode override for status bar based on split state.
 * Currently we only want immersive mode when the following conditions are met
 *   * device is in left/right split
 *   * device is in split screen in either 10:90 or 90:10
 *
 * We want to reset status bar behavior whenever the user exits split screen.
 * NOTE: Specifically this means when split is NOT user visible, it is NOT tied to the activation of
 * [StageTaskListener] since that can outlast the visible interaction of split screen
 */
class SplitStatusBarHider(
    private val taskOrganizer: ShellTaskOrganizer,
    splitState: SplitState,
    rootDisplayAreaOrganizer: RootDisplayAreaOrganizer
) {

    /**
     * Current split layout user is in. NOTE this may be a valid layout even if [isSplitVisible] is
     * false
     */
    private var currentSplitState = NOT_IN_SPLIT
    /** Is the device in a left/right split state */
    private var isLeftRightSplit = false
    /** If split screen is currently visible to the user */
    private var isSplitVisible = false
    /**
     * Indicates whether this class has requested an override for putting status bar in
     * immersive mode or not
     */
    private var statusBarImmersiveForSplit = false
    /**
     * The height of the status bar, in pixels, in the current configuration.
     */
    private var statusBarHeight = 0

    private lateinit var displayToken: WindowContainerToken
    private val splitStateListener: SplitState.SplitStateChangeListener =
        SplitState.SplitStateChangeListener {
            updateStatusBarBehavior(it, isLeftRightSplit, isSplitVisible)
        }
    private val systemBarOwner = Binder()

    init {
        if (enableFlexibleTwoAppSplit()) {
            splitState.registerSplitStateChangeListener(splitStateListener)
            // TODO(b/362720126): Make this display aware instead of using default display
            displayToken = checkNotNull(rootDisplayAreaOrganizer
                .getDisplayTokenForDisplay(DEFAULT_DISPLAY))
        }
    }

    /**
     * Call this whenever user leaves split (regardless of the mode) so we can reset status bar
     * behavior overrides to let other apps resume control.
     */
    fun onSplitVisibilityChanged(splitVisible: Boolean) {
        updateStatusBarBehavior(currentSplitState, isLeftRightSplit, splitVisible)
    }

    /** Call when leftRight split changes (device rotation, unfold, etc) */
    fun onLeftRightSplitUpdated(leftRightSplit: Boolean) {
        updateStatusBarBehavior(currentSplitState, leftRightSplit, isSplitVisible)
    }

    fun onInsetsChanged(insetsState: InsetsState, rootBounds: Rect) {
        // Check the height of the status bar inset and store it.
        statusBarHeight =
            insetsState.calculateInsets(rootBounds, rootBounds, statusBars(), true).top
    }

    fun isStatusBarImmersive(): Boolean = statusBarImmersiveForSplit
    /**
     * Determines if we want to put the status bar in immersive mode or not based on
     * [currentSplitState], [isLeftRightSplit], and [isSplitVisible].
     * This will create and apply a new [WindowContainerTransaction]
     *
     * See explanation in class docs for more info.
     *
     * Currently we request status bar overrides to hide whenever we're in leftRight split and in
     * 10:90/90:10 flexible split
     */
    private fun updateStatusBarBehavior(splitState: Int, leftRightSplit: Boolean,
                                        splitVisible: Boolean) {
        if (!enableFlexibleTwoAppSplit()) {
            return
        }

        val shouldPutStatusBarInImmersive =
            shouldPutStatusBarInImmersive(splitState, leftRightSplit, splitVisible)

        if (splitState == currentSplitState &&
            leftRightSplit == isLeftRightSplit &&
            splitVisible == isSplitVisible &&
            shouldPutStatusBarInImmersive == statusBarImmersiveForSplit) {
            // No change
            ProtoLog.v(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                "Updating status bar for split, no change in state")
            return
        }
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
            "Updating status bar override leftRight=%s currentSplit=%d " +
                    "splitVisible=%s overridden=%s", isLeftRightSplit, currentSplitState,
            isSplitVisible, statusBarImmersiveForSplit)
        isLeftRightSplit = leftRightSplit
        currentSplitState = splitState
        isSplitVisible = splitVisible

        if (shouldPutStatusBarInImmersive == statusBarImmersiveForSplit) {
            // No change in override state
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                "No change in status bar override state")
        } else {
            ProtoLog.d(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                "Updating status bar override to %s", shouldPutStatusBarInImmersive)
            setStatusBarVisibilityOverride(shouldPutStatusBarInImmersive)
        }
    }

    /**
    * Creates and applies a new [WindowContainerTransaction] to set or reset status bar immersive
     * mode based on [forceImmersive]
    */
    private fun setStatusBarVisibilityOverride(forceImmersive: Boolean) {
        val wct = WindowContainerTransaction()
        if (forceImmersive) {
            // Add a "replacement" inset for the status bar that is being hidden. This ensures that
            // apps lay out their contents in 90:10 in the same way as they do in 70:30, even though
            // we are hiding the status bar in 90:10.
            val flags = FLAG_FORCE_CONSUMING
            wct.addInsetsSource(displayToken, systemBarOwner, 0, captionBar(),
                Insets.of(0, statusBarHeight, 0, 0), null, flags)
            wct.setSystemBarVisibilityOverride(displayToken,
                systemBarOwner,
                navigationBars() or captionBar() /*forciblyShowingTypes*/,
                statusBars() /*forciblyHidingTypes*/)
        } else {
            wct.removeInsetsSource(displayToken, systemBarOwner, 0, captionBar())
            wct.setSystemBarVisibilityOverride(displayToken,
                systemBarOwner,
                0 /*forciblyShowingTypes*/,
                0 /*forciblyHidingTypes*/)
        }
        taskOrganizer.applyTransaction(wct)
        statusBarImmersiveForSplit = forceImmersive
    }

    /**
     * @return true if current state based on [isSplitVisible], [isLeftRightSplit], and
     * [currentSplitState] indicates device needs to be in immersive mode.
     */
    private fun shouldPutStatusBarInImmersive(
        currentSplitState: Int,
        isLeftRightSplit: Boolean,
        isSplitVisible: Boolean
    ): Boolean {
        val resetStatusBarBehavior = !isSplitVisible || !isLeftRightSplit
        if (resetStatusBarBehavior) {
            // Device state says we don't want to hide status bar
            return false
        }

        // Device state is fine to put into immersive, hide if we're in any flex split modes
        return hideStatusBarForSplitState(currentSplitState)
    }

    private fun hideStatusBarForSplitState(splitState: Int): Boolean {
        return when (splitState) {
            SNAP_TO_2_10_90 -> true
            SNAP_TO_2_90_10 -> true
            ANIMATING_OFFSCREEN_TAP -> true
            else -> false
        }
    }
}
