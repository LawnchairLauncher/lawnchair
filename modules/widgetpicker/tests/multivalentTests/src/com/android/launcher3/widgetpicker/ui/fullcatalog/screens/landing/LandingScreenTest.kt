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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.widgetpicker.TestUtils
import com.android.launcher3.widgetpicker.TestUtils.PERSONAL_TEST_APPS
import com.android.launcher3.widgetpicker.TestUtils.WORK_TEST_APPS
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
@OptIn(ExperimentalCoroutinesApi::class)
// Large screens where we can emulate both UI areas
@AllowedDevices(allowed = [DeviceProduct.CF_TABLET, DeviceProduct.TANGORPRO, DeviceProduct.FELIX])
class LandingScreenTest {
    @get:Rule val limitDevicesRule = LimitDevicesRule()

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalCoroutinesApi::class) private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val context = InstrumentationRegistry.getInstrumentation().context

    private lateinit var viewModel: LandingScreenViewModel
    private val widgetsRepository = FakeWidgetsRepository()
    private val widgetAppIconsRepository = FakeWidgetAppIconsRepository()
    private val widgetsUsersRepository = FakeWidgetUsersRepository()

    private lateinit var searchBarHint: String
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

        searchBarHint = "Search"
        // context.applicationContext.resources.getString(R.string.widgets_search_bar_hint)
        featuredTabLabel = "Featured"
        // context.resources.getString(R.string.featured_widgets_tab_label)
        browseTabLabel = "Browse"
        // context.resources.getString(R.string.browse_widgets_tab_label)

        widgetsRepository.seedWidgets(PERSONAL_TEST_APPS)
        widgetsRepository.seedFeaturedWidgets(setOf(featuredWidgetA.id, featuredWidgetB.id))
        widgetsUsersRepository.seedUserProfiles(
            WidgetUserProfiles(personal = TestUtils.widgetUserProfilePersonal, work = null),
            workProfileUser = null,
        )
    }

    @Test
    fun reenterLandingFromSearchMode_browseAppSelectedInitially_stateIsReset() =
        testScope.runTest {
            val appTitle = PERSONAL_TEST_APPS[0].title!!.toString()
            val widgetLabel = PERSONAL_TEST_APPS[0].widgets[0].label
            composeTestRule.setContent { LandingScreenTestContent(isCompact = true) }
            testScope.runCurrent()
            composeTestRule.waitForIdle()

            // By default featured tab is selected.
            composeTestRule
                .onNode(hasTextExactly(featuredTabLabel))
                .assertExists()
                .assertIsSelected()

            // Select the browse tab
            composeTestRule
                .onNode(hasTextExactly(browseTabLabel))
                .assertExists()
                .assertIsNotSelected()
                .performClick()
            testScope.runCurrent()
            composeTestRule.waitForIdle()

            // Select an app from browse list
            composeTestRule.onNode(hasText(appTitle)).performClick()
            testScope.runCurrent()
            composeTestRule.waitForIdle()
            // Wait for widgets to appear
            composeTestRule
                .onNode(hasContentDescription(widgetLabel, substring = true))
                .assertExists()

            // Now navigate to search
            composeTestRule.onNode(hasContentDescription(searchBarHint)).performClick()
            testScope.runCurrent()
            composeTestRule.waitForIdle()

            // Exit search
            composeTestRule.onNode(hasText(SEARCH_MODE_TEST_EXIT)).assertExists().performClick()
            testScope.runCurrent()
            composeTestRule.waitForIdle()

            // This time the default tab "Featured" is re-selected
            composeTestRule.onNode(hasText(featuredTabLabel)).assertIsSelected()
        }

    @Test
    fun switchBetweenSingleAndTwoPane_retainsSelectedApp() {
        val toggleCompactButton = "Toggle"
        val app1 = PERSONAL_TEST_APPS[0].title!!.toString()
        val app1Widget = PERSONAL_TEST_APPS[0].widgets[0].label
        val app2 = PERSONAL_TEST_APPS[1].title!!.toString()
        val app2Widget = PERSONAL_TEST_APPS[1].widgets[0].label

        composeTestRule.setContent {
            var isCompact by remember { mutableStateOf(true) }

            Column(modifier = Modifier.fillMaxSize()) {
                TextButton(onClick = { isCompact = !isCompact }) { Text(toggleCompactButton) }
                LandingScreenTestContent(isCompact = isCompact)
            }
        }
        testScope.runCurrent()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNode(hasTextExactly(browseTabLabel))
            .assertExists()
            .assertIsNotSelected()
            .performClick()
        testScope.runCurrent()
        composeTestRule.waitForIdle()

        // Select an app from browse list
        composeTestRule.onNode(hasText(app1)).performClick()
        testScope.runCurrent()
        composeTestRule.waitForIdle()

        // app1's widgets are showing
        composeTestRule
            .onNode(hasContentDescription(app1Widget, substring = true))
            .assertIsDisplayed()
        composeTestRule
            .onNode(hasContentDescription(app2Widget, substring = true))
            .assertDoesNotExist()

        // Now change to two pane
        composeTestRule.onNode(hasText(toggleCompactButton)).performClick()
        testScope.runCurrent()
        composeTestRule.waitForIdle()

        // app1 is still selected (in two pane).
        composeTestRule.onNode(hasText(app1)).assertIsSelected()
        composeTestRule.onNode(hasText(featuredTabLabel)).assertIsNotSelected()
        // selected app2
        composeTestRule.onNode(hasText(app2)).performClick()
        testScope.runCurrent()
        composeTestRule.waitForIdle()

        // Change back to single pane
        composeTestRule.onNode(hasText(toggleCompactButton)).performClick()
        testScope.runCurrent()
        composeTestRule.waitForIdle()

        // now app2's selection is maintained (i.e. its widgets are showing)
        composeTestRule
            .onNode(hasContentDescription(app2Widget, substring = true))
            .assertIsDisplayed()
        composeTestRule
            .onNode(hasContentDescription(app1Widget, substring = true))
            .assertDoesNotExist()
    }

    @Composable
    private fun LandingScreenTestContent(isCompact: Boolean) {
        val viewModel = rememberViewModel { viewModel }
        var isSearchMode by remember { mutableStateOf(false) }

        WidgetPickerTheme {
            if (isSearchMode) {
                TextButton(onClick = { isSearchMode = false }) { Text(SEARCH_MODE_TEST_EXIT) }
            } else {
                LandingScreen(
                    isCompact = isCompact,
                    onEnterSearchMode = { isSearchMode = true },
                    onWidgetInteraction = {},
                    showDragShadow = true,
                    viewModel = viewModel,
                )
            }
        }

        LaunchedEffect(Unit) { viewModel.onUiReady() }
    }

    companion object {
        private const val SEARCH_MODE_TEST_EXIT = "Search Mode; Click to go back"
    }
}
