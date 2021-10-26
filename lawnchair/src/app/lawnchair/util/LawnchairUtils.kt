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
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.UserManagerCompat
import androidx.core.view.children
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.R
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Themes
import com.android.systemui.shared.system.QuickStepContract
import org.json.JSONArray
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import kotlin.system.exitProcess

fun <T, A> ensureOnMainThread(creator: (A) -> T): (A) -> T = { it ->
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

fun <T> useApplicationContext(creator: (Context) -> T): (Context) -> T = { it ->
    creator(it.applicationContext)
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
    val mPendingIntent = PendingIntent.getActivity(context, 0, intent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE)
    val mgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    mgr[AlarmManager.RTC, System.currentTimeMillis() + 100] = mPendingIntent

    // Kill the application
    killLauncher()
}

fun killLauncher() {
    exitProcess(0)
}

fun getPrefsIfUnlocked(context: Context): PreferenceManager? {
    return if (UserManagerCompat.isUserUnlocked(context)) {
        PreferenceManager.getInstance(context)
    } else {
        null
    }
}

fun getWindowCornerRadius(context: Context): Float {
    val prefs = getPrefsIfUnlocked(context)
    if (prefs != null && prefs.overrideWindowCornerRadius.get()) {
        return prefs.windowCornerRadius.get().toFloat()
    }
    return QuickStepContract.getWindowCornerRadius(context.resources)
}

fun supportsRoundedCornersOnWindows(context: Context): Boolean {
    if (getPrefsIfUnlocked(context)?.overrideWindowCornerRadius?.get() == true) {
        return true
    }
    return QuickStepContract.supportsRoundedCornersOnWindows(context.resources)
}

fun overrideAllAppsTextColor(textView: TextView) {
    val context = textView.context
    val opacity = PreferenceManager.getInstance(context).drawerOpacity.get()
    if (opacity <= 0.3f) {
        textView.setTextColor(Themes.getAttrColor(context, R.attr.allAppsAlternateTextColor))
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> JSONArray.toArrayList(): ArrayList<T> {
    val arrayList = ArrayList<T>()
    for (i in (0 until length())) {
        arrayList.add(get(i) as T)
    }
    return arrayList
}

val ViewGroup.recursiveChildren: Sequence<View> get() = children.flatMap {
    if (it is ViewGroup) {
        it.recursiveChildren
    } else sequenceOf(it)
}
