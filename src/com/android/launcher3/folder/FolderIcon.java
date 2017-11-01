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

package com.android.launcher3.folder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.Alarm;
import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.CellLayout;
import com.android.launcher3.CheckLongPressHelper;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.FolderInfo;
import com.android.launcher3.FolderInfo.FolderListener;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.OnAlarmListener;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.SimpleOnStylusPressListener;
import com.android.launcher3.StylusEventHelper;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.badge.BadgeRenderer;
import com.android.launcher3.badge.FolderBadgeInfo;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.util.Thunk;

import java.util.ArrayList;
import java.util.List;

/**
 * An icon that can appear on in the workspace representing an {@link Folder}.
 */
public class FolderIcon extends FrameLayout implements FolderListener {
    @Thunk Launcher mLauncher;
    @Thunk Folder mFolder;
    private FolderInfo mInfo;
    @Thunk static boolean sStaticValuesDirty = true;

    public static final int NUM_ITEMS_IN_PREVIEW = FeatureFlags.LAUNCHER3_LEGACY_FOLDER_ICON ?
            StackFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW :
            ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW;

    private CheckLongPressHelper mLongPressHelper;
    private StylusEventHelper mStylusEventHelper;

    // The number of icons to display in the
    private static final int CONSUMPTION_ANIMATION_DURATION = 100;
    private static final int DROP_IN_ANIMATION_DURATION = 400;
    private static final int INITIAL_ITEM_ANIMATION_DURATION = 350;
    private static final int FINAL_ITEM_ANIMATION_DURATION = 200;

    // Flag whether the folder should open itself when an item is dragged over is enabled.
    public static final boolean SPRING_LOADING_ENABLED = true;

    // Delay when drag enters until the folder opens, in miliseconds.
    private static final int ON_OPEN_DELAY = 800;

    @Thunk BubbleTextView mFolderName;

    // These variables are all associated with the drawing of the preview; they are stored
    // as member variables for shared usage and to avoid computation on each frame
    private int mIntrinsicIconSize = -1;
    private int mTotalWidth = -1;
    private int mPrevTopPadding = -1;

    PreviewBackground mBackground = new PreviewBackground();

    private PreviewLayoutRule mPreviewLayoutRule;

    boolean mAnimating = false;
    private Rect mTempBounds = new Rect();

    private float mSlop;

    private PreviewItemDrawingParams mTmpParams = new PreviewItemDrawingParams(0, 0, 0, 0);
    private ArrayList<PreviewItemDrawingParams> mDrawingParams = new ArrayList<PreviewItemDrawingParams>();
    private Drawable mReferenceDrawable = null;

    private Alarm mOpenAlarm = new Alarm();

    private FolderBadgeInfo mBadgeInfo = new FolderBadgeInfo();
    private BadgeRenderer mBadgeRenderer;
    private float mBadgeScale;
    private Point mTempSpaceForBadgeOffset = new Point();

    private static final Property<FolderIcon, Float> BADGE_SCALE_PROPERTY
            = new Property<FolderIcon, Float>(Float.TYPE, "badgeScale") {
        @Override
        public Float get(FolderIcon folderIcon) {
            return folderIcon.mBadgeScale;
        }

        @Override
        public void set(FolderIcon folderIcon, Float value) {
            folderIcon.mBadgeScale = value;
            folderIcon.invalidate();
        }
    };

