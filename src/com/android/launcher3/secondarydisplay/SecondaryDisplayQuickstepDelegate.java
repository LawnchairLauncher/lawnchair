/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.launcher3.secondarydisplay;

import android.content.Context;

import com.android.launcher3.R;
import com.android.launcher3.model.data.PredictedContainerInfo;
import com.android.launcher3.util.ResourceBasedOverride;

/**
 * Exposes Quickstep-specific APIs to {@link SecondaryDisplayLauncher}.
 */
public class SecondaryDisplayQuickstepDelegate implements ResourceBasedOverride {
    /**
     * Creates a {@link SecondaryDisplayQuickstepDelegate} instance.
     */
    static SecondaryDisplayQuickstepDelegate newInstance(Context context) {
        return Overrides.getObject(
                SecondaryDisplayQuickstepDelegate.class, context,
                R.string.secondary_display_quickstep_delegate_class);
    }

    /**
     * Setup/update app divider separating app predictions from All Apps.
     */
    void updateAppDivider() {
    }

    /**
     * Set predicted apps in top of app drawer.
     */
    public void setPredictedApps(PredictedContainerInfo item) {
    }

    boolean enableTaskbarConnectedDisplays() {
        return false;
    }
}
