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
 * limitations under the License
 */

package com.android.launcher3.taskbar.navbutton

import android.content.res.Resources
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.taskbar.TaskbarActivityContext
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class AbstractNavButtonLayoutterTest {
    private val resources = mock<Resources>()
    private val navButtonContainer = mock<LinearLayout>()
    private val endContextualContainer = mock<ViewGroup>()
    private val startContextualContainer = mock<ViewGroup>()
    private val imeSwitcher = mock<ImageView>()
    private val a11yButton = mock<ImageView>()
    private val space = mock<Space>()

    private val backButton = mock<ImageView>()
    private val homeButton = mock<ImageView>()
    private val recentsButton = mock<ImageView>()

    @Test
    fun addThreeButtons_noFlip_ExpectedOrder() {
        val layoutter = TestLayoutter()

        layoutter.addThreeButtons()

        val captor = argumentCaptor<ImageView>()
        verify(navButtonContainer, times(3)).addView(captor.capture())

        assertThat(captor.allValues)
            .containsExactly(backButton, homeButton, recentsButton)
            .inOrder()
    }

    @Test
    fun addThreeButtons_flip_ExpectedOrder() {
        val layoutter = TestLayoutter()

        layoutter.flipOrder = true
        layoutter.addThreeButtons()

        val captor = argumentCaptor<ImageView>()
        verify(navButtonContainer, times(3)).addView(captor.capture())

        assertThat(captor.allValues)
            .containsExactly(recentsButton, homeButton, backButton)
            .inOrder()
    }

    inner class TestLayoutter :
        AbstractNavButtonLayoutter(
            resources,
            navButtonContainer,
            endContextualContainer,
            startContextualContainer,
            imeSwitcher,
            a11yButton,
            space,
            backButton,
            homeButton,
            recentsButton,
        ) {
        var flipOrder: Boolean = false

        override fun layoutButtons(
            context: TaskbarActivityContext,
            isA11yButtonPersistent: Boolean,
        ) {}

        override fun shouldFlipButtonOrder(): Boolean {
            return flipOrder
        }
    }
}
