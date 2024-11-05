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

package com.android.launcher3.model

import android.os.Looper
import android.platform.test.flag.junit.SetFlagsRule
import android.util.Pair
import android.util.SparseArray
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.model.BgDataModel.Callbacks
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.IntArray
import com.android.launcher3.util.IntSet
import com.android.launcher3.util.ItemInflater
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.RunnableList
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.Spy
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests to verify async binding of model views */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AsyncBindingTest {

    @get:Rule val setFlagsRule = SetFlagsRule()

    @Spy private var callbacks = MyCallbacks()
    @Mock private lateinit var itemInflater: ItemInflater<*>

    private val inflationLooper = SparseArray<Looper>()

    private lateinit var modelHelper: LauncherModelHelper

    @Before
    fun setUp() {
        setFlagsRule.enableFlags(Flags.FLAG_ENABLE_WORKSPACE_INFLATION)
        MockitoAnnotations.initMocks(this)
        modelHelper = LauncherModelHelper()

        doAnswer { i ->
                inflationLooper[(i.arguments[0] as ItemInfo).id] = Looper.myLooper()
                View(modelHelper.sandboxContext)
            }
            .whenever(itemInflater)
            .inflateItem(any(), any(), isNull())

        // Set up the workspace with 3 pages of apps
        modelHelper.setupDefaultLayoutProvider(
            LauncherLayoutBuilder()
                .atWorkspace(0, 1, 0)
                .putApp(TEST_PACKAGE, TEST_PACKAGE)
                .atWorkspace(1, 1, 0)
                .putApp(TEST_PACKAGE, TEST_PACKAGE)
                .atWorkspace(0, 1, 1)
                .putApp(TEST_PACKAGE, TEST_PACKAGE)
                .atWorkspace(1, 1, 1)
                .putApp(TEST_PACKAGE, TEST_PACKAGE)
                .atWorkspace(0, 1, 2)
                .putApp(TEST_PACKAGE, TEST_PACKAGE)
        )
    }

    @After
    fun tearDown() {
        modelHelper.destroy()
    }

    @Test
    fun test_bind_normally_without_itemInflater() {
        MAIN_EXECUTOR.execute { modelHelper.model.addCallbacksAndLoad(callbacks) }
        waitForLoaderAndTempMainThread()

        verify(callbacks, never()).bindInflatedItems(any())
        verify(callbacks, atLeastOnce()).bindItems(any(), any())
    }

    @Test
    fun test_bind_inflates_item_on_background() {
        callbacks.inflater = itemInflater
        MAIN_EXECUTOR.execute { modelHelper.model.addCallbacksAndLoad(callbacks) }
        waitForLoaderAndTempMainThread()

        verify(callbacks, never()).bindItems(any(), any())
        verify(callbacks, times(1)).bindInflatedItems(argThat { t -> t.size == 2 })

        // Verify remaining items are bound using pendingTasks
        reset(callbacks)
        MAIN_EXECUTOR.submit(callbacks.pendingTasks!!::executeAllAndDestroy).get()
        verify(callbacks, times(1)).bindInflatedItems(argThat { t -> t.size == 3 })

        // Verify that all items were inflated on the background thread
        assertEquals(5, inflationLooper.size())
        for (i in 0..4) assertEquals(MODEL_EXECUTOR.looper, inflationLooper.valueAt(i))
    }

    @Test
    fun test_bind_sync_partially_inflates_on_background() {
        modelHelper.loadModelSync()
        assertTrue(modelHelper.model.isModelLoaded)
        callbacks.inflater = itemInflater

        val firstPageBindIds = IntSet()

        MAIN_EXECUTOR.submit {
                modelHelper.model.addCallbacksAndLoad(callbacks)
                verify(callbacks, never()).bindItems(any(), any())
                verify(callbacks, times(1))
                    .bindInflatedItems(
                        argThat { t ->
                            t.forEach { firstPageBindIds.add(it.first.id) }
                            t.size == 2
                        }
                    )

                // Verify that onInitialBindComplete is called and the binding is not yet complete
                assertFalse(callbacks.onCompleteSignal!!.isDestroyed)
            }
            .get()

        waitForLoaderAndTempMainThread()
        assertTrue(callbacks.onCompleteSignal!!.isDestroyed)

        // Verify that firstPageBindIds are loaded on the main thread and remaining
        // on the background thread.
        assertEquals(5, inflationLooper.size())
        for (i in 0..4) {
            if (firstPageBindIds.contains(inflationLooper.keyAt(i)))
                assertEquals(MAIN_EXECUTOR.looper, inflationLooper.valueAt(i))
            else assertEquals(MODEL_EXECUTOR.looper, inflationLooper.valueAt(i))
        }

        MAIN_EXECUTOR.submit {
                reset(callbacks)
                callbacks.pendingTasks!!.executeAllAndDestroy()
                // Verify remaining 3 times are bound using pending tasks
                verify(callbacks, times(1)).bindInflatedItems(argThat { t -> t.size == 3 })
            }
            .get()
    }

    private fun waitForLoaderAndTempMainThread() {
        MAIN_EXECUTOR.submit {}.get()
        MODEL_EXECUTOR.submit {}.get()
        MAIN_EXECUTOR.submit {}.get()
    }

    class MyCallbacks : Callbacks {

        var inflater: ItemInflater<*>? = null
        var pendingTasks: RunnableList? = null
        var onCompleteSignal: RunnableList? = null

        override fun bindItems(shortcuts: MutableList<ItemInfo>, forceAnimateIcons: Boolean) {}

        override fun bindInflatedItems(items: MutableList<Pair<ItemInfo, View>>) {}

        override fun getPagesToBindSynchronously(orderedScreenIds: IntArray?) = IntSet.wrap(0)

        override fun onInitialBindComplete(
            boundPages: IntSet,
            pendingTasks: RunnableList,
            onCompleteSignal: RunnableList,
            workspaceItemCount: Int,
            isBindSync: Boolean
        ) {
            this.pendingTasks = pendingTasks
            this.onCompleteSignal = onCompleteSignal
        }

        override fun getItemInflater() = inflater
    }
}
