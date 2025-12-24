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

package com.android.launcher3.widgetpicker.ui.screens

import android.platform.test.rule.DisableAnimationsRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import com.android.launcher3.widgetpicker.WidgetPickerComponent
import com.android.launcher3.widgetpicker.goldenpathmanager.WidgetPickerGoldenPathManager
import com.android.launcher3.widgetpicker.shared.model.CloseBehavior
import com.android.launcher3.widgetpicker.shared.model.WidgetHostInfo
import com.android.launcher3.widgetpicker.ui.NoOpWidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.WidgetInteractionInfo
import com.android.launcher3.widgetpicker.ui.WidgetPickerEventListeners
import com.android.launcher3.widgetpicker.ui.testdata.ScreenshotTestData
import com.android.launcher3.widgetpicker.ui.testdata.ScreenshotTestWidgetAppIconsRepository
import com.android.launcher3.widgetpicker.ui.testdata.ScreenshotTestWidgetUsersRepository
import com.android.launcher3.widgetpicker.ui.testdata.ScreenshotTestWidgetsRepository
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import dagger.Component
import dagger.Module
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters
import platform.test.screenshot.DeviceEmulationSpec
import platform.test.screenshot.Displays
import platform.test.screenshot.getEmulatedDevicePathConfig
import platform.test.screenshot.utils.compose.ComposeScreenshotTestRule

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(ParameterizedAndroidJunit4::class)
class LandingScreenScreenshotTest(emulationSpec: DeviceEmulationSpec) {
    @get:Rule(order = 0) val disableAnimationsRule = DisableAnimationsRule()

    @get:Rule(order = 1)
    val screenshotRule =
        ComposeScreenshotTestRule(
            emulationSpec,
            WidgetPickerGoldenPathManager(getEmulatedDevicePathConfig(emulationSpec)),
        )

    private val testData =
        ScreenshotTestData(
            screenWidth = emulationSpec.display.width,
            screenHeight = emulationSpec.display.height,
        )
    private val usersRepository = ScreenshotTestWidgetUsersRepository()
    private val widgetsRepository = ScreenshotTestWidgetsRepository(testData)
    private val widgetAppIconsRepository = ScreenshotTestWidgetAppIconsRepository(testData)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val testComponent by lazy { DaggerScreenshotTestComponent.create() }

    private fun createWidgetPickerComponent(
        widgetHostInfo: WidgetHostInfo = WidgetHostInfo(title = "Widgets")
    ): WidgetPickerComponent {
        return testComponent
            .widgetPickerComponentFactory()
            .build(
                widgetUsersRepository = usersRepository,
                widgetAppIconsRepository = widgetAppIconsRepository,
                widgetsRepository = widgetsRepository,
                widgetHostInfo = widgetHostInfo,
                backgroundContext = testDispatcher,
            )
    }

    @Test
    fun fullCatalog_landingScreen_featuredSection() =
        testScope.runTest {
            val widgetPickerComponent = createWidgetPickerComponent()
            screenshotRule.screenshotTest(
                goldenIdentifier = "fullCatalog_landingScreen_featuredSection",
                beforeScreenshot = {
                    advanceUntilIdle()
                    runCurrent()
                },
            ) {
                WidgetPickerTheme {
                    widgetPickerComponent
                        .getFullWidgetsCatalog()
                        .Content(
                            eventListeners = NoOpEventListener,
                            cuiReporter = NoOpWidgetPickerCuiReporter(),
                        )
                }
            }
        }

    @Test
    fun fullCatalog_landingScreen_browseSection() =
        testScope.runTest {
            val widgetPickerComponent = createWidgetPickerComponent()
            screenshotRule.screenshotTest(
                goldenIdentifier = "fullCatalog_landingScreen_browseSection",
                beforeScreenshot = {
                    advanceUntilIdle()
                    runCurrent()

                    val isSinglePane =
                        screenshotRule.composeRule
                            .onAllNodes(hasText(BROWSE_TAB_LABEL))
                            .fetchSemanticsNodes(atLeastOneRootRequired = false)
                            .isNotEmpty()

                    if (isSinglePane) {
                        screenshotRule.composeRule
                            .onNode(hasText(BROWSE_TAB_LABEL))
                            .assertExists()
                            .performClick()
                        advanceUntilIdle()
                        runCurrent()
                    }

                    screenshotRule.composeRule
                        .onNode(hasText("App 2"))
                        .assertExists()
                        .performClick()
                },
            ) {
                WidgetPickerTheme {
                    widgetPickerComponent
                        .getFullWidgetsCatalog()
                        .Content(
                            eventListeners = NoOpEventListener,
                            cuiReporter = NoOpWidgetPickerCuiReporter(),
                        )
                }
            }
        }

    @Test
    fun enforceMaxSizes_landingScreen() =
        testScope.runTest {
            val widgetPickerComponent =
                createWidgetPickerComponent(
                    widgetHostInfo =
                        WidgetHostInfo(
                            title = "Widgets",
                            closeBehavior = CloseBehavior.CLOSE_BUTTON,
                        )
                )

            screenshotRule.screenshotTest(
                goldenIdentifier = "enforceMaxSizes_landingScreen",
                beforeScreenshot = {
                    advanceUntilIdle()
                    runCurrent()
                },
            ) {
                WidgetPickerTheme {
                    widgetPickerComponent
                        .getFullWidgetsCatalog()
                        .Content(
                            eventListeners = NoOpEventListener,
                            cuiReporter = NoOpWidgetPickerCuiReporter(),
                        )
                }
            }
        }

    companion object {
        private const val BROWSE_TAB_LABEL = "Browse"
        private val NoOpEventListener =
            object : WidgetPickerEventListeners {
                override fun onClose() {}

                override fun onWidgetInteraction(widgetInteractionInfo: WidgetInteractionInfo) {}
            }

        @Parameters(name = "{0}")
        @JvmStatic
        fun getTestSpecs(): List<DeviceEmulationSpec> {
            return DeviceEmulationSpec.forDisplays(
                Displays.Phone,
                Displays.FoldableInner,
                Displays.Tablet,
                isDarkTheme = false,
            )
        }
    }
}

@Singleton
@Component(modules = [ScreenshotTestSubcomponentModule::class])
interface ScreenshotTestComponent {
    // This function allows us to get the factory for the subcomponent.
    fun widgetPickerComponentFactory(): WidgetPickerComponent.Factory
}

@Module(subcomponents = [WidgetPickerComponent::class]) class ScreenshotTestSubcomponentModule
