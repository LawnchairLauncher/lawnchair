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

import android.platform.test.rule.DeniedDevices
import android.platform.test.rule.DeviceProduct
import android.platform.test.rule.LimitDevicesRule
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChildAt
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.widgetpicker.ui.LocalWidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.WidgetPickerCui
import com.android.launcher3.widgetpicker.ui.WidgetPickerCuiReporter
import com.android.launcher3.widgetpicker.ui.theme.WidgetPickerTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@RunWith(AndroidJUnit4::class)
@DeniedDevices(denied = [DeviceProduct.ROBOLECTRIC])
class WidgetsListHeaderTest {
    @get:Rule val limitDevicesRule = LimitDevicesRule()

    @get:Rule val mockito: MockitoRule = MockitoJUnit.rule()

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Mock private lateinit var cuiReporterMock: WidgetPickerCuiReporter

    @OptIn(ExperimentalCoroutinesApi::class) private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun expandCollapseWithSemantics() =
        testScope.runTest {
            composeTestRule.setContent {
                var expanded by remember { mutableStateOf(false) }

                ExpandableWidgetListHeaderTestContent(
                    expanded = expanded,
                    onClick = { expanded = !expanded },
                )
            }

            composeTestRule.waitForIdle()

            composeTestRule.onNode(hasText(EXPANDED_CONTENT_TEXT)).assertDoesNotExist()
            composeTestRule
                .onNode(hasText(TITLE))
                .assertExists()
                .assert(hasText(SUB_TITLE))
                .assertHasClickAction()
                .assert(
                    SemanticsMatcher("check expand semantics action") {
                        it.config.getOrNull(SemanticsActions.Expand) != null
                    }
                )
                .performSemanticsAction(SemanticsActions.Expand)

            composeTestRule.waitForIdle()

            // Expanded
            composeTestRule.onNode(hasText(EXPANDED_CONTENT_TEXT)).assertExists()
            // Still clickable and this time has collapse action instead.
            composeTestRule
                .onNode(hasText(TITLE))
                .assertExists()
                .assert(hasText(SUB_TITLE))
                .assertHasClickAction()
                .assert(
                    SemanticsMatcher("check collapse semantics action") {
                        it.config.getOrNull(SemanticsActions.Collapse) != null
                    }
                )
                .performSemanticsAction(SemanticsActions.Collapse)

            composeTestRule.waitForIdle()

            // Back to original state.
            composeTestRule.onNode(hasText(EXPANDED_CONTENT_TEXT)).assertDoesNotExist()
            composeTestRule
                .onNode(hasText(TITLE))
                .assertExists()
                .assert(hasText(SUB_TITLE))
                .assertHasClickAction()
                .assert(
                    SemanticsMatcher("check expand semantics action") {
                        it.config.getOrNull(SemanticsActions.Expand) != null
                    }
                )
        }

    @Test
    fun initiallyExpanded_showsContentAndHasCollapseAction() =
        testScope.runTest {
            composeTestRule.setContent {
                var expanded by remember { mutableStateOf(true) }

                ExpandableWidgetListHeaderTestContent(
                    expanded = expanded,
                    onClick = { expanded = !expanded },
                )
            }

            composeTestRule.waitForIdle()

            // Initially expanded, content should be visible.
            composeTestRule.onNode(hasText(EXPANDED_CONTENT_TEXT)).assertExists()

            // Should have collapse action.
            composeTestRule
                .onNode(hasText(TITLE))
                .assertExists()
                .assert(hasText(SUB_TITLE))
                .assertHasClickAction()
                .assert(
                    SemanticsMatcher("check collapse semantics action") {
                        it.config.getOrNull(SemanticsActions.Collapse) != null
                    }
                )
                .performSemanticsAction(SemanticsActions.Collapse)

            composeTestRule.waitForIdle()

            // Collapsed, content should be gone.
            composeTestRule.onNode(hasText(EXPANDED_CONTENT_TEXT)).assertDoesNotExist()
        }

    @Test
    fun widgetCountString_returnsCorrectWidgetsCount() =
        testScope.runTest {
            composeTestRule.setContent {
                Column {
                    Text(widgetsCountString(widgets = 10, shortcuts = 0))
                    Text(widgetsCountString(widgets = 1, shortcuts = 0))
                    Text(widgetsCountString(widgets = 0, shortcuts = 10))
                    Text(widgetsCountString(widgets = 0, shortcuts = 1))
                    Text(widgetsCountString(widgets = 1, shortcuts = 1))
                    Text(widgetsCountString(widgets = 10, shortcuts = 10))
                }
            }

            composeTestRule.waitForIdle()
            val root = composeTestRule.onRoot()
            root.onChildAt(0).assertTextEquals("10 widgets")
            root.onChildAt(1).assertTextEquals("1 widget")
            root.onChildAt(2).assertTextEquals("10 shortcuts")
            root.onChildAt(3).assertTextEquals("1 shortcut")
            root.onChildAt(4).assertTextEquals("1 widget, 1 shortcut")
            root.onChildAt(5).assertTextEquals("10 widgets, 10 shortcuts")
        }

