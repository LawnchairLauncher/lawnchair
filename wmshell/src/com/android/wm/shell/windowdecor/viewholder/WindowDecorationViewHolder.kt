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
package com.android.wm.shell.windowdecor.viewholder

import android.app.ActivityManager.RunningTaskInfo
import android.view.View
import com.android.wm.shell.windowdecor.viewholder.WindowDecorationViewHolder.Data

/**
 * Encapsulates the root [View] of a window decoration and its children to facilitate looking up
 * children (via findViewById) and updating to the latest data from [RunningTaskInfo].
 */
abstract class WindowDecorationViewHolder<T : Data> : AutoCloseable {
    /** The root view of the window decoration. */
    abstract val rootView: View

    /**
     * A signal to the view holder that new data is available and that the views should be updated
     * to reflect it.
     */
    abstract fun bindData(data: T)

    /** Callback when the handle menu is opened. */
    abstract fun onHandleMenuOpened()

    /** Callback when the handle menu is closed. */
    abstract fun onHandleMenuClosed()

    /** Callback when the window decoration is destroyed. */
    abstract override fun close()

    /** Data clas that contains the information needed to update the view holder. */
    abstract class Data

    /** Sets task focused state. */
    abstract fun setTaskFocusState(taskFocusState: Boolean)

    /** Sets the view's top padding. */
    fun setTopPadding(topPadding: Int) {
        rootView.setPadding(
            rootView.paddingLeft,
            topPadding,
            rootView.paddingRight,
            rootView.paddingBottom,
        )
    }
}
