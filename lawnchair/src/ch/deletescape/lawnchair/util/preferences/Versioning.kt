package ch.deletescape.lawnchair.util.preferences

import android.content.Context
import android.content.pm.PackageInfo
import java.util.*

fun getFormattedVersionName(context: Context): String {
    val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName

    val withoutMetadata = versionName.split("+")[0]
    val segments = withoutMetadata.split("-")
    val versionSegment = segments[0]
    val stabilitySegment = segments[1]
        .replace(".", " ")
        .split(" ")
        .joinToString(" ") {
            it.capitalize(Locale.ROOT)
        }

    return "$versionSegment $stabilitySegment"
}