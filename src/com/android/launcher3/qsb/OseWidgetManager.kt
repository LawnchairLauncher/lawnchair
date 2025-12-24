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

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX
import android.appwidget.AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Process.myUserHandle
import android.util.Log
import android.widget.RemoteViews
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import com.android.launcher3.BaseActivity
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.InvariantDeviceProfile.OnIDPChangeListener
import com.android.launcher3.LauncherConstants.ActivityCodes.REQUEST_RECONFIGURE_APPWIDGET
import com.android.launcher3.R
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.qsb.OSEManager.Companion.OSE_LOOPER
import com.android.launcher3.qsb.OSEManager.OSEInfo
import com.android.launcher3.qsb.QsbAppWidgetHost.Callbacks
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.MutableListenableRef
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.widget.WidgetManagerHelper
import com.android.launcher3.widget.util.WidgetSizeHandler
import javax.inject.Inject

/**
 * Manager for default search widget
 *
 * Listens to OSEManager for any OSE changes and provides the updated widget configurations
 */
@LauncherAppSingleton
class OseWidgetManager
@Inject
constructor(
    @ApplicationContext private val context: Context,
    oseManager: OSEManager,
    private val widgetHost: QsbAppWidgetHost,
    private val sizeHandler: WidgetSizeHandler,
    private val idp: InvariantDeviceProfile,
    tracker: DaggerSingletonTracker,
) {

    private val mutableProviderInfo = MutableListenableRef<AppWidgetProviderInfo?>(null)
    val providerInfo = mutableProviderInfo.asListenable()

    private val mutableViews = MutableListenableRef<RemoteViews?>(null)
    val views = mutableViews.asListenable()

    private val executor = OSE_LOOPER

    init {
        widgetHost.setCallbacks(
            object : Callbacks {

                override fun onProviderChanged(appWidget: AppWidgetProviderInfo?) =
                    mutableProviderInfo.dispatchValue(appWidget)

                override fun onViewsChanged(views: RemoteViews?) = mutableViews.dispatchValue(views)
            }
        )
        widgetHost.startListening()

        tracker.addCloseable(oseManager.oseInfo.forEach(executor, this::handleOseInfoUpdate))

        val idpListener = OnIDPChangeListener { updateWidgetSizeAsync() }
        idp.addOnChangeListener(idpListener)
        tracker.addCloseable {
            idp.removeOnChangeListener(idpListener)
            widgetHost.stopListening()
        }
    }

    private fun handleOseInfoUpdate(info: OSEInfo) {
        // If the package is null, leave it to the current value as the OSEManager
        // may not have initialized yet
        val providerPkg = info.pkg ?: return
        val searchWidget = findSearchWidgetForPackage(context, providerPkg)

        val currentWidgetId = widgetHost.getBoundWidgetId()
        val currentInfo =
            if (currentWidgetId != INVALID_APPWIDGET_ID)
                AppWidgetManager.getInstance(context).getAppWidgetInfo(currentWidgetId)
            else null

        // Everything is in order
        if (currentInfo?.provider == searchWidget?.provider) {
            widgetHost.setActiveWidget(currentWidgetId, currentInfo)
            updateWidgetSizeAsync()
            return
        }

        // If there is no possible search widget, switch to a null view
        if (searchWidget == null) {
            widgetHost.setActiveWidget(INVALID_APPWIDGET_ID, null)
            dispatchNullValues()
            return
        }

        // Try to bind a new search widget
        val widgetId = widgetHost.allocateAppWidgetId()
        val bindSuccess =
            AppWidgetManager.getInstance(context)
                .bindAppWidgetIdIfAllowed(widgetId, searchWidget.provider)

        if (bindSuccess) {
            widgetHost.setActiveWidget(widgetId, searchWidget)
            updateWidgetSizeAsync()
        } else {
            widgetHost.deleteAppWidgetId(widgetId)
            widgetHost.setActiveWidget(INVALID_APPWIDGET_ID, null)
            dispatchNullValues()
        }
    }

    private fun updateWidgetSizeAsync() {
        val widgetId = widgetHost.getActiveWidgetId()
        if (widgetId != INVALID_APPWIDGET_ID) {
            sizeHandler.updateSizeRangesAsync(widgetId, idp.numColumns, 1, executor)
        }
    }

    private fun dispatchNullValues() {
        if (mutableProviderInfo.value != null) mutableProviderInfo.dispatchValue(null)
        if (mutableViews.value != null) mutableViews.dispatchValue(null)
    }

    fun startConfigActivity(activity: BaseActivity): Boolean {
        val widgetId = widgetHost.getActiveWidgetId()
        if (widgetId == 0) {
            Log.e(TAG, "Couldn't find a valid widgetId")
            return false
        }
        try {
            widgetHost.startAppWidgetConfigureActivityForResult(
                activity,
                widgetId,
                0,
                REQUEST_RECONFIGURE_APPWIDGET,
                activity
                    .makeDefaultActivityOptions(-1 /* SPLASH_SCREEN_STYLE_UNDEFINED */)
                    .toBundle(),
            )
            return true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.activity_not_found, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security Exception " + e)
        }
        return false
    }

    companion object {
        private const val TAG = "OseWidgetManager"

        @VisibleForTesting
        fun findSearchWidgetForPackage(context: Context, pkg: String): AppWidgetProviderInfo? {
            val allEligibleWidgets =
                WidgetManagerHelper(context)
                    .getAllProviders(PackageUserKey(pkg, myUserHandle()))
                    .filter {
                        it.configure == null ||
                            ((it.widgetFeatures and WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0)
                    }
            return allEligibleWidgets.firstOrNull {
                (it.widgetCategory and WIDGET_CATEGORY_SEARCHBOX) != 0
            } ?: allEligibleWidgets.firstOrNull()
        }
    }
}
