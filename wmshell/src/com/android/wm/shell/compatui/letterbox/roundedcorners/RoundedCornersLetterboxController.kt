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

package com.android.wm.shell.compatui.letterbox.roundedcorners

import android.graphics.Rect
import android.view.SurfaceControl
import android.window.WindowContainerToken
import com.android.internal.protolog.ProtoLog
import com.android.window.flags2.Flags
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.compatui.letterbox.LetterboxController
import com.android.wm.shell.compatui.letterbox.LetterboxKey
import com.android.wm.shell.compatui.letterbox.LetterboxUtils.Maps.runOnItem
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskInfoRepository
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT
import com.android.wm.shell.shared.annotations.ShellMainThread
import javax.inject.Inject

/** [LetterboxController] implementation to handle rounded corners for Letterbox in Shell. */
@WMSingleton
class RoundedCornersLetterboxController
@Inject
constructor(
    @ShellMainThread private val animExecutor: ShellExecutor,
    private val roundedCornersSurfaceBuilder: RoundedCornersSurfaceBuilder,
    private val taskRepository: LetterboxTaskInfoRepository,
) : LetterboxController {

    companion object {
        @JvmStatic private val TAG = "RoundedCornersLetterboxController"
    }

    private val roundedCornersMap = mutableMapOf<Int, RoundedCornersSurface>()

    override fun createLetterboxSurface(
        key: LetterboxKey,
        transaction: SurfaceControl.Transaction,
        parentLeash: SurfaceControl,
        token: WindowContainerToken?,
    ) {
        roundedCornersMap.runOnItem(
            key.taskId,
            onMissed = { k, m ->
                taskRepository.find(key.taskId)?.let { item ->
                    m[k] = roundedCornersSurfaceBuilder.create(item.configuration, parentLeash)
                }
            },
        )
    }

    override fun destroyLetterboxSurface(
        key: LetterboxKey,
        transaction: SurfaceControl.Transaction,
    ) {
        roundedCornersMap.runOnItem(key.taskId, onFound = { item -> item.release() })
        roundedCornersMap.remove(key.taskId)
    }

    override fun updateLetterboxSurfaceVisibility(
        key: LetterboxKey,
        transaction: SurfaceControl.Transaction,
        visible: Boolean,
    ) {
        roundedCornersMap.runOnItem(
            key.taskId,
            onFound = { item ->
                item.setCornersVisibility(
                    animExecutor,
                    visible,
                    immediate = !Flags.appCompatRefactoringRoundedCornersAnimation(),
                )
            },
        )
    }

    override fun updateLetterboxSurfaceBounds(
        key: LetterboxKey,
        transaction: SurfaceControl.Transaction,
        taskBounds: Rect,
        activityBounds: Rect,
    ) {
        roundedCornersMap.runOnItem(
            key.taskId,
            onFound = { item -> item.updateSurfaceBounds(activityBounds) },
        )
    }

    override fun dump() {
        ProtoLog.v(WM_SHELL_APP_COMPAT, "%s: %s", TAG, "$roundedCornersMap")
    }
}
