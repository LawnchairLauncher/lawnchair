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
class QwantSearchProvider(context: Context) : FirefoxSearchProvider(context) {


    override val name: String = context.getString(R.string.search_provider_qwant)

    override fun getIcon(): Drawable = context.getDrawable(R.drawable.ic_qwant)!!
    override fun getPackage(context: Context) = listOf(
            "com.qwant.liberty"
        ).firstOrNull { PackageManagerHelper.isAppEnabled(context.packageManager, it, 0) }

    companion object {
        const val PACKAGE = "com.qwant.liberty"
    }
}
