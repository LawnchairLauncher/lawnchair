/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.taskbar.allapps;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.android.launcher3.allapps.ActivityAllAppsContainerView;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;

import java.util.Optional;

/** All apps container accessible from taskbar. */
public class TaskbarAllAppsContainerView extends
        ActivityAllAppsContainerView<TaskbarOverlayContext> {

    private @Nullable OnInvalidateHeaderListener mOnInvalidateHeaderListener;

    public TaskbarAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskbarAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    void setOnInvalidateHeaderListener(OnInvalidateHeaderListener onInvalidateHeaderListener) {
        mOnInvalidateHeaderListener = onInvalidateHeaderListener;
    }

    @Override
    public void invalidateHeader() {
        super.invalidateHeader();
        Optional.ofNullable(mOnInvalidateHeaderListener).ifPresent(
                OnInvalidateHeaderListener::onInvalidateHeader);
    }

    @Override
    public boolean isInAllApps() {
        // All apps is always open
        return true;
    }

    interface OnInvalidateHeaderListener {
        void onInvalidateHeader();
    }
}
