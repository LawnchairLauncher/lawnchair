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

package com.android.systemui.animation

import android.app.WindowConfiguration.ACTIVITY_TYPE_HOME
import android.os.IBinder
import android.util.Log
import android.util.RotationUtils
import android.view.SurfaceControl
import android.window.DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX
import android.window.DesktopModeFlags.ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS_BUGFIX
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo
import android.window.WindowContainerToken
import com.android.systemui.animation.RemoteTransitionHelper.Companion.CLOSING_MODES
import com.android.systemui.animation.RemoteTransitionHelper.Companion.OPENING_MODES
import com.android.wm.shell.shared.CounterRotator
import com.android.wm.shell.shared.TransitionUtil.FLAG_IS_DESKTOP_WALLPAPER_ACTIVITY
import com.android.wm.shell.shared.TransitionUtil.isClosingMode
import com.android.wm.shell.shared.TransitionUtil.isClosingType

private const val TAG = "DefaultTransitionHelper"

/**
 * General purpose RemoteTransitionHelper that initializes changes with particular attention to the
 * wallpaper and Launcher.
 */
class DefaultTransitionHelper : RemoteTransitionHelper {
    private val launcherRotators = mutableMapOf<IBinder, CounterRotator>()
    private val wallpaperRotators = mutableMapOf<IBinder, CounterRotator>()

    override fun setUpAnimation(
        token: IBinder,
        info: TransitionInfo,
        transaction: SurfaceControl.Transaction,
        finishCallback: IRemoteTransitionFinishedCallback?,
    ) {
        val launcherRotator = CounterRotator()
        launcherRotators[token] = launcherRotator
        val wallpaperRotator = CounterRotator()
        wallpaperRotators[token] = wallpaperRotator

        var launcher: Launcher? = null
        var rotationDelta = 0
        var displayWidth = 0f
        var displayHeight = 0f

        // First we extract the Launcher (if it is part of the transition) and the rotation delta,
        // as we will need them to process each change.
        info.changes.forEachIndexed { index, change ->
            // No need to keep going if we already have the info we needed to extract.
            if (launcher != null && rotationDelta != 0) return

            // Identify the Launcher task, its position, and its role in the transition.
            if (change.taskInfo?.activityType == ACTIVITY_TYPE_HOME) {
                val isOpening = OPENING_MODES.contains(change.mode)
                launcher =
                    Launcher(
                        change,
                        // If Launcher is opening, make sure it is at the back so we can put other
                        // surfaces in front of it. Otherwise give it the default layer (reverse of
                        // position in changes).
                        layer =
                            if (isOpening) {
                                info.changes.size * 3
                            } else {
                                info.changes.size - index
                            },
                        parentToken = change.parent,
                        info = info,
                        isOpening = isOpening,
                    )
            }

            // Find the rotation delta. This is the same across all surfaces that have a non-zero
            // value, so we just need to find the first one. We only look at root surfaces for this,
            // as child surfaces will inherit their parent's rotation.
            if (change.parent == null && rotationDelta == 0) {
                rotationDelta =
                    RotationUtils.deltaRotation(change.startRotation, change.endRotation)
                displayWidth = change.endAbsBounds.width().toFloat()
                displayHeight = change.endAbsBounds.height().toFloat()
            }
        }

        if (launcher?.parent != null && rotationDelta != 0) {
            launcherRotator.setup(
                transaction,
                launcher!!.parent!!.leash,
                rotationDelta,
                displayWidth,
                displayHeight,
            )
            transaction.setLayer(launcherRotator.surface, launcher!!.layer)
        }

        if (launcher?.isOpening == true) {
            setUpReturnToHome(info, launcher!!, transaction, launcherRotator)
        } else {
            setUpInternal(
                info,
                launcher,
                launcherRotator,
                wallpaperRotator,
                rotationDelta,
                transaction,
                displayWidth,
                displayHeight,
            )
        }

        transaction.apply()
    }

