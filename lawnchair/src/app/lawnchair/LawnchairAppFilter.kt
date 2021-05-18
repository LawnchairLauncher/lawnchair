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

package app.lawnchair

import android.content.ComponentName
import android.content.Context
import android.os.UserHandle
import androidx.annotation.Keep
import app.lawnchair.util.preferences.PreferenceManager
import com.android.launcher3.util.ComponentKey

@Keep
class LawnchairAppFilter(context: Context) : DefaultAppFilter() {
    private val prefs = PreferenceManager.getInstance(context)
    private val customHideList get() = prefs.hiddenAppSet.get()

    override fun shouldShowApp(app: ComponentName, user: UserHandle?): Boolean {
        if (!super.shouldShowApp(app, user)) {
            return false
        }
        val key = ComponentKey(app, user)
        if (customHideList.contains(key.toString())) {
            return false
        }
        return true
    }
}
