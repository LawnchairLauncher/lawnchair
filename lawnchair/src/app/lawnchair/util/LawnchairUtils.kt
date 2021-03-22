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

package app.lawnchair.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper

import com.android.launcher3.util.Executors.MAIN_EXECUTOR

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

import kotlin.system.exitProcess


fun <T, A>ensureOnMainThread(creator: (A) -> T): (A) -> T {
    return { it ->
        if (Looper.myLooper() == Looper.getMainLooper()) {
            creator(it)
        } else {
            try {
                MAIN_EXECUTOR.submit(Callable { creator(it) }).get()
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            } catch (e: ExecutionException) {
                throw RuntimeException(e)
            }

        }
    }
}

fun <T>useApplicationContext(creator: (Context) -> T): (Context) -> T {
    return { it -> creator(it.applicationContext) }
}

fun restartLauncher(context: Context) {
    val pm = context.packageManager
    var intent: Intent? = Intent(Intent.ACTION_MAIN)
    intent!!.addCategory(Intent.CATEGORY_HOME)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    val componentName = intent.resolveActivity(pm)
    if (context.packageName != componentName.packageName) {
        intent = pm.getLaunchIntentForPackage(context.packageName)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    restartLauncher(context, intent)
}

fun restartLauncher(context: Context, intent: Intent?) {
    context.startActivity(intent)

    // Create a pending intent so the application is restarted after System.exit(0) was called.
    // We use an AlarmManager to call this intent in 100ms
    val mPendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT)
    val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    mgr[AlarmManager.RTC, System.currentTimeMillis() + 100] = mPendingIntent

    // Kill the application
    killLauncher()
}

fun killLauncher() {
    exitProcess(0)
}