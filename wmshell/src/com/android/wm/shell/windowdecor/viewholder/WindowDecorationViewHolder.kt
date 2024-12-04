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
import android.content.Context
import android.view.View

/**
 * Encapsulates the root [View] of a window decoration and its children to facilitate looking up
 * children (via findViewById) and updating to the latest data from [RunningTaskInfo].
 */
internal abstract class WindowDecorationViewHolder(rootView: View) {
  val context: Context = rootView.context

  /**
   * A signal to the view holder that new data is available and that the views should be updated to
   * reflect it.
   */
  abstract fun bindData(taskInfo: RunningTaskInfo)

  /** Callback when the handle menu is opened. */
  abstract fun onHandleMenuOpened()

  /** Callback when the handle menu is closed. */
  abstract fun onHandleMenuClosed()
}
