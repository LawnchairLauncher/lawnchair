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
package com.android.launcher3.views;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.allapps.search.SearchEventTracker;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.icons.LauncherIcons;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

import java.util.ArrayList;

/**
 * A view representing a single people search result in all apps
 */
public class SearchResultPeopleView extends LinearLayout implements
        AllAppsSearchBarController.SearchTargetHandler {

    public static final String TARGET_TYPE_PEOPLE = "people";

    private final int mIconSize;
    private final int mButtonSize;
    private final PackageManager mPackageManager;
    private View mIconView;
    private TextView mTitleView;
    private ImageButton[] mProviderButtons = new ImageButton[3];
    private Intent mIntent;


    private SearchTarget mSearchTarget;

    public SearchResultPeopleView(Context context) {
        this(context, null, 0);
    }

    public SearchResultPeopleView(Context context,
            @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultPeopleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        DeviceProfile deviceProfile = Launcher.getLauncher(getContext()).getDeviceProfile();
        mPackageManager = getContext().getPackageManager();
        mIconSize = deviceProfile.iconSizePx;
        mButtonSize = (int) (deviceProfile.iconSizePx / 1.5f);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = findViewById(R.id.icon);
        mIconView.getLayoutParams().height = mIconSize;
        mIconView.getLayoutParams().width = mIconSize;
        mTitleView = findViewById(R.id.title);
        mProviderButtons[0] = findViewById(R.id.provider_0);
        mProviderButtons[1] = findViewById(R.id.provider_1);
        mProviderButtons[2] = findViewById(R.id.provider_2);
        for (ImageButton button : mProviderButtons) {
            button.getLayoutParams().width = mButtonSize;
            button.getLayoutParams().height = mButtonSize;
        }
        setOnClickListener(v -> handleSelection(SearchTargetEvent.SELECT));
    }

    @Override
    public void applySearchTarget(SearchTarget searchTarget) {
        mSearchTarget = searchTarget;
        Bundle payload = searchTarget.getExtras();
        mTitleView.setText(payload.getString("title"));
        mIntent = payload.getParcelable("intent");
        Bitmap contactIcon = payload.getParcelable("icon");
        try (LauncherIcons li = LauncherIcons.obtain(getContext())) {
            BitmapInfo badgeInfo = li.createBadgedIconBitmap(
                    getAppIcon(mIntent.getPackage()), Process.myUserHandle(),
                    Build.VERSION.SDK_INT);
            setIcon(li.badgeBitmap(roundBitmap(contactIcon), badgeInfo).icon, false);
        } catch (Exception e) {
            setIcon(contactIcon, true);
        }

        ArrayList<Bundle> providers = payload.getParcelableArrayList("providers");
        for (int i = 0; i < mProviderButtons.length; i++) {
            ImageButton button = mProviderButtons[i];
            if (providers != null && i < providers.size()) {
                Bundle provider = providers.get(i);
                Intent intent = provider.getParcelable("intent");
                setupProviderButton(button, provider, intent);
                UI_HELPER_EXECUTOR.post(() -> {
                    String pkg = provider.getString("package_name");
                    Drawable appIcon = getAppIcon(pkg);
                    if (appIcon != null) {
                        MAIN_EXECUTOR.post(() -> button.setImageDrawable(appIcon));
                    }
                });
                button.setVisibility(VISIBLE);
            } else {
                button.setVisibility(GONE);
            }
        }
        SearchEventTracker.INSTANCE.get(getContext()).registerWeakHandler(searchTarget, this);
    }

    /**
     * Normalizes the bitmap to look like rounded App Icon
     * TODO(b/170234747) to support styling, generate adaptive icon drawable and generate
     * bitmap from it.
     */
    private Bitmap roundBitmap(Bitmap icon) {
        final RoundedBitmapDrawable d = RoundedBitmapDrawableFactory.create(getResources(), icon);
        d.setCornerRadius(R.attr.folderIconRadius);
        d.setBounds(0, 0, mIconSize, mIconSize);
        final Bitmap bitmap = Bitmap.createBitmap(d.getBounds().width(), d.getBounds().height(),
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        d.draw(canvas);
        return bitmap;
    }

    private void setIcon(Bitmap icon, Boolean round) {
        if (round) {
            RoundedBitmapDrawable d = RoundedBitmapDrawableFactory.create(getResources(), icon);
            d.setCornerRadius(R.attr.folderIconRadius);
            d.setBounds(0, 0, mIconSize, mIconSize);
            mIconView.setBackground(d);
        } else {
            mIconView.setBackground(new BitmapDrawable(getResources(), icon));
        }
    }


    private Drawable getAppIcon(String pkg) {
        try {
            ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(
                    pkg, 0);
            return applicationInfo.loadIcon(mPackageManager);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private void setupProviderButton(ImageButton button, Bundle provider, Intent intent) {
        Launcher launcher = Launcher.getLauncher(getContext());
        button.setOnClickListener(b -> {
            launcher.startActivitySafely(b, intent, null);
            Bundle bundle = new Bundle();
            bundle.putBundle("provider", provider);
            SearchEventTracker.INSTANCE.get(getContext()).notifySearchTargetEvent(
                    new SearchTargetEvent.Builder(mSearchTarget,
                            SearchTargetEvent.CHILD_SELECT).setExtras(bundle).build());
        });
    }

    @Override
    public void handleSelection(int eventType) {
        if (mIntent != null) {
            Launcher launcher = Launcher.getLauncher(getContext());
            launcher.startActivitySafely(this, mIntent, null);
            SearchEventTracker.INSTANCE.get(getContext()).notifySearchTargetEvent(
                    new SearchTargetEvent.Builder(mSearchTarget, eventType).build());
        }
    }
}
