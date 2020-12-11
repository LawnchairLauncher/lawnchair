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


import static com.android.launcher3.views.SearchResultIcon.REMOTE_ACTION_SHOULD_START;
import static com.android.launcher3.views.SearchResultIcon.REMOTE_ACTION_TOKEN;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.util.AttributeSet;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.android.launcher3.Launcher;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.allapps.search.SearchEventTracker;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.RemoteActionItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.util.Themes;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

/**
 * A view representing a high confidence app search result that includes shortcuts
 */
public class ThumbnailSearchResultView extends androidx.appcompat.widget.AppCompatImageView
        implements AllAppsSearchBarController.SearchTargetHandler {

    public static final String TARGET_TYPE_SCREENSHOT = "screenshot";
    public static final String TARGET_TYPE_SCREENSHOT_LEGACY = "screenshot_legacy";

    private SearchTarget mSearchTarget;

    public ThumbnailSearchResultView(Context context) {
        super(context);
    }

    public ThumbnailSearchResultView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ThumbnailSearchResultView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void handleSelection(int eventType) {
        Launcher launcher = Launcher.getLauncher(getContext());
        ItemInfo itemInfo = (ItemInfo) getTag();
        if (itemInfo instanceof RemoteActionItemInfo) {
            RemoteActionItemInfo remoteItemInfo = (RemoteActionItemInfo) itemInfo;
            ItemClickHandler.onClickRemoteAction(launcher, remoteItemInfo);
        } else {
            ItemClickHandler.onClickAppShortcut(this, (WorkspaceItemInfo) itemInfo, launcher);
        }
        SearchEventTracker.INSTANCE.get(getContext()).notifySearchTargetEvent(
                new SearchTargetEvent.Builder(mSearchTarget, eventType).build());
    }

    @Override
    public void applySearchTarget(SearchTarget target) {
        mSearchTarget = target;
        Bitmap bitmap;
        if (target.getRemoteAction() != null) {
            RemoteActionItemInfo itemInfo = new RemoteActionItemInfo(target.getRemoteAction(),
                    target.getExtras().getString(REMOTE_ACTION_TOKEN),
                    target.getExtras().getBoolean(REMOTE_ACTION_SHOULD_START));
            bitmap = ((BitmapDrawable) target.getRemoteAction().getIcon()
                    .loadDrawable(getContext())).getBitmap();
            // crop
            if (bitmap.getWidth() < bitmap.getHeight()) {
                bitmap = Bitmap.createBitmap(bitmap, 0,
                        bitmap.getHeight() / 2 - bitmap.getWidth() / 2,
                        bitmap.getWidth(), bitmap.getWidth());
            } else {
                bitmap = Bitmap.createBitmap(bitmap, bitmap.getWidth() / 2 - bitmap.getHeight() / 2,
                        0,
                        bitmap.getHeight(), bitmap.getHeight());
            }
            setTag(itemInfo);
        } else {
            bitmap = (Bitmap) target.getExtras().getParcelable("bitmap");
            WorkspaceItemInfo itemInfo = new WorkspaceItemInfo();
            itemInfo.intent = new Intent(Intent.ACTION_VIEW)
                    .setData(Uri.parse(target.getExtras().getString("uri")))
                    .setType("image/*")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            setTag(itemInfo);
        }
        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(null, bitmap);
        drawable.setCornerRadius(Themes.getDialogCornerRadius(getContext()));
        setImageDrawable(drawable);
        setOnClickListener(v -> handleSelection(SearchTargetEvent.SELECT));
        SearchEventTracker.INSTANCE.get(getContext()).registerWeakHandler(target, this);
    }
}
