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

package com.android.launcher3.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_DATE_CHANGED
import android.content.Intent.ACTION_TIMEZONE_CHANGED
import android.content.Intent.ACTION_TIME_CHANGED
import android.os.Process.myUserHandle
import android.os.UserHandle
import android.text.TextUtils
import com.android.launcher3.concurrent.annotations.LightweightBackground
import com.android.launcher3.concurrent.annotations.LightweightBackgroundPriority
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.LooperExecutor
import com.android.launcher3.util.MutableListenableStream
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.SimpleBroadcastReceiver
import com.android.launcher3.util.SimpleBroadcastReceiver.Companion.actionsFilter
import javax.inject.Inject

/** Class to track changes to an app icon */
@LauncherAppSingleton
class IconChangeTracker
@Inject
constructor(
    @ApplicationContext context: Context,
    @LightweightBackground(LightweightBackgroundPriority.UI) executor: LooperExecutor,
    private val userCache: UserCache,
    lifecycleTracker: DaggerSingletonTracker,
) {

    private val calendar = context.parseComponentOrNull(R.string.calendar_component_name)
    private val clock = context.parseComponentOrNull(R.string.clock_component_name)

    private val _changes = MutableListenableStream<PackageUserKey>()
    val changes = _changes.asListenable()

    init {
        if (calendar != null || clock != null) {
            val receiver =
                SimpleBroadcastReceiver(context = context, executor = executor) { handleIntent(it) }
            receiver.register(
                actionsFilter(ACTION_TIMEZONE_CHANGED, ACTION_TIME_CHANGED, ACTION_DATE_CHANGED)
            )
            lifecycleTracker.addCloseable(receiver)
        }
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_TIMEZONE_CHANGED -> {
                if (clock != null) notifyIconChanged(clock.packageName, myUserHandle())
                dispatchCalendarIconChanged()
            }

            ACTION_DATE_CHANGED,
            ACTION_TIME_CHANGED -> dispatchCalendarIconChanged()
        }
    }

    private fun dispatchCalendarIconChanged() {
        if (calendar != null)
            userCache.userProfiles.forEach { notifyIconChanged(calendar.packageName, it) }
    }

    /** Notifies icon change event for [packageName] corresponding to [user] */
    fun notifyIconChanged(packageName: String, user: UserHandle) {
        _changes.dispatchValue(PackageUserKey(packageName, user))
    }

    companion object {
        private fun Context.parseComponentOrNull(resId: Int): ComponentName? {
            val cn = getString(resId)
            return if (TextUtils.isEmpty(cn)) null else ComponentName.unflattenFromString(cn)
        }
    }
}
