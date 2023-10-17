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

package com.android.launcher3.taskbar

import android.content.Context
import com.android.launcher3.R
import com.android.launcher3.util.ResourceBasedOverride
import com.android.launcher3.util.ResourceBasedOverride.Overrides

/** Creates [TaskbarModelCallbacks] instances. */
open class TaskbarModelCallbacksFactory : ResourceBasedOverride {

    open fun create(
        activityContext: TaskbarActivityContext,
        container: TaskbarView,
    ): TaskbarModelCallbacks = TaskbarModelCallbacks(activityContext, container)

    companion object {
        @JvmStatic
        fun newInstance(context: Context): TaskbarModelCallbacksFactory {
            return Overrides.getObject(
                TaskbarModelCallbacksFactory::class.java,
                context,
                R.string.taskbar_model_callbacks_factory_class,
            )
        }
    }
}
