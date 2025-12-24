/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.wm.shell.desktopmode

import android.app.ActivityManager.getCurrentUser
import android.app.TaskInfo
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.window.DesktopExperienceFlags
import androidx.activity.addCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import com.android.wm.shell.desktopmode.multidesks.animation.DeskWallpaperAnimator
import com.android.wm.shell.desktopmode.multidesks.animation.DeskWallpaperAnimator.Companion.JUMP_CUT_ANIMATION
import com.android.wm.shell.desktopmode.multidesks.animation.DeskWallpaperAnimator.Companion.SLIDE_ANIMATION

/**
 * A transparent activity used in the desktop mode to show the wallpaper under the freeform windows.
 * This activity will be running in `FULLSCREEN` windowing mode, which ensures it hides Launcher.
 * When entering desktop, we would ensure that it's added behind desktop apps and removed when
 * leaving the desktop mode.
 *
 * Note! This activity should NOT interact directly with any other code in the Shell without calling
 * onto the shell main thread. Activities are always started on the main thread.
 */
class DesktopWallpaperActivity : FragmentActivity() {

    private var wallpaperManager: WallpaperManager? = null
    private var displayManager: DisplayManager? = null
    // TODO(b/432710419): Refresh current user on user change if needed
    private var currentUser: Int = getCurrentUser()
    private var initialDisplayId: Int? = null

    private val wallpaperColorsListener =
        object : WallpaperManager.OnColorsChangedListener {
            override fun onColorsChanged(colors: WallpaperColors?, which: Int) {}

            override fun onColorsChanged(colors: WallpaperColors?, which: Int, userId: Int) {
                if (userId == currentUser) {
                    updateStatusBarIconColors(colors)
                }
            }
        }

