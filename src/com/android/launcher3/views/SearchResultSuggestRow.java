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

import static com.android.systemui.plugins.shared.SearchTarget.ItemType.SUGGEST;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItemWithPayload;
import com.android.launcher3.allapps.search.AllAppsSearchBarController;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.RemoteActionItemInfo;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.systemui.plugins.AllAppsSearchPlugin;
import com.android.systemui.plugins.shared.SearchTarget;
import com.android.systemui.plugins.shared.SearchTargetEvent;

/**
 * A view representing a fallback search suggestion row.
 */
public class SearchResultSuggestRow extends LinearLayout implements
        View.OnClickListener, AllAppsSearchBarController.PayloadResultHandler<SearchTarget> {

    private final Object[] mTargetInfo = createTargetInfo();
    private AllAppsSearchPlugin mPlugin;
    private AdapterItemWithPayload<SearchTarget> mAdapterItem;
    private TextView mTitle;


    public SearchResultSuggestRow(@NonNull Context context) {
        super(context);
    }

    public SearchResultSuggestRow(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SearchResultSuggestRow(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mTitle = findViewById(R.id.title);
        setOnClickListener(this);
    }
    @Override
    public void applyAdapterInfo(AdapterItemWithPayload<SearchTarget> adapterItemWithPayload) {
        mAdapterItem = adapterItemWithPayload;
        SearchTarget payload = adapterItemWithPayload.getPayload();
        mPlugin = adapterItemWithPayload.getPlugin();

        if (payload.mRemoteAction != null) {
            RemoteActionItemInfo itemInfo = new RemoteActionItemInfo(payload.mRemoteAction,
                    payload.bundle.getString(SearchTarget.REMOTE_ACTION_TOKEN),
                    payload.bundle.getBoolean(SearchTarget.REMOTE_ACTION_SHOULD_START));
            setTag(itemInfo);
        }
        showIfAvailable(mTitle, payload.mRemoteAction.getTitle().toString());
        setOnClickListener(v -> handleSelection(SearchTargetEvent.SELECT));
        adapterItemWithPayload.setSelectionHandler(this::handleSelection);
    }

    @Override
    public Object[] getTargetInfo() {
        return mTargetInfo;
    }

    private void handleSelection(int eventType) {
        ItemInfo itemInfo = (ItemInfo) getTag();
        Launcher launcher = Launcher.getLauncher(getContext());

        if (!(itemInfo instanceof  RemoteActionItemInfo)) return;

        RemoteActionItemInfo remoteItemInfo = (RemoteActionItemInfo) itemInfo;
        ItemClickHandler.onClickRemoteAction(launcher, remoteItemInfo);
        SearchTargetEvent searchTargetEvent = getSearchTargetEvent(SUGGEST, eventType);
        searchTargetEvent.bundle = new Bundle();
        searchTargetEvent.remoteAction = remoteItemInfo.getRemoteAction();
        searchTargetEvent.bundle.putBoolean(SearchTarget.REMOTE_ACTION_SHOULD_START,
                remoteItemInfo.shouldStartInLauncher());
        searchTargetEvent.bundle.putString(SearchTarget.REMOTE_ACTION_TOKEN,
                remoteItemInfo.getToken());

        if (mPlugin != null) {
            mPlugin.notifySearchTargetEvent(searchTargetEvent);
        }
    }

    @Override
    public void onClick(View view) {
        handleSelection(SearchTargetEvent.SELECT);
    }

    private void showIfAvailable(TextView view, @Nullable String string) {
        System.out.println("Plugin suggest string:" + string);
        if (TextUtils.isEmpty(string)) {
            view.setVisibility(GONE);
        } else {
            System.out.println("Plugin suggest string:" + string);
            view.setVisibility(VISIBLE);
            view.setText(string);
        }
    }
}
