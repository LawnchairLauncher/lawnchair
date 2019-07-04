/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.globalsearch.providers.web

import android.content.Context
import com.android.launcher3.R

class BaiduWebSearchProvider(context: Context) : WebSearchProvider(context) {
    override val searchUrl = "https://m.baidu.com/s?word=%s"
    override val suggestionsUrl = "https://m.baidu.com/su?action=opensearch&ie=UTF-8&wd=%s"
    override val name = context.getString(R.string.web_search_baidu)

    override fun getIcon() = context.getDrawable(R.drawable.ic_baidu)!!
}