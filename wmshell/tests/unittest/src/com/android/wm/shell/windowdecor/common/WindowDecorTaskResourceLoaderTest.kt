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
package com.android.wm.shell.windowdecor.common

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.LocaleList
import android.os.Looper
import android.os.UserHandle
import android.testing.AndroidTestingRunner
import android.testing.TestableContext
import androidx.test.filters.SmallTest
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BaseIconFactory.Companion.MODE_DEFAULT
import com.android.launcher3.icons.IconProvider
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.common.UserProfileContexts
import com.android.wm.shell.sysui.ShellController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.sysui.UserChangeListener
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoaderImpl.AppResources
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

/**
 * Tests for [WindowDecorTaskResourceLoaderImpl].
 *
 * Build/Install/Run: atest WindowDecorTaskResourceLoaderTest
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(AndroidTestingRunner::class)
class WindowDecorTaskResourceLoaderTest : ShellTestCase() {
    private val testExecutor = TestShellExecutor()
    private val shellInit = ShellInit(testExecutor)
    private val mockShellController = mock<ShellController>()
    private val mockPackageManager = mock<PackageManager>()
    private val mockIconProvider = mock<IconProvider>()
    private val mockHeaderIconFactory = mock<BaseIconFactory>()
    private val mockVeilIconFactory = mock<BaseIconFactory>()
    private val mMockUserProfileContexts = mock<UserProfileContexts>()
    private val mockHandler = mock<Handler>()
    private val mockLooper = mock<Looper>()
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var spyContext: TestableContext
    private lateinit var loader: WindowDecorTaskResourceLoaderImpl

    private val userChangeListenerCaptor = argumentCaptor<UserChangeListener>()
    private val userChangeListener: UserChangeListener by lazy {
        userChangeListenerCaptor.firstValue
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(mockHandler.looper).thenReturn(mockLooper)
        whenever(mockLooper.isCurrentThread).thenReturn(true)

        spyContext = spy(mContext)
        spyContext.setMockPackageManager(mockPackageManager)
        doReturn(spyContext).whenever(spyContext).createContextAsUser(any(), anyInt())
        doReturn(spyContext).whenever(mMockUserProfileContexts)[anyInt()]
        doReturn(spyContext).whenever(mMockUserProfileContexts).getOrCreate(anyInt())
        loader =
            WindowDecorTaskResourceLoaderImpl(
                shellInit = shellInit,
                shellController = mockShellController,
                mainHandler = mockHandler,
                mainScope = testScope,
                mainDispatcher = testDispatcher,
                bgDispatcher = testDispatcher,
                shellCommandHandler = mock(),
                userProfilesContexts = mMockUserProfileContexts,
                iconProvider = mockIconProvider,
                headerIconFactory = mockHeaderIconFactory,
                veilIconFactory = mockVeilIconFactory,
            )
        shellInit.init()
        testExecutor.flushAll()
        verify(mockShellController).addUserChangeListener(userChangeListenerCaptor.capture())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testScope.cancel()
    }

    @Test
    fun testGetNameAndHeaderIcon_notCached_loadsResourceAndCaches() = runTest {
        val task = createTaskInfo(context.userId)
        loader.onWindowDecorCreated(task)

        loader.getNameAndHeaderIcon(task)
        advanceUntilIdle()

        verify(mockPackageManager).getApplicationLabel(task.topActivityInfo!!.applicationInfo)
        verify(mockHeaderIconFactory).createIconBitmap(any(), anyFloat(), anyInt(), anyBoolean())
        assertThat(loader.taskToResourceCache[task.taskId]?.appName).isNotNull()
        assertThat(loader.taskToResourceCache[task.taskId]?.appIcon).isNotNull()
    }

    @Test
    fun testGetNameAndHeaderIcon_cached_returnsFromCache() = runTest {
        val task = createTaskInfo(context.userId)
        task.configuration.setLocales(LocaleList(Locale.US))
        loader.onWindowDecorCreated(task)
        loader.taskToResourceCache[task.taskId] = AppResources("App Name", mock(), mock(), mock())
        loader.localeListOnCache[task.taskId] = LocaleList(Locale.US)

        loader.getNameAndHeaderIcon(task)

        verifyNoMoreInteractions(mockPackageManager, mockIconProvider, mockHeaderIconFactory)
    }

    @Test
    fun testGetNameAndHeaderIcon_cached_localesChanged_loadsResourceAndCaches() = runTest {
        val task = createTaskInfo(context.userId)
        loader.onWindowDecorCreated(task)
        loader.taskToResourceCache[task.taskId] = AppResources("App Name", mock(), mock(), mock())
        loader.localeListOnCache[task.taskId] = LocaleList(Locale.US, Locale.FRANCE)
        task.configuration.setLocales(LocaleList(Locale.FRANCE, Locale.US))
        doReturn("Le App Name").whenever(mockPackageManager).getApplicationLabel(any())

        val result = loader.getNameAndHeaderIcon(task)
        advanceUntilIdle()

        assertThat(result.first).isEqualTo("Le App Name")
        assertThat(loader.taskToResourceCache[task.taskId]?.appName).isEqualTo("Le App Name")
    }

    @Test
    fun testGetVeilIcon_notCached_loadsResourceAndCaches() = runTest {
        val task = createTaskInfo(context.userId)
        loader.onWindowDecorCreated(task)

        loader.getVeilIcon(task)

        verify(mockVeilIconFactory).createScaledBitmap(any(), anyInt())
        assertThat(loader.taskToResourceCache[task.taskId]?.veilIcon).isNotNull()
    }

    @Test
    fun testGetVeilIcon_cached_returnsFromCache() = runTest {
        val task = createTaskInfo(context.userId)
        loader.onWindowDecorCreated(task)
        loader.taskToResourceCache[task.taskId] = AppResources("App Name", mock(), mock(), mock())

        loader.getVeilIcon(task)

        verifyNoMoreInteractions(mockPackageManager, mockIconProvider, mockVeilIconFactory)
    }

    @Test
    fun testUserChange_clearsCache() = runTest {
        val newUser = 5000
        val newContext = mock<Context>()
        val task = createTaskInfo(context.userId)
        loader.onWindowDecorCreated(task)
        loader.getNameAndHeaderIcon(task)

        userChangeListener.onUserChanged(newUser, newContext)

        assertThat(loader.taskToResourceCache[task.taskId]?.appName).isNull()
        assertThat(loader.taskToResourceCache[task.taskId]?.appIcon).isNull()
    }

    @Test
    fun testGet_nonexistentDecor_throws() = runTest {
        val task = createTaskInfo(context.userId)

        assertFailsWith<Exception> { loader.getNameAndHeaderIcon(task) }
    }

    @Test
    fun testGet_nonexistentPackage_returnsDefaultAndDontCache() = runTest {
        val componentName = ComponentName("com.foo", "BarActivity")
        val appIconDrawable = mock<Drawable>()
        val task =
            TestRunningTaskInfoBuilder()
                .setUserId(context.userId)
                .setBaseIntent(Intent().apply { component = componentName })
                .build()
        loader.onWindowDecorCreated(task)
        doReturn(appIconDrawable).whenever(mockPackageManager).getDefaultActivityIcon()
        whenever(mockHeaderIconFactory.createIconBitmap(appIconDrawable, 1f)).thenReturn(mock())
        whenever(mockVeilIconFactory.createScaledBitmap(appIconDrawable, MODE_DEFAULT))
            .thenReturn(mock())
        doThrow(NameNotFoundException())
            .whenever(mockPackageManager)
            .getActivityInfo(eq(componentName), anyInt())

        loader.getVeilIcon(task)

        verify(mockVeilIconFactory).createScaledBitmap(appIconDrawable, MODE_DEFAULT)
        assertThat(loader.taskToResourceCache[task.taskId]).isNull()
    }

    private fun createTaskInfo(userId: Int): ActivityManager.RunningTaskInfo {
        val appIconDrawable = mock<Drawable>()
        val badgedAppIconDrawable = mock<Drawable>()
        val activityInfo = ActivityInfo().apply { applicationInfo = ApplicationInfo() }
        val componentName = ComponentName("com.foo", "BarActivity")
        whenever(mockPackageManager.getActivityInfo(eq(componentName), anyInt()))
            .thenReturn(activityInfo)
        whenever(mockPackageManager.getApplicationLabel(activityInfo.applicationInfo))
            .thenReturn("Test App")
        whenever(mockPackageManager.getUserBadgedIcon(appIconDrawable, UserHandle.of(userId)))
            .thenReturn(badgedAppIconDrawable)
        whenever(mockIconProvider.getIcon(activityInfo)).thenReturn(appIconDrawable)
        whenever(mockHeaderIconFactory.createIconBitmap(badgedAppIconDrawable, 1f))
            .thenReturn(mock())
        whenever(mockVeilIconFactory.createScaledBitmap(appIconDrawable, MODE_DEFAULT))
            .thenReturn(mock())
        return TestRunningTaskInfoBuilder()
            .setUserId(userId)
            .setBaseIntent(Intent().apply { component = componentName })
            .build()
            .apply { topActivityInfo = activityInfo }
    }
}
