/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import com.android.launcher3.Launcher;
import com.android.quickstep.QuickScrubController;
import com.android.quickstep.views.RecentsView;

/**
 * Extension of overview state used for QuickScrub
 */
public class FastOverviewState extends OverviewState {

    private static final int STATE_FLAGS = FLAG_DISABLE_RESTORE | FLAG_DISABLE_INTERACTION
            | FLAG_OVERVIEW_UI | FLAG_HIDE_BACK_BUTTON | FLAG_DISABLE_ACCESSIBILITY;

    public FastOverviewState(int id) {
        super(id, QuickScrubController.QUICK_SCRUB_START_DURATION, STATE_FLAGS);
    }

    @Override
    public void onStateTransitionEnd(Launcher launcher) {
        super.onStateTransitionEnd(launcher);
        RecentsView recentsView = launcher.getOverviewPanel();
        recentsView.getQuickScrubController().onFinishedTransitionToQuickScrub();
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return NONE;
    }

    @Override
    public float[] getOverviewScaleAndTranslationYFactor(Launcher launcher) {
        return new float[] {1f, 0.5f};
    }
}
