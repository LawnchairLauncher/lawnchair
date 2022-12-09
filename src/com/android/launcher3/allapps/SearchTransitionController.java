/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.view.View.VISIBLE;

import static androidx.recyclerview.widget.RecyclerView.NO_POSITION;

import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.anim.AnimatorListeners.forSuccessCallback;
import static com.android.launcher3.anim.Interpolators.DEACCEL_1_7;
import static com.android.launcher3.anim.Interpolators.INSTANT;
import static com.android.launcher3.anim.Interpolators.clampToProgress;

import android.animation.ObjectAnimator;
import android.graphics.drawable.Drawable;
import android.util.FloatProperty;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.model.data.ItemInfo;

/** Coordinates the transition between Search and A-Z in All Apps. */
public class SearchTransitionController {

    private static final String LOG_TAG = "SearchTransitionCtrl";

    // Interpolator when the user taps the QSB while already in All Apps.
    private static final Interpolator INTERPOLATOR_WITHIN_ALL_APPS = DEACCEL_1_7;
    // Interpolator when the user taps the QSB from home screen, so transition to all apps is
    // happening simultaneously.
    private static final Interpolator INTERPOLATOR_TRANSITIONING_TO_ALL_APPS = INSTANT;

    /**
     * These values represent points on the [0, 1] animation progress spectrum. They are used to
     * animate items in the {@link SearchRecyclerView}.
     */
    private static final float TOP_CONTENT_FADE_PROGRESS_START = 0.133f;
    private static final float CONTENT_FADE_PROGRESS_DURATION = 0.083f;
    private static final float TOP_BACKGROUND_FADE_PROGRESS_START = 0.633f;
    private static final float BACKGROUND_FADE_PROGRESS_DURATION = 0.15f;
    private static final float CONTENT_STAGGER = 0.01f;  // Progress before next item starts fading.

    private static final FloatProperty<SearchTransitionController> SEARCH_TO_AZ_PROGRESS =
            new FloatProperty<SearchTransitionController>("searchToAzProgress") {
                @Override
                public Float get(SearchTransitionController controller) {
                    return controller.getSearchToAzProgress();
                }

                @Override
                public void setValue(SearchTransitionController controller, float progress) {
                    controller.setSearchToAzProgress(progress);
                }
            };

    private final ActivityAllAppsContainerView<?> mAllAppsContainerView;

    private ObjectAnimator mSearchToAzAnimator = null;
    private float mSearchToAzProgress = 1f;

    public SearchTransitionController(ActivityAllAppsContainerView<?> allAppsContainerView) {
        mAllAppsContainerView = allAppsContainerView;
    }

    /** Returns true if a transition animation is currently in progress. */
    public boolean isRunning() {
        return mSearchToAzAnimator != null;
    }

    /**
     * Starts the transition to or from search state. If a transition is already in progress, the
     * animation will start from that point with the new duration, and the previous onEndRunnable
     * will not be called.
     *
     * @param goingToSearch true if will be showing search results, otherwise will be showing a-z
     * @param duration time in ms for the animation to run
     * @param onEndRunnable will be called when the animation finishes, unless another animation is
     *                      scheduled in the meantime
     */
    public void animateToSearchState(boolean goingToSearch, long duration, Runnable onEndRunnable) {
        float targetProgress = goingToSearch ? 0 : 1;

        if (mSearchToAzAnimator != null) {
            mSearchToAzAnimator.cancel();
        }

        mSearchToAzAnimator = ObjectAnimator.ofFloat(this, SEARCH_TO_AZ_PROGRESS, targetProgress);
        boolean inAllApps = mAllAppsContainerView.isInAllApps();
        if (!inAllApps) {
            duration = 0;  // Don't want to animate when coming from QSB.
        }
        mSearchToAzAnimator.setDuration(duration).setInterpolator(
                inAllApps ? INTERPOLATOR_WITHIN_ALL_APPS : INTERPOLATOR_TRANSITIONING_TO_ALL_APPS);
        mSearchToAzAnimator.addListener(forEndCallback(() -> mSearchToAzAnimator = null));
        if (!goingToSearch) {
            mSearchToAzAnimator.addListener(forSuccessCallback(() -> {
                mAllAppsContainerView.getFloatingHeaderView().setFloatingRowsCollapsed(false);
                mAllAppsContainerView.getFloatingHeaderView().reset(false /* animate */);
                mAllAppsContainerView.getAppsRecyclerViewContainer().setTranslationY(0);
            }));
        }
        mSearchToAzAnimator.addListener(forSuccessCallback(onEndRunnable));

        mAllAppsContainerView.getFloatingHeaderView().setFloatingRowsCollapsed(true);
        mAllAppsContainerView.getFloatingHeaderView().setVisibility(VISIBLE);
        mAllAppsContainerView.getAppsRecyclerViewContainer().setVisibility(VISIBLE);
        getSearchRecyclerView().setVisibility(VISIBLE);
        getSearchRecyclerView().setChildAttachedConsumer(this::onSearchChildAttached);
        mSearchToAzAnimator.start();
    }

