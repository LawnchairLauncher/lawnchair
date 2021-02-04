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
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.slice.SliceItem;
import androidx.slice.widget.EventInfo;
import androidx.slice.widget.SliceView;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.model.data.PackageItemInfo;
import com.android.launcher3.util.SafeCloseable;

import java.util.ArrayList;
import java.util.List;

/**
 * A slice view wrapper with settings app icon at start
 */
public class SearchResultIconSlice extends LinearLayout implements SearchTargetHandler,
        SliceView.OnSliceActionListener {

    private final Launcher mLauncher;

    private SliceView mSliceView;
    private SearchResultIcon mIcon;
    private SafeCloseable mSliceSession;
    private String mTargetId;

    public SearchResultIconSlice(Context context) {
        this(context, null, 0);
    }

    public SearchResultIconSlice(Context context,
            @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultIconSlice(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(getContext());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSliceView = findViewById(R.id.slice);
        mIcon = findViewById(R.id.icon);
        mIcon.setTextVisibility(false);
        int iconSize = mLauncher.getDeviceProfile().iconSizePx;
        mIcon.getLayoutParams().height = iconSize;
        mIcon.getLayoutParams().width = iconSize;
    }

    @Override
    public void apply(SearchTarget parentTarget, List<SearchTarget> children) {
        mTargetId = parentTarget.getId();
        reset();
        updateIcon(parentTarget, children);
        mSliceSession = mLauncher.getLiveSearchManager()
                .addObserver(parentTarget.getSliceUri(), mSliceView);
    }

    private void updateIcon(SearchTarget parentTarget, List<SearchTarget> children) {
        if (children.size() == 1) {
            mIcon.apply(children.get(0), new ArrayList<>());
        } else {
            PackageItemInfo pkgItem = new PackageItemInfo(parentTarget.getPackageName());
            pkgItem.user = parentTarget.getUserHandle();
            if (!pkgItem.equals(mIcon.getTag())) {
                // The icon will load and apply high res icon automatically
                mIcon.applyFromItemInfoWithIcon(pkgItem);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSliceView.setOnSliceActionListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        reset();
    }

    private void reset() {
        mSliceView.setOnSliceActionListener(null);
        if (mSliceSession != null) {
            mSliceSession.close();
        }
    }

    @Override
    public void onSliceAction(@NonNull EventInfo eventInfo, @NonNull SliceItem sliceItem) {
        notifyEvent(mLauncher, mTargetId, SearchTargetEvent.ACTION_TAP);
    }
}
