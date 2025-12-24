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

package com.android.launcher3.widgetpicker.ui.components.bottomsheet

import android.platform.test.rule.DeniedDevices
import android.platform.test.rule.DeviceProduct
import android.platform.test.rule.LimitDevicesRule
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabelMatching
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.widgetpicker.shared.model.CloseBehavior
import com.android.launcher3.widgetpicker.ui.LocalWidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.NoOpWidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.components.accessibility.AccessibilityState
import com.android.launcher3.widgetpicker.ui.components.accessibility.LocalAccessibilityState
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for bottom sheet. */
@RunWith(AndroidJUnit4::class)
@DeniedDevices(denied = [DeviceProduct.ROBOLECTRIC])
class TitledBottomSheetTest {
    @get:Rule val limitDevicesRule = LimitDevicesRule()

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var accessibilityState = AccessibilityState(isEnabled = false)

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun canCloseSheetWithSemantics() {
        composeTestRule.setContent { SheetTestContent() }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasText(CONTENT_TEXT)).assertExists()
        composeTestRule.onNode(hasText(CLOSED_TEXT)).assertDoesNotExist()

        composeTestRule
            .onNode(
                SemanticsMatcher("check has close action") {
                    it.config.getOrNull(SemanticsActions.CustomActions) != null
                }
            )
            .performCustomAccessibilityActionWithLabelMatching(
                predicateDescription = "Close sheet action"
            ) { label ->
                label == "Close sheet"
            }

        composeTestRule.onNode(hasText(CONTENT_TEXT)).assertDoesNotExist()
        composeTestRule.onNode(hasText(CLOSED_TEXT)).assertExists()
    }

    @Test
    fun canCloseWithCloseButton() {
        composeTestRule.setContent {
            SheetTestContent(/* closeBehavior= */ CloseBehavior.CLOSE_BUTTON)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNode(hasText(CONTENT_TEXT)).assertExists()
        composeTestRule.onNode(hasText(CLOSED_TEXT)).assertDoesNotExist()
        composeTestRule
            .onNodeWithContentDescription("Close sheet")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.onNode(hasText(CONTENT_TEXT)).assertDoesNotExist()
        composeTestRule.onNode(hasText(CLOSED_TEXT)).assertExists()
    }

    @Test
    fun accessibilityEnabled_nestedScrollDisabled() {
        nestedScrollAccessibilityTest(accessibilityEnabled = true)
    }

    @Test
    fun accessibilityDisabled_nestedScrollEnabled() {
        nestedScrollAccessibilityTest(accessibilityEnabled = false)
    }

    private fun nestedScrollAccessibilityTest(accessibilityEnabled: Boolean) {
        accessibilityState = AccessibilityState(isEnabled = accessibilityEnabled)

        composeTestRule.setContent { SheetTestContent() }
        composeTestRule.waitForIdle()

        val sheetTitleTopBefore =
            composeTestRule.onNode(hasText(SHEET_TITLE)).fetchSemanticsNode().boundsInRoot.top
        // full-swipe down even if list is fully at the top
        composeTestRule.onNode(hasTestTag(LIST_TEST_TAG)).performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        // Closes the sheet only if accessibility services are disabled.
        if (accessibilityEnabled) {
            val sheetTitleTopAfter =
                composeTestRule.onNode(hasText(SHEET_TITLE)).fetchSemanticsNode().boundsInRoot.top
            assertThat(sheetTitleTopAfter).isEqualTo(sheetTitleTopBefore)
        } else {
            composeTestRule.onNode(hasText(SHEET_TITLE)).assertDoesNotExist()
            composeTestRule.onNode(hasText(CLOSED_TEXT)).assertExists()
        }
    }

    @Composable
    private fun SheetTestContent(closeBehavior: CloseBehavior = CloseBehavior.DRAG_HANDLE) {
        var isClosed by remember { mutableStateOf(false) }

        TestContentWrapper {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isClosed) {
                    Text(CLOSED_TEXT)
                } else {
                    TitledBottomSheet(
                        title = SHEET_TITLE,
                        description = null,
                        modifier = Modifier,
                        heightStyle = ModalBottomSheetHeightStyle.FILL_HEIGHT,
                        closeBehavior = closeBehavior,
                        onSheetOpen = {},
                        onDismissSheet = { isClosed = true },
                    ) {
                        LazyColumn(modifier = Modifier.testTag(LIST_TEST_TAG)) {
                            item { Text(CONTENT_TEXT) }
                            items(1000) { index -> Text("$index") }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TestContentWrapper(content: @Composable () -> Unit) {
        WidgetPickerTheme {
            CompositionLocalProvider(
                LocalWidgetPickerCuiReporter provides NoOpWidgetPickerCuiReporter(),
                LocalAccessibilityState provides accessibilityState,
            ) {
                content()
            }
        }
    }

    companion object {
        private const val SHEET_TITLE = "title"
        private const val CONTENT_TEXT = "Content"
        private const val CLOSED_TEXT = "Closed"

        private const val LIST_TEST_TAG = "list"
    }
}