    private SearchRecyclerView getSearchRecyclerView() {
        return mAllAppsContainerView.getSearchRecyclerView();
    }

    private void setSearchToAzProgress(float searchToAzProgress) {
        mSearchToAzProgress = searchToAzProgress;
        int searchHeight = updateSearchRecyclerViewProgress();

        FloatingHeaderView headerView = mAllAppsContainerView.getFloatingHeaderView();

        // Add predictions + app divider height to account for predicted apps which will now be in
        // the Search RV instead of the floating header view. Note `getFloatingRowsHeight` returns 0
        // when predictions are not shown.
        int appsTranslationY = searchHeight + headerView.getFloatingRowsHeight();

        if (headerView.usingTabs()) {
            // Move tabs below the search results, and fade them out in 20% of the animation.
            headerView.setTranslationY(searchHeight);
            headerView.setAlpha(clampToProgress(searchToAzProgress, 0.8f, 1f));

            // Account for the additional padding added for the tabs.
            appsTranslationY +=
                    headerView.getTabsAdditionalPaddingBottom()
                            + mAllAppsContainerView.getResources().getDimensionPixelOffset(
                                    R.dimen.all_apps_tabs_margin_top)
                            - headerView.getPaddingTop();
        }

        View appsContainer = mAllAppsContainerView.getAppsRecyclerViewContainer();
        appsContainer.setTranslationY(appsTranslationY);
        // Fade apps out with tabs (in 20% of the total animation).
        appsContainer.setAlpha(clampToProgress(searchToAzProgress, 0.8f, 1f));
    }

    /**
     * Updates the children views of SearchRecyclerView based on the current animation progress.
     *
     * @return the total height of animating views (excluding at most one row of app icons).
     */
    private int updateSearchRecyclerViewProgress() {
        int numSearchResultsAnimated = 0;
        int totalHeight = 0;
        int appRowHeight = 0;
        boolean appRowComplete = false;
        Integer top = null;
        SearchRecyclerView searchRecyclerView = getSearchRecyclerView();

        for (int i = 0; i < searchRecyclerView.getChildCount(); i++) {
            View searchResultView = searchRecyclerView.getChildAt(i);
            if (searchResultView == null) {
                continue;
            }

            if (top == null) {
                top = searchResultView.getTop();
            }

            int adapterPosition = searchRecyclerView.getChildAdapterPosition(searchResultView);
            int spanIndex = getSpanIndex(searchRecyclerView, adapterPosition);
            appRowComplete |= appRowHeight > 0 && spanIndex == 0;
            // We don't animate the first (currently only) app row we see, as that is assumed to be
            // predicted/prefix-matched apps.
            boolean shouldAnimate = !isAppIcon(searchResultView) || appRowComplete;

            float contentAlpha = 1f;
            float backgroundAlpha = 1f;
            if (shouldAnimate) {
                if (spanIndex > 0) {
                    // Animate this item with the previous item on the same row.
                    numSearchResultsAnimated--;
                }

                // Adjust content alpha based on start progress and stagger.
                float startContentFadeProgress = Math.max(0,
                        TOP_CONTENT_FADE_PROGRESS_START
                                - CONTENT_STAGGER * numSearchResultsAnimated);
                float endContentFadeProgress = Math.min(1,
                        startContentFadeProgress + CONTENT_FADE_PROGRESS_DURATION);
                contentAlpha = 1 - clampToProgress(mSearchToAzProgress,
                        startContentFadeProgress, endContentFadeProgress);

                // Adjust background (or decorator) alpha based on start progress and stagger.
                float startBackgroundFadeProgress = Math.max(0,
                        TOP_BACKGROUND_FADE_PROGRESS_START
                                - CONTENT_STAGGER * numSearchResultsAnimated);
                float endBackgroundFadeProgress = Math.min(1,
                        startBackgroundFadeProgress + BACKGROUND_FADE_PROGRESS_DURATION);
                backgroundAlpha = 1 - clampToProgress(mSearchToAzProgress,
                        startBackgroundFadeProgress, endBackgroundFadeProgress);

                numSearchResultsAnimated++;
            }

            Drawable background = searchResultView.getBackground();
            if (background != null
                    && searchResultView instanceof ViewGroup
                    && FeatureFlags.ENABLE_SEARCH_RESULT_BACKGROUND_DRAWABLES.get()) {
                searchResultView.setAlpha(1f);

                // Apply content alpha to each child, since the view needs to be fully opaque for
                // the background to show properly.
                ViewGroup searchResultViewGroup = (ViewGroup) searchResultView;
                for (int j = 0; j < searchResultViewGroup.getChildCount(); j++) {
                    searchResultViewGroup.getChildAt(j).setAlpha(contentAlpha);
                }

                // Apply background alpha to the background drawable directly.
                background.setAlpha((int) (255 * backgroundAlpha));
            } else {
                searchResultView.setAlpha(contentAlpha);

                // Apply background alpha to decorator if possible.
                if (adapterPosition != NO_POSITION) {
                    searchRecyclerView.getApps().getAdapterItems().get(adapterPosition)
                            .setDecorationFillAlpha((int) (255 * backgroundAlpha));
                }

                // Apply background alpha to view's background (e.g. for Search Edu card).
                if (background != null) {
                    background.setAlpha((int) (255 * backgroundAlpha));
                }
            }

            float scaleY = 1;
            if (shouldAnimate) {
                scaleY = 1 - mSearchToAzProgress;
            }
            int scaledHeight = (int) (searchResultView.getHeight() * scaleY);
            searchResultView.setScaleY(scaleY);

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
            searchResultView.setY(y);
        }

        return totalHeight - appRowHeight;
    }

