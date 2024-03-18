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

package com.android.launcher3.icons;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.launcher3.Flags;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.graphics.IconShape;
import com.android.launcher3.graphics.LauncherPreviewRenderer;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.UserIconInfo;

/**
 * Wrapper class to provide access to {@link BaseIconFactory} and also to provide pool of this class
 * that are threadsafe.
 */
public class LauncherIcons extends BaseIconFactory implements AutoCloseable {

    private static final Object sPoolSync = new Object();
    private static LauncherIcons sPool;
    private static int sPoolId = 0;

    /**
     * Return a new Message instance from the global pool. Allows us to
     * avoid allocating new objects in many cases.
     */
    public static LauncherIcons obtain(Context context) {
        if (context instanceof LauncherPreviewRenderer.PreviewContext) {
            return ((LauncherPreviewRenderer.PreviewContext) context).newLauncherIcons(context);
        }

        int poolId;
        synchronized (sPoolSync) {
            if (sPool != null) {
                LauncherIcons m = sPool;
                sPool = m.next;
                m.next = null;
                return m;
            }
            poolId = sPoolId;
        }

        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(context);
        return new LauncherIcons(context, idp.fillResIconDpi, idp.iconBitmapSize, poolId);
    }

    public static void clearPool() {
        synchronized (sPoolSync) {
            sPool = null;
            sPoolId++;
        }
    }

    private final int mPoolId;

    private LauncherIcons next;

    private MonochromeIconFactory mMonochromeIconFactory;

    protected LauncherIcons(Context context, int fillResIconDpi, int iconBitmapSize, int poolId) {
        super(context, fillResIconDpi, iconBitmapSize, IconShape.getShape().enableShapeDetection());
        mMonoIconEnabled = Themes.isThemedIconEnabled(context);
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
    protected Drawable getMonochromeDrawable(Drawable base) {
        Drawable mono = super.getMonochromeDrawable(base);
        if (mono != null || !Flags.forceMonochromeAppIcons()) {
            return mono;
        }
        if (mMonochromeIconFactory == null) {
            mMonochromeIconFactory = new MonochromeIconFactory(mIconBitmapSize);
        }
        return mMonochromeIconFactory.wrap(base);
    }

    @NonNull
    @Override
    protected UserIconInfo getUserInfo(@NonNull UserHandle user) {
        return UserCache.INSTANCE.get(mContext).getUserInfo(user);
    }

    @Override
    public void close() {
        recycle();
    }
}
