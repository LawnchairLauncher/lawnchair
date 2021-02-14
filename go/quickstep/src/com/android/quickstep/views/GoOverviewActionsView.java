/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.quickstep.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.quickstep.TaskOverlayFactoryGo.OverlayUICallbacksGo;

/**
 * View for showing Go-specific action buttons in Overview
 */
public final class GoOverviewActionsView extends OverviewActionsView<OverlayUICallbacksGo> {
    public GoOverviewActionsView(Context context) {
        this(context, null);
    }

    public GoOverviewActionsView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GoOverviewActionsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        if (getResources().getBoolean(R.bool.enable_niu_actions)) {
            findViewById(R.id.action_listen).setOnClickListener(this);
            findViewById(R.id.action_translate).setOnClickListener(this);
            findViewById(R.id.action_search).setOnClickListener(this);
        } else {
            findViewById(R.id.action_listen).setVisibility(View.GONE);
            findViewById(R.id.action_translate).setVisibility(View.GONE);
            findViewById(R.id.action_search).setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View view) {
        super.onClick(view);

        if (mCallbacks == null) {
            return;
        }
        int id = view.getId();
        if (id == R.id.action_listen) {
            mCallbacks.onListen();
        } else if (id == R.id.action_translate) {
            mCallbacks.onTranslate();
        } else if (id == R.id.action_search) {
            mCallbacks.onSearch();
        }
    }
}
