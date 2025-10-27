/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import static com.android.app.animation.Interpolators.ACCELERATE_DECELERATE;
import static com.android.launcher3.icons.FastBitmapDrawable.getDisabledColorFilter;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.Log;
import android.util.Property;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.celllayout.DelegatedCellDrawing;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.DoubleShadowBubbleTextView;

import app.lawnchair.theme.color.tokens.ColorTokens;

/**
 * A BubbleTextView with a ring around it's drawable
 */
public class PredictedAppIcon extends DoubleShadowBubbleTextView {

    private static final float RING_SCALE_START_VALUE = 0.75f;
    private static final int RING_SHADOW_COLOR = 0x99000000;
    private static final float RING_EFFECT_RATIO = Flags.enableLauncherIconShapes() ? 0.1f : 0.095f;
    private static final long ICON_CHANGE_ANIM_DURATION = 360;
    private static final long ICON_CHANGE_ANIM_STAGGER = 50;

    private static final Property<PredictedAppIcon, Float> RING_SCALE_PROPERTY =
            new Property<>(Float.TYPE, "ringScale") {
                @Override
                public Float get(PredictedAppIcon icon) {
                    return icon.mRingScale;
                }

                @Override
                public void set(PredictedAppIcon icon, Float value) {
                    icon.mRingScale = value;
                    icon.invalidate();
                }
            };

    boolean mIsDrawingDot = false;
    private final DeviceProfile mDeviceProfile;
    private final Paint mIconRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mRingPath = new Path();
    private final int mNormalizedIconSize;
    private final Path mShapePath;
    private final Matrix mTmpMatrix = new Matrix();

    private final BlurMaskFilter mShadowFilter;

    private boolean mIsPinned = false;
    private final AnimColorHolder mPlateColor = new AnimColorHolder();
    boolean mDrawForDrag = false;

    // Used for the "slot-machine" animation when prediction changes.
    private final Rect mSlotIconBound = new Rect(0, 0, getIconSize(), getIconSize());
    private Drawable mSlotMachineIcon;
    private float mSlotMachineIconTranslationY;

    // Used to animate the "ring" around predicted icons
    private float mRingScale = 1f;
    private boolean mForceHideRing = false;
    private Animator mRingScaleAnim;

    private int mWidth;

    private static final FloatProperty<PredictedAppIcon> SLOT_MACHINE_TRANSLATION_Y =
            new FloatProperty<PredictedAppIcon>("slotMachineTranslationY") {
        @Override
        public void setValue(PredictedAppIcon predictedAppIcon, float transY) {
            predictedAppIcon.mSlotMachineIconTranslationY = transY;
            predictedAppIcon.invalidate();
        }

        @Override
        public Float get(PredictedAppIcon predictedAppIcon) {
            return predictedAppIcon.mSlotMachineIconTranslationY;
        }
    };

    public PredictedAppIcon(Context context) {
        this(context, null, 0);
    }

