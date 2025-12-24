/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.qsb

import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.LauncherComponentProvider.appComponent
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.RunnableList
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.OptionsPopupView
import com.android.launcher3.views.OptionsPopupView.OptionItem
import com.android.launcher3.widget.LauncherAppWidgetHostView

/**
 * Renders the On-device search engine's widget [RemoteViews] based on [AppWidgetProviderInfo] by
 * listening to OSE changes through [OseWidgetManager]
 */
class OseWidgetView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
    LauncherAppWidgetHostView(context) {

    private val oseWidgetManager = context.appComponent.oseWidgetManager
    @VisibleForTesting val closeActions = RunnableList()
    private val activityContext: ActivityContext = ActivityContext.lookupContext(context)

    init {
        activityContext.appWidgetHolder?.onViewCreationCallback?.accept(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attachedToWindow()
    }

    @VisibleForTesting
    fun attachedToWindow() {
        closeActions.executeAllAndClear()
        // We use INVALID_APPWIDGET_ID because appWidgetId is not tracked in OseWidgetView. Instead
        // it is managed by OseWidgetManager and QsbAppWidgetHost.
        Log.i(
            TAG,
            "providerInfo= " +
                oseWidgetManager.providerInfo.value +
                " view = " +
                oseWidgetManager.views.value,
        )
        closeActions.add(
            oseWidgetManager.providerInfo.forEach(MAIN_EXECUTOR) {
                setAppWidget(INVALID_APPWIDGET_ID, it)
                tag = getTagInfo(it)
            }::close
        )
        closeActions.add(
            oseWidgetManager.views.forEach(MAIN_EXECUTOR, { updateAppWidget(it) })::close
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        detachedFromWindow()
    }

    @VisibleForTesting
    fun detachedFromWindow() {
        closeActions.executeAllAndClear()
    }

    override fun shouldDelayChildPressedState(): Boolean {
        // Delay the ripple effect on the widget view when swiping up from home screen
        // to go to all apps.
        return true
    }

    override fun getErrorView(): View =
        View.inflate(context, R.layout.ose_default_layout, null).apply {
            setOnClickListener {
                val oseManager = context.appComponent.getOseManager()
                val oseInfo = oseManager.oseInfo.value
                val osePkg: String? =
                    when {
                        oseInfo.isOseConfigured -> oseInfo.pkg
                        else -> null
                    }
                osePkg?.run {
                    val searchIntent =
                        Intent(Intent.ACTION_SEARCH)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            )
                            .setPackage(this)
                    activityContext.startActivitySafely(it, searchIntent, null)
                } ?: openDefaultBrowser(it)
            }
        }

    fun openDefaultBrowser(view: View) {
        val browserIntent =
            Intent(Intent.ACTION_VIEW)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                )
        // Set the data to a blank page uri
        browserIntent.setData("about:blank".toUri())
        activityContext.startActivitySafely(view, browserIntent, /* item= */ null)
    }

    override fun onLongClick(view: View?): Boolean {
        val oseWidgetOptionsProvider =
            activityContext.activityComponent.getOseWidgetOptionsProvider()
        val optionItems = oseWidgetOptionsProvider.getOptionItems()
        if (optionItems.isEmpty()) return false

        val bounds =
            RectF(Utilities.getViewBounds(this)).apply {
                left = centerX()
                right = centerX()
            }
        showOptionsPopup(bounds, optionItems)
        return true
    }

    @VisibleForTesting
    fun showOptionsPopup(bounds: RectF, optionItems: List<OptionItem>) {
        OptionsPopupView.showNoReturn(activityContext, bounds, optionItems, true)
    }

    private class QsbItemInfo : ItemInfo() {

        override fun getStableId() = STABLE_ID
    }

    companion object {
        private const val TAG = "OseWidgetView"

        private val STABLE_ID = Object()

        private fun getTagInfo(provider: AppWidgetProviderInfo?): ItemInfo {
            val info =
                provider?.let { LauncherAppWidgetInfo(INVALID_APPWIDGET_ID, it.provider) }
                    ?: QsbItemInfo()
            info.id = R.id.search_container_hotseat
            info.container = Favorites.CONTAINER_HOTSEAT
            return info
        }
    }
}
