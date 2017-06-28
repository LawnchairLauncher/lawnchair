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

package com.android.launcher3.dragndrop;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.FloatArrayEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.pm.LauncherActivityInfo;
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
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.animation.DynamicAnimation;
import android.support.animation.SpringAnimation;
import android.support.animation.SpringForce;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.ShortcutConfigActivityInfo;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.graphics.IconNormalizer;
import com.android.launcher3.graphics.LauncherIcons;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.shortcuts.ShortcutInfoCompat;
import com.android.launcher3.shortcuts.ShortcutKey;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.widget.PendingAddShortcutInfo;

import java.util.Arrays;
import java.util.List;

public class DragView extends FrameLayout {
    public static final int COLOR_CHANGE_DURATION = 120;
    public static final int VIEW_ZOOM_DURATION = 150;

    @Thunk static float sDragAlpha = 1f;

    private Bitmap mBitmap;
    private Bitmap mCrossFadeBitmap;
    @Thunk Paint mPaint;
    private final int mBlurSizeOutline;
    private final int mRegistrationX;
    private final int mRegistrationY;
    private final float mInitialScale;
    private final int[] mTempLoc = new int[2];

    private Point mDragVisualizeOffset = null;
    private Rect mDragRegion = null;
    private final Launcher mLauncher;
    private final DragLayer mDragLayer;
    @Thunk final DragController mDragController;
    private boolean mHasDrawn = false;
    @Thunk float mCrossFadeProgress = 0f;
    private boolean mAnimationCancelled = false;

    ValueAnimator mAnim;
    // The intrinsic icon scale factor is the scale factor for a drag icon over the workspace
    // size.  This is ignored for non-icons.
    private float mIntrinsicIconScale = 1f;

    @Thunk float[] mCurrentFilter;
    private ValueAnimator mFilterAnimator;

    private int mLastTouchX;
    private int mLastTouchY;
    private int mAnimatedShiftX;
    private int mAnimatedShiftY;

    // Below variable only needed IF FeatureFlags.LAUNCHER3_SPRING_ICONS is {@code true}
    private SpringAnimation mSpringX, mSpringY;
    private ImageView mFgImageView, mBgImageView;
    private Path mScaledMaskPath;
    private Drawable mBadge;

    // Following three values are fine tuned with motion ux designer
    private final static int STIFFNESS = 4000;
    private final static float DAMPENING_RATIO = 1f;
    private final static int PARALLAX_MAX_IN_DP = 8;
    private final int mDelta;

