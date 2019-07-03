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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.ContextCompat.getSystemService
import android.widget.Toast
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.Utilities

class BugReportService : Service() {

    private val handler = Handler()
    private val resetThrottleRunnable = Runnable { reportCount = 0 }
    private var autoUpload = false

    private var reportCount = 0

    override fun onCreate() {
        super.onCreate()

        BugReportFileManager.getInstance(this)

        val filter = IntentFilter().apply {
            addAction(COPY_ACTION)
            addAction(UPLOAD_ACTION)
            addAction(UPLOAD_COMPLETE_ACTION)
        }
        val receiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {
                val report = intent.getParcelableExtra<BugReport>("report")
                when (intent.action) {
                    COPY_ACTION -> copyReport(report)
                    UPLOAD_ACTION -> startDogbinUpload(report)
                    UPLOAD_COMPLETE_ACTION -> notify(report)
                }
            }
        }
        registerReceiver(receiver, filter, PERMISSION, handler)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.hasExtra("report")) {
            notify(intent.getParcelableExtra("report"))
        }
        return super.onStartCommand(intent, flags, startId)
    }

    fun onNewReport(report: BugReport) {
        if (autoUpload) {
            startDogbinUpload(report)
        } else {
            notify(report)
        }
    }

    fun notify(report: BugReport, uploading: Boolean = false) {
        val manager = getSystemService(this, NotificationManager::class.java)!!
        val notificationId = report.notificationId
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(report.getTitle(this))
                .setContentText(report.description)
                .setSmallIcon(R.drawable.ic_bug_notification)
                .setColor(ContextCompat.getColor(this, R.color.bugNotificationColor))
                .setOnlyAlertOnce(true)
                .setGroup(GROUP_KEY)
                .setShowWhen(true)
                .setWhen(report.id)
                // This apparently breaks grouping
                //.setAutoCancel(true)

        val count = if (Utilities.ATLEAST_MARSHMALLOW) {
            manager.activeNotifications.filter { it.groupKey == GROUP_KEY }.count()
        } else -1
        val summary = if (count > 99 || count < 0) {
            getString(R.string.bugreport_group_summary_multiple)
        } else {
            getString(R.string.bugreport_group_summary, count)
        }
        val groupBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.bugreport_channel_name))
                .setContentText(summary)
                .setSmallIcon(R.drawable.ic_bug_notification)
                .setColor(ContextCompat.getColor(this, R.color.bugNotificationColor))
                .setStyle(NotificationCompat.InboxStyle()
                                  .setBigContentTitle(summary)
                                  .setSummaryText(getString(R.string.bugreport_channel_name)))
                .setGroupSummary(true)
                .setGroup(GROUP_KEY)

        val fileUri = report.getFileUri(this)
        if (report.link != null) {
            val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(report.link))
            val pendingOpenIntent = PendingIntent.getActivity(
                    this, notificationId, openIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            builder.setContentIntent(pendingOpenIntent)
        } else if (fileUri != null) {
            val openIntent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(fileUri, "text/plain")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val pendingOpenIntent = PendingIntent.getActivity(
                    this, notificationId, openIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            builder.setContentIntent(pendingOpenIntent)
        }

        val pendingShareIntent = PendingIntent.getActivity(
                this, notificationId, report.createShareIntent(this), PendingIntent.FLAG_UPDATE_CURRENT)
        val shareActionBuilder = NotificationCompat.Action.Builder(
                R.drawable.ic_share, getString(R.string.action_share), pendingShareIntent)
        builder.addAction(shareActionBuilder.build())

        if (report.link != null || fileUri == null) {
            val copyIntent = Intent(COPY_ACTION)
                    .setPackage(BuildConfig.APPLICATION_ID)
                    .putExtra("report", report)
            val pendingCopyIntent = PendingIntent.getBroadcast(
                    this, notificationId, copyIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            val copyText = if (report.link != null) R.string.action_copy_link else R.string.action_copy
            val copyActionBuilder = NotificationCompat.Action.Builder(
                    R.drawable.ic_copy, getString(copyText), pendingCopyIntent)
            builder.addAction(copyActionBuilder.build())
        }

        if (uploading) {
            builder.setOngoing(true)
            builder.setProgress(0, 0, true)
        } else if (report.link == null) {
            val uploadIntent = Intent(UPLOAD_ACTION)
                    .setPackage(BuildConfig.APPLICATION_ID)
                    .putExtra("report", report)
            val pendingUploadIntent = PendingIntent.getBroadcast(
                    this, notificationId, uploadIntent, PendingIntent.FLAG_UPDATE_CURRENT)
            val uploadText = if (report.uploadError) R.string.action_dogbin_upload_error else R.string.action_dogbin_upload
            val uploadActionBuilder = NotificationCompat.Action.Builder(
                    R.drawable.ic_upload, getString(uploadText), pendingUploadIntent)
            builder.addAction(uploadActionBuilder.build())
        }

        manager.notify(notificationId, builder.build())
        manager.notify(GROUP_ID, groupBuilder.build())
    }

    private fun startDogbinUpload(report: BugReport) {
        notify(report, true)
        startService(Intent(this, DogbinUploadService::class.java)
                .putExtra("report", report))
    }

    private fun copyReport(report: BugReport) {
        val clipData = ClipData.newPlainText(getString(R.string.lawnchair_bug_report), report.link ?: report.contents)
        getSystemService(this, ClipboardManager::class.java)!!.primaryClip = clipData

        Toast.makeText(this, R.string.copied_toast, Toast.LENGTH_LONG).show()
    }

    override fun onBind(intent: Intent): IBinder {
        return object : IBugReportService.Stub() {

            override fun sendReport(report: BugReport) {
                handler.post {
                    reportCount++
                    handler.removeCallbacks(resetThrottleRunnable)
                    if (reportCount <= 3) {
                        onNewReport(report)
                        handler.postDelayed(resetThrottleRunnable, 10000)
                    }
                }
            }

            override fun setAutoUploadEnabled(enable: Boolean) {
                autoUpload = enable
            }
        }
    }

    companion object {

        private const val CHANNEL_ID = "bugreport"
        const val STATUS_ID = "status"
        const val GROUP_KEY = "ch.deletescape.lawnchair.CRASHES"
        const val GROUP_ID = 0

        private const val PERMISSION = "${BuildConfig.APPLICATION_ID}.permission.BROADCAST_BUGREPORT"
        private const val COPY_ACTION = "${BuildConfig.APPLICATION_ID}.bugreport.COPY"
        private const val UPLOAD_ACTION = "${BuildConfig.APPLICATION_ID}.bugreport.UPLOAD"
        const val UPLOAD_COMPLETE_ACTION = "${BuildConfig.APPLICATION_ID}.bugreport.UPLOAD_COMPLETE"

        fun registerNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channels = arrayListOf(
                        NotificationChannel(CHANNEL_ID, context.getString(R.string.bugreport_channel_name), NotificationManager.IMPORTANCE_HIGH),
                        NotificationChannel(STATUS_ID, context.getString(R.string.status_channel_name), NotificationManager.IMPORTANCE_NONE)
                )

                // Register the channel with the system
                context.getSystemService(NotificationManager::class.java).createNotificationChannels(channels)
            }
        }

        fun getBroadcastIntent(context: Context, report: BugReport): Intent {
            return Intent(context, BugReportService::class.java)
                    .putExtra("report", report)
        }
    }
}
