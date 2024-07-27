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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Handler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.android.launcher3.AppFilter
import com.android.launcher3.LauncherAppState
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import java.util.Comparator.comparing
import java.util.Locale

@Composable
fun appsState(
    filter: AppFilter = AppFilter(LocalContext.current),
    comparator: Comparator<App> = appComparator,
): State<List<App>> {
    val context = LocalContext.current
    val appsState = remember { mutableStateOf(emptyList<App>()) }
    DisposableEffect(Unit) {
        Utilities.postAsyncCallback(Handler(MODEL_EXECUTOR.looper)) {
            val launcherApps = context.getSystemService(LauncherApps::class.java)

            appsState.value = UserCache.INSTANCE.get(context).userProfiles.asSequence()
                .flatMap { launcherApps.getActivityList(null, it) }
                .filter { filter.shouldShowApp(it.componentName) }
                .map { App(context, it) }
                .sortedWith(comparator)
                .toList()
        }
        onDispose { }
    }
    return appsState
}

fun categorizeApps(context: Context): Map<String, List<AppInfo>> {
    val categories = mutableMapOf<String, List<AppInfo>>()
    val launcherApps = context.getSystemService(LauncherApps::class.java)

    val activityList = UserCache.INSTANCE.get(context).userProfiles.asSequence().flatMap {
        launcherApps.getActivityList(null, it)
    }

    for (activityInfo in activityList) {
        val packageName = activityInfo.componentName.packageName

        val applicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
        val categoryTitle = ApplicationInfo.getCategoryTitle(context, applicationInfo.category) ?: "Other"

        val appInfo = AppInfo(activityInfo, activityInfo.user, false)
        LauncherAppState.getInstance(context).iconCache.getTitleAndIcon(appInfo, false)
        val existingList = categories[categoryTitle.toString()]?.toMutableList() ?: mutableListOf()
        existingList.add(appInfo)
        categories[categoryTitle.toString()] = existingList
    }

    return categories
}

class App(context: Context, private val info: LauncherActivityInfo) {

    val label get() = info.label.toString()
    val icon: Bitmap
    val key = ComponentKey(info.componentName, info.user)

    init {
        val appInfo = AppInfo(context, info, info.user)
        LauncherAppState.getInstance(context).iconCache.getTitleAndIcon(appInfo, false)
        icon = appInfo.bitmap.icon
    }
}

val appComparator: Comparator<App> = comparing { it.label.lowercase(Locale.getDefault()) }
