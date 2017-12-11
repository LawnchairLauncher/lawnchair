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

package ch.deletescape.lawnchair.dragndrop;

import android.animation.FloatArrayEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.support.animation.DynamicAnimation;
import android.support.animation.SpringAnimation;
import android.support.animation.SpringForce;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.Arrays;

import ch.deletescape.lawnchair.FastBitmapDrawable;
import ch.deletescape.lawnchair.ItemInfo;
import ch.deletescape.lawnchair.Launcher;
import ch.deletescape.lawnchair.LauncherAnimUtils;
import ch.deletescape.lawnchair.LauncherAppState;
import ch.deletescape.lawnchair.LauncherModel;
import ch.deletescape.lawnchair.LauncherSettings;
import ch.deletescape.lawnchair.R;
import ch.deletescape.lawnchair.Utilities;
import ch.deletescape.lawnchair.compat.LauncherActivityInfoCompat;
import ch.deletescape.lawnchair.compat.LauncherAppsCompat;
import ch.deletescape.lawnchair.pixelify.ClockIconDrawable;
import ch.deletescape.lawnchair.util.IconNormalizer;
import ch.deletescape.lawnchair.util.Thunk;

public class DragView extends FrameLayout {
    public static final int COLOR_CHANGE_DURATION = 120;
    public static final int VIEW_ZOOM_DURATION = 150;

    @Thunk
    static float sDragAlpha = 1f;
    private final float mInitialScale;
    private final Launcher mLauncher;

    private Bitmap mBitmap;
    private Bitmap mCrossFadeBitmap;
    @Thunk
    Paint mPaint, mMaskPaint;
    private final int mRegistrationX;
    private final int mRegistrationY;

    private Point mDragVisualizeOffset = null;
    private Rect mDragRegion = null;
    private final DragLayer mDragLayer;
    @Thunk
    final DragController mDragController;
    private boolean mHasDrawn = false;
    @Thunk
    float mCrossFadeProgress = 0f;

    ValueAnimator mAnim;
    // The intrinsic icon scale factor is the scale factor for a drag icon over the workspace
    // size.  This is ignored for non-icons.
    private float mIntrinsicIconScale = 1f;

    @Thunk
    float[] mCurrentFilter;
    private ValueAnimator mFilterAnimator;

    private int mLastTouchX;
    private int mLastTouchY;
    private int mAnimatedShiftX;
    private int mAnimatedShiftY;
    private boolean mAnimationStarted;
    private final int[] mTempLoc = new int[2];

    private ImageView mFgImageView;
    private ImageView mBgImageView;

    private SpringAnimation mSpringX;
    private SpringAnimation mSpringY;

    private float mDelta;

    private Canvas mTmpCanvas;
    private Bitmap mTmpBitmap;

