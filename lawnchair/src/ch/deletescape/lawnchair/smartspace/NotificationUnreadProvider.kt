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

import android.support.annotation.Keep
import android.support.v4.app.NotificationCompat.PRIORITY_DEFAULT
import android.text.TextUtils
import ch.deletescape.lawnchair.loadSmallIcon
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController.CardData
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController.Line
import ch.deletescape.lawnchair.toBitmap
import com.android.launcher3.notification.NotificationInfo

@Keep
class NotificationUnreadProvider(controller: LawnchairSmartspaceController) :
        LawnchairSmartspaceController.DataProvider(controller),
        NotificationsManager.OnChangeListener {

    private val manager = NotificationsManager.instance

    init {
        manager.addListener(this)
        onNotificationsChanged()
    }

    override fun onNotificationsChanged() {
        updateData(null, getEventCard())
    }

    private fun getEventCard(): CardData? {
        val filtered = manager.notifications
                .filter { it.notification.priority >= PRIORITY_DEFAULT && !it.isOngoing }
        if (filtered.isEmpty()) return null

        val context = controller.context
        val sbn = filtered.reduce { acc, sbn ->
            if (sbn.notification.priority > acc.notification.priority) {
                sbn
            } else {
                acc
            }
        }
        val notif = NotificationInfo(context, sbn)
        val app = getApp(sbn).toString()
        val title = notif.title?.toString() ?: ""
        val splitted = splitTitle(title)
        val body = notif.text?.toString()?.trim()?.split("\n")?.firstOrNull() ?: ""

        val lines = mutableListOf<Line>()
        if (!TextUtils.isEmpty(body)) {
            lines.add(Line(body))
        }
        lines.addAll(splitted.reversed().map { Line(it) })

        val appLine = Line(app)
        if (!lines.contains(appLine)) {
            lines.add(appLine)
        }
        return CardData(
                sbn.notification.loadSmallIcon(context)?.toBitmap(),
                lines, notif.intent)
    }

    private fun splitTitle(title: String): Array<String> {
        val delimiters = arrayOf(": ", " - ", " • ")
        for (del in delimiters) {
            if (title.contains(del)) {
                return title.split(del.toRegex(), 2).toTypedArray()
            }
        }
        return arrayOf(title)
    }


    override fun onDestroy() {
        super.onDestroy()
        manager.removeListener(this)
    }
}
