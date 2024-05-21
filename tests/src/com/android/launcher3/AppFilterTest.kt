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

package com.android.launcher3

import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class AppFilterTest {

    @Mock private lateinit var mockContext: Context

    @Mock // Mock the Resources object as well
    private lateinit var mockResources: Resources

    private lateinit var appFilter: AppFilter

    @Before
    fun setUp() {
        `when`(mockContext.resources).thenReturn(mockResources) // Link the context and resources
        `when`(mockResources.getStringArray(R.array.filtered_components))
            .thenReturn(arrayOf("com.example.app1/Activity1"))
        appFilter = AppFilter(mockContext)
    }

    @Test
    fun shouldShowApp_notFiltered_returnsTrue() {
        val appToShow = ComponentName("com.example.app2", "Activity2")
        assertThat(appFilter.shouldShowApp(appToShow)).isTrue()
    }

    @Test
    fun shouldShowApp_filtered_returnsFalse() {
        val appToHide = ComponentName("com.example.app1", "Activity1")
        assertThat(appFilter.shouldShowApp(appToHide)).isFalse()
    }
}
