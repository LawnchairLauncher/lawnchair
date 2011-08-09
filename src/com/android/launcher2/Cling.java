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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher.R;

public class Cling extends FrameLayout {

    private static String WORKSPACE_PORTRAIT = "workspace_portrait";
    private static String WORKSPACE_LANDSCAPE = "workspace_landscape";
    private static String ALLAPPS_PORTRAIT = "all_apps_portrait";
    private static String ALLAPPS_LANDSCAPE = "all_apps_landscape";

    private Launcher mLauncher;
    private boolean mIsInitialized;
    private String mDrawIdentifier;
    private Drawable mPunchThroughGraphic;
    private int mPunchThroughGraphicCenterRadius;
    private int mAppIconSize;
    private int mTabBarHeight;
    private int mTabBarHorizontalPadding;

    View mWorkspaceDesc1;
    View mWorkspaceDesc2;
    View mAllAppsDesc;

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

    void init(Launcher l) {
        if (!mIsInitialized) {
            mLauncher = l;

            Resources r = getContext().getResources();
            mPunchThroughGraphic = r.getDrawable(R.drawable.cling);
            mPunchThroughGraphicCenterRadius =
                r.getDimensionPixelSize(R.dimen.clingPunchThroughGraphicCenterRadius);
            mAppIconSize = r.getDimensionPixelSize(R.dimen.app_icon_size);
            mTabBarHeight = r.getDimensionPixelSize(R.dimen.apps_customize_tab_bar_height);
            mTabBarHorizontalPadding =
                r.getDimensionPixelSize(R.dimen.toolbar_button_horizontal_padding);

            mWorkspaceDesc1 = findViewById(R.id.workspace_cling_move_item);
            mWorkspaceDesc2 = findViewById(R.id.workspace_cling_open_all_apps);
            mAllAppsDesc = findViewById(R.id.all_apps_cling_add_item);
            mIsInitialized = true;
        }
    }

    void cleanup() {
        mPunchThroughGraphic = null;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        // Do nothing
        return true;
    };

