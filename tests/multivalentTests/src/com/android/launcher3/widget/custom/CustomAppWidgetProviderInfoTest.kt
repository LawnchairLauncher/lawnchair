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

package com.android.launcher3.widget.custom

import android.content.ComponentName
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class CustomAppWidgetProviderInfoTest {

    private lateinit var underTest: CustomAppWidgetProviderInfo

    @Before
    fun setup() {
        underTest = CustomAppWidgetProviderInfo()
        underTest.provider = PROVIDER_NAME
    }

    @Test
    fun info_to_string() {
        assertEquals("WidgetProviderInfo($PROVIDER_NAME)", underTest.toString())
    }

    @Test
    fun get_label() {
        underTest.label = "  TEST_LABEL"
        assertEquals(LABEL_NAME, underTest.getLabel())
    }

    companion object {
        private val PROVIDER_NAME =
            ComponentName(getInstrumentation().targetContext.packageName, "TEST_PACKAGE")
        private const val LABEL_NAME = "TEST_LABEL"
    }
}
