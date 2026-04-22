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

package com.android.launcher3.model

import android.app.prediction.AppTarget
import android.app.prediction.AppTargetId
import android.os.Process.myUserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.cache.CacheLookupFlag.Companion.DEFAULT_LOOKUP_FLAG
import com.android.launcher3.util.AllModulesForTest
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY2
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import dagger.Component
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class PredictionUpdateTaskTest {

    @get:Rule val context = SandboxApplication().withModelDependency()

    private val containerId = -300
    private val predictorState = PredictorState(containerId, "test-storage", DEFAULT_LOOKUP_FLAG)

    lateinit var component: TestComponent

    @Before
    fun setup() {
        context.initDaggerComponent(DaggerPredictionUpdateTaskTest_TestComponent.builder())
        component = context.appComponent as TestComponent
    }

    @Test
    fun emptyPredictions_update_data_model() {
        assertThat(component.getDataModel().itemsIdMap[containerId]).isNull()

        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            PredictionUpdateTask(predictorState, listOf())
                .execute(
                    component.getTaskController(),
                    component.getDataModel(),
                    component.getAllAppsList(),
                )
        }
        assertThat(component.getDataModel().itemsIdMap[containerId]).isNotNull()
        assertThat(component.getDataModel().itemsIdMap.getPredictedContents(containerId)).isEmpty()
    }

    @Test
    fun validPredictions_added_to_data_model() {
        assertThat(component.getDataModel().itemsIdMap[containerId]).isNull()

        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            PredictionUpdateTask(
                    predictorState,
                    listOf(createAppTarget(TEST_ACTIVITY), createAppTarget(TEST_ACTIVITY2)),
                )
                .execute(
                    component.getTaskController(),
                    component.getDataModel(),
                    component.getAllAppsList(),
                )
        }
        assertThat(component.getDataModel().itemsIdMap[containerId]).isNotNull()
        val items = component.getDataModel().itemsIdMap.getPredictedContents(containerId)
        assertThat(items).hasSize(2)
    }

    private fun createAppTarget(className: String): AppTarget =
        AppTarget.Builder(AppTargetId("app:$className"), TEST_PACKAGE, myUserHandle())
            .setClassName(className)
            .build()

    @LauncherAppSingleton
    @Component(modules = [AllModulesForTest::class])
    interface TestComponent : LauncherAppComponent {

        fun getDataModel(): BgDataModel

        fun getAllAppsList(): AllAppsList

        fun getTaskController(): ModelTaskController

        @Component.Builder
        interface Builder : LauncherAppComponent.Builder {

            override fun build(): TestComponent
        }
    }
}