    /**
     * Construct the drag view.
     * <p>
     * The registration point is the point inside our view that the touch events should
     * be centered upon.
     *
     * @param launcher      The Launcher instance
     * @param bitmap        The view that we're dragging around.  We scale it up when we draw it.
     * @param registrationX The x coordinate of the registration point.
     * @param registrationY The y coordinate of the registration point.
     */
    public DragView(Launcher launcher, Bitmap bitmap, int registrationX, int registrationY,
                    final float initialScale, final float finalScaleDps) {
        super(launcher);
        mLauncher = launcher;
        mDragLayer = launcher.getDragLayer();
        mDragController = launcher.getDragController();

        final float scale = (bitmap.getWidth() + finalScaleDps) / bitmap.getWidth();

        // Set the initial scale to avoid any jumps
        setScaleX(initialScale);
        setScaleY(initialScale);

        // Animate the view into the correct position
        mAnim = LauncherAnimUtils.ofFloat(0f, 1f);
        mAnim.setDuration(VIEW_ZOOM_DURATION);
        mAnim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float value = (Float) animation.getAnimatedValue();

                setScaleX(initialScale + (value * (scale - initialScale)));
                setScaleY(initialScale + (value * (scale - initialScale)));
                if (sDragAlpha != 1f) {
                    setAlpha(sDragAlpha * value + (1f - value));
                }

                if (getParent() == null) {
                    animation.cancel();
                }
            }
        });

        mBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight());
        setDragRegion(new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()));

        // The point in our scaled bitmap that the touch events are located
        mRegistrationX = registrationX;
        mRegistrationY = registrationY;

        // Force a measure, because Workspace uses getMeasuredHeight() before the layout pass
        int ms = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        measure(ms, ms);
        mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        mMaskPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        mMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));

        setElevation(getResources().getDimension(R.dimen.drag_elevation));
        mInitialScale = initialScale;
        setWillNotDraw(false);
        mDelta = (int) (getResources().getDisplayMetrics().density * 8.0f);
    }

    /**
     * Sets the scale of the view over the normal workspace icon size.
     */
    public void setIntrinsicIconScaleFactor(float scale) {
        mIntrinsicIconScale = scale;
    }

    public float getIntrinsicIconScaleFactor() {
        return mIntrinsicIconScale;
    }

    public int getDragRegionTop() {
        return mDragRegion.top;
    }

    public int getDragRegionWidth() {
        return mDragRegion.width();
    }

    public void setDragVisualizeOffset(Point p) {
        mDragVisualizeOffset = p;
    }

    public Point getDragVisualizeOffset() {
        return mDragVisualizeOffset;
    }

    public void setDragRegion(Rect r) {
        mDragRegion = r;
    }

    public Rect getDragRegion() {
        return mDragRegion;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mHasDrawn = true;
        boolean crossFade = mCrossFadeProgress > 0 && mCrossFadeBitmap != null;
        if (crossFade) {
            int alpha = crossFade ? (int) (255 * (1 - mCrossFadeProgress)) : 255;
            mPaint.setAlpha(alpha);
        }
        canvas.drawBitmap(mBitmap, 0.0f, 0.0f, mPaint);
        if (crossFade) {
            mPaint.setAlpha((int) (255 * mCrossFadeProgress));
            final int saveCount = canvas.save(Canvas.MATRIX_SAVE_FLAG);
            float sX = (mBitmap.getWidth() * 1.0f) / mCrossFadeBitmap.getWidth();
            float sY = (mBitmap.getHeight() * 1.0f) / mCrossFadeBitmap.getHeight();
            canvas.scale(sX, sY);
            canvas.drawBitmap(mCrossFadeBitmap, 0.0f, 0.0f, mPaint);
            canvas.restoreToCount(saveCount);
        }
    }

    public void setCrossFadeBitmap(Bitmap crossFadeBitmap) {
        mCrossFadeBitmap = crossFadeBitmap;
    }

    public void crossFade(int duration) {
        ValueAnimator va = LauncherAnimUtils.ofFloat(0f, 1f);
        va.setDuration(duration);
        va.setInterpolator(new DecelerateInterpolator(1.5f));
        va.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mCrossFadeProgress = animation.getAnimatedFraction();
                invalidate();
            }
        });
        va.start();
    }

    public void setColor(int color) {
        if (mPaint == null) {
            mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        }
        if (color != 0) {
            ColorMatrix m1 = new ColorMatrix();
            m1.setSaturation(0);

            ColorMatrix m2 = new ColorMatrix();
            setColorScale(color, m2);
            m1.postConcat(m2);

            animateFilterTo(m1.getArray());
        } else {
            if (mCurrentFilter == null) {
                mPaint.setColorFilter(null);
                invalidate();
            } else {
                animateFilterTo(new ColorMatrix().getArray());
            }
        }
    }

    private void animateFilterTo(float[] targetFilter) {
        float[] oldFilter = mCurrentFilter == null ? new ColorMatrix().getArray() : mCurrentFilter;
        mCurrentFilter = Arrays.copyOf(oldFilter, oldFilter.length);

        if (mFilterAnimator != null) {
            mFilterAnimator.cancel();
        }
        mFilterAnimator = ValueAnimator.ofObject(new FloatArrayEvaluator(mCurrentFilter),
                oldFilter, targetFilter);
        mFilterAnimator.setDuration(COLOR_CHANGE_DURATION);
        mFilterAnimator.addUpdateListener(new AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                mPaint.setColorFilter(new ColorMatrixColorFilter(mCurrentFilter));
                invalidate();
            }
        });
        mFilterAnimator.start();
    }

    public void animateTo(int i, int i2, Runnable runnable, int i3) {
        this.mTempLoc[0] = i - this.mRegistrationX;
        this.mTempLoc[1] = i2 - this.mRegistrationY;
        this.mDragLayer.animateViewIntoPosition(this, this.mTempLoc, 1.0f, this.mInitialScale, this.mInitialScale, 0, runnable, i3);
    }

    public boolean hasDrawn() {
        return mHasDrawn;
    }

    @Override
    public void setAlpha(float alpha) {
        super.setAlpha(alpha);
        mPaint.setAlpha((int) (255 * alpha));
        invalidate();
    }

    /**
     * Create a window containing this view and show it.
     *
     * @param touchX the x coordinate the user touched in DragLayer coordinates
     * @param touchY the y coordinate the user touched in DragLayer coordinates
     */
    public void show(int touchX, int touchY) {
        mDragLayer.addView(this);

        // Start the pick-up animation
        DragLayer.LayoutParams lp = new DragLayer.LayoutParams(0, 0);
        lp.width = mBitmap.getWidth();
        lp.height = mBitmap.getHeight();
        lp.customPosition = true;
        setLayoutParams(lp);
        move(touchX, touchY);
        // Post the animation to skip other expensive work happening on the first frame
        post(new Runnable() {
            @Override
            public void run() {
                mAnimationStarted = true;
                mAnim.start();
            }
        });
    }

    public void cancelAnimation() {
        if (mAnim != null && mAnim.isRunning()) {
            mAnim.cancel();
        }
    }

    /**
     * Move the window containing this view.
     *
     * @param touchX the x coordinate the user touched in DragLayer coordinates
     * @param touchY the y coordinate the user touched in DragLayer coordinates
     */
    public void move(int touchX, int touchY) {
        if (touchX > 0 && touchY > 0 && mLastTouchX > 0 && mLastTouchY > 0) {
            applySpring(mLastTouchX - touchX, mLastTouchY - touchY);
        }
        mLastTouchX = touchX;
        mLastTouchY = touchY;
        applyTranslation();
    }

    public void shift(final int shiftX, final int shiftY) {
        mAnimatedShiftX = shiftX;
        mAnimatedShiftY = shiftY;
        applyTranslation();
    }

    public void animateShift(int shiftX, int shiftY) {
        animateShift(shiftX, shiftY, false);
    }

    public void animateShift(int shiftX, int shiftY, final boolean inverse) {
        final int baseShiftX = mAnimatedShiftX;
        final int baseShiftY = mAnimatedShiftY;
        final int targetShiftX = shiftX - baseShiftX;
        final int targetShiftY = shiftY - baseShiftY;
        mAnimatedShiftX = shiftX;
        mAnimatedShiftY = shiftY;
        applyTranslation();
        if (!mAnim.isRunning()) {
            if (mAnimationStarted) {
                mAnim = LauncherAnimUtils.ofFloat(0f, 1f);
                mAnim.setDuration(VIEW_ZOOM_DURATION);
            }
            mAnim.start();
        }
        mAnim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = inverse ? animation.getAnimatedFraction() : (1 - animation.getAnimatedFraction());
                mAnimatedShiftX = baseShiftX + (int) (fraction * targetShiftX);
                mAnimatedShiftY = baseShiftY + (int) (fraction * targetShiftY);
                applyTranslation();
            }
        });
    }

    private void applyTranslation() {
        setTranslationX(mLastTouchX - mRegistrationX + mAnimatedShiftX);
        setTranslationY(mLastTouchY - mRegistrationY + mAnimatedShiftY);
    }

    public void remove() {
        if (getParent() != null) {
            mDragLayer.removeView(this);
        }
    }

    public static void setColorScale(int color, ColorMatrix target) {
        target.setScale(Color.red(color) / 255f, Color.green(color) / 255f,
                Color.blue(color) / 255f, Color.alpha(color) / 255f);
    }

    public void setItemInfo(final ItemInfo itemInfo) {
        if (!Utilities.ATLEAST_NOUGAT)
            return;
        if (itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
            new Handler(LauncherModel.getWorkerLooper()).postAtFrontOfQueue(new Runnable() {
                public void run() {
                    LauncherAppState instance = LauncherAppState.getInstance();
                    Object[] objArr = new Object[1];
                    Drawable fullDrawable = getFullDrawable(itemInfo, instance, objArr);
                    if (Utilities.isAdaptive(fullDrawable)) {
                        int width = mBitmap.getWidth();
                        int height = mBitmap.getHeight();
                        float dimension = mLauncher.getResources().getDimension(R.dimen.blur_size_medium_outline);
                        float scale = IconNormalizer.getInstance().getScale(fullDrawable, null) * ((((float) width) - dimension) / ((float) width));
                        fullDrawable.setBounds(0, 0, width, height);
                        mFgImageView = setupImageView(Utilities.getForeground(fullDrawable), scale);
                        mBgImageView = setupImageView(Utilities.getBackground(fullDrawable), scale);
                        mSpringX = setupSpringAnimation((-width) / 4, width / 4, DynamicAnimation.TRANSLATION_X);
                        mSpringY = setupSpringAnimation((-height) / 4, height / 4, DynamicAnimation.TRANSLATION_Y);
                        mTmpBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                        mTmpCanvas = new Canvas(mTmpBitmap);
                        Handler handler = new Handler(Looper.getMainLooper());
                        handler.post(new Runnable() {
                            public void run() {
                                addView(mBgImageView);
                                addView(mFgImageView);
                                setWillNotDraw(true);
                                if (itemInfo.isDisabled()) {
                                    FastBitmapDrawable fastBitmapDrawable = new FastBitmapDrawable(null);
                                    ColorFilter colorFilter = fastBitmapDrawable.getColorFilter();
                                    mBgImageView.setColorFilter(colorFilter);
                                    mFgImageView.setColorFilter(colorFilter);
                                }
                            }
                        });
                    }
                }
            });
        }
    }

    private SpringAnimation setupSpringAnimation(int minValue, int maxValue, DynamicAnimation.ViewProperty iVar) {
        SpringAnimation springAnimation = new SpringAnimation(mFgImageView, iVar, 0);
        springAnimation.setMinValue((float) minValue).setMaxValue((float) maxValue);
        springAnimation.setSpring(new SpringForce(0).setDampingRatio(1).setStiffness(4000));
        return springAnimation;
    }

    private ImageView setupImageView(Drawable drawable, float f) {
        FrameLayout.LayoutParams layoutParams =
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ImageView imageView = new ImageView(getContext());
        imageView.setLayoutParams(layoutParams);
        imageView.setScaleType(ImageView.ScaleType.FIT_XY);
        imageView.setScaleX(f);
        imageView.setScaleY(f);
        imageView.setImageDrawable(drawable);
        return imageView;
    }

    private Drawable getFullDrawable(ItemInfo itemInfo, LauncherAppState launcherAppState, Object[] objArr) {
        if (itemInfo.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
            LauncherActivityInfoCompat resolveActivity = LauncherAppsCompat.getInstance(mLauncher).resolveActivity(itemInfo.getIntent(), itemInfo.user);
            objArr[0] = resolveActivity;
            if (resolveActivity != null) {
                if (Utilities.isAnimatedClock(getContext(), resolveActivity.getComponentName()))
                    return ClockIconDrawable.Companion.create(getContext());
                return launcherAppState.getIconCache().getFullResIcon(resolveActivity, false);
            }
            return null;
        } else {
            return null;
        }
    }

    private Path getMaskPath(Drawable drawable, float f) {
        Matrix matrix = new Matrix();
        float f2 = 0.97f * f;
        matrix.setScale(f2, f2, (float) drawable.getBounds().centerX(), (float) drawable.getBounds().centerY());
        Path path = new Path();
        Utilities.getIconMask(drawable).transform(matrix, path);
        return path;
    }

    private void applySpring(int x, int y) {
        if (mSpringX != null && mSpringY != null) {
            mSpringX.animateToFinalPosition(Utilities.boundToRange(x, -mDelta, mDelta));
            mSpringY.animateToFinalPosition(Utilities.boundToRange(y, -mDelta, mDelta));
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        int width = right - left;
        int height = bottom - top;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout((-width) / 4, (-height) / 4, (width / 4) + width, (height / 4) + height);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = mBitmap.getWidth();
        int height = mBitmap.getHeight();
        setMeasuredDimension(width, height);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(width, height);
        }
    }

    protected void dispatchDraw(Canvas canvas) {
        if (mTmpCanvas != null) {
            super.dispatchDraw(mTmpCanvas);
            mTmpCanvas.drawBitmap(mBitmap, 0, 0, mMaskPaint);
            canvas.drawBitmap(mTmpBitmap, 0, 0, mPaint);
            return;
        }
        super.dispatchDraw(canvas);
    }

}
