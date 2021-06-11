/*
 * Copyright 2021, Lawnchair
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

package app.lawnchair

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import app.lawnchair.util.restartLauncher
import com.android.launcher3.Utilities
import com.android.quickstep.RecentsActivity

class LawnchairApp : Application() {

    val activityHandler = ActivityHandler()
    var mismatchedQuickstepTarget = false
    private val recentsEnabled by lazy { checkRecentsComponent() }
    internal var accessibilityService: LawnchairAccessibilityService? = null
    val TAG = "LawnchairApp"

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    fun onLauncherAppStateCreated() {
        registerActivityLifecycleCallbacks(activityHandler)
    }

    fun restart(recreateLauncher: Boolean = true) {
        if (recreateLauncher) {
            activityHandler.finishAll()
        } else {
            restartLauncher(this)
        }
    }

    class ActivityHandler : ActivityLifecycleCallbacks {

        val activities = HashSet<Activity>()
        var foregroundActivity: Activity? = null

        fun finishAll() {
            HashSet(activities).forEach { it.finish() }
        }

        override fun onActivityPaused(activity: Activity) { }

        override fun onActivityResumed(activity: Activity) {
            foregroundActivity = activity
        }

        override fun onActivityStarted(activity: Activity) { }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity == foregroundActivity) foregroundActivity = null
            activities.remove(activity)
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) { }

        override fun onActivityStopped(activity: Activity) { }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activities.add(activity)
        }
    }

    private fun checkRecentsComponent(): Boolean {
        if (!Utilities.ATLEAST_R) {
            Log.d(TAG, "API < P, disabling recents")
            return false
        }

        val resId = resources.getIdentifier("config_recentsComponentName", "string", "android")
        if (resId == 0) {
            Log.d(TAG, "config_recentsComponentName not found, disabling recents")
            return false
        }
        val recentsComponent = ComponentName.unflattenFromString(resources.getString(resId))
        if (recentsComponent == null) {
            Log.d(TAG, "config_recentsComponentName is empty, disabling recents")
            return false
        }
        val isRecentsComponent = recentsComponent.packageName == packageName
                && recentsComponent.className == RecentsActivity::class.java.name
        if (!isRecentsComponent) {
            Log.d(TAG, "config_recentsComponentName ($recentsComponent) is not Lawnchair, disabling recents")
            return false
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            Log.d(TAG, "Quickstep target doesn't match, disabling recents")
            mismatchedQuickstepTarget = true
            return false
        }
        return true
    }

    fun isAccessibilityServiceBound(): Boolean = accessibilityService != null

    fun performGlobalAction(action: Int): Boolean {
        return if (accessibilityService != null) {
            accessibilityService!!.performGlobalAction(action)
        } else {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            false
        }
    }

    companion object {
        @JvmStatic
        var instance: LawnchairApp? = null
            private set

        @JvmStatic
        val isRecentsEnabled: Boolean get() = instance?.recentsEnabled == true
    }
}

val Context.lawnchairApp get() = applicationContext as LawnchairApp
val Context.foregroundActivity get() = lawnchairApp.activityHandler.foregroundActivity
