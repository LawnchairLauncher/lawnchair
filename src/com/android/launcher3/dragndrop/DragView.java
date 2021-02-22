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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.FloatArrayEvaluator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.TargetApi;
import android.content.pm.LauncherActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.animation.FloatPropertyCompat;
import android.support.animation.SpringAnimation;
import android.support.animation.SpringForce;
import android.view.View;

import ch.deletescape.lawnchair.LawnchairPreferences;
import ch.deletescape.lawnchair.iconpack.AdaptiveIconCompat;
import ch.deletescape.lawnchair.iconpack.IconPackManager;
import ch.deletescape.lawnchair.iconpack.LawnchairIconProvider;
import com.android.launcher3.*;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.anim.Interpolators;
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

import static com.android.launcher3.ItemInfoWithIcon.FLAG_ICON_BADGED;

public class DragView extends View {
    private static final ColorMatrix sTempMatrix1 = new ColorMatrix();
    private static final ColorMatrix sTempMatrix2 = new ColorMatrix();

    public static final int COLOR_CHANGE_DURATION = 120;
    public static final int VIEW_ZOOM_DURATION = 150;

    @Thunk static float sDragAlpha = 1f;

    private boolean mDrawBitmap = true;
    private Bitmap mBitmap;
    private Bitmap mCrossFadeBitmap;
    @Thunk Paint mPaint;
    private final int mBlurSizeOutline;
    private final int mRegistrationX;
    private final int mRegistrationY;
    private final float mInitialScale;
    private final float mScaleOnDrop;
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
    private Drawable mBgSpringDrawable, mFgSpringDrawable;
    private SpringFloatValue mTranslateX, mTranslateY;
    private Path mScaledMaskPath;
    private Drawable mBadge;
    private ColorMatrixColorFilter mBaseFilter;