    public PredictedAppIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PredictedAppIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mDeviceProfile = ActivityContext.lookupContext(context).getDeviceProfile();
        mNormalizedIconSize = Math.round(getIconSize() * ICON_VISIBLE_AREA_FACTOR);
        int shadowSize = context.getResources().getDimensionPixelSize(
                R.dimen.blur_size_thin_outline);
        mShadowFilter = new BlurMaskFilter(shadowSize, BlurMaskFilter.Blur.OUTER);
        mShapePath = ThemeManager.INSTANCE.get(context).getIconShape().getPath(mNormalizedIconSize);
    }

    @Override
    public void onDraw(Canvas canvas) {
        int count = canvas.save();
        boolean isSlotMachineAnimRunning = mSlotMachineIcon != null;
        if (!mIsPinned) {
            drawRingEffect(canvas);
            if (isSlotMachineAnimRunning) {
                // Clip to to outside of the ring during the slot machine animation.
                canvas.clipPath(mRingPath);
            }
            canvas.scale(1 - 2f * RING_EFFECT_RATIO, 1 - 2f * RING_EFFECT_RATIO,
                    getWidth() * .5f, getHeight() * .5f);
            if (isSlotMachineAnimRunning) {
                canvas.translate(0, mSlotMachineIconTranslationY);
                mSlotMachineIcon.setBounds(mSlotIconBound);
                mSlotMachineIcon.draw(canvas);
                canvas.translate(0, getSlotMachineIconPlusSpacingSize());
            }
        }
        super.onDraw(canvas);
        canvas.restoreToCount(count);
    }

    private float getSlotMachineIconPlusSpacingSize() {
        return getIconSize() + getOutlineOffsetY();
    }

    @Override
    protected void drawDotIfNecessary(Canvas canvas) {
        mIsDrawingDot = true;
        int count = canvas.save();
        canvas.translate(-getWidth() * RING_EFFECT_RATIO, -getHeight() * RING_EFFECT_RATIO);
        canvas.scale(1 + 2 * RING_EFFECT_RATIO, 1 + 2 * RING_EFFECT_RATIO);
        super.drawDotIfNecessary(canvas);
        canvas.restoreToCount(count);
        mIsDrawingDot = false;
    }

    /**
     * Returns whether the newInfo differs from the current getTag().
     */
    private boolean shouldAnimateIconChange(WorkspaceItemInfo newInfo) {
        boolean changedIcons = getTag() instanceof WorkspaceItemInfo oldInfo
                && oldInfo.getTargetComponent() != null
                && newInfo.getTargetComponent() != null
                && !oldInfo.getTargetComponent().equals(newInfo.getTargetComponent());
        return changedIcons && isShown();
    }

    @Override
    public void applyIconAndLabel(ItemInfoWithIcon info) {
        super.applyIconAndLabel(info);
        if (getIcon().isThemed()) {
            mPlateColor.endColor = ColorTokens.PredictedPlateColor.resolveColor(getContext());
        } else {
            float[] hctPlateColor = new float[3];
            ColorUtils.colorToM3HCT(mDotParams.appColor, hctPlateColor);
            mPlateColor.endColor = ColorUtils.M3HCTToColor(hctPlateColor[0], 36, 85);
        }
        mPlateColor.onUpdate();
    }

    /**
     * Tries to apply the icon with animation and returns true if the icon was indeed animated
     */
    public boolean applyFromWorkspaceItemWithAnimation(WorkspaceItemInfo info, int staggerIndex) {
        boolean animate = shouldAnimateIconChange(info);
        Drawable oldIcon = getIcon();
        int oldPlateColor = mPlateColor.currentColor;
        applyFromWorkspaceItem(info);

        setContentDescription(
                mIsPinned ? info.contentDescription :
                        getContext().getString(R.string.hotseat_prediction_content_description,
                                info.contentDescription));

        if (!animate) {
            mPlateColor.startColor = mPlateColor.endColor;
            mPlateColor.progress.value = 1;
            mPlateColor.onUpdate();
        } else {
            mPlateColor.startColor = oldPlateColor;
            mPlateColor.progress.value = 0;
            mPlateColor.onUpdate();

            AnimatorSet changeIconAnim = new AnimatorSet();

            ObjectAnimator plateColorAnim =
                    ObjectAnimator.ofFloat(mPlateColor.progress, AnimatedFloat.VALUE, 0, 1);
            plateColorAnim.setAutoCancel(true);
            changeIconAnim.play(plateColorAnim);

            if (!mIsPinned && oldIcon != null) {
                // Play the slot machine icon
                mSlotMachineIcon = oldIcon;

                float finalTrans = -getSlotMachineIconPlusSpacingSize();
                Keyframe[] keyframes = new Keyframe[] {
                        Keyframe.ofFloat(0f, 0f),
                        Keyframe.ofFloat(0.82f, finalTrans - getOutlineOffsetY() / 2f), // Overshoot
                        Keyframe.ofFloat(1f, finalTrans) // Ease back into the final position
                };
                keyframes[1].setInterpolator(ACCELERATE_DECELERATE);
                keyframes[2].setInterpolator(ACCELERATE_DECELERATE);

                ObjectAnimator slotMachineAnim = ObjectAnimator.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofKeyframe(SLOT_MACHINE_TRANSLATION_Y, keyframes));
                slotMachineAnim.addListener(AnimatorListeners.forEndCallback(() -> {
                    mSlotMachineIcon = null;
                    mSlotMachineIconTranslationY = 0;
                    invalidate();
                }));
                slotMachineAnim.setAutoCancel(true);
                changeIconAnim.play(slotMachineAnim);
            }

            changeIconAnim.setStartDelay(staggerIndex * ICON_CHANGE_ANIM_STAGGER);
            changeIconAnim.setDuration(ICON_CHANGE_ANIM_DURATION).start();
        }
        return animate;
    }

    /**
     * Removes prediction ring from app icon
     */
    public void pin(WorkspaceItemInfo info) {
        if (mIsPinned) return;
        mIsPinned = true;
        applyFromWorkspaceItem(info);
        setOnLongClickListener(ItemLongClickListener.INSTANCE_WORKSPACE);
        ((CellLayoutLayoutParams) getLayoutParams()).canReorder = true;
        invalidate();
    }

    /**
     * prepares prediction icon for usage after bind
     */
    public void finishBinding(OnLongClickListener longClickListener) {
        setOnLongClickListener(longClickListener);
        ((CellLayoutLayoutParams) getLayoutParams()).canReorder = false;
        setTextVisibility(false);
        verifyHighRes();
    }

    @Override
    public void getIconBounds(Rect outBounds) {
        super.getIconBounds(outBounds);
        if (!mIsPinned && !mIsDrawingDot) {
            int predictionInset = (int) (getIconSize() * RING_EFFECT_RATIO);
            outBounds.inset(predictionInset, predictionInset);
        }
    }

    public boolean isPinned() {
        return mIsPinned;
    }

    private int getOutlineOffsetX() {
        int measuredWidth = getMeasuredWidth();
        if (mDisplay != DISPLAY_TASKBAR) {
            Log.d("b/387844520", "getOutlineOffsetX: measured width = " + measuredWidth
                    + ", mNormalizedIconSize = " + mNormalizedIconSize
                    + ", last updated width = " + mWidth);
        }
        return (mWidth - mNormalizedIconSize) / 2;
    }

    private int getOutlineOffsetY() {
        if (mDisplay != DISPLAY_TASKBAR) {
            return getPaddingTop() + mDeviceProfile.folderIconOffsetYPx;
        }
        return (getMeasuredHeight() - mNormalizedIconSize) / 2;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mSlotIconBound.offsetTo((w - getIconSize()) / 2, (h - getIconSize()) / 2);
        if (mDisplay != DISPLAY_TASKBAR) {
            Log.d("b/387844520", "calling updateRingPath from onSizeChanged");
        }
        updateRingPath();
    }

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
        updateRingPath();
    }

    private void updateRingPath() {
        mRingPath.reset();
        mTmpMatrix.reset();
        mTmpMatrix.setTranslate(getOutlineOffsetX(), getOutlineOffsetY());
        mRingPath.addPath(mShapePath, mTmpMatrix);

        FastBitmapDrawable icon = getIcon();
        if (icon != null && icon.getBadge() != null) {
            float outlineSize = mNormalizedIconSize * RING_EFFECT_RATIO;
            float iconSize = getIconSize() * (1 - 2 * RING_EFFECT_RATIO);
            float badgeSize = LauncherIcons.getBadgeSizeForIconSize((int) iconSize) + outlineSize;
            float scale = badgeSize / mNormalizedIconSize;
            mTmpMatrix.postTranslate(mNormalizedIconSize, mNormalizedIconSize);
            mTmpMatrix.preScale(scale, scale);
            mTmpMatrix.preTranslate(-mNormalizedIconSize, -mNormalizedIconSize);
            mRingPath.addPath(mShapePath, mTmpMatrix);
        }
        invalidate();
    }

    @Override
    public void setForceHideRing(boolean forceHideRing) {
        if (mForceHideRing == forceHideRing) {
            return;
        }
        mForceHideRing = forceHideRing;

        if (forceHideRing) {
            invalidate();
        } else {
            animateRingScale(RING_SCALE_START_VALUE, 1);
        }
    }

    private void cancelRingScaleAnim() {
        if (mRingScaleAnim != null) {
            mRingScaleAnim.cancel();
        }
    }

    private void animateRingScale(float... ringScale) {
        cancelRingScaleAnim();
        mRingScaleAnim = ObjectAnimator.ofFloat(this, RING_SCALE_PROPERTY, ringScale);
        mRingScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mRingScaleAnim = null;
            }
        });
        mRingScaleAnim.start();
    }

    private void drawRingEffect(Canvas canvas) {
        // Don't draw ring effect if item is about to be dragged or if the icon is not visible.
        if (mDrawForDrag || !mIsIconVisible || mForceHideRing) {
            return;
        }
        mIconRingPaint.setColor(RING_SHADOW_COLOR);
        mIconRingPaint.setMaskFilter(mShadowFilter);
        int count = canvas.save();
        if (Flags.enableLauncherIconShapes()) {
            // Scale canvas properly to for ring to be inner stroke and not exceed bounds.
            // Since STROKE draws half on either side of Path, scale canvas down by 1x stroke ratio.
            canvas.scale(
                    mRingScale * (1f - RING_EFFECT_RATIO),
                    mRingScale * (1f - RING_EFFECT_RATIO),
                    getWidth() / 2f,
                    getHeight() / 2f);
        } else if (Float.compare(1, mRingScale) != 0) {
            canvas.scale(mRingScale, mRingScale, getWidth() / 2f, getHeight() / 2f);
        }
        // Draw ring shadow around canvas.
        canvas.drawPath(mRingPath, mIconRingPaint);
        mIconRingPaint.setColor(mPlateColor.currentColor);
        if (Flags.enableLauncherIconShapes()) {
            mIconRingPaint.setStrokeWidth(getWidth() * RING_EFFECT_RATIO);
            // Using FILL_AND_STROKE as there is still some gap to fill,
            // between inner curve of ring / outer curve of icon.
            mIconRingPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        }
        mIconRingPaint.setMaskFilter(null);
        // Draw ring around canvas.
        canvas.drawPath(mRingPath, mIconRingPaint);
        canvas.restoreToCount(count);
    }

    @Override
    public void setIconDisabled(boolean isDisabled) {
        super.setIconDisabled(isDisabled);
        mIconRingPaint.setColorFilter(isDisabled ? getDisabledColorFilter() : null);
        invalidate();
    }

    @Override
    protected void setItemInfo(ItemInfoWithIcon itemInfo) {
        super.setItemInfo(itemInfo);
        setIconDisabled(itemInfo.isDisabled());
    }

    @Override
    public void getSourceVisualDragBounds(Rect bounds) {
        super.getSourceVisualDragBounds(bounds);
        if (!mIsPinned) {
            int internalSize = (int) (bounds.width() * RING_EFFECT_RATIO);
            bounds.inset(internalSize, internalSize);
        }
    }

    @Override
    public SafeCloseable prepareDrawDragView() {
        mDrawForDrag = true;
        invalidate();
        SafeCloseable r = super.prepareDrawDragView();
        return () -> {
            r.close();
            mDrawForDrag = false;
        };
    }

    /**
     * Creates and returns a new instance of PredictedAppIcon from WorkspaceItemInfo
     */
    public static PredictedAppIcon createIcon(ViewGroup parent, WorkspaceItemInfo info) {
        PredictedAppIcon icon = (PredictedAppIcon) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.predicted_app_icon, parent, false);
        icon.applyFromWorkspaceItem(info);
        Launcher launcher = Launcher.getLauncher(parent.getContext());
        icon.setOnClickListener(launcher.getItemOnClickListener());
        icon.setOnFocusChangeListener(launcher.getFocusHandler());
        return icon;
    }

    private class AnimColorHolder {

        public final AnimatedFloat progress = new AnimatedFloat(this::onUpdate, 1);
        public final ArgbEvaluator evaluator = ArgbEvaluator.getInstance();
        public Integer startColor = 0;
        public Integer endColor = 0;

        public int currentColor = 0;

        private void onUpdate() {
            currentColor = (Integer) evaluator.evaluate(progress.value, startColor, endColor);
            invalidate();
        }
    }

    /**
     * Draws Predicted Icon outline on cell layout
     */
    public static class PredictedIconOutlineDrawing extends DelegatedCellDrawing {

        private final PredictedAppIcon mIcon;
        private final Paint mOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public PredictedIconOutlineDrawing(int cellX, int cellY, PredictedAppIcon icon) {
            mDelegateCellX = cellX;
            mDelegateCellY = cellY;
            mIcon = icon;
            mOutlinePaint.setStyle(Paint.Style.FILL);
            mOutlinePaint.setColor(Color.argb(24, 245, 245, 245));
        }

        /**
         * Draws predicted app icon outline under CellLayout
         */
        @Override
        public void drawUnderItem(Canvas canvas) {
            canvas.save();
            canvas.translate(mIcon.getOutlineOffsetX(), mIcon.getOutlineOffsetY());
            canvas.drawPath(mIcon.mShapePath, mOutlinePaint);
            canvas.restore();
        }

        /**
         * Draws PredictedAppIcon outline over CellLayout
         */
        @Override
        public void drawOverItem(Canvas canvas) {
            // Does nothing
        }
    }
}
