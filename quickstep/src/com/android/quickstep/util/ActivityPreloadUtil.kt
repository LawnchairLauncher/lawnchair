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

package com.android.quickstep.util

import android.content.Context
import android.content.Intent
import android.os.Trace
import android.view.Display.DEFAULT_DISPLAY
import com.android.launcher3.provider.RestoreDbTask
import com.android.launcher3.util.Executors
import com.android.launcher3.util.LockedUserState
import com.android.quickstep.OverviewComponentObserver
import com.android.quickstep.RecentsAnimationDeviceState
import com.android.systemui.shared.system.ActivityManagerWrapper

/** Utility class for preloading overview */
object ActivityPreloadUtil {

    @JvmStatic
    fun preloadOverviewForSUWAllSet(ctx: Context) {
        preloadOverview(ctx, fromInit = false, forSUWAllSet = true)
    }

    @JvmStatic
    fun preloadOverviewForTIS(ctx: Context, fromInit: Boolean) {
        preloadOverview(ctx, fromInit = fromInit, forSUWAllSet = false)
    }

    private fun preloadOverview(ctx: Context, fromInit: Boolean, forSUWAllSet: Boolean) {
        Trace.beginSection("preloadOverview(fromInit=$fromInit, forSUWAllSet=$forSUWAllSet)")

        try {
            if (!LockedUserState.get(ctx).isUserUnlocked) return

            val deviceState =
                RecentsAnimationDeviceState.REPOSITORY_INSTANCE.get(ctx)[ctx.displayId] ?: return
            val overviewCompObserver = OverviewComponentObserver.INSTANCE[ctx]

            // Prevent the overview from being started before the real home on first boot
            if (deviceState.isButtonNavMode && !overviewCompObserver.isHomeAndOverviewSame) return

            // Preloading while a restore is pending may cause launcher to start the restore too
            // early
            if ((RestoreDbTask.isPending(ctx) && !forSUWAllSet) || !deviceState.isUserSetupComplete)
                return

            // The activity has been created before the initialization of overview service. It is
            // usually happens when booting or launcher is the top activity, so we should already
            // have the latest state.
            if (
                fromInit &&
                    overviewCompObserver.getContainerInterface(DEFAULT_DISPLAY)?.createdContainer !=
                        null
            )
                return

            //ActiveGestureProtoLogProxy.logPreloadRecentsAnimation()
            val overviewIntent = Intent(overviewCompObserver.overviewIntentIgnoreSysUiState)
            Executors.UI_HELPER_EXECUTOR.execute {
                ActivityManagerWrapper.getInstance().preloadRecentsActivity(overviewIntent)
            }
        } finally {
            Trace.endSection()
        }
    }
}