    @Override
    protected void dispatchDraw(Canvas canvas) {
        // Draw the rest of the cling
        super.dispatchDraw(canvas);

        if (mIsInitialized) {
            DisplayMetrics metrics = new DisplayMetrics();
            mLauncher.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            int dotRadius = (int) (6f * metrics.density);

            Paint p = new Paint();
            p.setAntiAlias(true);
            p.setColor(0xFF49C0EC);

            if (mDrawIdentifier.equals(WORKSPACE_PORTRAIT)) {
                /* Draw the all apps line */ {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)
                            mWorkspaceDesc2.getLayoutParams();
                    int[] loc = new int[2];
                    mWorkspaceDesc2.getLocationInWindow(loc);
                    int x = loc[0];
                    int xOffset = (int) (10f * metrics.density);
                    int y = loc[1];
                    int yOffset = (int) (30f * metrics.density);
                    int w = mWorkspaceDesc2.getWidth();
                    int h = mWorkspaceDesc2.getHeight();

                    Point p1 = new Point(x + w + xOffset, y - (2 * dotRadius));
                    Point p2 = new Point(getMeasuredWidth() / 2, getMeasuredHeight() -
                            mAppIconSize / 2 - yOffset);
                    canvas.drawCircle(p1.x, p1.y, dotRadius, p);
                    canvas.drawCircle(p2.x, p2.y, dotRadius, p);

                    Point p3 = new Point(p1.x, (int) (p1.y + (p2.y - p1.y) * 0.30f));
                    Point p4 = new Point(p2.x, (int) (p1.y + (p2.y - p1.y) * 0.55f));
                    canvas.drawLine(p1.x, p1.y, p3.x, p3.y, p);
                    canvas.drawLine(p3.x, p3.y, p4.x, p4.y, p);
                    canvas.drawLine(p4.x, p4.y, p2.x, p2.y, p);
                }

                /* Draw the move line */ {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)
                            mWorkspaceDesc1.getLayoutParams();
                    int[] loc = new int[2];
                    mWorkspaceDesc1.getLocationInWindow(loc);
                    int x = loc[0];
                    int y = loc[1];
                    int w = mWorkspaceDesc1.getWidth();
                    int h = mWorkspaceDesc1.getHeight();

                    Point p1 = new Point(x + w, y - (2 * dotRadius));
                    Point p2 = new Point(x + w, getMeasuredHeight() - (4 * mAppIconSize));
                    canvas.drawCircle(p1.x, p1.y, dotRadius, p);
                    canvas.drawCircle(p2.x, p2.y, dotRadius, p);
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, p);
                }
            } else if (mDrawIdentifier.equals(WORKSPACE_LANDSCAPE)) {
                int xOffset = (int) (1.5f * mAppIconSize);
                /* Draw the all apps line */ {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)
                            mWorkspaceDesc2.getLayoutParams();
                    int[] loc = new int[2];
                    mWorkspaceDesc2.getLocationInWindow(loc);
                    int x = loc[0];
                    int y = loc[1];
                    int w = mWorkspaceDesc2.getWidth();
                    int h = mWorkspaceDesc2.getHeight();

                    Point p1 = new Point(x + w, y - (2 * dotRadius));
                    Point p2 = new Point(getMeasuredWidth() - xOffset,
                            getMeasuredHeight() / 2);
                    canvas.drawCircle(p1.x, p1.y, dotRadius, p);
                    canvas.drawCircle(p2.x, p2.y, dotRadius, p);

                    Point p3 = new Point((int) (p1.x + (p2.x - p1.x) * 0.6f), p1.y);
                    Point p4 = new Point((int) (p1.x + (p2.x - p1.x) * 0.75f), p2.y);
                    canvas.drawLine(p1.x, p1.y, p3.x, p3.y, p);
                    canvas.drawLine(p3.x, p3.y, p4.x, p4.y, p);
                    canvas.drawLine(p4.x, p4.y, p2.x, p2.y, p);
                }

                /* Draw the move line */ {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)
                            mWorkspaceDesc1.getLayoutParams();
                    int[] loc = new int[2];
                    mWorkspaceDesc1.getLocationInWindow(loc);
                    int x = loc[0];
                    int y = loc[1];
                    int w = mWorkspaceDesc1.getWidth();
                    int h = mWorkspaceDesc1.getHeight();

                    Point p1 = new Point(x + w, y - (2 * dotRadius));
                    Point p2 = new Point(getMeasuredWidth() - xOffset, y - (2 * dotRadius));
                    canvas.drawCircle(p1.x, p1.y, dotRadius, p);
                    canvas.drawCircle(p2.x, p2.y, dotRadius, p);
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, p);
                }
            } else if (mDrawIdentifier.equals(ALLAPPS_PORTRAIT)) {
                float r = mAppIconSize * 1.1f;
                float scale = r / mPunchThroughGraphicCenterRadius;
                int dw = (int) (scale * mPunchThroughGraphic.getIntrinsicWidth());
                int dh = (int) (scale * mPunchThroughGraphic.getIntrinsicHeight());
                int cx = getMeasuredWidth() / 2;
                int cy = mTabBarHeight + ((getMeasuredHeight() - mTabBarHeight) / 2);
                mPunchThroughGraphic.setBounds(cx - dw/2, cy - dh/2, cx + dw/2, cy + dh/2);
                mPunchThroughGraphic.draw(canvas);

                /* Draw the line */ {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)
                            mAllAppsDesc.getLayoutParams();
                    int[] loc = new int[2];
                    mAllAppsDesc.getLocationInWindow(loc);
                    int x = loc[0];
                    int y = loc[1];
                    int yOffset = (int) (2.5f * metrics.density);
                    int w = mAllAppsDesc.getWidth();
                    int h = mAllAppsDesc.getHeight();

                    Point p1 = new Point(getMeasuredWidth() / 2, y + h + yOffset);
                    Point p2 = new Point(cx, cy);
                    canvas.drawCircle(p1.x, p1.y, dotRadius, p);
                    canvas.drawCircle(p2.x, p2.y, dotRadius, p);
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, p);
                }
            } else if (mDrawIdentifier.equals(ALLAPPS_LANDSCAPE)) {
                float r = mAppIconSize * 1.1f;
                float scale = r / mPunchThroughGraphicCenterRadius;
                int dw = (int) (scale * mPunchThroughGraphic.getIntrinsicWidth());
                int dh = (int) (scale * mPunchThroughGraphic.getIntrinsicHeight());
                int cx = getMeasuredWidth() / 2 + getMeasuredWidth() / 4;
                int cy = mTabBarHeight + ((getMeasuredHeight() - mTabBarHeight) / 2);
                mPunchThroughGraphic.setBounds(cx - dw/2, cy - dh/2, cx + dw/2, cy + dh/2);
                mPunchThroughGraphic.draw(canvas);

                /* Draw the line */ {
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams)
                            mAllAppsDesc.getLayoutParams();
                    int[] loc = new int[2];
                    mAllAppsDesc.getLocationInWindow(loc);
                    int x = loc[0];
                    int y = loc[1];
                    int w = mAllAppsDesc.getWidth();
                    int h = mAllAppsDesc.getHeight();

                    Point p1 = new Point(x + w, y);
                    Point p2 = new Point(cx, cy);
                    canvas.drawCircle(p1.x, p1.y, dotRadius, p);
                    canvas.drawCircle(p2.x, p2.y, dotRadius, p);
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, p);
                }
            }

            /*
            // Draw the background
            Bitmap b = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawColor(0xD4000000);
            Paint p = new Paint();
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            p.setColor(0xFFFFFF);
            p.setAlpha(0);

            int cx = -1;
            int cy = -1;
            float r = mAppIconSize * 1.4f;
            float scale = r / mPunchThroughGraphicCenterRadius;
            int dw = (int) (scale * mPunchThroughGraphic.getIntrinsicWidth());
            int dh = (int) (scale * mPunchThroughGraphic.getIntrinsicHeight());

            if (mDrawIdentifier.equals("workspace_portrait")) {
                cx = getMeasuredWidth() / 2;
                cy = getMeasuredHeight() - mAppIconSize / 2;
            } else if (mDrawIdentifier.equals("workspace_landscape")) {
                cx = getMeasuredWidth() - mAppIconSize / 2;
                cy = getMeasuredHeight() / 2;
            } else if (mDrawIdentifier.equals("large_workspace_landscape") ||
                       mDrawIdentifier.equals("large_workspace_portrait")) {
                cx = getMeasuredWidth() - mTabBarHorizontalPadding;
                cy = 0;
            } else if (mDrawIdentifier.equals("all_apps_portrait")) {
                cx = getMeasuredWidth() / 2;
                cy = mTabBarHeight + ((getMeasuredHeight() - mTabBarHeight) / 2);
            } else if (mDrawIdentifier.equals("all_apps_landscape")) {
                cx = getMeasuredWidth() / 2 + getMeasuredWidth() / 4;
                cy = mTabBarHeight + ((getMeasuredHeight() - mTabBarHeight) / 2);
            } else if (mDrawIdentifier.equals("large_all_apps_portrait")) {
                cx = getMeasuredWidth() / 2;
                cy = mTabBarHeight + (int) ((getMeasuredHeight() - mTabBarHeight) * 2f / 5f);
            } else if (mDrawIdentifier.equals("large_all_apps_landscape")) {
                cx = getMeasuredWidth() / 2 + getMeasuredWidth() / 6;
                cy = mTabBarHeight + (int) ((getMeasuredHeight() - mTabBarHeight) * 2f / 5f);
            }
            if (cx > -1 && cy > -1) {
                c.drawCircle(cx, cy, r, p);
                mPunchThroughGraphic.setBounds(cx - dw/2, cy - dh/2, cx + dw/2, cy + dh/2);
                mPunchThroughGraphic.draw(c);
            }
            canvas.drawBitmap(b, 0, 0, null);
            c.setBitmap(null);
            b = null;
            */
        }
    };
}
