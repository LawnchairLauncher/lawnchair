/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.allapps;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;

import static com.android.app.animation.Interpolators.DECELERATE_1_7;
import static com.android.app.animation.Interpolators.INSTANT;
import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.anim.AnimatorListeners.forSuccessCallback;

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.graphics.drawable.Drawable;
import android.util.FloatProperty;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.ItemInfo;

import java.util.List;

public class RecyclerViewAnimationController {

    private static final String LOG_TAG = "AnimationCtrl";

    /**
     * These values represent points on the [0, 1] animation progress spectrum. They are used to
     * animate items in the {@link SearchRecyclerView} and private space container in
     * {@link AllAppsRecyclerView}.
     */
    protected static final float TOP_CONTENT_FADE_PROGRESS_START = 0.133f;
    protected static final float CONTENT_FADE_PROGRESS_DURATION = 0.083f;
    protected static final float TOP_BACKGROUND_FADE_PROGRESS_START = 0.633f;
    protected static final float BACKGROUND_FADE_PROGRESS_DURATION = 0.15f;
    // Progress before next item starts fading.
    protected static final float CONTENT_STAGGER = 0.01f;

    protected static final FloatProperty<RecyclerViewAnimationController> PROGRESS =
            new FloatProperty<RecyclerViewAnimationController>("expansionProgress") {
                @Override
                public Float get(RecyclerViewAnimationController controller) {
                    return controller.getAnimationProgress();
                }

                @Override
                public void setValue(RecyclerViewAnimationController controller, float progress) {
                    controller.setAnimationProgress(progress);
                }
            };

    protected final ActivityAllAppsContainerView<?> mAllAppsContainerView;
    protected ObjectAnimator mAnimator = null;
    private float mAnimatorProgress = 1f;

    public RecyclerViewAnimationController(ActivityAllAppsContainerView<?> allAppsContainerView) {
        mAllAppsContainerView = allAppsContainerView;
    }

    /**
     * Updates the children views of the current recyclerView based on the current animation
     * progress.
     *
     * @return the total height of animating views (may exclude at most one row of app icons
     * depending on which recyclerView is being acted upon).
     */
    protected int onProgressUpdated(float expansionProgress) {
        int numItemsAnimated = 0;
        int totalHeight = 0;
        int appRowHeight = 0;
        boolean appRowComplete = false;
        Integer top = null;
        AllAppsRecyclerView allAppsRecyclerView = getRecyclerView();

        for (int i = 0; i < allAppsRecyclerView.getChildCount(); i++) {
            View currentView = allAppsRecyclerView.getChildAt(i);
            if (currentView == null) {
                continue;
            }
            if (top == null) {
                top = currentView.getTop();
            }
            int adapterPosition = allAppsRecyclerView.getChildAdapterPosition(currentView);
            List<BaseAllAppsAdapter.AdapterItem> allAppsAdapters = allAppsRecyclerView.getApps()
                    .getAdapterItems();
            if (adapterPosition < 0 || adapterPosition >= allAppsAdapters.size()) {
                continue;
            }
            BaseAllAppsAdapter.AdapterItem adapterItemAtPosition =
                    allAppsAdapters.get(adapterPosition);
            int spanIndex = getSpanIndex(allAppsRecyclerView, adapterPosition);
            appRowComplete |= appRowHeight > 0 && spanIndex == 0;

            float backgroundAlpha = 1f;
            boolean hasDecorationInfo = adapterItemAtPosition.getDecorationInfo() != null;
            boolean shouldAnimate = shouldAnimate(currentView, hasDecorationInfo, appRowComplete);

            if (shouldAnimate) {
                if (spanIndex > 0) {
                    // Animate this item with the previous item on the same row.
                    numItemsAnimated--;
                }
                // Adjust background (or decorator) alpha based on start progress and stagger.
                backgroundAlpha = getAdjustedBackgroundAlpha(numItemsAnimated);
            }

            Drawable background = currentView.getBackground();
            if (background != null && currentView instanceof ViewGroup currentViewGroup) {
                currentView.setAlpha(1f);
                // Apply content alpha to each child, since the view needs to be fully opaque for
                // the background to show properly.
                for (int j = 0; j < currentViewGroup.getChildCount(); j++) {
                    setViewAdjustedContentAlpha(currentViewGroup.getChildAt(j), numItemsAnimated,
                            shouldAnimate);
                }

                // Apply background alpha to the background drawable directly.
                background.setAlpha((int) (255 * backgroundAlpha));
            } else {
                // Adjust content alpha based on start progress and stagger.
                setViewAdjustedContentAlpha(currentView, numItemsAnimated, shouldAnimate);

                // Apply background alpha to decorator if possible.
                setAdjustedAdapterItemDecorationBackgroundAlpha(
                        allAppsRecyclerView.getApps().getAdapterItems().get(adapterPosition),
                        numItemsAnimated);

                // Apply background alpha to view's background (e.g. for Search Edu card).
                if (background != null) {
                    background.setAlpha((int) (255 * backgroundAlpha));
                }
            }

            float scaleY = 1;
            if (shouldAnimate) {
                scaleY = 1 - getAnimationProgress();
                // Update number of search results that has been animated.
                numItemsAnimated++;
            }
            int scaledHeight = (int) (currentView.getHeight() * scaleY);
            currentView.setScaleY(scaleY);

            // For rows with multiple elements, only count the height once and translate elements to
            // the same y position.
            int y = top + totalHeight;
            if (spanIndex > 0) {
                // Continuation of an existing row; move this item into the row.
                y -= scaledHeight;
            } else {
                // Start of a new row contributes to total height.
                totalHeight += scaledHeight;
                if (!shouldAnimate) {
                    appRowHeight = scaledHeight;
                }
            }
            currentView.setY(y);
        }
        return totalHeight - appRowHeight;
    }

