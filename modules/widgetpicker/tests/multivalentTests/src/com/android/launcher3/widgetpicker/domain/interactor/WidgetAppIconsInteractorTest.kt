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

package com.android.launcher3.widgetpicker.domain.interactor

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.widgetpicker.TestUtils.PERSONAL_TEST_APPS
import com.android.launcher3.widgetpicker.TestUtils.WORK_TEST_APPS
import com.android.launcher3.widgetpicker.repository.FakeWidgetAppIconsRepository
import com.android.launcher3.widgetpicker.repository.FakeWidgetsRepository
import com.android.launcher3.widgetpicker.shared.model.AppIcon
import com.android.launcher3.widgetpicker.shared.model.AppIconBadge
import com.android.launcher3.widgetpicker.shared.model.WidgetAppIcon
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetAppIconsInteractorTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val widgetsRepository = FakeWidgetsRepository()
    private val widgetAppIconsRepository = FakeWidgetAppIconsRepository()

    private val underTest = WidgetAppIconsInteractor(
        widgetAppIconsRepository = widgetAppIconsRepository,
        widgetsRepository = widgetsRepository,
        backgroundContext = testDispatcher
    )

    @Test
    fun returnsMappedAppIcons() = testScope.runTest {
        widgetsRepository.seedWidgets(
            listOf(
                PERSONAL_TEST_APPS[0],
                PERSONAL_TEST_APPS[1],
                WORK_TEST_APPS[0]
            )
        )
        val widgetAppId1 = PERSONAL_TEST_APPS[0].id
        val widgetAppId2 = PERSONAL_TEST_APPS[1].id
        val widgetAppId3 = WORK_TEST_APPS[0].id // no app icon yet
        val appIcon1 = WidgetAppIcon(
            icon = AppIcon.LowResColorIcon(color = Color.BLUE),
            badge = AppIconBadge.NoBadge
        )
        val appIcon2 = WidgetAppIcon(
            icon = AppIcon.HighResBitmapIcon(
                Bitmap.createBitmap(
                    50,
                    50,
                    Bitmap.Config.ARGB_8888
                )
            ),
            badge = AppIconBadge.DrawableBadge(
                drawableResId = android.R.drawable.ic_menu_add,
                tintColor = Color.RED
            )
        )
        widgetAppIconsRepository.seedAppIcons(
            mapOf(
                widgetAppId1 to appIcon1,
                widgetAppId2 to appIcon2,
                // no widgetAppId3
            )
        )
        val expected = mapOf(
            widgetAppId1 to appIcon1,
            widgetAppId2 to appIcon2,
            widgetAppId3 to WidgetAppIcon(
                icon = AppIcon.PlaceHolderAppIcon,
                badge = AppIconBadge.NoBadge
            )
        )
        runCurrent()

        underTest.getAllWidgetAppIcons().filter { it == expected }.first() // no error
        runCurrent()
    }
}
