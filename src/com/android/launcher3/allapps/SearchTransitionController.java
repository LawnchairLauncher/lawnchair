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

import static com.android.app.animation.Interpolators.DECELERATE_1_7;
import static com.android.app.animation.Interpolators.INSTANT;
import static com.android.app.animation.Interpolators.clampToProgress;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.anim.AnimatorListeners.forSuccessCallback;

import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.view.View;
import android.view.animation.Interpolator;

import com.android.launcher3.R;

/** Coordinates the transition between Search and A-Z in All Apps. */
public class SearchTransitionController extends RecyclerViewAnimationController {

    // Interpolator when the user taps the QSB while already in All Apps.
    private static final Interpolator INTERPOLATOR_WITHIN_ALL_APPS = DECELERATE_1_7;
    // Interpolator when the user taps the QSB from home screen, so transition to all apps is
    // happening simultaneously.
    private static final Interpolator INTERPOLATOR_TRANSITIONING_TO_ALL_APPS = INSTANT;

    private boolean mSkipNextAnimationWithinAllApps;

    public SearchTransitionController(ActivityAllAppsContainerView<?> allAppsContainerView) {
        super(allAppsContainerView);
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
    @Override
    protected void animateToState(boolean goingToSearch, long duration, Runnable onEndRunnable) {
        super.animateToState(goingToSearch, duration, onEndRunnable);
        if (!goingToSearch) {
            mAnimator.addListener(forSuccessCallback(() -> {
                mAllAppsContainerView.getFloatingHeaderView().setFloatingRowsCollapsed(false);
                mAllAppsContainerView.getFloatingHeaderView().reset(false /* animate */);
                mAllAppsContainerView.getAppsRecyclerViewContainer().setTranslationY(0);
            }));
        }
        mAllAppsContainerView.getFloatingHeaderView().setFloatingRowsCollapsed(true);
        mAllAppsContainerView.getFloatingHeaderView().setVisibility(VISIBLE);
        mAllAppsContainerView.getFloatingHeaderView().maybeSetTabVisibility(VISIBLE);
        mAllAppsContainerView.getAppsRecyclerViewContainer().setVisibility(VISIBLE);
        getRecyclerView().setVisibility(VISIBLE);
    }

    @Override
    protected SearchRecyclerView getRecyclerView() {
        return mAllAppsContainerView.getSearchRecyclerView();
    }

    @Override
    protected int onProgressUpdated(float searchToAzProgress) {
        int searchHeight = super.onProgressUpdated(searchToAzProgress);

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
        return searchHeight;
    }

    /**
     * Should only animate if the view is not an app icon or if the app row is complete.
     */
    @Override
    protected boolean shouldAnimate(View view, boolean hasDecorationInfo, boolean appRowComplete) {
        return !isAppIcon(view) || appRowComplete;
    }

    @Override
    protected TimeInterpolator getInterpolator() {
        TimeInterpolator timeInterpolator =
                mAllAppsContainerView.isInAllApps()
                        ? INTERPOLATOR_WITHIN_ALL_APPS : INTERPOLATOR_TRANSITIONING_TO_ALL_APPS;
        if (mSkipNextAnimationWithinAllApps) {
            timeInterpolator = INSTANT;
            mSkipNextAnimationWithinAllApps = false;
        }
        return timeInterpolator;
    }

    /**
     * This should only be called from {@code LauncherSearchSessionManager} when app restarts due to
     * theme changes.
     */
    public void setSkipAnimationWithinAllApps(boolean skip) {
        mSkipNextAnimationWithinAllApps = skip;
    }
}
