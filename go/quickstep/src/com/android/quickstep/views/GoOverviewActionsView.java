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

import static android.view.View.GONE;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.Px;

import com.android.launcher3.R;
import com.android.launcher3.views.ArrowTipView;
import com.android.quickstep.TaskOverlayFactoryGo.OverlayUICallbacksGo;
import com.android.quickstep.util.RecentsOrientedState;

/**
 * View for showing Go-specific action buttons in Overview
 */
public class GoOverviewActionsView extends OverviewActionsView<OverlayUICallbacksGo> {

    @Nullable
    private ArrowTipView mArrowTipView;

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
        } else {
            findViewById(R.id.layout_listen).setVisibility(GONE);
            findViewById(R.id.spacer_listen).setVisibility(GONE);
            findViewById(R.id.layout_translate).setVisibility(GONE);
            findViewById(R.id.spacer_translate).setVisibility(GONE);
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

    /**
     * Shows Tooltip for action icons
     */
    private ArrowTipView showToolTip(int viewId, int textResourceId) {
        int[] location = new int[2];
        @Px int topMargin = getResources().getDimensionPixelSize(R.dimen.tooltip_top_margin);
        findViewById(viewId).getLocationOnScreen(location);
        mArrowTipView = new ArrowTipView(getContext(),  /* isPointingUp= */ false)
            .showAtLocation(getResources().getString(textResourceId),
                /* arrowXCoord= */ location[0] + findViewById(viewId).getWidth() / 2,
                /* yCoord= */ location[1] - topMargin,
                /* shouldAutoClose= */ false);

        mArrowTipView.bringToFront();
        return mArrowTipView;
    }

    /**
     * Shows Tooltip for listen action icon
     */
    public ArrowTipView showListenToolTip() {
        return showToolTip(/* viewId= */ R.id.action_listen,
                /* textResourceId= */ R.string.tooltip_listen);
    }

    /**
     * Shows Tooltip for translate action icon
     */
    public ArrowTipView showTranslateToolTip() {
        return showToolTip(/* viewId= */ R.id.action_translate,
                /* textResourceId= */ R.string.tooltip_translate);
    }

    /**
     * Called when device orientation is changed
     */
    public void updateOrientationState(RecentsOrientedState orientedState) {
        // dismiss tooltip
        boolean canLauncherRotate = orientedState.isRecentsActivityRotationAllowed();
        if (mArrowTipView != null && !canLauncherRotate) {
            mArrowTipView.close(/* animate= */ false);
        }
    }
}
