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
package com.android.wm.shell.desktopmode.multidesks

import android.content.Context
import android.graphics.Rect
import android.os.IBinder
import android.view.Choreographer
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopModeTransitionTypes
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.desktopmode.DesktopWallpaperActivity
import com.android.wm.shell.desktopmode.multidesks.animation.DeskSwitchAnimationUtils
import com.android.wm.shell.desktopmode.multidesks.animation.DeskSwitchAnimationUtils.FADE_IN_SPRING_CONFIG
import com.android.wm.shell.desktopmode.multidesks.animation.DeskSwitchAnimationUtils.FADE_IN_START_FRACTION
import com.android.wm.shell.desktopmode.multidesks.animation.DeskSwitchAnimationUtils.FADE_OUT_SPRING_CONFIG
import com.android.wm.shell.desktopmode.multidesks.animation.DeskSwitchAnimationUtils.FADE_OUT_START_FRACTION
import com.android.wm.shell.desktopmode.multidesks.animation.DeskSwitchAnimationUtils.LATERAL_MOTION_SCREEN_PCT
import com.android.wm.shell.desktopmode.multidesks.animation.DeskSwitchAnimationUtils.LATERAL_MOVEMENT_SPRING_CONFIG
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.shared.animation.PhysicsAnimator
import com.android.wm.shell.shared.desktopmode.DesktopState
import com.android.wm.shell.transition.Transitions

