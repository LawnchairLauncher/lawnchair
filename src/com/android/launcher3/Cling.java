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
import android.app.ActivityOptions;
import android.content.Context;
import android.content.ComponentName;
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
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

public class Cling extends FrameLayout implements Insettable, View.OnClickListener,
        View.OnLongClickListener, View.OnTouchListener {

    static final String FIRST_RUN_CLING_DISMISSED_KEY = "cling_gel.first_run.dismissed";
    static final String WORKSPACE_CLING_DISMISSED_KEY = "cling_gel.workspace.dismissed";
    static final String FOLDER_CLING_DISMISSED_KEY = "cling_gel.folder.dismissed";

    private static String FIRST_RUN_PORTRAIT = "first_run_portrait";
    private static String FIRST_RUN_LANDSCAPE = "first_run_landscape";

    private static String WORKSPACE_PORTRAIT = "workspace_portrait";
    private static String WORKSPACE_LANDSCAPE = "workspace_landscape";
    private static String WORKSPACE_LARGE = "workspace_large";
    private static String WORKSPACE_CUSTOM = "workspace_custom";

    private static String FOLDER_PORTRAIT = "folder_portrait";
    private static String FOLDER_LANDSCAPE = "folder_landscape";
    private static String FOLDER_LARGE = "folder_large";

    private static float FIRST_RUN_CIRCLE_BUFFER_DPS = 60;
    private static float WORKSPACE_INNER_CIRCLE_RADIUS_DPS = 50;
    private static float WORKSPACE_OUTER_CIRCLE_RADIUS_DPS = 60;
    private static float WORKSPACE_CIRCLE_Y_OFFSET_DPS = 30;

    private Launcher mLauncher;
    private boolean mIsInitialized;
    private String mDrawIdentifier;
    private Drawable mBackground;

    private int[] mTouchDownPt = new int[2];

    private Drawable mFocusedHotseatApp;
    private ComponentName mFocusedHotseatAppComponent;
    private Rect mFocusedHotseatAppBounds;

    private Paint mErasePaint;
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
            mBackgroundColor = 0xdd000000;
            setOnLongClickListener(this);
            setOnClickListener(this);
            setOnTouchListener(this);

            mErasePaint = new Paint();
            mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            mErasePaint.setColor(0xFFFFFF);
            mErasePaint.setAlpha(0);
            mErasePaint.setAntiAlias(true);

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
        if (hotseat != null && appIconId > -1 && appRank > -1 && !title.isEmpty() &&
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
                    (grid.hotseatIconSize / grid.iconSize));

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

    void show(boolean animate, int duration) {
        setVisibility(View.VISIBLE);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (mDrawIdentifier.equals(WORKSPACE_PORTRAIT) ||
                mDrawIdentifier.equals(WORKSPACE_LANDSCAPE) ||
                mDrawIdentifier.equals(WORKSPACE_LARGE) ||
                mDrawIdentifier.equals(WORKSPACE_CUSTOM)) {
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
                mDrawIdentifier.equals(FIRST_RUN_LANDSCAPE)) {
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
                mLauncher.dismissWorkspaceCling(this);
            }
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (mDrawIdentifier.equals(WORKSPACE_PORTRAIT) ||
                mDrawIdentifier.equals(WORKSPACE_LANDSCAPE) ||
                mDrawIdentifier.equals(WORKSPACE_LARGE)) {
            mLauncher.dismissWorkspaceCling(null);
            return true;
        }
        return false;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mIsInitialized) {
            canvas.save();

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
                    mDrawIdentifier.equals(WORKSPACE_LARGE)) {
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
                canvas.drawCircle(metrics.widthPixels / 2,
                        bubbleRect.centerY(),
                        (bubbleContent.getMeasuredWidth() + buffer) / 2,
                        mBubblePaint);
            } else if (mDrawIdentifier.equals(WORKSPACE_PORTRAIT) ||
                    mDrawIdentifier.equals(WORKSPACE_LANDSCAPE) ||
                    mDrawIdentifier.equals(WORKSPACE_LARGE)) {
                int offset = DynamicGrid.pxFromDp(WORKSPACE_CIRCLE_Y_OFFSET_DPS, metrics);
                mErasePaint.setAlpha((int) (128));
                eraseCanvas.drawCircle(metrics.widthPixels / 2,
                        metrics.heightPixels / 2 - offset,
                        DynamicGrid.pxFromDp(WORKSPACE_OUTER_CIRCLE_RADIUS_DPS, metrics),
                        mErasePaint);
                mErasePaint.setAlpha(0);
                eraseCanvas.drawCircle(metrics.widthPixels / 2,
                        metrics.heightPixels / 2 - offset,
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
            }

            canvas.restore();
        }

        // Draw the rest of the cling
        super.dispatchDraw(canvas);
    };
}
