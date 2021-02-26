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

import android.service.notification.StatusBarNotification
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.runOnUiWorkerThread
import com.android.launcher3.notification.NotificationKeyData
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.util.PackageUserKey

object NotificationsManager : NotificationListener.NotificationsChangedListener {

    private val notificationsMap = mutableMapOf<String?, NotificationKeyData?>()
    private val listeners = mutableListOf<OnChangeListener>()

    var sbNotifications = emptyList<StatusBarNotification>()
    var notifications = emptyList<NotificationKeyData?>()
        private set

    init {
        NotificationListener.setNotificationsChangedListener(this)
    }

    fun addListener(listener: OnChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: OnChangeListener) {
        listeners.remove(listener)
    }

    private fun onChange() {
        val notifications = notificationsMap.values.toList()
        this.notifications = notifications
        listeners.forEach(OnChangeListener::onNotificationsChanged)
        this.sbNotifications =
                NotificationListener.getInstanceIfConnected()?.activeNotifications?.toList()
                ?: emptyList()
    }

    interface OnChangeListener {
        fun onNotificationsChanged()
    }

    override fun onNotificationRemoved(removedPackageUserKey: PackageUserKey?,
                                       notificationKey: NotificationKeyData?) {
        notificationsMap.remove(removedPackageUserKey?.mPackageName)
        onChange()
    }

    override fun onNotificationFullRefresh(
            activeNotifications: MutableList<StatusBarNotification>?) {
        runOnUiWorkerThread {
            val tmpMap: MutableMap<String, NotificationKeyData> =
                    emptyMap<String, NotificationKeyData>().toMutableMap()
            if (activeNotifications != null) {
                for (notification: StatusBarNotification in activeNotifications) {
                    tmpMap[PackageUserKey.fromNotification(notification).mPackageName] =
                            NotificationKeyData.fromNotification(notification)
                }
                runOnMainThread {
                    notificationsMap.clear()
                    notificationsMap.putAll(tmpMap)
                    onChange()
                }
            }
        }
    }

    override fun onNotificationPosted(postedPackageUserKey: PackageUserKey?,
                                      notificationKey: NotificationKeyData?) {
        notificationsMap[postedPackageUserKey?.mPackageName] = notificationKey
        onChange()
    }
}
