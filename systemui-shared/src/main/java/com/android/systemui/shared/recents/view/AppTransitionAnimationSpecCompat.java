/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.systemui.shared.recents.view;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.AppTransitionAnimationSpec;

/**
 * Wraps the internal app transition animation spec.
 */
public class AppTransitionAnimationSpecCompat {

    private int mTaskId;
    private Bitmap mBuffer;
    private Rect mRect;

    public AppTransitionAnimationSpecCompat(int taskId, Bitmap buffer, Rect rect) {
        mTaskId = taskId;
        mBuffer = buffer;
        mRect = rect;
    }

    public AppTransitionAnimationSpec toAppTransitionAnimationSpec() {
        return new AppTransitionAnimationSpec(mTaskId,
                mBuffer != null ? mBuffer.createGraphicBufferHandle() : null, mRect);
    }
}
