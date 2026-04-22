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

package com.android.launcher3.model

import android.app.admin.DevicePolicyManager.ACTION_DEVICE_POLICY_RESOURCE_UPDATED
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ArchiveCompatibilityParams
import app.lawnchair.icons.LawnchairIconProvider
import com.android.launcher3.BuildConfigs
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.LauncherModel
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.graphics.ThemeManager.ThemeChangeListener
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.LauncherIcons.IconPool
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.pm.InstallSessionHelper
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.SettingsCache.NOTIFICATION_BADGING_URI
import com.android.launcher3.util.SettingsCache.PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI
import com.android.launcher3.util.SimpleBroadcastReceiver
import com.android.launcher3.widget.custom.CustomWidgetManager
import javax.inject.Inject

/** Utility class for initializing all model callbacks */
class ModelInitializer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val iconPool: IconPool,
    private val iconCache: IconCache,
    private val idp: InvariantDeviceProfile,
    private val themeManager: ThemeManager,
    private val userCache: UserCache,
    private val settingsCache: SettingsCache,
    private val iconProvider: LawnchairIconProvider,
    private val customWidgetManager: CustomWidgetManager,
    private val installSessionHelper: InstallSessionHelper,
    private val lifeCycle: DaggerSingletonTracker,
) {

    fun initialize(model: LauncherModel) {
        initializeDisplayEvents(model)

        // System changes
        val modelCallbacks = model.newModelCallbacks()
        val launcherApps = context.getSystemService(LauncherApps::class.java)!!
        launcherApps.registerCallback(modelCallbacks, MODEL_EXECUTOR.handler)
        lifeCycle.addCloseable { launcherApps.unregisterCallback(modelCallbacks) }

        if (Utilities.ATLEAST_V && Flags.enableSupportForArchiving()) {
            launcherApps.setArchiveCompatibility(
                ArchiveCompatibilityParams().apply {
                    setEnableUnarchivalConfirmation(false)
                    setEnableIconOverlay(!Flags.useNewIconForArchivedApps())
                }
            )
        }

        // Device profile policy changes
        val dpUpdateReceiver =
            SimpleBroadcastReceiver(context, UI_HELPER_EXECUTOR) {
                model.enqueueModelUpdateTask(ReloadStringCacheTask())
            }
        dpUpdateReceiver.register(ACTION_DEVICE_POLICY_RESOURCE_UPDATED)
        lifeCycle.addCloseable { dpUpdateReceiver.unregisterReceiverSafely() }

        // Development helper
        if (BuildConfigs.IS_STUDIO_BUILD) {
            val reloadReceiver =
                SimpleBroadcastReceiver(context, UI_HELPER_EXECUTOR) { model.forceReload() }
            reloadReceiver.register(Context.RECEIVER_EXPORTED, ACTION_FORCE_RELOAD)
            lifeCycle.addCloseable { reloadReceiver.unregisterReceiverSafely() }
        }

        // User changes
        lifeCycle.addCloseable(userCache.addUserEventListener(model::onUserEvent))

        // Private space settings changes
        val psSettingsListener = SettingsCache.OnChangeListener { model.forceReload() }
        settingsCache.register(PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI, psSettingsListener)
        lifeCycle.addCloseable {
            settingsCache.unregister(PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI, psSettingsListener)
        }

        // Notification dots changes
        val notificationChanges =
            SettingsCache.OnChangeListener { dotsEnabled ->
                if (dotsEnabled)
                    NotificationListener.requestRebind(
                        ComponentName(context, NotificationListener::class.java)
                    )
            }
        settingsCache.register(NOTIFICATION_BADGING_URI, notificationChanges)
        notificationChanges.onSettingsChanged(settingsCache.getValue(NOTIFICATION_BADGING_URI))
        lifeCycle.addCloseable {
            settingsCache.unregister(NOTIFICATION_BADGING_URI, notificationChanges)
        }

        // Custom widgets
        lifeCycle.addCloseable(customWidgetManager.addWidgetRefreshCallback(model::rebindCallbacks))

        // Install session changes
        lifeCycle.addCloseable(installSessionHelper.registerInstallTracker(modelCallbacks))
    }

    fun initializeDisplayEvents(model: LauncherModel) {
        fun refreshAndReloadLauncher() {
            iconPool.clear()
            iconCache.updateIconParams(idp.fillResIconDpi, idp.iconBitmapSize)
            model.forceReload()
        }

        // IDP changes
        val idpChangeListener = OnIDPChangeListener { modelChanged ->
            if (modelChanged) refreshAndReloadLauncher()
        }
        idp.addOnChangeListener(idpChangeListener)
        lifeCycle.addCloseable { idp.removeOnChangeListener(idpChangeListener) }

        // Theme changes
        val themeChangeListener = ThemeChangeListener { refreshAndReloadLauncher() }
        themeManager.addChangeListener(themeChangeListener)
        lifeCycle.addCloseable { themeManager.removeChangeListener(themeChangeListener) }

        // Icon changes
        lifeCycle.addCloseable(
            iconProvider.registerIconChangeListener(model::onAppIconChanged, MODEL_EXECUTOR.handler)
        )
    }

    companion object {
        private const val ACTION_FORCE_RELOAD = "force-reload-launcher"
    }
}
