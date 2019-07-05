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

package ch.deletescape.lawnchair.smartspace

import android.os.Looper
import android.service.notification.StatusBarNotification
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.runOnUiWorkerThread
import com.android.launcher3.LauncherNotifications
import com.android.launcher3.MainThreadExecutor
import com.android.launcher3.notification.NotificationKeyData
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.Preconditions
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

class NotificationsManager private constructor(): NotificationListener.NotificationsChangedListener {

    private val bgNotificationsMap = mutableMapOf<String, StatusBarNotification>()
    private val listeners  = mutableListOf<OnChangeListener>()
    private var refreshPending = false

    var notifications = emptyList<StatusBarNotification>()
        private set

    init {
        LauncherNotifications.getInstance().addListener(this)
    }

    fun addListener(listener: OnChangeListener) {
        listeners.add(listener)
        runOnUiWorkerThread {
            if (refreshPending) doFullRefresh()
        }
    }

    fun removeListener(listener: OnChangeListener) {
        listeners.remove(listener)
    }

    override fun onNotificationPosted(
            postedPackageUserKey: PackageUserKey?,
            notificationKey: NotificationKeyData,
            shouldBeFilteredOut: Boolean) {
        runOnUiWorkerThread {
            val sbn = NotificationListener.getInstanceIfConnected()
                    ?.getNotificationsForKeys(Collections.singletonList(notificationKey))
                    ?.firstOrNull()
            val key = notificationKey.notificationKey
            if (sbn != null) {
                bgNotificationsMap[key] = sbn
            } else {
                bgNotificationsMap.remove(key)
            }
            onChange()
        }
    }

    override fun onNotificationRemoved(
            removedPackageUserKey: PackageUserKey?,
            notificationKey: NotificationKeyData) {
        runOnUiWorkerThread {
            bgNotificationsMap.remove(notificationKey.notificationKey)
            onChange()
        }
    }

    override fun onNotificationFullRefresh(
            activeNotifications: MutableList<StatusBarNotification>?) {
        runOnUiWorkerThread {
            doFullRefresh()
        }
    }

    private fun doFullRefresh() {
        if (listeners.isEmpty()) {
            refreshPending = true
            return
        }

        refreshPending = false
        bgNotificationsMap.clear()
        NotificationListener.getInstanceIfConnected()?.activeNotifications
                ?.associateByTo(bgNotificationsMap) { it.key }
        onChange()
    }

    private fun onChange() {
        val notifications = bgNotificationsMap.values.toList()
        runOnMainThread {
            this.notifications = notifications
            listeners.forEach(OnChangeListener::onNotificationsChanged)
        }
    }

    interface OnChangeListener {

        fun onNotificationsChanged()
    }

    companion object {

        private var INSTANCE: NotificationsManager? = null

        @JvmStatic
        val instance: NotificationsManager get() {
            if (INSTANCE == null) {
                INSTANCE = if (Looper.myLooper() == Looper.getMainLooper()) {
                    NotificationsManager()
                } else {
                    try {
                        MainThreadExecutor().submit(Callable { NotificationsManager() }).get()
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