    /** Sets up the surfaces for a transition that puts Launcher in front. */
    private fun setUpReturnToHome(
        info: TransitionInfo,
        launcher: Launcher,
        transaction: SurfaceControl.Transaction,
        launcherRotator: CounterRotator,
    ) {
        info.changes.forEachIndexed { index, change ->
            // We only care about independent surfaces, as dependent ones inherit their parent's
            // properties.
            if (!TransitionInfo.isIndependent(change, info)) return@forEachIndexed

            if (CLOSING_MODES.contains(change.mode)) {
                // Launcher expects closing surfaces to be "boosted" above itself in the ordering.
                transaction.setLayer(change.leash, info.changes.size * 3 - index)
                // Add the surface to the Launcher's rotator, so their rotations match.
                launcherRotator.addChild(transaction, change.leash)
            }

            // Make the wallpaper immediately visible.
            if ((change.flags and TransitionInfo.FLAG_IS_WALLPAPER) != 0) {
                transaction.show(change.leash)
                transaction.setAlpha(change.leash, 1f)
            }

            // If needed, reset the alpha of the Launcher leash to give the Launcher time to hide
            // its Views before the exit-desktop animation starts.
            if (ENABLE_DESKTOP_WINDOWING_EXIT_TRANSITIONS_BUGFIX.isTrue) {
                if (
                    !isClosingType(info.type) &&
                        !ENABLE_DESKTOP_WINDOWING_EXIT_BY_MINIMIZE_TRANSITION_BUGFIX.isTrue
                ) {
                    return@forEachIndexed
                }

                if (
                    isClosingMode(change.mode) &&
                        (change.taskInfo?.isFreeform == true ||
                            (change.flags and FLAG_IS_DESKTOP_WALLPAPER_ACTIVITY) != 0)
                ) {
                    transaction.setAlpha(launcher.change.leash, 0f)
                }
            }
        }
    }

    /** Sets up the surfaces for any transition that does not involve putting Launcher in front. */
    private fun setUpInternal(
        info: TransitionInfo,
        launcher: Launcher?,
        launcherRotator: CounterRotator,
        wallpaperRotator: CounterRotator,
        rotationDelta: Int,
        transaction: SurfaceControl.Transaction,
        displayWidth: Float,
        displayHeight: Float,
    ) {
        // Make sure Launcher is rotated using its own rotator.
        if (launcher != null) launcherRotator.addChild(transaction, launcher.change.leash)

        // Set up the wallpaper's rotation.
        val wallpaper = info.changes.find { (it.flags and TransitionInfo.FLAG_IS_WALLPAPER) != 0 }
        val wallpaperParent = wallpaper?.parent
        if (wallpaperParent != null && rotationDelta != 0) {
            val parent = info.getChange(wallpaperParent)
            if (parent != null) {
                wallpaperRotator.setup(
                    transaction,
                    parent.leash,
                    rotationDelta,
                    displayWidth,
                    displayHeight,
                )
                transaction.setLayer(wallpaperRotator.surface, -1)
                wallpaperRotator.addChild(transaction, wallpaper.leash)
            } else {
                Log.e(
                    TAG,
                    "Malformed: $wallpaper has parent=$wallpaperParent, which is not part of the " +
                        "transition info=$info.",
                )
            }
        }
    }

    override fun cleanUpAnimation(token: IBinder, transaction: SurfaceControl.Transaction) {
        launcherRotators.remove(token)?.cleanUp(transaction)
        wallpaperRotators.remove(token)?.cleanUp(transaction)
        transaction.apply()
    }

    /** Wrapper for information related to a change for the Launcher surface. */
    private class Launcher(
        val change: TransitionInfo.Change,
        val layer: Int,
        val isOpening: Boolean,
        parentToken: WindowContainerToken?,
        info: TransitionInfo,
    ) {
        val parent: TransitionInfo.Change? = parentToken?.let { info.getChange(parentToken) }
    }
}
