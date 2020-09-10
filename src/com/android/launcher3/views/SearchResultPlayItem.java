/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItemWithPayload;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.systemui.plugins.AllAppsSearchPlugin;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

import java.io.IOException;
import java.net.URL;

/**
 * A View representing a PlayStore item.
 */
public class SearchResultPlayItem extends LinearLayout implements
        AllAppsSearchBarController.PayloadResultHandler<Bundle> {
    private final DeviceProfile mDeviceProfile;
    private View mIconView;
    private TextView mTitleView;
    private TextView[] mDetailViews = new TextView[3];
    private Button mPreviewButton;
    private String mPackageName;
    private boolean mIsInstantGame;
    private AllAppsSearchPlugin mPlugin;

    public SearchResultPlayItem(Context context) {
        this(context, null, 0);
    }

    public SearchResultPlayItem(Context context,
            @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultPlayItem(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mDeviceProfile = Launcher.getLauncher(getContext()).getDeviceProfile();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mIconView = findViewById(R.id.icon);
        mTitleView = findViewById(R.id.title_view);
        mPreviewButton = findViewById(R.id.try_button);
        mPreviewButton.setOnClickListener(view -> launchInstantGame());
        mDetailViews[0] = findViewById(R.id.detail_0);
        mDetailViews[1] = findViewById(R.id.detail_1);
        mDetailViews[2] = findViewById(R.id.detail_2);

        ViewGroup.LayoutParams iconParams = mIconView.getLayoutParams();
        iconParams.height = mDeviceProfile.allAppsIconSizePx;
        iconParams.width = mDeviceProfile.allAppsIconSizePx;
        setOnClickListener(view -> handleSelection(SearchTargetEvent.SELECT));

    }

    @Override
    public void applyAdapterInfo(AdapterItemWithPayload<Bundle> adapterItemWithPayload) {
        Bundle bundle = adapterItemWithPayload.getPayload();
        mPlugin = adapterItemWithPayload.getPlugin();
        adapterItemWithPayload.setSelectionHandler(this::handleSelection);
        if (bundle.getString("package", "").equals(mPackageName)) {
            return;
        }
        mIsInstantGame = bundle.getBoolean("instant_game", false);
        mPackageName = bundle.getString("package");
        mPreviewButton.setVisibility(mIsInstantGame ? VISIBLE : GONE);
        mTitleView.setText(bundle.getString("title"));
//        TODO: Should use a generic type to get values b/165320033
        showIfNecessary(mDetailViews[0], bundle.getString("price"));
        showIfNecessary(mDetailViews[1], bundle.getString("rating"));
        showIfNecessary(mDetailViews[2], bundle.getString("category"));

        mIconView.setBackgroundResource(R.drawable.ic_deepshortcut_placeholder);
        UI_HELPER_EXECUTOR.execute(() -> {
            try {
//                TODO: Handle caching
                URL url = new URL(bundle.getString("icon_url"));
                Bitmap bitmap = BitmapFactory.decodeStream(url.openStream());
                BitmapDrawable bitmapDrawable = new BitmapDrawable(getResources(),
                        Bitmap.createScaledBitmap(bitmap, mDeviceProfile.allAppsIconSizePx,
                                mDeviceProfile.allAppsIconSizePx, false));
                mIconView.post(() -> mIconView.setBackground(bitmapDrawable));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void showIfNecessary(TextView textView, @Nullable String string) {
        if (string == null || string.isEmpty()) {
            textView.setVisibility(GONE);
        } else {
            textView.setText(string);
            textView.setVisibility(VISIBLE);
        }
    }

    private void handleSelection(int eventType) {
        if (mPackageName == null) return;
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://play.google.com/store/apps/details?id="
                        + mPackageName));
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        logSearchEvent(eventType);
    }

    private void launchInstantGame() {
        if (!mIsInstantGame) return;
        Intent intent = new Intent(Intent.ACTION_VIEW);
        String referrer = "Pixel_Launcher";
        String id = mPackageName;
        String deepLinkUrl = "market://details?id=" + id + "&launch=true&referrer=" + referrer;
        intent.setPackage("com.android.vending");
        intent.setData(Uri.parse(deepLinkUrl));
        intent.putExtra("overlay", true);
        intent.putExtra("callerId", getContext().getPackageName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);
        logSearchEvent(SearchTargetEvent.CHILD_SELECT);
    }

    private void logSearchEvent(int eventType) {
        SearchTargetEvent searchTargetEvent = new SearchTargetEvent(
                SearchTarget.ItemType.PLAY_RESULTS, eventType);
        searchTargetEvent.bundle = new Bundle();
        searchTargetEvent.bundle.putString("package_name", mPackageName);
        if (mPlugin != null) {
            mPlugin.notifySearchTargetEvent(searchTargetEvent);
        }
    }
}
