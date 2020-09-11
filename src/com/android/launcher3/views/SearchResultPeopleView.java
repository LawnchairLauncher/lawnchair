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

import static android.content.Intent.URI_ALLOW_UNSAFE;
import static android.content.Intent.URI_ANDROID_APP_SCHEME;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
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
import com.android.launcher3.allapps.AllAppsGridAdapter;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.util.Themes;

import java.net.URISyntaxException;
import java.util.ArrayList;

/**
 * A view representing a single people search result in all apps
 */
public class SearchResultPeopleView extends LinearLayout implements
        AllAppsSearchBarController.PayloadResultHandler<Bundle> {

    private final int mIconSize;
    private final int mButtonSize;
    private final PackageManager mPackageManager;
    View mIconView;
    TextView mTitleView;
    ImageButton[] mProviderButtons = new ImageButton[3];

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
        setOnClickListener(v -> handleSelection());
    }

    @Override
    public void applyAdapterInfo(
            AllAppsGridAdapter.AdapterItemWithPayload<Bundle> adapterItemWithPayload) {
        Launcher launcher = Launcher.getLauncher(getContext());
        Bundle payload = adapterItemWithPayload.getPayload();
        mTitleView.setText(payload.getString("title"));
        mContactUri = payload.getParcelable("contact_uri");
        Bitmap icon = payload.getParcelable("icon");
        if (icon != null) {
            RoundedBitmapDrawable d = RoundedBitmapDrawableFactory.create(getResources(), icon);
            float radius = Themes.getDialogCornerRadius(getContext());
            d.setCornerRadius(radius);
            d.setBounds(0, 0, mIconSize, mIconSize);
            BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(),
                    Bitmap.createScaledBitmap(icon, mIconSize, mIconSize, false));
            mIconView.setBackground(d);
        }

        ArrayList<Bundle> providers = payload.getParcelableArrayList("providers");
        for (int i = 0; i < mProviderButtons.length; i++) {
            ImageButton button = mProviderButtons[i];
            if (providers != null && i < providers.size()) {
                try {
                    Bundle provider = providers.get(i);
                    Intent intent = Intent.parseUri(provider.getString("intent_uri_str"),
                            URI_ANDROID_APP_SCHEME | URI_ALLOW_UNSAFE);
                    String pkg = provider.getString("package_name");
                    button.setOnClickListener(b -> launcher.startActivitySafely(b, intent, null));
                    UI_HELPER_EXECUTOR.post(() -> {
                        try {
                            ApplicationInfo applicationInfo = mPackageManager.getApplicationInfo(
                                    pkg, 0);
                            Drawable appIcon = applicationInfo.loadIcon(mPackageManager);
                            MAIN_EXECUTOR.post(()-> button.setImageDrawable(appIcon));
                        } catch (PackageManager.NameNotFoundException ignored) {
                        }

                    });
                } catch (URISyntaxException ex) {
                    button.setVisibility(GONE);
                }
            } else {
                button.setVisibility(GONE);
            }
        }
        adapterItemWithPayload.setSelectionHandler(this::handleSelection);
    }

    Uri mContactUri;

    private void handleSelection() {
        if (mContactUri != null) {
            Launcher launcher = Launcher.getLauncher(getContext());
            launcher.startActivitySafely(this, new Intent(Intent.ACTION_VIEW, mContactUri).setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK), null);
        }
    }
}
