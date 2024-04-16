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

import static android.view.View.GONE;

import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_BOTTOM_LEFT;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_BOTTOM_RIGHT;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_NOTHING;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_TOP_LEFT;
import static com.android.launcher3.allapps.SectionDecorationInfo.ROUND_TOP_RIGHT;
import static com.android.launcher3.allapps.UserProfileManager.STATE_DISABLED;
import static com.android.launcher3.allapps.UserProfileManager.STATE_ENABLED;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.allapps.search.SearchAdapterProvider;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.views.ActivityContext;

/**
 * Adapter for all the apps.
 *
 * @param <T> Type of context inflating all apps.
 */
public abstract class BaseAllAppsAdapter<T extends Context & ActivityContext> extends
        RecyclerView.Adapter<BaseAllAppsAdapter.ViewHolder> {

    public static final String TAG = "BaseAllAppsAdapter";

    // A normal icon
    public static final int VIEW_TYPE_ICON = 1 << 1;
    // The message shown when there are no filtered results
    public static final int VIEW_TYPE_EMPTY_SEARCH = 1 << 2;
    // A divider that separates the apps list and the search market button
    public static final int VIEW_TYPE_ALL_APPS_DIVIDER = 1 << 3;

    public static final int VIEW_TYPE_WORK_EDU_CARD = 1 << 4;
    public static final int VIEW_TYPE_WORK_DISABLED_CARD = 1 << 5;
    public static final int VIEW_TYPE_PRIVATE_SPACE_HEADER = 1 << 6;
    public static final int VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER = 1 << 7;
    public static final int NEXT_ID = 8;

    // Common view type masks
    public static final int VIEW_TYPE_MASK_DIVIDER = VIEW_TYPE_ALL_APPS_DIVIDER;
    public static final int VIEW_TYPE_MASK_ICON = VIEW_TYPE_ICON;

    public static final int VIEW_TYPE_MASK_PRIVATE_SPACE_HEADER =
            VIEW_TYPE_PRIVATE_SPACE_HEADER;
    public static final int VIEW_TYPE_MASK_PRIVATE_SPACE_SYS_APPS_DIVIDER =
            VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER;

    protected final SearchAdapterProvider<?> mAdapterProvider;

    /**
     * ViewHolder for each icon.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {

        public ViewHolder(View v) {
            super(v);
        }
    }

    /** Sets the number of apps to be displayed in one row of the all apps screen. */
    public abstract void setAppsPerRow(int appsPerRow);

    /**
     * Info about a particular adapter item (can be either section or app)
     */
    public static class AdapterItem {
        /** Common properties */
        // The type of this item
        public final int viewType;

        // The row that this item shows up on
        public int rowIndex;
        // The index of this app in the row
        public int rowAppIndex;
        // The associated ItemInfoWithIcon for the item
        public AppInfo itemInfo = null;
        // Private App Decorator
        public SectionDecorationInfo decorationInfo = null;
        public AdapterItem(int viewType) {
            this.viewType = viewType;
        }

        /**
         * Factory method for AppIcon AdapterItem
         */
        public static AdapterItem asApp(AppInfo appInfo) {
            AdapterItem item = new AdapterItem(VIEW_TYPE_ICON);
            item.itemInfo = appInfo;
            return item;
        }

        public static AdapterItem asAppWithDecorationInfo(AppInfo appInfo,
                SectionDecorationInfo decorationInfo) {
            AdapterItem item = asApp(appInfo);
            item.decorationInfo = decorationInfo;
            return item;
        }

        protected boolean isCountedForAccessibility() {
            return viewType == VIEW_TYPE_ICON;
        }

        /**
         * Returns true if the items represent the same object
         */
        public boolean isSameAs(AdapterItem other) {
            return (other.viewType == viewType) && (other.getClass() == getClass());
        }

        /**
         * This is called only if {@link #isSameAs} returns true to check if the contents are same
         * as well. Returning true will prevent redrawing of thee item.
         */
        public boolean isContentSame(AdapterItem other) {
            return itemInfo == null && other.itemInfo == null;
        }

        @Nullable
        public SectionDecorationInfo getDecorationInfo() {
            return decorationInfo;
        }

        /** Sets the alpha of the decorator for this item. */
        protected void setDecorationFillAlpha(int alpha) {
            if (decorationInfo == null || decorationInfo.getDecorationHandler() == null) {
                return;
            }
            decorationInfo.getDecorationHandler().setFillAlpha(alpha);
        }
    }

    protected final T mActivityContext;
    protected final AlphabeticalAppsList<T> mApps;
    // The text to show when there are no search results and no market search handler.
    protected int mAppsPerRow;

    protected final LayoutInflater mLayoutInflater;
    protected final OnClickListener mOnIconClickListener;
    protected final OnLongClickListener mOnIconLongClickListener;
    protected OnFocusChangeListener mIconFocusListener;

    public BaseAllAppsAdapter(T activityContext, LayoutInflater inflater,
            AlphabeticalAppsList<T> apps, SearchAdapterProvider<?> adapterProvider) {
        mActivityContext = activityContext;
        mApps = apps;
        mLayoutInflater = inflater;

        mOnIconClickListener = mActivityContext.getItemOnClickListener();
        mOnIconLongClickListener = mActivityContext.getAllAppsItemLongClickListener();

        mAdapterProvider = adapterProvider;
    }

    /** Checks if the passed viewType represents all apps divider. */
    public static boolean isDividerViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_DIVIDER);
    }

    /** Checks if the passed viewType represents all apps icon. */
    public static boolean isIconViewType(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_ICON);
    }

    /** Checks if the passed viewType represents private space header. */
    public static boolean isPrivateSpaceHeaderView(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_PRIVATE_SPACE_HEADER);
    }

    /** Checks if the passed viewType represents private space system apps divider. */
    public static boolean isPrivateSpaceSysAppsDividerView(int viewType) {
        return isViewType(viewType, VIEW_TYPE_MASK_PRIVATE_SPACE_SYS_APPS_DIVIDER);
    }

    public void setIconFocusListener(OnFocusChangeListener focusListener) {
        mIconFocusListener = focusListener;
    }

    /**
     * Returns the layout manager.
     */
    public abstract RecyclerView.LayoutManager getLayoutManager();

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_ICON:
                int layout = (Flags.enableTwolineToggle()
                        && LauncherPrefs.ENABLE_TWOLINE_ALLAPPS_TOGGLE.get(
                                mActivityContext.getApplicationContext()))
                        ? R.layout.all_apps_icon_twoline : R.layout.all_apps_icon;
                BubbleTextView icon = (BubbleTextView) mLayoutInflater.inflate(
                        layout, parent, false);
                icon.setLongPressTimeoutFactor(1f);
                icon.setOnFocusChangeListener(mIconFocusListener);
                icon.setOnClickListener(mOnIconClickListener);
                icon.setOnLongClickListener(mOnIconLongClickListener);
                // Ensure the all apps icon height matches the workspace icons in portrait mode.
                icon.getLayoutParams().height =
                        mActivityContext.getDeviceProfile().allAppsCellHeightPx;
                return new ViewHolder(icon);
            case VIEW_TYPE_EMPTY_SEARCH:
                return new ViewHolder(mLayoutInflater.inflate(R.layout.all_apps_empty_search,
                        parent, false));
            case VIEW_TYPE_ALL_APPS_DIVIDER, VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.private_space_divider, parent, false));
            case VIEW_TYPE_WORK_EDU_CARD:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.work_apps_edu, parent, false));
            case VIEW_TYPE_WORK_DISABLED_CARD:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.work_apps_paused, parent, false));
            case VIEW_TYPE_PRIVATE_SPACE_HEADER:
                return new ViewHolder(mLayoutInflater.inflate(
                        R.layout.private_space_header, parent, false));
            default:
                if (mAdapterProvider.isViewSupported(viewType)) {
                    return mAdapterProvider.onCreateViewHolder(mLayoutInflater, parent, viewType);
                }
                throw new RuntimeException("Unexpected view type" + viewType);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.itemView.setVisibility(View.VISIBLE);
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_ICON: {
                AdapterItem adapterItem = mApps.getAdapterItems().get(position);
                BubbleTextView icon = (BubbleTextView) holder.itemView;
                icon.reset();
                icon.applyFromApplicationInfo(adapterItem.itemInfo);
                icon.setOnFocusChangeListener(mIconFocusListener);
                PrivateProfileManager privateProfileManager = mApps.getPrivateProfileManager();
                if (privateProfileManager != null) {
                    // Set the alpha of the private space icon to 0 upon expanding the header so the
                    // alpha can animate -> 1.
                    boolean isPrivateSpaceItem =
                            privateProfileManager.isPrivateSpaceItem(adapterItem);
                    if (icon.getAlpha() == 0 || icon.getAlpha() == 1) {
                        icon.setAlpha(isPrivateSpaceItem
                                && privateProfileManager.getAnimationScrolling()
                                && privateProfileManager.getAnimate()
                                && privateProfileManager.getCurrentState() == STATE_ENABLED
                                ? 0 : 1);
                    }
                    // Views can still be bounded before the app list is updated hence showing icons
                    // after collapsing.
                    if (privateProfileManager.getCurrentState() == STATE_DISABLED
                            && isPrivateSpaceItem) {
                        adapterItem.decorationInfo = null;
                        icon.setVisibility(GONE);
                    }
                }
                break;
            }
            case VIEW_TYPE_EMPTY_SEARCH: {
                AppInfo info = mApps.getAdapterItems().get(position).itemInfo;
                if (info != null) {
                    ((TextView) holder.itemView).setText(mActivityContext.getString(
                            R.string.all_apps_no_search_results, info.title));
                }
                break;
            }
            case VIEW_TYPE_PRIVATE_SPACE_HEADER:
                RelativeLayout psHeaderLayout = holder.itemView.findViewById(
                        R.id.ps_header_layout);
                mApps.getPrivateProfileManager().addPrivateSpaceHeaderViewElements(psHeaderLayout);
                AdapterItem adapterItem = mApps.getAdapterItems().get(position);
                int roundRegions = ROUND_TOP_LEFT | ROUND_TOP_RIGHT;
                if (mApps.getPrivateProfileManager().getCurrentState() == STATE_DISABLED) {
                    roundRegions |= (ROUND_BOTTOM_LEFT | ROUND_BOTTOM_RIGHT);
                }
                adapterItem.decorationInfo =
                        new SectionDecorationInfo(mActivityContext, roundRegions,
                                false /* decorateTogether */);
                break;
            case VIEW_TYPE_PRIVATE_SPACE_SYS_APPS_DIVIDER:
                adapterItem = mApps.getAdapterItems().get(position);
                adapterItem.decorationInfo = mApps.getPrivateProfileManager().getCurrentState()
                        == STATE_DISABLED ? null : new SectionDecorationInfo(mActivityContext,
                        ROUND_NOTHING, true /* decorateTogether */);
                break;
            case VIEW_TYPE_ALL_APPS_DIVIDER:
            case VIEW_TYPE_WORK_DISABLED_CARD:
                // nothing to do
                break;
            case VIEW_TYPE_WORK_EDU_CARD:
                ((WorkEduCard) holder.itemView).setPosition(position);
                break;
            default:
                if (mAdapterProvider.isViewSupported(holder.getItemViewType())) {
                    mAdapterProvider.onBindView(holder, position);
                }
        }
    }

    @Override
    public boolean onFailedToRecycleView(ViewHolder holder) {
        // Always recycle and we will reset the view when it is bound
        return true;
    }

    @Override
    public int getItemCount() {
        return mApps.getAdapterItems().size();
    }

    @Override
    public int getItemViewType(int position) {
        AdapterItem item = mApps.getAdapterItems().get(position);
        return item.viewType;
    }

    protected static boolean isViewType(int viewType, int viewTypeMask) {
        return (viewType & viewTypeMask) != 0;
    }

}
