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
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.support.annotation.Keep
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController
import com.android.launcher3.Utilities
import com.android.quickstep.RecentsActivity

class LawnchairApp : Application() {

    val activityHandler = ActivityHandler()
    val fontLoader by lazy { FontLoader(this) }
    val smartspace by lazy { LawnchairSmartspaceController(this) }
    val bugReporter = LawnchairBugReporter(this, Thread.getDefaultUncaughtExceptionHandler())
    val recentsEnabled by lazy { checkRecentsComponent() }

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

    @Keep
    fun checkRecentsComponent(): Boolean {
        if (!Utilities.ATLEAST_P) return false
        if (!Utilities.HIDDEN_APIS_ALLOWED) return false

        val resId = resources.getIdentifier("config_recentsComponentName", "string", "android")
        if (resId == 0) return false
        val recentsComponent = ComponentName.unflattenFromString(resources.getString(resId))
                ?: return false
        return recentsComponent.packageName == packageName
                && recentsComponent.className == RecentsActivity::class.java.name
    }
}

val Context.lawnchairApp get() = applicationContext as LawnchairApp