    public FolderIcon(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FolderIcon(Context context) {
        super(context);
        init();
    }

    private void init() {
        mLongPressHelper = new CheckLongPressHelper(this);
        mStylusEventHelper = new StylusEventHelper(new SimpleOnStylusPressListener(this), this);
        mPreviewLayoutRule = FeatureFlags.LAUNCHER3_LEGACY_FOLDER_ICON ?
                new StackFolderIconLayoutRule() :
                new ClippedFolderIconLayoutRule();
        mSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    public static FolderIcon fromXml(int resId, Launcher launcher, ViewGroup group,
            FolderInfo folderInfo) {
        @SuppressWarnings("all") // suppress dead code warning
        final boolean error = INITIAL_ITEM_ANIMATION_DURATION >= DROP_IN_ANIMATION_DURATION;
        if (error) {
            throw new IllegalStateException("DROP_IN_ANIMATION_DURATION must be greater than " +
                    "INITIAL_ITEM_ANIMATION_DURATION, as sequencing of adding first two items " +
                    "is dependent on this");
        }

        DeviceProfile grid = launcher.getDeviceProfile();
        FolderIcon icon = (FolderIcon) LayoutInflater.from(launcher).inflate(resId, group, false);

        icon.setClipToPadding(false);
        icon.mFolderName = (BubbleTextView) icon.findViewById(R.id.folder_icon_name);
        icon.mFolderName.setText(folderInfo.title);
        icon.mFolderName.setCompoundDrawablePadding(0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) icon.mFolderName.getLayoutParams();
        lp.topMargin = grid.iconSizePx + grid.iconDrawablePaddingPx;

        icon.setTag(folderInfo);
        icon.setOnClickListener(launcher);
        icon.mInfo = folderInfo;
        icon.mLauncher = launcher;
        icon.mBadgeRenderer = launcher.getDeviceProfile().mBadgeRenderer;
        icon.setContentDescription(launcher.getString(R.string.folder_name_format, folderInfo.title));
        Folder folder = Folder.fromXml(launcher);
        folder.setDragController(launcher.getDragController());
        folder.setFolderIcon(icon);
        folder.bind(folderInfo);
        icon.setFolder(folder);
        icon.setAccessibilityDelegate(launcher.getAccessibilityDelegate());

        folderInfo.addListener(icon);

        icon.setOnFocusChangeListener(launcher.mFocusHandler);
        return icon;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        sStaticValuesDirty = true;
        return super.onSaveInstanceState();
    }

    public Folder getFolder() {
        return mFolder;
    }

    private void setFolder(Folder folder) {
        mFolder = folder;
        updateItemDrawingParams(false);
    }

    private boolean willAcceptItem(ItemInfo item) {
        final int itemType = item.itemType;
        return ((itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT ||
                itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) &&
                !mFolder.isFull() && item != mInfo && !mFolder.isOpen());
    }

    public boolean acceptDrop(ItemInfo dragInfo) {
        final ItemInfo item = dragInfo;
        return !mFolder.isDestroyed() && willAcceptItem(item);
    }

    public void addItem(ShortcutInfo item) {
        mInfo.add(item, true);
    }

    public void onDragEnter(ItemInfo dragInfo) {
        if (mFolder.isDestroyed() || !willAcceptItem(dragInfo)) return;
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) getLayoutParams();
        CellLayout cl = (CellLayout) getParent().getParent();

        mBackground.animateToAccept(cl, lp.cellX, lp.cellY);
        mOpenAlarm.setOnAlarmListener(mOnOpenListener);
        if (SPRING_LOADING_ENABLED &&
                ((dragInfo instanceof AppInfo) || (dragInfo instanceof ShortcutInfo))) {
            // TODO: we currently don't support spring-loading for PendingAddShortcutInfos even
            // though widget-style shortcuts can be added to folders. The issue is that we need
            // to deal with configuration activities which are currently handled in
            // Workspace#onDropExternal.
            mOpenAlarm.setAlarm(ON_OPEN_DELAY);
        }
    }

    OnAlarmListener mOnOpenListener = new OnAlarmListener() {
        public void onAlarm(Alarm alarm) {
            mFolder.beginExternalDrag();
            mFolder.animateOpen();
        }
    };

    public Drawable prepareCreate(final View destView) {
        Drawable animateDrawable = ((TextView) destView).getCompoundDrawables()[1];
        computePreviewDrawingParams(animateDrawable.getIntrinsicWidth(),
                destView.getMeasuredWidth());
        return animateDrawable;
    }

    public void performCreateAnimation(final ShortcutInfo destInfo, final View destView,
            final ShortcutInfo srcInfo, final DragView srcView, Rect dstRect,
            float scaleRelativeToDragLayer, Runnable postAnimationRunnable) {

        // These correspond two the drawable and view that the icon was dropped _onto_
        Drawable animateDrawable = prepareCreate(destView);

        mReferenceDrawable = animateDrawable;

        addItem(destInfo);
        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        animateFirstItem(animateDrawable, INITIAL_ITEM_ANIMATION_DURATION, false, null);

        // This will animate the dragView (srcView) into the new folder
        onDrop(srcInfo, srcView, dstRect, scaleRelativeToDragLayer, 1, postAnimationRunnable);
    }

    public void performDestroyAnimation(final View finalView, Runnable onCompleteRunnable) {
        Drawable animateDrawable = ((TextView) finalView).getCompoundDrawables()[1];
        computePreviewDrawingParams(animateDrawable.getIntrinsicWidth(),
                finalView.getMeasuredWidth());

        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        animateFirstItem(animateDrawable, FINAL_ITEM_ANIMATION_DURATION, true,
                onCompleteRunnable);
    }

    public void onDragExit() {
        mBackground.animateToRest();
        mOpenAlarm.cancelAlarm();
    }

    private void onDrop(final ShortcutInfo item, DragView animateView, Rect finalRect,
            float scaleRelativeToDragLayer, int index, Runnable postAnimationRunnable) {
        item.cellX = -1;
        item.cellY = -1;

        // Typically, the animateView corresponds to the DragView; however, if this is being done
        // after a configuration activity (ie. for a Shortcut being dragged from AllApps) we
        // will not have a view to animate
        if (animateView != null) {
            DragLayer dragLayer = mLauncher.getDragLayer();
            Rect from = new Rect();
            dragLayer.getViewRectRelativeToSelf(animateView, from);
            Rect to = finalRect;
            if (to == null) {
                to = new Rect();
                Workspace workspace = mLauncher.getWorkspace();
                // Set cellLayout and this to it's final state to compute final animation locations
                workspace.setFinalTransitionTransform((CellLayout) getParent().getParent());
                float scaleX = getScaleX();
                float scaleY = getScaleY();
                setScaleX(1.0f);
                setScaleY(1.0f);
                scaleRelativeToDragLayer = dragLayer.getDescendantRectRelativeToSelf(this, to);
                // Finished computing final animation locations, restore current state
                setScaleX(scaleX);
                setScaleY(scaleY);
                workspace.resetTransitionTransform((CellLayout) getParent().getParent());
            }

            int[] center = new int[2];
            float scale = getLocalCenterForIndex(index, index + 1, center);
            center[0] = (int) Math.round(scaleRelativeToDragLayer * center[0]);
            center[1] = (int) Math.round(scaleRelativeToDragLayer * center[1]);

            to.offset(center[0] - animateView.getMeasuredWidth() / 2,
                      center[1] - animateView.getMeasuredHeight() / 2);

            float finalAlpha = index < mPreviewLayoutRule.maxNumItems() ? 0.5f : 0f;

            float finalScale = scale * scaleRelativeToDragLayer;
            dragLayer.animateView(animateView, from, to, finalAlpha,
                    1, 1, finalScale, finalScale, DROP_IN_ANIMATION_DURATION,
                    new DecelerateInterpolator(2), new AccelerateInterpolator(2),
                    postAnimationRunnable, DragLayer.ANIMATION_END_DISAPPEAR, null);
            addItem(item);
            mFolder.hideItem(item);

            final PreviewItemDrawingParams params = index < mDrawingParams.size() ?
                    mDrawingParams.get(index) : null;
            if (params != null) params.hidden = true;
            postDelayed(new Runnable() {
                public void run() {
                    if (params != null) params.hidden = false;
                    mFolder.showItem(item);
                    invalidate();
                }
            }, DROP_IN_ANIMATION_DURATION);
        } else {
            addItem(item);
        }
    }

    public void onDrop(DragObject d) {
        ShortcutInfo item;
        if (d.dragInfo instanceof AppInfo) {
            // Came from all apps -- make a copy
            item = ((AppInfo) d.dragInfo).makeShortcut();
        } else {
            item = (ShortcutInfo) d.dragInfo;
        }
        mFolder.notifyDrop();
        onDrop(item, d.dragView, null, 1.0f, mInfo.contents.size(), d.postAnimationRunnable);
    }

    private void computePreviewDrawingParams(int drawableSize, int totalSize) {
        if (mIntrinsicIconSize != drawableSize || mTotalWidth != totalSize ||
                mPrevTopPadding != getPaddingTop()) {
            DeviceProfile grid = mLauncher.getDeviceProfile();

            mIntrinsicIconSize = drawableSize;
            mTotalWidth = totalSize;
            mPrevTopPadding = getPaddingTop();

            mBackground.setup(getResources().getDisplayMetrics(), grid, this, mTotalWidth,
                    getPaddingTop());
            mPreviewLayoutRule.init(mBackground.previewSize, mIntrinsicIconSize,
                    Utilities.isRtl(getResources()));

            updateItemDrawingParams(false);
        }
    }

    private void computePreviewDrawingParams(Drawable d) {
        computePreviewDrawingParams(d.getIntrinsicWidth(), getMeasuredWidth());
    }

    public void setBadgeInfo(FolderBadgeInfo badgeInfo) {
        updateBadgeScale(mBadgeInfo.hasBadge(), badgeInfo.hasBadge());
        mBadgeInfo = badgeInfo;
    }

    /**
     * Sets mBadgeScale to 1 or 0, animating if wasBadged or isBadged is false
     * (the badge is being added or removed).
     */
    private void updateBadgeScale(boolean wasBadged, boolean isBadged) {
        float newBadgeScale = isBadged ? 1f : 0f;
        // Animate when a badge is first added or when it is removed.
        if ((wasBadged ^ isBadged) && isShown()) {
            ObjectAnimator.ofFloat(this, BADGE_SCALE_PROPERTY, newBadgeScale).start();
        } else {
            mBadgeScale = newBadgeScale;
            invalidate();
        }
    }

    static class PreviewItemDrawingParams {
        PreviewItemDrawingParams(float transX, float transY, float scale, float overlayAlpha) {
            this.transX = transX;
            this.transY = transY;
            this.scale = scale;
            this.overlayAlpha = overlayAlpha;
        }

        public void update(float transX, float transY, float scale) {
            // We ensure the update will not interfere with an animation on the layout params
            // If the final values differ, we cancel the animation.
            if (anim != null) {
                if (anim.finalTransX == transX || anim.finalTransY == transY
                        || anim.finalScale == scale) {
                    return;
                }
                anim.cancel();
            }

            this.transX = transX;
            this.transY = transY;
            this.scale = scale;
        }

        float transX;
        float transY;
        float scale;
        public float overlayAlpha;
        boolean hidden;
        FolderPreviewItemAnim anim;
        Drawable drawable;
    }

    private float getLocalCenterForIndex(int index, int curNumItems, int[] center) {
        mTmpParams = computePreviewItemDrawingParams(
                Math.min(mPreviewLayoutRule.maxNumItems(), index), curNumItems, mTmpParams);

        mTmpParams.transX += mBackground.basePreviewOffsetX;
        mTmpParams.transY += mBackground.basePreviewOffsetY;
        float offsetX = mTmpParams.transX + (mTmpParams.scale * mIntrinsicIconSize) / 2;
        float offsetY = mTmpParams.transY + (mTmpParams.scale * mIntrinsicIconSize) / 2;

        center[0] = (int) Math.round(offsetX);
        center[1] = (int) Math.round(offsetY);
        return mTmpParams.scale;
    }

    private PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
            PreviewItemDrawingParams params) {
        // We use an index of -1 to represent an icon on the workspace for the destroy and
        // create animations
        if (index == -1) {
            return getFinalIconParams(params);
        }
        return mPreviewLayoutRule.computePreviewItemDrawingParams(index, curNumItems, params);
    }

