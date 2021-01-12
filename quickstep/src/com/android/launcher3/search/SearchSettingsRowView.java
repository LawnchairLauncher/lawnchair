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
package com.android.launcher3.search;

import static com.android.launcher3.FastBitmapDrawable.newIcon;
import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.FastBitmapDrawable;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.R;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * A row of clickable TextViews with a breadcrumb for settings search.
 */
public class SearchSettingsRowView extends LinearLayout implements
        View.OnClickListener, SearchTargetHandler {

    public static final String TARGET_TYPE_SETTINGS_ROW = "settings_row";

    private View mIconView;
    private TextView mTitleView;
    private TextView mBreadcrumbsView;
    private Intent mIntent;
    private SearchTarget mSearchTarget;


    public SearchSettingsRowView(@NonNull Context context) {
        this(context, null, 0);
    }

    public SearchSettingsRowView(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchSettingsRowView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = findViewById(R.id.icon);
        mTitleView = findViewById(R.id.title);
        mBreadcrumbsView = findViewById(R.id.breadcrumbs);
        setOnClickListener(this);
        applySettingsIcon(Launcher.getLauncher(getContext()), mIconView);
    }

    @Override
    public void applySearchTarget(SearchTarget searchTarget) {
        mSearchTarget = searchTarget;
        Bundle bundle = searchTarget.getExtras();
        mIntent = bundle.getParcelable("intent");
        showIfAvailable(mTitleView, bundle.getString("title"));
        mIconView.setContentDescription(bundle.getString("title"));
        ArrayList<String> breadcrumbs = bundle.getStringArrayList("breadcrumbs");
        //TODO: implement RTL friendly breadcrumbs view
        showIfAvailable(mBreadcrumbsView, breadcrumbs != null
                ? String.join(" > ", breadcrumbs) : null);
        SearchEventTracker.INSTANCE.get(getContext()).registerWeakHandler(searchTarget, this);
    }

    private void showIfAvailable(TextView view, @Nullable String string) {
        if (TextUtils.isEmpty(string)) {
            view.setVisibility(GONE);
        } else {
            view.setVisibility(VISIBLE);
            view.setText(string);
        }
    }

    @Override
    public void onClick(View view) {
        handleSelection(SearchTargetEvent.SELECT);
    }

    @Override
    public void handleSelection(int eventType) {
        if (mIntent == null) return;
        // TODO: create ItemInfo object and then use it to call startActivityForResult for proper
        //  WW logging
        Launcher launcher = Launcher.getLauncher(getContext());
        launcher.startActivityForResult(mIntent, 0);

        SearchEventTracker.INSTANCE.get(getContext()).notifySearchTargetEvent(
                new SearchTargetEvent.Builder(mSearchTarget, eventType).build());
    }

    /**
     * Requests settings app icon from {@link com.android.launcher3.icons.IconCache} and applies
     * to to view
     */
    public static void applySettingsIcon(Launcher launcher, View view) {
        LauncherAppState appState = LauncherAppState.getInstance(launcher);
        MODEL_EXECUTOR.post(() -> {
            PackageItemInfo packageItemInfo = new PackageItemInfo(getSettingsPackageName(launcher));
            appState.getIconCache().getTitleAndIconForApp(packageItemInfo, false);
            MAIN_EXECUTOR.post(() -> {
                FastBitmapDrawable iconDrawable = newIcon(appState.getContext(), packageItemInfo);
                view.setBackground(iconDrawable);
            });
        });
    }

    private static String getSettingsPackageName(Launcher launcher) {
        Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        List<ResolveInfo> resolveInfos = launcher.getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfos.size() == 0) {
            return "";
        }
        return resolveInfos.get(0).activityInfo.packageName;
    }
}
