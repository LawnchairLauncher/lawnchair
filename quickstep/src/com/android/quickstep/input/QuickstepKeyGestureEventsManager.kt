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

package com.android.quickstep.input

import android.Manifest.permission.MANAGE_KEY_GESTURES
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.hardware.input.InputManager
import android.hardware.input.InputManager.KeyGestureEventHandler
import android.hardware.input.KeyGestureEvent
import android.hardware.input.KeyGestureEvent.ACTION_GESTURE_COMPLETE
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_ALL_APPS
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER
import android.hardware.input.KeyGestureEvent.KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import android.provider.Settings.Secure.USER_SETUP_COMPLETE
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SettingsCache
import com.android.launcher3.util.SettingsCache.OnChangeListener
import com.android.quickstep.OverviewCommandHelper
import com.android.quickstep.dagger.QuickstepBaseAppComponent
import com.android.quickstep.input.QuickstepKeyGestureEventsManager.OverviewGestureHandler.OverviewType.ALT_TAB
import com.android.quickstep.input.QuickstepKeyGestureEventsManager.OverviewGestureHandler.OverviewType.UNDEFINED
import com.android.window.flags2.Flags
import javax.inject.Inject

/**
 * Manages subscription and unsubscription to launcher's key gesture events, e.g. all apps and
 * recents (incl. alt + tab).
 */
