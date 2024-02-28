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

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.MAIN;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_HEADER;
import static com.android.launcher3.allapps.PrivateProfileManager.STATE_DISABLED;
import static com.android.launcher3.allapps.PrivateProfileManager.STATE_ENABLED;
import static com.android.launcher3.allapps.PrivateProfileManager.STATE_TRANSITION;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_LOCK_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_SETTINGS_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_UNLOCK_TAP;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.android.app.animation.Interpolators;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.R;
import com.android.launcher3.allapps.UserProfileManager.UserProfileState;
import com.android.launcher3.anim.AnimatedPropertySetter;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.RecyclerViewFastScroller;

import java.util.List;

/**
 * Controller which returns views to be added to Private Space Header based upon
 * {@link UserProfileState}
 */
public class PrivateSpaceHeaderViewController {
    private static final int EXPAND_COLLAPSE_DURATION = 800;
    private static final int SETTINGS_OPACITY_DURATION = 160;
    private final ActivityAllAppsContainerView mAllApps;
    private final PrivateProfileManager mPrivateProfileManager;

    public PrivateSpaceHeaderViewController(ActivityAllAppsContainerView allApps,
            PrivateProfileManager privateProfileManager) {
        this.mAllApps = allApps;
        this.mPrivateProfileManager = privateProfileManager;
    }

    /** Add Private Space Header view elements based upon {@link UserProfileState} */
    public void addPrivateSpaceHeaderViewElements(RelativeLayout parent) {
        // Set the transition duration for the settings and lock button to animate.
        ViewGroup settingsAndLockGroup = parent.findViewById(R.id.settingsAndLockGroup);
        LayoutTransition settingsAndLockTransition = settingsAndLockGroup.getLayoutTransition();
        settingsAndLockTransition.enableTransitionType(LayoutTransition.CHANGING);
        settingsAndLockTransition.setDuration(EXPAND_COLLAPSE_DURATION);

        //Add quietMode image and action for lock/unlock button
        ViewGroup lockButton =
                parent.findViewById(R.id.ps_lock_unlock_button);
        assert lockButton != null;
        addLockButton(parent, lockButton);

        //Trigger lock/unlock action from header.
        addHeaderOnClickListener(parent);

        //Add image and action for private space settings button
        ImageButton settingsButton = parent.findViewById(R.id.ps_settings_button);
        assert settingsButton != null;
        addPrivateSpaceSettingsButton(settingsButton);

        //Add image for private space transitioning view
        ImageView transitionView = parent.findViewById(R.id.ps_transition_image);
        assert transitionView != null;
        addTransitionImage(transitionView);
    }

    /**
     *  Adds the quietModeButton and attach onClickListener for the header to animate different
     *  states when clicked.
     */
    private void addLockButton(ViewGroup psHeader, ViewGroup lockButton) {
        TextView lockText = lockButton.findViewById(R.id.lock_text);
        switch (mPrivateProfileManager.getCurrentState()) {
            case STATE_ENABLED -> {
                lockText.setVisibility(VISIBLE);
                lockButton.setVisibility(VISIBLE);
                lockButton.setOnClickListener(view -> lockAction(psHeader));
            }
            case STATE_DISABLED -> {
                lockText.setVisibility(GONE);
                lockButton.setVisibility(VISIBLE);
                lockButton.setOnClickListener(view -> unlockAction(psHeader));
            }
            default -> lockButton.setVisibility(GONE);
        }
    }

    private void addHeaderOnClickListener(RelativeLayout header) {
        if (mPrivateProfileManager.getCurrentState() == STATE_DISABLED) {
            header.setOnClickListener(view -> unlockAction(header));
        } else {
            header.setOnClickListener(null);
        }
    }

    private void unlockAction(ViewGroup psHeader) {
        mPrivateProfileManager.logEvents(LAUNCHER_PRIVATE_SPACE_UNLOCK_TAP);
        mPrivateProfileManager.unlockPrivateProfile((() -> onPrivateProfileUnlocked(psHeader)));
    }

    private void lockAction(ViewGroup psHeader) {
        mPrivateProfileManager.logEvents(LAUNCHER_PRIVATE_SPACE_LOCK_TAP);
        if (Flags.enablePrivateSpace() && Flags.privateSpaceAnimation()) {
            updatePrivateStateAnimator(false, psHeader);
        } else {
            mPrivateProfileManager.lockPrivateProfile();
        }
    }

