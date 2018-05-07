package ch.deletescape.lawnchair.views

import android.annotation.TargetApi
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.icu.text.DateFormat
import android.view.ViewGroup
import android.widget.TextView
import ch.deletescape.lawnchair.forEachChild
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppWidgetHostView
import com.android.launcher3.Utilities
import com.google.android.apps.nexuslauncher.graphics.IcuDateTextView

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

    private var isSmartspace = false
    private var firstText = false
    private var dateFormat: DateFormat? = null
    private val typeface by lazy { Utilities.getGoogleSans(context) }

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo?) {
        super.setAppWidget(appWidgetId, info)
        isSmartspace = info?.run { provider == ComponentName(googlePackage, smartspaceComponent) } ?: false
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        updateText()
    }

    fun updateText() {
        if (isSmartspace) {
            firstText = true
            forceProductSans(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (isSmartspace) {
            Launcher.getLauncher(context).mPrefCallback.addSmartspaceWidget(this)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        if (isSmartspace) {
            Launcher.getLauncher(context).mPrefCallback.removeSmartspaceWidget(this)
        }
    }

    @TargetApi(24)
    private fun forceProductSans(parent: ViewGroup) {
        parent.forEachChild { v ->
            if (v is ViewGroup) {
                forceProductSans(v)
            } else if (v is TextView) {
                if (firstText) {
                    firstText = false
                    dateFormat = IcuDateTextView.getDateFormat(context, false, null)
                    if (dateFormat != null) {
                        v.text = dateFormat?.format(System.currentTimeMillis())
                    }
                }
                v.typeface = typeface
            }
        }
    }

    companion object {
        const val googlePackage = "com.google.android.googlequicksearchbox"
        const val smartspaceComponent = "com.google.android.apps.gsa.staticplugins.smartspace.widget.SmartspaceWidgetProvider"
    }
}