@LauncherAppSingleton
class QuickstepKeyGestureEventsManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val settingsCache: SettingsCache,
) {
    @VisibleForTesting
    val onUserSetupCompleteListener = OnChangeListener { isUserSetupCompleted = it }
    private val inputManager = requireNotNull(context.getSystemService(InputManager::class.java))
    private var allAppsPendingIntent: PendingIntent? = null
    private var overviewGestureHandler: OverviewGestureHandler? = null
    private var overviewCommandHelper: OverviewCommandHelper? = null
    private var isUserSetupCompleted: Boolean = settingsCache.getValue(USER_SETUP_COMPLETE_URI)

    init {
        settingsCache.register(USER_SETUP_COMPLETE_URI, onUserSetupCompleteListener)
    }

    @VisibleForTesting
    val allAppsKeyGestureEventHandler =
        object : KeyGestureEventHandler {
            override fun handleKeyGestureEvent(event: KeyGestureEvent, focusedToken: IBinder?) {
                if (!isManageKeyGesturesGrantedToRecents()) {
                    return
                }
                if (!isUserSetupCompleted) {
                    return
                }

                if (event.keyGestureType != KEY_GESTURE_TYPE_ALL_APPS) {
                    Log.e(TAG, "Ignore unsupported key gesture event type: ${event.keyGestureType}")
                    return
                }

                // Ignore the display ID from the KeyGestureEvent as we will use the focus display
                // from the SysUi proxy as the source of truth.
                allAppsPendingIntent?.send()
            }
        }

    @VisibleForTesting
    val overviewKeyGestureEventHandler =
        object : KeyGestureEventHandler {
            override fun handleKeyGestureEvent(event: KeyGestureEvent, focusedToken: IBinder?) {
                if (!isManageKeyGesturesGrantedToRecents()) {
                    return
                }
                if (!isUserSetupCompleted) {
                    return
                }

                val handler = overviewGestureHandler ?: return
                when (event.keyGestureType) {
                    KEY_GESTURE_TYPE_RECENT_APPS -> {
                        if (event.action == ACTION_GESTURE_COMPLETE && !event.isCancelled) {
                            handler.showOverview(UNDEFINED)
                        }
                    }
                    KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER -> {
                        if (event.action == KeyGestureEvent.ACTION_GESTURE_START) {
                            handler.showOverview(ALT_TAB)
                        } else {
                            handler.hideOverview(ALT_TAB)
                        }
                    }
                    else -> {
                        Log.e(
                            TAG,
                            "Ignore unsupported overview key gesture event type: " +
                                event.keyGestureType,
                        )
                    }
                }
            }
        }

    @VisibleForTesting
    val homeKeyGestureEventHandler =
        object : KeyGestureEventHandler {
            override fun handleKeyGestureEvent(event: KeyGestureEvent, focusedToken: IBinder?) {
                if (!isManageKeyGesturesGrantedToRecents()) {
                    return
                }
                if (!isUserSetupCompleted) {
                    return
                }

                if (event.keyGestureType != KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY) {
                    Log.e(TAG, "Ignore unsupported key gesture event type: ${event.keyGestureType}")
                    return
                }

                overviewCommandHelper?.addCommand(
                    OverviewCommandHelper.CommandType.HOME,
                    event.displayId,
                )
            }
        }

    /**
     * Registers the all apps key gesture events.
     *
     * Subsequent registrations are ignored until [unregisterAllAppsKeyGestureEvent] is called.
     */
    fun registerAllAppsKeyGestureEvent(allAppsPendingIntent: PendingIntent) {
        if (isManageKeyGesturesGrantedToRecents()) {
            synchronized(this) {
                if (this.allAppsPendingIntent != null) {
                    Log.w(TAG, "All apps key gesture has already been registered. Ignored.")
                    return
                }
                this.allAppsPendingIntent = allAppsPendingIntent
                inputManager.registerKeyGestureEventHandler(
                    listOf(KEY_GESTURE_TYPE_ALL_APPS),
                    allAppsKeyGestureEventHandler,
                )
            }
        }
    }

    /** Unregisters the all apps key gesture events. */
    fun unregisterAllAppsKeyGestureEvent() {
        if (isManageKeyGesturesGrantedToRecents()) {
            synchronized(this) {
                inputManager.unregisterKeyGestureEventHandler(allAppsKeyGestureEventHandler)
                this.allAppsPendingIntent = null
            }
        }
    }

    /**
     * Registers the overview key gesture events.
     *
     * Subsequent registrations are ignored until [unregisterOverviewKeyGestureEvent] is called.
     */
    fun registerOverviewKeyGestureEvent(overviewGestureHandler: OverviewGestureHandler) {
        if (isManageKeyGesturesGrantedToRecents()) {
            synchronized(this) {
                if (this.overviewGestureHandler != null) {
                    Log.w(TAG, "Overview key gesture has already been registered. Ignored.")
                    return
                }
                this.overviewGestureHandler = overviewGestureHandler
                inputManager.registerKeyGestureEventHandler(
                    listOf(KEY_GESTURE_TYPE_RECENT_APPS, KEY_GESTURE_TYPE_RECENT_APPS_SWITCHER),
                    overviewKeyGestureEventHandler,
                )
            }
        }
    }

    /** Unregisters the overview key gesture events. */
    fun unregisterOverviewKeyGestureEvent() {
        if (isManageKeyGesturesGrantedToRecents()) {
            synchronized(this) {
                inputManager.unregisterKeyGestureEventHandler(overviewKeyGestureEventHandler)
                this.overviewGestureHandler = null
            }
        }
    }

    fun registerHomeKeyGestureEvent(overviewCommandHelper: OverviewCommandHelper) {
        if (!isManageKeyGesturesGrantedToRecents()) {
            return
        }
        synchronized(this) {
            if (this.overviewCommandHelper != null) {
                Log.w(TAG, "Overview command helper has already been registered. Ignored.")
                return
            }
            this.overviewCommandHelper = overviewCommandHelper
            inputManager.registerKeyGestureEventHandler(
                listOf(KEY_GESTURE_TYPE_REJECT_HOME_ON_EXTERNAL_DISPLAY),
                homeKeyGestureEventHandler,
            )
        }
    }

    /** Unregisters the home key gesture events. */
    @VisibleForTesting
    fun unregisterHomeKeyGestureEvent() {
        if (!isManageKeyGesturesGrantedToRecents()) {
            return
        }
        synchronized(this) {
            inputManager.unregisterKeyGestureEventHandler(homeKeyGestureEventHandler)
            this.overviewCommandHelper = null
        }
    }

    fun onDestroy() {
        settingsCache.unregister(USER_SETUP_COMPLETE_URI, onUserSetupCompleteListener)
        unregisterOverviewKeyGestureEvent()
        unregisterAllAppsKeyGestureEvent()
        unregisterHomeKeyGestureEvent()
    }

    private fun isManageKeyGesturesGrantedToRecents(): Boolean =
        Flags.grantManageKeyGesturesToRecents() &&
            context.checkSelfPermission(MANAGE_KEY_GESTURES) == PERMISSION_GRANTED

    /** Callbacks for overview events, including alt + tab. */
    interface OverviewGestureHandler {
        enum class OverviewType {
            UNDEFINED,
            ALT_TAB,
            HOME,
        }

        /** Shows the overview UI with [type]. */
        fun showOverview(type: OverviewType)

        /** Hides the overview UI with [type]. */
        fun hideOverview(type: OverviewType)
    }

    private companion object {
        const val TAG = "KeyGestureEventsHandler"
        val USER_SETUP_COMPLETE_URI: Uri = Settings.Secure.getUriFor(USER_SETUP_COMPLETE)

        @JvmStatic
        val INSTANCE: DaggerSingletonObject<QuickstepKeyGestureEventsManager> =
            DaggerSingletonObject<QuickstepKeyGestureEventsManager>(
                QuickstepBaseAppComponent::getQuickstepKeyGestureEventsManager
            )
    }
}
