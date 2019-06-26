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

package ch.deletescape.lawnchair.bugreport

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.util.LawnchairSingletonHolder
import ch.deletescape.lawnchair.util.extensions.d
import ch.deletescape.lawnchair.util.extensions.e

class BugReportClient(private val context: Context) {

    private var bugReportService: IBugReportService? = null

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            d("Service connected")
            bugReportService = IBugReportService.Stub.asInterface(service)
            setAutoUploadEnabled()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            d("Service disconnected")
            bugReportService = null
            rebind()
        }
    }

    init {
        rebind()
    }

    fun rebindIfNeeded() {
        if (bugReportService == null) {
            rebind()
        }
    }

    private fun rebind() {
        try {
            context.startService(Intent(context, BugReportService::class.java))
            context.bindService(Intent(context, BugReportService::class.java), connection, 0)
        } catch (t: Throwable) {
            e("Failed to rebind", t)
        }
    }

    fun sendReport(report: BugReport) {
        bugReportService?.sendReport(report) ?: throw RuntimeException("Bug report service is null")
    }

    fun setAutoUploadEnabled() {
        bugReportService?.setAutoUploadEnabled(context.lawnchairPrefs.autoUploadBugReport)
    }

    companion object : LawnchairSingletonHolder<BugReportClient>(::BugReportClient)
}
