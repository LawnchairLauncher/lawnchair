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

package com.android.wm.shell.compatui.letterbox.lifecycle

import android.graphics.Rect
import android.window.TransitionInfo.Change
import com.android.window.flags2.Flags
import com.android.wm.shell.compatui.letterbox.config.LetterboxDependenciesHelper
import com.android.wm.shell.compatui.letterbox.state.LetterboxTaskInfoRepository
import com.android.wm.shell.compatui.letterbox.state.updateConfiguration
import com.android.wm.shell.compatui.letterbox.state.updateTaskLeafState

/**
 * [LetterboxLifecycleEventFactory] implementation which creates a [LetterboxLifecycleEvent] from a
 * [TransitionInfo.Change] using a [TaskInfo] when present.
 */
class TaskInfoLetterboxLifecycleEventFactory(
    private val letterboxDependenciesHelper: LetterboxDependenciesHelper,
    private val letterboxTaskInfoRepository: LetterboxTaskInfoRepository,
    private val taskIdResolver: TaskIdResolver,
) : LetterboxLifecycleEventFactory {
    override fun canHandle(change: Change): Boolean = change.taskInfo != null

    override fun createLifecycleEvent(change: Change): LetterboxLifecycleEvent? {
        change.taskInfo?.let { ti ->
            val isLetterboxed = ti.appCompatTaskInfo?.isTopActivityLetterboxed ?: false
            val taskBoundsAbs = change.endAbsBounds
            // The bounds are absolute to the screen but we need them relative to the Task.
            val taskBounds =
                Rect(taskBoundsAbs).apply { offset(-taskBoundsAbs.left, -taskBoundsAbs.top) }
            // Letterbox bounds are null when the activity is not letterboxed.
            val letterboxBoundsAbs =
                if (isLetterboxed) ti.appCompatTaskInfo?.topActivityLetterboxBounds else null
            val letterboxBounds =
                letterboxBoundsAbs?.let { absBounds ->
                    Rect(absBounds).apply { offset(-taskBoundsAbs.left, -taskBoundsAbs.top) }
                }
            val shouldSupportInput = letterboxDependenciesHelper.shouldSupportInputSurface(change)
            if (Flags.appCompatRefactoringRoundedCorners()) {
                // Sometimes the [TransitionObserver] is notified before than the
                // [TaskAppearedListener] and the related information (e.g. [Configuration])
                // are required soon. This is the case, for instance, of rounded corners
                // implementation which require the [Configuration] when the related surfaces
                // are created.
                letterboxTaskInfoRepository.updateConfiguration(ti, change.leash)
            }
            if (Flags.appCompatRefactoringFixMultiwindowTaskHierarchy()) {
                // Because the [TransitionObserver] is invoked before the [OnTaskAppearedListener]s
                // it's important to store the information about the Task to be reused below for the
                // actual Task resolution given its id and parentId. Only Leaf tasks are stored
                // because they are the only ones with the capability of containing letterbox
                // surfaces.
                letterboxTaskInfoRepository.updateTaskLeafState(ti, change.leash)
                // If the task is not a leaf task the related entry is not present in the
                // Repository. The taskIdResolver will then search for a task which is a direct
                // child. If no Task is found the same id will be used later and the event
                // will be null resulting in a skipped event.
                // If the task is a leaf task the related entry will be present in the Repository
                // and the effectiveTaskId will be the correct taskId to use for the event.
                val effectiveTaskId = taskIdResolver.getLetterboxTaskId(ti)
                // The effectiveTaskId will then be the taskId of a leaf task (using parentId or
                // not) or the id of a missing task (no leaf). In the former case we need to use the
                // related token and leash. In the latter case the method returns null as mentioned
                // above.
                letterboxTaskInfoRepository.find(effectiveTaskId)?.let { item ->
                    return LetterboxLifecycleEvent(
                        type = change.asLetterboxLifecycleEventType(),
                        displayId = ti.displayId,
                        taskId = effectiveTaskId,
                        taskBounds = taskBounds,
                        letterboxBounds = letterboxBounds,
                        containerToken = item.containerToken,
                        taskLeash = item.containerLeash,
                        isBubble = ti.isAppBubble,
                        isTranslucent = change.isTranslucent(),
                        supportsInput = shouldSupportInput,
                    )
                }
            } else {
                return LetterboxLifecycleEvent(
                    type = change.asLetterboxLifecycleEventType(),
                    displayId = ti.displayId,
                    taskId = ti.taskId,
                    taskBounds = taskBounds,
                    letterboxBounds = letterboxBounds,
                    containerToken = ti.token,
                    taskLeash = change.leash,
                    isBubble = ti.isAppBubble,
                    isTranslucent = change.isTranslucent(),
                    supportsInput = shouldSupportInput,
                )
            }
        }
        return null
    }
}
