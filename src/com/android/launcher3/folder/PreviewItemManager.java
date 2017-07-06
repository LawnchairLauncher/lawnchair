/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.TextView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;

import java.util.ArrayList;
import java.util.List;

import static com.android.launcher3.folder.FolderIcon.DROP_IN_ANIMATION_DURATION;

/**
 * Manages the drawing and animations of {@link PreviewItemDrawingParams} for a {@link FolderIcon}.
 */
public class PreviewItemManager {

    private FolderIcon mIcon;

    // These variables are all associated with the drawing of the preview; they are stored
    // as member variables for shared usage and to avoid computation on each frame
    private float mIntrinsicIconSize = -1;
    private int mTotalWidth = -1;
    private int mPrevTopPadding = -1;
    private Drawable mReferenceDrawable = null;

    // These hold the first page preview items
    private ArrayList<PreviewItemDrawingParams> mFirstPageParams = new ArrayList<>();
    // These hold the current page preview items. It is empty if the current page is the first page.
    private ArrayList<PreviewItemDrawingParams> mCurrentPageParams = new ArrayList<>();

    private float mCurrentPageItemsTransX = 0;
    private boolean mShouldSlideInFirstPage;

    static final int INITIAL_ITEM_ANIMATION_DURATION = 350;
    private static final int FINAL_ITEM_ANIMATION_DURATION = 200;

    private static final int SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION_DELAY = 200;
    private static final int SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION = 300;
    private static final int ITEM_SLIDE_IN_OUT_DISTANCE_PX = 200;

    public PreviewItemManager(FolderIcon icon) {
        mIcon = icon;
    }

    /**
     * @param reverse If true, animates the final item in the preview to be full size. If false,
     *                animates the first item to its position in the preview.
     */
    public FolderPreviewItemAnim createFirstItemAnimation(final boolean reverse,
            final Runnable onCompleteRunnable) {
        return reverse
                ? new FolderPreviewItemAnim(this, mFirstPageParams.get(0), 0, 2, -1, -1,
                        FINAL_ITEM_ANIMATION_DURATION, onCompleteRunnable)
                : new FolderPreviewItemAnim(this, mFirstPageParams.get(0), -1, -1, 0, 2,
                        INITIAL_ITEM_ANIMATION_DURATION, onCompleteRunnable);
    }

    Drawable prepareCreateAnimation(final View destView) {
        Drawable animateDrawable = ((TextView) destView).getCompoundDrawables()[1];
        computePreviewDrawingParams(animateDrawable.getIntrinsicWidth(),
                destView.getMeasuredWidth());
        mReferenceDrawable = animateDrawable;
        return animateDrawable;
    }

    private void computePreviewDrawingParams(Drawable d) {
        computePreviewDrawingParams(d.getIntrinsicWidth(), mIcon.getMeasuredWidth());
    }

    private void computePreviewDrawingParams(int drawableSize, int totalSize) {
        if (mIntrinsicIconSize != drawableSize || mTotalWidth != totalSize ||
                mPrevTopPadding != mIcon.getPaddingTop()) {
            mIntrinsicIconSize = drawableSize;
            mTotalWidth = totalSize;
            mPrevTopPadding = mIcon.getPaddingTop();

            mIcon.mBackground.setup(mIcon.mLauncher, mIcon, mTotalWidth, mIcon.getPaddingTop());
            mIcon.mPreviewLayoutRule.init(mIcon.mBackground.previewSize, mIntrinsicIconSize,
                    Utilities.isRtl(mIcon.getResources()));

            updateItemDrawingParams(false);
        }
    }

    PreviewItemDrawingParams computePreviewItemDrawingParams(int index, int curNumItems,
            PreviewItemDrawingParams params) {
        // We use an index of -1 to represent an icon on the workspace for the destroy and
        // create animations
        if (index == -1) {
            return getFinalIconParams(params);
        }
        return mIcon.mPreviewLayoutRule.computePreviewItemDrawingParams(index, curNumItems, params);
    }

    private PreviewItemDrawingParams getFinalIconParams(PreviewItemDrawingParams params) {
        float iconSize = mIcon.mLauncher.getDeviceProfile().iconSizePx;

        final float scale = iconSize / mReferenceDrawable.getIntrinsicWidth();
        final float trans = (mIcon.mBackground.previewSize - iconSize) / 2;

        params.update(trans, trans, scale);
        return params;
    }

