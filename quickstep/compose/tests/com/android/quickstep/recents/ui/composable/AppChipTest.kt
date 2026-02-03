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
 *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.quickstep.recents.ui.composable

import android.graphics.drawable.Drawable
import androidx.activity.ComponentActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.helper.hasMinTouchArea
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppChipTest {
    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun AppChipForTest(expanded: Boolean, onClick: () -> Unit = {}) =
        AppChip(
            title = APP_NAME,
            icon =  getGoogleDrawable()!!,
            expanded = expanded,
            onClick = onClick,
        )

    @Test
    fun givenIsCollapsed_whenClicked_chipExpands() {
        composeTestRule.setContent {
            val (expanded, setExpanded) = remember { mutableStateOf(false) }
            AppChipForTest(expanded, onClick = { setExpanded(!expanded) })
        }
        composeTestRule
            .onNodeWithText(APP_NAME)
            .assertWidthIsEqualTo(156.dp)
            .assertHasClickAction()
            .performClick()
            .assertWidthIsEqualTo(216.dp)
    }

    // Accessibility Checks
    @Test
    fun checkAccessibility_contentDescription() {
        composeTestRule.setContent {
            val (expanded, setExpanded) = remember { mutableStateOf(false) }
            AppChipForTest(expanded, onClick = { setExpanded(!expanded) })
        }
        composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .assertAll(hasContentDescription(APP_NAME))
    }

    @Test
    fun checkAccessibility_touchTargetArea() {
        composeTestRule.setContent {
            val (expanded, setExpanded) = remember { mutableStateOf(false) }
            AppChipForTest(expanded, onClick = { setExpanded(!expanded) })
        }
        composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick))
            .assertAll(hasMinTouchArea())
    }

    private fun getGoogleDrawable(): Drawable? {
        val context = InstrumentationRegistry.getInstrumentation().context
        val identifier =
            context.resources.getIdentifier(
                APP_ICON,
                "drawable",
                InstrumentationRegistry.getInstrumentation().context.packageName,
            )
        return AppCompatResources.getDrawable(context, identifier)
    }

    private companion object {
        const val APP_NAME = "Very long name for the app that won't fit in the chip"
        const val APP_ICON = "ic_super_g_color"
    }
}