    /** @return the column that the view at this position is found (0 assumed if indeterminate). */
    private int getSpanIndex(SearchRecyclerView searchRecyclerView, int adapterPosition) {
        if (adapterPosition == NO_POSITION) {
            Log.w(LOG_TAG, "Can't determine span index - child not found in adapter");
            return 0;
        }
        if (!(searchRecyclerView.getAdapter() instanceof AllAppsGridAdapter<?>)) {
            Log.e(LOG_TAG, "Search RV doesn't have an AllAppsGridAdapter?");
            // This case shouldn't happen, but for debug devices we will continue to create a more
            // visible crash.
            if (!Utilities.IS_DEBUG_DEVICE) {
                return 0;
            }
        }
        AllAppsGridAdapter<?> adapter = (AllAppsGridAdapter<?>) searchRecyclerView.getAdapter();
        return adapter.getSpanIndex(adapterPosition);
    }

    private boolean isAppIcon(View item) {
        return item instanceof BubbleTextView && item.getTag() instanceof ItemInfo
                && ((ItemInfo) item.getTag()).itemType == ITEM_TYPE_APPLICATION;
    }

    /** Called just before a child is attached to the SearchRecyclerView. */
    private void onSearchChildAttached(View child) {
        // Avoid allocating hardware layers for alpha changes.
        child.forceHasOverlappingRendering(false);
        child.setPivotY(0);
        if (mSearchToAzProgress > 0) {
            // Before the child is rendered, apply the animation including it to avoid flicker.
            updateSearchRecyclerViewProgress();
        } else {
            // Apply default states without processing the full layout.
            child.setAlpha(1);
            child.setScaleY(1);
            child.setTranslationY(0);
            int adapterPosition = getSearchRecyclerView().getChildAdapterPosition(child);
            if (adapterPosition != NO_POSITION) {
                getSearchRecyclerView().getApps().getAdapterItems().get(adapterPosition)
                        .setDecorationFillAlpha(255);
            }
            if (child instanceof ViewGroup
                    && FeatureFlags.ENABLE_SEARCH_RESULT_BACKGROUND_DRAWABLES.get()) {
                ViewGroup childGroup = (ViewGroup) child;
                for (int i = 0; i < childGroup.getChildCount(); i++) {
                    childGroup.getChildAt(i).setAlpha(1f);
                }
            }
            if (child.getBackground() != null) {
                child.getBackground().setAlpha(255);
            }
        }
    }

    private float getSearchToAzProgress() {
        return mSearchToAzProgress;
    }
}
