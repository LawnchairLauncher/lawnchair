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

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.android.launcher3.Launcher;
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItemWithPayload;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.util.Themes;
import com.android.systemui.plugins.AllAppsSearchPlugin;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

/**
 * A view representing a high confidence app search result that includes shortcuts
 */
public class ThumbnailSearchResultView extends androidx.appcompat.widget.AppCompatImageView
        implements AllAppsSearchBarController.PayloadResultHandler<Bundle> {

    private final Object[] mTargetInfo = createTargetInfo();
    Intent mIntent;
    AllAppsSearchPlugin mPlugin;
    int mPosition;

    public ThumbnailSearchResultView(Context context) {
        super(context);
    }

    public ThumbnailSearchResultView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ThumbnailSearchResultView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private void handleSelection(int eventType) {
        Launcher launcher = Launcher.getLauncher(getContext());
        launcher.startActivitySafely(this, mIntent, null);

        SearchTargetEvent event = getSearchTargetEvent(
                SearchTarget.ItemType.SCREENSHOT, eventType);
        if (mPlugin != null) {
            mPlugin.notifySearchTargetEvent(event);
        }
    }

    @Override
    public void applyAdapterInfo(AdapterItemWithPayload<Bundle> adapterItem) {
        mPosition = adapterItem.position;
        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(null,
                (Bitmap) adapterItem.getPayload().getParcelable("bitmap"));
        drawable.setCornerRadius(Themes.getDialogCornerRadius(getContext()));
        setImageDrawable(drawable);
        mIntent = new Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse(adapterItem.getPayload().getString("uri")))
                .setType("image/*")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mPlugin = adapterItem.getPlugin();
        adapterItem.setSelectionHandler(this::handleSelection);
    }

    @Override
    public Object[] getTargetInfo() {
        return mTargetInfo;
    }
}
