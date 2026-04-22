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

package com.android.launcher3.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.test.annotation.UiThreadTest
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.same
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.verifyNoMoreInteractions

@SmallTest
@RunWith(LauncherMultivalentJUnit::class)
@UiThreadTest
class ViewPoolTest {

    @Mock private lateinit var viewParent: ViewGroup
    @Mock private lateinit var view: ReusableView
    @Mock private lateinit var inflater: LayoutInflater

    private lateinit var underTest: ViewPool<ReusableView>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        `when`(inflater.cloneInContext(any())).thenReturn(inflater)
        underTest = ViewPool(inflater, viewParent, LAYOUT_ID, 10, 0)
    }

    @Test
    fun get_view_from_empty_pool_inflate_new_view() {
        underTest.view

        verify(inflater).inflate(eq(LAYOUT_ID), same(viewParent), eq(false))
    }

    @Test
    fun recycle_view() {
        underTest.recycle(view)

        val returnedView = underTest.view

        verify(view).onRecycle()
        assertThat(returnedView).isSameInstanceAs(view)
        verifyNoMoreInteractions(inflater)
    }

    @Test
    fun get_view_twice_from_view_pool_with_one_view() {
        underTest.recycle(view)
        underTest.view
        verifyNoMoreInteractions(inflater)

        underTest.view

        verify(inflater).inflate(eq(LAYOUT_ID), same(viewParent), eq(false))
    }

    companion object {
        private const val LAYOUT_ID = 1000
    }

    private inner class ReusableView(context: Context) : View(context), ViewPool.Reusable {
        override fun onRecycle() {
            TODO("Not yet implemented")
        }
    }
}
