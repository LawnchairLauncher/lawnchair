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
 * limitations under the License.
 */
package com.android.launcher3.icons;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import com.android.launcher3.icons.cache.CachingLogic;

public interface ComponentWithLabel {

    ComponentName getComponent();

    UserHandle getUser();

    CharSequence getLabel(PackageManager pm);


    class ComponentCachingLogic<T extends ComponentWithLabel> implements CachingLogic<T> {

        private final PackageManager mPackageManager;
        private final boolean mAddToMemCache;

        public ComponentCachingLogic(Context context, boolean addToMemCache) {
            mPackageManager = context.getPackageManager();
            mAddToMemCache = addToMemCache;
        }

        @Override
        public ComponentName getComponent(T object) {
            return object.getComponent();
        }

        @Override
        public UserHandle getUser(T object) {
            return object.getUser();
        }

        @Override
        public CharSequence getLabel(T object) {
            return object.getLabel(mPackageManager);
        }

        @Override
        public BitmapInfo loadIcon(Context context, T object) {
            return BitmapInfo.LOW_RES_INFO;
        }

        @Override
        public boolean addToMemCache() {
            return mAddToMemCache;
        }
    }
}
