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

package com.android.launcher3.widgetpicker.domain.usecase

import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_NOT_KEYGUARD
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.widgetpicker.TestUtils.buildTestWidget
import com.android.launcher3.widgetpicker.shared.model.HostConstraint
import com.android.launcher3.widgetpicker.shared.model.WidgetHostInfo
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilterWidgetsForHostUseCaseTest {
    @Test
    fun noFilter_returnsAll() {
        val underTest = FilterWidgetsForHostUseCase(hostInfo = WidgetHostInfo())
        val inputWidgets = listOf(buildTestWidget("provider1"), buildTestWidget("provider2"))

        val filteredWidgets = underTest(inputWidgets)

        assertThat(filteredWidgets).containsExactlyElementsIn(inputWidgets)
    }

    @Test
    fun userAndCategoryFilter_appliesBoth() {
        val underTest =
            FilterWidgetsForHostUseCase(
                hostInfo =
                    WidgetHostInfo(
                        constraints =
                            listOf(
                                HostConstraint.HostUserConstraint(
                                    userFilters = listOf(UserHandle.of(10))
                                ),
                                HostConstraint.HostCategoryConstraint(
                                    categoryInclusionMask =
                                        WIDGET_CATEGORY_HOME_SCREEN or WIDGET_CATEGORY_KEYGUARD,
                                    categoryExclusionMask = WIDGET_CATEGORY_NOT_KEYGUARD.inv(),
                                ),
                            )
                    )
            )

        val provider1 =
            buildTestWidget(
                providerClassName = "provider1",
                category = WIDGET_CATEGORY_HOME_SCREEN, // match
                userHandle = UserHandle(0), // match
            )
        val provider2 =
            buildTestWidget(
                providerClassName = "provider2",
                category = WIDGET_CATEGORY_KEYGUARD, // match
                userHandle = UserHandle(0), // match
            )
        val provider3 =
            buildTestWidget(
                providerClassName = "provider3",
                category = WIDGET_CATEGORY_HOME_SCREEN, // match
                userHandle = UserHandle(10), // no match
            )
        val provider4 =
            buildTestWidget(
                providerClassName = "provider4",
                category = WIDGET_CATEGORY_HOME_SCREEN or WIDGET_CATEGORY_NOT_KEYGUARD, // no match
                userHandle = UserHandle(0), // match
            )
        val provider5 =
            buildTestWidget(
                providerClassName = "provider5",
                category = WIDGET_CATEGORY_SEARCHBOX, // no match
                userHandle = UserHandle(0), // match
            )
        val provider6 =
            buildTestWidget(
                providerClassName = "provider6",
                category = WIDGET_CATEGORY_KEYGUARD or WIDGET_CATEGORY_HOME_SCREEN, // match
                userHandle = UserHandle(0), // match
            )
        val filteredWidgets =
            underTest(listOf(provider1, provider2, provider3, provider4, provider5, provider6))

        assertThat(filteredWidgets)
            .containsExactlyElementsIn(listOf(provider1, provider2, provider6))
    }

    @Test
    fun onlyOneOfInclusionOrExclusionFilter() {
        val inclusionOnlyFilter =
            FilterWidgetsForHostUseCase(
                hostInfo =
                    WidgetHostInfo(
                        constraints =
                            listOf(
                                HostConstraint.HostCategoryConstraint(
                                    categoryInclusionMask =
                                        WIDGET_CATEGORY_HOME_SCREEN or WIDGET_CATEGORY_KEYGUARD,
                                    categoryExclusionMask = 0,
                                )
                            )
                    )
            )
        val exclusionOnlyFilter =
            FilterWidgetsForHostUseCase(
                hostInfo =
                    WidgetHostInfo(
                        constraints =
                            listOf(
                                HostConstraint.HostCategoryConstraint(
                                    categoryInclusionMask = 0,
                                    categoryExclusionMask = WIDGET_CATEGORY_NOT_KEYGUARD.inv(),
                                )
                            )
                    )
            )

        val provider1 =
            buildTestWidget(providerClassName = "provider1", category = WIDGET_CATEGORY_HOME_SCREEN)
        val provider2 =
            buildTestWidget(
                providerClassName = "provider2",
                category = WIDGET_CATEGORY_HOME_SCREEN or WIDGET_CATEGORY_KEYGUARD,
            )
        val provider3 =
            buildTestWidget(
                providerClassName = "provider3",
                category = WIDGET_CATEGORY_HOME_SCREEN or WIDGET_CATEGORY_NOT_KEYGUARD,
            )
        val provider4 =
            buildTestWidget(providerClassName = "provider4", category = WIDGET_CATEGORY_SEARCHBOX)
        val filteredInclusionOnlyWidgets =
            inclusionOnlyFilter(listOf(provider1, provider2, provider3, provider4))
        val filteredExclusionOnlyWidgets =
            exclusionOnlyFilter(listOf(provider1, provider2, provider3, provider4))

        assertThat(filteredInclusionOnlyWidgets)
            .containsExactlyElementsIn(listOf(provider1, provider2, provider3))
        assertThat(filteredExclusionOnlyWidgets)
            .containsExactlyElementsIn(listOf(provider1, provider2, provider4))
    }
}
