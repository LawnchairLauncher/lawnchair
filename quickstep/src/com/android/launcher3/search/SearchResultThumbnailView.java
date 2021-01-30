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


import android.app.search.SearchTarget;
import android.app.search.SearchTargetEvent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.android.launcher3.Launcher;
import com.android.launcher3.model.data.SearchActionItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.util.Themes;

import java.util.List;

/**
 * A view representing a high confidence app search result that includes shortcuts
 */
public class SearchResultThumbnailView extends androidx.appcompat.widget.AppCompatImageView
        implements SearchTargetHandler, View.OnClickListener {

    private SearchTarget mSearchTarget;

    public SearchResultThumbnailView(Context context) {
        super(context);
    }

    public SearchResultThumbnailView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SearchResultThumbnailView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setOnFocusChangeListener(Launcher.getLauncher(getContext()).getFocusHandler());
        setOnClickListener(this);
    }

    @Override
    public void apply(SearchTarget parentTarget, List<SearchTarget> children) {
        mSearchTarget = parentTarget;
        Bitmap bitmap;

        SearchActionItemInfo itemInfo = new SearchActionItemInfo(
                parentTarget.getSearchAction().getIcon(),
                parentTarget.getPackageName(),
                parentTarget.getUserHandle(),
                parentTarget.getSearchAction().getTitle());
        itemInfo.setIntent(parentTarget.getSearchAction().getIntent());
        itemInfo.setPendingIntent(parentTarget.getSearchAction().getPendingIntent());

        bitmap = ((BitmapDrawable) itemInfo.getIcon()
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

        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(null, bitmap);
        drawable.setCornerRadius(Themes.getDialogCornerRadius(getContext()));
        setImageDrawable(drawable);
    }

    @Override
    public void onClick(View view) {
        ItemClickHandler.onClickSearchAction(Launcher.getLauncher(getContext()),
                (SearchActionItemInfo) view.getTag());
        notifyEvent(getContext(), mSearchTarget.getId(), SearchTargetEvent.ACTION_LAUNCH_TOUCH);
    }
}
