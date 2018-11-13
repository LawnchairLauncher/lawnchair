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

import android.content.Context;

/**
 * Wrapper class to provide access to {@link BaseIconFactory} and also to provide pool of this class
 * that are threadsafe.
 */
public class IconFactory extends BaseIconFactory {

    private static final Object sPoolSync = new Object();
    private static IconFactory sPool;
    private static int sPoolId = 0;

    /**
     * Return a new Message instance from the global pool. Allows us to
     * avoid allocating new objects in many cases.
     */
    public static IconFactory obtain(Context context) {
        int poolId;
        synchronized (sPoolSync) {
            if (sPool != null) {
                IconFactory m = sPool;
                sPool = m.next;
                m.next = null;
                return m;
            }
            poolId = sPoolId;
        }

        return new IconFactory(context,
                context.getResources().getConfiguration().densityDpi,
                context.getResources().getDimensionPixelSize(R.dimen.default_icon_bitmap_size),
                poolId);
    }

    public static void clearPool() {
        synchronized (sPoolSync) {
            sPool = null;
            sPoolId++;
        }
    }

    private final int mPoolId;

    private IconFactory next;

    private IconFactory(Context context, int fillResIconDpi, int iconBitmapSize, int poolId) {
        super(context, fillResIconDpi, iconBitmapSize);
        mPoolId = poolId;
    }

    /**
     * Recycles a LauncherIcons that may be in-use.
     */
    public void recycle() {
        synchronized (sPoolSync) {
            if (sPoolId != mPoolId) {
                return;
            }
            // Clear any temporary state variables
            clear();

            next = sPool;
            sPool = this;
        }
    }

    @Override
    public void close() {
        recycle();
    }
}
