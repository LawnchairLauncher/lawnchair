/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.quickstep.util

import android.util.Log
import android.view.WindowManager.TRANSIT_CHANGE
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import android.window.TransitionInfo.FLAG_FIRST_CUSTOM
import com.android.launcher3.util.SplitConfigurationOptions
import com.android.wm.shell.shared.TransitionUtil
import com.android.wm.shell.shared.split.SplitBounds

class SplitScreenUtils {
    companion object {
        private const val TAG = "SplitScreenUtils"

        /**
         * Given a TransitionInfo, generates the tree structure for those changes and extracts out
         * the top most root and it's two immediate children. Changes can be provided in any order.
         *
         * @return a [Pair] where first -> top most split root, second -> [List] of 2,
         *   leftTop/bottomRight stage roots
         */
        fun extractTopParentAndChildren(
            transitionInfo: TransitionInfo
        ): Pair<Change, List<Change>>? {
            val parentToChildren = mutableMapOf<Change, MutableList<Change>>()
            val hasParent = mutableSetOf<Change>()
            val taskChanges: List<Change> = getNonClosingChanges(transitionInfo)

            // 1. Build Parent-Child Relationships
            for (change in taskChanges) {
                // TODO (b/316490565): Replace this logic when SplitBounds is available to
                //  startAnimation() and we can know the precise taskIds of launching tasks.
                change.parent?.let { parent ->
                    parentToChildren
                        .getOrPut(transitionInfo.getChange(parent)!!) { mutableListOf() }
                        .add(change)
                    hasParent.add(change)
                }
            }

            // 2. Find Top Parent
            val topParent = taskChanges.firstOrNull { it !in hasParent }

            // 3. Extract Immediate Children
            return if (topParent != null) {
                val immediateChildren = parentToChildren.getOrDefault(topParent, emptyList())
                if (immediateChildren.size != 2) {
                    throw IllegalStateException("incorrect split stage root size")
                }
                Pair(topParent, immediateChildren)
            } else {
                Log.w(TAG, "No top parent found")
                null
            }
        }


        /** @return includes only opening + [TRANSIT_CHANGE] changes and the divider */
        private fun getNonClosingChanges(transitionInfo: TransitionInfo): List<Change> {
            return transitionInfo.changes
                .filter { change ->
                    (TransitionUtil.isOpeningMode(change.mode) || change.mode == TRANSIT_CHANGE)
                            && change.flags < FLAG_FIRST_CUSTOM
                }
                .toList()
        }
    }
}