    @Test
    fun expand_reportsCuiEventsForExpandAnimation() =
        testScope.runTest {
            composeTestRule.setContent {
                var expanded by remember { mutableStateOf(false) }
                CompositionLocalProvider(LocalWidgetPickerCuiReporter provides cuiReporterMock) {
                    ExpandableWidgetListHeaderTestContent(
                        expanded = expanded,
                        onClick = { expanded = !expanded },
                    )
                }
            }
            composeTestRule.waitForIdle()

            composeTestRule
                .onNode(hasText(TITLE))
                .assertExists()
                .assertHasClickAction()
                .performClick()
            composeTestRule.waitForIdle()

            composeTestRule.onNode(hasText(EXPANDED_CONTENT_TEXT)).assertExists()
            verify(cuiReporterMock, times(1))
                .report(eq(WidgetPickerCui.WIDGET_APP_EXPAND_BEGIN), any())
            verify(cuiReporterMock, times(1))
                .report(eq(WidgetPickerCui.WIDGET_APP_EXPAND_END), any())
        }

    @Test
    fun selectableHeader_canSelectAndRemainsSelected() {
        testScope.runTest {
            composeTestRule.setContent {
                var selected by remember { mutableStateOf(false) }

                SelectableWidgetListHeaderTestContent(
                    selected = selected,
                    onSelect = { selected = true },
                )
            }

            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasText(SELECTED_CONTENT_TEXT)).assertDoesNotExist()

            // not selected; is clickable; and click!
            composeTestRule
                .onNode(hasText(TITLE))
                .assertExists()
                .assert(hasText(SUB_TITLE))
                .assertIsNotSelected()
                .assertHasClickAction()
                .performClick()

            composeTestRule.waitForIdle()
            // shows selected content
            composeTestRule.onNode(hasText(SELECTED_CONTENT_TEXT)).assertExists()
            // Now selected and is still clickable; click again!
            composeTestRule
                .onNode(hasText(TITLE))
                .assertExists()
                .assert(hasText(SUB_TITLE))
                .assertIsSelected()
                .assertHasClickAction()
                .performClick()

            composeTestRule.waitForIdle()
            // This time on click nothing changes (i.e. this was an idempotent click)
            composeTestRule.onNode(hasText(SELECTED_CONTENT_TEXT)).assertExists()
            // still selected and clickable.
            composeTestRule
                .onNode(hasText(TITLE))
                .assertExists()
                .assert(hasText(SUB_TITLE))
                .assertIsSelected()
                .assertHasClickAction()
        }
    }

    @Test
    fun selectableHeader_titleTextHasAccessibilityPrefixWhenSet() {
        composeTestRule.setContent {
            SelectableWidgetListHeaderTestContent(
                selected = false,
                accessibilityPrefix = WORK_ACCESSIBILITY_PREFIX,
                onSelect = {},
            )
        }

        // accessibility prefix only on content description; not on visual content
        composeTestRule
            .onNode(hasText(TITLE), useUnmergedTree = true)
            .assertExists()
            .assert(hasText(WORK_ACCESSIBILITY_PREFIX).not())
            .assert(hasContentDescription("$WORK_ACCESSIBILITY_PREFIX $TITLE"))
    }

    @Test
    fun expandableHeader_titleTextHasAccessibilityPrefixWhenSet() {
        composeTestRule.setContent {
            ExpandableWidgetListHeaderTestContent(
                expanded = false,
                accessibilityPrefix = WORK_ACCESSIBILITY_PREFIX,
                onClick = {},
            )
        }

        // accessibility prefix only on content description; not on visual content
        composeTestRule
            .onNode(hasText(TITLE), useUnmergedTree = true)
            .assertExists()
            .assert(hasText(WORK_ACCESSIBILITY_PREFIX).not())
            .assert(hasContentDescription("$WORK_ACCESSIBILITY_PREFIX $TITLE"))
    }

    @Composable
    private fun ExpandableWidgetListHeaderTestContent(
        expanded: Boolean,
        accessibilityPrefix: String? = null,
        onClick: () -> Unit,
    ) {
        WidgetPickerTheme {
            ExpandableListHeader(
                modifier = Modifier.fillMaxWidth(),
                expanded = expanded,
                leadingAppIcon = {},
                title = TITLE,
                accessibilityPrefix = accessibilityPrefix,
                subTitle = SUB_TITLE,
                onClick = onClick,
                shape = RoundedCornerShape(28.dp),
                expandedContent = {
                    Box(modifier = Modifier.fillMaxWidth().wrapContentWidth()) {
                        Text(EXPANDED_CONTENT_TEXT)
                    }
                },
            )
        }
    }

    @Composable
    private fun SelectableWidgetListHeaderTestContent(
        selected: Boolean,
        accessibilityPrefix: String? = null,
        onSelect: () -> Unit,
    ) {
        WidgetPickerTheme {
            Row(modifier = Modifier.fillMaxWidth()) {
                SelectableListHeader(
                    modifier = Modifier.weight(1f),
                    selected = selected,
                    leadingAppIcon = {},
                    title = TITLE,
                    accessibilityPrefix = accessibilityPrefix,
                    subTitle = SUB_TITLE,
                    onSelect = onSelect,
                    shape = RoundedCornerShape(28.dp),
                )
                if (selected) {
                    Box(modifier = Modifier.wrapContentWidth()) { Text(SELECTED_CONTENT_TEXT) }
                }
            }
        }
    }

    companion object {
        private const val TITLE = "Title"
        private const val WORK_ACCESSIBILITY_PREFIX = "Work"
        private const val SUB_TITLE = "SubTitle"
        private const val EXPANDED_CONTENT_TEXT = "Expanded test content"
        private const val SELECTED_CONTENT_TEXT = "Selected test content"
    }
}
