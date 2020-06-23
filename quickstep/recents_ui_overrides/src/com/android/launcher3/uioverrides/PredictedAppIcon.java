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

import static com.android.launcher3.accessibility.LauncherAccessibilityDelegate.PIN_PREDICTION;
import static com.android.launcher3.graphics.IconShape.getShape;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Process;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.graphics.IconPalette;
import com.android.launcher3.hybridhotseat.HotseatPredictionController;
import com.android.launcher3.icons.IconNormalizer;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.touch.ItemLongClickListener;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.DoubleShadowBubbleTextView;

/**
 * A BubbleTextView with a ring around it's drawable
 */
public class PredictedAppIcon extends DoubleShadowBubbleTextView implements
        LauncherAccessibilityDelegate.AccessibilityActionHandler {

    private static final int RING_SHADOW_COLOR = 0x99000000;
    private static final float RING_EFFECT_RATIO = 0.095f;

    boolean mIsDrawingDot = false;
    private final DeviceProfile mDeviceProfile;
    private final Paint mIconRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mRingPath = new Path();
    private boolean mIsPinned = false;
    private final int mNormalizedIconRadius;
    private final BlurMaskFilter mShadowFilter;
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
        mNormalizedIconRadius = IconNormalizer.getNormalizedCircleSize(getIconSize()) / 2;
        int shadowSize = context.getResources().getDimensionPixelSize(
                R.dimen.blur_size_thin_outline);
        mShadowFilter = new BlurMaskFilter(shadowSize, BlurMaskFilter.Blur.OUTER);
    }

    @Override
    public void onDraw(Canvas canvas) {
        int count = canvas.save();
        if (!mIsPinned) {
            boolean isBadged = getTag() instanceof WorkspaceItemInfo
                    && !Process.myUserHandle().equals(((ItemInfo) getTag()).user);
            drawEffect(canvas, isBadged);
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
        mPlateColor = ColorUtils.setAlphaComponent(color, 200);
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
    public void addSupportedAccessibilityActions(AccessibilityNodeInfo accessibilityNodeInfo) {
        if (!mIsPinned) {
            accessibilityNodeInfo.addAction(
                    new AccessibilityNodeInfo.AccessibilityAction(PIN_PREDICTION,
                            getContext().getText(R.string.pin_prediction)));
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, ItemInfo info) {
        QuickstepLauncher launcher = Launcher.cast(Launcher.getLauncher(getContext()));
        if (action == PIN_PREDICTION) {
            if (launcher == null || launcher.getHotseatPredictionController() == null) {
                return false;
            }
            HotseatPredictionController controller = launcher.getHotseatPredictionController();
            controller.pinPrediction(info);
            return true;
        }
        return false;
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

    private void drawEffect(Canvas canvas, boolean isBadged) {
        // Don't draw ring effect if item is about to be dragged.
        if (mDrawForDrag) {
            return;
        }
        mRingPath.reset();
        getShape().addToPath(mRingPath, getOutlineOffsetX(), getOutlineOffsetY(),
                mNormalizedIconRadius);
        if (isBadged) {
            float outlineSize = mNormalizedIconRadius * RING_EFFECT_RATIO * 2;
            float iconSize = getIconSize() * (1 - 2 * RING_EFFECT_RATIO);
            float badgeSize = LauncherIcons.getBadgeSizeForIconSize((int) iconSize) + outlineSize;
            float badgeInset = mNormalizedIconRadius * 2 - badgeSize;
            getShape().addToPath(mRingPath, getOutlineOffsetX() + badgeInset,
                    getOutlineOffsetY() + badgeInset, badgeSize / 2);

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
            mOutlinePaint.setStyle(Paint.Style.FILL);
            mOutlinePaint.setColor(Color.argb(24, 245, 245, 245));
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
