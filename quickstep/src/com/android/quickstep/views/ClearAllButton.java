/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.quickstep.views;

import android.content.Context;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.widget.Button;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.touch.PagedOrientationHandler;

public class ClearAllButton extends Button {

    public static final FloatProperty<ClearAllButton> VISIBILITY_ALPHA =
            new FloatProperty<ClearAllButton>("visibilityAlpha") {
                @Override
                public Float get(ClearAllButton view) {
                    return view.mVisibilityAlpha;
                }

                @Override
                public void setValue(ClearAllButton view, float v) {
                    view.setVisibilityAlpha(v);
                }
            };

    public static final FloatProperty<ClearAllButton> DISMISS_ALPHA =
            new FloatProperty<ClearAllButton>("dismissAlpha") {
                @Override
                public Float get(ClearAllButton view) {
                    return view.mDismissAlpha;
                }

                @Override
                public void setValue(ClearAllButton view, float v) {
                    view.setDismissAlpha(v);
                }
            };

    private final StatefulActivity mActivity;
    private float mScrollAlpha = 1;
    private float mContentAlpha = 1;
    private float mVisibilityAlpha = 1;
    private float mDismissAlpha = 1;
    private float mFullscreenProgress = 1;
    private float mGridProgress = 1;

    private boolean mIsRtl;
    private float mNormalTranslationPrimary;
    private float mFullscreenTranslationPrimary;
    private float mGridTranslationPrimary;
    private float mGridScrollOffset;
    private float mScrollOffsetPrimary;
    private float mSplitSelectScrollOffsetPrimary;

    private int mSidePadding;

