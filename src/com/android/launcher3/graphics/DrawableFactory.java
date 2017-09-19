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
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;
import android.support.annotation.UiThread;
import android.util.ArrayMap;
import android.util.Log;
import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.AllAppsBackgroundDrawable;

/**
 * Factory for creating new drawables.
 */
public class DrawableFactory {

    private static final String TAG = "DrawableFactory";

    private static DrawableFactory sInstance;
    private static final Object LOCK = new Object();

    private Path mPreloadProgressPath;

    public static DrawableFactory get(Context context) {
        synchronized (LOCK) {
            if (sInstance == null) {
                sInstance = Utilities.getOverrideObject(DrawableFactory.class,
                        context.getApplicationContext(), R.string.drawable_factory_class);
            }
            return sInstance;
        }
    }

    protected final UserHandle mMyUser = Process.myUserHandle();
    protected final ArrayMap<UserHandle, Bitmap> mUserBadges = new ArrayMap<>();

    /**
     * Returns a FastBitmapDrawable with the icon.
     */
    public FastBitmapDrawable newIcon(Bitmap icon, ItemInfo info) {
        return new FastBitmapDrawable(icon);
    }

    /**
     * Returns a FastBitmapDrawable with the icon.
     */
    public PreloadIconDrawable newPendingIcon(Bitmap icon, Context context) {
        if (mPreloadProgressPath == null) {
            mPreloadProgressPath = getPreloadProgressPath(context);
        }
        return new PreloadIconDrawable(icon, mPreloadProgressPath, context);
    }


    protected Path getPreloadProgressPath(Context context) {
        if (Utilities.ATLEAST_OREO) {
            try {
                // Try to load the path from Mask Icon
                Drawable icon = context.getDrawable(R.drawable.adaptive_icon_drawable_wrapper);
                icon.setBounds(0, 0,
                        PreloadIconDrawable.PATH_SIZE, PreloadIconDrawable.PATH_SIZE);
                return (Path) icon.getClass().getMethod("getIconMask").invoke(icon);
            } catch (Exception e) {
                Log.e(TAG, "Error loading mask icon", e);
            }
        }

        // Create a circle static from top center and going clockwise.
        Path p = new Path();
        p.moveTo(PreloadIconDrawable.PATH_SIZE / 2, 0);
        p.addArc(0, 0, PreloadIconDrawable.PATH_SIZE, PreloadIconDrawable.PATH_SIZE, -90, 360);
        return p;
    }

    public AllAppsBackgroundDrawable getAllAppsBackground(Context context) {
        return new AllAppsBackgroundDrawable(context);
    }

    /**
     * Returns a drawable that can be used as a badge for the user or null.
     */
    @UiThread
    public Drawable getBadgeForUser(UserHandle user, Context context) {
        if (mMyUser.equals(user)) {
            return null;
        }

        Bitmap badgeBitmap = getUserBadge(user, context);
        FastBitmapDrawable d = new FastBitmapDrawable(badgeBitmap);
        d.setFilterBitmap(true);
        d.setBounds(0, 0, badgeBitmap.getWidth(), badgeBitmap.getHeight());
        return d;
    }

    protected synchronized Bitmap getUserBadge(UserHandle user, Context context) {
        Bitmap badgeBitmap = mUserBadges.get(user);
        if (badgeBitmap != null) {
            return badgeBitmap;
        }

        final Resources res = context.getApplicationContext().getResources();
        int badgeSize = res.getDimensionPixelSize(R.dimen.profile_badge_size);
        badgeBitmap = Bitmap.createBitmap(badgeSize, badgeSize, Bitmap.Config.ARGB_8888);

        Drawable drawable = context.getPackageManager().getUserBadgedDrawableForDensity(
                new BitmapDrawable(res, badgeBitmap), user, new Rect(0, 0, badgeSize, badgeSize),
                0);
        if (drawable instanceof BitmapDrawable) {
            badgeBitmap = ((BitmapDrawable) drawable).getBitmap();
        } else {
            badgeBitmap.eraseColor(Color.TRANSPARENT);
            Canvas c = new Canvas(badgeBitmap);
            drawable.setBounds(0, 0, badgeSize, badgeSize);
            drawable.draw(c);
            c.setBitmap(null);
        }

        mUserBadges.put(user, badgeBitmap);
        return badgeBitmap;
    }
}
