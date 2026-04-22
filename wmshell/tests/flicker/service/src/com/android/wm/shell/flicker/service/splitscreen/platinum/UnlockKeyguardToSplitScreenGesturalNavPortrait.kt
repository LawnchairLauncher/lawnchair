/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.flicker.service.splitscreen.platinum

import android.platform.test.annotations.PlatinumTest
import android.platform.test.annotations.Presubmit
import com.android.wm.shell.flicker.service.splitscreen.scenarios.UnlockKeyguardToSplitScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

@RunWith(BlockJUnit4ClassRunner::class)
open class UnlockKeyguardToSplitScreenGesturalNavPortrait : UnlockKeyguardToSplitScreen() {
    @PlatinumTest(focusArea = "sysui")
    @Presubmit
    @Test
    override fun unlockKeyguardToSplitScreen() = super.unlockKeyguardToSplitScreen()
}