    public void drawParams(Canvas canvas, ArrayList<PreviewItemDrawingParams> params,
            float transX) {
        canvas.translate(transX, 0);
        // The first item should be drawn last (ie. on top of later items)
        for (int i = params.size() - 1; i >= 0; i--) {
            PreviewItemDrawingParams p = params.get(i);
            if (!p.hidden) {
                drawPreviewItem(canvas, p);
            }
        }
        canvas.translate(-transX, 0);
    }

    public void draw(Canvas canvas) {
        computePreviewDrawingParams(mReferenceDrawable);

        float firstPageItemsTransX = 0;
        if (mShouldSlideInFirstPage) {
            drawParams(canvas, mCurrentPageParams, mCurrentPageItemsTransX);

            firstPageItemsTransX = -ITEM_SLIDE_IN_OUT_DISTANCE_PX + mCurrentPageItemsTransX;
        }

        drawParams(canvas, mFirstPageParams, firstPageItemsTransX);
    }

    public void onParamsChanged() {
        mIcon.invalidate();
    }

    private void drawPreviewItem(Canvas canvas, PreviewItemDrawingParams params) {
        canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.translate(params.transX, params.transY);
        canvas.scale(params.scale, params.scale);
        Drawable d = params.drawable;

        if (d != null) {
            Rect bounds = d.getBounds();
            canvas.save();
            canvas.translate(-bounds.left, -bounds.top);
            canvas.scale(mIntrinsicIconSize / bounds.width(), mIntrinsicIconSize / bounds.height());
            d.draw(canvas);
            canvas.restore();
        }
        canvas.restore();
    }

    public void hidePreviewItem(int index, boolean hidden) {
        PreviewItemDrawingParams params = index < mFirstPageParams.size() ?
                mFirstPageParams.get(index) : null;
        if (params != null) {
            params.hidden = hidden;
        }
    }

    void buildParamsForPage(int page, ArrayList<PreviewItemDrawingParams> params, boolean animate) {
        List<BubbleTextView> items = mIcon.getPreviewItemsOnPage(page);
        int prevNumItems = params.size();

        // We adjust the size of the list to match the number of items in the preview.
        while (items.size() < params.size()) {
            params.remove(params.size() - 1);
        }
        while (items.size() > params.size()) {
            params.add(new PreviewItemDrawingParams(0, 0, 0, 0));
        }

        int numItemsInFirstPagePreview = page == 0 ? items.size() : FolderIcon.NUM_ITEMS_IN_PREVIEW;
        for (int i = 0; i < params.size(); i++) {
            PreviewItemDrawingParams p = params.get(i);
            p.drawable = items.get(i).getCompoundDrawables()[1];

            if (p.drawable != null && !mIcon.mFolder.isOpen()) {
                // Set the callback to FolderIcon as it is responsible to drawing the icon. The
                // callback will be released when the folder is opened.
                p.drawable.setCallback(mIcon);
            }

            if (!animate || FeatureFlags.LAUNCHER3_LEGACY_FOLDER_ICON) {
                computePreviewItemDrawingParams(i, numItemsInFirstPagePreview, p);
                if (mReferenceDrawable == null) {
                    mReferenceDrawable = p.drawable;
                }
            } else {
                FolderPreviewItemAnim anim = new FolderPreviewItemAnim(this, p, i, prevNumItems, i,
                        numItemsInFirstPagePreview, DROP_IN_ANIMATION_DURATION, null);

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

    void onFolderClose(int currentPage) {
        // If we are not closing on the first page, we animate the current page preview items
        // out, and animate the first page preview items in.
        mShouldSlideInFirstPage = currentPage != 0;
        if (mShouldSlideInFirstPage) {
            mCurrentPageItemsTransX = 0;
            buildParamsForPage(currentPage, mCurrentPageParams, false);
            onParamsChanged();

            ValueAnimator slideAnimator = ValueAnimator.ofFloat(0, ITEM_SLIDE_IN_OUT_DISTANCE_PX);
            slideAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    mCurrentPageItemsTransX = (float) valueAnimator.getAnimatedValue();
                    onParamsChanged();
                }
            });
            slideAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mCurrentPageParams.clear();
                }
            });
            slideAnimator.setStartDelay(SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION_DELAY);
            slideAnimator.setDuration(SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION);
            slideAnimator.start();
        }
    }

    void updateItemDrawingParams(boolean animate) {
        buildParamsForPage(0, mFirstPageParams, animate);
    }

    boolean verifyDrawable(@NonNull Drawable who) {
        for (int i = 0; i < mFirstPageParams.size(); i++) {
            if (mFirstPageParams.get(i).drawable == who) {
                return true;
            }
        }
        return false;
    }

    float getIntrinsicIconSize() {
        return mIntrinsicIconSize;
    }
}
