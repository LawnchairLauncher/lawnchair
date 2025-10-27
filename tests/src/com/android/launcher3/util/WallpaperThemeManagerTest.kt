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

package com.android.launcher3.util

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.res.Configuration
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.dx.mockito.inline.extended.ExtendedMockito.any
import com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn
import com.android.dx.mockito.inline.extended.ExtendedMockito.eq
import com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession
import com.android.dx.mockito.inline.extended.ExtendedMockito.times
import com.android.dx.mockito.inline.extended.ExtendedMockito.verify
import com.android.dx.mockito.inline.extended.StaticMockitoSession
import com.android.launcher3.util.WallpaperThemeManager.Companion.setWallpaperDependentTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever

/** Tests for WallpaperThemeManager */
@SmallTest
@RunWith(AndroidJUnit4::class)
class WallpaperThemeManagerTest {

    @get:Rule val context = SandboxApplication()

    @Mock lateinit var activity: Activity
    @Captor lateinit var callbacksCaptor: ArgumentCaptor<ComponentCallbacks>

    private lateinit var mockSession: StaticMockitoSession

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        mockSession = mockitoSession().spyStatic(Themes::class.java).startMocking()

        doReturn(1).`when`<Int> { Themes.getActivityThemeRes(any()) }
        doReturn(context).whenever(activity).applicationContext
    }

    @After
    fun tearDown() {
        mockSession.finishMocking()
    }

    @Test
    fun `correct theme set on activity create`() {
        activity.setWallpaperDependentTheme()
        verify(activity, times(1)).setTheme(eq(1))
    }

    @Test
    fun `ignores update if theme does not change`() {
        activity.setWallpaperDependentTheme()
        verify(activity).registerComponentCallbacks(callbacksCaptor.capture())
        callbacksCaptor.value.onConfigurationChanged(Configuration())
        verify(activity, never()).recreate()
    }

    @Test
    fun `activity recreated if theme changes`() {
        activity.setWallpaperDependentTheme()
        verify(activity).registerComponentCallbacks(callbacksCaptor.capture())

        doReturn(3).`when`<Int> { Themes.getActivityThemeRes(any()) }
        callbacksCaptor.value.onConfigurationChanged(Configuration())
        verify(activity, times(1)).recreate()
    }
}
