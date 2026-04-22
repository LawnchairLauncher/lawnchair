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

package com.android.launcher3.widget.picker

import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_NOT_KEYGUARD
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetCategoryFilterTest {

    @Test
    fun filterValueZero_everythingMatches() {
        val noFilter = WidgetCategoryFilter(categoryMask = 0)

        noFilter.assertMatches(WIDGET_CATEGORY_HOME_SCREEN)
        noFilter.assertMatches(WIDGET_CATEGORY_KEYGUARD)
        noFilter.assertMatches(WIDGET_CATEGORY_NOT_KEYGUARD)
        noFilter.assertMatches(WIDGET_CATEGORY_SEARCHBOX)
        noFilter.assertMatches(WIDGET_CATEGORY_HOME_SCREEN or WIDGET_CATEGORY_KEYGUARD)
        noFilter.assertMatches(WIDGET_CATEGORY_HOME_SCREEN or WIDGET_CATEGORY_NOT_KEYGUARD)
        noFilter.assertMatches(
            WIDGET_CATEGORY_HOME_SCREEN or WIDGET_CATEGORY_SEARCHBOX or WIDGET_CATEGORY_NOT_KEYGUARD
        )
        noFilter.assertMatches(
            WIDGET_CATEGORY_HOME_SCREEN or WIDGET_CATEGORY_KEYGUARD or WIDGET_CATEGORY_NOT_KEYGUARD
        )
        noFilter.assertMatches(WIDGET_CATEGORY_SEARCHBOX or WIDGET_CATEGORY_KEYGUARD)
    }

    @Test
    fun includeHomeScreen_matchesOnlyIfHomeScreenExists() {
        val filter = WidgetCategoryFilter(WIDGET_CATEGORY_HOME_SCREEN)

        filter.assertMatches(WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertMatches(WIDGET_CATEGORY_KEYGUARD or WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertMatches(WIDGET_CATEGORY_SEARCHBOX or WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertMatches(WIDGET_CATEGORY_NOT_KEYGUARD or WIDGET_CATEGORY_HOME_SCREEN)

        filter.assertDoesNotMatch(WIDGET_CATEGORY_KEYGUARD)
        filter.assertDoesNotMatch(WIDGET_CATEGORY_NOT_KEYGUARD)
        filter.assertDoesNotMatch(WIDGET_CATEGORY_SEARCHBOX)
        filter.assertDoesNotMatch(WIDGET_CATEGORY_KEYGUARD or WIDGET_CATEGORY_SEARCHBOX)
    }

    @Test
    fun includeHomeScreenOrKeyguard_matchesIfEitherHomeScreenOrKeyguardExists() {
        val filter = WidgetCategoryFilter(WIDGET_CATEGORY_HOME_SCREEN or WIDGET_CATEGORY_KEYGUARD)

        filter.assertMatches(WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertMatches(WIDGET_CATEGORY_KEYGUARD)
        filter.assertMatches(WIDGET_CATEGORY_KEYGUARD or WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertMatches(WIDGET_CATEGORY_SEARCHBOX or WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertMatches(WIDGET_CATEGORY_SEARCHBOX or WIDGET_CATEGORY_KEYGUARD)
        filter.assertMatches(WIDGET_CATEGORY_NOT_KEYGUARD or WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertMatches(WIDGET_CATEGORY_NOT_KEYGUARD or WIDGET_CATEGORY_KEYGUARD)

        filter.assertDoesNotMatch(WIDGET_CATEGORY_NOT_KEYGUARD)
        filter.assertDoesNotMatch(WIDGET_CATEGORY_SEARCHBOX)
        filter.assertDoesNotMatch(WIDGET_CATEGORY_SEARCHBOX or WIDGET_CATEGORY_NOT_KEYGUARD)
    }

    @Test
    fun excludeNotKeyguard_doesNotMatchIfNotKeyguardExists() {
        val filter = WidgetCategoryFilter(WIDGET_CATEGORY_NOT_KEYGUARD.inv())

        filter.assertMatches(WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertMatches(WIDGET_CATEGORY_KEYGUARD)
        filter.assertMatches(WIDGET_CATEGORY_SEARCHBOX)
        filter.assertMatches(WIDGET_CATEGORY_KEYGUARD or WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertMatches(WIDGET_CATEGORY_SEARCHBOX or WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertMatches(WIDGET_CATEGORY_SEARCHBOX or WIDGET_CATEGORY_KEYGUARD)

        filter.assertDoesNotMatch(WIDGET_CATEGORY_NOT_KEYGUARD)
        filter.assertDoesNotMatch(WIDGET_CATEGORY_NOT_KEYGUARD or WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertDoesNotMatch(WIDGET_CATEGORY_NOT_KEYGUARD or WIDGET_CATEGORY_KEYGUARD)
        filter.assertDoesNotMatch(WIDGET_CATEGORY_SEARCHBOX or WIDGET_CATEGORY_NOT_KEYGUARD)
        filter.assertDoesNotMatch(
            WIDGET_CATEGORY_NOT_KEYGUARD or WIDGET_CATEGORY_KEYGUARD or WIDGET_CATEGORY_HOME_SCREEN
        )
    }

    @Test
    fun multipleExclusions_doesNotMatchIfExcludedCategoriesExist() {
        val filter =
            WidgetCategoryFilter(
                WIDGET_CATEGORY_HOME_SCREEN.inv() and WIDGET_CATEGORY_NOT_KEYGUARD.inv()
            )

        filter.assertMatches(WIDGET_CATEGORY_SEARCHBOX)
        filter.assertMatches(WIDGET_CATEGORY_KEYGUARD)
        filter.assertMatches(WIDGET_CATEGORY_SEARCHBOX or WIDGET_CATEGORY_KEYGUARD)

        filter.assertDoesNotMatch(WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertDoesNotMatch(WIDGET_CATEGORY_KEYGUARD or WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertDoesNotMatch(WIDGET_CATEGORY_SEARCHBOX or WIDGET_CATEGORY_HOME_SCREEN)

        filter.assertDoesNotMatch(WIDGET_CATEGORY_NOT_KEYGUARD)
        filter.assertDoesNotMatch(WIDGET_CATEGORY_NOT_KEYGUARD or WIDGET_CATEGORY_HOME_SCREEN)
        filter.assertDoesNotMatch(WIDGET_CATEGORY_NOT_KEYGUARD or WIDGET_CATEGORY_KEYGUARD)
        filter.assertDoesNotMatch(WIDGET_CATEGORY_SEARCHBOX or WIDGET_CATEGORY_NOT_KEYGUARD)
        filter.assertDoesNotMatch(
            WIDGET_CATEGORY_NOT_KEYGUARD or WIDGET_CATEGORY_KEYGUARD or WIDGET_CATEGORY_HOME_SCREEN
        )
    }

    private fun WidgetCategoryFilter.assertMatches(category: Int) {
        assertThat(matches(category)).isTrue()
    }

    private fun WidgetCategoryFilter.assertDoesNotMatch(category: Int) {
        assertThat(matches(category)).isFalse()
    }
}
