/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.quickstep.RecentsView.SCROLL_TYPE_WORKSPACE;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.quickstep.RecentsView;
import com.android.quickstep.RecentsView.PageCallbacks;
import com.android.quickstep.RecentsView.ScrollState;

public class WorkspaceCard extends View implements PageCallbacks, OnClickListener {

    private final Rect mTempRect = new Rect();

    private Launcher mLauncher;
    private Workspace mWorkspace;

    private float mLinearInterpolationForPage2 = 1;
    private float mTranslateXPage0, mTranslateXPage1;
    private float mExtraScrollShift;

    private boolean mIsWorkspaceScrollingEnabled;

    public WorkspaceCard(Context context) {
        this(context, null);
    }

    public WorkspaceCard(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkspaceCard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOnClickListener(this);
    }

    /**
     * Draw nothing.
     */
    @Override
    public void draw(Canvas canvas) { }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        // Initiate data
        mLinearInterpolationForPage2 = RecentsView.getScaledDownPageRect(
                mLauncher.getDeviceProfile(), mLauncher, mTempRect);

        float[] scale = OverviewState.getScaleAndTranslationForPageRect(mLauncher, 0, mTempRect);
        mTranslateXPage0 = scale[1];
        mTranslateXPage1 = OverviewState
                .getScaleAndTranslationForPageRect(mLauncher,
                        getResources().getDimension(R.dimen.workspace_overview_offset_x),
                        mTempRect)[1];

        mExtraScrollShift = 0;
        if (mWorkspace != null && getWidth() > 0) {
            float workspaceWidth = mWorkspace.getNormalChildWidth() * scale[0];
            mExtraScrollShift = (workspaceWidth - getWidth()) / 2;
            setScaleX(workspaceWidth / getWidth());
        }
    }

    @Override
    public void onClick(View view) {
        mLauncher.getStateManager().goToState(NORMAL);
    }

    public void setup(Launcher launcher) {
        mLauncher = launcher;
        mWorkspace = mLauncher.getWorkspace();
    }

    public void setWorkspaceScrollingEnabled(boolean isEnabled) {
        mIsWorkspaceScrollingEnabled = isEnabled;
    }

    @Override
    public int onPageScroll(ScrollState scrollState) {
        float factor = scrollState.linearInterpolation;
        float translateX = scrollState.distanceFromScreenCenter;
        if (mIsWorkspaceScrollingEnabled) {
            float shift = factor * (mTranslateXPage1 - mTranslateXPage0);
            mWorkspace.setTranslationX(shift + mTranslateXPage0);
            translateX += shift;
        }

        setTranslationX(translateX);

        // If the workspace card is still the first page, shift all the other pages.
        if (scrollState.linearInterpolation > mLinearInterpolationForPage2) {
            scrollState.prevPageExtraWidth = 0;
        } else if (mLinearInterpolationForPage2 > 0) {
            scrollState.prevPageExtraWidth = mExtraScrollShift *
                    (1 - scrollState.linearInterpolation / mLinearInterpolationForPage2);
        } else {
            scrollState.prevPageExtraWidth = mExtraScrollShift;
        }
        return SCROLL_TYPE_WORKSPACE;
    }
}
