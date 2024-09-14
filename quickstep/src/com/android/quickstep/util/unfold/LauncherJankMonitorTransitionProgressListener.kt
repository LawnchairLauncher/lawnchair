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
package com.android.quickstep.util.unfold

import android.view.View
import com.android.internal.jank.Cuj
import com.android.systemui.shared.system.InteractionJankMonitorWrapper
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import java.util.function.Supplier

/** Reports beginning and end of the unfold animation to interaction jank monitor */
class LauncherJankMonitorTransitionProgressListener(
    private val attachedViewProvider: Supplier<View>
) : TransitionProgressListener {

    override fun onTransitionStarted() {
        InteractionJankMonitorWrapper.begin(
            attachedViewProvider.get(),
            Cuj.CUJ_LAUNCHER_UNFOLD_ANIM
        )
    }

    override fun onTransitionFinished() {
        InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_UNFOLD_ANIM)
    }
}
