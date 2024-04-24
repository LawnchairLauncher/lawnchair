/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.launcher3

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.UtilitiesKt.CLIP_CHILDREN_FALSE_MODIFIER
import com.android.launcher3.UtilitiesKt.CLIP_TO_PADDING_FALSE_MODIFIER
import com.android.launcher3.UtilitiesKt.modifyAttributesOnViewTree
import com.android.launcher3.UtilitiesKt.restoreAttributesOnViewTree
import com.android.launcher3.tests.R
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class UtilitiesKtTest {

    val context: Context = InstrumentationRegistry.getInstrumentation().context

    private lateinit var rootView: ViewGroup
    private lateinit var midView: ViewGroup
    private lateinit var childView: View
    @Before
    fun setup() {
        rootView =
            LayoutInflater.from(context).inflate(R.layout.utilities_test_view, null) as ViewGroup
        midView = rootView.requireViewById(R.id.mid_view)
        childView = rootView.requireViewById(R.id.child_view)
    }

    @Test
    fun set_clipChildren_false() {
        assertThat(rootView.clipChildren).isTrue()
        assertThat(midView.clipChildren).isTrue()

        modifyAttributesOnViewTree(childView, rootView, CLIP_CHILDREN_FALSE_MODIFIER)

        assertThat(rootView.clipChildren).isFalse()
        assertThat(midView.clipChildren).isFalse()
    }

    @Test
    fun restore_clipChildren_true() {
        assertThat(rootView.clipChildren).isTrue()
        assertThat(midView.clipChildren).isTrue()
        modifyAttributesOnViewTree(childView, rootView, CLIP_CHILDREN_FALSE_MODIFIER)
        assertThat(rootView.clipChildren).isFalse()
        assertThat(midView.clipChildren).isFalse()

        restoreAttributesOnViewTree(childView, rootView, CLIP_CHILDREN_FALSE_MODIFIER)

        assertThat(rootView.clipChildren).isTrue()
        assertThat(midView.clipChildren).isTrue()
    }

    @Test
    fun restore_clipChildren_skipRestoreMidView() {
        assertThat(rootView.clipChildren).isTrue()
        assertThat(midView.clipChildren).isTrue()
        rootView.clipChildren = false
        modifyAttributesOnViewTree(childView, rootView, CLIP_CHILDREN_FALSE_MODIFIER)
        assertThat(rootView.clipChildren).isFalse()
        assertThat(midView.clipChildren).isFalse()

        restoreAttributesOnViewTree(childView, rootView, CLIP_CHILDREN_FALSE_MODIFIER)

        assertThat(rootView.clipChildren).isFalse()
        assertThat(midView.clipChildren).isTrue()
    }

    @Test
    fun set_clipToPadding_false() {
        assertThat(rootView.clipToPadding).isTrue()
        assertThat(midView.clipToPadding).isTrue()

        modifyAttributesOnViewTree(childView, rootView, CLIP_TO_PADDING_FALSE_MODIFIER)

        assertThat(rootView.clipToPadding).isFalse()
        assertThat(midView.clipToPadding).isFalse()
    }

    @Test
    fun restore_clipToPadding_true() {
        assertThat(rootView.clipToPadding).isTrue()
        assertThat(midView.clipToPadding).isTrue()
        modifyAttributesOnViewTree(childView, rootView, CLIP_TO_PADDING_FALSE_MODIFIER)
        assertThat(rootView.clipToPadding).isFalse()
        assertThat(midView.clipToPadding).isFalse()

        restoreAttributesOnViewTree(childView, rootView, CLIP_TO_PADDING_FALSE_MODIFIER)

        assertThat(rootView.clipToPadding).isTrue()
        assertThat(midView.clipToPadding).isTrue()
    }
}
