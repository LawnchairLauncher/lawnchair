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

package app.lawnchair.preferences

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
            it.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

    return "$versionSegment $stabilitySegment"
}

fun getMajorVersion(context: Context): String {
    val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val versionName = packageInfo.versionName
    return versionName.split(".")[0]
}