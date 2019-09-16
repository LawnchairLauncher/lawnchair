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

package ch.deletescape.lawnchair.util

import android.content.res.XmlResourceParser

class ApkAssets(apkPath: String) {
    private val instance = loadFromPath(apkPath, false, true)

    private fun loadFromPath(path: String, system: Boolean, forceSharedLibrary: Boolean): Any {
        return loadFromPath.invoke(null, path, system, forceSharedLibrary)
    }

    fun openXml(filename: String) = getXml.invoke(instance, filename) as XmlResourceParser

    companion object {
        private val clazz by lazy { Class.forName("android.content.res.ApkAssets") }
        private val loadFromPath by lazy { clazz.getDeclaredMethod("loadFromPath", String::class.java, Boolean::class.java, Boolean::class.java) }
        private val getXml by lazy { clazz.getDeclaredMethod("openXml", String::class.java) }
    }
}