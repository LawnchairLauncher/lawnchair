/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.secondarydisplay;

import static android.content.Context.MODE_PRIVATE;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Process;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsStore;
import com.android.launcher3.allapps.AppInfoComparator;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.popup.SystemShortcut;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.Executors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adapter to manage pinned apps and show then in a grid.
 */
public class PinnedAppsAdapter extends BaseAdapter implements OnSharedPreferenceChangeListener {

    private static final String PINNED_APPS_KEY = "pinned_apps";

    private final SecondaryDisplayLauncher mLauncher;
    private final OnClickListener mOnClickListener;
    private final OnLongClickListener mOnLongClickListener;
    private final SharedPreferences mPrefs;
    private final AllAppsStore<SecondaryDisplayLauncher> mAllAppsList;
    private final AppInfoComparator mAppNameComparator;

    private final Set<ComponentKey> mPinnedApps = new HashSet<>();
    private final ArrayList<AppInfo> mItems = new ArrayList<>();

    public PinnedAppsAdapter(
            SecondaryDisplayLauncher launcher,
            AllAppsStore<SecondaryDisplayLauncher> allAppsStore,
            OnLongClickListener onLongClickListener) {
        mLauncher = launcher;
        mOnClickListener = launcher.getItemOnClickListener();
        mOnLongClickListener = onLongClickListener;
        mAllAppsList = allAppsStore;
        mPrefs = launcher.getSharedPreferences(PINNED_APPS_KEY, MODE_PRIVATE);
        mAppNameComparator = new AppInfoComparator(launcher);

        mAllAppsList.addUpdateListener(this::createFilteredAppsList);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (PINNED_APPS_KEY.equals(key)) {
            Executors.MODEL_EXECUTOR.submit(() -> {
                Set<ComponentKey> apps = prefs.getStringSet(key, Collections.emptySet())
                        .stream()
                        .map(this::parseComponentKey)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                Executors.MAIN_EXECUTOR.submit(() -> {
                    mPinnedApps.clear();
                    mPinnedApps.addAll(apps);
                    createFilteredAppsList();
                });
            });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return mItems.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppInfo getItem(int position) {
        return mItems.get(position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View getView(int position, View view, ViewGroup parent) {
        BubbleTextView icon;
        if (view instanceof BubbleTextView) {
            icon = (BubbleTextView) view;
        } else {
            icon = (BubbleTextView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.app_icon, parent, false);
            icon.setOnClickListener(mOnClickListener);
            icon.setOnLongClickListener(mOnLongClickListener);
            icon.setLongPressTimeoutFactor(1f);
            int padding = mLauncher.getDeviceProfile().edgeMarginPx;
            icon.setPadding(padding, padding, padding, padding);
        }

        icon.applyFromApplicationInfo(mItems.get(position));
        return icon;
    }

    private void createFilteredAppsList() {
        mItems.clear();
        mPinnedApps.stream().map(mAllAppsList::getApp)
                .filter(Objects::nonNull).forEach(mItems::add);
        mItems.sort(mAppNameComparator);
        notifyDataSetChanged();
    }

    /**
     * Initialized the pinned apps list and starts listening for changes
     */
    public void init() {
        mPrefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(mPrefs, PINNED_APPS_KEY);
    }

    /**
     * Stops listening for any pinned apps changes
     */
    public void destroy() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    /**
     * Pins or unpins apps from home screen
     */
    public void update(ItemInfo info, Function<ComponentKey, Boolean> op) {
        ComponentKey key = new ComponentKey(info.getTargetComponent(), info.user);
        if (op.apply(key)) {
            createFilteredAppsList();
            Set<ComponentKey> copy = new HashSet<>(mPinnedApps);
            Executors.MODEL_EXECUTOR.submit(() ->
                    mPrefs.edit().putStringSet(PINNED_APPS_KEY,
                                    copy.stream().map(this::encode).collect(Collectors.toSet()))
                            .apply());
        }
    }

    private ComponentKey parseComponentKey(String string) {
        try {
            String[] parts = string.split("#");
            UserHandle user;
            if (parts.length > 2) {
                user = UserCache.INSTANCE.get(mLauncher)
                        .getUserForSerialNumber(Long.parseLong(parts[2]));
            } else {
                user = Process.myUserHandle();
            }
            ComponentName cn = ComponentName.unflattenFromString(parts[0]);
            return new ComponentKey(cn, user);
        } catch (Exception e) {
            return null;
        }
    }

    private String encode(ComponentKey key) {
        return key.componentName.flattenToShortString() + "#"
                + UserCache.INSTANCE.get(mLauncher).getSerialNumberForUser(key.user);
    }

    /**
     * Returns a system shortcut to pin/unpin a shortcut
     */
    public SystemShortcut getSystemShortcut(ItemInfo info, View originalView) {
        return new PinUnPinShortcut(mLauncher, info, originalView,
                mPinnedApps.contains(new ComponentKey(info.getTargetComponent(), info.user)));
    }

    /**
     * Pins app to home screen
     */
    public void addPinnedApp(ItemInfo info) {
        update(info, mPinnedApps::add);
    }

    private class PinUnPinShortcut extends SystemShortcut<SecondaryDisplayLauncher> {

        private final boolean mIsPinned;

        PinUnPinShortcut(SecondaryDisplayLauncher target, ItemInfo info, View originalView,
                boolean isPinned) {
            super(isPinned ? R.drawable.ic_remove_no_shadow : R.drawable.ic_pin,
                    isPinned ? R.string.remove_drop_target_label : R.string.action_add_to_workspace,
                    target, info, originalView);
            mIsPinned = isPinned;
        }

        @Override
        public void onClick(View view) {
            if (mIsPinned) {
                update(mItemInfo, mPinnedApps::remove);
            } else {
                update(mItemInfo, mPinnedApps::add);
            }
            AbstractFloatingView.closeAllOpenViews(mLauncher);
        }
    }
}
