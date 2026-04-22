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

import android.platform.test.rule.AllowedDevices
import android.platform.test.rule.DeviceProduct
import android.platform.test.rule.LimitDevicesRule
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.widgetpicker.TestUtils
import com.android.launcher3.widgetpicker.TestUtils.PERSONAL_TEST_APPS
import com.android.launcher3.widgetpicker.ui.WidgetInteractionInfo
import com.android.launcher3.widgetpicker.ui.model.WidgetSizeGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for widget interactions e.g. tap to add. */
@RunWith(AndroidJUnit4::class)
@AllowedDevices(allowed = [DeviceProduct.CF_PHONE])
class WidgetInteractionsTest {
    @get:Rule val limitDevicesRule = LimitDevicesRule()

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tapPreview_andClickAdd() {
        composeTestRule.setContent { TapToAddTestComposable() }

        composeTestRule.waitForIdle()

        // tap on preview for widget 1
        composeTestRule
            .onAllNodesWithTag(PREVIEW_TEST_TAG)
            .assertCountEquals(2)
            .onFirst()
            .performClick()

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(WIDGET_ONE.label).isNotDisplayed() // label text not shown
        composeTestRule.onAllNodesWithText(ADD_BUTTON_TEXT).assertCountEquals(1)
        composeTestRule
            .onNodeWithContentDescription(WIDGET_ONE_ADD_BUTTON_CONTENT_DESC)
            .assertExists()
            .performClick()

        composeTestRule.waitForIdle()

        // widget interaction callback invoked and correct provider info returned.
        composeTestRule.onNodeWithText(WIDGET_ONE.widgetInfo.toString()).assertExists()

        // tap again on preview for widget 1
        composeTestRule
            .onAllNodesWithTag(PREVIEW_TEST_TAG)
            .assertCountEquals(2)
            .onFirst()
            .performClick()

        // No add button
        composeTestRule.onAllNodesWithText(ADD_BUTTON_TEXT).assertCountEquals(0)
        // label text shown
        composeTestRule.onNodeWithText(WIDGET_ONE.label).isDisplayed()
    }

    @Test
    fun tapPreview_togglesAddButton_onlyOneShownAtATime() {
        composeTestRule.setContent { TapToAddTestComposable() }

        composeTestRule.waitForIdle()

        // tap on preview for widget 1
        composeTestRule
            .onAllNodesWithTag(PREVIEW_TEST_TAG)
            .assertCountEquals(2)
            .onFirst()
            .performClick()

        composeTestRule.waitForIdle()

        // widget 1 - label text not shown, widget 2 - label still shown
        composeTestRule.onNodeWithText(WIDGET_ONE.label).isNotDisplayed()
        composeTestRule.onNodeWithText(WIDGET_TWO.label).isNotDisplayed()
        composeTestRule.onAllNodesWithText(ADD_BUTTON_TEXT).assertCountEquals(1)
        composeTestRule
            .onNodeWithContentDescription(WIDGET_ONE_ADD_BUTTON_CONTENT_DESC)
            .assertExists()

        // tap on preview for widget 2
        composeTestRule
            .onAllNodesWithTag(PREVIEW_TEST_TAG)
            .assertCountEquals(2)
            .onLast()
            .performClick()

        composeTestRule.waitForIdle()

        // now, widget 1 - label text shown & widget 2 - label not shown
        composeTestRule.onNodeWithText(WIDGET_ONE.label).isDisplayed()
        composeTestRule.onNodeWithText(WIDGET_TWO.label).isNotDisplayed()
        composeTestRule.onAllNodesWithText(ADD_BUTTON_TEXT).assertCountEquals(1)
        composeTestRule
            .onNodeWithContentDescription(WIDGET_TWO_ADD_BUTTON_CONTENT_DESC)
            .assertExists()
    }

    @Composable
    fun TapToAddTestComposable() {
        var provider by remember { mutableStateOf("invalid") }

        Column(modifier = Modifier.fillMaxSize()) {
            Text(provider)
            WidgetsGrid(
                widgetSizeGroups = listOf(TEST_WIDGET_GROUP),
                showAllWidgetDetails = false,
                previews = PREVIEWS,
                modifier = Modifier.weight(1f),
                appIcons = emptyMap(),
                showDragShadow = false,
                onWidgetInteraction = { widgetInteractionInfo ->
                    if (widgetInteractionInfo is WidgetInteractionInfo.WidgetAddInfo) {
                        provider = widgetInteractionInfo.widgetInfo.toString()
                    }
                },
            )
        }
    }

    companion object {
        private val WIDGET_ONE = PERSONAL_TEST_APPS[0].widgets[0]
        private val WIDGET_TWO = PERSONAL_TEST_APPS[1].widgets[0]

        private val TEST_WIDGET_GROUP =
            WidgetSizeGroup(
                previewContainerHeightPx = 200,
                previewContainerWidthPx = 200,
                widgets = listOf(WIDGET_ONE, WIDGET_TWO),
            )

        private val PREVIEWS =
            mapOf(
                WIDGET_ONE.id to TestUtils.createBitmapPreview(),
                WIDGET_TWO.id to TestUtils.createBitmapPreview(),
            )

        private val PREVIEW_TEST_TAG = buildWidgetPickerTestTag("widget_preview")
        private const val ADD_BUTTON_TEXT = "Add"
        private val WIDGET_ONE_ADD_BUTTON_CONTENT_DESC = "Add ${WIDGET_ONE.label} widget"
        private val WIDGET_TWO_ADD_BUTTON_CONTENT_DESC = "Add ${WIDGET_TWO.label} widget"
    }
}
