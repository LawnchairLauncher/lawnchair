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
 * limitations under the License.
 */

package com.android.quickstep

import android.content.Context
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.AllModulesMinusWMProxy
import com.android.launcher3.util.TestDispatcherProvider
import com.android.launcher3.util.coroutines.DispatcherProvider
import com.android.quickstep.recents.data.AppTimersRepository
import com.android.quickstep.recents.data.FakeAppTimersRepository
import com.android.quickstep.recents.data.FakeRecentsDeviceProfileRepository
import com.android.quickstep.recents.data.FakeRecentsRotationStateRepository
import com.android.quickstep.recents.data.RecentsDeviceProfileRepository
import com.android.quickstep.recents.data.RecentsRotationStateRepository
import com.android.quickstep.recents.di.RecentsDependencies.Companion.maybeInitialize
import dagger.BindsInstance
import dagger.Component
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@LauncherAppSingleton
@Component(modules = [AllModulesMinusWMProxy::class])
interface TaskViewTestComponent : LauncherAppComponent {
    @Component.Builder
    interface Builder : LauncherAppComponent.Builder {
        @BindsInstance fun bindRecentsModel(recentsModel: RecentsModel): Builder

        override fun build(): TaskViewTestComponent
    }
}

object TaskViewTestDIHelpers {
    /** [context] is used as if it is RecentsView's context. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @JvmStatic
    fun initializeRecentsDependencies(context: Context) {
        val dp: DispatcherProvider = TestDispatcherProvider(UnconfinedTestDispatcher())
        val recentsDependencies = maybeInitialize(context, dp)
        recentsDependencies.createRecentsViewScope(context)
        recentsDependencies
            .getScope(context)[RecentsRotationStateRepository::class.java.simpleName] =
            FakeRecentsRotationStateRepository()
        recentsDependencies
            .getScope(context)[RecentsDeviceProfileRepository::class.java.simpleName] =
            FakeRecentsDeviceProfileRepository()
        recentsDependencies.getScope(context)[AppTimersRepository::class.java.simpleName] =
            FakeAppTimersRepository()
    }

    @JvmStatic
    fun mockRecentsModel(): RecentsModel {
        val recentsModel: RecentsModel = mock()
        val taskThumbnailCache: TaskThumbnailCache = mock()
        whenever(taskThumbnailCache.highResLoadingState).thenReturn(HighResLoadingState())
        whenever(recentsModel.thumbnailCache).thenReturn(taskThumbnailCache)
        whenever(recentsModel.iconCache).thenReturn(mock())
        return recentsModel
    }
}
