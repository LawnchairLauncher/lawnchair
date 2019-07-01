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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import android.app.Activity;
import android.view.View;
import android.view.ViewHierarchyEncoder;

import java.io.ByteArrayOutputStream;

public class ActivityCompat {
    private final Activity mWrapped;

    public ActivityCompat(Activity activity) {
        mWrapped = activity;
    }

    /**
     * @see Activity#registerRemoteAnimations
     */
    public void registerRemoteAnimations(RemoteAnimationDefinitionCompat definition) {
        mWrapped.registerRemoteAnimations(definition.getWrapped());
    }

    /**
     * @see android.view.ViewDebug#dumpv2(View, ByteArrayOutputStream)
     */
    public boolean encodeViewHierarchy(ByteArrayOutputStream out) {
        View view = null;
        if (mWrapped.getWindow() != null &&
                mWrapped.getWindow().peekDecorView() != null &&
                mWrapped.getWindow().peekDecorView().getViewRootImpl() != null) {
            view = mWrapped.getWindow().peekDecorView().getViewRootImpl().getView();
        }
        if (view == null) {
            return false;
        }

        final ViewHierarchyEncoder encoder = new ViewHierarchyEncoder(out);
        int[] location = view.getLocationOnScreen();
        encoder.addProperty("window:left", location[0]);
        encoder.addProperty("window:top", location[1]);
        view.encode(encoder);
        encoder.endStream();
        return true;
    }
}
