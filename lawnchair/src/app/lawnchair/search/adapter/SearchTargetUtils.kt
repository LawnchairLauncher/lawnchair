package app.lawnchair.search.adapter

import android.content.pm.ShortcutInfo
import android.os.Bundle
import android.os.Process
import androidx.core.os.bundleOf
import com.android.app.search.LayoutType
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.util.ComponentKey
import java.security.MessageDigest
import okio.ByteString

// We're generate hash key as alt in id, so we can avoid
// the wrong action when click,
// TODO remove when we properly manage creating search target
private fun generateHashKey(input: String): String =
    ByteString.of(*MessageDigest.getInstance("SHA-256").digest(input.toByteArray())).hex()

fun createSearchTarget(appInfo: AppInfo, asRow: Boolean = false): SearchTargetCompat {
    val componentName = appInfo.componentName
    val user = appInfo.user
    return SearchTargetCompat.Builder(
        SearchTargetCompat.RESULT_TYPE_APPLICATION,
        if (asRow) LayoutType.SMALL_ICON_HORIZONTAL_TEXT else LayoutType.ICON_SINGLE_VERTICAL_TEXT,
        generateHashKey(ComponentKey(componentName, user).toString()),
    )
        .setPackageName(componentName.packageName)
        .setUserHandle(user)
        .setExtras(bundleOf("class" to componentName.className))
        .build()
}

fun createSearchTarget(shortcutInfo: ShortcutInfo): SearchTargetCompat {
    return SearchTargetCompat.Builder(
        SearchTargetCompat.RESULT_TYPE_SHORTCUT,
        LayoutType.SMALL_ICON_HORIZONTAL_TEXT,
        "shortcut_" + generateHashKey("${shortcutInfo.`package`}|${shortcutInfo.userHandle}|${shortcutInfo.id}"),
    )
        .setShortcutInfo(shortcutInfo)
        .setUserHandle(shortcutInfo.userHandle)
        .setExtras(Bundle())
        .build()
}

fun createSearchTarget(
    id: String,
    action: SearchActionCompat,
    pkg: String,
    extras: Bundle = Bundle(),
): SearchTargetCompat {
    return SearchTargetCompat.Builder(
        SearchTargetCompat.RESULT_TYPE_REDIRECTION,
        LayoutType.ICON_HORIZONTAL_TEXT,
        generateHashKey(id),
    )
        .setPackageName(pkg)
        .setUserHandle(Process.myUserHandle())
        .setSearchAction(action)
        .setExtras(extras)
        .build()
}

fun createSearchTarget(
    id: String,
    action: SearchActionCompat,
    layoutType: String,
    targetCompat: Int,
    pkg: String,
    extras: Bundle = Bundle(),
): SearchTargetCompat {
    return SearchTargetCompat.Builder(
        targetCompat,
        layoutType,
        generateHashKey(id),
    )
        .setPackageName(pkg)
        .setUserHandle(Process.myUserHandle())
        .setSearchAction(action)
        .setExtras(extras)
        .build()
}

fun createDividerTarget(): SearchTargetCompat {
    return SearchTargetCompat.Builder(
        SearchTargetCompat.RESULT_TYPE_SHORTCUT,
        LayoutType.DIVIDER,
        "divider",
    )
        .setPackageName("")
        .setUserHandle(Process.myUserHandle())
        .build()
}

const val START_PAGE = "startpage"
const val MARKET_STORE = "marketstore"
const val WEB_SUGGESTION = "suggestion"
const val HEADER = "header"
const val CONTACT = "contact"
const val FILES = "files"
const val SPACE = "space"
const val SPACE_MINI = "space_mini"
const val LOADING = "loading"
const val ERROR = "error"
const val SETTINGS = "setting"
const val SHORTCUT = "shortcut"
const val HISTORY = "recent_keyword"
const val HEADER_JUSTIFY = "header_justify"
