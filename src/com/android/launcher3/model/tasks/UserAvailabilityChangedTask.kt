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

package com.android.launcher3.model.tasks

import android.os.UserHandle
import com.android.launcher3.Flags
import com.android.launcher3.LauncherModel.ModelUpdateTask
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.data.AppsListData.Companion.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED
import com.android.launcher3.model.data.AppsListData.Companion.FLAG_QUIET_MODE_ENABLED
import com.android.launcher3.model.data.AppsListData.Companion.FLAG_WORK_PROFILE_QUIET_MODE_ENABLED
import com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_DISABLED_QUIET_USER
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.FlagOp
import com.android.launcher3.util.ItemInfoMatcher

/**
 * Model task to handle a profile user availability changes (eg, enabling/disabling work-profile or
 * private profile).
 */
class UserAvailabilityChangedTask(private val user: UserHandle) : ModelUpdateTask {

    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        val ums = UserCache.INSTANCE[taskController.context].userManagerState
        val userInfo = ums.getCachedInfo(user)

        val isUserQuiet = userInfo.isQuietModeEnabled
        val flagOp = FlagOp.NO_OP.setFlag(FLAG_DISABLED_QUIET_USER, isUserQuiet)

        apps.updateDisabledFlags(ItemInfoMatcher.ofUser(user), flagOp)

        if (Flags.enablePrivateSpace()) {
            if (userInfo.iconInfo.isWork) {
                apps.setFlags(FLAG_WORK_PROFILE_QUIET_MODE_ENABLED, isUserQuiet)
            } else if (userInfo.iconInfo.isPrivate) {
                apps.setFlags(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED, isUserQuiet)
            }
        } else {
            // We are not synchronizing here, as int operations are atomic
            apps.setFlags(FLAG_QUIET_MODE_ENABLED, ums.isAnyProfileQuietModeEnabled)
        }
        taskController.bindApplicationsIfNeeded()

        val updates =
            dataModel.updateAndCollectWorkspaceItemInfos(
                user,
                {
                    val oldFlag = it.runtimeStatusFlags
                    it.runtimeStatusFlags = flagOp.apply(oldFlag)
                    it.runtimeStatusFlags != oldFlag
                },
            )
        taskController.bindUpdatedWorkspaceItems(updates)
    }
}
