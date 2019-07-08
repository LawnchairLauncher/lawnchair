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
import android.support.v4.graphics.ColorUtils
import com.android.launcher3.R

class NaverWebSearchProvider(context: Context) : WebSearchProvider(context) {
    override val searchUrl = "https://m.search.naver.com/search.naver?query=%s"
    override val suggestionsUrl = "https://ac.search.naver.com/nx/ac?of=os&ie=utf-8&q=%s"
    override val name = context.getString(R.string.web_search_naver)

    // Replace with green 'N' svg icon without backplate
    override fun getIcon() = context.getDrawable(R.drawable.ic_search)!!.mutate().apply {
        setTint(ColorUtils.setAlphaComponent(COLOR, 0xFF))
    }

    companion object {
        private const val COLOR = 0x00c73c
    }
}