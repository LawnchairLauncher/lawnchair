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

package com.android.wm.shell.compatui

import android.testing.AndroidTestingRunner
import androidx.test.filters.SmallTest
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.desktopmode.DesktopTestHelpers.Companion.createFreeformTask
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for {@link AppCompatUtils}.
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:AppCompatUtilsTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class AppCompatUtilsTest : ShellTestCase() {

    @Test
    fun testIsSingleTopActivityTranslucent() {
        assertTrue(isSingleTopActivityTranslucent(
            createFreeformTask(/* displayId */ 0)
                    .apply {
                        isTopActivityTransparent = true
                        numActivities = 1
                    }))
        assertFalse(isSingleTopActivityTranslucent(
            createFreeformTask(/* displayId */ 0)
                    .apply {
                        isTopActivityTransparent = true
                        numActivities = 0
                    }))
        assertFalse(isSingleTopActivityTranslucent(
            createFreeformTask(/* displayId */ 0)
                    .apply {
                        isTopActivityTransparent = false
                        numActivities = 1
                    }))
    }
}