    private void addPrivateSpaceSettingsButton(ImageButton settingsButton) {
        if (mPrivateProfileManager.getCurrentState() == STATE_ENABLED
                && mPrivateProfileManager.isPrivateSpaceSettingsAvailable()) {
            settingsButton.setVisibility(VISIBLE);
            settingsButton.setAlpha(1f);
            settingsButton.setOnClickListener(
                    view -> {
                        mPrivateProfileManager.logEvents(LAUNCHER_PRIVATE_SPACE_SETTINGS_TAP);
                        mPrivateProfileManager.openPrivateSpaceSettings();
                    });
        } else {
            settingsButton.setVisibility(GONE);
        }
    }

    private void addTransitionImage(ImageView transitionImage) {
        if (mPrivateProfileManager.getCurrentState() == STATE_TRANSITION) {
            transitionImage.setVisibility(VISIBLE);
        } else {
            transitionImage.setVisibility(GONE);
        }
    }

    private void onPrivateProfileUnlocked(ViewGroup header) {
        // If we are on main adapter view, we apply the PS Container expansion animation and
        // then scroll down to load the entire container, making animation visible.
        ActivityAllAppsContainerView<?>.AdapterHolder mainAdapterHolder =
                (ActivityAllAppsContainerView<?>.AdapterHolder) mAllApps.mAH.get(MAIN);
        if (Flags.enablePrivateSpace() && Flags.privateSpaceAnimation()
                && mAllApps.getActiveRecyclerView() == mainAdapterHolder.mRecyclerView) {
            // Animate the text and settings icon.
            updatePrivateStateAnimator(true, header);
            DeviceProfile deviceProfile =
                    ActivityContext.lookupContext(mAllApps.getContext()).getDeviceProfile();
            AllAppsRecyclerView allAppsRecyclerView = mAllApps.getActiveRecyclerView();
            scrollForViewToBeVisibleInContainer(allAppsRecyclerView,
                    allAppsRecyclerView.getApps().getAdapterItems(),
                    header.getHeight(), deviceProfile.allAppsCellHeightPx);
        }
    }

    /** Finds the private space header to scroll to and set the private space icons to GONE. */
    private void collapse() {
        AllAppsRecyclerView allAppsRecyclerView = mAllApps.getActiveRecyclerView();
        List<BaseAllAppsAdapter.AdapterItem> appListAdapterItems =
                allAppsRecyclerView.getApps().getAdapterItems();
        for (int i = appListAdapterItems.size() - 1; i > 0; i--) {
            BaseAllAppsAdapter.AdapterItem currentItem = appListAdapterItems.get(i);
            // Scroll to the private space header.
            if (currentItem.viewType == VIEW_TYPE_PRIVATE_SPACE_HEADER) {
                // Note: SmoothScroller is meant to be used once.
                RecyclerView.SmoothScroller smoothScroller =
                        new LinearSmoothScroller(mAllApps.getContext()) {
                            @Override protected int getVerticalSnapPreference() {
                                return LinearSmoothScroller.SNAP_TO_END;
                            }
                        };
                smoothScroller.setTargetPosition(i);
                RecyclerView.LayoutManager layoutManager = allAppsRecyclerView.getLayoutManager();
                if (layoutManager != null) {
                    layoutManager.startSmoothScroll(smoothScroller);
                }
                break;
            }
            // Make the private space apps gone to "collapse".
            if (currentItem.decorationInfo != null) {
                RecyclerView.ViewHolder viewHolder =
                        allAppsRecyclerView.findViewHolderForAdapterPosition(i);
                if (viewHolder != null) {
                    viewHolder.itemView.setVisibility(GONE);
                }
            }
        }
    }

