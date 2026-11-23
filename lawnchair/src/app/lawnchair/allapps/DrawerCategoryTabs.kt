/*
 * Copyright 2026 Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.allapps

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.navigation.AppDrawerFolder
import app.lawnchair.util.overrideAllAppsTextColor
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.util.Themes
import com.android.launcher3.views.ActivityContext

/**
 * Views of the app drawer's "Apps" / "Categories" tabs (see [LawnchairAlphabeticalAppsList]).
 */
object DrawerCategoryTabs {

    private const val TAG_APPS = "drawer_tab_apps"
    private const val TAG_CATEGORIES = "drawer_tab_categories"

    /** Height of a large folder of the Categories tab: two rows of icons. */
    @JvmStatic
    fun getLargeFolderHeight(context: Context): Int {
        val dp = (context as ActivityContext).deviceProfile
        return dp.allAppsProfile.cellHeightPx * 2
    }

    /** The segmented "Apps | Categories" control shown at the top of the drawer. */
    @JvmStatic
    fun createTabsView(context: Context): View {
        val res = context.resources
        val padding = dp(context, 4f)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 24f).toFloat()
                setColor(ColorUtils.setAlphaComponent(textColor(context), 0x22))
            }
        }
        listOf(TAG_APPS to R.string.drawer_tab_apps, TAG_CATEGORIES to R.string.drawer_tab_categories)
            .forEach { (tag, label) ->
                val tab = TextView(context).apply {
                    this.tag = tag
                    text = res.getString(label)
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    isAllCaps = false
                    setPadding(dp(context, 16f), 0, dp(context, 16f), 0)
                }
                container.addView(
                    tab,
                    LinearLayout.LayoutParams(0, dp(context, 36f), 1f),
                )
            }
        val horizontalMargin = dp(context, 16f)
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(horizontalMargin, dp(context, 4f), horizontalMargin, dp(context, 12f))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        wrapper.addView(
            container,
            LinearLayout.LayoutParams(dp(context, 280f), ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        return wrapper
    }

    /** Shows the selected tab and switches tabs on click. */
    @JvmStatic
    fun bindTabsView(view: View, apps: AlphabeticalAppsList<*>) {
        val list = apps as? LawnchairAlphabeticalAppsList<*> ?: return
        val context = view.context
        val selected = list.isCategoriesTabSelected
        val accent = Themes.getColorAccent(context)
        val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.5) Color.BLACK else Color.WHITE
        listOf(TAG_APPS to false, TAG_CATEGORIES to true).forEach { (tag, isCategories) ->
            val tab = view.findViewWithTag<TextView>(tag) ?: return@forEach
            val isSelected = selected == isCategories
            tab.isSelected = isSelected
            if (isSelected) {
                tab.background = GradientDrawable().apply {
                    cornerRadius = dp(context, 20f).toFloat()
                    setColor(accent)
                }
                tab.setTextColor(onAccent)
            } else {
                tab.background = null
                tab.setTextColor(textColor(context))
                overrideAllAppsTextColor(tab)
            }
            tab.setOnClickListener {
                if (list.isCategoriesTabSelected != isCategories) {
                    list.setCategoriesTabSelected(isCategories)
                }
            }
        }
    }

    /** The "New folder" button at the end of the Categories tab. */
    @JvmStatic
    fun createNewFolderView(context: Context): View {
        val wrapper = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(context, 12f), 0, dp(context, 24f))
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val button = TextView(context).apply {
            text = "+  " + context.getString(R.string.drawer_new_folder)
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(textColor(context))
            overrideAllAppsTextColor(this)
            setPadding(dp(context, 24f), 0, dp(context, 24f), 0)
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 22f).toFloat()
                setColor(ColorUtils.setAlphaComponent(textColor(context), 0x22))
            }
            setOnClickListener {
                context.startActivity(PreferenceActivity.createIntent(context, AppDrawerFolder))
            }
        }
        wrapper.addView(
            button,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 44f)),
        )
        return wrapper
    }

    private fun textColor(context: Context): Int = Themes.getAttrColor(context, android.R.attr.textColorPrimary)

    private fun dp(context: Context, value: Float): Int = Utilities.dpToPx(value, context)
}
