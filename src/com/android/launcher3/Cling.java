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

package com.android.launcher3;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.FocusFinder;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

public class Cling extends FrameLayout implements Insettable, View.OnClickListener,
        View.OnLongClickListener, View.OnTouchListener {

    private static String FIRST_RUN_PORTRAIT = "first_run_portrait";
    private static String FIRST_RUN_LANDSCAPE = "first_run_landscape";

    private static String WORKSPACE_PORTRAIT = "workspace_portrait";
    private static String WORKSPACE_LANDSCAPE = "workspace_landscape";
    private static String WORKSPACE_LARGE = "workspace_large";
    private static String WORKSPACE_CUSTOM = "workspace_custom";

    private static String MIGRATION_PORTRAIT = "migration_portrait";
    private static String MIGRATION_LANDSCAPE = "migration_landscape";

    private static String MIGRATION_WORKSPACE_PORTRAIT = "migration_workspace_portrait";
    private static String MIGRATION_WORKSPACE_LARGE_PORTRAIT = "migration_workspace_large_portrait";
    private static String MIGRATION_WORKSPACE_LANDSCAPE = "migration_workspace_landscape";

    private static String FOLDER_PORTRAIT = "folder_portrait";
    private static String FOLDER_LANDSCAPE = "folder_landscape";
    private static String FOLDER_LARGE = "folder_large";

    private static float FIRST_RUN_CIRCLE_BUFFER_DPS = 60;
    private static float FIRST_RUN_MAX_CIRCLE_RADIUS_DPS = 180;
    private static float WORKSPACE_INNER_CIRCLE_RADIUS_DPS = 50;
    private static float WORKSPACE_OUTER_CIRCLE_RADIUS_DPS = 60;
    private static float WORKSPACE_CIRCLE_Y_OFFSET_DPS = 30;
    private static float MIGRATION_WORKSPACE_INNER_CIRCLE_RADIUS_DPS = 42;
    private static float MIGRATION_WORKSPACE_OUTER_CIRCLE_RADIUS_DPS = 46;

    private Launcher mLauncher;
    private boolean mIsInitialized;
    private String mDrawIdentifier;
    private Drawable mBackground;

    private int[] mTouchDownPt = new int[2];

    private Drawable mFocusedHotseatApp;
    private ComponentName mFocusedHotseatAppComponent;
    private Rect mFocusedHotseatAppBounds;

    private Paint mErasePaint;
    private Paint mBorderPaint;
    private Paint mBubblePaint;
    private Paint mDotPaint;

    private View mScrimView;
    private int mBackgroundColor;

    private final Rect mInsets = new Rect();

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

        setClickable(true);

    }

    void init(Launcher l, View scrim) {
        if (!mIsInitialized) {
            mLauncher = l;
            mScrimView = scrim;
            mBackgroundColor = 0xcc000000;
            setOnLongClickListener(this);
            setOnClickListener(this);
            setOnTouchListener(this);

            mErasePaint = new Paint();
            mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            mErasePaint.setColor(0xFFFFFF);
            mErasePaint.setAlpha(0);
            mErasePaint.setAntiAlias(true);

            mBorderPaint = new Paint();
            mBorderPaint.setColor(0xFFFFFFFF);
            mBorderPaint.setAntiAlias(true);

            int circleColor = getResources().getColor(
                    R.color.first_run_cling_circle_background_color);
            mBubblePaint = new Paint();
            mBubblePaint.setColor(circleColor);
            mBubblePaint.setAntiAlias(true);

            mDotPaint = new Paint();
            mDotPaint.setColor(0x72BBED);
            mDotPaint.setAntiAlias(true);

            mIsInitialized = true;
        }
    }

    void setFocusedHotseatApp(int drawableId, int appRank, ComponentName cn, String title,
                              String description) {
        // Get the app to draw
        Resources r = getResources();
        int appIconId = drawableId;
        Hotseat hotseat = mLauncher.getHotseat();
        // Skip the focused app in the large layouts
        if (!mDrawIdentifier.equals(WORKSPACE_LARGE) &&
                hotseat != null && appIconId > -1 && appRank > -1 && !title.isEmpty() &&
                !description.isEmpty()) {
            // Set the app bounds
            int x = hotseat.getCellXFromOrder(appRank);
            int y = hotseat.getCellYFromOrder(appRank);
            Rect pos = hotseat.getCellCoordinates(x, y);
            LauncherAppState app = LauncherAppState.getInstance();
            DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
            mFocusedHotseatApp = getResources().getDrawable(appIconId);
            mFocusedHotseatAppComponent = cn;
            mFocusedHotseatAppBounds = new Rect(pos.left, pos.top,
                    pos.left + Utilities.sIconTextureWidth,
                    pos.top + Utilities.sIconTextureHeight);
            Utilities.scaleRectAboutCenter(mFocusedHotseatAppBounds,
                    ((float) grid.hotseatIconSizePx / grid.iconSizePx));

            // Set the title
            TextView v = (TextView) findViewById(R.id.focused_hotseat_app_title);
            if (v != null) {
                v.setText(title);
            }

            // Set the description
            v = (TextView) findViewById(R.id.focused_hotseat_app_description);
            if (v != null) {
                v.setText(description);
            }

            // Show the bubble
            View bubble = findViewById(R.id.focused_hotseat_app_bubble);
            bubble.setVisibility(View.VISIBLE);
        }
    }

    void setOpenFolderRect(Rect r) {
        if (mDrawIdentifier.equals(FOLDER_LANDSCAPE) ||
            mDrawIdentifier.equals(FOLDER_LARGE)) {
            ViewGroup vg = (ViewGroup) findViewById(R.id.folder_bubble);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) vg.getLayoutParams();
            lp.topMargin = r.top - mInsets.bottom;
            lp.leftMargin = r.right;
            vg.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            vg.requestLayout();
        }
    }

    void updateMigrationWorkspaceBubblePosition() {
        DisplayMetrics metrics = new DisplayMetrics();
        mLauncher.getWindowManager().getDefaultDisplay().getMetrics(metrics);

        // Get the page indicator bounds
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        Rect pageIndicatorBounds = grid.getWorkspacePageIndicatorBounds(mInsets);

        if (mDrawIdentifier.equals(MIGRATION_WORKSPACE_PORTRAIT)) {
            View bubble = findViewById(R.id.migration_workspace_cling_bubble);
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) bubble.getLayoutParams();
            lp.bottomMargin = grid.heightPx - pageIndicatorBounds.top;
            bubble.requestLayout();
        } else if (mDrawIdentifier.equals(MIGRATION_WORKSPACE_LARGE_PORTRAIT)) {
            View bubble = findViewById(R.id.content);
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) bubble.getLayoutParams();
            lp.bottomMargin = grid.heightPx - pageIndicatorBounds.top;
            bubble.requestLayout();
        } else if (mDrawIdentifier.equals(MIGRATION_WORKSPACE_LANDSCAPE)) {
            View bubble = findViewById(R.id.content);
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) bubble.getLayoutParams();
            if (grid.isLayoutRtl) {
                lp.leftMargin = pageIndicatorBounds.right;
            } else {
                lp.rightMargin = (grid.widthPx - pageIndicatorBounds.left);
            }
            bubble.requestLayout();
        }
    }

    void updateWorkspaceBubblePosition() {
        DisplayMetrics metrics = new DisplayMetrics();
        mLauncher.getWindowManager().getDefaultDisplay().getMetrics(metrics);

        // Get the cut-out bounds
        LauncherAppState app = LauncherAppState.getInstance();
        DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
        Rect cutOutBounds = getWorkspaceCutOutBounds(metrics);

        if (mDrawIdentifier.equals(WORKSPACE_LARGE)) {
            View bubble = findViewById(R.id.workspace_cling_bubble);
            ViewGroup.MarginLayoutParams lp =
                    (ViewGroup.MarginLayoutParams) bubble.getLayoutParams();
            lp.bottomMargin = grid.heightPx - cutOutBounds.top - mInsets.bottom;
            bubble.requestLayout();
        }
    }

    private Rect getWorkspaceCutOutBounds(DisplayMetrics metrics) {
        int halfWidth = metrics.widthPixels / 2;
        int halfHeight = metrics.heightPixels / 2;
        int yOffset = DynamicGrid.pxFromDp(WORKSPACE_CIRCLE_Y_OFFSET_DPS, metrics);
        if (mDrawIdentifier.equals(WORKSPACE_LARGE)) {
            yOffset = 0;
        }
        int radius = DynamicGrid.pxFromDp(WORKSPACE_OUTER_CIRCLE_RADIUS_DPS, metrics);
        return new Rect(halfWidth - radius, halfHeight - yOffset - radius, halfWidth + radius,
                halfHeight - yOffset + radius);
    }

    void show(boolean animate, int duration) {
        setVisibility(View.VISIBLE);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (mDrawIdentifier.equals(WORKSPACE_PORTRAIT) ||
                mDrawIdentifier.equals(WORKSPACE_LANDSCAPE) ||
                mDrawIdentifier.equals(WORKSPACE_LARGE) ||
                mDrawIdentifier.equals(WORKSPACE_CUSTOM) ||
                mDrawIdentifier.equals(MIGRATION_WORKSPACE_PORTRAIT) ||
                mDrawIdentifier.equals(MIGRATION_WORKSPACE_LARGE_PORTRAIT) ||
                mDrawIdentifier.equals(MIGRATION_WORKSPACE_LANDSCAPE)) {
            View content = getContent();
            content.setAlpha(0f);
            content.animate()
                    .alpha(1f)
                    .setDuration(duration)
                    .setListener(null)
                    .start();
            setAlpha(1f);
        } else {
            if (animate) {
                buildLayer();
                setAlpha(0f);
                animate()
                    .alpha(1f)
                    .setInterpolator(new AccelerateInterpolator())
                    .setDuration(duration)
                    .setListener(null)
                    .start();
            } else {
                setAlpha(1f);
            }
        }

        // Show the scrim if necessary
        if (mScrimView != null) {
            mScrimView.setVisibility(View.VISIBLE);
            mScrimView.setAlpha(0f);
            mScrimView.animate()
                    .alpha(1f)
                    .setDuration(duration)
                    .setListener(null)
                    .start();
        }

        setFocusableInTouchMode(true);
        post(new Runnable() {
            public void run() {
                setFocusable(true);
                requestFocus();
            }
        });
    }

    void hide(final int duration, final Runnable postCb) {
        if (mDrawIdentifier.equals(FIRST_RUN_PORTRAIT) ||
                mDrawIdentifier.equals(FIRST_RUN_LANDSCAPE) ||
                mDrawIdentifier.equals(MIGRATION_PORTRAIT) ||
                mDrawIdentifier.equals(MIGRATION_LANDSCAPE)) {
            View content = getContent();
            content.animate()
                .alpha(0f)
                .setDuration(duration)
                .setListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator animation) {
                        // We are about to trigger the workspace cling, so don't do anything else
                        setVisibility(View.GONE);
                        postCb.run();
                    };
                })
                .start();
        } else {
            animate()
                .alpha(0f)
                .setDuration(duration)
                .setListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator animation) {
                        // We are about to trigger the workspace cling, so don't do anything else
                        setVisibility(View.GONE);
                        postCb.run();
                    };
                })
                .start();
        }

        // Show the scrim if necessary
        if (mScrimView != null) {
            mScrimView.animate()
                .alpha(0f)
                .setDuration(duration)
                .setListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator animation) {
                        mScrimView.setVisibility(View.GONE);
                    };
                })
                .start();
        }
    }

    void cleanup() {
        mBackground = null;
        mIsInitialized = false;
    }

    void bringScrimToFront() {
        if (mScrimView != null) {
            mScrimView.bringToFront();
        }
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        setPadding(insets.left, insets.top, insets.right, insets.bottom);
    }

    View getContent() {
        return findViewById(R.id.content);
    }

    String getDrawIdentifier() {
        return mDrawIdentifier;
    }

    @Override
    public View focusSearch(int direction) {
        return this.focusSearch(this, direction);
    }

    @Override
    public View focusSearch(View focused, int direction) {
        return FocusFinder.getInstance().findNextFocus(this, focused, direction);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return (mDrawIdentifier.equals(WORKSPACE_PORTRAIT)
                || mDrawIdentifier.equals(WORKSPACE_LANDSCAPE)
                || mDrawIdentifier.equals(WORKSPACE_LARGE)
                || mDrawIdentifier.equals(WORKSPACE_CUSTOM));
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (mDrawIdentifier.equals(FOLDER_PORTRAIT) ||
                   mDrawIdentifier.equals(FOLDER_LANDSCAPE) ||
                   mDrawIdentifier.equals(FOLDER_LARGE)) {
            Folder f = mLauncher.getWorkspace().getOpenFolder();
            if (f != null) {
                Rect r = new Rect();
                f.getHitRect(r);
                if (r.contains((int) event.getX(), (int) event.getY())) {
                    return false;
                }
            }
        }
        return super.onTouchEvent(event);
    };

    @Override
    public boolean onTouch(View v, MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchDownPt[0] = (int) ev.getX();
            mTouchDownPt[1] = (int) ev.getY();
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        if (mDrawIdentifier.equals(WORKSPACE_PORTRAIT) ||
                mDrawIdentifier.equals(WORKSPACE_LANDSCAPE) ||
                mDrawIdentifier.equals(WORKSPACE_LARGE)) {
            if (mFocusedHotseatAppBounds != null &&
                mFocusedHotseatAppBounds.contains(mTouchDownPt[0], mTouchDownPt[1])) {
                // Launch the activity that is being highlighted
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setComponent(mFocusedHotseatAppComponent);
                intent.addCategory(Intent.CATEGORY_LAUNCHER);
                mLauncher.startActivity(intent, null);
                mLauncher.getLauncherClings().dismissWorkspaceCling(this);
            }
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (mDrawIdentifier.equals(WORKSPACE_PORTRAIT) ||
                mDrawIdentifier.equals(WORKSPACE_LANDSCAPE) ||
                mDrawIdentifier.equals(WORKSPACE_LARGE)) {
            mLauncher.getLauncherClings().dismissWorkspaceCling(null);
            return true;
        } else if (mDrawIdentifier.equals(MIGRATION_WORKSPACE_PORTRAIT) ||
                mDrawIdentifier.equals(MIGRATION_WORKSPACE_LARGE_PORTRAIT) ||
                mDrawIdentifier.equals(MIGRATION_WORKSPACE_LANDSCAPE)) {
            mLauncher.getLauncherClings().dismissMigrationWorkspaceCling(null);
            return true;
        }
        return false;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mIsInitialized) {
            canvas.save();

            // Get the page indicator bounds
            LauncherAppState app = LauncherAppState.getInstance();
            DeviceProfile grid = app.getDynamicGrid().getDeviceProfile();
            Rect pageIndicatorBounds = grid.getWorkspacePageIndicatorBounds(mInsets);

            // Get the background override if there is one
            if (mBackground == null) {
                if (mDrawIdentifier.equals(WORKSPACE_CUSTOM)) {
                    mBackground = getResources().getDrawable(R.drawable.bg_cling5);
                }
            }
            // Draw the background
            Bitmap eraseBg = null;
            Canvas eraseCanvas = null;
            if (mScrimView != null) {
                // Skip drawing the background
                mScrimView.setBackgroundColor(mBackgroundColor);
            } else if (mBackground != null) {
                mBackground.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                mBackground.draw(canvas);
            } else if (mDrawIdentifier.equals(WORKSPACE_PORTRAIT) ||
                    mDrawIdentifier.equals(WORKSPACE_LANDSCAPE) ||
                    mDrawIdentifier.equals(WORKSPACE_LARGE) ||
                    mDrawIdentifier.equals(MIGRATION_WORKSPACE_PORTRAIT) ||
                    mDrawIdentifier.equals(MIGRATION_WORKSPACE_LARGE_PORTRAIT) ||
                    mDrawIdentifier.equals(MIGRATION_WORKSPACE_LANDSCAPE)) {
                // Initialize the draw buffer (to allow punching through)
                eraseBg = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(),
                        Bitmap.Config.ARGB_8888);
                eraseCanvas = new Canvas(eraseBg);
                eraseCanvas.drawColor(mBackgroundColor);
            } else {
                canvas.drawColor(mBackgroundColor);
            }

            // Draw everything else
            DisplayMetrics metrics = new DisplayMetrics();
            mLauncher.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            float alpha = getAlpha();
            View content = getContent();
            if (content != null) {
                alpha *= content.getAlpha();
            }
            if (mDrawIdentifier.equals(FIRST_RUN_PORTRAIT) ||
                    mDrawIdentifier.equals(FIRST_RUN_LANDSCAPE)) {
                // Draw the circle
                View bubbleContent = findViewById(R.id.bubble_content);
                Rect bubbleRect = new Rect();
                bubbleContent.getGlobalVisibleRect(bubbleRect);
                mBubblePaint.setAlpha((int) (255 * alpha));
                float buffer = DynamicGrid.pxFromDp(FIRST_RUN_CIRCLE_BUFFER_DPS, metrics);
                float maxRadius = DynamicGrid.pxFromDp(FIRST_RUN_MAX_CIRCLE_RADIUS_DPS, metrics);
                float radius = Math.min(maxRadius, (bubbleContent.getMeasuredWidth() + buffer) / 2);
                canvas.drawCircle(metrics.widthPixels / 2,
                        bubbleRect.centerY(), radius,
                        mBubblePaint);
            } else if (mDrawIdentifier.equals(WORKSPACE_PORTRAIT) ||
                    mDrawIdentifier.equals(WORKSPACE_LANDSCAPE) ||
                    mDrawIdentifier.equals(WORKSPACE_LARGE)) {
                Rect cutOutBounds = getWorkspaceCutOutBounds(metrics);
                // Draw the outer circle
                mErasePaint.setAlpha(128);
                eraseCanvas.drawCircle(cutOutBounds.centerX(), cutOutBounds.centerY(),
                        DynamicGrid.pxFromDp(WORKSPACE_OUTER_CIRCLE_RADIUS_DPS, metrics),
                        mErasePaint);
                // Draw the inner circle
                mErasePaint.setAlpha(0);
                eraseCanvas.drawCircle(cutOutBounds.centerX(), cutOutBounds.centerY(),
                        DynamicGrid.pxFromDp(WORKSPACE_INNER_CIRCLE_RADIUS_DPS, metrics),
                        mErasePaint);
                canvas.drawBitmap(eraseBg, 0, 0, null);
                eraseCanvas.setBitmap(null);
                eraseBg = null;

                // Draw the focused hotseat app icon
                if (mFocusedHotseatAppBounds != null && mFocusedHotseatApp != null) {
                    mFocusedHotseatApp.setBounds(mFocusedHotseatAppBounds.left,
                            mFocusedHotseatAppBounds.top, mFocusedHotseatAppBounds.right,
                            mFocusedHotseatAppBounds.bottom);
                    mFocusedHotseatApp.setAlpha((int) (255 * alpha));
                    mFocusedHotseatApp.draw(canvas);
                }
            } else if (mDrawIdentifier.equals(MIGRATION_WORKSPACE_PORTRAIT) ||
                    mDrawIdentifier.equals(MIGRATION_WORKSPACE_LARGE_PORTRAIT) ||
                    mDrawIdentifier.equals(MIGRATION_WORKSPACE_LANDSCAPE)) {
                int offset = DynamicGrid.pxFromDp(WORKSPACE_CIRCLE_Y_OFFSET_DPS, metrics);
                // Draw the outer circle
                eraseCanvas.drawCircle(pageIndicatorBounds.centerX(),
                        pageIndicatorBounds.centerY(),
                        DynamicGrid.pxFromDp(MIGRATION_WORKSPACE_OUTER_CIRCLE_RADIUS_DPS, metrics),
                        mBorderPaint);
                // Draw the inner circle
                mErasePaint.setAlpha(0);
                eraseCanvas.drawCircle(pageIndicatorBounds.centerX(),
                        pageIndicatorBounds.centerY(),
                        DynamicGrid.pxFromDp(MIGRATION_WORKSPACE_INNER_CIRCLE_RADIUS_DPS, metrics),
                        mErasePaint);
                canvas.drawBitmap(eraseBg, 0, 0, null);
                eraseCanvas.setBitmap(null);
                eraseBg = null;
            }
            canvas.restore();
        }

        // Draw the rest of the cling
        super.dispatchDraw(canvas);
    };
}
