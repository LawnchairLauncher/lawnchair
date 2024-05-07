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
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.UserIconInfo;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Wrapper class to provide access to {@link BaseIconFactory} and also to provide pool of this class
 * that are threadsafe.
 */
public class LauncherIcons extends BaseIconFactory implements AutoCloseable {

    private static final MainThreadInitializedObject<Pool> POOL =
            new MainThreadInitializedObject<>(Pool::new);

    /**
     * Return a new Message instance from the global pool. Allows us to
     * avoid allocating new objects in many cases.
     */
    public static LauncherIcons obtain(Context context) {
        return POOL.get(context).obtain();
    }

    public static void clearPool(Context context) {
        POOL.get(context).close();
    }

    private final ConcurrentLinkedQueue<LauncherIcons> mPool;

    private MonochromeIconFactory mMonochromeIconFactory;

    protected LauncherIcons(Context context, int fillResIconDpi, int iconBitmapSize,
            ConcurrentLinkedQueue<LauncherIcons> pool) {
        super(context, fillResIconDpi, iconBitmapSize,
                IconShape.INSTANCE.get(context).getShape().enableShapeDetection());
        mMonoIconEnabled = Themes.isThemedIconEnabled(context);
        mPool = pool;
    }

    /**
     * Recycles a LauncherIcons that may be in-use.
     */
    public void recycle() {
        clear();
        mPool.add(this);
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

    private static class Pool implements SafeCloseable {

        private final Context mContext;

        @NonNull
        private ConcurrentLinkedQueue<LauncherIcons> mPool = new ConcurrentLinkedQueue<>();

        private Pool(Context context) {
            mContext = context;
        }

        public LauncherIcons obtain() {
            ConcurrentLinkedQueue<LauncherIcons> pool = mPool;
            LauncherIcons m = pool.poll();

            if (m == null) {
                InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(mContext);
                return new LauncherIcons(mContext, idp.fillResIconDpi, idp.iconBitmapSize, pool);
            } else {
                return m;
            }
        }

        @Override
        public void close() {
            mPool = new ConcurrentLinkedQueue<>();
        }
    }
}
