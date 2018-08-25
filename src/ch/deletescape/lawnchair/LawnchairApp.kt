/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController

class LawnchairApp : Application() {

    val activityHandler = ActivityHandler()
    val fontLoader by lazy { FontLoader(this) }
    val smartspace by lazy { LawnchairSmartspaceController(this) }
    val bugReporter = LawnchairBugReporter(this, Thread.getDefaultUncaughtExceptionHandler())

    init {
        Thread.setDefaultUncaughtExceptionHandler(bugReporter)
        registerActivityLifecycleCallbacks(activityHandler)
    }

    fun restart(recreateLauncher: Boolean = true) {
        activityHandler.finishAll(recreateLauncher)
    }

    class ActivityHandler : ActivityLifecycleCallbacks {

        val activities = HashSet<Activity>()
        var foregroundActivity: Activity? = null

        fun finishAll(recreateLauncher: Boolean = true) {
            HashSet(activities).forEach { if (recreateLauncher && it is LawnchairLauncher) it.recreate() else it.finish() }
        }

        override fun onActivityPaused(activity: Activity) {

        }

        override fun onActivityResumed(activity: Activity) {
            foregroundActivity = activity
        }

        override fun onActivityStarted(activity: Activity) {

        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity == foregroundActivity)
                foregroundActivity = null
            activities.remove(activity)
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {

        }

        override fun onActivityStopped(activity: Activity) {

        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activities.add(activity)
        }
    }
}

val Context.lawnchairApp get() = applicationContext as LawnchairApp
