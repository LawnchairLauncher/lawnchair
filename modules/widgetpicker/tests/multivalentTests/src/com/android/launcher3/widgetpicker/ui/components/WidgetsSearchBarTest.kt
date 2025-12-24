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

package com.android.launcher3.widgetpicker.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for the [WidgetsSearchBar] component. See SearchScreenTest for integration of this
 * component with rest of picker.
 */
@RunWith(AndroidJUnit4::class)
class WidgetsSearchBarTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalCoroutinesApi::class) private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun clickingSearchBar_entersSearchMode() =
        testScope.runTest {
            composeTestRule.setContent {
                var isSearching by remember { mutableStateOf(false) }

                WidgetsSearchBarTestContent(
                    isSearching = isSearching,
                    onToggleSearchMode = { isSearching = it },
                )
            }

            testScope.runCurrent()
            composeTestRule.awaitIdle()

            composeTestRule.onNode(hasText(NOT_IN_SEARCH_MODE_TEXT)).assertExists()

            composeTestRule
                .onNode(isEditable())
                .assertExists()
                .assertHasClickAction()
                .performClick()

            testScope.runCurrent()
            composeTestRule.awaitIdle()

            // Enters search mode
            composeTestRule.onNode(hasText(SEARCH_MODE_TEXT)).assertExists()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun inSearchMode_canEdit() {
        testScope.runTest {
            composeTestRule.setContent {
                var isSearching by remember { mutableStateOf(true) }

                WidgetsSearchBarTestContent(
                    isSearching = isSearching,
                    onToggleSearchMode = { isSearching = it },
                )
            }

            testScope.runCurrent()
            composeTestRule.waitForIdle()

            composeTestRule.onNode(hasText(SEARCH_MODE_TEXT)).assertExists()
            composeTestRule.onNode(ResultTextMatcher).assertDoesNotExist()
            composeTestRule
                .onNode(isEditable())
                .assertExists()
                .assertIsEnabled()
                .performTextInput(RESULT_TEXT)

            testScope.runCurrent()
            composeTestRule.waitForIdle()

            // But enter press with keyboard does enter to search mode
            composeTestRule.onNode(ResultTextMatcher).assertExists()
            // And still in search mode
            composeTestRule.onNode(hasText(SEARCH_MODE_TEXT)).assertExists()
        }
    }

    @Composable
    fun WidgetsSearchBarTestContent(isSearching: Boolean, onToggleSearchMode: (Boolean) -> Unit) {
        var input: String by remember { mutableStateOf("") }

        WidgetPickerTheme {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
                WidgetsSearchBar(
                    text = input,
                    isSearching = isSearching,
                    onSearch = { input = it },
                    onToggleSearchMode = onToggleSearchMode,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text =
                        when {
                            isSearching -> SEARCH_MODE_TEXT
                            else -> NOT_IN_SEARCH_MODE_TEXT
                        }
                )
                Text(input)
            }
        }
    }

    companion object {
        private const val SEARCH_MODE_TEXT = "In search mode"
        private const val NOT_IN_SEARCH_MODE_TEXT = "Not in search mode"
        private const val RESULT_TEXT = "Matched result"

        private val ResultTextMatcher = hasText(RESULT_TEXT) and isEditable().not()
    }
}
