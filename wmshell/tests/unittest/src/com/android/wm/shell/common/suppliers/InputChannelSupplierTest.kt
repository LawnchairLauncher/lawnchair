/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.common.suppliers

import android.testing.AndroidTestingRunner
import android.view.InputChannel
import androidx.test.filters.SmallTest
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [InputChannelSupplier].
 *
 * Build/Install/Run:
 *  atest WMShellUnitTests:InputChannelSupplierTest
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class InputChannelSupplierTest {

    @Test
    fun `InputChannelSupplier supplies an InputChannel`() {
        val supplier = InputChannelSupplier()
        SuppliersUtilsTest.assertSupplierProvidesValue(supplier) {
            it is InputChannel
        }
    }
}
