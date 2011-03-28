/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import android.view.animation.DecelerateInterpolator;

import com.android.launcher.R;

public class DragView extends View {
    private Bitmap mBitmap;
    private Paint mPaint;
    private int mRegistrationX;
    private int mRegistrationY;

    private int mDragRegionLeft = 0;
    private int mDragRegionTop = 0;
    private int mDragRegionWidth;
    private int mDragRegionHeight;

    ValueAnimator mAnim;
    private float mOffsetX = 0.0f;
    private float mOffsetY = 0.0f;

    private WindowManager.LayoutParams mLayoutParams;
    private WindowManager mWindowManager;

    /**
     * A callback to be called the first time this view is drawn.
     * This allows the originator of the drag to dim or hide the original view as soon
     * as the DragView is drawn.
     */
    private Runnable mOnDrawRunnable = null;

    /**
     * Construct the drag view.
     * <p>
     * The registration point is the point inside our view that the touch events should
     * be centered upon.
     *
     * @param context A context
     * @param bitmap The view that we're dragging around.  We scale it up when we draw it.
     * @param registrationX The x coordinate of the registration point.
     * @param registrationY The y coordinate of the registration point.
     */
    public DragView(Context context, Bitmap bitmap, int registrationX, int registrationY,
            int left, int top, int width, int height) {
        super(context);

        final Resources res = getResources();
        final int dragScale = res.getInteger(R.integer.config_dragViewExtraPixels);

        mWindowManager = WindowManagerImpl.getDefault();

        Matrix scale = new Matrix();
        final float scaleFactor = (width + dragScale) / width;
        if (scaleFactor != 1.0f) {
            scale.setScale(scaleFactor, scaleFactor);
        }

        final int offsetX = res.getDimensionPixelSize(R.dimen.dragViewOffsetX);
        final int offsetY = res.getDimensionPixelSize(R.dimen.dragViewOffsetY);

        // Animate the view into the correct position
        mAnim = ValueAnimator.ofFloat(0.0f, 1.0f);
        mAnim.setDuration(110);
        mAnim.setInterpolator(new DecelerateInterpolator(2.5f));
        mAnim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float value = (Float) animation.getAnimatedValue();

                final int deltaX = (int) ((value * offsetX) - mOffsetX);
                final int deltaY = (int) ((value * offsetY) - mOffsetY);

                mOffsetX += deltaX;
                mOffsetY += deltaY;

                if (getParent() == null) {
                    animation.cancel();
                } else {
                    WindowManager.LayoutParams lp = mLayoutParams;
                    lp.x += deltaX;
                    lp.y += deltaY;
                    mWindowManager.updateViewLayout(DragView.this, lp);
                }
            }
        });

        mBitmap = Bitmap.createBitmap(bitmap, left, top, width, height, scale, true);
        setDragRegion(0, 0, width, height);

        // The point in our scaled bitmap that the touch events are located
        mRegistrationX = registrationX;
        mRegistrationY = registrationY;

        // Force a measure, because Workspace uses getMeasuredHeight() before the layout pass
        int ms = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        measure(ms, ms);
    }

    public float getOffsetY() {
        return mOffsetY;
    }

    public void setDragRegion(int left, int top, int width, int height) {
        mDragRegionLeft = left;
        mDragRegionTop = top;
        mDragRegionWidth = width;
        mDragRegionHeight = height;
    }

    public void setOnDrawRunnable(Runnable r) {
        mOnDrawRunnable = r;
    }

    public int getDragRegionLeft() {
        return mDragRegionLeft;
    }

    public int getDragRegionTop() {
        return mDragRegionTop;
    }

    public int getDragRegionWidth() {
        return mDragRegionWidth;
    }

    public int getDragRegionHeight() {
        return mDragRegionHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mBitmap.getWidth(), mBitmap.getHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (false) {
            // for debugging
            Paint p = new Paint();
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xaaffffff);
            canvas.drawRect(0, 0, getWidth(), getHeight(), p);
        }

        // Call the callback if we haven't already been detached
        if (getParent() != null) {
            if (mOnDrawRunnable != null) {
                mOnDrawRunnable.run();
                mOnDrawRunnable = null;
            }
        }

        canvas.drawBitmap(mBitmap, 0.0f, 0.0f, mPaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mBitmap.recycle();
    }

    public void setPaint(Paint paint) {
        mPaint = paint;
        invalidate();
    }

    /**
     * Create a window containing this view and show it.
     *
     * @param windowToken obtained from v.getWindowToken() from one of your views
     * @param touchX the x coordinate the user touched in screen coordinates
     * @param touchY the y coordinate the user touched in screen coordinates
     */
    public void show(IBinder windowToken, int touchX, int touchY) {
        WindowManager.LayoutParams lp;
        int pixelFormat;

        pixelFormat = PixelFormat.TRANSLUCENT;

        lp = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                touchX - mRegistrationX, touchY - mRegistrationY,
                WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    /*| WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM*/,
                pixelFormat);
//        lp.token = mStatusBarView.getWindowToken();
        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.token = windowToken;
        lp.setTitle("DragView");
        mLayoutParams = lp;

        mWindowManager.addView(this, lp);

        mAnim.start();
    }
    
    /**
     * Move the window containing this view.
     *
     * @param touchX the x coordinate the user touched in screen coordinates
     * @param touchY the y coordinate the user touched in screen coordinates
     */
    void move(int touchX, int touchY) {
        WindowManager.LayoutParams lp = mLayoutParams;
        lp.x = touchX - mRegistrationX + (int) mOffsetX;
        lp.y = touchY - mRegistrationY + (int) mOffsetY;
        mWindowManager.updateViewLayout(this, lp);
    }

    void remove() {
        mWindowManager.removeView(this);
    }

    int[] getPosition(int[] result) {
        WindowManager.LayoutParams lp = mLayoutParams;
        if (result == null) result = new int[2];
        result[0] = lp.x;
        result[1] = lp.y;
        return result;
    }
}

