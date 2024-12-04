/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.systemui.unfold.util

import android.view.View
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.jank.InteractionJankMonitor.CUJ_UNFOLD_ANIM
import com.android.systemui.unfold.UnfoldTransitionProgressProvider.TransitionProgressListener
import java.util.function.Supplier

class JankMonitorTransitionProgressListener(private val attachedViewProvider: Supplier<View>) :
    TransitionProgressListener {

    private val interactionJankMonitor = InteractionJankMonitor.getInstance()

    override fun onTransitionStarted() {
        interactionJankMonitor.begin(attachedViewProvider.get(), CUJ_UNFOLD_ANIM)
    }

    override fun onTransitionFinished() {
        interactionJankMonitor.end(CUJ_UNFOLD_ANIM)
    }
}
