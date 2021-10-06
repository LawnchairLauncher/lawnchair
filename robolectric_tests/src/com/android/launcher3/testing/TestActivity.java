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
package com.android.launcher3.testing;

import com.android.launcher3.BaseActivity;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.BaseDragLayer;

/** An empty activity for {@link android.app.Fragment}s, {@link android.view.View}s testing. */
public class TestActivity extends BaseActivity implements ActivityContext {

    private DeviceProfile mDeviceProfile;

    @Override
    public BaseDragLayer getDragLayer() {
        return new BaseDragLayer(this, /* attrs= */ null, /* alphaChannelCount= */ 1) {
            @Override
            public void recreateControllers() {
                // Do nothing.
            }
        };
    }

    @Override
    public DeviceProfile getDeviceProfile() {
        return mDeviceProfile;
    }

    public void setDeviceProfile(DeviceProfile deviceProfile) {
        mDeviceProfile = deviceProfile;
    }
}
