/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.wm.shell.flicker.utils

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.android.compatibility.common.util.SystemUtil.runShellCommand

class NotificationListener : NotificationListenerService() {
    private val notifications: MutableMap<Any, StatusBarNotification> = mutableMapOf()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (DEBUG) Log.d(TAG, "onNotificationPosted: $sbn")
        notifications[sbn.key] = sbn
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (DEBUG) Log.d(TAG, "onNotificationRemoved: $sbn")
        notifications.remove(sbn.key)
    }

    override fun onListenerConnected() {
        if (DEBUG) Log.d(TAG, "onListenerConnected")
        instance = this
    }

    override fun onListenerDisconnected() {
        if (DEBUG) Log.d(TAG, "onListenerDisconnected")
        instance = null
        notifications.clear()
    }

    companion object {
        private const val DEBUG = false
        private const val TAG = "WMShellFlickerTests_NotificationListener"

        private const val CMD_NOTIFICATION_ALLOW_LISTENER = "cmd notification allow_listener %s"
        private const val CMD_NOTIFICATION_DISALLOW_LISTENER =
            "cmd notification disallow_listener %s"
        private const val COMPONENT_NAME = "com.android.wm.shell.flicker/.NotificationListener"

        private var instance: NotificationListener? = null

        fun startNotificationListener(): Boolean {
            if (instance != null) {
                return true
            }

            runShellCommand(CMD_NOTIFICATION_ALLOW_LISTENER.format(COMPONENT_NAME))
            return wait { instance != null }
        }

        fun stopNotificationListener(): Boolean {
            if (instance == null) {
                return true
            }

            runShellCommand(CMD_NOTIFICATION_DISALLOW_LISTENER.format(COMPONENT_NAME))
            return wait { instance == null }
        }

        fun findNotification(
            predicate: (StatusBarNotification) -> Boolean
        ): StatusBarNotification? {
            instance?.run {
                return notifications.values.firstOrNull(predicate)
            }
                ?: throw IllegalStateException("NotificationListenerService is not connected")
        }

        fun waitForNotificationToAppear(
            predicate: (StatusBarNotification) -> Boolean
        ): StatusBarNotification? {
            instance?.let {
                return waitForResult(extractor = { it.notifications.values.firstOrNull(predicate) })
                    .second
            }
                ?: throw IllegalStateException("NotificationListenerService is not connected")
        }

        fun waitForNotificationToDisappear(predicate: (StatusBarNotification) -> Boolean): Boolean {
            return instance?.let { wait { it.notifications.values.none(predicate) } }
                ?: throw IllegalStateException("NotificationListenerService is not connected")
        }
    }
}
