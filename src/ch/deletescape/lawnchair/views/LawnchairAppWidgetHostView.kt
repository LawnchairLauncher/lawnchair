package ch.deletescape.lawnchair.views

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.view.ViewGroup
import android.widget.TextView
import ch.deletescape.lawnchair.forEachChild
import com.android.launcher3.LauncherAppWidgetHostView
import com.android.launcher3.Utilities

/*
 * Copyright (C) 2018 paphonb@xda
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

open class LawnchairAppWidgetHostView(context: Context) : LauncherAppWidgetHostView(context) {

    private var isAtAGlance = false
    private val typeface by lazy { Utilities.getGoogleSans(context) }

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        isAtAGlance = info?.run { provider == ComponentName(googlePackage, atAGlanceComponent) } ?: false
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (isAtAGlance) {
            forceProductSans(this)
        }
    }

    private fun forceProductSans(parent: ViewGroup) {
        parent.forEachChild { v ->
            if (v is ViewGroup) {
                forceProductSans(v)
            } else if (v is TextView) {
                v.typeface = typeface
            }
        }
    }

    companion object {
        const val googlePackage = "com.google.android.googlequicksearchbox"
        const val atAGlanceComponent = "com.google.android.apps.gsa.staticplugins.smartspace.widget.SmartspaceWidgetProvider"
    }
}