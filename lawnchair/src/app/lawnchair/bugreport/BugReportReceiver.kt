package app.lawnchair.bugreport

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.*
import android.graphics.drawable.Icon
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

class BugReportReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val report = intent.getParcelableExtra<BugReport>("report")!!
        when (intent.action) {
            COPY_ACTION -> copyReport(context, report)
            UPLOAD_ACTION -> startUpload(context, report)
            UPLOAD_COMPLETE_ACTION -> notify(context, report)
        }
    }

    private fun copyReport(context: Context, report: BugReport) {
        val clipData = ClipData.newPlainText(context.getString(R.string.lawnchair_bug_report), report.link ?: report.contents)
        context.getSystemService<ClipboardManager>()!!.setPrimaryClip(clipData)
        Toast.makeText(context, R.string.copied_toast, Toast.LENGTH_LONG).show()
    }

    private fun startUpload(context: Context, report: BugReport) {
        notify(context, report, true)
        context.startService(Intent(context, UploaderService::class.java)
            .putExtra("report", report))
    }

    companion object {
        const val notificationChannelId = "${BuildConfig.APPLICATION_ID}.BugReport"
        const val statusChannelId = "${BuildConfig.APPLICATION_ID}.status"

        private const val GROUP_KEY = "${BuildConfig.APPLICATION_ID}.crashes"
        private const val GROUP_ID = 0

        private const val COPY_ACTION = "${BuildConfig.APPLICATION_ID}.bugreport.COPY"
        private const val UPLOAD_ACTION = "${BuildConfig.APPLICATION_ID}.bugreport.UPLOAD"
        const val UPLOAD_COMPLETE_ACTION = "${BuildConfig.APPLICATION_ID}.bugreport.UPLOAD_COMPLETE"

        fun notify(context: Context, report: BugReport, uploading: Boolean = false) {
            val manager = context.getSystemService<NotificationManager>()!!
            val notificationId = report.notificationId
            val builder = Notification.Builder(context, notificationChannelId)
                .setContentTitle(report.getTitle(context))
                .setContentText(report.description)
                .setSmallIcon(R.drawable.ic_bug_notification)
                .setColor(ContextCompat.getColor(context, R.color.bugNotificationColor))
                .setOnlyAlertOnce(true)
                .setGroup(GROUP_KEY)
                .setShowWhen(true)
                .setWhen(report.timestamp)

            val count = manager.activeNotifications.count { it.groupKey == GROUP_KEY }
            val summary = if (count > 99 || count < 0) {
                context.getString(R.string.bugreport_group_summary_multiple)
            } else {
                context.getString(R.string.bugreport_group_summary, count)
            }
            val groupBuilder = Notification.Builder(context, notificationChannelId)
                .setContentTitle(context.getString(R.string.bugreport_channel_name))
                .setContentText(summary)
                .setSmallIcon(R.drawable.ic_bug_notification)
                .setColor(ContextCompat.getColor(context, R.color.bugNotificationColor))
                .setStyle(Notification.InboxStyle()
                    .setBigContentTitle(summary)
                    .setSummaryText(context.getString(R.string.bugreport_channel_name)))
                .setGroupSummary(true)
                .setGroup(GROUP_KEY)

            val fileUri = report.getFileUri(context)
            if (report.link != null) {
                val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(report.link))
                val pendingOpenIntent = PendingIntent.getActivity(
                    context, notificationId, openIntent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
                builder.setContentIntent(pendingOpenIntent)
            } else if (fileUri != null) {
                val openIntent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(fileUri, "text/plain")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val pendingOpenIntent = PendingIntent.getActivity(
                    context, notificationId, openIntent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
                builder.setContentIntent(pendingOpenIntent)
            }

            val pendingShareIntent = PendingIntent.getActivity(
                context, notificationId, report.createShareIntent(context), FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
            val icon = Icon.createWithResource(context, R.drawable.ic_share)
            val shareActionBuilder = Notification.Action.Builder(
                icon, context.getString(R.string.action_share), pendingShareIntent)
            builder.addAction(shareActionBuilder.build())

            if (report.link != null || fileUri == null) {
                val copyIntent = Intent(COPY_ACTION)
                    .setPackage(BuildConfig.APPLICATION_ID)
                    .putExtra("report", report)
                val pendingCopyIntent = PendingIntent.getBroadcast(
                    context, notificationId, copyIntent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
                val copyText = if (report.link != null) R.string.action_copy_link else R.string.action_copy
                val copyIcon = Icon.createWithResource(context, R.drawable.ic_copy)
                val copyActionBuilder = Notification.Action.Builder(
                    copyIcon, context.getString(copyText), pendingCopyIntent)
                builder.addAction(copyActionBuilder.build())
            }

            if (uploading) {
                builder.setOngoing(true)
                builder.setProgress(0, 0, true)
            } else if (report.link == null && UploaderUtils.isAvailable) {
                val uploadIntent = Intent(UPLOAD_ACTION)
                    .setPackage(BuildConfig.APPLICATION_ID)
                    .putExtra("report", report)
                val pendingUploadIntent = PendingIntent.getBroadcast(
                    context, notificationId, uploadIntent, FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
                val uploadText = if (report.uploadError) R.string.action_upload_error else R.string.action_upload_crash_report
                val uploadIcon = Icon.createWithResource(context, R.drawable.ic_upload)
                val uploadActionBuilder = Notification.Action.Builder(
                    uploadIcon, context.getString(uploadText), pendingUploadIntent)
                builder.addAction(uploadActionBuilder.build())
            }

            manager.notify(notificationId, builder.build())
            manager.notify(GROUP_ID, groupBuilder.build())
        }
    }
}
