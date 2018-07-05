package ch.deletescape.lawnchair

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.support.v4.content.ContextCompat
import com.android.launcher3.BuildConfig
import java.io.File
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashSet

class LawnchairApp : Application() {

    val activityHandler = ActivityHandler()
    val fontLoader by lazy { FontLoader(this) }

    init {
        Thread.setDefaultUncaughtExceptionHandler(LawnchairCrashHandler(this, Thread.getDefaultUncaughtExceptionHandler()))
        registerActivityLifecycleCallbacks(activityHandler)
    }

    fun restart() {
        activityHandler.finishAll()
    }

    private class LawnchairCrashHandler(val context: Context, val defaultHandler: Thread.UncaughtExceptionHandler)
        : Thread.UncaughtExceptionHandler {

        val hasPermission get() = ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lawnchair/logs")

        override fun uncaughtException(t: Thread?, e: Throwable?) {
            handleException(t, e)
            defaultHandler.uncaughtException(t, e)
        }

        fun handleException(t: Thread?, e: Throwable?) {
            if (e == null) return
            if (!hasPermission) return
            if (!folder.exists()) folder.mkdirs()

            val file = File(folder, "${getFileName()}.txt")
            if (!file.createNewFile()) return

            val stream = PrintStream(file)
            addReportHeader(stream)
            e.printStackTrace(stream)
            stream.close()
        }

        private fun addReportHeader(stream: PrintStream) {
            stream.println(getFileName())
            stream.println("Lawnchair version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
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
            stream.println("--------- beginning of crash")
        }

        fun getFileName(): String? {
            val dateFormat = SimpleDateFormat.getDateTimeInstance()
            return "Lawnchair crash ${dateFormat.format(Date())}"
        }
    }

    class ActivityHandler : ActivityLifecycleCallbacks {

        val activities = HashSet<Activity>()
        var foregroundActivity: Activity? = null

        fun finishAll() {
            HashSet(activities).forEach { if (it is LawnchairLauncher) it.recreate() else it.finish() }
        }

        override fun onActivityPaused(activity: Activity) {

        }

        override fun onActivityResumed(activity: Activity) {
            foregroundActivity = activity
        }

        override fun onActivityStarted(activity: Activity) {

        }

        override fun onActivityDestroyed(activity: Activity) {
            if (activity == foregroundActivity)
                foregroundActivity = null
            activities.remove(activity)
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle?) {

        }

        override fun onActivityStopped(activity: Activity) {

        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activities.add(activity)
        }
    }
}

val Context.lawnchairApp get() = applicationContext as LawnchairApp
