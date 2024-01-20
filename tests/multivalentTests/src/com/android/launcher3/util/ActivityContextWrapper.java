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
package com.android.launcher3.util;

import android.content.Context;
import android.content.ContextWrapper;
import android.view.ContextThemeWrapper;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ContextWrapper} with internal Launcher interface for testing
 */
public class ActivityContextWrapper extends ContextThemeWrapper implements ActivityContext {

    private final List<OnDeviceProfileChangeListener> mDpChangeListeners = new ArrayList<>();

    private final DeviceProfile mProfile;
    private final MyDragLayer mMyDragLayer;

    public ActivityContextWrapper(Context base) {
        super(base, android.R.style.Theme_DeviceDefault);
        mProfile = InvariantDeviceProfile.INSTANCE.get(base).getDeviceProfile(base).copy(base);
        mMyDragLayer = new MyDragLayer(this);
    }

    @Override
    public BaseDragLayer getDragLayer() {
        return mMyDragLayer;
    }

    @Override
    public List<OnDeviceProfileChangeListener> getOnDeviceProfileChangeListeners() {
        return mDpChangeListeners;
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mProfile;
    }

    private static class MyDragLayer extends BaseDragLayer<ActivityContextWrapper> {

        MyDragLayer(Context context) {
            super(context, null, 1);
        }

        @Override
        public void recreateControllers() {
            mControllers = new TouchController[0];
        }
    }
}
