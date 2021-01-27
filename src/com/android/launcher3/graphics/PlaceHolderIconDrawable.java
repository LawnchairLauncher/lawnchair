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
package com.android.launcher3.graphics;

import static androidx.core.graphics.ColorUtils.compositeColors;

import static com.android.launcher3.graphics.IconShape.getShapePath;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;

import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.R;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.util.Themes;

/**
 * Subclass which draws a placeholder icon when the actual icon is not yet loaded
 */
public class PlaceHolderIconDrawable extends FastBitmapDrawable {

    // Path in [0, 100] bounds.
    private final Path mProgressPath;

    public PlaceHolderIconDrawable(BitmapInfo info, Context context) {
        super(info);

        mProgressPath = getShapePath();
        mPaint.setColor(compositeColors(
                Themes.getAttrColor(context, R.attr.loadingIconColor), info.color));
    }

    @Override
    protected void drawInternal(Canvas canvas, Rect bounds) {
        int saveCount = canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.scale(bounds.width() / 100f, bounds.height() / 100f);
        canvas.drawPath(mProgressPath, mPaint);
        canvas.restoreToCount(saveCount);
    }
}
