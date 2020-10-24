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
package com.android.quickstep.interaction;

import android.content.Context;
import android.view.View;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.graphics.LauncherPreviewRenderer;

/** Renders a fake Launcher for use in the Sandbox. */
class SandboxLauncherRenderer extends LauncherPreviewRenderer {
    SandboxLauncherRenderer(Context context, InvariantDeviceProfile idp, boolean migrated) {
        super(context, idp, migrated);
    }

    @Override
    public boolean shouldShowRealLauncherPreview() {
        return false;
    }

    @Override
    public boolean shouldShowQsb() {
        return false;
    }

    @Override
    public View.OnLongClickListener getWorkspaceChildOnLongClickListener() {
        return null;
    }
}
