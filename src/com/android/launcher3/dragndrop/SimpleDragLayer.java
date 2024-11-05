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

package com.android.launcher3.dragndrop;

import android.content.Context;
import android.util.AttributeSet;

import com.android.launcher3.util.TouchController;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

/**
 * A concrete {@link BaseDragLayer} that creates an empty list of {@link TouchController}s.
 * @param <T> The {@link ActivityContext} hosting the drag layer.
 */
public class SimpleDragLayer<T extends Context & ActivityContext> extends BaseDragLayer<T> {

    public SimpleDragLayer(Context context, AttributeSet attrs) {
        this(context, attrs, /*alphaChannelCount= */ 1);
    }

    public SimpleDragLayer(Context context, AttributeSet attrs, int alphaChannelCount) {
        super(context, attrs, alphaChannelCount);
    }

    @Override
    public void recreateControllers() {
        mControllers = new TouchController[] {};
    }
}
