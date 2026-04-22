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

package com.android.launcher3.util

import android.os.IBinder
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.ItemInfo.NO_ID

/** Info parameters that can be used to identify a Launcher object */
data class StableViewInfo(val itemId: Int, val containerId: Int, val stableId: Any) {

    fun matches(info: ItemInfo?) =
        info != null &&
            itemId == info.id &&
            containerId == info.container &&
            stableId == info.stableId

    companion object {

        private fun ItemInfo.toStableViewInfo() =
            stableId?.let { sId ->
                if (id != NO_ID || container != NO_ID) StableViewInfo(id, container, sId) else null
            }

        /**
         * Return a new launch cookie for the activity launch if supported.
         *
         * @param info the item info for the launch
         */
        @JvmStatic
        fun toLaunchCookie(info: ItemInfo?) =
            info?.toStableViewInfo()?.let { ObjectWrapper.wrap(it) }

        /**
         * Unwraps the binder and returns the first non-null StableViewInfo in the list or null if
         * none can be found
         */
        @JvmStatic
        fun fromLaunchCookies(launchCookies: List<IBinder>) =
            launchCookies.firstNotNullOfOrNull { ObjectWrapper.unwrap<StableViewInfo>(it) }
    }
}
