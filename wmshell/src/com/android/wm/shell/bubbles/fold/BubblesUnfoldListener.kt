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

package com.android.wm.shell.bubbles.fold

import com.android.wm.shell.bubbles.Bubble
import com.android.wm.shell.bubbles.BubbleData
import com.android.wm.shell.unfold.ShellUnfoldProgressProvider

/**
 * An implementation of [ShellUnfoldProgressProvider.UnfoldListener] that notifies when we should
 * start transitioning an expanded bubble to floating.
 */
class BubblesUnfoldListener(
    private val bubbleData: BubbleData,
    private val foldLockSettingsObserver: BubblesFoldLockSettingsObserver,
    private val onStartBarToFloatingOrFullscreenTransition: (Bubble, Boolean) -> Unit
) : ShellUnfoldProgressProvider.UnfoldListener {

    override fun onFoldStateChanged(isFolded: Boolean) {
        val selectedBubble = bubbleData.selectedBubble
        // if we're folding with an expanded bubble and the device is configured to stay awake on
        // fold, notify that we should start transitioning from bar to floating
        if (isFolded
            && bubbleData.isExpanded
            && selectedBubble is Bubble
            && foldLockSettingsObserver.isStayAwakeOnFold()
        ) {
            val moveToFullscreen: Boolean = selectedBubble.isTopActivityFixedOrientationLandscape
            onStartBarToFloatingOrFullscreenTransition(selectedBubble, moveToFullscreen)
        }
    }
}
