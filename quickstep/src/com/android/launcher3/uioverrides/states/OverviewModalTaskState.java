/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.uioverrides.states;

import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_OVERVIEW;

import android.content.Context;
import android.graphics.Rect;

import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.quickstep.views.RecentsView;

/**
 * An Overview state that shows the current task in a modal fashion. Modal state is where the
 * current task is shown on its own without other tasks visible.
 */
public class OverviewModalTaskState extends OverviewState {

    private static final int STATE_FLAGS =
            FLAG_DISABLE_RESTORE | FLAG_RECENTS_VIEW_VISIBLE | FLAG_WORKSPACE_INACCESSIBLE;

    public OverviewModalTaskState(int id) {
        super(id, LAUNCHER_STATE_OVERVIEW, STATE_FLAGS);
    }

    @Override
    public int getTransitionDuration(Context launcher, boolean isToState) {
        return 300;
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return OVERVIEW_ACTIONS | CLEAR_ALL_BUTTON;
    }

    @Override
    public float[] getOverviewScaleAndOffset(Launcher launcher) {
        return getOverviewScaleAndOffsetForModalState(launcher.getOverviewPanel());
    }

    @Override
    public float getOverviewModalness() {
        return 1.0f;
    }

    @Override
    public void onBackInvoked(Launcher launcher) {
        launcher.getStateManager().goToState(LauncherState.OVERVIEW);
    }

    @Override
    public boolean isTaskbarStashed(Launcher launcher) {
        if (Flags.enableGridOnlyOverview()) {
            return true;
        }
        return super.isTaskbarStashed(launcher);
    }

    public static float[] getOverviewScaleAndOffsetForModalState(RecentsView recentsView) {
        Rect taskSize = recentsView.getSelectedTaskBounds();
        Rect modalTaskSize = new Rect();
        recentsView.getModalTaskSize(modalTaskSize);

        float scale = Math.min((float) modalTaskSize.height() / taskSize.height(),
                (float) modalTaskSize.width() / taskSize.width());

        return new float[] {scale, NO_OFFSET};
    }
}
