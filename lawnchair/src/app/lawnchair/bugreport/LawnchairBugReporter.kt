package app.lawnchair.bugreport

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import app.lawnchair.LawnchairApp
import app.lawnchair.util.requireSystemService
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import com.android.launcher3.util.MainThreadInitializedObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class LawnchairBugReporter(private val context: Context) {

    private val notificationManager: NotificationManager = context.requireSystemService()
    private val logsFolder by lazy { File(context.cacheDir, "logs").apply { mkdirs() } }
    private val appName by lazy { context.getString(R.string.derived_app_name) }

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                BugReportReceiver.notificationChannelId,
                context.getString(R.string.bugreport_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                BugReportReceiver.statusChannelId,
                context.getString(R.string.status_channel_name),
                NotificationManager.IMPORTANCE_NONE
            )
        )

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            sendNotification(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        removeDismissedLogs()
    }

    private fun removeDismissedLogs() {
        val activeIds = notificationManager.activeNotifications
            .mapTo(mutableSetOf()) { String.format("%x", it.id) }
        (logsFolder.listFiles() as Array<File>)
            .filter { it.name !in activeIds }
            .forEach { it.deleteRecursively() }
    }

    private fun sendNotification(throwable: Throwable) {
        val bugReport = Report(BugReport.TYPE_UNCAUGHT_EXCEPTION, throwable)
            .generateBugReport() ?: return

        val notifications = notificationManager.activeNotifications
        val hasNotification = notifications.any { it.id == bugReport.notificationId }
        if (hasNotification || notifications.size > 3) {
            return
        }
        BugReportReceiver.notify(context, bugReport)
    }

    inner class Report(val error: String, val throwable: Throwable? = null) {

        private val fileName = "$appName bug report ${SimpleDateFormat.getDateTimeInstance().format(Date())}"

        fun generateBugReport(): BugReport? {
            val contents = writeContents()
            val contentsWithHeader = "$fileName\n$contents"
            val id = contents.hashCode()
            val reportFile = save(contentsWithHeader, id)

            return BugReport(id, error, getDescription(throwable ?: return null), contentsWithHeader, reportFile)
        }

        private fun getDescription(throwable: Throwable): String {
            return "${throwable::class.java.name}: ${throwable.message}"
        }

        private fun save(contents: String, id: Int): File? {
            val dest = File(logsFolder, String.format("%x", id))
            dest.mkdirs()

            val file = File(dest, "$fileName.txt")
            if (!file.createNewFile()) return null
            file.writeText(contents)
            return file
        }

        private fun writeContents() = StringBuilder()
            .appendLine("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            .appendLine("commit: ${BuildConfig.COMMIT_HASH}")
            .appendLine("build.brand: ${Build.BRAND}")
            .appendLine("build.device: ${Build.DEVICE}")
            .appendLine("build.display: ${Build.DISPLAY}")
            .appendLine("build.fingerprint: ${Build.FINGERPRINT}")
            .appendLine("build.hardware: ${Build.HARDWARE}")
            .appendLine("build.id: ${Build.ID}")
            .appendLine("build.manufacturer: ${Build.MANUFACTURER}")
            .appendLine("build.model: ${Build.MODEL}")
            .appendLine("build.product: ${Build.PRODUCT}")
            .appendLine("build.type: ${Build.TYPE}")
            .appendLine("version.codename: ${Build.VERSION.CODENAME}")
            .appendLine("version.incremental: ${Build.VERSION.INCREMENTAL}")
            .appendLine("version.release: ${Build.VERSION.RELEASE}")
            .appendLine("version.sdk_int: ${Build.VERSION.SDK_INT}")
            .appendLine("display.density_dpi: ${context.resources.displayMetrics.densityDpi}")
            .appendLine("isRecentsEnabled: ${LawnchairApp.isRecentsEnabled}")
            .appendLine()
            .appendLine("error: $error")
            .also {
                if (throwable != null) {
                    it
                        .appendLine()
                        .appendLine(Log.getStackTraceString(throwable))
                }
            }
            .toString()
    }

    companion object {
        val INSTANCE = MainThreadInitializedObject(::LawnchairBugReporter)
    }
}
