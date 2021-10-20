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
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.util.restartLauncher
import com.android.launcher3.InvariantDeviceProfile
import com.android.quickstep.RecentsActivity
import com.android.systemui.shared.system.QuickStepContract
import java.io.File

class LawnchairApp : Application() {

    val activityHandler = ActivityHandler()
    private val recentsEnabled by lazy { checkRecentsComponent() }
    internal var accessibilityService: LawnchairAccessibilityService? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        QuickStepContract.sRecentsDisabled = !recentsEnabled
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

    fun migrateDbName(dbName: String) {
        val dbFile = getDatabasePath(dbName)
        if (dbFile.exists()) return
        val prefs = PreferenceManager.INSTANCE.get(this)
        val dbJournalFile = getJournalFile(dbFile)
        val oldDbSlot = prefs.sp.getString("pref_currentDbSlot", "a")
        val oldDbName = if (oldDbSlot == "a") "launcher.db" else "launcher.db_b"
        val oldDbFile = getDatabasePath(oldDbName)
        val oldDbJournalFile = getJournalFile(oldDbFile)
        if (oldDbFile.exists()) {
            oldDbFile.copyTo(dbFile)
            oldDbJournalFile.copyTo(dbJournalFile)
        }
    }

    fun cleanUpDatabases() {
        val idp = InvariantDeviceProfile.INSTANCE.get(this)
        val dbName = idp.dbFile
        val dbFile = getDatabasePath(dbName)
        dbFile?.parentFile?.listFiles()?.forEach { file ->
            val name = file.name
            if (name.startsWith("launcher") && !name.startsWith(dbName)) {
                file.delete()
            }
        }
    }

    private fun getJournalFile(file: File): File =
        File(file.parentFile, "${file.name}-journal")

    class ActivityHandler : ActivityLifecycleCallbacks {

        val activities = HashSet<Activity>()
        var foregroundActivity: Activity? = null

        fun finishAll() {
            HashSet(activities).forEach { it.finish() }
        }

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) {
            foregroundActivity = activity
        }

        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityDestroyed(activity: Activity) {
            if (activity == foregroundActivity) foregroundActivity = null
            activities.remove(activity)
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityStopped(activity: Activity) {}

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activities.add(activity)
        }
    }

    private fun checkRecentsComponent(): Boolean {
        if (Build.VERSION.SDK_INT !in Build.VERSION_CODES.R..Build.VERSION_CODES.S) {
            Log.d(TAG, "API ${Build.VERSION.SDK_INT} unsupported, disabling recents")
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
        private const val TAG = "LawnchairApp"

        @JvmStatic
        var instance: LawnchairApp? = null
            private set

        @JvmStatic
        val isRecentsEnabled: Boolean
            get() = instance?.recentsEnabled == true
    }
}

val Context.lawnchairApp get() = applicationContext as LawnchairApp
val Context.foregroundActivity get() = lawnchairApp.activityHandler.foregroundActivity
