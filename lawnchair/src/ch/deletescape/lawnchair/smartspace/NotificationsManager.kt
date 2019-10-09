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
import com.android.launcher3.notification.NotificationListener

object NotificationsManager : NotificationListener.StatusBarNotificationsChangedListener {

    private val notificationsMap = mutableMapOf<String, StatusBarNotification>()
    private val listeners  = mutableListOf<OnChangeListener>()

    var notifications = emptyList<StatusBarNotification>()
        private set

    init {
        NotificationListener.setStatusBarNotificationsChangedListener(this)
    }

    fun addListener(listener: OnChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: OnChangeListener) {
        listeners.remove(listener)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        notificationsMap[sbn.key] = sbn
        onChange()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        notificationsMap.remove(sbn.key)
        onChange()
    }

    override fun onNotificationFullRefresh() {
        runOnUiWorkerThread {
            val tmpMap = NotificationListener.getInstanceIfConnected()
                    ?.activeNotifications?.associateBy { it.key }
            runOnMainThread {
                notificationsMap.clear()
                if (tmpMap != null) {
                    notificationsMap.putAll(tmpMap)
                }
                onChange()
            }
        }
    }

    private fun onChange() {
        val notifications = notificationsMap.values.toList()
        this.notifications = notifications
        listeners.forEach(OnChangeListener::onNotificationsChanged)
    }

    interface OnChangeListener {

        fun onNotificationsChanged()
    }
}
