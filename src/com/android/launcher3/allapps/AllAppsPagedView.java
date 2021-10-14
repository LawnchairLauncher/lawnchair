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
package com.android.launcher3.allapps;

import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_SWIPE_TO_PERSONAL_TAB;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_ALLAPPS_SWIPE_TO_WORK_TAB;

import android.content.Context;
import android.util.AttributeSet;

import com.android.launcher3.Launcher;
import com.android.launcher3.PagedView;
import com.android.launcher3.workprofile.PersonalWorkPagedView;

/**
 *  A {@link PagedView} for showing different views for the personal and work profile respectively
 *  in the {@link AllAppsContainerView}.
 */
public class AllAppsPagedView extends PersonalWorkPagedView {

    public AllAppsPagedView(Context context) {
        this(context, null);
    }

    public AllAppsPagedView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsPagedView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected boolean snapToPageWithVelocity(int whichPage, int velocity) {
        boolean resp = super.snapToPageWithVelocity(whichPage, velocity);
        if (resp && whichPage != mCurrentPage) {
            Launcher.getLauncher(getContext()).getStatsLogManager().logger()
                    .log(mCurrentPage < whichPage
                            ? LAUNCHER_ALLAPPS_SWIPE_TO_WORK_TAB
                            : LAUNCHER_ALLAPPS_SWIPE_TO_PERSONAL_TAB);
        }
        return resp;
    }
}
