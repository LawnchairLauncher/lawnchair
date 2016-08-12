/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.launcher3.shortcuts;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.IconCache;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LogAccelerateInterpolator;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.shortcuts.DeepShortcutsContainer.UnbadgedShortcutInfo;
import com.android.launcher3.util.PillRevealOutlineProvider;
import com.android.launcher3.util.PillWidthRevealOutlineProvider;

/**
 * A {@link android.widget.FrameLayout} that contains a {@link DeepShortcutView}.
 * This lets us animate the DeepShortcutView (icon and text) separately from the background.
 */
public class DeepShortcutView extends FrameLayout implements ValueAnimator.AnimatorUpdateListener {

    private static final Point sTempPoint = new Point();

    private final Rect mPillRect;

    private DeepShortcutTextView mBubbleText;
    private View mIconView;
    private float mOpenAnimationProgress;

    private UnbadgedShortcutInfo mInfo;

    public DeepShortcutView(Context context) {
        this(context, null, 0);
    }

    public DeepShortcutView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeepShortcutView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mPillRect = new Rect();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = findViewById(R.id.deep_shortcut_icon);
        mBubbleText = (DeepShortcutTextView) findViewById(R.id.deep_shortcut);
    }

    public DeepShortcutTextView getBubbleText() {
        return mBubbleText;
    }

    public void setWillDrawIcon(boolean willDraw) {
        mIconView.setVisibility(willDraw ? View.VISIBLE : View.INVISIBLE);
    }

    public boolean willDrawIcon() {
        return mIconView.getVisibility() == View.VISIBLE;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        mPillRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    /** package private **/
    void applyShortcutInfo(UnbadgedShortcutInfo info, DeepShortcutsContainer container) {
        mInfo = info;
        IconCache cache = LauncherAppState.getInstance().getIconCache();
        mBubbleText.applyFromShortcutInfo(info, cache);
        mIconView.setBackground(mBubbleText.getIcon());

        // Use the long label as long as it exists and fits.
        CharSequence longLabel = info.mDetail.getLongLabel();
        int availableWidth = mBubbleText.getWidth() - mBubbleText.getTotalPaddingLeft()
                - mBubbleText.getTotalPaddingRight();
        boolean usingLongLabel = !TextUtils.isEmpty(longLabel)
                && mBubbleText.getPaint().measureText(longLabel.toString()) <= availableWidth;
        mBubbleText.setText(usingLongLabel ? longLabel : info.mDetail.getShortLabel());

        // TODO: Add the click handler to this view directly and not the child view.
        mBubbleText.setOnClickListener(Launcher.getLauncher(getContext()));
        mBubbleText.setOnLongClickListener(container);
        mBubbleText.setOnTouchListener(container);
    }

    /**
     * Returns the shortcut info that is suitable to be added on the homescreen
     */
    public ShortcutInfo getFinalInfo() {
        ShortcutInfo badged = new ShortcutInfo(mInfo);
        // Queue an update task on the worker thread. This ensures that the badged
        // shortcut eventually gets its icon updated.
        Launcher.getLauncher(getContext()).getModel().updateShortcutInfo(mInfo.mDetail, badged);
        return badged;
    }

    public View getIconView() {
        return mIconView;
    }

    /**
     * Creates an animator to play when the shortcut container is being opened.
     */
    public Animator createOpenAnimation(boolean isContainerAboveIcon, boolean pivotLeft) {
        Point center = getIconCenter();
        ValueAnimator openAnimator =  new ZoomRevealOutlineProvider(center.x, center.y,
                mPillRect, this, mIconView, isContainerAboveIcon, pivotLeft)
                        .createRevealAnimator(this, false);
        mOpenAnimationProgress = 0f;
        openAnimator.addUpdateListener(this);
        return openAnimator;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator valueAnimator) {
        mOpenAnimationProgress = valueAnimator.getAnimatedFraction();
    }

    public boolean isOpenOrOpening() {
        return mOpenAnimationProgress > 0;
    }

    /**
     * Creates an animator to play when the shortcut container is being closed.
     */
    public Animator createCloseAnimation(boolean isContainerAboveIcon, boolean pivotLeft,
            long duration) {
        Point center = getIconCenter();
        ValueAnimator closeAnimator =  new ZoomRevealOutlineProvider(center.x, center.y,
                mPillRect, this, mIconView, isContainerAboveIcon, pivotLeft)
                        .createRevealAnimator(this, true);
        // Scale down the duration and interpolator according to the progress
        // that the open animation was at when the close started.
        closeAnimator.setDuration((long) (duration * mOpenAnimationProgress));
        closeAnimator.setInterpolator(new CloseInterpolator(mOpenAnimationProgress));
        return closeAnimator;
    }

    /**
     * Creates an animator which clips the container to form a circle around the icon.
     */
    public Animator collapseToIcon() {
        int halfHeight = getMeasuredHeight() / 2;
        int iconCenterX = getIconCenter().x;
        return new PillWidthRevealOutlineProvider(mPillRect,
                iconCenterX - halfHeight, iconCenterX + halfHeight)
                        .createRevealAnimator(this, true);
    }

    /**
     * Returns the position of the center of the icon relative to the container.
     */
    public Point getIconCenter() {
        sTempPoint.y = sTempPoint.x = getMeasuredHeight() / 2;
        if (Utilities.isRtl(getResources())) {
            sTempPoint.x = getMeasuredWidth() - sTempPoint.x;
        }
        return sTempPoint;
    }

    /**
     * Extension of {@link PillRevealOutlineProvider} which scales the icon based on the height.
     */
    private static class ZoomRevealOutlineProvider extends PillRevealOutlineProvider {

        private final View mTranslateView;
        private final View mZoomView;

        private final float mFullHeight;
        private final float mTranslateYMultiplier;

        private final boolean mPivotLeft;
        private final float mTranslateX;

        public ZoomRevealOutlineProvider(int x, int y, Rect pillRect,
                View translateView, View zoomView, boolean isContainerAboveIcon, boolean pivotLeft) {
            super(x, y, pillRect);
            mTranslateView = translateView;
            mZoomView = zoomView;
            mFullHeight = pillRect.height();

            mTranslateYMultiplier = isContainerAboveIcon ? 0.5f : -0.5f;

            mPivotLeft = pivotLeft;
            mTranslateX = pivotLeft ? pillRect.height() / 2 : pillRect.right - pillRect.height() / 2;
        }

        @Override
        public void setProgress(float progress) {
            super.setProgress(progress);

            mZoomView.setScaleX(progress);
            mZoomView.setScaleY(progress);

            float height = mOutline.height();
            mTranslateView.setTranslationY(mTranslateYMultiplier * (mFullHeight - height));

            float pivotX = mPivotLeft ? (mOutline.left + height / 2) : (mOutline.right - height / 2);
            mTranslateView.setTranslationX(mTranslateX - pivotX);
        }
    }

    /**
     * An interpolator that reverses the current open animation progress.
     */
    private static class CloseInterpolator extends LogAccelerateInterpolator {
        private float mStartProgress;
        private float mRemainingProgress;

        /**
         * @param openAnimationProgress The progress that the open interpolator ended at.
         */
        public CloseInterpolator(float openAnimationProgress) {
            super(100, 0);
            mStartProgress = 1f - openAnimationProgress;
            mRemainingProgress = openAnimationProgress;
        }

        @Override
        public float getInterpolation(float v) {
            return mStartProgress + super.getInterpolation(v) * mRemainingProgress;
        }
    }
}
