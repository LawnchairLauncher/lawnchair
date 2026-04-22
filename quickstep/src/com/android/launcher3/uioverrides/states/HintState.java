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

import static com.android.launcher3.Flags.enableScalingRevealHomeAnimation;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;

import android.content.Context;
import android.graphics.Color;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimColors;

import app.lawnchair.theme.color.tokens.ColorTokens;

/**
 * Scale down workspace/hotseat to hint at going to either overview (on pause) or first home screen.
 */
public class HintState extends LauncherState {

    private static final int STATE_FLAGS = FLAG_WORKSPACE_INACCESSIBLE | FLAG_DISABLE_RESTORE
            | FLAG_HAS_SYS_UI_SCRIM;

    public static final float DEPTH_5_PERCENT = 0.05f;

    public HintState(int id) {
        this(id, LAUNCHER_STATE_HOME);
    }

    public HintState(int id, int statsLogOrdinal) {
        super(id, statsLogOrdinal, STATE_FLAGS);
    }

    @Override
    public int getTransitionDuration(ActivityContext context, boolean isToState) {
        return 80;
    }

    @Override
    protected float getDepthUnchecked(Context context) {
        if (enableScalingRevealHomeAnimation()) {
            return DEPTH_5_PERCENT;
        } else {
            return 0.15f;
        }
    }

    @Override
    public ScrimColors getWorkspaceScrimColor(Launcher launcher) {
//        ScrimColors overviewStateColor = OVERVIEW.getWorkspaceScrimColor(launcher);
        return new ScrimColors(
                /* backgroundColor */
                ColorUtils.setAlphaComponent(ColorTokens.OverviewScrim.resolveColor(launcher),
                        Math.round(Color.valueOf(ColorTokens.OverviewScrim.resolveColor(launcher)).alpha()
                                * 100)),
                /* foregroundColor */ Color.TRANSPARENT);
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        return new ScaleAndTranslation(0.92f, 0, 0);
    }
}
