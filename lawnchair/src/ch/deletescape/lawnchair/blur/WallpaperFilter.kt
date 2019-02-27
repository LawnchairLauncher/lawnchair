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

package ch.deletescape.lawnchair.blur

import android.graphics.Bitmap
import ch.deletescape.lawnchair.LawnchairPreferences

interface WallpaperFilter {

    fun applyPrefs(prefs: LawnchairPreferences)

    fun apply(wallpaper: Bitmap): ApplyTask

    class ApplyTask {

        val emitter = Emitter()

        private var result: Bitmap? = null
        private var error: Throwable? = null

        private var callback: ((Bitmap?, Throwable?) -> Unit)? = null

        fun setCallback(callback: (Bitmap?, Throwable?) -> Unit): ApplyTask {
            result?.let {
                callback(it, null)
                return this
            }
            error?.let {
                callback(null, it)
                return this
            }
            this.callback = callback
            return this
        }

        inner class Emitter {

            fun onSuccess(result: Bitmap) {
                callback?.let {
                    it(result, null)
                    return
                }
                this@ApplyTask.result = result
            }

            fun onError(error: Throwable) {
                callback?.let {
                    it(null, error)
                    return
                }
                this@ApplyTask.error = error
            }
        }

        companion object {

            inline fun create(source: (Emitter) -> Unit): ApplyTask {
                return ApplyTask().also { source(it.emitter) }
            }
        }
    }
}
