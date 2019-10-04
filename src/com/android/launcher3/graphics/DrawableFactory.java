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

import static com.android.launcher3.graphics.IconShape.getShapePath;
import static com.android.launcher3.util.MainThreadInitializedObject.forOverride;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.annotation.UiThread;

import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.R;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.util.MainThreadInitializedObject;
import com.android.launcher3.util.ResourceBasedOverride;

/**
 * Factory for creating new drawables.
 */
public class DrawableFactory implements ResourceBasedOverride {

    public static final MainThreadInitializedObject<DrawableFactory> INSTANCE =
            forOverride(DrawableFactory.class, R.string.drawable_factory_class);

    protected final UserHandle mMyUser = Process.myUserHandle();
    protected final ArrayMap<UserHandle, Bitmap> mUserBadges = new ArrayMap<>();

    /**
     * Returns a FastBitmapDrawable with the icon.
     */
    public FastBitmapDrawable newIcon(Context context, ItemInfoWithIcon info) {
        FastBitmapDrawable drawable = info.usingLowResIcon()
                ? new PlaceHolderIconDrawable(info, getShapePath(), context)
                : new FastBitmapDrawable(info);
        drawable.setIsDisabled(info.isDisabled());
        return drawable;
    }

    public FastBitmapDrawable newIcon(Context context, BitmapInfo info, ActivityInfo target) {
        return info.isLowRes()
                ? new PlaceHolderIconDrawable(info, getShapePath(), context)
                : new FastBitmapDrawable(info);
    }

    /**
     * Returns a FastBitmapDrawable with the icon.
     */
    public PreloadIconDrawable newPendingIcon(Context context, ItemInfoWithIcon info) {
        return new PreloadIconDrawable(info, getShapePath(), context);
    }

    /**
     * Returns a drawable that can be used as a badge for the user or null.
     */
    @UiThread
    public Drawable getBadgeForUser(UserHandle user, Context context, int badgeSize) {
        if (mMyUser.equals(user)) {
            return null;
        }

        Bitmap badgeBitmap = getUserBadge(user, context, badgeSize);
        FastBitmapDrawable d = new FastBitmapDrawable(badgeBitmap);
        d.setFilterBitmap(true);
        d.setBounds(0, 0, badgeBitmap.getWidth(), badgeBitmap.getHeight());
        return d;
    }

    protected synchronized Bitmap getUserBadge(UserHandle user, Context context, int badgeSize) {
        Bitmap badgeBitmap = mUserBadges.get(user);
        if (badgeBitmap != null) {
            return badgeBitmap;
        }

        final Resources res = context.getApplicationContext().getResources();
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
