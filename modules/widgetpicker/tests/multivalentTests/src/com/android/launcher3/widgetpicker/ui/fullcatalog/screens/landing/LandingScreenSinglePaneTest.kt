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

package com.android.launcher3.widgetpicker.ui.fullcatalog.screens.landing

import android.platform.test.rule.AllowedDevices
import android.platform.test.rule.DeviceProduct
import android.platform.test.rule.LimitDevicesRule
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onSiblings
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.widgetpicker.R
import com.android.launcher3.widgetpicker.TestUtils
import com.android.launcher3.widgetpicker.TestUtils.PERSONAL_LABEL
import com.android.launcher3.widgetpicker.TestUtils.PERSONAL_TEST_APPS
import com.android.launcher3.widgetpicker.TestUtils.WORK_LABEL
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
@AllowedDevices(allowed = [DeviceProduct.CF_PHONE])
class LandingScreenSinglePaneTest {
    @get:Rule val limitDevicesRule = LimitDevicesRule()

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalCoroutinesApi::class) private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val context = InstrumentationRegistry.getInstrumentation().context

    private lateinit var viewModel: LandingScreenViewModel
    private val widgetsRepository = FakeWidgetsRepository()
    private val widgetAppIconsRepository = FakeWidgetAppIconsRepository()
    private val widgetsUsersRepository = FakeWidgetUsersRepository()

    private lateinit var featuredTabLabel: String
    private lateinit var browseTabLabel: String
    private val featuredWidgetA = PERSONAL_TEST_APPS[0].widgets[0]
    private val featuredWidgetB = WORK_TEST_APPS[0].widgets[0]

    @Before
    fun setUp() {
        viewModel =
            LandingScreenViewModel(
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

        featuredTabLabel = context.resources.getString(R.string.featured_widgets_tab_label)
        browseTabLabel = context.resources.getString(R.string.browse_widgets_tab_label)

        widgetsRepository.seedWidgets(PERSONAL_TEST_APPS + WORK_TEST_APPS)
        widgetsRepository.seedFeaturedWidgets(setOf(featuredWidgetA.id, featuredWidgetB.id))
        widgetsUsersRepository.seedUserProfiles(
            WidgetUserProfiles(
                personal = TestUtils.widgetUserProfilePersonal,
                work = TestUtils.widgetUserProfileWork,
            ),
            workProfileUser = workUser,
        )
    }

    @Composable
    private fun SinglePaneTestContent() {
        val viewModel = rememberViewModel { viewModel }

        WidgetPickerTheme {
            LandingScreen(
                isCompact = true,
                onEnterSearchMode = {},
                onWidgetInteraction = {},
                showDragShadow = true,
                viewModel = viewModel,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun noWidgets_showsError() =
        testScope.runTest {
            widgetsRepository.seedWidgets(listOf())

            composeTestRule.setContent { SinglePaneTestContent() }

            runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onNode(hasTextExactly(PERSONAL_LABEL)).assertExists().performClick()
            runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNode(hasText("Widgets and shortcuts aren\'t available"))
                .assertExists()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun showsFeaturedSectionByDefault() =
        testScope.runTest {
            composeTestRule.setContent { SinglePaneTestContent() }

            runCurrent()
            composeTestRule.waitForIdle()

            // Toolbar tabs state
            composeTestRule.onNode(hasTextExactly(featuredTabLabel)).assertIsSelected()
            composeTestRule
                .onNode(hasTextExactly(PERSONAL_LABEL))
                .assertExists()
                .assertIsNotSelected()
            composeTestRule.onNode(hasTextExactly(WORK_LABEL)).assertExists().assertIsNotSelected()
            // Featured Widgets state
            composeTestRule
                .onNode(hasContentDescription(featuredWidgetA.label, substring = true))
                .assertExists()
            composeTestRule
                .onNode(hasContentDescription(featuredWidgetB.label, substring = true))
                .assertExists()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun clickingOnAnotherTabShowsAppsList() =
        testScope.runTest {
            composeTestRule.setContent { SinglePaneTestContent() }

            runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onNode(hasTextExactly(PERSONAL_LABEL)).assertExists().performClick()
            runCurrent()
            composeTestRule.waitForIdle()

            // Toolbar tabs state
            composeTestRule.onNode(hasTextExactly(PERSONAL_LABEL)).assertExists().assertIsSelected()
            composeTestRule.onNode(hasTextExactly(featuredTabLabel)).assertIsNotSelected()
            composeTestRule.onNode(hasTextExactly(WORK_LABEL)).assertExists().assertIsNotSelected()
            // No recommendations showing
            composeTestRule
                .onNode(hasContentDescription(featuredWidgetA.label, substring = true))
                .assertIsNotDisplayed()
            // Has list of personal apps showing
            composeTestRule.onNode(hasText(PERSONAL_TEST_APPS[0].title!!.toString())).assertExists()
            // But not the work apps
            composeTestRule
                .onNode(hasText(WORK_TEST_APPS[0].title!!.toString()))
                .assertIsNotDisplayed()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun noWorkProfile() =
        testScope.runTest {
            widgetsRepository.seedWidgets(PERSONAL_TEST_APPS)
            widgetsRepository.seedFeaturedWidgets(setOf(featuredWidgetA.id))
            widgetsUsersRepository.seedUserProfiles(
                WidgetUserProfiles(personal = TestUtils.widgetUserProfilePersonal, work = null),
                workProfileUser = null,
            )

            composeTestRule.setContent { SinglePaneTestContent() }

            runCurrent()
            composeTestRule.waitForIdle()

            // Toolbar tabs state shows 2 tabs (featured / browse)
            composeTestRule.onNode(hasTextExactly(featuredTabLabel)).assertIsSelected()
            composeTestRule
                .onNode(hasTextExactly(browseTabLabel))
                .assertExists()
                .assertIsNotSelected()
            composeTestRule.onNode(hasTextExactly(browseTabLabel)).onSiblings().assertCountEquals(1)
            // featured widgets showing
            composeTestRule
                .onNode(hasContentDescription(featuredWidgetA.label, substring = true))
                .assertIsDisplayed()
            // Widget apps list is not showing.
            composeTestRule
                .onNode(hasText(PERSONAL_TEST_APPS[0].title!!.toString()))
                .assertIsNotDisplayed()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun pausedWorkProfile_workWidgetsNotShown() =
        testScope.runTest {
            val pausedError = "work apps paused"
            widgetsRepository.seedWidgets(PERSONAL_TEST_APPS + WORK_TEST_APPS)
            widgetsRepository.seedFeaturedWidgets(setOf(featuredWidgetA.id))
            widgetsUsersRepository.seedUserProfiles(
                WidgetUserProfiles(
                    personal = TestUtils.widgetUserProfilePersonal,
                    work =
                        TestUtils.widgetUserProfileWork.copy(
                            paused = true,
                            pausedProfileMessage = "work apps paused",
                        ),
                ),
                workProfileUser = workUser,
            )

            composeTestRule.setContent { SinglePaneTestContent() }

            runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onNode(hasTextExactly(WORK_LABEL)).assertExists().performClick()

            runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onNode(hasText(pausedError)).assertIsDisplayed()
            // And work apps aren't displayed
            composeTestRule
                .onNode(hasText(WORK_TEST_APPS[0].title!!.toString()))
                .assertIsNotDisplayed()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun clickingOnAppHeaderShowsWidgetsForThatApp() =
        testScope.runTest {
            composeTestRule.setContent { SinglePaneTestContent() }

            runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onNode(hasTextExactly(PERSONAL_LABEL)).assertExists().performClick()

            runCurrent()
            composeTestRule.waitForIdle()

            // Click on the an app header
            val appTitle = PERSONAL_TEST_APPS[0].title!!.toString()
            composeTestRule.onNode(hasText(appTitle)).assertExists().performClick()

            runCurrent()
            composeTestRule.waitForIdle()

            // Verify the widget for that selected app is displayed
            composeTestRule
                .onNode(
                    hasContentDescription(PERSONAL_TEST_APPS[0].widgets[0].label, substring = true)
                )
                .assertIsDisplayed()
        }
}
