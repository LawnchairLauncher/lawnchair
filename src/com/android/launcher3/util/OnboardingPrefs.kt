/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.content.Context
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefs.Companion.backedUpItem

/** Stores and retrieves onboarding-related data via SharedPreferences. */
object OnboardingPrefs {

    data class CountedItem(
        val sharedPrefKey: String,
        val maxCount: Int,
    ) {
        private val prefItem = backedUpItem(sharedPrefKey, 0)

        /** @return The number of times we have seen the given event. */
        fun get(c: Context): Int {
            return prefItem.get(c)
        }

        /** @return Whether we have seen this event enough times, as defined by [.MAX_COUNTS]. */
        fun hasReachedMax(c: Context): Boolean {
            return get(c) >= maxCount
        }

        /**
         * Add 1 to the given event count, if we haven't already reached the max count.
         *
         * @return Whether we have now reached the max count.
         */
        fun increment(c: Context): Boolean {
            val count = get(c)
            if (count >= maxCount) {
                return true
            }
            return set(count + 1, c)
        }

        /**
         * Sets the event count to the given value.
         *
         * @return Whether we have now reached the max count.
         */
        fun set(count: Int, c: Context): Boolean {
            LauncherPrefs.get(c).put(prefItem, count)
            return count >= maxCount
        }
    }

    @JvmField val TASKBAR_EDU_TOOLTIP_STEP = CountedItem("launcher.taskbar_edu_tooltip_step", 3)

    @JvmField val HOME_BOUNCE_COUNT = CountedItem("launcher.home_bounce_count", 3)

    @JvmField
    val HOTSEAT_DISCOVERY_TIP_COUNT = CountedItem("launcher.hotseat_discovery_tip_count", 5)

    @JvmField val ALL_APPS_VISITED_COUNT = CountedItem("launcher.all_apps_visited_count", 20)

    @JvmField val HOME_BOUNCE_SEEN = backedUpItem("launcher.apps_view_shown", false)

    @JvmField
    val HOTSEAT_LONGPRESS_TIP_SEEN = backedUpItem("launcher.hotseat_longpress_tip_seen", false)

    @JvmField val TASKBAR_SEARCH_EDU_SEEN = backedUpItem("launcher.taskbar_search_edu_seen", false)
}
