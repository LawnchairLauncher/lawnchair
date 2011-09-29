/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher2;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher.R;

public class Cling extends FrameLayout {

    static final String WORKSPACE_CLING_DISMISSED_KEY = "cling.workspace.dismissed";
    static final String ALLAPPS_CLING_DISMISSED_KEY = "cling.allapps.dismissed";
    static final String FOLDER_CLING_DISMISSED_KEY = "cling.folder.dismissed";

    private static String WORKSPACE_PORTRAIT = "workspace_portrait";
    private static String WORKSPACE_LANDSCAPE = "workspace_landscape";
    private static String ALLAPPS_PORTRAIT = "all_apps_portrait";
    private static String ALLAPPS_LANDSCAPE = "all_apps_landscape";
    private static String FOLDER_PORTRAIT = "folder_portrait";
    private static String FOLDER_LANDSCAPE = "folder_landscape";

    private Launcher mLauncher;
    private boolean mIsInitialized;
    private String mDrawIdentifier;
    private Drawable mBackground;
    private Drawable mPunchThroughGraphic;
    private Drawable mHandTouchGraphic;
    private int mPunchThroughGraphicCenterRadius;
    private int mAppIconSize;
    private int mTabBarHeight;
    private int mTabBarHorizontalPadding;
    private int mButtonBarHeight;
    private float mRevealRadius;
    private int[] mPositionData;

    private Paint mErasePaint;

    public Cling(Context context) {
        this(context, null, 0);
    }

    public Cling(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Cling(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Cling, defStyle, 0);
        mDrawIdentifier = a.getString(R.styleable.Cling_drawIdentifier);
        a.recycle();
    }

    void init(Launcher l, int[] positionData) {
        if (!mIsInitialized) {
            mLauncher = l;
            mPositionData = positionData;

            Resources r = getContext().getResources();
            mPunchThroughGraphic = r.getDrawable(R.drawable.cling);
            mPunchThroughGraphicCenterRadius =
                r.getDimensionPixelSize(R.dimen.clingPunchThroughGraphicCenterRadius);
            mAppIconSize = r.getDimensionPixelSize(R.dimen.app_icon_size);
            mRevealRadius = mAppIconSize * 1f;
            mTabBarHeight = r.getDimensionPixelSize(R.dimen.apps_customize_tab_bar_height);
            mTabBarHorizontalPadding =
                r.getDimensionPixelSize(R.dimen.toolbar_button_horizontal_padding);
            mButtonBarHeight = r.getDimensionPixelSize(R.dimen.button_bar_height);

            mErasePaint = new Paint();
            mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            mErasePaint.setColor(0xFFFFFF);
            mErasePaint.setAlpha(0);

            mIsInitialized = true;
        }
    }

    void cleanup() {
        mBackground = null;
        mPunchThroughGraphic = null;
        mHandTouchGraphic = null;
        mIsInitialized = false;
    }

    private int[] getPunchThroughPosition() {
        if (mDrawIdentifier.equals(WORKSPACE_PORTRAIT)) {
            return new int[]{getMeasuredWidth() / 2, getMeasuredHeight() - (mButtonBarHeight / 2)};
        } else if (mDrawIdentifier.equals(WORKSPACE_LANDSCAPE)) {
            return new int[]{getMeasuredWidth() - (mButtonBarHeight / 2), getMeasuredHeight() / 2};
        } else if (mDrawIdentifier.equals(ALLAPPS_PORTRAIT) ||
                   mDrawIdentifier.equals(ALLAPPS_LANDSCAPE)) {
            return mPositionData;
        }
        return new int[]{-1, -1};
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (mDrawIdentifier.equals(WORKSPACE_PORTRAIT) ||
            mDrawIdentifier.equals(WORKSPACE_LANDSCAPE) ||
            mDrawIdentifier.equals(ALLAPPS_PORTRAIT) ||
            mDrawIdentifier.equals(ALLAPPS_LANDSCAPE)) {
            int[] pos = getPunchThroughPosition();
            double diff = Math.sqrt(Math.pow(event.getX() - pos[0], 2) +
                    Math.pow(event.getY() - pos[1], 2));
            if (diff < mRevealRadius) {
                return false;
            }
        } else if (mDrawIdentifier.equals(FOLDER_PORTRAIT) ||
                   mDrawIdentifier.equals(FOLDER_LANDSCAPE)) {
            Folder f = mLauncher.getWorkspace().getOpenFolder();
            if (f != null) {
                Rect r = new Rect();
                f.getHitRect(r);
                if (r.contains((int) event.getX(), (int) event.getY())) {
                    return false;
                }
            }
        }
        return true;
    };

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mIsInitialized) {
            DisplayMetrics metrics = new DisplayMetrics();
            mLauncher.getWindowManager().getDefaultDisplay().getMetrics(metrics);

            // Initialize the draw buffer (to allow punching through)
            Bitmap b = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);

            // Draw the background
            if (mBackground == null) {
                if (mDrawIdentifier.equals(WORKSPACE_PORTRAIT) ||
                    mDrawIdentifier.equals(WORKSPACE_LANDSCAPE)) {
                    mBackground = getResources().getDrawable(R.drawable.bg_cling1);
                } else if (mDrawIdentifier.equals(ALLAPPS_PORTRAIT) ||
                        mDrawIdentifier.equals(ALLAPPS_LANDSCAPE)) {
                    mBackground = getResources().getDrawable(R.drawable.bg_cling2);
                } else if (mDrawIdentifier.equals(FOLDER_PORTRAIT) ||
                        mDrawIdentifier.equals(FOLDER_LANDSCAPE)) {
                    mBackground = getResources().getDrawable(R.drawable.bg_cling3);
                }
            }
            if (mBackground != null) {
                mBackground.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                mBackground.draw(c);
            } else {
                c.drawColor(0x99000000);
            }

            int cx = -1;
            int cy = -1;
            float scale = mRevealRadius / mPunchThroughGraphicCenterRadius;
            int dw = (int) (scale * mPunchThroughGraphic.getIntrinsicWidth());
            int dh = (int) (scale * mPunchThroughGraphic.getIntrinsicHeight());

            // Determine where to draw the punch through graphic
            int[] pos = getPunchThroughPosition();
            cx = pos[0];
            cy = pos[1];
            if (cx > -1 && cy > -1) {
                c.drawCircle(cx, cy, mRevealRadius, mErasePaint);
                mPunchThroughGraphic.setBounds(cx - dw/2, cy - dh/2, cx + dw/2, cy + dh/2);
                mPunchThroughGraphic.draw(c);
            }

            // Draw the hand graphic in All Apps
            if (mDrawIdentifier.equals(ALLAPPS_PORTRAIT) ||
                mDrawIdentifier.equals(ALLAPPS_LANDSCAPE)) {
                if (mHandTouchGraphic == null) {
                    mHandTouchGraphic = getResources().getDrawable(R.drawable.hand);
                }
                int offset = mAppIconSize / 4;
                mHandTouchGraphic.setBounds(cx + offset, cy + offset,
                        cx + mHandTouchGraphic.getIntrinsicWidth() + offset,
                        cy + mHandTouchGraphic.getIntrinsicHeight() + offset);
                mHandTouchGraphic.draw(c);
            }

            canvas.drawBitmap(b, 0, 0, null);
            c.setBitmap(null);
            b = null;
        }

        // Draw the rest of the cling
        super.dispatchDraw(canvas);
    };
}
