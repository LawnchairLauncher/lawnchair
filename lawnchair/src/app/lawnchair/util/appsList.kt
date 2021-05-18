package app.lawnchair.util

import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.DefaultAppFilter
import com.android.launcher3.AppFilter
import com.android.launcher3.LauncherAppState
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import java.util.*
import java.util.Comparator.comparing

private val appFilter = AppFilter()

@Composable
fun appsList(filter: AppFilter = appFilter, comparator: Comparator<App> = defaultComparator): State<Optional<List<App>>> {
    val context = LocalContext.current
    val appsState = remember { mutableStateOf(Optional.empty<List<App>>()) }
    DisposableEffect(Unit) {
        Utilities.postAsyncCallback(Handler(MODEL_EXECUTOR.looper)) {
            val appInfos = ArrayList<LauncherActivityInfo>()
            val profiles = UserCache.INSTANCE.get(context).userProfiles
            val launcherApps  = context.getSystemService(LauncherApps::class.java)
            profiles.forEach { appInfos += launcherApps.getActivityList(null, it) }

            val apps = appInfos
                .filter { filter.shouldShowApp(it.componentName, it.user) }
                .map { App(context, it) }
                .sortedWith(comparator)
            appsState.value = Optional.of(apps)
        }
        onDispose {  }
    }
    return appsState
}

class App(context: Context, val info: LauncherActivityInfo) {

    val icon: Bitmap
    val key = ComponentKey(info.componentName, info.user)

    init {
        val appInfo = AppInfo(context, info, info.user)
        LauncherAppState.getInstance(context).iconCache.getTitleAndIcon(appInfo, false)
        icon = appInfo.bitmap.icon
    }
}

private val defaultComparator = comparing<App, String> {
    it.info.label.toString().toLowerCase(Locale.getDefault())
}
