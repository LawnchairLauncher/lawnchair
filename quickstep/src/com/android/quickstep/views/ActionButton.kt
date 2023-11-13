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

package com.android.quickstep.views

import android.content.Context
import android.util.AttributeSet
import android.widget.Button

/**
 * A button on the Overview Actions Bar. Custom logic for hiding/showing each button type is handled
 * in the respective subclass.
 */
open class ActionButton : Button {
    private var mHiddenFlags = 0

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    /**
     * Updates the proper flags to indicate whether the button should be hidden.
     *
     * @param flag The flag to update.
     * @param enable Whether to enable the hidden flag: True will cause view to be hidden.
     */
    protected fun updateHiddenFlags(flag: Int, enable: Boolean) {
        if (enable) {
            mHiddenFlags = mHiddenFlags or flag
        } else {
            mHiddenFlags = mHiddenFlags and flag.inv()
        }
        val shouldBeVisible = mHiddenFlags == 0
        this.visibility = if (shouldBeVisible) VISIBLE else GONE
    }

    /** Show/hide the button when the focused task is a single/pair. */
    open fun updateForMultipleTasks(hasMultipleTasks: Boolean) {
        // overridden in subclass, or else don't do anything
    }

    /** Show/hide the button depending on if the device is a tablet. */
    open fun updateForTablet(isTablet: Boolean) {
        // overridden in subclass, or else don't do anything
    }
}
