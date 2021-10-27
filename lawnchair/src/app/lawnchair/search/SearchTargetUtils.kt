package app.lawnchair.search

import android.content.pm.ShortcutInfo
import android.os.Bundle
import android.os.Process
import com.android.app.search.LayoutType
import com.android.launcher3.BuildConfig
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey

fun createSearchTarget(appInfo: AppInfo, asRow: Boolean = false): SearchTargetCompat {
    val componentName = appInfo.componentName
    val user = appInfo.user
    return SearchTargetCompat.Builder(
        SearchTargetCompat.RESULT_TYPE_APPLICATION,
        if (asRow) LayoutType.ICON_HORIZONTAL_TEXT else LayoutType.ICON_SINGLE_VERTICAL_TEXT ,
        ComponentKey(componentName, user).toString()
    )
        .setPackageName(componentName.packageName)
        .setUserHandle(user)
        .setExtras(Bundle().apply {
            putString("class", componentName.className)
        })
        .build()
}

fun createSearchTarget(shortcutInfo: ShortcutInfo): SearchTargetCompat {
    return SearchTargetCompat.Builder(
        SearchTargetCompat.RESULT_TYPE_SHORTCUT,
        LayoutType.SMALL_ICON_HORIZONTAL_TEXT,
        "${shortcutInfo.`package`}|${shortcutInfo.userHandle}|${shortcutInfo.id}"
    )
        .setShortcutInfo(shortcutInfo)
        .setUserHandle(shortcutInfo.userHandle)
        .setExtras(Bundle())
        .build()
}

fun createSearchTarget(id: String, action: SearchActionCompat, extras: Bundle = Bundle()): SearchTargetCompat {
    return SearchTargetCompat.Builder(
        SearchTargetCompat.RESULT_TYPE_SHORTCUT,
        LayoutType.ICON_HORIZONTAL_TEXT,
        id
    )
        .setPackageName(BuildConfig.APPLICATION_ID)
        .setUserHandle(Process.myUserHandle())
        .setSearchAction(action)
        .setExtras(extras)
        .build()
}

fun createDividerTarget(): SearchTargetCompat {
    return SearchTargetCompat.Builder(
        SearchTargetCompat.RESULT_TYPE_SHORTCUT,
        LayoutType.DIVIDER,
        "divider"
    )
        .setPackageName(BuildConfig.APPLICATION_ID)
        .setUserHandle(Process.myUserHandle())
        .build()
}
