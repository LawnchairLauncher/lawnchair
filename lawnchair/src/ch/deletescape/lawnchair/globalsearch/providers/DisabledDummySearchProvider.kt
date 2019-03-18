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

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import ch.deletescape.lawnchair.globalsearch.SearchProvider
import com.android.launcher3.R


class DisabledDummySearchProvider(context: Context) : SearchProvider(context) {
    override val name: String
        get() = context.getString(R.string.special_greeting)
    override val supportsVoiceSearch = false
    override val supportsAssistant = false
    override val supportsFeed = false

    override val isAvailable: Boolean
        get() = false

    override fun startSearch(callback: (intent: Intent) -> Unit) {
        TODO("not implemented")
    }

    override fun getIcon(): Drawable {
        TODO("not implemented")
    }

}