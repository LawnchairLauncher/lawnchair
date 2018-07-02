package ch.deletescape.lawnchair.views

import android.annotation.TargetApi
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import ch.deletescape.lawnchair.forEachChild
import ch.deletescape.lawnchair.getBooleanAttr
import ch.deletescape.lawnchair.getColorAttr
import ch.deletescape.lawnchair.getGoogleSans
import com.android.launcher3.LauncherAppWidgetHostView
import com.android.launcher3.R

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
    private var firstImage = false
    private var typeface: Typeface? = null
    private val textColor by lazy { context.getColorAttr(R.attr.workspaceTextColor) }
    private val darkText by lazy { context.getBooleanAttr(R.attr.isWorkspaceDarkText) }
    private val divider by lazy { ColorDrawable(textColor) }

    init {
        getGoogleSans(context, ::onTypefaceRetrieved)
    }

    fun onTypefaceRetrieved(typeface: Typeface) {
        this.typeface = typeface
        forceProductSans(this)
    }

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
            firstImage = true
            forceProductSans(this)
        }
    }

    @TargetApi(24)
    private fun forceProductSans(parent: ViewGroup) {
        parent.forEachChild { v ->
            if (v is ViewGroup) {
                forceProductSans(v)
            } else if (v is TextView) {
                v.typeface = typeface
                v.setTextColor(textColor)
                if (darkText) v.setShadowLayer(0f, 0f, 0f, 0)
            } else if (v is ImageView) {
                if (firstImage) {
                    firstImage = false
                    v.setImageDrawable(divider)
                }
            }
        }
    }

    companion object {
        const val googlePackage = "com.google.android.googlequicksearchbox"
        const val smartspaceComponent = "com.google.android.apps.gsa.staticplugins.smartspace.widget.SmartspaceWidgetProvider"
    }
}