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

package com.android.launcher3.dragndrop;

import static android.content.pm.LauncherApps.EXTRA_PIN_ITEM_REQUEST;

import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_EXIT_DELAY;
import static com.android.launcher3.LauncherState.EDIT_MODE;
import static com.android.launcher3.LauncherState.SPRING_LOADED;
import static com.android.launcher3.config.FeatureFlags.MULTI_SELECT_EDIT_MODE;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherApps.PinItemRequest;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.pm.PinRequestHelper;
import com.android.launcher3.pm.ShortcutConfigActivityInfo;
import com.android.launcher3.util.StartActivityParams;

import java.util.function.Supplier;

/**
 * Extension of ShortcutConfigActivityInfo to be used in the confirmation prompt for pin item
 * request.
 */
@TargetApi(Build.VERSION_CODES.O)
public class PinShortcutRequestActivityInfo extends ShortcutConfigActivityInfo {

    // Class name used in the target component, such that it will never represent an
    // actual existing class.
    private static final String STUB_COMPONENT_CLASS = "pinned-shortcut";

    private final Supplier<PinItemRequest> mRequestSupplier;
    private final ShortcutInfo mInfo;
    private final Context mContext;

    public PinShortcutRequestActivityInfo(PinItemRequest request, Context context) {
        this(request.getShortcutInfo(), () -> request, context);
    }

    public PinShortcutRequestActivityInfo(
            ShortcutInfo si, Supplier<PinItemRequest> requestSupplier, Context context) {
        super(new ComponentName(si.getPackage(), STUB_COMPONENT_CLASS), si.getUserHandle());
        mRequestSupplier = requestSupplier;
        mInfo = si;
        mContext = context;
    }

    @Override
    public int getItemType() {
        return LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT;
    }

    @Override
    public CharSequence getLabel(PackageManager pm) {
        return mInfo.getShortLabel();
    }

    @Override
    public Drawable getFullResIcon(IconCache cache) {
        Drawable d = mContext.getSystemService(LauncherApps.class)
                .getShortcutIconDrawable(mInfo, LauncherAppState.getIDP(mContext).fillResIconDpi);
        if (d == null) {
            d = cache.getDefaultIcon(Process.myUserHandle()).newIcon(mContext);
        }
        return d;
    }

    @Override
    public WorkspaceItemInfo createWorkspaceItemInfo() {
        long transitionDuration = (MULTI_SELECT_EDIT_MODE.get() ? EDIT_MODE : SPRING_LOADED)
                .getTransitionDuration(Launcher.getLauncher(mContext), true /* isToState */);
        // Total duration for the drop animation to complete.
        long duration = mContext.getResources().getInteger(R.integer.config_dropAnimMaxDuration) +
                SPRING_LOADED_EXIT_DELAY + transitionDuration;
        // Delay the actual accept() call until the drop animation is complete.
        return PinRequestHelper.createWorkspaceItemFromPinItemRequest(
                mContext, mRequestSupplier.get(), duration);
    }

    @Override
    public boolean startConfigActivity(Activity activity, int requestCode) {
        new StartActivityParams(activity, requestCode).deliverResult(
                activity,
                Activity.RESULT_OK,
                new Intent().putExtra(EXTRA_PIN_ITEM_REQUEST, mRequestSupplier.get()));
        return true;
    }

    @Override
    public boolean isPersistable() {
        return false;
    }
}
