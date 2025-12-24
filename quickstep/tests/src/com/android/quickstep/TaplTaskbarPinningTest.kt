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

import android.platform.test.rule.AllowedDevices
import android.platform.test.rule.DeviceProduct
import com.android.launcher3.taskbar.customization.TaskbarFeatureEvaluator
import com.android.quickstep.TaskbarModeSwitchRule.TaskbarModeSwitch
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@AllowedDevices(allowed = [DeviceProduct.TANGORPRO, DeviceProduct.CF_TABLET])
class TaplTaskbarPinningTest : AbstractTaplTestsTaskbar() {

  private lateinit var taskbarFeatureEvaluator: TaskbarFeatureEvaluator

  override fun setUp() {
    super.setUp()
    taskbarFeatureEvaluator = TaskbarFeatureEvaluator.INSTANCE[mTargetContext]
  }

  override fun tearDown() {
    mLauncher.clearLauncherData()
    super.tearDown()
  }

  @Test
  @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.TRANSIENT)
  fun testPinningUnpinningTransientTaskbar_whenInOverview() {
    assertThat(taskbarFeatureEvaluator.isTransient).isTrue()
    mLauncher.goHome().switchToOverview()
    // Pinning
    taskbar.toggleAlwaysShowTaskbarOption()
    mLauncher.goHome()

    assertThat(taskbarFeatureEvaluator.isTransient).isFalse()
    assertThat(taskbarFeatureEvaluator.isPinned).isTrue()

    // unpinning
    mLauncher.goHome().switchToOverview()
    taskbar.toggleAlwaysShowTaskbarOption()
    mLauncher.goHome()

    assertThat(taskbarFeatureEvaluator.isTransient).isTrue()
    assertThat(taskbarFeatureEvaluator.isPinned).isFalse()
  }

  @Test
  @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.TRANSIENT)
  fun testPinningUnpinningTransientTaskbar_whenInApp() {
    startAppFast(TEST_APP_PACKAGE)
    assertThat(taskbarFeatureEvaluator.isTransient).isTrue()

    mLauncher.launchedAppState.assertTaskbarVisible()

    // Pinning
    taskbar.toggleAlwaysShowTaskbarOption()
    mLauncher.goHome()

    assertThat(taskbarFeatureEvaluator.isTransient).isFalse()
    assertThat(taskbarFeatureEvaluator.isPinned).isTrue()

    // unpinning
    startAppFast(TEST_APP_PACKAGE)
    taskbar.toggleAlwaysShowTaskbarOption()
    mLauncher.goHome()

    assertThat(taskbarFeatureEvaluator.isTransient).isTrue()
    assertThat(taskbarFeatureEvaluator.isPinned).isFalse()
  }

  @Test
  @TaskbarModeSwitch(mode = TaskbarModeSwitchRule.Mode.TRANSIENT)
  fun testPinningUnpinningTransientTaskbar_whenInDesktopMode() {
    startAppFast(CALCULATOR_APP_PACKAGE)
    assertThat(taskbarFeatureEvaluator.isTransient).isTrue()
    mLauncher.goHome().switchToOverview().currentTask.tapMenu().tapDesktopMenuItem()
    mLauncher.launchedAppState.assertTaskbarVisible()
    assertThat(taskbarFeatureEvaluator.isPinned).isTrue()

    // unpinning
    mLauncher.launchedAppState.taskbar.toggleAlwaysShowTaskbarOption()
    mLauncher.launchedAppState.assertTaskbarHidden()

    // pinning
    mLauncher.launchedAppState.hoverScreenBottomEdgeToUnstashTaskbar()
    mLauncher.launchedAppState.taskbar.toggleAlwaysShowTaskbarOption()
    mLauncher.launchedAppState.assertTaskbarVisible()
  }
}
