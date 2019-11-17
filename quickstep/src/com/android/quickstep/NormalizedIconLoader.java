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
package com.android.quickstep;

import android.annotation.TargetApi;
import android.app.ActivityManager.TaskDescription;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandle;
import android.util.LruCache;
import android.util.SparseArray;

import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.graphics.DrawableFactory;
import com.android.launcher3.icons.LauncherIcons;
import com.android.systemui.shared.recents.model.IconLoader;
import com.android.systemui.shared.recents.model.TaskKeyLruCache;

/**
 * Extension of {@link IconLoader} with icon normalization support
 */
@TargetApi(Build.VERSION_CODES.O)
public class NormalizedIconLoader extends IconLoader {

    private final SparseArray<BitmapInfo> mDefaultIcons = new SparseArray<>();
    private final DrawableFactory mDrawableFactory;
    private final boolean mDisableColorExtraction;

    public NormalizedIconLoader(Context context, TaskKeyLruCache<Drawable> iconCache,
            LruCache<ComponentName, ActivityInfo> activityInfoCache,
            boolean disableColorExtraction) {
        super(context, iconCache, activityInfoCache);
        mDrawableFactory = DrawableFactory.INSTANCE.get(context);
        mDisableColorExtraction = disableColorExtraction;
    }

    @Override
    public Drawable getDefaultIcon(int userId) {
        synchronized (mDefaultIcons) {
            BitmapInfo info = mDefaultIcons.get(userId);
            if (info == null) {
                info = getBitmapInfo(Resources.getSystem()
                        .getDrawable(android.R.drawable.sym_def_app_icon), userId, 0, false);
                mDefaultIcons.put(userId, info);
            }

            return new FastBitmapDrawable(info);
        }
    }

    @Override
    protected Drawable createBadgedDrawable(Drawable drawable, int userId, TaskDescription desc) {
        return new FastBitmapDrawable(getBitmapInfo(drawable, userId, desc.getPrimaryColor(),
                false));
    }

    private BitmapInfo getBitmapInfo(Drawable drawable, int userId,
            int primaryColor, boolean isInstantApp) {
        try (LauncherIcons la = LauncherIcons.obtain(mContext)) {
            if (mDisableColorExtraction) {
                la.disableColorExtraction();
            }
            la.setWrapperBackgroundColor(primaryColor);

            // User version code O, so that the icon is always wrapped in an adaptive icon container
            return la.createBadgedIconBitmap(drawable, UserHandle.of(userId),
                    Build.VERSION_CODES.O, isInstantApp);
        }
    }

    @Override
    protected Drawable getBadgedActivityIcon(ActivityInfo activityInfo, int userId,
            TaskDescription desc) {
        BitmapInfo bitmapInfo = getBitmapInfo(
                activityInfo.loadUnbadgedIcon(mContext.getPackageManager()),
                userId,
                desc.getPrimaryColor(),
                activityInfo.applicationInfo.isInstantApp());
        return mDrawableFactory.newIcon(mContext, bitmapInfo, activityInfo);
    }
}