    protected void animateToState(boolean expand, long duration, Runnable onEndRunnable) {
        float targetProgress = expand ? 0 : 1;
        if (mAnimator != null) {
            mAnimator.cancel();
        }
        mAnimator = ObjectAnimator.ofFloat(this, PROGRESS, targetProgress);

        TimeInterpolator timeInterpolator = getInterpolator();
        if (timeInterpolator == INSTANT) {
            duration = 0;
        }

        mAnimator.addListener(forEndCallback(() -> mAnimator = null));
        mAnimator.setDuration(duration).setInterpolator(timeInterpolator);
        mAnimator.addListener(forSuccessCallback(onEndRunnable));
        mAnimator.start();
        getRecyclerView().setChildAttachedConsumer(this::onChildAttached);
    }

    /** Called just before a child is attached to the RecyclerView. */
    private void onChildAttached(View child) {
        // Avoid allocating hardware layers for alpha changes.
        child.forceHasOverlappingRendering(false);
        child.setPivotY(0);
        if (getAnimationProgress() > 0 && getAnimationProgress() < 1) {
            // Before the child is rendered, apply the animation including it to avoid flicker.
            onProgressUpdated(getAnimationProgress());
        } else {
            // Apply default states without processing the full layout.
            child.setAlpha(1);
            child.setScaleY(1);
            child.setTranslationY(0);
            int adapterPosition = getRecyclerView().getChildAdapterPosition(child);
            List<BaseAllAppsAdapter.AdapterItem> allAppsAdapters =
                    getRecyclerView().getApps().getAdapterItems();
            if (adapterPosition >= 0 && adapterPosition < allAppsAdapters.size()) {
                allAppsAdapters.get(adapterPosition).setDecorationFillAlpha(255);
            }
            if (child instanceof ViewGroup childGroup) {
                for (int i = 0; i < childGroup.getChildCount(); i++) {
                    childGroup.getChildAt(i).setAlpha(1f);
                }
            }
            if (child.getBackground() != null) {
                child.getBackground().setAlpha(255);
            }
        }
    }

    /** @return the column that the view at this position is found (0 assumed if indeterminate). */
    protected int getSpanIndex(AllAppsRecyclerView appsRecyclerView, int adapterPosition) {
        if (adapterPosition == NO_POSITION) {
            Log.w(LOG_TAG, "Can't determine span index - child not found in adapter");
            return 0;
        }
        if (!(appsRecyclerView.getAdapter() instanceof AllAppsGridAdapter<?>)) {
            Log.e(LOG_TAG, "Search RV doesn't have an AllAppsGridAdapter?");
            // This case shouldn't happen, but for debug devices we will continue to create a more
            // visible crash.
            if (!Utilities.IS_DEBUG_DEVICE) {
                return 0;
            }
        }
        AllAppsGridAdapter<?> adapter = (AllAppsGridAdapter<?>) appsRecyclerView.getAdapter();
        return adapter.getSpanIndex(adapterPosition);
    }

    protected TimeInterpolator getInterpolator() {
        return DECELERATE_1_7;
    }

    protected AllAppsRecyclerView getRecyclerView() {
        return mAllAppsContainerView.mAH.get(ActivityAllAppsContainerView.AdapterHolder.MAIN)
                .mRecyclerView;
    }

    /** Returns true if a transition animation is currently in progress. */
    protected boolean isRunning() {
        return mAnimator != null;
    }

    /** Should only animate if the view is an app icon and if it has a decoration info. */
    protected boolean shouldAnimate(View view, boolean hasDecorationInfo,
            boolean firstAppRowComplete) {
        return isAppIcon(view) && hasDecorationInfo;
    }

    private float getAdjustedContentAlpha(int itemsAnimated) {
        float startContentFadeProgress = Math.max(0,
                TOP_CONTENT_FADE_PROGRESS_START - CONTENT_STAGGER * itemsAnimated);
        float endContentFadeProgress = Math.min(1,
                startContentFadeProgress + CONTENT_FADE_PROGRESS_DURATION);
        return 1 - clampToProgress(mAnimatorProgress,
                startContentFadeProgress, endContentFadeProgress);
    }

    private float getAdjustedBackgroundAlpha(int itemsAnimated) {
        float startBackgroundFadeProgress = Math.max(0,
                TOP_BACKGROUND_FADE_PROGRESS_START - CONTENT_STAGGER * itemsAnimated);
        float endBackgroundFadeProgress = Math.min(1,
                startBackgroundFadeProgress + BACKGROUND_FADE_PROGRESS_DURATION);
        return 1 - clampToProgress(mAnimatorProgress,
                startBackgroundFadeProgress, endBackgroundFadeProgress);
    }

    private void setViewAdjustedContentAlpha(View view, int numberOfItemsAnimated,
            boolean shouldAnimate) {
        view.setAlpha(shouldAnimate ? getAdjustedContentAlpha(numberOfItemsAnimated) : 1f);
    }

    private void setAdjustedAdapterItemDecorationBackgroundAlpha(
            BaseAllAppsAdapter.AdapterItem adapterItem, int numberOfItemsAnimated) {
        adapterItem.setDecorationFillAlpha((int)
                (255 * getAdjustedBackgroundAlpha(numberOfItemsAnimated)));
    }

    private float getAnimationProgress() {
        return mAnimatorProgress;
    }

    private void setAnimationProgress(float expansionProgress) {
        mAnimatorProgress = expansionProgress;
        onProgressUpdated(expansionProgress);
    }

    protected boolean isAppIcon(View item) {
        return item instanceof BubbleTextView && item.getTag() instanceof ItemInfo
                && ((ItemInfo) item.getTag()).itemType == ITEM_TYPE_APPLICATION;
    }
}
