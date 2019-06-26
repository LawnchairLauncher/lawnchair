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

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import com.android.launcher3.BuildConfig
import java.util.*

import com.android.launcher3.R

class DogbinUploadService : Service() {

    private var uploadStarted = false
    private val uploadQueue: Queue<BugReport> = LinkedList()

    override fun onBind(intent: Intent): IBinder {
        TODO("not implemented")
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        uploadQueue.offer(intent.getParcelableExtra("report"))
        if (!uploadStarted) {
            uploadNext()
        }
        return START_STICKY
    }

    private fun uploadNext() {
        uploadStarted = true
        val report = uploadQueue.poll()
        if (report == null) {
            stopSelf()
        } else {
            DogbinUtils.upload(report.contents, object : DogbinUtils.UploadResultCallback {

                override fun onSuccess(url: String) {
                    report.link = url
                    next()
                }

                override fun onFail(message: String, e: Exception) {
                    report.uploadError = true
                    next()
                }

                private fun next() {
                    startService(Intent(this@DogbinUploadService, BugReportService::class.java)
                            .putExtra("report", report))
                    uploadNext()
                }
            })
        }
    }

    override fun onCreate() {
        super.onCreate()

        Log.d("DUS", "onCreate")

        startForeground(101, NotificationCompat.Builder(this, BugReportService.STATUS_ID)
                .setSmallIcon(R.drawable.ic_bug_notification)
                .setContentTitle(getString(R.string.dogbin_uploading))
                .setColor(ContextCompat.getColor(this, R.color.bugNotificationColor))
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build())
    }
}
