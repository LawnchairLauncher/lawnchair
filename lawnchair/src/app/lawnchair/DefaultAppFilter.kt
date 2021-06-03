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
import androidx.annotation.Keep
import com.android.launcher3.AppFilter
import com.android.launcher3.BuildConfig

@Keep
open class DefaultAppFilter : AppFilter() {
    private val defaultHideList = setOf(
        // Voice search
        ComponentName.unflattenFromString("com.google.android.googlequicksearchbox/.VoiceSearchActivity"),
        // Wallpapers
        ComponentName.unflattenFromString("com.google.android.apps.wallpaper/.picker.CategoryPickerActivity"),
        // GNL
        ComponentName.unflattenFromString("com.google.android.launcher/.StubApp"),
        // Actions Services
        ComponentName.unflattenFromString("com.google.android.as/com.google.android.apps.miphone.aiai.allapps.main.MainDummyActivity"),
        // Lawnchair
        ComponentName(BuildConfig.APPLICATION_ID, LawnchairLauncher::class.java.name),
    )

    override fun shouldShowApp(app: ComponentName) = !defaultHideList.contains(app)
}
