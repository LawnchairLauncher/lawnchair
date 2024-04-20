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
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_PRIVATESPACE;
import static com.android.launcher3.allapps.ActivityAllAppsContainerView.AdapterHolder.MAIN;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_ICON;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_HEADER;
import static com.android.launcher3.allapps.BaseAllAppsAdapter.VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_NOTHING;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_LOCK_TAP;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_PRIVATE_SPACE_SETTINGS_TAP;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Companion class for {@link ActivityAllAppsContainerView} to manage private space section related
 * logic in the Personal tab.
 */
public class PrivateProfileManager extends UserProfileManager {
    private static final int EXPAND_COLLAPSE_DURATION = 800;
    private static final int SETTINGS_OPACITY_DURATION = 160;
    private final ActivityAllAppsContainerView<?> mAllApps;
    private final Predicate<UserHandle> mPrivateProfileMatcher;
    private final int mPsHeaderHeight;
    private final RecyclerView.OnScrollListener mOnIdleScrollListener =
            new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                mAnimationScrolling = false;
            }
        }
    };
    private Set<String> mPreInstalledSystemPackages = new HashSet<>();
    private Intent mAppInstallerIntent = new Intent();
    private PrivateAppsSectionDecorator mPrivateAppsSectionDecorator;
    private boolean mPrivateSpaceSettingsAvailable;
    private boolean mIsAnimationRunning;
    private boolean mAnimate;
    private boolean mAnimationScrolling;
    private Runnable mOnPSHeaderAdded;
    @Nullable
    private RelativeLayout mPSHeader;

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

    /**
     * Disables quiet mode for Private Space User Profile.
     * When called from search, a runnable is set and executed in the {@link #reset()} method, when
     * Launcher receives update about profile availability.
     * The runnable is only executed once, and reset after execution.
     * In case the method is called again, before the previously set runnable was executed,
     * the runnable will be updated.
     */
    public void unlockPrivateProfile() {
        setQuietMode(false);
    }

    /** Enables quiet mode for Private Space User Profile. */
    void lockPrivateProfile() {
        setQuietMode(true);
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
        if (mPSHeader != null) {
            mPSHeader.setAlpha(1);
        }
        if (transitioningFromLockedToUnlocked(previousState, updatedState)) {
            postUnlock();
        } else if (transitioningFromUnlockedToLocked(previousState, updatedState)){
            executeLock();
        }
        resetPrivateSpaceDecorator(updatedState);
    }

    /**
     * Opens the Private Space Settings Page.
     *
     * @param view the view that was clicked to open the settings page and which will be the same
     *             view to animate back. Otherwise if there is no view, simply start the activity.
     */
    public void openPrivateSpaceSettings(View view) {
        if (mPrivateSpaceSettingsAvailable) {
            Context context = mAllApps.getContext();
            Intent intent = ApiWrapper.INSTANCE.get(context).getPrivateSpaceSettingsIntent();
            if (view == null) {
                context.startActivity(intent);
                return;
            }
            ActivityContext activityContext = ActivityContext.lookupContext(context);
            AppInfo itemInfo = new AppInfo();
            itemInfo.id = CONTAINER_PRIVATESPACE;
            itemInfo.componentName = intent.getComponent();
            itemInfo.container = CONTAINER_PRIVATESPACE;
            view.setTag(itemInfo);
            activityContext.startActivitySafely(view, intent, itemInfo);
        }
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
            mPreInstalledSystemPackages = new HashSet<>(
                    apiWrapper.getPreInstalledSystemPackages(profileUser));
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
        mAnimate = true;
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
            mAnimate = false;
        }
        mIsAnimationRunning = isAnimationRunning;
    }

    boolean getAnimationRunning() {
        return mIsAnimationRunning;
    }

    private boolean transitioningFromLockedToUnlocked(int previousState, int updatedState) {
        return previousState == STATE_DISABLED && updatedState == STATE_ENABLED;
    }

    private boolean transitioningFromUnlockedToLocked(int previousState, int updatedState) {
        return previousState == STATE_ENABLED && updatedState == STATE_DISABLED;
    }

    @Override
    public Predicate<UserHandle> getUserMatcher() {
        return mPrivateProfileMatcher;
    }

    /**
     * Splits private apps into user installed and system apps.
     * When the list of system apps is empty, all apps are treated as system.
     */
    public Predicate<AppInfo> splitIntoUserInstalledAndSystemApps() {
        return appInfo -> !mPreInstalledSystemPackages.isEmpty()
                && (appInfo.componentName == null
                || !(mPreInstalledSystemPackages.contains(appInfo.componentName.getPackageName())));
    }

    /** Add Private Space Header view elements based upon {@link UserProfileState} */
    public void addPrivateSpaceHeaderViewElements(RelativeLayout parent) {
        mPSHeader = parent;
        if (mOnPSHeaderAdded != null) {
            MAIN_EXECUTOR.execute(mOnPSHeaderAdded);
            mOnPSHeaderAdded = null;
        }
        // Set the transition duration for the settings and lock button to animate.
        ViewGroup settingAndLockGroup = mPSHeader.findViewById(R.id.settingsAndLockGroup);
        if (mAnimate) {
            enableLayoutTransition(settingAndLockGroup);
        } else {
            // Ensure any unwanted animations to not happen.
            settingAndLockGroup.setLayoutTransition(null);
        }

        //Add quietMode image and action for lock/unlock button
        ViewGroup lockButton = mPSHeader.findViewById(R.id.ps_lock_unlock_button);
        assert lockButton != null;
        addLockButton(lockButton);

        //Trigger lock/unlock action from header.
        addHeaderOnClickListener(mPSHeader);

        //Add image and action for private space settings button
        ImageButton settingsButton = mPSHeader.findViewById(R.id.ps_settings_button);
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
    private void addLockButton(ViewGroup lockButton) {
        TextView lockText = lockButton.findViewById(R.id.lock_text);
        switch (getCurrentState()) {
            case STATE_ENABLED -> {
                lockText.setVisibility(VISIBLE);
                lockButton.setVisibility(VISIBLE);
                lockButton.setOnClickListener(view -> lockingAction(/* lock */ true));
            }
            case STATE_DISABLED -> {
                lockText.setVisibility(GONE);
                lockButton.setVisibility(VISIBLE);
                lockButton.setOnClickListener(view -> lockingAction(/* lock */ false));
            }
            default -> lockButton.setVisibility(GONE);
        }
    }

    private void addHeaderOnClickListener(RelativeLayout header) {
        if (getCurrentState() == STATE_DISABLED) {
            header.setOnClickListener(view -> lockingAction(/* lock */ false));
            header.setClickable(true);
        } else {
            header.setOnClickListener(null);
            header.setClickable(false);
        }
    }

    /** Sets the enablement of the profile when header or button is clicked. */
    private void lockingAction(boolean lock) {
        logEvents(lock ? LAUNCHER_PRIVATE_SPACE_LOCK_TAP : LAUNCHER_PRIVATE_SPACE_UNLOCK_TAP);
        if (lock) {
            lockPrivateProfile();
        } else {
            unlockPrivateProfile();
        }
    }

    private void addPrivateSpaceSettingsButton(ImageButton settingsButton) {
        if (getCurrentState() == STATE_ENABLED
                && isPrivateSpaceSettingsAvailable()) {
            settingsButton.setVisibility(VISIBLE);
            settingsButton.setAlpha(1f);
            settingsButton.setOnClickListener(
                    view -> {
                        logEvents(LAUNCHER_PRIVATE_SPACE_SETTINGS_TAP);
                        openPrivateSpaceSettings(view);
                    });
        } else {
            settingsButton.setVisibility(GONE);
        }
    }

    private void addTransitionImage(ImageView transitionImage) {
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
                smoothScroller.setTargetPosition(i);
                RecyclerView.LayoutManager layoutManager = allAppsRecyclerView.getLayoutManager();
                if (layoutManager != null) {
                    startAnimationScroll(allAppsRecyclerView, layoutManager, smoothScroller);
                    currentItem.decorationInfo = null;
                }
                break;
            }
            // Make the private space apps gone to "collapse".
            if (isPrivateSpaceItem(currentItem)) {
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
        alphaAnim.setDuration(EXPAND_COLLAPSE_DURATION);
        alphaAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float newAlpha = (float) valueAnimator.getAnimatedValue();
                for (int i = 0; i < allAppsAdapterItems.size(); i++) {
                    BaseAllAppsAdapter.AdapterItem currentItem = allAppsAdapterItems.get(i);
                    if (isPrivateSpaceItem(currentItem) &&
                            currentItem.viewType != VIEW_TYPE_PRIVATE_SPACE_HEADER) {
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
        ViewGroup settingsAndLockGroup = mPSHeader.findViewById(R.id.settingsAndLockGroup);
        ViewGroup lockButton = mPSHeader.findViewById(R.id.ps_lock_unlock_button);
        if (settingsAndLockGroup.getLayoutTransition() == null) {
            // Set a new transition if the current ViewGroup does not already contain one as each
            // transition should only happen once when applied.
            enableLayoutTransition(settingsAndLockGroup);
        }
        PropertySetter headerSetter = new AnimatedPropertySetter();
        ImageButton settingsButton = mPSHeader.findViewById(R.id.ps_settings_button);
        updateSettingsGearAlpha(settingsButton, expand, headerSetter);
        AnimatorSet animatorSet = headerSetter.buildAnim();
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                // Animate the collapsing of the text at the same time while updating lock button.
                lockButton.findViewById(R.id.lock_text).setVisibility(expand ? VISIBLE : GONE);
                setAnimationRunning(true);
            }
        });
        animatorSet.addListener(forEndCallback(() -> {
            setAnimationRunning(false);
            getMainRecyclerView().setChildAttachedConsumer(child -> child.setAlpha(1));
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
            animatorSet.playTogether(animateAlphaOfIcons(true));
        } else {
            if (isPrivateSpaceHidden()) {
                animatorSet.playSequentially(animateAlphaOfIcons(false),
                        animateCollapseAnimation(), fadeOutHeaderAlpha());
            } else {
                animatorSet.playSequentially(animateAlphaOfIcons(false),
                        animateCollapseAnimation());
            }
        }
        animatorSet.setDuration(EXPAND_COLLAPSE_DURATION);
        animatorSet.start();
    }

    /** Fades out the private space container. */
    private ValueAnimator fadeOutHeaderAlpha() {
        if (mPSHeader == null) {
            return new ValueAnimator();
        }
        float from = 1;
        float to = 0;
        ValueAnimator alphaAnim = ObjectAnimator.ofFloat(from, to);
        alphaAnim.setDuration(EXPAND_COLLAPSE_DURATION);
        alphaAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                if (mPSHeader != null) {
                    mPSHeader.setAlpha((float) valueAnimator.getAnimatedValue());
                }
            }
        });
        return alphaAnim;
    }

    /** Animates the layout changes when the text of the button becomes visible/gone. */
    private void enableLayoutTransition(ViewGroup settingsAndLockGroup) {
        LayoutTransition settingsAndLockTransition = new LayoutTransition();
        settingsAndLockTransition.enableTransitionType(LayoutTransition.CHANGING);
        settingsAndLockTransition.setDuration(EXPAND_COLLAPSE_DURATION);
        settingsAndLockTransition.addTransitionListener(new LayoutTransition.TransitionListener() {
            @Override
            public void startTransition(LayoutTransition transition, ViewGroup viewGroup,
                    View view, int i) {
            }
            @Override
            public void endTransition(LayoutTransition transition, ViewGroup viewGroup,
                    View view, int i) {
                settingsAndLockGroup.setLayoutTransition(null);
                mAnimate = false;
            }
        });
        settingsAndLockGroup.setLayoutTransition(settingsAndLockTransition);
    }

    /** Change the settings gear alpha when expanded or collapsed. */
    private void updateSettingsGearAlpha(ImageButton settingsButton, boolean expand,
            PropertySetter setter) {
        float toAlpha = expand ? 1 : 0;
        setter.setFloat(settingsButton, VIEW_ALPHA, toAlpha, Interpolators.LINEAR)
                .setDuration(SETTINGS_OPACITY_DURATION).setStartDelay(0);
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

    /** Starts the smooth scroll with the provided smoothScroller and add idle listener. */
    private void startAnimationScroll(AllAppsRecyclerView allAppsRecyclerView,
            RecyclerView.LayoutManager layoutManager, RecyclerView.SmoothScroller smoothScroller) {
        mAnimationScrolling = true;
        layoutManager.startSmoothScroll(smoothScroller);
        allAppsRecyclerView.removeOnScrollListener(mOnIdleScrollListener);
        allAppsRecyclerView.addOnScrollListener(mOnIdleScrollListener);
    }

    AllAppsRecyclerView getMainRecyclerView() {
        return mAllApps.mAH.get(ActivityAllAppsContainerView.AdapterHolder.MAIN).mRecyclerView;
    }

    boolean getAnimate() {
        return mAnimate;
    }

    boolean getAnimationScrolling() {
        return mAnimationScrolling;
    }

    int getPsHeaderHeight() {
        return mPsHeaderHeight;
    }

    boolean isPrivateSpaceItem(BaseAllAppsAdapter.AdapterItem item) {
        return getItemInfoMatcher().test(item.itemInfo) || item.decorationInfo != null
                || (item.itemInfo instanceof PrivateSpaceInstallAppButtonInfo);
    }
}