    public ClearAllButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mIsRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        mActivity = StatefulActivity.fromContext(context);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        PagedOrientationHandler orientationHandler = getRecentsView().getPagedOrientationHandler();
        mSidePadding = orientationHandler.getClearAllSidePadding(getRecentsView(), mIsRtl);
    }

    private RecentsView getRecentsView() {
        return (RecentsView) getParent();
    }

    @Override
    public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        mIsRtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    public float getScrollAlpha() {
        return mScrollAlpha;
    }

    public void setContentAlpha(float alpha) {
        if (mContentAlpha != alpha) {
            mContentAlpha = alpha;
            updateAlpha();
        }
    }

    public void setVisibilityAlpha(float alpha) {
        if (mVisibilityAlpha != alpha) {
            mVisibilityAlpha = alpha;
            updateAlpha();
        }
    }

    public void setDismissAlpha(float alpha) {
        if (mDismissAlpha != alpha) {
            mDismissAlpha = alpha;
            updateAlpha();
        }
    }

    public void onRecentsViewScroll(int scroll, boolean gridEnabled) {
        RecentsView recentsView = getRecentsView();
        if (recentsView == null) {
            return;
        }

        PagedOrientationHandler orientationHandler = recentsView.getPagedOrientationHandler();
        float orientationSize = orientationHandler.getPrimaryValue(getWidth(), getHeight());
        if (orientationSize == 0) {
            return;
        }

        int clearAllScroll = recentsView.getClearAllScroll();
        int adjustedScrollFromEdge = Math.abs(scroll - clearAllScroll);
        float shift = Math.min(adjustedScrollFromEdge, orientationSize);
        mNormalTranslationPrimary = mIsRtl ? -shift : shift;
        if (!gridEnabled) {
            mNormalTranslationPrimary += mSidePadding;
        }
        applyPrimaryTranslation();
        applySecondaryTranslation();
        float clearAllSpacing =
                recentsView.getPageSpacing() + recentsView.getClearAllExtraPageSpacing();
        clearAllSpacing = mIsRtl ? -clearAllSpacing : clearAllSpacing;
        mScrollAlpha = Math.max((clearAllScroll + clearAllSpacing - scroll) / clearAllSpacing, 0);
        updateAlpha();
    }

    private void updateAlpha() {
        final float alpha = mScrollAlpha * mContentAlpha * mVisibilityAlpha * mDismissAlpha;
        setAlpha(alpha);
        setClickable(Math.min(alpha, 1) == 1);
    }

    public void setFullscreenTranslationPrimary(float fullscreenTranslationPrimary) {
        mFullscreenTranslationPrimary = fullscreenTranslationPrimary;
        applyPrimaryTranslation();
    }

    public void setGridTranslationPrimary(float gridTranslationPrimary) {
        mGridTranslationPrimary = gridTranslationPrimary;
        applyPrimaryTranslation();
    }

    public void setGridScrollOffset(float gridScrollOffset) {
        mGridScrollOffset = gridScrollOffset;
    }

    public void setScrollOffsetPrimary(float scrollOffsetPrimary) {
        mScrollOffsetPrimary = scrollOffsetPrimary;
    }

    public void setSplitSelectScrollOffsetPrimary(float splitSelectScrollOffsetPrimary) {
        mSplitSelectScrollOffsetPrimary = splitSelectScrollOffsetPrimary;
    }

    public float getScrollAdjustment(boolean fullscreenEnabled, boolean gridEnabled) {
        float scrollAdjustment = 0;
        if (fullscreenEnabled) {
            scrollAdjustment += mFullscreenTranslationPrimary;
        }
        if (gridEnabled) {
            scrollAdjustment += mGridTranslationPrimary + mGridScrollOffset;
        }
        scrollAdjustment += mScrollOffsetPrimary;
        scrollAdjustment += mSplitSelectScrollOffsetPrimary;
        return scrollAdjustment;
    }

    public float getOffsetAdjustment(boolean fullscreenEnabled, boolean gridEnabled) {
        return getScrollAdjustment(fullscreenEnabled, gridEnabled);
    }

    /**
     * Adjust translation when this TaskView is about to be shown fullscreen.
     *
     * @param progress: 0 = no translation; 1 = translate according to TaskVIew translations.
     */
    public void setFullscreenProgress(float progress) {
        mFullscreenProgress = progress;
        applyPrimaryTranslation();
    }

    /**
     * Moves ClearAllButton between carousel and 2 row grid.
     *
     * @param gridProgress 0 = carousel; 1 = 2 row grid.
     */
    public void setGridProgress(float gridProgress) {
        mGridProgress = gridProgress;
        applyPrimaryTranslation();
    }

    private void applyPrimaryTranslation() {
        RecentsView recentsView = getRecentsView();
        if (recentsView == null) {
            return;
        }

        PagedOrientationHandler orientationHandler = recentsView.getPagedOrientationHandler();
        orientationHandler.getPrimaryViewTranslate().set(this,
                orientationHandler.getPrimaryValue(0f, getOriginalTranslationY())
                        + mNormalTranslationPrimary + getFullscreenTrans(
                        mFullscreenTranslationPrimary) + getGridTrans(mGridTranslationPrimary));
    }

    private void applySecondaryTranslation() {
        RecentsView recentsView = getRecentsView();
        if (recentsView == null) {
            return;
        }

        PagedOrientationHandler orientationHandler = recentsView.getPagedOrientationHandler();
        orientationHandler.getSecondaryViewTranslate().set(this,
                orientationHandler.getSecondaryValue(0f, getOriginalTranslationY()));
    }

    private float getFullscreenTrans(float endTranslation) {
        return mFullscreenProgress > 0 ? endTranslation : 0;
    }

    private float getGridTrans(float endTranslation) {
        return mGridProgress > 0 ? endTranslation : 0;
    }

    /**
     * Get the Y translation that is set in the original layout position, before scrolling.
     */
    private float getOriginalTranslationY() {
        DeviceProfile deviceProfile = mActivity.getDeviceProfile();
        return deviceProfile.isTablet
                ? deviceProfile.overviewRowSpacing
                : deviceProfile.overviewTaskThumbnailTopMarginPx / 2.0f;
    }
}
