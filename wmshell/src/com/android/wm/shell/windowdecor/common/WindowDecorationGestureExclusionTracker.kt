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

package com.android.wm.shell.windowdecor.common

import android.content.Context
import android.graphics.Region
import android.os.RemoteException
import android.util.Slog
import android.view.ISystemGestureExclusionListener
import android.view.IWindowManager
import android.window.DesktopExperienceFlags
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.shared.annotations.ShellMainThread
import com.android.wm.shell.sysui.ShellInit

/**
 * A tracking class that helps listening for display add/removed callbacks, and subsequently starts
 * tracking system gesture exclusion region reported for each display. Used by
 * WindowDecorationViewModel so it can update individual WindowDecorations regarding regions.
 */
class WindowDecorationGestureExclusionTracker(
    private val context: Context,
    private val windowManager: IWindowManager,
    private val displayController: DisplayController,
    @ShellMainThread private val mainExecutor: ShellExecutor,
    shellInit: ShellInit,
    exclusionRegionChangedCallback: (Int, Region) -> Unit,
) : DisplayController.OnDisplaysChangedListener {
    private val exclusionRegion = Region()
    private val exclusionRegions =
        object : HashMap<Int, Region>() {
            override operator fun get(displayId: Int): Region {
                return if (DesktopExperienceFlags.ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue()) {
                    super.get(displayId) ?: Region()
                } else {
                    exclusionRegion
                }
            }
        }

    private val exclusionListener =
        object : ISystemGestureExclusionListener.Stub() {
            override fun onSystemGestureExclusionChanged(
                displayId: Int,
                systemGestureExclusion: Region,
                systemGestureExclusionUnrestricted: Region?,
            ) {
                if (DesktopExperienceFlags.ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue()) {
                    mainExecutor.execute {
                        exclusionRegions[displayId].set(systemGestureExclusion)
                        exclusionRegionChangedCallback(displayId, exclusionRegions[displayId])
                    }
                } else {
                    if (context.getDisplayId() != displayId) {
                        return
                    }
                    mainExecutor.execute {
                        exclusionRegion.set(systemGestureExclusion)
                        exclusionRegionChangedCallback(displayId, exclusionRegion)
                    }
                }
            }
        }

    init {
        shellInit.addInitCallback(::onShellInit, this)
    }

    private fun onShellInit() {
        if (DesktopExperienceFlags.ENABLE_BUG_FIXES_FOR_SECONDARY_DISPLAY.isTrue()) {
            displayController.addDisplayWindowListener(this)
        } else {
            try {
                windowManager.registerSystemGestureExclusionListener(
                    exclusionListener,
                    context.displayId,
                )
            } catch (ex: RemoteException) {
                Slog.e(
                    TAG,
                    "Failed to register window manager callbacks for display: " + context.displayId,
                    ex,
                )
            }
        }
    }

    /** Returns the current exclusion region for the given display id. */
    fun getExclusionRegion(displayId: Int): Region {
        return exclusionRegions[displayId]
    }

    override fun onDisplayAdded(displayId: Int) {
        try {
            windowManager.registerSystemGestureExclusionListener(exclusionListener, displayId)
            exclusionRegions[displayId] = Region()
        } catch (ex: RemoteException) {
            Slog.e(TAG, "Failed to register window manager callbacks for display: $displayId", ex)
        }
    }

    override fun onDisplayRemoved(displayId: Int) {
        try {
            windowManager.unregisterSystemGestureExclusionListener(exclusionListener, displayId)
        } catch (ex: Exception) {
            when (ex) {
                is IllegalArgumentException,
                is RemoteException -> {
                    // Catching both IllegalArgumentException and RemoteException with Exception
                    Slog.e(
                        TAG,
                        "Failed to unregister window manager callbacks for display: $displayId",
                    )
                }
                else -> throw ex
            }
        }
        exclusionRegions.remove(displayId)
    }

    override fun toString(): String {
        return exclusionRegions.toString()
    }

    private companion object {
        private const val TAG = "WindowDecorGestureExclTracker"
    }
}
