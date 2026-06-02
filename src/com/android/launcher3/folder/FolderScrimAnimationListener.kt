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

package com.android.launcher3.folder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import com.android.launcher3.Launcher
import com.android.launcher3.views.ScrimView

/**
 * After the expressive folder close animation, reapply the scrim for the current launcher state.
 * On the home screen the folder animation temporarily uses a black dim layer; when closing, that
 * must be cleared. In ALL_APPS, [FolderSpringAnimatorSet.addScrimAnimators] does not run, so the
 * drawer scrim is never replaced (#6551).
 */
class FolderScrimAnimationListener(
    private val scrimView: ScrimView,
    private val isOpening: Boolean,
    private val launcher: Launcher,
) : AnimatorListenerAdapter() {
    override fun onAnimationEnd(animation: Animator) {
        super.onAnimationEnd(animation)
        if (!isOpening) {
            restoreScrimAfterFolderClose()
        }
    }

    override fun onAnimationCancel(animation: Animator) {
        super.onAnimationCancel(animation)
        if (!isOpening) {
            restoreScrimAfterFolderClose()
        }
    }

    private fun restoreScrimAfterFolderClose() {
        scrimView.alpha = 1f
        scrimView.setScrimColors(
            launcher.stateManager.state.getWorkspaceScrimColor(launcher),
        )
    }
}
