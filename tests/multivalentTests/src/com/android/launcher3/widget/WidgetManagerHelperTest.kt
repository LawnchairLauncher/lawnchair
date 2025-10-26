/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.util.ActivityContextWrapper
import com.android.launcher3.util.PackageUserKey
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class WidgetManagerHelperTest {

    private val context: Context
        get() = ActivityContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext)

    private val info =
        LauncherAppWidgetProviderInfo().apply {
            provider = InstrumentationRegistry.getInstrumentation().componentName
            providerInfo =
                mock(ActivityInfo::class.java).apply { applicationInfo = context.applicationInfo }
        }

    @Mock private lateinit var appWidgetManager: AppWidgetManager

    private lateinit var underTest: WidgetManagerHelper

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        underTest = WidgetManagerHelper(context, appWidgetManager)
    }

    @Test
    fun getAllProviders_returnsCorrectWidgetProviderInfo() {
        val packageUserKey =
            mock(PackageUserKey::class.java).apply {
                mPackageName = context.packageName
                mUser = Process.myUserHandle()
            }
        val desiredResult = listOf(info)
        whenever(
                appWidgetManager.getInstalledProvidersForPackage(
                    packageUserKey.mPackageName,
                    packageUserKey.mUser
                )
            )
            .thenReturn(desiredResult)
        Truth.assertThat(underTest.getAllProviders(packageUserKey)).isSameInstanceAs(desiredResult)
    }

    @Test
    fun getLauncherAppWidgetInfo_returnsCorrectInfo_ifWidgetExists() {
        val id = 123
        whenever(appWidgetManager.getAppWidgetInfo(id)).thenReturn(info)
        val componentName = InstrumentationRegistry.getInstrumentation().componentName
        Truth.assertThat(underTest.getLauncherAppWidgetInfo(id, componentName))
            .isSameInstanceAs(info)
    }

    @Test
    fun bindAppWidgetIdIfAllowed_correctly_forwardsBindCommandToAppWidgetManager() {
        val id = 124
        val options = Bundle()
        underTest.bindAppWidgetIdIfAllowed(id, info, options)
        verify(appWidgetManager).bindAppWidgetIdIfAllowed(id, info.profile, info.provider, options)
    }

    @Test
    fun findProvider_returnsNull_ifNoProviderExists() {
        val info =
            underTest.getLauncherAppWidgetInfo(
                1,
                InstrumentationRegistry.getInstrumentation().componentName
            )
        Truth.assertThat(info).isNull()
    }

    @Test
    fun isAppWidgetRestored_returnsTrue_ifWidgetIsRestored() {
        val id = 126
        whenever(appWidgetManager.getAppWidgetOptions(id))
            .thenReturn(
                Bundle().apply {
                    putBoolean(WidgetManagerHelper.WIDGET_OPTION_RESTORE_COMPLETED, true)
                }
            )
        Truth.assertThat(underTest.isAppWidgetRestored(id)).isTrue()
    }

    @Test
    fun loadGeneratedPreview_returnsWidgetPreview_fromAppWidgetManager() {
        val widgetCategory = 130
        with(info) {
            underTest.loadGeneratedPreview(this, widgetCategory)
            verify(appWidgetManager).getWidgetPreview(provider, profile, widgetCategory)
        }
    }
}
