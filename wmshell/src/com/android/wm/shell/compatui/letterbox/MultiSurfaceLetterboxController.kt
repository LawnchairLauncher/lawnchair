/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.graphics.Rect
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.window.WindowContainerToken
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.compatui.letterbox.LetterboxUtils.Maps.runOnItem
import com.android.wm.shell.compatui.letterbox.LetterboxUtils.Transactions.moveAndCrop
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT
import javax.inject.Inject

/** Component responsible for handling the lifecycle of multiple letterbox surfaces when needed. */
@WMSingleton
class MultiSurfaceLetterboxController
@Inject
constructor(private val letterboxBuilder: LetterboxSurfaceBuilder) : LetterboxController {

    companion object {
        @JvmStatic private val TAG = "MultiSurfaceLetterboxController"
    }

    private val letterboxMap = mutableMapOf<Int, LetterboxSurfaces>()

    override fun createLetterboxSurface(
        key: LetterboxKey,
        transaction: Transaction,
        parentLeash: SurfaceControl,
        token: WindowContainerToken?,
    ) {
        val surfaceBuilderFn = { position: String ->
            letterboxBuilder.createSurface(
                transaction,
                parentLeash,
                "ShellLetterboxSurface-$key-$position",
                "MultiSurfaceLetterboxController#createLetterboxSurface",
            )
        }
        letterboxMap.runOnItem(
            key.taskId,
            onMissed = { k, m ->
                m[k] =
                    LetterboxSurfaces(
                        leftSurface = surfaceBuilderFn("Left"),
                        topSurface = surfaceBuilderFn("Top"),
                        rightSurface = surfaceBuilderFn("Right"),
                        bottomSurface = surfaceBuilderFn("Bottom"),
                    )
            },
        )
    }

    override fun destroyLetterboxSurface(key: LetterboxKey, transaction: Transaction) {
        letterboxMap.runOnItem(
            key.taskId,
            onFound = { item -> item.forEach { s -> s.remove(transaction) } },
        )
        letterboxMap.remove(key.taskId)
    }

    override fun updateLetterboxSurfaceVisibility(
        key: LetterboxKey,
        transaction: Transaction,
        visible: Boolean,
    ) {
        letterboxMap.runOnItem(
            key.taskId,
            onFound = { item -> item.forEach { s -> s.setVisibility(transaction, visible) } },
        )
    }

    override fun updateLetterboxSurfaceBounds(
        key: LetterboxKey,
        transaction: Transaction,
        taskBounds: Rect,
        activityBounds: Rect,
    ) {
        letterboxMap.runOnItem(
            key.taskId,
            onFound = { item -> item.updateSurfacesBounds(transaction, taskBounds, activityBounds) },
        )
    }

    override fun dump() {
        ProtoLog.v(WM_SHELL_APP_COMPAT, "%s: %s", TAG, "${letterboxMap.keys}")
    }

    private fun SurfaceControl?.remove(tx: Transaction) = this?.let { tx.remove(this) }

    private fun SurfaceControl?.setVisibility(tx: Transaction, visible: Boolean) =
        this?.let { tx.setVisibility(this, visible) }

    private fun LetterboxSurfaces.updateSurfacesBounds(
        tx: Transaction,
        taskBounds: Rect,
        activityBounds: Rect,
    ) {
        // Update the bounds depending on the activity position.
        leftSurface?.let { s ->
            tx.moveAndCrop(
                s,
                Rect(taskBounds.left, taskBounds.top, activityBounds.left, taskBounds.bottom),
            )
        }
        rightSurface?.let { s ->
            tx.moveAndCrop(
                s,
                Rect(activityBounds.right, taskBounds.top, taskBounds.right, taskBounds.bottom),
            )
        }
        topSurface?.let { s ->
            tx.moveAndCrop(
                s,
                Rect(taskBounds.left, taskBounds.top, taskBounds.right, activityBounds.top),
            )
        }
        bottomSurface?.let { s ->
            tx.moveAndCrop(
                s,
                Rect(taskBounds.left, activityBounds.bottom, taskBounds.right, taskBounds.bottom),
            )
        }
    }
}
