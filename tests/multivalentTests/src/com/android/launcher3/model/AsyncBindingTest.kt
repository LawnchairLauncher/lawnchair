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
import android.util.SparseArray
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.Flags
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherModel
import com.android.launcher3.ModelCallbacks
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.pageindicators.PageIndicatorDots
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.IntSet
import com.android.launcher3.util.ItemInflater
import com.android.launcher3.util.LauncherLayoutBuilder
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.ModelTestExtensions.isPersistedModelItem
import com.android.launcher3.util.ModelTestExtensions.loadModelSync
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.android.launcher3.util.TestUtil.runOnExecutorSync
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Answers
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Tests to verify async binding of model views */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AsyncBindingTest {

    @get:Rule val mockitoRule = MockitoJUnit.rule()
    @get:Rule val context = SandboxApplication().withModelDependency()

    @Mock private lateinit var itemInflater: ItemInflater<*>
    // PageIndicatorDots need to be mocked separately as Workspace uses generics and doesn't define
    // the actual class of PageIndicator being used
    @Mock private lateinit var pageIndicatorDots: PageIndicatorDots
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private lateinit var launcher: Launcher

    private lateinit var callbacks: ModelCallbacks

    private val inflationLooper = SparseArray<Looper>()

    private val model: LauncherModel
        get() = context.appComponent.testableModelState.model

    @Before
    fun setUp() {
        doAnswer { i ->
                inflationLooper[(i.arguments[0] as ItemInfo).id] = Looper.myLooper()
                View(context)
            }
            .whenever(itemInflater)
            .inflateItem(any(), isNull(), any())

        doReturn(itemInflater).whenever(launcher).itemInflater
        doReturn(InvariantDeviceProfile.INSTANCE.get(context).getDeviceProfile(context))
            .whenever(launcher)
            .deviceProfile
        launcher.workspace.apply { doReturn(pageIndicatorDots).whenever(this).getPageIndicator() }
        doReturn(context).whenever(launcher).applicationContext

        // Set up the workspace with 3 pages of apps
        context.appComponent.layoutParserFactory.overrideXmlLayout(
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
                .build()
        )
        callbacks =
            spy(ModelCallbacks(launcher).apply { pagesToBindSynchronously = IntSet.wrap(0) })
        TestUtil.grantWriteSecurePermission()
    }

    @Test
    fun test_bind_inflates_item_on_background() {
        MAIN_EXECUTOR.execute { model.addCallbacksAndLoad(callbacks) }
        waitForLoaderAndTempMainThread()

        verify(callbacks, never()).bindItems(any(), any())
        // First 2 items were bound and eventually remaining items were bound
        verify(launcher, times(1))
            .bindInflatedItems(
                argThat { count { it.first.isPersistedModelItem() } == 2 },
                anyOrNull(),
            )
        verify(launcher, times(1))
            .bindInflatedItems(
                argThat { count { it.first.isPersistedModelItem() } == 3 },
                anyOrNull(),
            )

        // Verify that all items were inflated on the background thread
        assertThat(inflationLooper.size()).isAtLeast(5)
        for (i in 0..<inflationLooper.size()) assertNotEquals(
            MAIN_EXECUTOR.looper,
            inflationLooper.valueAt(i),
        )
    }

    @Test
    fun test_bind_sync_partially_inflates_on_background() {
        model.loadModelSync()
        assertTrue(model.isModelLoaded())

        val firstPageBindIds = mutableSetOf<Int>()
        runOnExecutorSync(MAIN_EXECUTOR) {
            model.addCallbacksAndLoad(callbacks)
            verify(callbacks, never()).bindItems(any(), any())
            verify(launcher, times(1))
                .bindInflatedItems(
                    argThat {
                        firstPageBindIds.addAll(map { it.first.id })
                        count { it.first.isPersistedModelItem() } == 2
                    },
                    anyOrNull(),
                )

            // Verify that onInitialBindComplete is called and the binding is not yet complete
            verify(launcher).bindComplete(any(), eq(true))

            if (Flags.simplifiedLauncherModelBinding()) {
                assertFalse(callbacks.activeBindTask.get().isCanceled)
            } else {
                assertNotNull(callbacks.pendingExecutor)
            }
            clearInvocations(launcher)
        }

        waitForLoaderAndTempMainThread()

        // Verify remaining 3 times are bound using pending tasks
        assertNull(callbacks.pendingExecutor)
        verify(launcher, times(1))
            .bindInflatedItems(
                argThat { count { it.first.isPersistedModelItem() } == 3 },
                anyOrNull(),
            )

        // Verify that firstPageBindIds are loaded on the main thread and remaining
        // on the background thread.
        assertThat(inflationLooper.size()).isAtLeast(5)
        for (i in 0..<inflationLooper.size()) {
            if (firstPageBindIds.contains(inflationLooper.keyAt(i)))
                assertEquals(MAIN_EXECUTOR.looper, inflationLooper.valueAt(i))
            else assertNotEquals(MAIN_EXECUTOR.looper, inflationLooper.valueAt(i))
        }
    }

    private fun waitForLoaderAndTempMainThread() {
        repeat(5) {
            runOnExecutorSync(MAIN_EXECUTOR) {}
            runOnExecutorSync(MODEL_EXECUTOR) {}
        }
    }
}
