/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.model;

import com.android.launcher3.LauncherAppState;

/**
 * Handles updates due to changes in Device Policy Management resources triggered by
 * {@link android.app.admin.DevicePolicyManager#ACTION_DEVICE_POLICY_RESOURCE_UPDATED}.
 */
public class ReloadStringCacheTask extends BaseModelUpdateTask {
    private ModelDelegate mModelDelegate;

    public ReloadStringCacheTask(ModelDelegate modelDelegate) {
        mModelDelegate = modelDelegate;
    }

    @Override
    public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList appsList) {
        synchronized (dataModel) {
            mModelDelegate.loadStringCache(dataModel.stringCache);
            StringCache cloneSC = dataModel.stringCache.clone();
            scheduleCallbackTask(c -> c.bindStringCache(cloneSC));
        }
    }
}
