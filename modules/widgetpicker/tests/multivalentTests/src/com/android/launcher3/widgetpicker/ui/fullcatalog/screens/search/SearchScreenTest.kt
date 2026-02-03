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

package com.android.launcher3.widgetpicker.ui.fullcatalog.screens.search

import android.platform.test.rule.AllowedDevices
import android.platform.test.rule.DeviceProduct
import android.platform.test.rule.LimitDevicesRule
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.widgetpicker.R
import com.android.launcher3.widgetpicker.TestUtils
import com.android.launcher3.widgetpicker.TestUtils.PERSONAL_TEST_APPS
import com.android.launcher3.widgetpicker.TestUtils.WORK_TEST_APPS
import com.android.launcher3.widgetpicker.TestUtils.workUser
import com.android.launcher3.widgetpicker.domain.interactor.WidgetAppIconsInteractor
import com.android.launcher3.widgetpicker.domain.interactor.WidgetsInteractor
import com.android.launcher3.widgetpicker.domain.usecase.FilterWidgetsForHostUseCase
import com.android.launcher3.widgetpicker.domain.usecase.GroupWidgetAppsByProfileUseCase
import com.android.launcher3.widgetpicker.repository.FakeWidgetAppIconsRepository
import com.android.launcher3.widgetpicker.repository.FakeWidgetUsersRepository
import com.android.launcher3.widgetpicker.repository.FakeWidgetsRepository
import com.android.launcher3.widgetpicker.shared.model.WidgetHostInfo
import com.android.launcher3.widgetpicker.shared.model.WidgetUserProfiles
import com.android.launcher3.widgetpicker.ui.rememberViewModel
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AllowedDevices(allowed = [DeviceProduct.CF_TABLET])
class SearchScreenTest {
    @get:Rule val limitDevicesRule = LimitDevicesRule()
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalCoroutinesApi::class) private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val context = InstrumentationRegistry.getInstrumentation().context

    private lateinit var viewModel: SearchScreenViewModel
    private val widgetsRepository = FakeWidgetsRepository()
    private val widgetAppIconsRepository = FakeWidgetAppIconsRepository()
    private val widgetsUsersRepository = FakeWidgetUsersRepository()

    private lateinit var searchBarHint: String
    private lateinit var clearButtonContentDescription: String

    @Before
    fun setUp() {
        viewModel =
            SearchScreenViewModel(
                widgetsInteractor =
                    WidgetsInteractor(
                        widgetsRepository = widgetsRepository,
                        widgetUsersRepository = widgetsUsersRepository,
                        filterWidgetsForHostUseCase = FilterWidgetsForHostUseCase(WidgetHostInfo()),
                        getWidgetAppsByProfileUseCase = GroupWidgetAppsByProfileUseCase(),
                        backgroundContext = testDispatcher,
                    ),
                widgetAppIconsInteractor =
                    WidgetAppIconsInteractor(
                        widgetAppIconsRepository = widgetAppIconsRepository,
                        widgetsRepository = widgetsRepository,
                        backgroundContext = testDispatcher,
                    ),
            )

        searchBarHint =
            context.applicationContext.resources.getString(R.string.widgets_search_bar_hint)
        clearButtonContentDescription =
            context.applicationContext.resources.getString(
                R.string.widget_search_bar_clear_button_label
            )
        widgetsRepository.seedWidgets(PERSONAL_TEST_APPS + WORK_TEST_APPS)
        widgetsUsersRepository.seedUserProfiles(
            WidgetUserProfiles(
                personal = TestUtils.widgetUserProfilePersonal,
                work = TestUtils.widgetUserProfileWork,
            ),
            workProfileUser = workUser,
        )
    }

    @Composable
    private fun TestContent(isCompact: Boolean) {
        val viewModel = rememberViewModel { viewModel }

        WidgetPickerTheme {
            SearchScreen(
                isCompact = isCompact,
                onExitSearchMode = {},
                onWidgetInteraction = {},
                showDragShadow = true,
                viewModel = viewModel,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun singlePane_showMatchingApps() =
        testScope.runTest {
            composeTestRule.setContent { TestContent(isCompact = true) }

            runCurrent()
            composeTestRule.waitForIdle()

            val testInputOne = "PersonalWidget"
            composeTestRule.onNodeWithText(searchBarHint).performTextInput(testInputOne)

            runCurrent()
            composeTestRule.waitForIdle()

            // has both apps
            composeTestRule.onNode(hasText(PERSONAL_TEST_APPS[0].title!!.toString())).assertExists()
            composeTestRule.onNode(hasText(PERSONAL_TEST_APPS[1].title!!.toString())).assertExists()

            composeTestRule
                .onNodeWithContentDescription(clearButtonContentDescription)
                .performClick()

            runCurrent()
            composeTestRule.waitForIdle()

            // change query
            val testInputTwo = "PersonalWidget1A"
            composeTestRule.onNodeWithText(searchBarHint).performTextInput(testInputTwo)

            runCurrent()
            composeTestRule.waitForIdle()

            // has only app 1
            composeTestRule.onNode(hasText(PERSONAL_TEST_APPS[0].title!!.toString())).assertExists()
            composeTestRule
                .onNode(hasText(PERSONAL_TEST_APPS[1].title!!.toString()))
                .assertDoesNotExist()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun singlePane_noResults_showsNotFoundMessage() =
        testScope.runTest {
            composeTestRule.setContent { TestContent(isCompact = true) }

            runCurrent()
            composeTestRule.waitForIdle()

            val notMatchingInput = "Invalid"
            composeTestRule.onNodeWithText(searchBarHint).performTextInput(notMatchingInput)

            runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onNode(hasText("No widgets or shortcuts found")).assertExists()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun twoPane_showMatchingApps() =
        testScope.runTest {
            composeTestRule.setContent { TestContent(isCompact = false) }

            runCurrent()
            composeTestRule.waitForIdle()

            val testInputOne = "PersonalWidget"
            composeTestRule.onNodeWithText(searchBarHint).performTextInput(testInputOne)

            runCurrent()
            composeTestRule.waitForIdle()

            // has both apps
            composeTestRule.onNode(hasText(PERSONAL_TEST_APPS[0].title!!.toString())).assertExists()
            composeTestRule.onNode(hasText(PERSONAL_TEST_APPS[1].title!!.toString())).assertExists()

            composeTestRule
                .onNodeWithContentDescription(clearButtonContentDescription)
                .performClick()

            runCurrent()
            composeTestRule.waitForIdle()

            // change query
            val testInputTwo = "PersonalWidget1A"
            composeTestRule.onNodeWithText(searchBarHint).performTextInput(testInputTwo)

            runCurrent()
            composeTestRule.waitForIdle()

            // has only app 1
            composeTestRule.onNode(hasText(PERSONAL_TEST_APPS[0].title!!.toString())).assertExists()
            composeTestRule
                .onNode(hasText(PERSONAL_TEST_APPS[1].title!!.toString()))
                .assertDoesNotExist()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun twoPane_noResults_showsNotFoundMessage() =
        testScope.runTest {
            composeTestRule.setContent { TestContent(isCompact = false) }

            runCurrent()
            composeTestRule.waitForIdle()

            val notMatchingInput = "Invalid"
            composeTestRule.onNodeWithText(searchBarHint).performTextInput(notMatchingInput)

            runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onNode(hasText("No widgets or shortcuts found")).assertExists()
        }
}
