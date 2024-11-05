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

package com.android.quickstep.taskbar.customization

import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator
import com.android.launcher3.taskbar.customization.TaskbarIconSpecs
import com.android.launcher3.taskbar.customization.TaskbarSpecsEvaluator
import com.android.launcher3.util.LauncherMultivalentJUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

@RunWith(LauncherMultivalentJUnit::class)
class TaskbarSpecsEvaluatorTest {

    private val taskbarFeatureEvaluator = mock<TaskbarFeatureEvaluator>()
    private val taskbarSpecsEvaluator = spy(TaskbarSpecsEvaluator(taskbarFeatureEvaluator))

    @Test
    fun testGetIconSizeByGrid_whenTaskbarIsTransient_withValidRowAndColumn() {
        doReturn(true).whenever(taskbarFeatureEvaluator).isTransient
        assertThat(taskbarSpecsEvaluator.getIconSizeByGrid(6, 5))
            .isEqualTo(TaskbarIconSpecs.iconSize52dp)
    }

    @Test
    fun testGetIconSizeByGrid_whenTaskbarIsTransient_withInvalidRowAndColumn() {
        doReturn(true).whenever(taskbarFeatureEvaluator).isTransient
        assertThat(taskbarSpecsEvaluator.getIconSizeByGrid(1, 2))
            .isEqualTo(TaskbarIconSpecs.defaultTransientIconSize)
    }

    @Test
    fun testGetIconSizeByGrid_whenTaskbarIsPersistent() {
        doReturn(false).whenever(taskbarFeatureEvaluator).isTransient
        assertThat(taskbarSpecsEvaluator.getIconSizeByGrid(6, 5))
            .isEqualTo(TaskbarIconSpecs.defaultPersistentIconSize)
    }

    @Test
    fun testGetIconSizeStepDown_whenTaskbarIsPersistent() {
        doReturn(false).whenever(taskbarFeatureEvaluator).isTransient
        assertThat(taskbarSpecsEvaluator.getIconSizeStepDown(TaskbarIconSpecs.iconSize44dp))
            .isEqualTo(TaskbarIconSpecs.defaultPersistentIconSize)
    }

    @Test
    fun testGetIconSizeStepDown_whenTaskbarIsTransientAndIconSizeAreInBound() {
        doReturn(true).whenever(taskbarFeatureEvaluator).isTransient
        assertThat(taskbarSpecsEvaluator.getIconSizeStepDown(TaskbarIconSpecs.iconSize52dp))
            .isEqualTo(TaskbarIconSpecs.iconSize48dp)
    }

    @Test
    fun testGetIconSizeStepDown_whenTaskbarIsTransientAndIconSizeAreOutOfBound() {
        doReturn(true).whenever(taskbarFeatureEvaluator).isTransient
        assertThat(taskbarSpecsEvaluator.getIconSizeStepDown(TaskbarIconSpecs.iconSize44dp))
            .isEqualTo(TaskbarIconSpecs.iconSize44dp)
    }

    @Test
    fun testGetIconSizeStepUp_whenTaskbarIsPersistent() {
        doReturn(false).whenever(taskbarFeatureEvaluator).isTransient
        assertThat(taskbarSpecsEvaluator.getIconSizeStepUp(TaskbarIconSpecs.iconSize40dp))
            .isEqualTo(TaskbarIconSpecs.iconSize40dp)
    }

    @Test
    fun testGetIconSizeStepUp_whenTaskbarIsTransientAndIconSizeAreInBound() {
        doReturn(true).whenever(taskbarFeatureEvaluator).isTransient
        assertThat(taskbarSpecsEvaluator.getIconSizeStepUp(TaskbarIconSpecs.iconSize44dp))
            .isEqualTo(TaskbarIconSpecs.iconSize48dp)
    }

    @Test
    fun testGetIconSizeStepUp_whenTaskbarIsTransientAndIconSizeAreOutOfBound() {
        doReturn(true).whenever(taskbarFeatureEvaluator).isTransient
        assertThat(taskbarSpecsEvaluator.getIconSizeStepUp(TaskbarIconSpecs.iconSize52dp))
            .isEqualTo(TaskbarIconSpecs.iconSize52dp)
    }
}
