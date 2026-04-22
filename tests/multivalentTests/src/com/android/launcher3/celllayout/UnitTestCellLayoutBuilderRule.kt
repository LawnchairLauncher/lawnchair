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

package com.android.launcher3.celllayout

import android.content.Context
import android.graphics.Point
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.android.launcher3.CellLayout
import com.android.launcher3.CellLayoutContainer
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.MultipageCellLayout
import com.android.launcher3.util.ActivityContextWrapper
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Create CellLayouts to be used in Unit testing and make sure to set the DeviceProfile back to
 * normal.
 */
class UnitTestCellLayoutBuilderRule : TestWatcher() {

    private var prevNumColumns = 0
    private var prevNumRows = 0

    private val applicationContext =
        ActivityContextWrapper(ApplicationProvider.getApplicationContext())

    private val container =
        object : CellLayoutContainer {
            override fun getCellLayoutId(cellLayout: CellLayout): Int = 0

            override fun getCellLayoutIndex(cellLayout: CellLayout): Int = 0

            override fun getPanelCount(): Int = 1

            override fun getPageDescription(pageIndex: Int): String = ""
        }

    override fun starting(description: Description?) {
        val dp = getDeviceProfile()
        prevNumColumns = dp.inv.numColumns
        prevNumRows = dp.inv.numRows
    }

    override fun finished(description: Description?) {
        val dp = getDeviceProfile()
        dp.inv.numColumns = prevNumColumns
        dp.inv.numRows = prevNumRows
    }

    fun createCellLayoutDefaultSize(columns: Int, rows: Int, isMulti: Boolean): CellLayout {
        return createCellLayout(columns, rows, isMulti)
    }

    fun createCellLayout(
        columns: Int,
        rows: Int,
        isMulti: Boolean,
        width: Int = 1000,
        height: Int = 1000
    ): CellLayout {
        val dp = getDeviceProfile()
        // modify the device profile.
        dp.inv.numColumns = if (isMulti) columns / 2 else columns
        dp.inv.numRows = rows
        dp.cellLayoutBorderSpacePx = Point(0, 0)
        val cl =
            if (isMulti) MultipageCellLayout(getWrappedContext(applicationContext, dp))
            else CellLayout(getWrappedContext(applicationContext, dp), container)
        // I put a very large number for width and height so that all the items can fit, it doesn't
        // need to be exact, just bigger than the sum of cell border
        cl.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        return cl
    }

    private fun getDeviceProfile(): DeviceProfile =
        InvariantDeviceProfile.INSTANCE[applicationContext].getDeviceProfile(applicationContext)
            .copy(applicationContext)

    private fun getWrappedContext(context: Context, dp: DeviceProfile): Context {
        return object : ActivityContextWrapper(context) {
            override fun getDeviceProfile(): DeviceProfile {
                return dp
            }
        }
    }
}
