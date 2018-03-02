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

/**
 * Extension of overview state used for QuickScrub
 */
public class FastOverviewState extends OverviewState {

    private static final int STATE_FLAGS = FLAG_SHOW_SCRIM | FLAG_DISABLE_RESTORE
            | FLAG_PAGE_BACKGROUNDS | FLAG_DISABLE_INTERACTION | FLAG_OVERVIEW_UI;

    private static final boolean DEBUG_DIFFERENT_UI = false;

    public FastOverviewState(int id) {
        super(id, STATE_FLAGS);
    }

    @Override
    public float getHoseatAlpha(Launcher launcher) {
        if (DEBUG_DIFFERENT_UI) {
            return 0;
        }
        return super.getHoseatAlpha(launcher);
    }
}
