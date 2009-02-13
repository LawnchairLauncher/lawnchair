/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.launcher;

import android.content.Context;
import android.gadget.GadgetHost;
import android.gadget.GadgetHostView;
import android.gadget.GadgetProviderInfo;

/**
 * Specific {@link GadgetHost} that creates our {@link LauncherGadgetHostView}
 * which correctly captures all long-press events. This ensures that users can
 * always pick up and move gadgets.
 */
public class LauncherGadgetHost extends GadgetHost {
    public LauncherGadgetHost(Context context, int hostId) {
        super(context, hostId);
    }
    
    protected GadgetHostView onCreateView(Context context, int gadgetId,
            GadgetProviderInfo gadget) {
        return new LauncherGadgetHostView(context);
    }
}
