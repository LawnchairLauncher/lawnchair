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

package com.android.wm.shell.draganddrop

import android.graphics.Insets
import android.window.WindowContainerToken
import com.android.internal.logging.InstanceId

/**
 * Interface to be implemented by classes which want to provide drop targets
 * for DragAndDrop in Shell
 */
interface DropTarget {
    // TODO(b/349828130) Delete after flexible split launches
    /**
     * Called at the start of a Drag, before input events are processed.
     */
    fun start(dragSession: DragSession, logSessionId: InstanceId)
    /**
     * @return [SplitDragPolicy.Target] corresponding to the given coords in display bounds.
     */
    fun getTargetAtLocation(x: Int, y: Int) : SplitDragPolicy.Target
    /**
     * @return total number of drop targets for the current drag session.
     */
    fun getNumTargets() : Int
    // TODO(b/349828130)

    /**
     * @return [List<SplitDragPolicy.Target>] to show for the current drag session.
     */
    fun getTargets(insets: Insets) : List<SplitDragPolicy.Target>
    /**
     * Called when user is hovering Drag object over the given Target
     */
    fun onHoveringOver(target: SplitDragPolicy.Target?) {}
    /**
     * Called when the user has dropped the provided target (need not be the same target as
     * [onHoveringOver])
     */
    fun onDropped(target: SplitDragPolicy.Target, hideTaskToken: WindowContainerToken)
}