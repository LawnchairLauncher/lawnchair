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

package com.android.launcher3

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.provider.Settings.Global.ANIMATOR_DURATION_SCALE
import android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE
import android.provider.Settings.Global.WINDOW_ANIMATION_SCALE
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/** Tracker Class for when user turns on/off remove animation setting. */
@LauncherAppSingleton
class RemoveAnimationSettingsTracker
@Inject
constructor(@ApplicationContext val context: Context, tracker: DaggerSingletonTracker) :
    ContentObserver(Handler(Looper.getMainLooper())) {

    private val contentResolver = context.contentResolver

    /** Caches the last seen value for registered keys. */
    private val cache: MutableMap<Uri, Float> = ConcurrentHashMap()

    init {
        UI_HELPER_EXECUTOR.execute {
            contentResolver.registerContentObserver(WINDOW_ANIMATION_SCALE_URI, false, this)
            contentResolver.registerContentObserver(TRANSITION_ANIMATION_SCALE_URI, false, this)
            contentResolver.registerContentObserver(ANIMATOR_DURATION_SCALE_URI, false, this)
        }

        tracker.addCloseable {
            UI_HELPER_EXECUTOR.execute { contentResolver.unregisterContentObserver(this) }
        }
    }

    /**
     * Returns the value for this classes key from the cache. If not in cache, will call
     * [updateValue] to fetch.
     */
    fun getValue(uri: Uri): Float {
        return getValue(uri, 1f)
    }

    /**
     * Returns the value for this classes key from the cache. If not in cache, will call
     * [getValueFromSettingsGlobal] to fetch.
     */
    private fun getValue(uri: Uri, defaultValue: Float): Float {
        return cache.computeIfAbsent(uri) { getValueFromSettingsGlobal(uri, defaultValue) }
    }

    /** Returns if user has opted into having no animation on their device. */
    fun isRemoveAnimationEnabled(): Boolean {
        return getValue(WINDOW_ANIMATION_SCALE_URI) == 0f &&
            getValue(TRANSITION_ANIMATION_SCALE_URI) == 0f &&
            getValue(ANIMATOR_DURATION_SCALE_URI) == 0f
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        if (uri == null) return
        updateValue(uri)
    }

    private fun getValueFromSettingsGlobal(uri: Uri, defaultValue: Float = 1f): Float {
        return Settings.Global.getFloat(contentResolver, uri.lastPathSegment, defaultValue)
    }

    private fun updateValue(uri: Uri, defaultValue: Float = 1f) {
        val newValue = getValueFromSettingsGlobal(uri, defaultValue)
        cache[uri] = newValue
    }

    companion object {
        @JvmField
        val INSTANCE =
            DaggerSingletonObject(LauncherAppComponent::getRemoveAnimationSettingsTracker)
        @JvmField
        val WINDOW_ANIMATION_SCALE_URI: Uri = Settings.Global.getUriFor(WINDOW_ANIMATION_SCALE)
        @JvmField
        val TRANSITION_ANIMATION_SCALE_URI: Uri =
            Settings.Global.getUriFor(TRANSITION_ANIMATION_SCALE)
        @JvmField
        val ANIMATOR_DURATION_SCALE_URI: Uri = Settings.Global.getUriFor(ANIMATOR_DURATION_SCALE)
    }
}
