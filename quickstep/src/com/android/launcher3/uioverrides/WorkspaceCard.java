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
import static com.android.launcher3.uioverrides.OverviewState.WORKSPACE_SCALE_ON_SCROLL;
import static com.android.quickstep.RecentsView.SCROLL_TYPE_WORKSPACE;

import android.animation.FloatArrayEvaluator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.widget.WidgetsFullSheet;
import com.android.quickstep.RecentsView;
import com.android.quickstep.RecentsView.PageCallbacks;
import com.android.quickstep.RecentsView.ScrollState;

public class WorkspaceCard extends FrameLayout implements PageCallbacks, OnClickListener {

    private final Rect mTempRect = new Rect();
    private final float[] mEvaluatedFloats = new float[3];
    private final FloatArrayEvaluator mEvaluator = new FloatArrayEvaluator(mEvaluatedFloats);

    // UI related information
    private float[] mScaleAndTranslatePage0, mScaleAndTranslatePage1;
    private boolean mUIDataValid = false;

    private Launcher mLauncher;
    private Workspace mWorkspace;

    private boolean mIsWorkspaceScrollingEnabled;

    private View mWorkspaceClickTarget;
    private View mWidgetsButton;

    public WorkspaceCard(Context context) {
        super(context);
    }

    public WorkspaceCard(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public WorkspaceCard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mWorkspaceClickTarget = findViewById(R.id.workspace_click_target);
        mWidgetsButton = findViewById(R.id.widget_button);

        mWorkspaceClickTarget.setOnClickListener(this);
        mWidgetsButton.setOnClickListener(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We measure the dimensions of the PagedView to be larger than the pages so that when we
        // zoom out (and scale down), the view is still contained in the parent
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode == MeasureSpec.UNSPECIFIED || heightMode == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        // Return early if we aren't given a proper dimension
        if (widthSize <= 0 || heightSize <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int childWidthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);

        int pageHeight = mWorkspace.getNormalChildHeight() * widthSize /
                mWorkspace.getNormalChildWidth();
        mWorkspaceClickTarget.measure(childWidthSpec,
                MeasureSpec.makeMeasureSpec(pageHeight, MeasureSpec.EXACTLY));

        int buttonHeight = heightSize - pageHeight - getPaddingTop() - getPaddingBottom();
        mWidgetsButton.measure(childWidthSpec,
                MeasureSpec.makeMeasureSpec(buttonHeight, MeasureSpec.EXACTLY));

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int y1 = getPaddingTop();
        int y2 = y1 + mWorkspaceClickTarget.getMeasuredHeight();

        mWorkspaceClickTarget.layout(getPaddingLeft(), y1,
                mWorkspaceClickTarget.getMeasuredWidth(), y2);

        mWidgetsButton.layout(getPaddingLeft(), y2, mWidgetsButton.getMeasuredWidth(),
                mWidgetsButton.getMeasuredHeight() + y2);

        mUIDataValid = false;
    }

    @Override
    public void onClick(View view) {
        if (view == mWorkspaceClickTarget) {
            mLauncher.getStateManager().goToState(NORMAL);
        } else if (view == mWidgetsButton) {
            WidgetsFullSheet.show(mLauncher, true);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mUIDataValid = false;
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
        float scale = factor * WORKSPACE_SCALE_ON_SCROLL + (1 - factor);
        setScaleX(scale);
        setScaleY(scale);

        float translateX = scrollState.distanceFromScreenCenter;
        if (mIsWorkspaceScrollingEnabled) {
            initUiData();

            mEvaluator.evaluate(factor, mScaleAndTranslatePage0, mScaleAndTranslatePage1);
            mWorkspace.setScaleX(mEvaluatedFloats[0]);
            mWorkspace.setScaleY(mEvaluatedFloats[0]);
            mWorkspace.setTranslationX(mEvaluatedFloats[1]);
            mWorkspace.setTranslationY(mEvaluatedFloats[2]);
            translateX += mEvaluatedFloats[1];
        }

        setTranslationX(translateX);

        return SCROLL_TYPE_WORKSPACE;
    }

    private void initUiData() {
        if (mUIDataValid && mScaleAndTranslatePage0 != null) {
            return;
        }

        float overlap = getResources().getDimension(R.dimen.workspace_overview_offset_x);

        RecentsView.getPageRect(mLauncher, mTempRect);
        mScaleAndTranslatePage0 = OverviewState
                .getScaleAndTranslationForPageRect(mLauncher, 0, mTempRect);
        Rect scaledDown = new Rect(mTempRect);
        Utilities.scaleRectAboutCenter(scaledDown, WORKSPACE_SCALE_ON_SCROLL);
        mScaleAndTranslatePage1 = OverviewState
                .getScaleAndTranslationForPageRect(mLauncher, overlap, scaledDown);
        mUIDataValid = true;
    }
}
