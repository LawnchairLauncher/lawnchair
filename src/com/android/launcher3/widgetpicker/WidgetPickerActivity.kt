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

package com.android.launcher3.widgetpicker

import android.os.Bundle
import com.android.launcher3.BaseActivity
import com.android.launcher3.Flags
import com.android.launcher3.R
import com.android.launcher3.compose.ComposeFacade.isComposeAvailable
import com.android.launcher3.dagger.LauncherComponentProvider
import com.android.launcher3.dragndrop.SimpleDragLayer
import com.android.launcher3.util.ScreenOnTracker

/**
 * Activity that shows widget picker UI; shows content only if `enableWidgetPickerRefactor` flag is
 * on and compose is available.
 */
open class WidgetPickerActivity : BaseActivity() {
    private var _dragLayer: SimpleDragLayer<WidgetPickerActivity>? = null
    protected var widgetPickerConfig: WidgetPickerConfig = WidgetPickerConfig()

    private val screenOnListener =
        ScreenOnTracker.ScreenOnListener { on: Boolean -> this.onScreenOnChange(on) }

    override fun getDragLayer(): SimpleDragLayer<WidgetPickerActivity>? = _dragLayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val component = LauncherComponentProvider.get(this)
        component.screenOnTracker.addListener(screenOnListener)
        mDeviceProfile = component.idp.getDeviceProfile(this)

        setContentView(R.layout.widget_picker_activity)
        _dragLayer = findViewById(R.id.drag_layer)
        checkNotNull(_dragLayer).recreateControllers()

        if (Flags.enableWidgetPickerRefactor() && isComposeAvailable()) {
            component.widgetPickerComposeWrapper.showAllWidgets(this, widgetPickerConfig)
        }
    }

    private fun onScreenOnChange(on: Boolean) {
        if (!on) {
            finish() // auto close when user locks device.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ScreenOnTracker.INSTANCE[this].removeListener(screenOnListener)
    }
}