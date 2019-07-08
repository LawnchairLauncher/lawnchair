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

package ch.deletescape.lawnchair

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.strictmode.Violation
import android.support.annotation.RequiresApi
import android.support.v4.content.ContextCompat
import ch.deletescape.lawnchair.bugreport.BugReport
import ch.deletescape.lawnchair.bugreport.BugReportClient
import ch.deletescape.lawnchair.bugreport.BugReportFileManager
import ch.deletescape.lawnchair.util.extensions.e
import com.android.launcher3.BuildConfig
import com.android.launcher3.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

class LawnchairBugReporter(private val context: Context, private val crashHandler: Thread.UncaughtExceptionHandler)
    : Thread.UncaughtExceptionHandler {

    private val hasPermission get() = ContextCompat.checkSelfPermission(context,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    private val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lawnchair/logs")
    private val cacheFolder by lazy { BugReportFileManager.getFolder(context) }
    private val appName by lazy { context.getString(R.string.derived_app_name) }

    override fun uncaughtException(t: Thread?, e: Throwable?) {
        handleException(e)
        crashHandler.uncaughtException(t, e)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun reportVmViolation(v: Violation) {
        writeReport(BugReport.TYPE_STRICT_MODE_VIOLATION, v)
    }

    private fun handleException(e: Throwable?) {
        if (e == null) return

        writeReport(BugReport.TYPE_UNCAUGHT_EXCEPTION, e)
    }

    fun writeReport(error: String, throwable: Throwable?) {
        Report(error, throwable).apply {
            send(save())
        }
    }

    inner class Report(val error: String, val throwable: Throwable? = null) {

        private val fileName = "$appName bug report ${SimpleDateFormat.getDateTimeInstance().format(Date())}"

        fun send(reportFile: File?) {
            if (!context.lawnchairPrefs.showCrashNotifications) return

            val baos = ByteArrayOutputStream()
            PrintStream(baos, true, "UTF-8").use {
                writeContents(it)
            }
            val contents = String(baos.toByteArray(), StandardCharsets.UTF_8)
            val report = BugReport(error, getDescription(throwable ?: return), contents, reportFile)
            try {
                BugReportClient.getInstance(context).sendReport(report)
            } catch (t: Throwable) {
                e("Failed to send bug report", t)
            }
        }

        private fun getDescription(throwable: Throwable): String {
            return "${throwable::class.java.name}: ${throwable.message}"
        }

        fun save(): File? {
            val dest = if (cacheFolder.exists()) {
                cacheFolder
            } else {
                if (!hasPermission) return null
                if (!folder.exists()) folder.mkdirs()
                folder
            }

            val file = File(dest, "$fileName.txt")
            if (!file.createNewFile()) return null

            val stream = PrintStream(file)
            writeContents(stream)
            stream.close()
            return file
        }

        private fun writeContents(stream: PrintStream) {
            stream.println(fileName)
            stream.println("$appName version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            stream.println("build.brand: ${Build.BRAND}")
            stream.println("build.device: ${Build.DEVICE}")
            stream.println("build.display: ${Build.DISPLAY}")
            stream.println("build.fingerprint: ${Build.FINGERPRINT}")
            stream.println("build.hardware: ${Build.HARDWARE}")
            stream.println("build.id: ${Build.ID}")
            stream.println("build.manufacturer: ${Build.MANUFACTURER}")
            stream.println("build.model: ${Build.MODEL}")
            stream.println("build.product: ${Build.PRODUCT}")
            stream.println("build.type: ${Build.TYPE}")
            stream.println("version.codename: ${Build.VERSION.CODENAME}")
            stream.println("version.incremental: ${Build.VERSION.INCREMENTAL}")
            stream.println("version.release: ${Build.VERSION.RELEASE}")
            stream.println("version.sdk_int: ${Build.VERSION.SDK_INT}")
            stream.println()
            stream.println("error: $error")
            if (throwable != null) {
                stream.println()
                stream.println("--------- beginning of stacktrace")
                throwable.printStackTrace(stream)
            }
        }
    }
}