    private PreviewItemDrawingParams getFinalIconParams(PreviewItemDrawingParams params) {
        float iconSize = mLauncher.getDeviceProfile().iconSizePx;

        final float scale = iconSize / mReferenceDrawable.getIntrinsicWidth();
        final float trans = (mBackground.previewSize - iconSize) / 2;

        params.update(trans, trans, scale);
        return params;
    }

    private void drawPreviewItem(Canvas canvas, PreviewItemDrawingParams params) {
        canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.translate(params.transX, params.transY);
        canvas.scale(params.scale, params.scale);
        Drawable d = params.drawable;

        if (d != null) {
            mTempBounds.set(d.getBounds());
            d.setBounds(0, 0, mIntrinsicIconSize, mIntrinsicIconSize);
            if (d instanceof FastBitmapDrawable) {
                FastBitmapDrawable fd = (FastBitmapDrawable) d;
                fd.drawWithBrightness(canvas, params.overlayAlpha);
            } else {
                d.setColorFilter(Color.argb((int) (params.overlayAlpha * 255), 255, 255, 255),
                        PorterDuff.Mode.SRC_ATOP);
                d.draw(canvas);
                d.clearColorFilter();
            }
            d.setBounds(mTempBounds);
        }
        canvas.restore();
    }

    /**
     * This object represents a FolderIcon preview background. It stores drawing / measurement
     * information, handles drawing, and animation (accept state <--> rest state).
     */
    public static class PreviewBackground {

