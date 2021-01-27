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
package com.android.launcher3.states;

import android.content.Context;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

/**
 * Scale down workspace/hotseat to hint at going to either overview (on pause) or first home screen.
 */
public class HintState extends LauncherState {

    private static final int STATE_FLAGS = FLAG_WORKSPACE_INACCESSIBLE | FLAG_DISABLE_RESTORE
            | FLAG_HAS_SYS_UI_SCRIM;

    public HintState(int id) {
        super(id, ContainerType.DEFAULT_CONTAINERTYPE, STATE_FLAGS);
    }

    @Override
    public int getTransitionDuration(Context context) {
        return 80;
    }

    @Override
    protected float getDepthUnchecked(Context context) {
        return 0.15f;
    }

    @Override
    public float getOverviewScrimAlpha(Launcher launcher) {
        return 0.4f;
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        return new ScaleAndTranslation(0.92f, 0, 0);
    }

    @Override
    public ScaleAndTranslation getQsbScaleAndTranslation(Launcher launcher) {
        // Treat the QSB as part of the hotseat so they move together.
        return getHotseatScaleAndTranslation(launcher);
    }
}
