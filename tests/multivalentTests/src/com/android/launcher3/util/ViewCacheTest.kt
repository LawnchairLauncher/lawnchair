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

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.R
import com.android.launcher3.util.ViewCache.CacheEntry
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class ViewCacheTest {

    private lateinit var underTest: ViewCache

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val layoutId =
        context.run { resources.getIdentifier("test_layout_appwidget_blue", "layout", packageName) }

    @Before
    fun setUp() {
        underTest = ViewCache()
        underTest.setCacheSize(layoutId, 5)
    }

    @Test
    fun get_view_from_empty_cache() {
        val view: View = underTest.getView(layoutId, context, null)

        val cacheEntry = view.getTag(R.id.cache_entry_tag_id) as ViewCache.CacheEntry
        assertThat(cacheEntry).isNotNull()
        assertThat(cacheEntry.mMaxSize).isEqualTo(5)
        assertThat(cacheEntry.mCurrentSize).isEqualTo(0)
        assertThat(cacheEntry.mViews[0]).isNull()
    }

    @Test
    fun recyclerView() {
        val view: View = underTest.getView(layoutId, context, null)
        val cacheEntry = view.getTag(R.id.cache_entry_tag_id) as ViewCache.CacheEntry

        underTest.recycleView(layoutId, view)

        assertThat(cacheEntry.mMaxSize).isEqualTo(5)
        assertThat(cacheEntry.mCurrentSize).isEqualTo(1)
        assertThat(cacheEntry.mViews[0]).isSameInstanceAs(view)
    }

    @Test
    fun get_view_from_cache() {
        val view: View = underTest.getView(layoutId, context, null)
        underTest.recycleView(layoutId, view)

        val newView = underTest.getView<View>(layoutId, context, null)

        assertThat(view).isSameInstanceAs(newView)
    }

    @Test
    fun change_tag_id_recyclerView_noOp() {
        val view: View = underTest.getView(layoutId, context, null)
        val cacheEntry = view.getTag(R.id.cache_entry_tag_id) as ViewCache.CacheEntry

        view.setTag(R.id.cache_entry_tag_id, CacheEntry(3))
        underTest.recycleView(layoutId, view)

        assertThat(cacheEntry.mMaxSize).isEqualTo(5)
        assertThat(cacheEntry.mCurrentSize).isEqualTo(0)
        assertThat(cacheEntry.mViews[0]).isNull()
    }
}
