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

package com.android.launcher3.icons

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.database.MatrixCursor
import android.os.Handler
import android.os.Process.myUserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.icons.cache.BaseIconCache
import com.android.launcher3.icons.cache.BaseIconCache.IconDB
import com.android.launcher3.icons.cache.CachedObject
import com.android.launcher3.icons.cache.CachedObjectCachingLogic
import com.android.launcher3.icons.cache.IconCacheUpdateHandler
import com.android.launcher3.util.RoboApiWrapper
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.FutureTask
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class IconCacheUpdateHandlerTest {

    @Mock private lateinit var iconProvider: IconProvider
    @Mock private lateinit var baseIconCache: BaseIconCache
    @Mock private lateinit var cacheDb: IconDB
    @Mock private lateinit var workerHandler: Handler

    @Captor private lateinit var deleteCaptor: ArgumentCaptor<String>

    private var cursor =
        MatrixCursor(
            arrayOf(
                BaseIconCache.COLUMN_ROWID,
                BaseIconCache.COLUMN_COMPONENT,
                BaseIconCache.COLUMN_FRESHNESS_ID,
            )
        )

    private lateinit var updateHandlerUnderTest: IconCacheUpdateHandler

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        doReturn(iconProvider).whenever(baseIconCache).iconProvider
        doReturn(cursor).whenever(cacheDb).query(any(), any(), any())

        updateHandlerUnderTest = IconCacheUpdateHandler(baseIconCache, cacheDb, workerHandler)
    }

    @After
    fun tearDown() {
        cursor?.close()
    }

    @Test
    fun `keeps correct icons irrespective of call order`() {
        val obj1 = TestCachedObject(1).apply { addToCursor(cursor) }
        val obj2 = TestCachedObject(2).apply { addToCursor(cursor) }

        updateHandlerUnderTest.updateIcons(obj1)
        updateHandlerUnderTest.updateIcons(obj2)
        updateHandlerUnderTest.finish()

        verify(cacheDb, never()).delete(any(), anyOrNull())
    }

    @Test
    fun `removes missing entries in single call`() {
        TestCachedObject(1).addToCursor(cursor)
        TestCachedObject(2).addToCursor(cursor)
        TestCachedObject(3).addToCursor(cursor)
        TestCachedObject(4).addToCursor(cursor)
        TestCachedObject(5).addToCursor(cursor)

        updateHandlerUnderTest.updateIcons(TestCachedObject(1), TestCachedObject(4))
        updateHandlerUnderTest.finish()

        verifyItemsDeleted(2, 3, 5)
    }

    @Test
    fun `removes missing entries in multiple calls`() {
        TestCachedObject(1).addToCursor(cursor)
        TestCachedObject(2).addToCursor(cursor)
        TestCachedObject(3).addToCursor(cursor)
        TestCachedObject(4).addToCursor(cursor)
        TestCachedObject(5).addToCursor(cursor)
        TestCachedObject(6).addToCursor(cursor)

        updateHandlerUnderTest.updateIcons(TestCachedObject(1), TestCachedObject(2))
        updateHandlerUnderTest.updateIcons(TestCachedObject(4), TestCachedObject(5))
        updateHandlerUnderTest.finish()

        verifyItemsDeleted(3, 6)
    }

    @Test
    fun `keeps valid app infos`() {
        val appInfo = ApplicationInfo()
        doReturn("app-fresh").whenever(iconProvider).getStateForApp(eq(appInfo))

        TestCachedObject(1).addToCursor(cursor)
        TestCachedObject(2).addToCursor(cursor)
        cursor.addRow(arrayOf(33, TestCachedObject(1).getPackageKey(), "app-fresh"))

        updateHandlerUnderTest.updateIcons(
            TestCachedObject(1, appInfo = appInfo),
            TestCachedObject(2),
        )
        updateHandlerUnderTest.finish()

        verify(cacheDb, never()).delete(any(), anyOrNull())
    }

    @Test
    fun `deletes stale app infos`() {
        val appInfo1 = ApplicationInfo()
        doReturn("app1-fresh").whenever(iconProvider).getStateForApp(eq(appInfo1))

        val appInfo2 = ApplicationInfo()
        doReturn("app2-fresh").whenever(iconProvider).getStateForApp(eq(appInfo2))

        TestCachedObject(1).addToCursor(cursor)
        TestCachedObject(2).addToCursor(cursor)
        cursor.addRow(arrayOf(33, TestCachedObject(1).getPackageKey(), "app1-not-fresh"))
        cursor.addRow(arrayOf(34, TestCachedObject(2).getPackageKey(), "app2-fresh"))

        updateHandlerUnderTest.updateIcons(
            TestCachedObject(1, appInfo = appInfo1),
            TestCachedObject(2, appInfo = appInfo2),
        )
        updateHandlerUnderTest.finish()

        verifyItemsDeleted(33)
    }

    @Test
    fun `updates stale entries`() {
        doAnswer { i ->
                (i.arguments[0] as Runnable).run()
                true
            }
            .whenever(workerHandler)
            .postAtTime(any(), anyOrNull(), any())

        TestCachedObject(1).addToCursor(cursor)
        TestCachedObject(2).addToCursor(cursor)
        TestCachedObject(3).addToCursor(cursor)

        var updatedPackages = mutableSetOf<String>()
        updateHandlerUnderTest.updateIcons(
            listOf(
                TestCachedObject(1, freshnessId = "not-fresh"),
                TestCachedObject(2, freshnessId = "not-fresh"),
                TestCachedObject(3),
            ),
            CachedObjectCachingLogic,
        ) { apps, _ ->
            updatedPackages.addAll(apps)
        }
        updateHandlerUnderTest.finish()

        assertThat(updatedPackages)
            .isEqualTo(
                mutableSetOf(TestCachedObject(1).cn.packageName, TestCachedObject(2).cn.packageName)
            )
    }

    private fun IconCacheUpdateHandler.updateIcons(vararg items: TestCachedObject) {
        updateIcons(items.toList(), CachedObjectCachingLogic) { _, _ -> }
    }

    private fun verifyItemsDeleted(vararg rowIds: Long) {
        verify(cacheDb, times(1)).delete(deleteCaptor.capture(), anyOrNull())
        val actual =
            deleteCaptor.value
                .split('(')
                ?.get(1)
                ?.split(')')
                ?.get(0)
                ?.split(",")
                ?.map { it.trim().toLong() }!!
                .sorted()
        assertThat(actual).isEqualTo(rowIds.toList().sorted())
    }
}

