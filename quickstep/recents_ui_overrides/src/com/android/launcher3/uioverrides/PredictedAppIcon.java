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

import static com.android.launcher3.graphics.IconShape.getShape;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.WorkspaceItemInfo;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.icons.IconNormalizer;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.views.DoubleShadowBubbleTextView;

/**
 * A BubbleTextView with a ring around it's drawable
 */
public class PredictedAppIcon extends DoubleShadowBubbleTextView {

    private static final float RING_EFFECT_RATIO = 0.11f;

    boolean mIsDrawingDot = false;
    private final DeviceProfile mDeviceProfile;
    private final Paint mIconRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean mIsPinned = false;
    private int mNormalizedIconRadius;

    public PredictedAppIcon(Context context) {
        this(context, null, 0);
    }

    public PredictedAppIcon(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PredictedAppIcon(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mDeviceProfile = Launcher.getLauncher(context).getDeviceProfile();
        mNormalizedIconRadius = IconNormalizer.getNormalizedCircleSize(getIconSize()) / 2;
        setOnClickListener(ItemClickHandler.INSTANCE);
        setOnFocusChangeListener(Launcher.getLauncher(context).getFocusHandler());
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
        int color = IconPalette.getMutedColor(info.bitmap.color, 0.54f);
        mIconRingPaint.setColor(ColorUtils.setAlphaComponent(color, 200));
    }

    /**
     * Removes prediction ring from app icon
     */
    public void pin(WorkspaceItemInfo info) {
        if (mIsPinned) return;
        applyFromWorkspaceItem(info);
        setOnLongClickListener(ItemLongClickListener.INSTANCE_WORKSPACE);
        mIsPinned = true;
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

    private int getOutlineOffsetX() {
        return (getMeasuredWidth() / 2) - mNormalizedIconRadius;
    }

    private int getOutlineOffsetY() {
        return getPaddingTop() + mDeviceProfile.folderIconOffsetYPx;
    }

    private void drawEffect(Canvas canvas) {
        getShape().drawShape(canvas, getOutlineOffsetX(), getOutlineOffsetY(),
                mNormalizedIconRadius, mIconRingPaint);
    }

    /**
     * Creates and returns a new instance of PredictedAppIcon from WorkspaceItemInfo
     */
    public static PredictedAppIcon createIcon(ViewGroup parent, WorkspaceItemInfo info) {
        PredictedAppIcon icon = (PredictedAppIcon) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.predicted_app_icon, parent, false);
        icon.applyFromWorkspaceItem(info);
        return icon;
    }

    /**
     * Draws Predicted Icon outline on cell layout
     */
    public static class PredictedIconOutlineDrawing extends CellLayout.DelegatedCellDrawing {

        private int mOffsetX;
        private int mOffsetY;
        private int mIconRadius;
        private Paint mOutlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public PredictedIconOutlineDrawing(int cellX, int cellY, PredictedAppIcon icon) {
            mDelegateCellX = cellX;
            mDelegateCellY = cellY;
            mOffsetX = icon.getOutlineOffsetX();
            mOffsetY = icon.getOutlineOffsetY();
            mIconRadius = icon.mNormalizedIconRadius;
            mOutlinePaint.setStyle(Paint.Style.STROKE);
            mOutlinePaint.setStrokeWidth(5);
            mOutlinePaint.setPathEffect(new DashPathEffect(new float[]{15, 15}, 0));
            mOutlinePaint.setColor(Color.argb(100, 245, 245, 245));
        }

        /**
         * Draws predicted app icon outline under CellLayout
         */
        @Override
        public void drawUnderItem(Canvas canvas) {
            getShape().drawShape(canvas, mOffsetX, mOffsetY, mIconRadius, mOutlinePaint);
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