        private final PorterDuffXfermode mClipPorterDuffXfermode
                = new PorterDuffXfermode(PorterDuff.Mode.DST_IN);
        // Create a RadialGradient such that it draws a black circle and then extends with
        // transparent. To achieve this, we keep the gradient to black for the range [0, 1) and
        // just at the edge quickly change it to transparent.
        private final RadialGradient mClipShader = new RadialGradient(0, 0, 1,
                new int[] {Color.BLACK, Color.BLACK, Color.TRANSPARENT },
                new float[] {0, 0.999f, 1},
                Shader.TileMode.CLAMP);

        private final PorterDuffXfermode mShadowPorterDuffXfermode
                = new PorterDuffXfermode(PorterDuff.Mode.DST_OUT);
        private RadialGradient mShadowShader = null;

        private final Matrix mShaderMatrix = new Matrix();
        private final Path mPath = new Path();

        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private float mScale = 1f;
        private float mColorMultiplier = 1f;
        private float mStrokeWidth;
        private View mInvalidateDelegate;

        public int previewSize;
        private int basePreviewOffsetX;
        private int basePreviewOffsetY;

        private CellLayout mDrawingDelegate;
        public int delegateCellX;
        public int delegateCellY;

        // When the PreviewBackground is drawn under an icon (for creating a folder) the border
        // should not occlude the icon
        public boolean isClipping = true;

        // Drawing / animation configurations
        private static final float ACCEPT_SCALE_FACTOR = 1.25f;
        private static final float ACCEPT_COLOR_MULTIPLIER = 1.5f;

        // Expressed on a scale from 0 to 255.
        private static final int BG_OPACITY = 160;
        private static final int MAX_BG_OPACITY = 225;
        private static final int BG_INTENSITY = 245;
        private static final int SHADOW_OPACITY = 40;

        ValueAnimator mScaleAnimator;

        public void setup(DisplayMetrics dm, DeviceProfile grid, View invalidateDelegate,
                   int availableSpace, int topPadding) {
            mInvalidateDelegate = invalidateDelegate;

            final int previewSize = grid.folderIconSizePx;
            final int previewPadding = grid.folderIconPreviewPadding;

            this.previewSize = (previewSize - 2 * previewPadding);

            basePreviewOffsetX = (availableSpace - this.previewSize) / 2;
            basePreviewOffsetY = previewPadding + grid.folderBackgroundOffset + topPadding;

            // Stroke width is 1dp
            mStrokeWidth = dm.density;

            float radius = getScaledRadius();
            float shadowRadius = radius + mStrokeWidth;
            int shadowColor = Color.argb(SHADOW_OPACITY, 0, 0, 0);
            mShadowShader = new RadialGradient(0, 0, 1,
                    new int[] {shadowColor, Color.TRANSPARENT},
                    new float[] {radius / shadowRadius, 1},
                    Shader.TileMode.CLAMP);

            invalidate();
        }

