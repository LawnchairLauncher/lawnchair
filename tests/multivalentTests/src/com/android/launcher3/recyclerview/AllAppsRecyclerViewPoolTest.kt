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

package com.android.launcher3.recyclerview

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.util.Executors
import com.android.launcher3.views.ActivityContext
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(AndroidJUnit4::class)
class AllAppsRecyclerViewPoolTest<T> where T : Context, T : ActivityContext {

    private lateinit var underTest: AllAppsRecyclerViewPool<T>
    private lateinit var adapter: RecyclerView.Adapter<*>

    @Mock private lateinit var parent: RecyclerView
    @Mock private lateinit var itemView: View
    @Mock private lateinit var layoutManager: LayoutManager

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
        underTest = spy(AllAppsRecyclerViewPool())
        adapter =
            object : RecyclerView.Adapter<ViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                    object : ViewHolder(itemView) {}

                override fun getItemCount() = 0

                override fun onBindViewHolder(holder: ViewHolder, position: Int) {}
            }
        underTest.setMaxRecycledViews(VIEW_TYPE, 20)
        `when`(parent.layoutManager).thenReturn(layoutManager)
    }

    @Test
    fun preinflate_success() {
        underTest.preInflateAllAppsViewHolders(adapter, VIEW_TYPE, parent, 10) { 10 }

        awaitTasksCompleted()
        assertThat(underTest.getRecycledViewCount(VIEW_TYPE)).isEqualTo(10)
    }

    @Test
    fun preinflate_not_triggered() {
        underTest.preInflateAllAppsViewHolders(adapter, VIEW_TYPE, parent, 0) { 0 }

        awaitTasksCompleted()
        assertThat(underTest.getRecycledViewCount(VIEW_TYPE)).isEqualTo(0)
    }

    @Test
    fun preinflate_cancel_before_runOnMainThread() {
        underTest.preInflateAllAppsViewHolders(adapter, VIEW_TYPE, parent, 10) { 10 }
        assertThat(underTest.mCancellableTask!!.canceled).isFalse()

        underTest.clear()

        awaitTasksCompleted()
        verify(underTest, never()).putRecycledView(any(ViewHolder::class.java))
        assertThat(underTest.mCancellableTask!!.canceled).isTrue()
        assertThat(underTest.getRecycledViewCount(VIEW_TYPE)).isEqualTo(0)
    }

    @Test
    fun preinflate_cancel_after_run() {
        underTest.preInflateAllAppsViewHolders(adapter, VIEW_TYPE, parent, 10) { 10 }
        assertThat(underTest.mCancellableTask!!.canceled).isFalse()
        awaitTasksCompleted()

        underTest.clear()

        verify(underTest, times(10)).putRecycledView(any(ViewHolder::class.java))
        assertThat(underTest.mCancellableTask!!.canceled).isTrue()
        assertThat(underTest.getRecycledViewCount(VIEW_TYPE)).isEqualTo(0)
    }

    private fun awaitTasksCompleted() {
        Executors.VIEW_PREINFLATION_EXECUTOR.submit<Any> { null }.get()
        Executors.MAIN_EXECUTOR.submit<Any?> { null }.get()
    }

    companion object {
        private const val VIEW_TYPE: Int = 4
    }
}
