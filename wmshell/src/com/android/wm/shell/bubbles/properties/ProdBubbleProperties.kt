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

package com.android.wm.shell.bubbles.properties

import android.os.SystemProperties
import com.android.wm.shell.Flags

/** Provides bubble properties in production. */
object ProdBubbleProperties : BubbleProperties {

    private var _isBubbleBarEnabled = Flags.enableBubbleBar() ||
            SystemProperties.getBoolean("persist.wm.debug.bubble_bar", false)

    override val isBubbleBarEnabled
        get() = _isBubbleBarEnabled

    override fun refresh() {
        _isBubbleBarEnabled = Flags.enableBubbleBar() ||
                SystemProperties.getBoolean("persist.wm.debug.bubble_bar", false)
    }
}