        int getRadius() {
            return previewSize / 2;
        }

        int getScaledRadius() {
            return (int) (mScale * getRadius());
        }

        int getOffsetX() {
            return basePreviewOffsetX - (getScaledRadius() - getRadius());
        }

        int getOffsetY() {
            return basePreviewOffsetY - (getScaledRadius() - getRadius());
        }

        /**
         * Returns the progress of the scale animation, where 0 means the scale is at 1f
         * and 1 means the scale is at ACCEPT_SCALE_FACTOR.
         */
        float getScaleProgress() {
            return (mScale - 1f) / (ACCEPT_SCALE_FACTOR - 1f);
        }

        void invalidate() {
            if (mInvalidateDelegate != null) {
                mInvalidateDelegate.invalidate();
            }

            if (mDrawingDelegate != null) {
                mDrawingDelegate.invalidate();
            }
        }

        void setInvalidateDelegate(View invalidateDelegate) {
            mInvalidateDelegate = invalidateDelegate;
            invalidate();
        }

        public void drawBackground(Canvas canvas) {
            mPaint.setStyle(Paint.Style.FILL);
            int alpha = (int) Math.min(MAX_BG_OPACITY, BG_OPACITY * mColorMultiplier);
            mPaint.setColor(Color.argb(alpha, BG_INTENSITY, BG_INTENSITY, BG_INTENSITY));

            drawCircle(canvas, 0 /* deltaRadius */);

            // Draw shadow.
            if (mShadowShader == null) {
                return;
            }
            float radius = getScaledRadius();
            float shadowRadius = radius + mStrokeWidth;
            mPaint.setColor(Color.BLACK);
            int offsetX = getOffsetX();
            int offsetY = getOffsetY();
            final int saveCount;
            if (canvas.isHardwareAccelerated()) {
                saveCount = canvas.saveLayer(offsetX - mStrokeWidth, offsetY,
                        offsetX + radius + shadowRadius, offsetY + shadowRadius + shadowRadius,
                        null, Canvas.CLIP_TO_LAYER_SAVE_FLAG | Canvas.HAS_ALPHA_LAYER_SAVE_FLAG);

            } else {
                saveCount = canvas.save(Canvas.CLIP_SAVE_FLAG);
                clipCanvasSoftware(canvas, Region.Op.DIFFERENCE);
            }

            mShaderMatrix.setScale(shadowRadius, shadowRadius);
            mShaderMatrix.postTranslate(radius + offsetX, shadowRadius + offsetY);
            mShadowShader.setLocalMatrix(mShaderMatrix);
            mPaint.setShader(mShadowShader);
            canvas.drawPaint(mPaint);
            mPaint.setShader(null);

            if (canvas.isHardwareAccelerated()) {
                mPaint.setXfermode(mShadowPorterDuffXfermode);
                canvas.drawCircle(radius + offsetX, radius + offsetY, radius, mPaint);
                mPaint.setXfermode(null);
            }

            canvas.restoreToCount(saveCount);
        }

        public void drawBackgroundStroke(Canvas canvas) {
            mPaint.setColor(Color.argb(255, BG_INTENSITY, BG_INTENSITY, BG_INTENSITY));
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(mStrokeWidth);
            drawCircle(canvas, 1 /* deltaRadius */);
        }

        public void drawLeaveBehind(Canvas canvas) {
            float originalScale = mScale;
            mScale = 0.5f;

            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(Color.argb(160, 245, 245, 245));
            drawCircle(canvas, 0 /* deltaRadius */);

            mScale = originalScale;
        }

        private void drawCircle(Canvas canvas,float deltaRadius) {
            float radius = getScaledRadius();
            canvas.drawCircle(radius + getOffsetX(), radius + getOffsetY(),
                    radius - deltaRadius, mPaint);
        }

        // It is the callers responsibility to save and restore the canvas layers.
        private void clipCanvasSoftware(Canvas canvas, Region.Op op) {
            mPath.reset();
            float r = getScaledRadius();
            mPath.addCircle(r + getOffsetX(), r + getOffsetY(), r, Path.Direction.CW);
            canvas.clipPath(mPath, op);
        }

        // It is the callers responsibility to save and restore the canvas layers.
        private void clipCanvasHardware(Canvas canvas) {
            mPaint.setColor(Color.BLACK);
            mPaint.setXfermode(mClipPorterDuffXfermode);

            float radius = getScaledRadius();
            mShaderMatrix.setScale(radius, radius);
            mShaderMatrix.postTranslate(radius + getOffsetX(), radius + getOffsetY());
            mClipShader.setLocalMatrix(mShaderMatrix);
            mPaint.setShader(mClipShader);
            canvas.drawPaint(mPaint);
            mPaint.setXfermode(null);
            mPaint.setShader(null);
        }

