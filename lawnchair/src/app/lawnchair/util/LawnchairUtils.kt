/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Looper
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.systemui.shared.system.QuickStepContract
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import kotlin.system.exitProcess

fun <T, A> ensureOnMainThread(creator: (A) -> T): (A) -> T {
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

fun <T> useApplicationContext(creator: (Context) -> T): (Context) -> T {
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

fun getWindowCornerRadius(context: Context): Float {
    val prefs = PreferenceManager.getInstance(context)
    if (prefs.overrideWindowCornerRadius.get()) {
        return prefs.windowCornerRadius.get().toFloat()
    }
    return QuickStepContract.getWindowCornerRadius(context.resources)
}

fun supportsRoundedCornersOnWindows(context: Context): Boolean {
    if (PreferenceManager.getInstance(context).overrideWindowCornerRadius.get()) {
        return true
    }
    return QuickStepContract.supportsRoundedCornersOnWindows(context.resources)
}
