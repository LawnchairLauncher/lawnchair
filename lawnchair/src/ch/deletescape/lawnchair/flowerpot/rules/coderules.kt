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

package ch.deletescape.lawnchair.flowerpot.rules

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import ch.deletescape.lawnchair.hasFlag
import com.android.launcher3.Utilities

sealed class CodeRule(vararg val args: String) {
    abstract fun matches(info: ApplicationInfo): Boolean

    class IsGame(vararg args: String) : CodeRule(*args) {
        override fun matches(info: ApplicationInfo) = info.flags hasFlag ApplicationInfo.FLAG_IS_GAME
    }

    class Category(vararg args: String) : CodeRule(*args) {
        val category: Int

        init {
            if (args.size != 1) {
                throw IllegalArgumentException("Expected exactly one argument")
            }
            category = when (args[0]) {
                "undefined" -> ApplicationInfo.CATEGORY_UNDEFINED
                "game" -> ApplicationInfo.CATEGORY_GAME
                "audio" -> ApplicationInfo.CATEGORY_AUDIO
                "video" -> ApplicationInfo.CATEGORY_VIDEO
                "image" -> ApplicationInfo.CATEGORY_IMAGE
                "social" -> ApplicationInfo.CATEGORY_SOCIAL
                "news" -> ApplicationInfo.CATEGORY_NEWS
                "maps" -> ApplicationInfo.CATEGORY_MAPS
                "productivity" -> ApplicationInfo.CATEGORY_PRODUCTIVITY
                else -> throw IllegalArgumentException("Expected a known category, got '${args[0]}' instead")
            }
        }

        override fun matches(info: ApplicationInfo) = Utilities.ATLEAST_OREO && info.category == category
    }

    companion object {
        private val cache = mutableMapOf<Pair<String, Array<out String>>, CodeRule>()
        fun get(name: String, vararg args: String) = cache.getOrPut(Pair(name, args)) {
            when (name) {
                "isGame" -> IsGame(*args)
                "category" -> Category(*args)
                else -> throw IllegalArgumentException("Unknown Code Rule '$name'")
            }
        }
    }
}