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

package com.android.launcher3.uioverrides;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Bundle;
import android.util.Size;
import android.view.View;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.graphics.LauncherPreviewRenderer;
import com.android.systemui.shared.system.SurfaceViewRequestReceiver;

/** Render preview using surface view. */
public class PreviewSurfaceRenderer {

    /** Handle a received surface view request. */
    public static void render(Context context, Bundle bundle) {
        final String gridName = bundle.getString("name");
        bundle.remove("name");
        final InvariantDeviceProfile idp = new InvariantDeviceProfile(context, gridName);

        MAIN_EXECUTOR.execute(() -> {
            View view = new LauncherPreviewRenderer(context, idp).getRenderedView();
            new SurfaceViewRequestReceiver().onReceive(context, bundle, view,
                    new Size(view.getMeasuredWidth(), view.getMeasuredHeight()));
        });
    }
}
