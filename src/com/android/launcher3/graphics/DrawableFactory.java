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

package com.android.launcher3.graphics;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;

import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.R;

import java.lang.reflect.InvocationTargetException;

/**
 * Factory for creating new drawables.
 */
public class DrawableFactory {

    private static DrawableFactory sInstance;
    private static final Object LOCK = new Object();

    public static DrawableFactory get(Context context) {
        synchronized (LOCK) {
            if (sInstance == null) {
                context = context.getApplicationContext();
                sInstance = loadByName(context.getString(R.string.drawable_factory_class), context);
            }
            return sInstance;
        }
    }

    public static DrawableFactory loadByName(String className, Context context) {
        if (!TextUtils.isEmpty(className)) {
            try {
                Class<?> cls = Class.forName(className);
                return (DrawableFactory)
                        cls.getDeclaredConstructor(Context.class).newInstance(context);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException
                    | ClassCastException | NoSuchMethodException | InvocationTargetException e) {
                return new DrawableFactory();
            }
        }
        return new DrawableFactory();
    }

    /**
     * Returns a FastBitmapDrawable with the icon.
     */
    public FastBitmapDrawable newIcon(Bitmap icon, ItemInfo info) {
        FastBitmapDrawable d = new FastBitmapDrawable(icon);
        d.setFilterBitmap(true);
        return d;
    }
}
