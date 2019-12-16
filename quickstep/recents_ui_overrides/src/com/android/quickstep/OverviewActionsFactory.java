/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep;

import static com.android.launcher3.util.MainThreadInitializedObject.forOverride;

import android.view.View;

import com.android.launcher3.R;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.ResourceBasedOverride;

/**
 * Overview actions are shown in overview underneath the task snapshot. This factory class is
 * overrideable in an overlay. The {@link OverviewActions} class provides the view that should be
 * shown in the Overview.
 */
public class OverviewActionsFactory implements ResourceBasedOverride {

    public static final MainThreadInitializedObject<OverviewActionsFactory> INSTANCE =
            forOverride(OverviewActionsFactory.class, R.string.overview_actions_factory_class);

    /** Create a new Overview Actions for interacting between the actions and overview. */
    public OverviewActions createOverviewActions() {
        return new OverviewActions();
    }

    /** Overlay overrideable, base class does nothing. */
    public static class OverviewActions {
        /** Get the view to show in the overview. */
        public View getView() {
            return null;
        }
    }
}