    private val displayRemovedListener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                // No-op
            }

            override fun onDisplayRemoved(displayId: Int) {
                // DesktopWallpaperActivity should never move to another display; if this
                // activity's display is removed, finish the activity.
                if (displayId == initialDisplayId) {
                    finish()
                }
            }

            override fun onDisplayChanged(displayId: Int) {
                // If the display can no longer host tasks, remove this to ensure
                // core does not move it to the front of another display.
                if (displayId == initialDisplayId && !display.canHostTasks()) finish()
            }
        }

    private val wallpaperAnimationReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent ?: return
                handleStartWallpaperAnimationRequest(intent)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        // Set to |false| by default. This shouldn't matter because
        // [Activity#onTopResumedActivityChanged] is supposed to be called after [onResume] which
        // should set the correct state. However, there's a lifecycle bug that causes it not to
        // be called after [onCreate] (see b/416700931) and may leave the wallpaper touchable after
        // entering desktop mode with another app. To prevent this make it not focusable by
        // default, as it is more likely a user will enter desktop with a task than without one
        // (entering through an empty desk may result in a reversed bug: unfocusable when we wanted
        // it to be focusable).
        updateFocusableFlag(focusable = false)

        if (
            DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue &&
                DesktopExperienceFlags.ENABLE_EMPTY_DESK_ON_MINIMIZE.isTrue
        ) {
            onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }
        }

        // Handle wallpaper color changes
        wallpaperManager = getSystemService(WallpaperManager::class.java)
        wallpaperManager?.addOnColorsChangedListener(wallpaperColorsListener, mainThreadHandler)

        // Handle self-removal on display disconnect
        displayManager = getSystemService(DisplayManager::class.java)
        displayManager?.registerDisplayListener(displayRemovedListener, mainThreadHandler)

        // Set the initial color of status bar icons on activity creation.
        updateStatusBarIconColors(
            wallpaperManager?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM, currentUser)
        )
        initialDisplayId = displayId
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(
            wallpaperAnimationReceiver,
            IntentFilter(START_WALLPAPER_ANIMATION_ACTION),
            RECEIVER_EXPORTED,
        )
    }

    override fun onPause() {
        unregisterReceiver(wallpaperAnimationReceiver)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        wallpaperManager?.removeOnColorsChangedListener(wallpaperColorsListener)
        displayManager?.unregisterDisplayListener(displayRemovedListener)
    }

    override fun onMovedToDisplay(displayId: Int, config: Configuration?) {
        finish()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        if (!DesktopExperienceFlags.ENABLE_MULTIPLE_DESKTOPS_BACKEND.isTrue) return
        Log.d(TAG, "onTopResumedActivityChanged: $isTopResumedActivity")
        // Let the activity be focusable when it is top-resumed (e.g. empty desk), otherwise input
        // events will result in an ANR because the focused app would have no focusable window.
        updateFocusableFlag(focusable = isTopResumedActivity)
    }

    private fun updateFocusableFlag(focusable: Boolean) {
        if (focusable) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    /** Set the status bar icon colours depending on wallpaper hint. */
    private fun updateStatusBarIconColors(wallpaperColors: WallpaperColors?) {
        wallpaperColors?.colorHints?.let {
            getWindowInsetsController().isAppearanceLightStatusBars =
                (it and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        }
    }

    private fun getWindowInsetsController(): WindowInsetsControllerCompat =
        WindowCompat.getInsetsController(window, window.decorView)

    private fun handleStartWallpaperAnimationRequest(intent: Intent) {
        Log.d(TAG, "handleStartWallpaperAnimationRequest")
        if (intent.action != START_WALLPAPER_ANIMATION_ACTION) return
        val animType = intent.getIntExtra(WALLPAPER_ANIMATION_EXTRA_ANIM_TYPE, JUMP_CUT_ANIMATION)
        val displayId = intent.getIntExtra(WALLPAPER_ANIMATION_EXTRA_DISPLAY_ID, -1)
        if (displayId == -1 || displayId != initialDisplayId) return
        val token = window?.decorView?.rootView?.windowToken ?: return
        val wm = wallpaperManager ?: return
        when (animType) {
            JUMP_CUT_ANIMATION -> {
                val numberOfDesks = intent.getIntExtra(WALLPAPER_ANIMATION_EXTRA_NUM_OF_DESKS, -1)
                val toDeskIndex = intent.getIntExtra(WALLPAPER_ANIMATION_EXTRA_FROM_DESK_INDEX, -1)
                if (numberOfDesks == -1 || toDeskIndex == -1) {
                    return
                }
                DeskWallpaperAnimator.jumpCutAnimator(
                        wallpaperManager = wm,
                        windowToken = token,
                        numberOfDesks = numberOfDesks,
                        toDeskIndex = toDeskIndex,
                    )
                    .start()
            }
            SLIDE_ANIMATION -> {
                val numberOfDesks = intent.getIntExtra(WALLPAPER_ANIMATION_EXTRA_NUM_OF_DESKS, -1)
                val fromDeskIndex =
                    intent.getIntExtra(WALLPAPER_ANIMATION_EXTRA_FROM_DESK_INDEX, -1)
                val toDeskIndex = intent.getIntExtra(WALLPAPER_ANIMATION_EXTRA_TO_DESK_INDEX, -1)
                if (numberOfDesks == -1 || fromDeskIndex == -1 || toDeskIndex == -1) {
                    return
                }
                DeskWallpaperAnimator.slideAnimator(
                        wallpaperManager = wm,
                        windowToken = token,
                        numberOfDesks = numberOfDesks,
                        fromDeskIndex = fromDeskIndex,
                        toDeskIndex = toDeskIndex,
                    )
                    .start()
            }
        }
    }

    companion object {
        private const val TAG = "DesktopWallpaperActivity"
        private const val SYSTEM_UI_PACKAGE_NAME = "com.android.systemui"

        private const val START_WALLPAPER_ANIMATION_ACTION =
            "com.android.wm.shell.desktop.action.ANIMATE_WALLPAPER"
        private const val WALLPAPER_ANIMATION_EXTRA_ANIM_TYPE = "animation_type"
        private const val WALLPAPER_ANIMATION_EXTRA_DISPLAY_ID = "display_id"
        private const val WALLPAPER_ANIMATION_EXTRA_NUM_OF_DESKS = "num_of_desks"
        private const val WALLPAPER_ANIMATION_EXTRA_FROM_DESK_INDEX = "from_desk_index"
        private const val WALLPAPER_ANIMATION_EXTRA_TO_DESK_INDEX = "to_desk_index"

        @JvmStatic
        val wallpaperActivityComponent =
            ComponentName(SYSTEM_UI_PACKAGE_NAME, DesktopWallpaperActivity::class.java.name)

        @JvmStatic
        fun isWallpaperTask(taskInfo: TaskInfo) =
            taskInfo.baseIntent.component?.let(::isWallpaperComponent) ?: false

        @JvmStatic
        fun isWallpaperComponent(component: ComponentName) = component == wallpaperActivityComponent

        /** An intent to use in a broadcast to trigger the slide wallpaper animation. */
        fun createWallpaperSlideAnimationIntent(
            displayId: Int,
            numberOfDesks: Int,
            fromDeskIndex: Int,
            toDeskIndex: Int,
        ): Intent =
            Intent(START_WALLPAPER_ANIMATION_ACTION).apply {
                putExtra(WALLPAPER_ANIMATION_EXTRA_ANIM_TYPE, SLIDE_ANIMATION)
                putExtra(WALLPAPER_ANIMATION_EXTRA_DISPLAY_ID, displayId)
                putExtra(WALLPAPER_ANIMATION_EXTRA_NUM_OF_DESKS, numberOfDesks)
                putExtra(WALLPAPER_ANIMATION_EXTRA_FROM_DESK_INDEX, fromDeskIndex)
                putExtra(WALLPAPER_ANIMATION_EXTRA_TO_DESK_INDEX, toDeskIndex)
            }

        /** An intent to use in a broadcast to trigger the no-animation wallpaper animation. */
        fun createWallpaperNoAnimationIntent(
            displayId: Int,
            numberOfDesks: Int,
            deskIndex: Int,
        ): Intent =
            Intent(START_WALLPAPER_ANIMATION_ACTION).apply {
                putExtra(WALLPAPER_ANIMATION_EXTRA_ANIM_TYPE, JUMP_CUT_ANIMATION)
                putExtra(WALLPAPER_ANIMATION_EXTRA_DISPLAY_ID, displayId)
                putExtra(WALLPAPER_ANIMATION_EXTRA_NUM_OF_DESKS, numberOfDesks)
                putExtra(WALLPAPER_ANIMATION_EXTRA_TO_DESK_INDEX, deskIndex)
            }
    }
}
