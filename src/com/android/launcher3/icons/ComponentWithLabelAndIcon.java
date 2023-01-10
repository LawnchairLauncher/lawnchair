/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.icons.BaseIconFactory.IconOptions;

/**
 * Extension of ComponentWithLabel to also support loading icons
 */
public interface ComponentWithLabelAndIcon extends ComponentWithLabel {

    /**
     * Provide an icon for this object
     */
    Drawable getFullResIcon(IconCache cache);

    class ComponentWithIconCachingLogic extends ComponentCachingLogic<ComponentWithLabelAndIcon> {

        public ComponentWithIconCachingLogic(Context context, boolean addToMemCache) {
            super(context, addToMemCache);
        }

        @NonNull
        @Override
        public BitmapInfo loadIcon(@NonNull Context context,
                @NonNull ComponentWithLabelAndIcon object) {
            Drawable d = object.getFullResIcon(LauncherAppState.getInstance(context)
                    .getIconCache());
            if (d == null) {
                return super.loadIcon(context, object);
            }
            try (LauncherIcons li = LauncherIcons.obtain(context)) {
                return li.createBadgedIconBitmap(d, new IconOptions().setUser(object.getUser()));
            }
        }
    }
}
