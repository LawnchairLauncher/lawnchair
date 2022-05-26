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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.lawnchair.backup.LawnchairBackup
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.preferences.openAppInfo
import app.lawnchair.util.restartLauncher
import app.lawnchair.views.ComposeBottomSheet
import com.android.launcher3.BuildConfig
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.quickstep.RecentsActivity
import com.android.systemui.shared.system.QuickStepContract
import java.io.File

class LawnchairApp : Application() {

    val activityHandler = ActivityHandler()
    private val compatible = Build.VERSION.SDK_INT in BuildConfig.QUICKSTEP_MIN_SDK..BuildConfig.QUICKSTEP_MAX_SDK
    private val isRecentsComponent by lazy { checkRecentsComponent() }
    private val recentsEnabled get() = compatible && isRecentsComponent
    internal var accessibilityService: LawnchairAccessibilityService? = null
    val vibrateOnIconAnimation by lazy { getSystemUiBoolean("config_vibrateOnIconAnimation", false) }

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

    fun renameRestoredDb(dbName: String) {
        val restoredDbFile = getDatabasePath(LawnchairBackup.RESTORED_DB_FILE_NAME)
        if (!restoredDbFile.exists()) return
        val dbFile = getDatabasePath(dbName)
        restoredDbFile.renameTo(dbFile)
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
            oldDbFile.delete()
            oldDbJournalFile.delete()
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

    private fun getSystemUiBoolean(resName: String, fallback: Boolean): Boolean {
        val systemUiPackage = "com.android.systemui"
        val res = packageManager.getResourcesForApplication(systemUiPackage)
        val resId = res.getIdentifier(resName, "bool", systemUiPackage)
        if (resId == 0) {
            return fallback
        }
        return res.getBoolean(resId)
    }

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

        fun Launcher.showQuickstepWarningIfNecessary() {
            val launcher = this
            if (!lawnchairApp.isRecentsComponent || isRecentsEnabled) return
            ComposeBottomSheet.show(this) {
                AlertBottomSheetContent(
                    title = { Text(text = stringResource(id = R.string.quickstep_incompatible)) },
                    text = {
                        val description = stringResource(
                            id = R.string.quickstep_incompatible_description,
                            stringResource(id = R.string.derived_app_name),
                            Build.VERSION.RELEASE
                        )
                        Text(text = description)
                    },
                    buttons = {
                        OutlinedButton(
                            onClick = {
                                openAppInfo(launcher)
                                close(true)
                            }
                        ) {
                            Text(text = stringResource(id = R.string.app_info_drop_target_label))
                        }
                        Spacer(modifier = Modifier.requiredWidth(8.dp))
                        Button(
                            onClick = { close(true) }
                        ) {
                            Text(text = stringResource(id = android.R.string.ok))
                        }
                    }
                )
            }
        }

        fun getUriForFile(context: Context, file: File): Uri {
            return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        }
    }
}

val Context.lawnchairApp get() = applicationContext as LawnchairApp
val Context.foregroundActivity get() = lawnchairApp.activityHandler.foregroundActivity