/** A transition handler to animate a switch between two desks in the same display. */
class DeskSwitchTransitionHandler(
    private val context: Context,
    private val desktopUserRepositories: DesktopUserRepositories,
    private val desktopState: DesktopState,
    private val transitions: Transitions,
    private val displayController: DisplayController,
    private val transactionProvider: () -> SurfaceControl.Transaction,
) : Transitions.TransitionHandler {

    constructor(
        context: Context,
        desktopUserRepositories: DesktopUserRepositories,
        desktopState: DesktopState,
        transitions: Transitions,
        displayController: DisplayController,
    ) : this(
        context = context,
        desktopUserRepositories = desktopUserRepositories,
        desktopState = desktopState,
        transitions = transitions,
        displayController = displayController,
        transactionProvider = { SurfaceControl.Transaction() },
    )

    private val pendingTransitions = mutableMapOf<IBinder, PendingSwitch>()

    /** Adds a pending transition that will switch between two desks in the same display. */
    fun addPendingTransition(
        transition: IBinder,
        userId: Int,
        displayId: Int,
        fromDeskId: Int,
        toDeskId: Int,
    ) {
        pendingTransitions[transition] =
            PendingSwitch(
                displayId = displayId,
                userId = userId,
                fromDeskId = fromDeskId,
                toDeskId = toDeskId,
            )
    }

    /** Starts a transition to switch between two desks in the same display. */
    fun startTransition(
        wct: WindowContainerTransaction,
        userId: Int,
        displayId: Int,
        fromDeskId: Int,
        toDeskId: Int,
    ): IBinder {
        val transition =
            transitions.startTransition(
                DesktopModeTransitionTypes.TRANSIT_DESKTOP_MODE_DESK_TO_DESK_SWITCH,
                wct,
                this,
            )
        pendingTransitions[transition] =
            PendingSwitch(
                displayId = displayId,
                userId = userId,
                fromDeskId = fromDeskId,
                toDeskId = toDeskId,
            )
        return transition
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        // Find the changes using the pending state set for transitions started by this handler.
        // If there's no pending state try to detect a desk-switch for transitions started
        // elsewhere.
        val changes =
            pendingTransitions[transition]?.let { pendingSwitch ->
                getDeskSwitchChanges(pendingSwitch, info)
            } ?: return false
        val displayLayout = displayController.getDisplayLayout(changes.displayId) ?: return false
        val displayBounds = Rect(0, 0, displayLayout.width(), displayLayout.height())
        val lateralMotionWidth = (displayBounds.width() * LATERAL_MOTION_SCREEN_PCT).toInt()

        // All tasks inside each desk will move laterally by the same amount, so use display bounds
        // as the reference point for the physics animator.
        val fromDeskStartBounds = Rect(displayBounds)
        val fromDeskEndBounds =
            when (changes.direction) {
                DeskSwitchChanges.Direction.LEFT ->
                    Rect(fromDeskStartBounds).apply {
                        // Moving to desk on the left, so motion is to the right, hence the positive
                        // offset.
                        offset(lateralMotionWidth, 0)
                    }
                DeskSwitchChanges.Direction.RIGHT ->
                    Rect(fromDeskStartBounds).apply {
                        // Moving to desk on the right, so motion is to the left, hence the negative
                        // offset.
                        offset(-lateralMotionWidth, 0)
                    }
            }
        val toDeskStartBounds =
            when (changes.direction) {
                DeskSwitchChanges.Direction.LEFT ->
                    Rect(displayBounds).apply {
                        // Moving to desk on the left, so motion is to the right, hence the negative
                        // offset.
                        offset(-lateralMotionWidth, 0)
                    }
                DeskSwitchChanges.Direction.RIGHT ->
                    Rect(displayBounds).apply {
                        // Moving to desk on the right, so motion is to the left, hence the positive
                        // offset.
                        offset(lateralMotionWidth, 0)
                    }
            }
        val toDeskEndBounds = Rect(displayBounds)

        startTransaction.apply {
            changes.fromDeskTasks.forEach { c -> setAlpha(c.leash, 1f) }
            changes.fromDesk?.leash?.let { leash ->
                setPosition(
                    leash,
                    fromDeskStartBounds.left.toFloat(),
                    fromDeskStartBounds.top.toFloat(),
                )
            }
            changes.toDeskTasks.forEach { c -> setAlpha(c.leash, 0f) }
            changes.toDesk?.leash?.let { leash ->
                setPosition(
                    leash,
                    toDeskStartBounds.left.toFloat(),
                    toDeskStartBounds.top.toFloat(),
                )
            }
            apply()
        }

        // Fade in/out animations will be started half-way through the bounds animation with
        // different springs, so since they could finish at different times we need to track each
        // finish separately to trigger the |finishCallback| once all have finished.
        var runningAnimationCount = 0
        val onAnimationEnd = {
            runningAnimationCount--
            if (runningAnimationCount <= 0) {
                logD("All animations finished, finishing transition")
                finishTransaction.apply {
                    changes.fromDeskTasks.forEach { c -> setAlpha(c.leash, 0f) }
                    changes.toDeskTasks.forEach { c -> setAlpha(c.leash, 1f) }
                }
                finishCallback.onTransitionFinished(/* wct */ null)
            }
        }

        // Flags to ensure we only trigger the alpha animations once.
        var isFadeOutStarted = false
        var isFadeInStarted = false

        // Now actually start the animations.
        logD("startAnimation: changes=%s", changes)
        val tx = transactionProvider()
        // First the wallpaper animation.
        startWallpaperAnimation(
            displayId = changes.displayId,
            numberOfDesks = changes.totalDesks,
            fromDeskIndex = changes.fromDeskPosition,
            toDeskIndex = changes.toDeskPosition,
        )
        // Then the bounds animation, which triggers fade-in/out animations within it.
        PhysicsAnimator.getInstance(
                DeskSwitchAnimationUtils.DeskBoundsChange(
                    fromDeskBounds = Rect(fromDeskStartBounds),
                    toDeskBounds = Rect(toDeskStartBounds),
                )
            )
            .spring(
                property = DeskSwitchAnimationUtils.DESK_BOUNDS_FROM_X,
                toPosition = fromDeskEndBounds.left.toFloat(),
                config = LATERAL_MOVEMENT_SPRING_CONFIG,
            )
            .spring(
                property = DeskSwitchAnimationUtils.DESK_BOUNDS_TO_X,
                toPosition = toDeskEndBounds.left.toFloat(),
                config = LATERAL_MOVEMENT_SPRING_CONFIG,
            )
            .addUpdateListener { change, _ ->
                val fromDeskAnimBounds = change.fromDeskBounds
                val toDeskAnimBounds = change.toDeskBounds
                val animFraction =
                    getAnimationFraction(
                        startBounds = fromDeskStartBounds,
                        endBounds = fromDeskEndBounds,
                        animBounds = fromDeskAnimBounds,
                    )

                // Remap the progress for the fade-out, which starts at 10%
                // This converts the [0.1, 1.0] range of animFraction into a [0.0, 1.0] range.
                val fadeOutTotalRange = 1f - FADE_OUT_START_FRACTION
                val fadeOutProgress =
                    ((animFraction - FADE_OUT_START_FRACTION) / fadeOutTotalRange).coerceIn(
                        0f,
                        1f,
                    ) // Clamp between 0 and 1

                // Remap the progress for the fade-in, which starts at 40%
                val fadeInTotalRange = 1f - FADE_IN_START_FRACTION
                val fadeInProgress =
                    ((animFraction - FADE_IN_START_FRACTION) / fadeInTotalRange).coerceIn(
                        0f,
                        1f,
                    ) // Clamp between 0 and 1

                // When progress > 10% for the first time, start the fade-out spring
                if (animFraction >= FADE_OUT_START_FRACTION && !isFadeOutStarted) {
                    isFadeOutStarted = true
                    val fadeOutTx = transactionProvider()
                    PhysicsAnimator.getInstance(
                            DeskSwitchAnimationUtils.DeskOpacityChange(
                                leashes = changes.fromDeskTasks.map { it.leash },
                                alpha = 1f,
                            )
                        )
                        .spring(
                            property = DeskSwitchAnimationUtils.DeskAlphaProperty(fadeOutTx),
                            toPosition = 0f,
                            config = FADE_OUT_SPRING_CONFIG,
                        )
                        .withEndActions({
                            logD("fade out animation finished")
                            onAnimationEnd()
                        })
                        .apply {
                            runningAnimationCount++
                            logD("fade out animation starting")
                            start()
                        }
                }

                // When progress > 40% for the first time, start the fade-in spring
                if (animFraction >= FADE_IN_START_FRACTION && !isFadeInStarted) {
                    isFadeInStarted = true
                    val fadeInTx = transactionProvider()
                    // Set the desk alpha to 1 so its children can be shown
                    fadeInTx.apply {
                        changes.toDesk?.leash?.let { leash -> setAlpha(leash, 1f) }
                        apply()
                    }
                    PhysicsAnimator.getInstance(
                            DeskSwitchAnimationUtils.DeskOpacityChange(
                                leashes = changes.toDeskTasks.map { it.leash },
                                alpha = 0f,
                            )
                        )
                        .spring(
                            property = DeskSwitchAnimationUtils.DeskAlphaProperty(fadeInTx),
                            toPosition = 1f,
                            config = FADE_IN_SPRING_CONFIG,
                        )
                        .withEndActions({
                            logD("fade in animation finished")
                            onAnimationEnd()
                        })
                        .apply {
                            runningAnimationCount++
                            logD("fade in animation starting")
                            start()
                        }
                }

                if (DeskSwitchAnimationUtils.DEBUG_ANIMATION) {
                    logD(
                        "tick(%d): fromAnimBounds=%s toAnimBounds=%s fadeOut=%d fadeIn=%d",
                        animFraction,
                        fromDeskAnimBounds,
                        toDeskAnimBounds,
                        fadeOutProgress,
                        fadeInProgress,
                    )
                }
                tx.apply {
                    changes.fromDesk?.leash?.let { leash ->
                        setPosition(
                            leash,
                            fromDeskAnimBounds.left.toFloat(),
                            fromDeskAnimBounds.top.toFloat(),
                        )
                    }
                    changes.toDesk?.leash?.let { leash ->
                        setPosition(
                            leash,
                            toDeskAnimBounds.left.toFloat(),
                            toDeskAnimBounds.top.toFloat(),
                        )
                    }
                    setFrameTimeline(Choreographer.getInstance().vsyncId)
                    apply()
                }
            }
            .withEndActions({
                logD("lateral animation finished")
                onAnimationEnd()
            })
            .apply {
                runningAnimationCount++
                start()
            }
        return true
    }

    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? = null

    override fun onTransitionConsumed(
        transition: IBinder,
        aborted: Boolean,
        finishTransaction: SurfaceControl.Transaction?,
    ) {
        // An aborted pending switch transition might mean we're moving from an empty desk to
        // another empty desk, so there won't be animation targets. The desktop wallpaper still
        // needs to be animated though.
        if (!aborted) return
        val pendingSwitch = pendingTransitions.remove(transition) ?: return
        val repository = desktopUserRepositories.getProfile(pendingSwitch.userId)
        val fromDeskIndex = repository.getDeskPosition(pendingSwitch.fromDeskId) ?: return
        val toDeskIndex = repository.getDeskPosition(pendingSwitch.toDeskId) ?: return
        startWallpaperAnimation(
            displayId = pendingSwitch.displayId,
            numberOfDesks = repository.getNumberOfDesks(pendingSwitch.displayId),
            fromDeskIndex = fromDeskIndex,
            toDeskIndex = toDeskIndex,
        )
    }

    private fun getDeskSwitchChanges(
        pendingSwitch: PendingSwitch,
        info: TransitionInfo,
    ): DeskSwitchChanges? {
        return getDeskSwitchChanges(
            info = info,
            displayId = pendingSwitch.displayId,
            userId = pendingSwitch.userId,
            fromDeskId = pendingSwitch.fromDeskId,
            toDeskId = pendingSwitch.toDeskId,
        )
    }

    private fun getDeskSwitchChanges(
        info: TransitionInfo,
        displayId: Int,
        userId: Int,
        fromDeskId: Int,
        toDeskId: Int,
    ): DeskSwitchChanges? {
        val fromDesk = info.changes.find { c -> c.taskInfo?.taskId == fromDeskId }
        val toDesk = info.changes.find { c -> c.taskInfo?.taskId == toDeskId }
        val repository = desktopUserRepositories.getProfile(userId)
        val fromDeskPosition = repository.getDeskPosition(fromDeskId) ?: return null
        val toDeskPosition = repository.getDeskPosition(toDeskId) ?: return null
        if (fromDeskPosition == toDeskPosition) return null
        val direction =
            if (fromDeskPosition < toDeskPosition) {
                DeskSwitchChanges.Direction.RIGHT
            } else {
                DeskSwitchChanges.Direction.LEFT
            }
        val fromDeskTasks = info.changes.filter { c -> c.taskInfo?.parentTaskId == fromDeskId }
        val toDeskTasks = info.changes.filter { c -> c.taskInfo?.parentTaskId == toDeskId }
        return DeskSwitchChanges(
            displayId = displayId,
            fromDesk = fromDesk,
            fromDeskTasks = fromDeskTasks,
            toDesk = toDesk,
            toDeskTasks = toDeskTasks,
            direction = direction,
            fromDeskPosition = fromDeskPosition,
            toDeskPosition = toDeskPosition,
            totalDesks = repository.getNumberOfDesks(displayId),
        )
    }

    private fun startWallpaperAnimation(
        displayId: Int,
        numberOfDesks: Int,
        fromDeskIndex: Int,
        toDeskIndex: Int,
    ) {
        if (!desktopState.shouldShowHomeBehindDesktop) {
            logD("startWallpaperAnimation: sending broadcast")
            context.sendBroadcast(
                DesktopWallpaperActivity.createWallpaperSlideAnimationIntent(
                    displayId = displayId,
                    numberOfDesks = numberOfDesks,
                    fromDeskIndex = fromDeskIndex,
                    toDeskIndex = toDeskIndex,
                )
            )
        } else {
            // TODO: b/441146489 - animate the launcher wallpaper?
        }
    }

    private fun getAnimationFraction(startBounds: Rect, endBounds: Rect, animBounds: Rect): Float {
        val totalDistance = (endBounds.left - startBounds.left).toFloat()
        if (totalDistance == 0f) return 0f
        val distanceTravelled = (animBounds.left - startBounds.left).toFloat()
        return (distanceTravelled / totalDistance).let { kotlin.math.abs(it) }
    }

    /** Describes the changes involved in a desk switch transition. */
    private data class DeskSwitchChanges(
        val displayId: Int,
        val fromDesk: TransitionInfo.Change?,
        val fromDeskTasks: List<TransitionInfo.Change>,
        val toDesk: TransitionInfo.Change?,
        val toDeskTasks: List<TransitionInfo.Change>,
        val direction: Direction,
        val fromDeskPosition: Int,
        val toDeskPosition: Int,
        val totalDesks: Int,
    ) {
        override fun toString(): String {
            val fromDeskId = fromDesk?.taskInfo?.taskId
            val fromDeskTaskIds = fromDeskTasks.mapNotNull { it.taskInfo?.taskId }
            val toDeskId = toDesk?.taskInfo?.taskId
            val toDeskTaskIds = toDeskTasks.mapNotNull { it.taskInfo?.taskId }
            return "DeskSwitchChanges(displayId=$displayId, " +
                "fromDesk=$fromDeskId with tasks=$fromDeskTaskIds, " +
                "toDesk=$toDeskId with tasks=$toDeskTaskIds, " +
                "direction=$direction, " +
                "fromDeskPosition=$fromDeskPosition, toDeskPosition=$toDeskPosition, " +
                "totalDesks=$totalDesks)"
        }

        enum class Direction {
            LEFT,
            RIGHT,
        }
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private data class PendingSwitch(
        val displayId: Int,
        val userId: Int,
        val fromDeskId: Int,
        val toDeskId: Int,
    )

    private companion object {
        private const val TAG = "DeskSwitchTransitionHandler"
    }
}
