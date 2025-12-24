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
package com.android.wm.shell.desktopmode.multidesks.animation

import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.os.IBinder
import android.util.Slog
import androidx.core.animation.addListener
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.desktopmode.multidesks.animation.DeskSwitchAnimationUtils.WALLPAPER_TRANSLATION_DURATION
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE

/** A class to animate the wallpaper offsets behind the desks. */
class DeskWallpaperAnimator
private constructor(
    private val wallpaperManager: WallpaperManager,
    private val windowToken: IBinder,
    private val animation: Animation,
) {

    /** Starts the animation. */
    fun start() {
        when (animation) {
            is Animation.JumpCutAnimation -> startAnimation(animation)
            is Animation.SlideAnimation -> startAnimation(animation)
        }
    }

    private fun setWallpaperOffset(offsetStep: Float, offsetX: Float) {
        wallpaperManager.setWallpaperOffsetSteps(offsetStep, 1f)
        try {
            wallpaperManager.setWallpaperOffsets(windowToken, offsetX, 0.5f)
        } catch (e: IllegalArgumentException) {
            Slog.e(TAG, "Error updating wallpaper offset", e)
        }
    }

    private fun startAnimation(animation: Animation.JumpCutAnimation) {
        val offsetStep = 1f / (animation.numberOfDesks - 1)
        val finalOffset = animation.toDeskIndex * offsetStep
        if (DeskSwitchAnimationUtils.DEBUG_ANIMATION) {
            logD("startAnimation: offsetStep=%d finalOffset=%d", offsetStep, finalOffset)
        }
        setWallpaperOffset(offsetStep, finalOffset)
    }

    private fun startAnimation(animation: Animation.SlideAnimation) {
        val offsetStep = 1f / (animation.numberOfDesks - 1)
        val initialOffset = animation.fromDeskIndex * offsetStep
        val finalOffset = animation.toDeskIndex * offsetStep
        if (DeskSwitchAnimationUtils.DEBUG_ANIMATION) {
            logD(
                "startAnimation: offsetStep=%d initialOffset=%d finalOffset=%d",
                offsetStep,
                initialOffset,
                finalOffset,
            )
        }
        ValueAnimator.ofFloat(initialOffset, finalOffset)
            .setDuration(WALLPAPER_TRANSLATION_DURATION)
            .apply {
                addListener(
                    onStart = {
                        if (DeskSwitchAnimationUtils.DEBUG_ANIMATION) {
                            logD("startAnimation(start): initialOffset=%d", initialOffset)
                        }
                        setWallpaperOffset(offsetStep, initialOffset)
                    },
                    onEnd = {
                        if (DeskSwitchAnimationUtils.DEBUG_ANIMATION) {
                            logD("startAnimation(end): finalOffset=%d", finalOffset)
                        }
                        setWallpaperOffset(offsetStep, finalOffset)
                    },
                )
                addUpdateListener { animator ->
                    val offsetX = animator.animatedValue as Float
                    if (DeskSwitchAnimationUtils.DEBUG_ANIMATION) {
                        logD("startAnimation(update): offset=%d", offsetX)
                    }
                    setWallpaperOffset(offsetStep, offsetX)
                }
                start()
            }
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    private sealed class Animation {
        class JumpCutAnimation(val numberOfDesks: Int, val toDeskIndex: Int) : Animation()

        class SlideAnimation(val numberOfDesks: Int, val fromDeskIndex: Int, val toDeskIndex: Int) :
            Animation()
    }

    companion object {
        private const val TAG = "DeskWallpaperAnimator"

        const val JUMP_CUT_ANIMATION = 0
        const val SLIDE_ANIMATION = 1

        /** Creates a jump-cut animation wallpaper animator. */
        fun jumpCutAnimator(
            wallpaperManager: WallpaperManager,
            windowToken: IBinder,
            numberOfDesks: Int,
            toDeskIndex: Int,
        ): DeskWallpaperAnimator =
            DeskWallpaperAnimator(
                wallpaperManager = wallpaperManager,
                windowToken = windowToken,
                animation =
                    Animation.JumpCutAnimation(
                        numberOfDesks = numberOfDesks,
                        toDeskIndex = toDeskIndex,
                    ),
            )

        /** Creates a slide animation wallpaper animator. */
        fun slideAnimator(
            wallpaperManager: WallpaperManager,
            windowToken: IBinder,
            numberOfDesks: Int,
            fromDeskIndex: Int,
            toDeskIndex: Int,
        ): DeskWallpaperAnimator =
            DeskWallpaperAnimator(
                wallpaperManager = wallpaperManager,
                windowToken = windowToken,
                animation =
                    Animation.SlideAnimation(
                        numberOfDesks = numberOfDesks,
                        fromDeskIndex = fromDeskIndex,
                        toDeskIndex = toDeskIndex,
                    ),
            )
    }
}
