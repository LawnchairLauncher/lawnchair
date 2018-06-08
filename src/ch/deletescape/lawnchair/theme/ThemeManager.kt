package ch.deletescape.lawnchair.theme

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Looper
import com.android.launcher3.MainThreadExecutor
import com.android.launcher3.Utilities
import com.android.launcher3.dynamicui.WallpaperColorInfo
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

/*
 * Copyright (C) 2018 paphonb@xda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class ThemeManager(context: Context) : Application.ActivityLifecycleCallbacks, WallpaperColorInfo.OnThemeChangeListener {

    private val application = context.applicationContext as Application
    private val wallpaperColorInfo = WallpaperColorInfo.getInstance(context)!!
    private val listeners = HashMap<Activity, ThemeOverride>()
    private val prefs = Utilities.getLawnchairPrefs(context)
    private var themeFlags = 0

    init {
        application.registerActivityLifecycleCallbacks(this)
        wallpaperColorInfo.setOnThemeChangeListener(this)
        onThemeChanged()
    }

    fun addOverride(themeOverride: ThemeOverride) {
        synchronized(listeners) {
            listeners[themeOverride.activity] = themeOverride
        }
        themeOverride.overrideTheme(themeFlags)
    }

    override fun onThemeChanged() {
        val theme = prefs.launcherTheme
        val supportsDarkText: Boolean
        val isDark: Boolean
        val isBlack = isBlack(theme)
        if ((theme and THEME_AUTO) == 0) {
            supportsDarkText = isDarkText(theme)
            isDark = isDark(theme)
        } else {
            supportsDarkText = wallpaperColorInfo.supportsDarkText()
            isDark = wallpaperColorInfo.isDark
        }
        themeFlags = 0
        if (supportsDarkText) themeFlags = themeFlags or THEME_DARK_TEXT
        if (isDark) themeFlags = themeFlags or THEME_DARK
        if (isBlack) themeFlags = themeFlags or THEME_USE_BLACK
        synchronized(listeners) {
            listeners.values.forEach { it.onThemeChanged(themeFlags) }
        }
    }

    override fun onActivityDestroyed(activity: Activity?) {
        synchronized(listeners) {
            listeners.remove(activity)
        }
    }

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {}

    override fun onActivityPaused(activity: Activity?) {}

    override fun onActivityResumed(activity: Activity?) {}

    override fun onActivityStarted(activity: Activity?) {}

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {}

    override fun onActivityStopped(activity: Activity?) {}

    companion object {

        private const val THEME_AUTO = 1
        private const val THEME_DARK_TEXT = 1 shl 1
        private const val THEME_DARK = 1 shl 2
        private const val THEME_USE_BLACK = 1 shl 3

        fun isDarkText(flags: Int) = (flags and THEME_DARK_TEXT) != 0
        fun isDark(flags: Int) = (flags and THEME_DARK) != 0
        fun isBlack(flags: Int) = (flags and THEME_USE_BLACK) != 0

        private var INSTANCE: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager {
            if (INSTANCE == null) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    INSTANCE = ThemeManager(context.applicationContext)
                } else {
                    try {
                        return MainThreadExecutor().submit(Callable { ThemeManager.getInstance(context) }).get()
                    } catch (e: InterruptedException) {
                        throw RuntimeException(e)
                    } catch (e: ExecutionException) {
                        throw RuntimeException(e)
                    }

                }
            }
            return INSTANCE!!
        }
    }
}