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

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PRIVATESPACE;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.MAIN;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_ICON;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_HEADER;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_NOTHING;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_LOCK_ANIMATION_BEGIN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_LOCK_ANIMATION_END;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_LOCK_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_UNLOCK_ANIMATION_BEGIN;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_UNLOCK_ANIMATION_END;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_UNLOCK_TAP;
import static com.android.launcher3.model.BgDataModel.Callbacks.FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_NOT_PINNABLE;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;
import static com.android.launcher3.util.SettingsCache.PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.android.app.animation.Interpolators;
import com.android.launcher3.BuildConfig;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedPropertySetter;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.logging.StatsLogManager;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.PrivateSpaceInstallAppButtonInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.util.ApiWrapper;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.SettingsCache;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.RecyclerViewFastScroller;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Companion class for {@link ActivityAllAppsContainerView} to manage private space section related
 * logic in the Personal tab.
 */
public class PrivateProfileManager extends UserProfileManager {
    private static final int EXPAND_COLLAPSE_DURATION = 800;
    private static final int SETTINGS_OPACITY_DURATION = 400;
    private static final int TEXT_UNLOCK_OPACITY_DURATION = 300;
    private static final int TEXT_LOCK_OPACITY_DURATION = 50;
    private static final int APP_OPACITY_DURATION = 400;
    private static final int MASK_VIEW_DURATION = 200;
    private static final int APP_OPACITY_DELAY = 400;
    private static final int SETTINGS_AND_LOCK_GROUP_TRANSITION_DELAY = 400;
    private static final int SETTINGS_OPACITY_DELAY = 400;
    private static final int LOCK_TEXT_OPACITY_DELAY = 500;
    private static final int MASK_VIEW_DELAY = 400;
    private static final int NO_DELAY = 0;
    private static final int CONTAINER_OPACITY_DURATION = 150;
    private final ActivityAllAppsContainerView<?> mAllApps;
    private final Predicate<UserHandle> mPrivateProfileMatcher;
    private final int mPsHeaderHeight;
    private final int mFloatingMaskViewCornerRadius;
    private final RecyclerView.OnScrollListener mOnIdleScrollListener =
            new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                mIsScrolling = false;
            }
        }
    };
    private Intent mAppInstallerIntent = new Intent();
    private PrivateAppsSectionDecorator mPrivateAppsSectionDecorator;
    private boolean mPrivateSpaceSettingsAvailable;
    // Returns if the animation is currently running.
    private boolean mIsAnimationRunning;
    // mAnimate denotes if private space is ready to be animated.
    private boolean mReadyToAnimate;
    // Returns when the recyclerView is currently scrolling.
    private boolean mIsScrolling;
    // mIsStateTransitioning indicates that private space is transitioning between states.
    private boolean mIsStateTransitioning;
    private Runnable mOnPSHeaderAdded;
    @Nullable
    private RelativeLayout mPSHeader;
    private ConstraintLayout mFloatingMaskView;
    private final String mLockedStateContentDesc;
    private final String mUnLockedStateContentDesc;

    public PrivateProfileManager(UserManager userManager,
            ActivityAllAppsContainerView<?> allApps,
            StatsLogManager statsLogManager,
            UserCache userCache) {
        super(userManager, statsLogManager, userCache);
        mAllApps = allApps;
        mPrivateProfileMatcher = (user) -> userCache.getUserInfo(user).isPrivate();

        Context appContext = allApps.getContext().getApplicationContext();
        UI_HELPER_EXECUTOR.post(() -> initializeInBackgroundThread(appContext));
        mPsHeaderHeight = mAllApps.getContext().getResources().getDimensionPixelSize(
                R.dimen.ps_header_height);
        mLockedStateContentDesc = mAllApps.getContext()
                .getString(R.string.ps_container_lock_button_content_description);
        mUnLockedStateContentDesc = mAllApps.getContext()
                .getString(R.string.ps_container_unlock_button_content_description);
        mFloatingMaskViewCornerRadius = mAllApps.getContext().getResources().getDimensionPixelSize(
                R.dimen.ps_floating_mask_corner_radius);
    }

    /** Adds Private Space Header to the layout. */
    public int addPrivateSpaceHeader(ArrayList<BaseAllAppsAdapter.AdapterItem> adapterItems) {
        adapterItems.add(new BaseAllAppsAdapter.AdapterItem(VIEW_TYPE_PRIVATE_SPACE_HEADER));
        mAllApps.mAH.get(MAIN).mAdapter.notifyItemInserted(adapterItems.size() - 1);
        return adapterItems.size();
    }

    /** Adds Private Space System Apps Divider to the layout. */
    public int addSystemAppsDivider(List<BaseAllAppsAdapter.AdapterItem> adapterItems) {
        adapterItems.add(new BaseAllAppsAdapter
                .AdapterItem(VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER));
        mAllApps.mAH.get(MAIN).mAdapter.notifyItemInserted(adapterItems.size() - 1);
        return adapterItems.size();
    }

    /** Adds Private Space install app button to the layout. */
    public void addPrivateSpaceInstallAppButton(List<BaseAllAppsAdapter.AdapterItem> adapterItems) {
        Context context = mAllApps.getContext();
        // Prepare bitmapInfo
        Intent.ShortcutIconResource shortcut = Intent.ShortcutIconResource.fromContext(
                context, com.android.launcher3.R.drawable.private_space_install_app_icon);
        BitmapInfo bitmapInfo = LauncherIcons.obtain(context).createIconBitmap(shortcut);

        PrivateSpaceInstallAppButtonInfo itemInfo = new PrivateSpaceInstallAppButtonInfo();
        itemInfo.title = context.getResources().getString(R.string.ps_add_button_label);
        itemInfo.intent = mAppInstallerIntent;
        itemInfo.bitmap = bitmapInfo;
        itemInfo.contentDescription = context.getResources().getString(
                com.android.launcher3.R.string.ps_add_button_content_description);
        itemInfo.runtimeStatusFlags |= FLAG_NOT_PINNABLE;

        BaseAllAppsAdapter.AdapterItem item = new BaseAllAppsAdapter.AdapterItem(VIEW_TYPE_ICON);
        item.itemInfo = itemInfo;
        item.decorationInfo = new SectionDecorationInfo(context, ROUND_NOTHING,
                /* decorateTogether */ true);

        adapterItems.add(item);
        mAllApps.mAH.get(MAIN).mAdapter.notifyItemInserted(adapterItems.size() - 1);
    }

    /** Whether private profile should be hidden on Launcher. */
    public boolean isPrivateSpaceHidden() {
        return getCurrentState() == STATE_DISABLED && SettingsCache.INSTANCE
                    .get(mAllApps.getContext()).getValue(PRIVATE_SPACE_HIDE_WHEN_LOCKED_URI, 0);
    }

    /**
     * Resets the current state of Private Profile, w.r.t. to Launcher. The decorator should only
     * be applied upon expand before animating. When collapsing, reset() will remove the decorator
     * when animation is not running.
     */
    public void reset() {
        getMainRecyclerView().setChildAttachedConsumer(null);
        int previousState = getCurrentState();
        boolean isEnabled = !mAllApps.getAppsStore()
                .hasModelFlag(FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED);
        int updatedState = isEnabled ? STATE_ENABLED : STATE_DISABLED;
        setCurrentState(updatedState);
        mFloatingMaskView = null;
        if (mPSHeader != null) {
            mPSHeader.setAlpha(1);
        }
        // It's possible that previousState is 0 when reset is first called.
        mIsStateTransitioning = previousState != STATE_UNKNOWN && previousState != updatedState;
        if (previousState == STATE_DISABLED && updatedState == STATE_ENABLED) {
            postUnlock();
        } else if (previousState == STATE_ENABLED && updatedState == STATE_DISABLED){
            executeLock();
        }
        resetPrivateSpaceDecorator(updatedState);
    }

    /** Returns whether or not Private Space Settings Page is available. */
    public boolean isPrivateSpaceSettingsAvailable() {
        return mPrivateSpaceSettingsAvailable;
    }

    /** Sets whether Private Space Settings Page is available. */
    public boolean setPrivateSpaceSettingsAvailable(boolean value) {
        return mPrivateSpaceSettingsAvailable = value;
    }

    /** Initializes binder call based properties in non-main thread.
     * <p>
     * This can cause the Private Space container items to not load/respond correctly sometimes,
     * when the All Apps Container loads for the first time (device restarts, new profiles
     * added/removed, etc.), as the properties are being set in non-ui thread whereas the container
     * loads in the ui thread.
     * This case should still be ok, as locking the Private Space container and unlocking it,
     * reloads the values, fixing the incorrect UI.
     */
    private void initializeInBackgroundThread(Context appContext) {
        Preconditions.assertNonUiThread();
        ApiWrapper apiWrapper = ApiWrapper.INSTANCE.get(appContext);
        UserHandle profileUser = getProfileUser();
        if (profileUser != null) {
            mAppInstallerIntent = apiWrapper
                    .getAppMarketActivityIntent(BuildConfig.APPLICATION_ID, profileUser);
        }
        setPrivateSpaceSettingsAvailable(apiWrapper.getPrivateSpaceSettingsIntent() != null);
    }

    @VisibleForTesting
    void resetPrivateSpaceDecorator(int updatedState) {
        ActivityAllAppsContainerView<?>.AdapterHolder mainAdapterHolder = mAllApps.mAH.get(MAIN);
        if (updatedState == STATE_ENABLED) {
            // Create a new decorator instance if not already available.
            if (mPrivateAppsSectionDecorator == null) {
                mPrivateAppsSectionDecorator = new PrivateAppsSectionDecorator(
                        mainAdapterHolder.mAppsList);
            }
            for (int i = 0; i < mainAdapterHolder.mRecyclerView.getItemDecorationCount(); i++) {
                if (mainAdapterHolder.mRecyclerView.getItemDecorationAt(i)
                        .equals(mPrivateAppsSectionDecorator)) {
                    // No need to add another decorator if one is already present in recycler view.
                    return;
                }
            }
            // Add Private Space Decorator to the Recycler view.
            mainAdapterHolder.mRecyclerView.addItemDecoration(mPrivateAppsSectionDecorator);
        } else {
            // Remove Private Space Decorator from the Recycler view.
            if (mPrivateAppsSectionDecorator != null && !mIsAnimationRunning) {
                mainAdapterHolder.mRecyclerView.removeItemDecoration(mPrivateAppsSectionDecorator);
            }
        }
    }

    @Override
    public void setQuietMode(boolean enable) {
        super.setQuietMode(enable);
        mReadyToAnimate = true;
    }

    /**
     * Expand the private space after the app list has been added and updated from
     * {@link AlphabeticalAppsList#onAppsUpdated()}
     */
    void postUnlock() {
        if (mAllApps.isSearching()) {
            MAIN_EXECUTOR.post(this::exitSearchAndExpand);
        } else {
            MAIN_EXECUTOR.post(this::expandPrivateSpace);
        }
    }

    /** Collapses the private space before the app list has been updated. */
    void executeLock() {
        MAIN_EXECUTOR.execute(() -> updatePrivateStateAnimator(false));
    }

    void setAnimationRunning(boolean isAnimationRunning) {
        if (!isAnimationRunning) {
            mReadyToAnimate = false;
        }
        mIsAnimationRunning = isAnimationRunning;
    }

    boolean getAnimationRunning() {
        return mIsAnimationRunning;
    }

    @Override
    public Predicate<UserHandle> getUserMatcher() {
        return mPrivateProfileMatcher;
    }

    /**
     * Splits private apps into user installed and system apps.
     * When the list of system apps is empty, all apps are treated as system.
     */
    public Predicate<AppInfo> splitIntoUserInstalledAndSystemApps(Context context) {
        List<String> preInstallApps = UserCache.getInstance(context)
                .getPreInstallApps(getProfileUser());
        return appInfo -> !preInstallApps.isEmpty()
                && (appInfo.componentName == null
                || !(preInstallApps.contains(appInfo.componentName.getPackageName())));
    }

    /** Add Private Space Header view elements based upon {@link UserProfileState} */
    public void bindPrivateSpaceHeaderViewElements(RelativeLayout parent) {
        mPSHeader = parent;
        if (mOnPSHeaderAdded != null) {
            MAIN_EXECUTOR.execute(mOnPSHeaderAdded);
            mOnPSHeaderAdded = null;
        }
        // Set the transition duration for the settings and lock button to animate.
        ViewGroup settingAndLockGroup = mPSHeader.findViewById(R.id.settingsAndLockGroup);
        if (mReadyToAnimate) {
            enableLayoutTransition(settingAndLockGroup);
        } else {
            // Ensure any unwanted animations to not happen.
            settingAndLockGroup.setLayoutTransition(null);
        }

        //Add quietMode image and action for lock/unlock button
        ViewGroup lockButton = mPSHeader.findViewById(R.id.ps_lock_unlock_button);
        assert lockButton != null;
        updateLockButton(lockButton);

        //Trigger lock/unlock action from header.
        updateHeaderOnClickListener(mPSHeader);

        //Add image and action for private space settings button
        PrivateSpaceSettingsButton settingsButton = mPSHeader.findViewById(R.id.ps_settings_button);
        assert settingsButton != null;
        updatePrivateSpaceSettingsButton(settingsButton);

        //Add image for private space transitioning view
        ImageView transitionView = parent.findViewById(R.id.ps_transition_image);
        assert transitionView != null;
        updateTransitionImage(transitionView);
    }

    /**
     *  Adds the quietModeButton and attach onClickListener for the header to animate different
     *  states when clicked.
     */
    private void updateLockButton(ViewGroup lockButton) {
        TextView lockText = lockButton.findViewById(R.id.lock_text);
        switch (getCurrentState()) {
            case STATE_ENABLED -> {
                lockText.setVisibility(VISIBLE);
                lockButton.setVisibility(VISIBLE);
                lockButton.setOnClickListener(view -> lockingAction(/* lock */ true));
                lockButton.setContentDescription(mUnLockedStateContentDesc);
            }
            case STATE_DISABLED -> {
                lockText.setVisibility(GONE);
                lockButton.setVisibility(VISIBLE);
                lockButton.setOnClickListener(view -> lockingAction(/* lock */ false));
                lockButton.setContentDescription(mLockedStateContentDesc);
            }
            default -> lockButton.setVisibility(GONE);
        }
    }

    private void updateHeaderOnClickListener(RelativeLayout header) {
        if (getCurrentState() == STATE_DISABLED) {
            header.setOnClickListener(view -> lockingAction(/* lock */ false));
            header.setClickable(true);
            // Add header as accessibility target when disabled.
            header.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            header.setContentDescription(mLockedStateContentDesc);
        } else {
            header.setOnClickListener(null);
            header.setClickable(false);
            // Remove header from accessibility target when enabled.
            header.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
    }

    /** Sets the enablement of the profile when header or button is clicked. */
    private void lockingAction(boolean lock) {
        logEvents(lock ? LAUNCHER_PRIVATE_SPACE_LOCK_TAP : LAUNCHER_PRIVATE_SPACE_UNLOCK_TAP);
        setQuietMode(lock);
    }

    private void updatePrivateSpaceSettingsButton(PrivateSpaceSettingsButton settingsButton) {
        if (getCurrentState() == STATE_ENABLED
                && isPrivateSpaceSettingsAvailable()) {
            settingsButton.setVisibility(VISIBLE);
        } else {
            settingsButton.setVisibility(GONE);
        }
    }

    private void updateTransitionImage(ImageView transitionImage) {
        if (getCurrentState() == STATE_TRANSITION) {
            transitionImage.setVisibility(VISIBLE);
        } else {
            transitionImage.setVisibility(GONE);
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
                // If privateSpaceHidden() then the entire container decorator will be invisible and
                // we can directly move to an element above the header. There should always be one
                // element, as PS is present in the bottom of All Apps.
                smoothScroller.setTargetPosition(isPrivateSpaceHidden() ? i - 1 : i);
                RecyclerView.LayoutManager layoutManager = allAppsRecyclerView.getLayoutManager();
                if (layoutManager != null) {
                    startAnimationScroll(allAppsRecyclerView, layoutManager, smoothScroller);
                    // Preserve decorator if floating mask view exists.
                    if (mFloatingMaskView == null) {
                        currentItem.decorationInfo = null;
                    }
                }
                break;
            }
            // Make the private space apps gone to "collapse".
            if (mFloatingMaskView == null && isPrivateSpaceItem(currentItem)) {
                RecyclerView.ViewHolder viewHolder =
                        allAppsRecyclerView.findViewHolderForAdapterPosition(i);
                if (viewHolder != null) {
                    viewHolder.itemView.setVisibility(GONE);
                    currentItem.decorationInfo = null;
                }
            }
        }
    }

    /**
     * Upon expanding, only scroll to the item position in the adapter that allows the header to be
     * visible.
     */
    public int scrollForHeaderToBeVisibleInContainer(
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
                itemToScrollTo = i;
                if (rowToExpandToWithRespectToHeader == -1) {
                    rowToExpandToWithRespectToHeader = currentItem.rowIndex;
                }
                // If there are no tabs, decrease the row to scroll to by 1 since the header
                // may be cut off slightly.
                int rowToScrollTo =
                        (int) Math.floor((double) (mAllApps.getHeight() - psHeaderHeight
                                - mAllApps.getHeaderProtectionHeight()) / allAppsCellHeight)
                                - (mAllApps.isUsingTabs() ? 0 : 1);
                int currentRowDistance = currentItem.rowIndex - rowToExpandToWithRespectToHeader;
                // rowToScrollTo - 1 since the item to scroll to is 0 indexed.
                if (currentRowDistance == rowToScrollTo - 1) {
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
                startAnimationScroll(allAppsRecyclerView, layoutManager, smoothScroller);
            }
        }
        return itemToScrollTo;
    }

    /**
     * Scrolls up to the private space header and animates the collapsing of the text.
     */
    private ValueAnimator animateCollapseAnimation() {
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
                // Scroll up to header.
                collapse();
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (scrollBar != null) {
                    scrollBar.setThumbOffsetY(-1);
                    scrollBar.setVisibility(VISIBLE);
                }
            }
        });
        return collapseAnim;
    }

    private ValueAnimator animateAlphaOfIcons(boolean isExpanding) {
        float from = isExpanding ? 0 : 1;
        float to = isExpanding ? 1 : 0;
        AllAppsRecyclerView allAppsRecyclerView = mAllApps.getActiveRecyclerView();
        List<BaseAllAppsAdapter.AdapterItem> allAppsAdapterItems =
                mAllApps.getActiveRecyclerView().getApps().getAdapterItems();
        ValueAnimator alphaAnim = ObjectAnimator.ofFloat(from, to);
        alphaAnim.setDuration(APP_OPACITY_DURATION)
                .setStartDelay(isExpanding ? APP_OPACITY_DELAY : NO_DELAY);
        alphaAnim.setInterpolator(Interpolators.LINEAR);
        alphaAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float newAlpha = (float) valueAnimator.getAnimatedValue();
                for (int i = 0; i < allAppsAdapterItems.size(); i++) {
                    BaseAllAppsAdapter.AdapterItem currentItem = allAppsAdapterItems.get(i);
                    // When not hidden: Fade all PS items except header.
                    // When hidden: Fade all items.
                    if (isPrivateSpaceItem(currentItem) &&
                            (currentItem.viewType != VIEW_TYPE_PRIVATE_SPACE_HEADER
                                    || isPrivateSpaceHidden())) {
                        RecyclerView.ViewHolder viewHolder =
                                allAppsRecyclerView.findViewHolderForAdapterPosition(i);
                        if (viewHolder != null) {
                            viewHolder.itemView.setAlpha(newAlpha);
                        }
                    }
                }
            }
        });
        return alphaAnim;
    }

    /**
     * Using PropertySetter{@link PropertySetter}, we can update the view's attributes within an
     * animation. At the moment, collapsing, setting alpha changes, and animating the text is done
     * here.
     */
    private void updatePrivateStateAnimator(boolean expand) {
        if (!Flags.enablePrivateSpace() || !Flags.privateSpaceAnimation()) {
            return;
        }
        if (mPSHeader == null) {
            mOnPSHeaderAdded = () -> updatePrivateStateAnimator(expand);
            setAnimationRunning(false);
            return;
        }
        attachFloatingMaskView(expand);
        ViewGroup settingsAndLockGroup = mPSHeader.findViewById(R.id.settingsAndLockGroup);
        if (settingsAndLockGroup.getLayoutTransition() == null) {
            // Set a new transition if the current ViewGroup does not already contain one as each
            // transition should only happen once when applied.
            enableLayoutTransition(settingsAndLockGroup);
        }
        settingsAndLockGroup.getLayoutTransition().setStartDelay(
                LayoutTransition.CHANGING,
                expand ? SETTINGS_AND_LOCK_GROUP_TRANSITION_DELAY : NO_DELAY);
        PropertySetter headerSetter = new AnimatedPropertySetter();
        headerSetter.add(updateSettingsGearAlpha(expand));
        headerSetter.add(updateLockTextAlpha(expand));
        AnimatorSet animatorSet = headerSetter.buildAnim();
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mStatsLogManager.logger().sendToInteractionJankMonitor(
                        expand
                                ? LAUNCHER_PRIVATE_SPACE_UNLOCK_ANIMATION_BEGIN
                                : LAUNCHER_PRIVATE_SPACE_LOCK_ANIMATION_BEGIN,
                        mAllApps.getActiveRecyclerView());
                // Animate the collapsing of the text at the same time while updating lock button.
                mPSHeader.findViewById(R.id.lock_text).setVisibility(expand ? VISIBLE : GONE);
                setAnimationRunning(true);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                detachFloatingMaskView();
            }
        });
        animatorSet.addListener(forEndCallback(() -> {
            mIsStateTransitioning = false;
            setAnimationRunning(false);
            getMainRecyclerView().setChildAttachedConsumer(child -> child.setAlpha(1));
            mStatsLogManager.logger().sendToInteractionJankMonitor(
                    expand
                            ? LAUNCHER_PRIVATE_SPACE_UNLOCK_ANIMATION_END
                            : LAUNCHER_PRIVATE_SPACE_LOCK_ANIMATION_END,
                    mAllApps.getActiveRecyclerView());
            if (!expand) {
                // Call onAppsUpdated() because it may be canceled when this animation occurs.
                mAllApps.getPersonalAppList().onAppsUpdated();
                if (isPrivateSpaceHidden()) {
                    // TODO (b/325455879): Figure out if we can avoid this.
                    getMainRecyclerView().getAdapter().notifyDataSetChanged();
                }
            }
        }));
        if (expand) {
            animatorSet.playTogether(animateAlphaOfIcons(true),
                    translateFloatingMaskView(false));
        } else {
            if (isPrivateSpaceHidden()) {
                animatorSet.playSequentially(animateAlphaOfIcons(false),
                        animateAlphaOfPrivateSpaceContainer(),
                        animateCollapseAnimation());
            } else {
                animatorSet.playSequentially(translateFloatingMaskView(true),
                        animateAlphaOfIcons(false),
                        animateCollapseAnimation());
            }
        }
        animatorSet.start();
    }

    /** Fades out the private space container (defined by its items' decorators). */
    private ValueAnimator animateAlphaOfPrivateSpaceContainer() {
        int from = 255; // 100% opacity.
        int to = 0; // No opacity.
        ValueAnimator alphaAnim = ObjectAnimator.ofInt(from, to);
        AllAppsRecyclerView allAppsRecyclerView = mAllApps.getActiveRecyclerView();
        List<BaseAllAppsAdapter.AdapterItem> allAppsAdapterItems =
                allAppsRecyclerView.getApps().getAdapterItems();
        alphaAnim.setDuration(CONTAINER_OPACITY_DURATION);
        alphaAnim.addUpdateListener(valueAnimator -> {
            for (BaseAllAppsAdapter.AdapterItem currentItem : allAppsAdapterItems) {
                if (isPrivateSpaceItem(currentItem)) {
                    currentItem.setDecorationFillAlpha((int) valueAnimator.getAnimatedValue());
                }
            }
            // Invalidate the parent view, to redraw the decorations with changed alpha.
            allAppsRecyclerView.invalidate();
        });
        return alphaAnim;
    }

    /** Fades out the private space container. */
    private ValueAnimator translateFloatingMaskView(boolean animateIn) {
        if (!Flags.privateSpaceFloatingMaskView() || mFloatingMaskView == null) {
            return new ValueAnimator();
        }
        // Translate base on the height amount. Translates out on expand and in on collapse.
        float floatingMaskViewHeight = getFloatingMaskViewHeight();
        float from = animateIn ? floatingMaskViewHeight : 0;
        float to = animateIn ? 0 : floatingMaskViewHeight;
        ValueAnimator alphaAnim = ObjectAnimator.ofFloat(from, to);
        alphaAnim.setDuration(MASK_VIEW_DURATION);
        alphaAnim.setStartDelay(MASK_VIEW_DELAY);
        alphaAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mFloatingMaskView.setTranslationY((float) valueAnimator.getAnimatedValue());
            }
        });
        return alphaAnim;
    }

    /** Animates the layout changes when the text of the button becomes visible/gone. */
    private void enableLayoutTransition(ViewGroup settingsAndLockGroup) {
        LayoutTransition settingsAndLockTransition = new LayoutTransition();
        settingsAndLockTransition.enableTransitionType(LayoutTransition.CHANGING);
        settingsAndLockTransition.setDuration(EXPAND_COLLAPSE_DURATION);
        settingsAndLockTransition.setInterpolator(LayoutTransition.CHANGING,
                Interpolators.STANDARD);
        settingsAndLockTransition.addTransitionListener(new LayoutTransition.TransitionListener() {
            @Override
            public void startTransition(LayoutTransition transition, ViewGroup viewGroup,
                    View view, int i) {
            }
            @Override
            public void endTransition(LayoutTransition transition, ViewGroup viewGroup,
                    View view, int i) {
                settingsAndLockGroup.setLayoutTransition(null);
                mReadyToAnimate = false;
            }
        });
        settingsAndLockGroup.setLayoutTransition(settingsAndLockTransition);
    }

    /** Change the settings gear alpha when expanded or collapsed. */
    private ValueAnimator updateSettingsGearAlpha(boolean expand) {
        if (mPSHeader == null) {
            return new ValueAnimator();
        }
        float from = expand ? 0 : 1;
        float to = expand ? 1 : 0;
        ValueAnimator settingsAlphaAnim = ObjectAnimator.ofFloat(from, to);
        settingsAlphaAnim.setDuration(SETTINGS_OPACITY_DURATION);
        settingsAlphaAnim.setStartDelay(expand ? SETTINGS_OPACITY_DELAY : NO_DELAY);
        settingsAlphaAnim.setInterpolator(Interpolators.LINEAR);
        settingsAlphaAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mPSHeader.findViewById(R.id.ps_settings_button)
                        .setAlpha((float) valueAnimator.getAnimatedValue());
            }
        });
        return settingsAlphaAnim;
    }

    private ValueAnimator updateLockTextAlpha(boolean expand) {
        if (mPSHeader == null) {
            return new ValueAnimator();
        }
        float from = expand ? 0 : 1;
        float to = expand ? 1 : 0;
        ValueAnimator alphaAnim = ObjectAnimator.ofFloat(from, to);
        alphaAnim.setDuration(expand ? TEXT_UNLOCK_OPACITY_DURATION : TEXT_LOCK_OPACITY_DURATION);
        alphaAnim.setStartDelay(expand ? LOCK_TEXT_OPACITY_DELAY : NO_DELAY);
        alphaAnim.setInterpolator(Interpolators.LINEAR);
        alphaAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mPSHeader.findViewById(R.id.lock_text).setAlpha(
                        (float) valueAnimator.getAnimatedValue());
            }
        });
        return alphaAnim;
    }

    void expandPrivateSpace() {
        // If we are on main adapter view, we apply the PS Container expansion animation and
        // scroll down to load the entire container, making animation visible.
        ActivityAllAppsContainerView<?>.AdapterHolder mainAdapterHolder = mAllApps.mAH.get(MAIN);
        List<BaseAllAppsAdapter.AdapterItem> adapterItems =
                mainAdapterHolder.mAppsList.getAdapterItems();
        if (Flags.enablePrivateSpace() && Flags.privateSpaceAnimation()
                && mAllApps.isPersonalTab()) {
            // Animate the text and settings icon.
            DeviceProfile deviceProfile =
                    ActivityContext.lookupContext(mAllApps.getContext()).getDeviceProfile();
            scrollForHeaderToBeVisibleInContainer(mainAdapterHolder.mRecyclerView, adapterItems,
                    getPsHeaderHeight(), deviceProfile.allAppsCellHeightPx);
            updatePrivateStateAnimator(true);
        }
    }

    private void exitSearchAndExpand() {
        mAllApps.updateHeaderScroll(0);
        // Animate to A-Z with 0 time to reset the animation with proper state management.
        mAllApps.animateToSearchState(false, 0);
        MAIN_EXECUTOR.post(() -> {
            mAllApps.mSearchUiManager.resetSearch();
            mAllApps.switchToTab(ActivityAllAppsContainerView.AdapterHolder.MAIN);
            expandPrivateSpace();
        });
    }

    private void attachFloatingMaskView(boolean expand) {
        if (!Flags.privateSpaceFloatingMaskView()) {
            return;
        }
        mFloatingMaskView = (FloatingMaskView) mAllApps.getLayoutInflater().inflate(
                R.layout.private_space_mask_view, mAllApps, false);
        mAllApps.addView(mFloatingMaskView);
        // Translate off the screen first if its collapsing so this header view isn't visible to
        // user when animation starts.
        if (!expand) {
            mFloatingMaskView.setTranslationY(getFloatingMaskViewHeight());
        }
        mFloatingMaskView.setVisibility(VISIBLE);
    }

    private void detachFloatingMaskView() {
        if (mFloatingMaskView != null) {
            mAllApps.removeView(mFloatingMaskView);
        }
        mFloatingMaskView = null;
    }

    /** Starts the smooth scroll with the provided smoothScroller and add idle listener. */
    private void startAnimationScroll(AllAppsRecyclerView allAppsRecyclerView,
            RecyclerView.LayoutManager layoutManager, RecyclerView.SmoothScroller smoothScroller) {
        mIsScrolling = true;
        layoutManager.startSmoothScroll(smoothScroller);
        allAppsRecyclerView.removeOnScrollListener(mOnIdleScrollListener);
        allAppsRecyclerView.addOnScrollListener(mOnIdleScrollListener);
    }

    private float getFloatingMaskViewHeight() {
        return mFloatingMaskViewCornerRadius + getMainRecyclerView().getPaddingBottom();
    }

    AllAppsRecyclerView getMainRecyclerView() {
        return mAllApps.mAH.get(ActivityAllAppsContainerView.AdapterHolder.MAIN).mRecyclerView;
    }

    /** Returns if private space is readily available to be animated. */
    boolean getReadyToAnimate() {
        return mReadyToAnimate;
    }

    /** Returns when a smooth scroll is happening. */
    boolean isScrolling() {
        return mIsScrolling;
    }

    /**
     * Returns when private space is in the process of transitioning. This is different from
     * getAnimate() since mStateTransitioning checks from the time transitioning starts happening
     * in reset() as oppose to when private space is animating. This should be used to ensure
     * Private Space state during onBind().
     */
    boolean isStateTransitioning() {
        return mIsStateTransitioning;
    }

    int getPsHeaderHeight() {
        return mPsHeaderHeight;
    }

    boolean isPrivateSpaceItem(BaseAllAppsAdapter.AdapterItem item) {
        return getItemInfoMatcher().test(item.itemInfo) || item.decorationInfo != null
                || (item.itemInfo instanceof PrivateSpaceInstallAppButtonInfo);
    }
}
