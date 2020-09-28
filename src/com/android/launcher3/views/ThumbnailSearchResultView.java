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
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.util.AttributeSet;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.android.launcher3.Launcher;
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItemWithPayload;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.RemoteActionItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.util.Themes;
import com.android.systemui.plugins.AllAppsSearchPlugin;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

/**
 * A view representing a high confidence app search result that includes shortcuts
 */
public class ThumbnailSearchResultView extends androidx.appcompat.widget.AppCompatImageView
        implements AllAppsSearchBarController.PayloadResultHandler<SearchTarget> {

    private final Object[] mTargetInfo = createTargetInfo();
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
        ItemInfo itemInfo = (ItemInfo) getTag();
        if (itemInfo instanceof RemoteActionItemInfo) {
            RemoteActionItemInfo remoteItemInfo = (RemoteActionItemInfo) itemInfo;
            ItemClickHandler.onClickRemoteAction(launcher, remoteItemInfo);
        } else {
            ItemClickHandler.onClickAppShortcut(this, (WorkspaceItemInfo) itemInfo, launcher);
        }
        if (mPlugin != null) {
            SearchTargetEvent event = getSearchTargetEvent(
                    SearchTarget.ItemType.SCREENSHOT, eventType);
            mPlugin.notifySearchTargetEvent(event);
        }
    }

    @Override
    public void applyAdapterInfo(AdapterItemWithPayload<SearchTarget> adapterItem) {
        Launcher launcher = Launcher.getLauncher(getContext());
        mPosition = adapterItem.position;

        SearchTarget target = adapterItem.getPayload();
        Bitmap bitmap;
        if (target.mRemoteAction != null) {
            RemoteActionItemInfo itemInfo = new RemoteActionItemInfo(target.mRemoteAction,
                    target.bundle.getString(SearchTarget.REMOTE_ACTION_TOKEN),
                    target.bundle.getBoolean(SearchTarget.REMOTE_ACTION_SHOULD_START));
            ItemClickHandler.onClickRemoteAction(launcher, itemInfo);
            bitmap = ((BitmapDrawable) target.mRemoteAction.getIcon()
                    .loadDrawable(getContext())).getBitmap();
            setTag(itemInfo);
        } else {
            bitmap = (Bitmap) target.bundle.getParcelable("bitmap");
            WorkspaceItemInfo itemInfo = new WorkspaceItemInfo();
            itemInfo.intent = new Intent(Intent.ACTION_VIEW)
                    .setData(Uri.parse(target.bundle.getString("uri")))
                    .setType("image/*")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            setTag(itemInfo);
        }
        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(null, bitmap);
        drawable.setCornerRadius(Themes.getDialogCornerRadius(getContext()));
        setImageDrawable(drawable);
        setOnClickListener(v -> handleSelection(SearchTargetEvent.SELECT));
        mPlugin = adapterItem.getPlugin();
        adapterItem.setSelectionHandler(this::handleSelection);
    }

    @Override
    public Object[] getTargetInfo() {
        return mTargetInfo;
    }
}
