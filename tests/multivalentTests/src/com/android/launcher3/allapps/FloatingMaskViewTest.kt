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
package com.android.launcher3.allapps

import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.util.TestActivityContext
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit

@RunWith(AndroidJUnit4::class)
class FloatingMaskViewTest {

    @get:Rule val context = TestActivityContext()

    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @Mock private val mockAllAppsRecyclerView: AllAppsRecyclerView? = null

    @Mock private val mockBottomBox: ImageView? = null
    private var mVut: FloatingMaskView? = null

    @Before
    fun setUp() {
        mVut = FloatingMaskView(context)
        mVut!!.layoutParams =
            MarginLayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
    }

    @Test
    fun setParameters_paramsMarginEqualRecyclerViewPadding() {
        val floatingMaskView = Mockito.spy(mVut)
        Mockito.`when`(mockAllAppsRecyclerView!!.paddingLeft).thenReturn(PADDING_PX)
        Mockito.`when`(mockAllAppsRecyclerView.paddingRight).thenReturn(PADDING_PX)
        Mockito.`when`(mockAllAppsRecyclerView.paddingBottom).thenReturn(PADDING_PX)
        Mockito.`when`(floatingMaskView!!.bottomBox).thenReturn(mockBottomBox)
        val lp = floatingMaskView.layoutParams as MarginLayoutParams

        floatingMaskView.setParameters(lp, mockAllAppsRecyclerView)

        Truth.assertThat(lp.leftMargin).isEqualTo(PADDING_PX)
        Truth.assertThat(lp.rightMargin).isEqualTo(PADDING_PX)
        Mockito.verify(mockBottomBox)?.minimumHeight = PADDING_PX
    }

    companion object {
        private const val PADDING_PX = 15
    }
}
