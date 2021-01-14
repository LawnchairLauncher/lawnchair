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

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.slice.Slice;
import androidx.slice.SliceItem;
import androidx.slice.widget.EventInfo;
import androidx.slice.widget.SliceView;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

/**
 * A slice view wrapper with settings app icon at start
 */
public class SearchResultSettingsSlice extends LinearLayout implements
        SearchTargetHandler, SliceView.OnSliceActionListener {


    public static final String TARGET_TYPE_SLICE = "settings_slice";

    private static final String TAG = "SearchSliceController";
    private static final String URI_EXTRA_KEY = "slice_uri";

    private SliceView mSliceView;
    private View mIcon;
    private LiveData<Slice> mSliceLiveData;
    private SearchTarget mSearchTarget;
    private final Launcher mLauncher;

    public SearchResultSettingsSlice(Context context) {
        this(context, null, 0);
    }

    public SearchResultSettingsSlice(Context context,
            @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchResultSettingsSlice(Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mLauncher = Launcher.getLauncher(getContext());
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSliceView = findViewById(R.id.slice);
        mIcon = findViewById(R.id.icon);
        SearchSettingsRowView.applySettingsIcon(mLauncher, mIcon);
    }

    @Override
    public void applySearchTarget(SearchTarget searchTarget) {
        reset();
        mSearchTarget = searchTarget;
        try {
            mSliceLiveData = mLauncher.getLiveSearchManager().getSliceForUri(getSliceUri());
            mSliceLiveData.observe(mLauncher, mSliceView);
        } catch (Exception ex) {
            Log.e(TAG, "unable to bind slice", ex);
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

    @Override
    public void handleSelection(int eventType) {
        SearchEventTracker.INSTANCE.get(mLauncher).notifySearchTargetEvent(
                new SearchTargetEvent.Builder(mSearchTarget,
                        SearchTargetEvent.CHILD_SELECT).build());
    }

    private void reset() {
        mSliceView.setOnSliceActionListener(null);
        if (mSliceLiveData != null) {
            mSliceLiveData.removeObservers(mLauncher);
        }
    }

    @Override
    public void onSliceAction(@NonNull EventInfo eventInfo, @NonNull SliceItem sliceItem) {
        handleSelection(SearchTargetEvent.CHILD_SELECT);
    }

    private Uri getSliceUri() {
        return mSearchTarget.getExtras().getParcelable(URI_EXTRA_KEY);
    }

}
