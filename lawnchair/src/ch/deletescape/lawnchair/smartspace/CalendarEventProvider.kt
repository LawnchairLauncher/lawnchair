/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.text.TextUtils
import android.text.format.DateFormat
import ch.deletescape.lawnchair.runOnMainThread
import ch.deletescape.lawnchair.smartspace.LawnchairSmartspaceController.Line
import ch.deletescape.lawnchair.toBitmap
import com.android.launcher3.R
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

@SuppressLint("MissingPermission")
class CalendarEventProvider(controller: LawnchairSmartspaceController)
    : LawnchairSmartspaceController.PeriodicDataProvider(controller) {

    override val requiredPermissions = listOf(android.Manifest.permission.READ_CALENDAR)
    private val calendarProjection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DTSTART,
            CalendarContract.Instances.DTEND,
            CalendarContract.Instances.CUSTOM_APP_PACKAGE)

    private val oneMinute = TimeUnit.MINUTES.toMillis(1)

    private val includeBehind = oneMinute * 5
    private val includeAhead = oneMinute * 30

    override val timeout = oneMinute

    override fun updateData() {
        val card = createEventCard(getNextEvent())
        runOnMainThread {
            updateData(null, card)
        }
    }

    private fun createEventCard(event: CalendarEvent?): LawnchairSmartspaceController.CardData? {
        if (event == null) return null
        val icon = context.getDrawable(R.drawable.ic_calendar)!!.toBitmap()
        val lines = mutableListOf<Line>()
        lines.add(Line("${event.title} ${formatTimeRelative(event.start)}", TextUtils.TruncateAt.MIDDLE))
        lines.add(Line("${formatTime(event.start)} – ${formatTime(event.end)}"))
        return LawnchairSmartspaceController.CardData(icon, lines, getPendingIntent(event))
    }

    private fun getNextEvent(): CalendarEvent? {
        val currentTime = System.currentTimeMillis()
        context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                calendarProjection,
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf("${currentTime - includeBehind}", "${currentTime + includeAhead}"),
                "${CalendarContract.Events.DTSTART} ASC LIMIT 1")
                ?.use { c ->
                    while (c.moveToNext()) {
                        return CalendarEvent(
                                c.getLong(c.getColumnIndex(CalendarContract.Events._ID)),
                                c.getString(c.getColumnIndex(CalendarContract.Events.TITLE)),
                                c.getLong(c.getColumnIndex(CalendarContract.Events.DTSTART)),
                                c.getLong(c.getColumnIndex(CalendarContract.Events.DTEND)),
                                c.getString(c.getColumnIndex(CalendarContract.Events.CUSTOM_APP_PACKAGE)))
                    }
                }
        return null
    }

    private fun formatTime(time: Long) = DateFormat.getTimeFormat(context).format(Date(time))

    private fun formatTimeRelative(time: Long): String {
        val res = context.resources
        val currentTime = System.currentTimeMillis()
        if (time <= currentTime) {
            return res.getString(R.string.smartspace_now)
        }
        val minutesToEvent = ceil((time - currentTime).toDouble() / oneMinute).toInt()
        val timeString = if (minutesToEvent >= 60) {
            val hours = minutesToEvent / 60
            val minutes = minutesToEvent % 60
            val hoursString = res.getQuantityString(R.plurals.smartspace_hours, hours, hours)
            if (minutes <= 0) {
                hoursString
            } else {
                val minutesString =
                        res.getQuantityString(R.plurals.smartspace_minutes, minutes, minutes)
                res.getString(R.string.smartspace_hours_mins, hoursString, minutesString)
            }
        } else {
            res.getQuantityString(R.plurals.smartspace_minutes, minutesToEvent, minutesToEvent)
        }
        return res.getString(R.string.smartspace_in_time, timeString)
    }

    private fun getPendingIntent(event: CalendarEvent): PendingIntent? {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://com.android.calendar/events/${event.id}")
            `package` = event.appPackage
        }
        return PendingIntent.getActivity(context, 0, intent, 0)
    }

    data class CalendarEvent(val id: Long, val title: String, val start: Long, val end: Long, val appPackage: String?)
}
