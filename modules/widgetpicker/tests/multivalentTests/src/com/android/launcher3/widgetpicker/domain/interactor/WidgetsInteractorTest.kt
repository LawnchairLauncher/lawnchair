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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.widgetpicker.TestUtils.PERSONAL_TEST_APPS
import com.android.launcher3.widgetpicker.TestUtils.WORK_TEST_APPS
import com.android.launcher3.widgetpicker.TestUtils.widgetUserProfilePersonal
import com.android.launcher3.widgetpicker.TestUtils.widgetUserProfileWork
import com.android.launcher3.widgetpicker.TestUtils.workUser
import com.android.launcher3.widgetpicker.domain.usecase.FilterWidgetsForHostUseCase
import com.android.launcher3.widgetpicker.domain.usecase.GroupWidgetAppsByProfileUseCase
import com.android.launcher3.widgetpicker.repository.FakeWidgetUsersRepository
import com.android.launcher3.widgetpicker.repository.FakeWidgetsRepository
import com.android.launcher3.widgetpicker.shared.model.WidgetHostInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetPreview
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfiles
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WidgetsInteractorTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val widgetUsersRepository = FakeWidgetUsersRepository()
    private val widgetsRepository = FakeWidgetsRepository()
    private val underTest =
        WidgetsInteractor(
            widgetsRepository = widgetsRepository,
            widgetUsersRepository = widgetUsersRepository,
            getWidgetAppsByProfileUseCase = GroupWidgetAppsByProfileUseCase(),
            filterWidgetsForHostUseCase = FilterWidgetsForHostUseCase(WidgetHostInfo()),
            backgroundContext = testDispatcher
        )

    @Test
    fun noWidgets_returnsEmpty() =
        testScope.runTest {
            assertThat(underTest.getWidgetAppsByProfile().first()).isEmpty()
            assertThat(underTest.getFeaturedWidgets().first()).isEmpty()
            runCurrent()
        }

    @Test
    fun userProfilesNotLoaded_returnsEmpty() =
        testScope.runTest {
            widgetsRepository.seedWidgets(PERSONAL_TEST_APPS + WORK_TEST_APPS)
            runCurrent()

            assertThat(underTest.getWidgetAppsByProfile().first()).isEmpty()
            assertThat(underTest.getFeaturedWidgets().first()).isEmpty()
            runCurrent()
        }

    @Test
    fun getWidgetAppsByProfile_returnsWidgetGroupedByProfile() =
        testScope.runTest {
            widgetUsersRepository.seedUserProfiles(
                profiles =
                    WidgetUserProfiles(
                        personal = widgetUserProfilePersonal,
                        work = widgetUserProfileWork,
                    ),
                workProfileUser = workUser,
            )
            widgetsRepository.seedWidgets(PERSONAL_TEST_APPS + WORK_TEST_APPS)
            runCurrent()

            val result = underTest.getWidgetAppsByProfile().first()
            runCurrent()

            assertThat(result).hasSize(2)
            assertThat(result[widgetUserProfilePersonal])
                .containsExactlyElementsIn(PERSONAL_TEST_APPS)
            assertThat(result[widgetUserProfileWork]).containsExactlyElementsIn(WORK_TEST_APPS)
        }

    @Test
    fun getFeaturedWidgets_returnsAllFeaturedWidgets() =
        testScope.runTest {
            widgetUsersRepository.seedUserProfiles(
                profiles =
                    WidgetUserProfiles(
                        personal = widgetUserProfilePersonal,
                        work = widgetUserProfileWork,
                    ),
                workProfileUser = workUser,
            )
            widgetsRepository.seedWidgets(PERSONAL_TEST_APPS + WORK_TEST_APPS)
            val personalFeaturedWidget = PERSONAL_TEST_APPS[0].widgets[0]
            val workFeaturedWidget = WORK_TEST_APPS[0].widgets[0]
            widgetsRepository.seedFeaturedWidgets(
                setOf(workFeaturedWidget.id, personalFeaturedWidget.id)
            )
            runCurrent()

            val result = underTest.getFeaturedWidgets().first()
            runCurrent()

            assertThat(result).containsExactly(personalFeaturedWidget, workFeaturedWidget)
        }

    @Test
    fun getFeaturedWidgets_pausedWorkProfile_returnsOnlyPersonalWidgets() =
        testScope.runTest {
            widgetUsersRepository.seedUserProfiles(
                profiles =
                    WidgetUserProfiles(
                        personal = widgetUserProfilePersonal,
                        work = widgetUserProfileWork.copy(paused = true),
                    ),
                workProfileUser = workUser,
            )
            widgetsRepository.seedWidgets(PERSONAL_TEST_APPS + WORK_TEST_APPS)
            val personalFeaturedWidget = PERSONAL_TEST_APPS[0].widgets[0]
            val workFeaturedWidget = WORK_TEST_APPS[0].widgets[0]
            widgetsRepository.seedFeaturedWidgets(
                setOf(workFeaturedWidget.id, personalFeaturedWidget.id)
            )
            runCurrent()

            val result = underTest.getFeaturedWidgets().first()
            runCurrent()

            assertThat(result).containsExactly(personalFeaturedWidget)
        }

    @Test
    fun getWidgetPreviews_returnsExpectedPreviews() =
        testScope.runTest {
            widgetUsersRepository.seedUserProfiles(
                profiles =
                    WidgetUserProfiles(
                        personal = widgetUserProfilePersonal,
                        work = widgetUserProfileWork,
                    ),
                workProfileUser = workUser,
            )
            widgetsRepository.seedWidgets(PERSONAL_TEST_APPS + WORK_TEST_APPS)
            val personalWidgetId = PERSONAL_TEST_APPS[0].widgets[0].id
            val personalWidgetPreview =
                WidgetPreview.BitmapWidgetPreview(
                    Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
                )
            val workWidgetId = WORK_TEST_APPS[0].widgets[0].id
            widgetsRepository.seedWidgetPreviews(
                mapOf(
                    personalWidgetId to personalWidgetPreview
                ) // no work widget preview available.
            )
            runCurrent()

            assertThat(underTest.getWidgetPreview(personalWidgetId))
                .isEqualTo(personalWidgetPreview)
            assertThat(underTest.getWidgetPreview(workWidgetId))
                .isEqualTo(WidgetPreview.PlaceholderWidgetPreview)
        }

    @Test
    fun searchWidgets_returnsExpectedWidgets() =
        testScope.runTest {
            widgetUsersRepository.seedUserProfiles(
                profiles =
                    WidgetUserProfiles(
                        personal = widgetUserProfilePersonal,
                        work = widgetUserProfileWork,
                    ),
                workProfileUser = workUser,
            )
            widgetsRepository.seedWidgets(PERSONAL_TEST_APPS + WORK_TEST_APPS)
            runCurrent()

            val input = PERSONAL_TEST_APPS[0].widgets[0].label
            val result = underTest.searchWidgetApps(input).first()
            runCurrent()

            assertThat(result.size).isEqualTo(1)
            assertThat(result[0].widgets.size).isEqualTo(1)
            assertThat(result[0].widgets[0].label).isEqualTo(input)
        }

    @Test
    fun searchWidgets_pausedWorkProfile_returnsOnlyPersonalWidgets() =
        testScope.runTest {
            widgetUsersRepository.seedUserProfiles(
                profiles =
                    WidgetUserProfiles(
                        personal = widgetUserProfilePersonal,
                        work = widgetUserProfileWork.copy(paused = true),
                    ),
                workProfileUser = workUser,
            )
            widgetsRepository.seedWidgets(PERSONAL_TEST_APPS + WORK_TEST_APPS)
            runCurrent()

            val input = "Widget"
            val result = underTest.searchWidgetApps(input).first()
            runCurrent()

            assertThat(result.size).isEqualTo(PERSONAL_TEST_APPS.size)
            assertThat(result.filter { it.title == WORK_TEST_APPS[0].title }).isEmpty()
        }
}
