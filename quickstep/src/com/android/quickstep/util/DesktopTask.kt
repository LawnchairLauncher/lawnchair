/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.quickstep.views.TaskViewType
import com.android.systemui.shared.recents.model.Task
import java.util.Objects

/**
 * A [Task] container that can contain N number of tasks that are part of the desktop in recent
 * tasks list. Note that desktops can be empty with no tasks in them. The [deskId], [displayId]
 * makes sense only when the multiple desks feature is enabled.
 */
class DesktopTask(val deskId: Int, desktopDisplayId: Int, tasks: List<Task>) :
    GroupTask(tasks, desktopDisplayId, TaskViewType.DESKTOP) {

    override fun copy() = DesktopTask(deskId, displayId, tasks)

    override fun toString() = "type=$taskViewType deskId=$deskId displayId=$displayId tasks=$tasks"

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is DesktopTask) return false
        if (deskId != o.deskId) return false
        if (displayId != o.displayId) return false
        return super.equals(o)
    }

    override fun hashCode() = Objects.hash(super.hashCode(), deskId, displayId)
}