    /**
     * Construct the drag view.
     * <p>
     * The registration point is the point inside our view that the touch events should
     * be centered upon.
     * @param launcher The Launcher instance
     * @param bitmap The view that we're dragging around.  We scale it up when we draw it.
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

        mAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mAnimationCancelled) {
                    mDragController.onDragViewAnimationEnd();
                }
            }
        });

        mBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight());
        setDragRegion(new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()));

        // The point in our scaled bitmap that the touch events are located
        mRegistrationX = registrationX;
        mRegistrationY = registrationY;

        mInitialScale = initialScale;

        // Force a measure, because Workspace uses getMeasuredHeight() before the layout pass
        int ms = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        measure(ms, ms);
        mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

        mBlurSizeOutline = getResources().getDimensionPixelSize(R.dimen.blur_size_medium_outline);
        setElevation(getResources().getDimension(R.dimen.drag_elevation));
        setWillNotDraw(false);
        mDelta = (int)(getResources().getDisplayMetrics().density * PARALLAX_MAX_IN_DP);
    }

    /**
     * Initialize {@code #mIconDrawable} only if the icon type is app icon (not shortcut or folder).
     */
    @TargetApi(Build.VERSION_CODES.O)
    public void setItemInfo(final ItemInfo info) {
        if (!(FeatureFlags.LAUNCHER3_SPRING_ICONS && Utilities.isAtLeastO())) {
            return;
        }
        if (info.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPLICATION &&
                info.itemType != LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            return;
        }
        // Load the adaptive icon on a background thread and add the view in ui thread.
        final Looper workerLooper = LauncherModel.getWorkerLooper();
        new Handler(workerLooper).postAtFrontOfQueue(new Runnable() {
            @Override
            public void run() {
                LauncherAppState appState = LauncherAppState.getInstance(mLauncher);
                Object[] outObj = new Object[1];
                Drawable dr = getFullDrawable(info, appState, outObj);

                if (dr instanceof AdaptiveIconDrawable) {
                    int w = mBitmap.getWidth();
                    int h = mBitmap.getHeight();
                    AdaptiveIconDrawable adaptiveIcon = (AdaptiveIconDrawable) dr;
                    adaptiveIcon.setBounds(0, 0, w, h);
                    float blurSizeOutline = mLauncher.getResources()
                            .getDimension(R.dimen.blur_size_medium_outline);
                    float normalizationScale = IconNormalizer.getInstance(mLauncher)
                            .getScale(adaptiveIcon, null, null, null) * ((w - blurSizeOutline) / w);

                    final Path mask = getMaskPath(adaptiveIcon, normalizationScale);
                    mFgImageView = setupImageView(adaptiveIcon.getForeground(), normalizationScale);
                    mBgImageView = setupImageView(adaptiveIcon.getBackground(), normalizationScale);
                    mSpringX = setupSpringAnimation(-w/4, w/4, DynamicAnimation.TRANSLATION_X);
                    mSpringY = setupSpringAnimation(-h/4, h/4, DynamicAnimation.TRANSLATION_Y);

                    mBadge = getBadge(info, appState, outObj[0]);
                    int blurMargin = (int) blurSizeOutline / 2;
                    mBadge.setBounds(blurMargin, blurMargin, w - blurMargin, h - blurMargin);

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            // Assign the variable on the UI thread to avoid race conditions.
                            mScaledMaskPath = mask;
                            addView(mBgImageView);
                            addView(mFgImageView);
                            setWillNotDraw(true);

                            if (info.isDisabled()) {
                                FastBitmapDrawable d = new FastBitmapDrawable(null);
                                d.setIsDisabled(true);
                                ColorFilter cf = d.getColorFilter();
                                mBgImageView.setColorFilter(cf);
                                mFgImageView.setColorFilter(cf);
                                mBadge.setColorFilter(cf);
                            }
                        }
                    });
                }
            }});
    }

    /**
     * Returns the full drawable for {@param info}.
     * @param outObj this is set to the internal data associated with {@param info},
     *               eg {@link LauncherActivityInfo} or {@link ShortcutInfoCompat}.
     */
    private Drawable getFullDrawable(ItemInfo info, LauncherAppState appState, Object[] outObj) {
        if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION) {
            LauncherActivityInfo activityInfo = LauncherAppsCompat.getInstance(mLauncher)
                    .resolveActivity(info.getIntent(), info.user);
            outObj[0] = activityInfo;
            return (activityInfo != null) ? appState.getIconCache()
                    .getFullResIcon(activityInfo, false) : null;
        } else if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            if (info instanceof PendingAddShortcutInfo) {
                ShortcutConfigActivityInfo activityInfo =
                        ((PendingAddShortcutInfo) info).activityInfo;
                outObj[0] = activityInfo;
                return activityInfo.getFullResIcon(appState.getIconCache());
            }
            ShortcutKey key = ShortcutKey.fromItemInfo(info);
            DeepShortcutManager sm = DeepShortcutManager.getInstance(mLauncher);
            List<ShortcutInfoCompat> si = sm.queryForFullDetails(
                    key.componentName.getPackageName(), Arrays.asList(key.getId()), key.user);
            if (si.isEmpty()) {
                return null;
            } else {
                outObj[0] = si.get(0);
                return sm.getShortcutIconDrawable(si.get(0),
                        appState.getInvariantDeviceProfile().fillResIconDpi);
            }
        } else {
            return null;
        }
    }

    /**
     * For apps icons and shortcut icons that have badges, this method creates a drawable that can
     * later on be rendered on top of the layers for the badges. For app icons, work profile badges
     * can only be applied. For deep shortcuts, when dragged from the pop up container, there's no
     * badge. When dragged from workspace or folder, it may contain app AND/OR work profile badge
     **/

    @TargetApi(Build.VERSION_CODES.O)
    private Drawable getBadge(ItemInfo info, LauncherAppState appState, Object obj) {
        int iconSize = appState.getInvariantDeviceProfile().iconBitmapSize;
        if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) {
            if (info.id == ItemInfo.NO_ID || !(obj instanceof ShortcutInfoCompat)) {
                // The item is not yet added on home screen.
                return new FixedSizeEmptyDrawable(iconSize);
            }
            ShortcutInfoCompat si = (ShortcutInfoCompat) obj;
            Bitmap badge = LauncherIcons.getShortcutInfoBadge(si, appState.getIconCache());

            float badgeSize = mLauncher.getResources().getDimension(R.dimen.profile_badge_size);
            float insetFraction = (iconSize - badgeSize) / iconSize;
            return new InsetDrawable(new FastBitmapDrawable(badge),
                    insetFraction, insetFraction, 0, 0);
        } else {
            return mLauncher.getPackageManager()
                    .getUserBadgedIcon(new FixedSizeEmptyDrawable(iconSize), info.user);
        }
    }

    private ImageView setupImageView(Drawable drawable, float normalizationScale) {
        FrameLayout.LayoutParams params = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        ImageView imageViewOut = new ImageView(getContext());
        imageViewOut.setLayoutParams(params);
        imageViewOut.setScaleType(ImageView.ScaleType.FIT_XY);
        imageViewOut.setScaleX(normalizationScale);
        imageViewOut.setScaleY(normalizationScale);
        imageViewOut.setImageDrawable(drawable);
        return imageViewOut;
    }

    private SpringAnimation setupSpringAnimation(int minValue, int maxValue,
            DynamicAnimation.ViewProperty property) {
        SpringAnimation s = new SpringAnimation(mFgImageView, property, 0);
        s.setMinValue(minValue).setMaxValue(maxValue);
        s.setSpring(new SpringForce(0)
                        .setDampingRatio(DAMPENING_RATIO)
                        .setStiffness(STIFFNESS));
        return s;
    }

    @TargetApi(Build.VERSION_CODES.O)
    private Path getMaskPath(AdaptiveIconDrawable dr, float normalizationScale) {
        Matrix m = new Matrix();
        // Shrink very tiny bit so that the clip path is smaller than the original bitmap
        // that has anti aliased edges and shadows.
        float s = normalizationScale * .97f;
        m.setScale(s, s, dr.getBounds().centerX(), dr.getBounds().centerY());
        Path p = new Path();
        dr.getIconMask().transform(m, p);
        return p;
    }

    private void applySpring(int x, int y) {
        if (mSpringX == null || mSpringY == null) {
            return;
        }
        mSpringX.animateToFinalPosition(Utilities.boundToRange(x, -mDelta, mDelta));
        mSpringY.animateToFinalPosition(Utilities.boundToRange(y, -mDelta, mDelta));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int w = right - left;
        int h = bottom - top;
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).layout(-w / 4, -h / 4, w + w / 4, h + h / 4);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = mBitmap.getWidth();
        int h = mBitmap.getHeight();
        setMeasuredDimension(w, h);
        for (int i = 0; i < getChildCount(); i++) {
            getChildAt(i).measure(w, h);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mScaledMaskPath != null) {
            int cnt = canvas.save();
            canvas.drawBitmap(mBitmap, 0.0f, 0.0f, mPaint);
            canvas.clipPath(mScaledMaskPath);
            super.dispatchDraw(canvas);
            canvas.restoreToCount(cnt);
            mBadge.draw(canvas);
        } else {
            super.dispatchDraw(canvas);
        }
    }

    /** Sets the scale of the view over the normal workspace icon size. */
    public void setIntrinsicIconScaleFactor(float scale) {
        mIntrinsicIconScale = scale;
    }

    public float getIntrinsicIconScaleFactor() {
        return mIntrinsicIconScale;
    }

    public int getDragRegionLeft() {
        return mDragRegion.left;
    }

    public int getDragRegionTop() {
        return mDragRegion.top;
    }

    public int getDragRegionWidth() {
        return mDragRegion.width();
    }

    public int getDragRegionHeight() {
        return mDragRegion.height();
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

    // Draws drag shadow for system DND.
    @SuppressLint("WrongCall")
    public void drawDragShadow(Canvas canvas) {
        final int saveCount = canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.scale(getScaleX(), getScaleY());
        onDraw(canvas);
        canvas.restoreToCount(saveCount);
    }

    // Provides drag shadow metrics for system DND.
    public void provideDragShadowMetrics(Point size, Point touch) {
        size.set((int)(mBitmap.getWidth() * getScaleX()), (int)(mBitmap.getHeight() * getScaleY()));

        final float xGrowth = mBitmap.getWidth() * (getScaleX() - 1);
        final float yGrowth = mBitmap.getHeight() * (getScaleY() - 1);
        touch.set(
                mRegistrationX + (int)Math.round(xGrowth / 2),
                mRegistrationY + (int)Math.round(yGrowth / 2));
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
            Themes.setColorScaleOnMatrix(color, m2);
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
            public void run() {
                mAnim.start();
            }
        });
    }

    public void cancelAnimation() {
        mAnimationCancelled = true;
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

    public void animateTo(int toTouchX, int toTouchY, Runnable onCompleteRunnable, int duration) {
        mTempLoc[0] = toTouchX - mRegistrationX;
        mTempLoc[1] = toTouchY - mRegistrationY;
        mDragLayer.animateViewIntoPosition(this, mTempLoc, 1f, mInitialScale, mInitialScale,
                DragLayer.ANIMATION_END_DISAPPEAR, onCompleteRunnable, duration);
    }

    public void animateShift(final int shiftX, final int shiftY) {
        if (mAnim.isStarted()) {
            return;
        }
        mAnimatedShiftX = shiftX;
        mAnimatedShiftY = shiftY;
        applyTranslation();
        mAnim.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = 1 - animation.getAnimatedFraction();
                mAnimatedShiftX = (int) (fraction * shiftX);
                mAnimatedShiftY = (int) (fraction * shiftY);
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
            mDragLayer.removeView(DragView.this);
        }
    }

    public int getBlurSizeOutline() {
        return mBlurSizeOutline;
    }

    public float getInitialScale() {
        return mInitialScale;
    }

    private static class FixedSizeEmptyDrawable extends ColorDrawable {

        private final int mSize;

        public FixedSizeEmptyDrawable(int size) {
            super(Color.TRANSPARENT);
            mSize = size;
        }

        @Override
        public int getIntrinsicHeight() {
            return mSize;
        }

        @Override
        public int getIntrinsicWidth() {
            return mSize;
        }
    }
}
