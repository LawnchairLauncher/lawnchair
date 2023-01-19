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

package com.android.systemui.shared.system;

import android.content.Intent;
import android.os.Bundle;
import android.view.SurfaceView;

/** Utility class that is shared between SysUI and Launcher for Universal Smartspace features. */
public final class UniversalSmartspaceUtils {
    public static final String ACTION_REQUEST_SMARTSPACE_VIEW =
            "com.android.systemui.REQUEST_SMARTSPACE_VIEW";
    public static final String INTENT_BUNDLE_KEY = "bundle_key";

    private static final String SYSUI_PACKAGE = "com.android.systemui";

    /** Creates an intent to request that sysui draws the Smartspace to the SurfaceView. */
    public static Intent createRequestSmartspaceIntent(SurfaceView surfaceView) {
        Intent intent = new Intent(ACTION_REQUEST_SMARTSPACE_VIEW);

        Bundle bundle = SurfaceViewRequestUtils.createSurfaceBundle(surfaceView);
        return intent
                .putExtra(INTENT_BUNDLE_KEY, bundle)
                .setPackage(SYSUI_PACKAGE)
                .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY
                        | Intent.FLAG_RECEIVER_FOREGROUND);
    }

    private UniversalSmartspaceUtils() {}
}
