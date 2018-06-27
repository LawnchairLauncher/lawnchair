package ch.deletescape.lawnchair

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.support.v4.content.ContextCompat
import java.io.File
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.*

class LawnchairApp : Application() {

    init {
        Thread.setDefaultUncaughtExceptionHandler(LawnchairCrashHandler(this, Thread.getDefaultUncaughtExceptionHandler()))
    }

    private class LawnchairCrashHandler(val context: Context, val defaultHandler: Thread.UncaughtExceptionHandler)
        : Thread.UncaughtExceptionHandler {

        val hasPermission get() = ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lawnchair/logs")

        override fun uncaughtException(t: Thread?, e: Throwable?) {
            if (e == null) return
            if (!hasPermission) return
            if (!folder.exists()) folder.mkdirs()

            val file = File(folder, getFileName())
            if (!file.createNewFile()) return

            val stream = PrintStream(file)
            e.printStackTrace(stream)
            stream.close()

            defaultHandler.uncaughtException(t, e)
        }

        fun getFileName(): String? {
            val dateFormat = SimpleDateFormat.getDateTimeInstance()
            return "Lawnchair_crash_${dateFormat.format(Date())}.txt"
        }
    }
}