    private final IconProvider iconProvider;
    private final LawnchairPreferences prefs;

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
                    final float initialScale, final float scaleOnDrop, final float finalScaleDps) {
        super(launcher);
        mLauncher = launcher;
        mDragLayer = launcher.getDragLayer();
        mDragController = launcher.getDragController();

        iconProvider = IconProvider.newInstance(launcher);

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

        mBitmap = bitmap;
        setDragRegion(new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()));

        // The point in our scaled bitmap that the touch events are located
        mRegistrationX = registrationX;
        mRegistrationY = registrationY;

        mInitialScale = initialScale;
        mScaleOnDrop = scaleOnDrop;

        // Force a measure, because Workspace uses getMeasuredHeight() before the layout pass
        int ms = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        measure(ms, ms);
        mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

        mBlurSizeOutline = getResources().getDimensionPixelSize(R.dimen.blur_size_medium_outline);
        setElevation(getResources().getDimension(R.dimen.drag_elevation));

        prefs = Utilities.getLawnchairPrefs(mLauncher);
    }

    /**
     * Initialize {@code #mIconDrawable} if the item can be represented using
     * an {@link AdaptiveIconCompat} or {@link FolderAdaptiveIcon}.
     */
    @TargetApi(Build.VERSION_CODES.O)
    public void setItemInfo(final ItemInfo info) {
        if (!(FeatureFlags.LAUNCHER3_SPRING_ICONS && Utilities.ATLEAST_OREO)) {
            return;
        }
        if (info.itemType != LauncherSettings.Favorites.ITEM_TYPE_APPLICATION &&
                info.itemType != LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT &&
                info.itemType != LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
            return;
        }
        if (info instanceof FolderInfo && ((FolderInfo) info).usingCustomIcon(mLauncher)) {
            return;
        }
        // Load the adaptive icon on a background thread and add the view in ui thread.
        final Looper workerLooper = LauncherModel.getWorkerLooper();
        new Handler(workerLooper).postAtFrontOfQueue(new Runnable() {
            @Override
            public void run() {
                LauncherAppState appState = LauncherAppState.getInstance(mLauncher);
                Object[] outObj = new Object[1];
                // TODO: Actually support wiggling for custom folder icons, but with proper sizing
                final Drawable dr = getFullDrawable(info, appState, outObj);

                if (dr instanceof AdaptiveIconCompat) {
                    int w = mBitmap.getWidth();
                    int h = mBitmap.getHeight();
                    int blurMargin = (int) mLauncher.getResources()
                            .getDimension(R.dimen.blur_size_medium_outline) / 2;

                    Rect bounds = new Rect(0, 0, w, h);
                    bounds.inset(blurMargin, blurMargin);
                    // Badge is applied after icon normalization so the bounds for badge should not
                    // be scaled down due to icon normalization.
                    Rect badgeBounds = new Rect(bounds);
                    mBadge = getBadge(info, appState, outObj[0]);
                    mBadge.setBounds(badgeBounds);

                    LauncherIcons li = LauncherIcons.obtain(mLauncher);
                    Utilities.scaleRectAboutCenter(bounds,
                            li.getNormalizer().getScale(dr, null, null, null));
                    li.recycle();
                    AdaptiveIconCompat adaptiveIcon = (AdaptiveIconCompat) dr;

                    Rect fgBounds = new Rect(bounds);
                    Rect bgBounds = new Rect(bounds);
                    Utilities.scaleRectAboutCenter(bgBounds, 1.1f);
                    // Shrink very tiny bit so that the clip path is smaller than the original bitmap
                    // that has anti aliased edges and shadows.
                    Rect shrunkBounds = new Rect(prefs.getScaleAdaptiveBg() ? bgBounds : fgBounds);
                    Utilities.scaleRectAboutCenter(shrunkBounds, 0.98f);
                    adaptiveIcon.setBounds(shrunkBounds);
                    final Path mask = adaptiveIcon.getIconMask();

                    mTranslateX = new SpringFloatValue(DragView.this,
                            w * AdaptiveIconCompat.getExtraInsetFraction());
                    mTranslateY = new SpringFloatValue(DragView.this,
                            h * AdaptiveIconCompat.getExtraInsetFraction());

                    fgBounds.inset(
                            (int) (-fgBounds.width() * AdaptiveIconCompat.getExtraInsetFraction()),
                            (int) (-fgBounds.height() * AdaptiveIconCompat.getExtraInsetFraction())
                    );
                    bgBounds.inset(
                            (int) (-bgBounds.width() * AdaptiveIconCompat.getExtraInsetFraction()),
                            (int) (-bgBounds.height() * AdaptiveIconCompat.getExtraInsetFraction())
                    );
                    mBgSpringDrawable = adaptiveIcon.getBackground();
                    if (mBgSpringDrawable == null) {
                        mBgSpringDrawable = new ColorDrawable(Color.TRANSPARENT);
                    }
                    mBgSpringDrawable.setBounds(prefs.getScaleAdaptiveBg() ? bgBounds : fgBounds);
                    mFgSpringDrawable = adaptiveIcon.getForeground();
                    if (mFgSpringDrawable == null) {
                        mFgSpringDrawable = new ColorDrawable(Color.TRANSPARENT);
                    }
                    mFgSpringDrawable.setBounds(fgBounds);

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            // Assign the variable on the UI thread to avoid race conditions.
                            mScaledMaskPath = mask;

                            // Do not draw the background in case of folder as its translucent
                            mDrawBitmap = !(dr instanceof FolderAdaptiveIcon);

                            if (info.isDisabled()) {
                                FastBitmapDrawable d = new FastBitmapDrawable((Bitmap) null);
                                d.setIsDisabled(true);
                                mBaseFilter = (ColorMatrixColorFilter) d.getColorFilter();
                            }
                            updateColorFilter();
                        }
                    });
                }
            }});
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void updateColorFilter() {
        if (mCurrentFilter == null) {
            mPaint.setColorFilter(null);

            if (mScaledMaskPath != null) {
                mBgSpringDrawable.setColorFilter(mBaseFilter);
                mFgSpringDrawable.setColorFilter(mBaseFilter);
                mBadge.setColorFilter(mBaseFilter);
            }
        } else {
            ColorMatrixColorFilter currentFilter = new ColorMatrixColorFilter(mCurrentFilter);
            mPaint.setColorFilter(currentFilter);

            if (mScaledMaskPath != null) {
                if (mBaseFilter != null) {
                    mBaseFilter.getColorMatrix(sTempMatrix1);
                    sTempMatrix2.set(mCurrentFilter);
                    sTempMatrix1.postConcat(sTempMatrix2);

                    currentFilter = new ColorMatrixColorFilter(sTempMatrix1);
                }

                mBgSpringDrawable.setColorFilter(currentFilter);
                mFgSpringDrawable.setColorFilter(currentFilter);
                mBadge.setColorFilter(currentFilter);
            }
        }

        invalidate();
    }

    /**
     * Returns the full drawable for {@param info}.
     * @param outObj this is set to the internal data associated with {@param info},
     *               eg {@link LauncherActivityInfo} or {@link ShortcutInfoCompat}.
     */
    private Drawable getFullDrawable(ItemInfo info, LauncherAppState appState, Object[] outObj) {
        IconPackManager.CustomIconEntry customIconEntry = (info instanceof ShortcutInfo) ?
                ((ShortcutInfo) info).customIconEntry : null;
        if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION || customIconEntry != null) {
            LauncherActivityInfo activityInfo = LauncherAppsCompat.getInstance(mLauncher)
                    .resolveActivity(info.getIntent(), info.user);
            outObj[0] = activityInfo;
            return (activityInfo != null) ? appState.getIconCache()
                    .getFullResIcon(activityInfo, info, false) : null;
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
                int iconDpi = appState.getInvariantDeviceProfile().fillResIconDpi;
                if (iconProvider instanceof LawnchairIconProvider) {
                    return ((LawnchairIconProvider) iconProvider).getIcon(si.get(0), iconDpi);
                }
                return sm.getShortcutIconDrawable(si.get(0), iconDpi);
            }
        } else if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
            FolderInfo folderInfo = (FolderInfo) info;
            if (folderInfo.isCoverMode()) {
                return getFullDrawable(folderInfo.getCoverInfo(), appState, outObj);
            }

            FolderAdaptiveIcon icon =  FolderAdaptiveIcon.createFolderAdaptiveIcon(
                    mLauncher, info.id, new Point(mBitmap.getWidth(), mBitmap.getHeight()));
            if (icon == null) {
                return null;
            }
            outObj[0] = icon;
            return icon;
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
            boolean iconBadged = (info instanceof ItemInfoWithIcon)
                    && ((ItemInfoWithIcon) info).isBadgeVisible();
            if (info.id == ItemInfo.NO_ID || !iconBadged
                    || !(obj instanceof ShortcutInfoCompat)) {
                // The item is not yet added on home screen.
                return new FixedSizeEmptyDrawable(iconSize);
            }
            ShortcutInfoCompat si = (ShortcutInfoCompat) obj;
            LauncherIcons li = LauncherIcons.obtain(appState.getContext());
            Bitmap badge = li.getShortcutInfoBadge(si, appState.getIconCache()).iconBitmap;
            li.recycle();
            float badgeSize = mLauncher.getResources().getDimension(R.dimen.profile_badge_size);
            float insetFraction = (iconSize - badgeSize) / iconSize;
            return new InsetDrawable(new FastBitmapDrawable(badge),
                    insetFraction, insetFraction, 0, 0);
        } else if (info.itemType == LauncherSettings.Favorites.ITEM_TYPE_FOLDER) {
            return obj instanceof FolderAdaptiveIcon ? ((FolderAdaptiveIcon) obj).getBadge() : new FixedSizeEmptyDrawable(iconSize);
        } else {
            return mLauncher.getPackageManager()
                    .getUserBadgedIcon(new FixedSizeEmptyDrawable(iconSize), info.user);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mBitmap.getWidth(), mBitmap.getHeight());
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

    public Bitmap getPreviewBitmap() {
        return mBitmap;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mHasDrawn = true;

        if (mDrawBitmap) {
            // Always draw the bitmap to mask anti aliasing due to clipPath
            boolean crossFade = mCrossFadeProgress > 0 && mCrossFadeBitmap != null;
            if (crossFade) {
                int alpha = crossFade ? (int) (255 * (1 - mCrossFadeProgress)) : 255;
                mPaint.setAlpha(alpha);
            }
            canvas.drawBitmap(mBitmap, 0.0f, 0.0f, mPaint);
            if (crossFade) {
                mPaint.setAlpha((int) (255 * mCrossFadeProgress));
                final int saveCount = canvas.save();
                float sX = (mBitmap.getWidth() * 1.0f) / mCrossFadeBitmap.getWidth();
                float sY = (mBitmap.getHeight() * 1.0f) / mCrossFadeBitmap.getHeight();
                canvas.scale(sX, sY);
                canvas.drawBitmap(mCrossFadeBitmap, 0.0f, 0.0f, mPaint);
                canvas.restoreToCount(saveCount);
            }
        }

        if (mScaledMaskPath != null) {
            int cnt = canvas.save();
            canvas.clipPath(mScaledMaskPath);
            mBgSpringDrawable.draw(canvas);
            canvas.translate(mTranslateX.mValue, mTranslateY.mValue);
            mFgSpringDrawable.draw(canvas);
            canvas.restoreToCount(cnt);
            mBadge.draw(canvas);
        }
    }

    public void setCrossFadeBitmap(Bitmap crossFadeBitmap) {
        mCrossFadeBitmap = crossFadeBitmap;
    }

    public void crossFade(int duration) {
        ValueAnimator va = LauncherAnimUtils.ofFloat(0f, 1f);
        va.setDuration(duration);
        va.setInterpolator(Interpolators.DEACCEL_1_5);
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
                updateColorFilter();
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
                updateColorFilter();
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
        if (touchX > 0 && touchY > 0 && mLastTouchX > 0 && mLastTouchY > 0
                && mScaledMaskPath != null) {
            mTranslateX.animateToPos(mLastTouchX - touchX);
            mTranslateY.animateToPos(mLastTouchY - touchY);
        }
        mLastTouchX = touchX;
        mLastTouchY = touchY;
        applyTranslation();
    }

    public void animateTo(int toTouchX, int toTouchY, Runnable onCompleteRunnable, int duration) {
        mTempLoc[0] = toTouchX - mRegistrationX;
        mTempLoc[1] = toTouchY - mRegistrationY;
        mDragLayer.animateViewIntoPosition(this, mTempLoc, 1f, mScaleOnDrop, mScaleOnDrop,
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

    private static class SpringFloatValue {

        private static final FloatPropertyCompat<SpringFloatValue> VALUE =
                new FloatPropertyCompat<SpringFloatValue>("value") {
                    @Override
                    public float getValue(SpringFloatValue object) {
                        return object.mValue;
                    }

                    @Override
                    public void setValue(SpringFloatValue object, float value) {
                        object.mValue = value;
                        object.mView.invalidate();
                    }
                };

        // Following three values are fine tuned with motion ux designer
        private final static int STIFFNESS = 4000;
        private final static float DAMPENING_RATIO = 1f;
        private final static int PARALLAX_MAX_IN_DP = 8;

        private final View mView;
        private final SpringAnimation mSpring;
        private final float mDelta;

        private float mValue;

        public SpringFloatValue(View view, float range) {
            mView = view;
            mSpring = new SpringAnimation(this, VALUE, 0)
                    .setMinValue(-range).setMaxValue(range)
                    .setSpring(new SpringForce(0)
                            .setDampingRatio(DAMPENING_RATIO)
                            .setStiffness(STIFFNESS));
            mDelta = view.getResources().getDisplayMetrics().density * PARALLAX_MAX_IN_DP;
        }

        public void animateToPos(float value) {
            mSpring.animateToFinalPosition(Utilities.boundToRange(value, -mDelta, mDelta));
        }
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