        private void delegateDrawing(CellLayout delegate, int cellX, int cellY) {
            if (mDrawingDelegate != delegate) {
                delegate.addFolderBackground(this);
            }

            mDrawingDelegate = delegate;
            delegateCellX = cellX;
            delegateCellY = cellY;

            invalidate();
        }

        private void clearDrawingDelegate() {
            if (mDrawingDelegate != null) {
                mDrawingDelegate.removeFolderBackground(this);
            }

            mDrawingDelegate = null;
            invalidate();
        }

        private boolean drawingDelegated() {
            return mDrawingDelegate != null;
        }

        private void animateScale(float finalScale, float finalMultiplier,
                final Runnable onStart, final Runnable onEnd) {
            final float scale0 = mScale;
            final float scale1 = finalScale;

            final float bgMultiplier0 = mColorMultiplier;
            final float bgMultiplier1 = finalMultiplier;

            if (mScaleAnimator != null) {
                mScaleAnimator.cancel();
            }

            mScaleAnimator = LauncherAnimUtils.ofFloat(0f, 1.0f);

            mScaleAnimator.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float prog = animation.getAnimatedFraction();
                    mScale = prog * scale1 + (1 - prog) * scale0;
                    mColorMultiplier = prog * bgMultiplier1 + (1 - prog) * bgMultiplier0;
                    invalidate();
                }
            });
            mScaleAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    if (onStart != null) {
                        onStart.run();
                    }
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (onEnd != null) {
                        onEnd.run();
                    }
                    mScaleAnimator = null;
                }
            });

            mScaleAnimator.setDuration(CONSUMPTION_ANIMATION_DURATION);
            mScaleAnimator.start();
        }

        public void animateToAccept(final CellLayout cl, final int cellX, final int cellY) {
            Runnable onStart = new Runnable() {
                @Override
                public void run() {
                    delegateDrawing(cl, cellX, cellY);
                }
            };
            animateScale(ACCEPT_SCALE_FACTOR, ACCEPT_COLOR_MULTIPLIER, onStart, null);
        }

        public void animateToRest() {
            // This can be called multiple times -- we need to make sure the drawing delegate
            // is saved and restored at the beginning of the animation, since cancelling the
            // existing animation can clear the delgate.
            final CellLayout cl = mDrawingDelegate;
            final int cellX = delegateCellX;
            final int cellY = delegateCellY;

            Runnable onStart = new Runnable() {
                @Override
                public void run() {
                    delegateDrawing(cl, cellX, cellY);
                }
            };
            Runnable onEnd = new Runnable() {
                @Override
                public void run() {
                    clearDrawingDelegate();
                }
            };
            animateScale(1f, 1f, onStart, onEnd);
        }
    }

    public void setFolderBackground(PreviewBackground bg) {
        mBackground = bg;
        mBackground.setInvalidateDelegate(this);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mReferenceDrawable != null) {
            computePreviewDrawingParams(mReferenceDrawable);
        }

        if (!mBackground.drawingDelegated()) {
            mBackground.drawBackground(canvas);
        }

        if (mFolder == null) return;
        if (mFolder.getItemCount() == 0 && !mAnimating) return;

        final int saveCount;

        if (canvas.isHardwareAccelerated()) {
            saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null,
                    Canvas.HAS_ALPHA_LAYER_SAVE_FLAG | Canvas.CLIP_TO_LAYER_SAVE_FLAG);
        } else {
            saveCount = canvas.save(Canvas.CLIP_SAVE_FLAG);
            if (mPreviewLayoutRule.clipToBackground()) {
                mBackground.clipCanvasSoftware(canvas, Region.Op.INTERSECT);
            }
        }

        // The items are drawn in coordinates relative to the preview offset
        canvas.translate(mBackground.basePreviewOffsetX, mBackground.basePreviewOffsetY);

        // The first item should be drawn last (ie. on top of later items)
        for (int i = mDrawingParams.size() - 1; i >= 0; i--) {
            PreviewItemDrawingParams p = mDrawingParams.get(i);
            if (!p.hidden) {
                drawPreviewItem(canvas, p);
            }
        }
        canvas.translate(-mBackground.basePreviewOffsetX, -mBackground.basePreviewOffsetY);

        if (mPreviewLayoutRule.clipToBackground() && canvas.isHardwareAccelerated()) {
            mBackground.clipCanvasHardware(canvas);
        }
        canvas.restoreToCount(saveCount);

        if (mPreviewLayoutRule.clipToBackground() && !mBackground.drawingDelegated()) {
            mBackground.drawBackgroundStroke(canvas);
        }

        if ((mBadgeInfo != null && mBadgeInfo.hasBadge()) || mBadgeScale > 0) {
            int offsetX = mBackground.getOffsetX();
            int offsetY = mBackground.getOffsetY();
            int previewSize = (int) (mBackground.previewSize * mBackground.mScale);
            mTempBounds.set(offsetX, offsetY, offsetX + previewSize, offsetY + previewSize);

            // If we are animating to the accepting state, animate the badge out.
            float badgeScale = Math.max(0, mBadgeScale - mBackground.getScaleProgress());
            mTempSpaceForBadgeOffset.set(getWidth() - mTempBounds.right, mTempBounds.top);
            IconPalette badgePalette = IconPalette.getFolderBadgePalette(getResources());
            mBadgeRenderer.draw(canvas, badgePalette, mBadgeInfo, mTempBounds,
                    badgeScale, mTempSpaceForBadgeOffset);
        }
    }

    class FolderPreviewItemAnim {
        ValueAnimator mValueAnimator;
        float finalScale;
        float finalTransX;
        float finalTransY;

        /**
         *
         * @param params layout params to animate
         * @param index0 original index of the item to be animated
         * @param nItems0 original number of items in the preview
         * @param index1 new index of the item to be animated
         * @param nItems1 new number of items in the preview
         * @param duration duration in ms of the animation
         * @param onCompleteRunnable runnable to execute upon animation completion
         */
        public FolderPreviewItemAnim(final PreviewItemDrawingParams params, int index0, int nItems0,
                int index1, int nItems1, int duration, final Runnable onCompleteRunnable) {

            computePreviewItemDrawingParams(index1, nItems1, mTmpParams);

            finalScale = mTmpParams.scale;
            finalTransX = mTmpParams.transX;
            finalTransY = mTmpParams.transY;

            computePreviewItemDrawingParams(index0, nItems0, mTmpParams);

            final float scale0 = mTmpParams.scale;
            final float transX0 = mTmpParams.transX;
            final float transY0 = mTmpParams.transY;

            mValueAnimator = LauncherAnimUtils.ofFloat(0f, 1.0f);
            mValueAnimator.addUpdateListener(new AnimatorUpdateListener(){
                public void onAnimationUpdate(ValueAnimator animation) {
                    float progress = animation.getAnimatedFraction();

                    params.transX = transX0 + progress * (finalTransX - transX0);
                    params.transY = transY0 + progress * (finalTransY - transY0);
                    params.scale = scale0 + progress * (finalScale - scale0);
                    invalidate();
                }
            });

            mValueAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (onCompleteRunnable != null) {
                        onCompleteRunnable.run();
                    }
                    params.anim = null;
                }
            });
            mValueAnimator.setDuration(duration);
        }

        public void start() {
            mValueAnimator.start();
        }

        public void cancel() {
            mValueAnimator.cancel();
        }

        public boolean hasEqualFinalState(FolderPreviewItemAnim anim) {
            return finalTransY == anim.finalTransY && finalTransX == anim.finalTransX &&
                    finalScale == anim.finalScale;

        }
    }

    private void animateFirstItem(final Drawable d, int duration, final boolean reverse,
            final Runnable onCompleteRunnable) {

        FolderPreviewItemAnim anim;
        if (!reverse) {
            anim = new FolderPreviewItemAnim(mDrawingParams.get(0), -1, -1, 0, 2, duration,
                    onCompleteRunnable);
        } else {
            anim = new FolderPreviewItemAnim(mDrawingParams.get(0), 0, 2, -1, -1, duration,
                    onCompleteRunnable);
        }
        anim.start();
    }

    public void setTextVisible(boolean visible) {
        if (visible) {
            mFolderName.setVisibility(VISIBLE);
        } else {
            mFolderName.setVisibility(INVISIBLE);
        }
    }

    public boolean getTextVisible() {
        return mFolderName.getVisibility() == VISIBLE;
    }

    private void updateItemDrawingParams(boolean animate) {
        List<View> items = mPreviewLayoutRule.getItemsToDisplay(mFolder);
        int nItemsInPreview = items.size();

        int prevNumItems = mDrawingParams.size();

        // We adjust the size of the list to match the number of items in the preview
        while (nItemsInPreview < mDrawingParams.size()) {
            mDrawingParams.remove(mDrawingParams.size() - 1);
        }
        while (nItemsInPreview > mDrawingParams.size()) {
            mDrawingParams.add(new PreviewItemDrawingParams(0, 0, 0, 0));
        }

        for (int i = 0; i < mDrawingParams.size(); i++) {
            PreviewItemDrawingParams p = mDrawingParams.get(i);
            p.drawable = ((TextView) items.get(i)).getCompoundDrawables()[1];

            if (!animate || FeatureFlags.LAUNCHER3_LEGACY_FOLDER_ICON) {
                computePreviewItemDrawingParams(i, nItemsInPreview, p);
                if (mReferenceDrawable == null) {
                    mReferenceDrawable = p.drawable;
                }
            } else {
                FolderPreviewItemAnim anim = new FolderPreviewItemAnim(p, i, prevNumItems, i,
                        nItemsInPreview, DROP_IN_ANIMATION_DURATION, null);

                if (p.anim != null) {
                    if (p.anim.hasEqualFinalState(anim)) {
                        // do nothing, let the current animation finish
                        continue;
                    }
                    p.anim.cancel();
                }
                p.anim = anim;
                p.anim.start();
            }
        }
    }

    @Override
    public void onItemsChanged(boolean animate) {
        updateItemDrawingParams(animate);
        invalidate();
        requestLayout();
    }

    @Override
    public void prepareAutoUpdate() {
    }

    @Override
    public void onAdd(ShortcutInfo item) {
        boolean wasBadged = mBadgeInfo.hasBadge();
        mBadgeInfo.addBadgeInfo(mLauncher.getPopupDataProvider().getBadgeInfoForItem(item));
        boolean isBadged = mBadgeInfo.hasBadge();
        updateBadgeScale(wasBadged, isBadged);
        invalidate();
        requestLayout();
    }

    @Override
    public void onRemove(ShortcutInfo item) {
        boolean wasBadged = mBadgeInfo.hasBadge();
        mBadgeInfo.subtractBadgeInfo(mLauncher.getPopupDataProvider().getBadgeInfoForItem(item));
        boolean isBadged = mBadgeInfo.hasBadge();
        updateBadgeScale(wasBadged, isBadged);
        invalidate();
        requestLayout();
    }

    @Override
    public void onTitleChanged(CharSequence title) {
        mFolderName.setText(title);
        setContentDescription(getContext().getString(R.string.folder_name_format, title));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Call the superclass onTouchEvent first, because sometimes it changes the state to
        // isPressed() on an ACTION_UP
        boolean result = super.onTouchEvent(event);

        // Check for a stylus button press, if it occurs cancel any long press checks.
        if (mStylusEventHelper.onMotionEvent(event)) {
            mLongPressHelper.cancelLongPress();
            return true;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLongPressHelper.postCheckForLongPress();
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                mLongPressHelper.cancelLongPress();
                break;
            case MotionEvent.ACTION_MOVE:
                if (!Utilities.pointInView(this, event.getX(), event.getY(), mSlop)) {
                    mLongPressHelper.cancelLongPress();
                }
                break;
        }
        return result;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    public void removeListeners() {
        mInfo.removeListener(this);
        mInfo.removeListener(mFolder);
    }

    public void shrinkAndFadeIn(boolean animate) {
        final CellLayout cl = (CellLayout) getParent().getParent();
        ((CellLayout.LayoutParams) getLayoutParams()).canReorder = true;

        // We remove and re-draw the FolderIcon in-case it has changed
        final PreviewImageView previewImage = PreviewImageView.get(getContext());
        previewImage.removeFromParent();
        copyToPreview(previewImage);

        if (cl != null) {
            cl.clearFolderLeaveBehind();
        }

        ObjectAnimator oa = LauncherAnimUtils.ofViewAlphaAndScale(previewImage, 1, 1, 1);
        oa.setDuration(getResources().getInteger(R.integer.config_folderExpandDuration));
        oa.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (cl != null) {
                    // Remove the ImageView copy of the FolderIcon and make the original visible.
                    previewImage.removeFromParent();
                    setVisibility(View.VISIBLE);
                }
            }
        });
        oa.start();
        if (!animate) {
            oa.end();
        }
    }

    public void growAndFadeOut() {
        CellLayout.LayoutParams lp = (CellLayout.LayoutParams) getLayoutParams();
        // While the folder is open, the position of the icon cannot change.
        lp.canReorder = false;
        if (mInfo.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            CellLayout cl = (CellLayout) getParent().getParent();
            cl.setFolderLeaveBehindCell(lp.cellX, lp.cellY);
        }

        // Push an ImageView copy of the FolderIcon into the DragLayer and hide the original
        PreviewImageView previewImage = PreviewImageView.get(getContext());
        copyToPreview(previewImage);
        setVisibility(View.INVISIBLE);

        ObjectAnimator oa = LauncherAnimUtils.ofViewAlphaAndScale(previewImage, 0, 1.5f, 1.5f);
        oa.setDuration(getResources().getInteger(R.integer.config_folderExpandDuration));
        oa.start();
    }

    /**
     * This method draws the FolderIcon to an ImageView and then adds and positions that ImageView
     * in the DragLayer in the exact absolute location of the original FolderIcon.
     */
    private void copyToPreview(PreviewImageView previewImageView) {
        previewImageView.copy(this);
        if (mFolder != null) {
            previewImageView.setPivotX(mFolder.getPivotXForIconAnimation());
            previewImageView.setPivotY(mFolder.getPivotYForIconAnimation());
            mFolder.bringToFront();
        }
    }

    public interface PreviewLayoutRule {
        PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
            PreviewItemDrawingParams params);
        void init(int availableSpace, int intrinsicIconSize, boolean rtl);
        float scaleForItem(int index, int totalNumItems);
        int maxNumItems();
        boolean clipToBackground();
        List<View> getItemsToDisplay(Folder folder);
    }
}
