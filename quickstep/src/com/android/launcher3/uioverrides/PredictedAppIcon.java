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

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Process;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.icons.GraphicsUtils;
import com.android.launcher3.icons.IconNormalizer;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.DoubleShadowBubbleTextView;

/**
 * A BubbleTextView with a ring around it's drawable
 */
public class PredictedAppIcon extends DoubleShadowBubbleTextView {

    private static final int RING_SHADOW_COLOR = 0x99000000;
    private static final float RING_EFFECT_RATIO = 0.095f;

    boolean mIsDrawingDot = false;
    private final DeviceProfile mDeviceProfile;
    private final Paint mIconRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mRingPath = new Path();
    private final int mNormalizedIconSize;
    private final Path mShapePath;
    private final Matrix mTmpMatrix = new Matrix();

    private final BlurMaskFilter mShadowFilter;

    private boolean mIsPinned = false;
    private int mPlateColor;
    boolean mDrawForDrag = false;

    public PredictedAppIcon(Context context) {
        this(context, null, 0);
    }

    public PredictedAppIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PredictedAppIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mDeviceProfile = ActivityContext.lookupContext(context).getDeviceProfile();
        mNormalizedIconSize = IconNormalizer.getNormalizedCircleSize(getIconSize());
        int shadowSize = context.getResources().getDimensionPixelSize(
                R.dimen.blur_size_thin_outline);
        mShadowFilter = new BlurMaskFilter(shadowSize, BlurMaskFilter.Blur.OUTER);
        mShapePath = GraphicsUtils.getShapePath(mNormalizedIconSize);
    }

    @Override
    public void onDraw(Canvas canvas) {
        int count = canvas.save();
        if (!mIsPinned) {
            drawEffect(canvas);
            canvas.translate(getWidth() * RING_EFFECT_RATIO, getHeight() * RING_EFFECT_RATIO);
            canvas.scale(1 - 2 * RING_EFFECT_RATIO, 1 - 2 * RING_EFFECT_RATIO);
        }
        super.onDraw(canvas);
        canvas.restoreToCount(count);
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

    @Override
    public void applyFromWorkspaceItem(WorkspaceItemInfo info) {
        super.applyFromWorkspaceItem(info);
        mPlateColor = ColorUtils.setAlphaComponent(mDotParams.color, 200);
        if (mIsPinned) {
            setContentDescription(info.contentDescription);
        } else {
            setContentDescription(
                    getContext().getString(R.string.hotseat_prediction_content_description,
                            info.contentDescription));
        }
    }

    /**
     * Removes prediction ring from app icon
     */
    public void pin(WorkspaceItemInfo info) {
        if (mIsPinned) return;
        mIsPinned = true;
        applyFromWorkspaceItem(info);
        setOnLongClickListener(ItemLongClickListener.INSTANCE_WORKSPACE);
        ((CellLayout.LayoutParams) getLayoutParams()).canReorder = true;
        invalidate();
    }

    /**
     * prepares prediction icon for usage after bind
     */
    public void finishBinding(OnLongClickListener longClickListener) {
        setOnLongClickListener(longClickListener);
        ((CellLayout.LayoutParams) getLayoutParams()).canReorder = false;
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
        return (getMeasuredWidth() - mNormalizedIconSize) / 2;
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
        updateRingPath();
    }

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
        updateRingPath();
    }

    private void updateRingPath() {
        boolean isBadged = false;
        if (getTag() instanceof WorkspaceItemInfo) {
            WorkspaceItemInfo info = (WorkspaceItemInfo) getTag();
            isBadged = !Process.myUserHandle().equals(info.user)
                    || info.itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT
                    || info.itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
        }

        mRingPath.reset();
        mTmpMatrix.setTranslate(getOutlineOffsetX(), getOutlineOffsetY());

        mRingPath.addPath(mShapePath, mTmpMatrix);
        if (isBadged) {
            float outlineSize = mNormalizedIconSize * RING_EFFECT_RATIO;
            float iconSize = getIconSize() * (1 - 2 * RING_EFFECT_RATIO);
            float badgeSize = LauncherIcons.getBadgeSizeForIconSize((int) iconSize) + outlineSize;
            float scale = badgeSize / mNormalizedIconSize;
            mTmpMatrix.postTranslate(mNormalizedIconSize, mNormalizedIconSize);
            mTmpMatrix.preScale(scale, scale);
            mTmpMatrix.preTranslate(-mNormalizedIconSize, -mNormalizedIconSize);
            mRingPath.addPath(mShapePath, mTmpMatrix);
        }
    }

    private void drawEffect(Canvas canvas) {
        // Don't draw ring effect if item is about to be dragged.
        if (mDrawForDrag) {
            return;
        }
        mIconRingPaint.setColor(RING_SHADOW_COLOR);
        mIconRingPaint.setMaskFilter(mShadowFilter);
        canvas.drawPath(mRingPath, mIconRingPaint);
        mIconRingPaint.setColor(mPlateColor);
        mIconRingPaint.setMaskFilter(null);
        canvas.drawPath(mRingPath, mIconRingPaint);
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
        icon.setOnClickListener(ItemClickHandler.INSTANCE);
        icon.setOnFocusChangeListener(Launcher.getLauncher(parent.getContext()).getFocusHandler());
        return icon;
    }

    /**
     * Draws Predicted Icon outline on cell layout
     */
    public static class PredictedIconOutlineDrawing extends CellLayout.DelegatedCellDrawing {

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
