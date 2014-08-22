/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.launcher3;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;

public class ClearCircleLayout extends View {

    private static final String HOLE_LOCATION_PAGE_INDICATOR = "page_indicator";
    private static final String HOLE_LOCATION_CENTER_SCREEN = "center_screen";

    private static final int BACKGROUND_COLOR = 0x80000000;
    private static float MIGRATION_WORKSPACE_INNER_CIRCLE_RADIUS_DPS = 42;
    private static float MIGRATION_WORKSPACE_OUTER_CIRCLE_RADIUS_DPS = 46;

    private final String mHoleLocation;
    private final Paint mErasePaint;
    private final Paint mBorderPaint;

    private Launcher mLauncher;
    private Point mHoleCenter;
    private DisplayMetrics mMetrics;

    public ClearCircleLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ClearCircleLayout);
        mHoleLocation = a.getString(R.styleable.ClearCircleLayout_holeLocation);
        a.recycle();

        mErasePaint = new Paint();
        mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        mErasePaint.setColor(0xFFFFFF);
        mErasePaint.setAlpha(0);
        mErasePaint.setAntiAlias(true);

        mBorderPaint = new Paint();
        mBorderPaint.setColor(0xFFFFFFFF);
        mBorderPaint.setAntiAlias(true);
    }

    void initHole(Launcher launcher) {
        mLauncher = launcher;
        mMetrics = new DisplayMetrics();
        launcher.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);

        if (mHoleLocation.endsWith(HOLE_LOCATION_PAGE_INDICATOR)) {
            LauncherAppState app = LauncherAppState.getInstance();
            DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();

            Rect indicator = grid.getWorkspacePageIndicatorBounds(new Rect());
            mHoleCenter = new Point(indicator.centerX(), indicator.centerY());
        } else if (mHoleLocation.endsWith(HOLE_LOCATION_CENTER_SCREEN)) {
            mHoleCenter = new Point(mMetrics.widthPixels / 2, mMetrics.heightPixels / 2);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mHoleCenter == null) {
            canvas.drawColor(BACKGROUND_COLOR);
        } else {
            drawHole(canvas);
        }

        super.dispatchDraw(canvas);
    }

    private void drawHole(Canvas canvas) {
        // Initialize the draw buffer (to allow punching through)
        Bitmap eraseBg = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas eraseCanvas = new Canvas(eraseBg);
        eraseCanvas.drawColor(BACKGROUND_COLOR);

        Rect insets = mLauncher.getDragLayer().getInsets();
        float x = mHoleCenter.x - insets.left;
        float y = mHoleCenter.y - insets.top;
        // Draw the outer circle
        eraseCanvas.drawCircle(x, y,
                DynamicGrid.pxFromDp(MIGRATION_WORKSPACE_OUTER_CIRCLE_RADIUS_DPS, mMetrics),
                mBorderPaint);

        // Draw the inner circle
        eraseCanvas.drawCircle(x, y,
                DynamicGrid.pxFromDp(MIGRATION_WORKSPACE_INNER_CIRCLE_RADIUS_DPS, mMetrics),
                mErasePaint);

        canvas.drawBitmap(eraseBg, 0, 0, null);
        eraseCanvas.setBitmap(null);
        eraseBg.recycle();
    }
}
