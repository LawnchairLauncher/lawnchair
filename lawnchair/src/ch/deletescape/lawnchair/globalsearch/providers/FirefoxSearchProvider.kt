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

package ch.deletescape.lawnchair.globalsearch.providers

import android.content.*
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.support.annotation.Keep
import ch.deletescape.lawnchair.globalsearch.SearchProvider
import com.android.launcher3.R
import com.android.launcher3.util.PackageManagerHelper

@Keep
open class FirefoxSearchProvider(context: Context) : SearchProvider(context) {


    override val name: String = context.getString(R.string.search_provider_firefox)
    override val supportsVoiceSearch = false
    override val supportsAssistant = false
    override val supportsFeed = true

    override val isAvailable: Boolean
        get() = getPackage(context) != null

    override fun startSearch(callback: (intent: Intent) -> Unit) = callback(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setPackage(getPackage(context)))
    override fun startFeed(callback: (intent: Intent) -> Unit) = callback(Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setPackage(getPackage(context)))

    override fun getIcon(): Drawable = context.getDrawable(R.drawable.ic_firefox)!!

    open fun getPackage(context: Context) = listOf(
            "org.mozilla.firefox",
            "org.mozilla.fennec_fdroid",
            "org.mozilla.firefox_beta",
            "org.mozilla.fennec_aurora"
    ).firstOrNull { PackageManagerHelper.isAppEnabled(context.packageManager, it, 0) }
}