/** Utility method to wait for the icon update handler to finish */
fun IconCache.waitForUpdateHandlerToFinish() {
    var cacheUpdateInProgress = true
    while (cacheUpdateInProgress) {
        val cacheCheck = FutureTask {
            // Check for pending message on the worker thread itself as some task may be
            // running currently
            workerHandler.hasMessages(0, iconUpdateToken)
        }
        workerHandler.postDelayed(cacheCheck, 10)
        RoboApiWrapper.waitForLooperSync(workerHandler.looper)
        cacheUpdateInProgress = cacheCheck.get()
    }
}

class TestCachedObject(
    val rowId: Long,
    val cn: ComponentName =
        ComponentName.unflattenFromString("com.android.fake$rowId/.FakeActivity")!!,
    val freshnessId: String = "fresh-$rowId",
    val appInfo: ApplicationInfo? = null,
) : CachedObject {

    override fun getComponent() = cn

    override fun getUser() = myUserHandle()

    override fun getLabel(): CharSequence? = null

    override fun getApplicationInfo(): ApplicationInfo? = appInfo

    override fun getFreshnessIdentifier(iconProvider: IconProvider): String? = freshnessId

    fun addToCursor(cursor: MatrixCursor) =
        cursor.addRow(arrayOf(rowId, cn.flattenToString(), freshnessId))

    fun getPackageKey() =
        BaseIconCache.getPackageKey(cn.packageName, user).componentName.flattenToString()
}
