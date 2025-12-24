/*
 * Copyright 2025 The Android Open Source Project
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

import android.content.Context
import android.graphics.Rect
import android.graphics.Region
import android.os.Handler
import android.view.SurfaceControl
import android.view.SurfaceControl.Transaction
import android.window.WindowContainerToken
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.common.suppliers.WindowSessionSupplier
import com.android.wm.shell.compatui.letterbox.LetterboxUtils.Maps.runOnItem
import com.android.wm.shell.compatui.letterbox.events.ReachabilityGestureListenerFactory
import com.android.wm.shell.dagger.WMSingleton
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT
import com.android.wm.shell.shared.annotations.ShellMainThread
import javax.inject.Inject

/**
 * [LetterboxController] implementation responsible for handling the spy [SurfaceControl] we use to
 * detect letterbox events.
 */
@WMSingleton
class LetterboxInputController
@Inject
constructor(
    private val context: Context,
    @ShellMainThread private val handler: Handler,
    private val inputSurfaceBuilder: LetterboxInputSurfaceBuilder,
    private val listenerFactory: ReachabilityGestureListenerFactory,
    private val windowSessionSupplier: WindowSessionSupplier,
) : LetterboxController {

    companion object {
        @JvmStatic private val TAG = "LetterboxInputController"
    }

    private val inputDetectorMap = mutableMapOf<Int, LetterboxInputItems>()

    override fun createLetterboxSurface(
        key: LetterboxKey,
        transaction: Transaction,
        parentLeash: SurfaceControl,
        token: WindowContainerToken?,
    ) {
        inputDetectorMap.runOnItem(
            key.taskId,
            onMissed = { k, m ->
                val gestureListener =
                    listenerFactory.createReachabilityGestureListener(key.taskId, token)
                val detector =
                    LetterboxInputDetector(
                            context,
                            handler,
                            gestureListener,
                            inputSurfaceBuilder,
                            windowSessionSupplier,
                        )
                        .apply { start(transaction, parentLeash, key) }
                m[k] = LetterboxInputItems(detector, gestureListener)
            },
        )
    }

    override fun destroyLetterboxSurface(key: LetterboxKey, transaction: Transaction) {
        with(inputDetectorMap) {
            runOnItem(key.taskId, onFound = { item -> item.inputDetector.stop(transaction) })
            remove(key.taskId)
        }
    }

    override fun updateLetterboxSurfaceVisibility(
        key: LetterboxKey,
        transaction: Transaction,
        visible: Boolean,
    ) {
        with(inputDetectorMap) {
            runOnItem(
                key.taskId,
                onFound = { item -> item.inputDetector.updateVisibility(transaction, visible) },
            )
        }
    }

    override fun updateLetterboxSurfaceBounds(
        key: LetterboxKey,
        transaction: Transaction,
        taskBounds: Rect,
        activityBounds: Rect,
    ) {
        inputDetectorMap.runOnItem(
            key.taskId,
            onFound = { item ->
                item.inputDetector.updateTouchableRegion(transaction, Region(taskBounds))
                item.gestureListener.updateActivityBounds(activityBounds)
            },
        )
    }

    override fun dump() {
        ProtoLog.v(WM_SHELL_APP_COMPAT, "%s: %s", TAG, "${inputDetectorMap.keys}")
    }
}