    /**
     * Upon expanding, only scroll to the item position in the adapter that allows the header to be
     * visible.
     */
    @VisibleForTesting
    public int scrollForViewToBeVisibleInContainer(
            AllAppsRecyclerView allAppsRecyclerView,
            List<BaseAllAppsAdapter.AdapterItem> appListAdapterItems,
            int psHeaderHeight,
            int allAppsCellHeight) {
        int rowToExpandToWithRespectToHeader = -1;
        int itemToScrollTo = -1;
        // Looks for the item in the app list to scroll to so that the header is visible.
        for (int i = 0; i < appListAdapterItems.size(); i++) {
            BaseAllAppsAdapter.AdapterItem currentItem = appListAdapterItems.get(i);
            if (currentItem.viewType == VIEW_TYPE_PRIVATE_SPACE_HEADER) {
                itemToScrollTo = i;
                continue;
            }
            if (itemToScrollTo != -1) {
                if (rowToExpandToWithRespectToHeader == -1) {
                    rowToExpandToWithRespectToHeader = currentItem.rowIndex;
                }
                int rowToScrollTo =
                        (int) Math.floor((double) (mAllApps.getHeight() - psHeaderHeight
                                - mAllApps.getHeaderProtectionHeight()) / allAppsCellHeight);
                int currentRowDistance = currentItem.rowIndex - rowToExpandToWithRespectToHeader;
                // rowToScrollTo - 1 since the item to scroll to is 0 indexed.
                if (currentRowDistance == rowToScrollTo - 1) {
                    itemToScrollTo = i;
                    break;
                }
            }
        }
        if (itemToScrollTo != -1) {
            // Note: SmoothScroller is meant to be used once.
            RecyclerView.SmoothScroller smoothScroller =
                    new LinearSmoothScroller(mAllApps.getContext()) {
                        @Override protected int getVerticalSnapPreference() {
                            return LinearSmoothScroller.SNAP_TO_ANY;
                        }
                    };
            smoothScroller.setTargetPosition(itemToScrollTo);
            RecyclerView.LayoutManager layoutManager = allAppsRecyclerView.getLayoutManager();
            if (layoutManager != null) {
                layoutManager.startSmoothScroll(smoothScroller);
            }
        }
        return itemToScrollTo;
    }

    PrivateProfileManager getPrivateProfileManager() {
        return mPrivateProfileManager;
    }

    /**
     * Scrolls up to the private space header and animates the collapsing of the text.
     */
    private ValueAnimator animateCollapseAnimation(ViewGroup lockButton) {
        float from = 1;
        float to = 0;
        RecyclerViewFastScroller scrollBar = mAllApps.getActiveRecyclerView().getScrollbar();
        ValueAnimator collapseAnim = ValueAnimator.ofFloat(from, to);
        collapseAnim.setDuration(EXPAND_COLLAPSE_DURATION);
        collapseAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                if (scrollBar != null) {
                    scrollBar.setVisibility(INVISIBLE);
                }
                // scroll up
                collapse();
                // Animate the collapsing of the text.
                lockButton.findViewById(R.id.lock_text).setVisibility(GONE);
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (scrollBar != null) {
                    scrollBar.setThumbOffsetY(-1);
                    scrollBar.setVisibility(VISIBLE);
                }
                mPrivateProfileManager.lockPrivateProfile();
            }
        });
        return collapseAnim;
    }

    /**
     * Using PropertySetter{@link PropertySetter}, we can update the view's attributes within an
     * animation. At the moment, collapsing, setting alpha changes, and animating the text is done
     * here.
     */
    private void updatePrivateStateAnimator(boolean expand, ViewGroup psHeader) {
        PropertySetter setter = new AnimatedPropertySetter();
        ViewGroup lockButton = psHeader.findViewById(R.id.ps_lock_unlock_button);
        ImageButton settingsButton = psHeader.findViewById(R.id.ps_settings_button);
        updateSettingsGearAlpha(settingsButton, expand, setter);
        AnimatorSet animatorSet = setter.buildAnim();
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                // Animate the collapsing of the text at the same time while updating lock button.
                lockButton.findViewById(R.id.lock_text).setVisibility(expand ? VISIBLE : GONE);
            }
        });
        // Play the collapsing together of the stateAnimator to avoid being unable to scroll to the
        // header. Otherwise the smooth scrolling will scroll higher when played with the state
        // animator.
        if (!expand) {
            animatorSet.playTogether(animateCollapseAnimation(lockButton));
        }
        animatorSet.setDuration(EXPAND_COLLAPSE_DURATION);
        animatorSet.start();
     }

    /** Change the settings gear alpha when expanded or collapsed. */
     private void updateSettingsGearAlpha(ImageButton settingsButton, boolean expand,
            PropertySetter setter) {
        float toAlpha = expand ? 1 : 0;
        setter.setFloat(settingsButton, VIEW_ALPHA, toAlpha, Interpolators.LINEAR)
                .setDuration(SETTINGS_OPACITY_DURATION).setStartDelay(0);
    }
}
