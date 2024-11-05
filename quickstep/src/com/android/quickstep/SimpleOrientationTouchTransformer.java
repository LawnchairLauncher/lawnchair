/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.launcher3.util.DisplayController.CHANGE_ACTIVE_SCREEN;
import static com.android.launcher3.util.DisplayController.CHANGE_ALL;
import static com.android.launcher3.util.DisplayController.CHANGE_ROTATION;

import android.content.Context;
import android.view.MotionEvent;

import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SafeCloseable;

public class SimpleOrientationTouchTransformer implements
        DisplayController.DisplayInfoChangeListener, SafeCloseable {

    public static final MainThreadInitializedObject<SimpleOrientationTouchTransformer> INSTANCE =
            new MainThreadInitializedObject<>(SimpleOrientationTouchTransformer::new);

    private final Context mContext;
    private OrientationRectF mOrientationRectF;

    public SimpleOrientationTouchTransformer(Context context) {
        mContext = context;
        DisplayController.INSTANCE.get(context).addChangeListener(this);
        onDisplayInfoChanged(context, DisplayController.INSTANCE.get(context).getInfo(),
                CHANGE_ALL);
    }

    @Override
    public void close() {
        DisplayController.INSTANCE.get(mContext).removeChangeListener(this);
    }

    @Override
    public void onDisplayInfoChanged(Context context, DisplayController.Info info, int flags) {
        if ((flags & (CHANGE_ROTATION | CHANGE_ACTIVE_SCREEN)) == 0) {
            return;
        }
        mOrientationRectF = new OrientationRectF(0, 0, info.currentSize.y, info.currentSize.x,
                info.rotation);
    }

    public void transform(MotionEvent ev, int rotation) {
        mOrientationRectF.applyTransformToRotation(ev, rotation, true /* forceTransform */);
    }
}
