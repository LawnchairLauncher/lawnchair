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

package com.android.launcher3.util

import android.R
import android.content.Context
import android.graphics.Point
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.android.launcher3.CellLayout
import com.android.launcher3.CellLayoutContainer
import com.android.launcher3.DeviceProfile
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.MultipageCellLayout
import com.android.launcher3.allapps.ActivityAllAppsContainerView
import com.android.launcher3.dragndrop.DragController
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.views.BaseDragLayer
import com.android.launcher3.widget.picker.model.WidgetPickerDataProvider
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

private typealias AllAppsView = ActivityAllAppsContainerView<TestActivityContext>

/** [BaseContext] implementation for as a [TestRule] for easily managing cleanup */
class TestActivityContext
@JvmOverloads
constructor(
    base: Context = InstrumentationRegistry.getInstrumentation().targetContext,
    themeResId: Int = R.style.Theme_DeviceDefault,
) : BaseContext(base, themeResId, destroyOnDetach = false), TestRule {

    /** Store the full stacktrace so that any leak can be traced from a heap dump */
    val creationStack = Exception("TestActivityContext Creation stack").stackTraceToString()

    private val myDeviceProfile: DeviceProfile by lazy {
        InvariantDeviceProfile.INSTANCE.get(base).getDeviceProfile(base).copy()
    }
    private val myDragLayer: BaseDragLayer<TestActivityContext> by lazy { MyDragLayer(this) }

    private val myAppsView: AllAppsView by lazy {
        MAIN_EXECUTOR.submit<AllAppsView> { AllAppsView(this) }.get()
    }

    private val myWidgetPickerDataProvider = WidgetPickerDataProvider()

    override fun getDragLayer() = myDragLayer

    override fun getDeviceProfile() = myDeviceProfile

    override fun getAppsView() = myAppsView

    override fun getWidgetPickerDataProvider() = myWidgetPickerDataProvider

    /** Override required to allow spying */
    override fun getStatsLogManager() = super.getStatsLogManager()

    /** Override required to allow spying */
    override fun getAccessibilityDelegate() = super.getAccessibilityDelegate()

    /** Override required to allow spying */
    override fun <T : DragController<*>?> getDragController(): T = super.getDragController()

    /** Override required to allow spying */
    override fun getModelWriter() = super.getModelWriter()

    /** Create CellLayouts to be used in Unit testing by overriding grid properties */
    @JvmOverloads
    fun createCellLayout(
        columns: Int,
        rows: Int,
        isMulti: Boolean,
        width: Int = 1000,
        height: Int = 1000,
    ): CellLayout {
        if (applicationContext !is SandboxApplication) {
            throw IllegalStateException(
                "Grid modification should only happen for SandboxApplication as it can affect global state"
            )
        }
        val dp = getDeviceProfile()
        // modify the device profile.
        dp.inv.numColumns = if (isMulti) columns / 2 else columns
        dp.inv.numRows = rows
        dp.workspaceIconProfile =
            dp.workspaceIconProfile.copy(cellLayoutBorderSpacePx = Point(0, 0))
        val cl =
            if (isMulti) MultipageCellLayout(this)
            else
                CellLayout(
                    this,
                    object : CellLayoutContainer {
                        override fun getCellLayoutId(cellLayout: CellLayout): Int = 0

                        override fun getCellLayoutIndex(cellLayout: CellLayout): Int = 0

                        override fun getPanelCount(): Int = 1

                        override fun getPageDescription(pageIndex: Int): String = ""
                    },
                )
        // I put a very large number for width and height so that all the items can fit, it doesn't
        // need to be exact, just bigger than the sum of cell border
        cl.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        return cl
    }

    override fun apply(statement: Statement, description: Description): Statement {
        return object : ExternalResource() {
                override fun before() =
                    TestUtil.runOnExecutorSync(MAIN_EXECUTOR) { onViewCreated() }

                override fun after() =
                    TestUtil.runOnExecutorSync(MAIN_EXECUTOR) { onViewDestroyed() }
            }
            .apply(statement, description)
    }

    private class MyDragLayer(context: Context) :
        BaseDragLayer<TestActivityContext>(context, null, 1) {

        override fun recreateControllers() {
            super.recreateControllers()
            mControllers = arrayOfNulls(0)
        }
    }
}
