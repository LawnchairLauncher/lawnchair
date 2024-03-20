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
package com.android.launcher3.allapps;

import static com.android.launcher3.LauncherPrefs.WORK_EDU_STEP;
import static com.android.launcher3.workprofile.PersonalWorkSlidingTabStrip.getTabWidth;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.model.StringCache;
import com.android.launcher3.views.ActivityContext;

/**
 * Work profile toggle switch shown at the bottom of AllApps work tab
 */
public class WorkEduCard extends FrameLayout implements
        View.OnClickListener,
        Animation.AnimationListener {

    private final ActivityContext mActivityContext;
    Animation mDismissAnim;
    private int mPosition = -1;

    public WorkEduCard(Context context) {
        this(context, null, 0);
    }

    public WorkEduCard(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WorkEduCard(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mActivityContext = ActivityContext.lookupContext(getContext());
        mDismissAnim = AnimationUtils.loadAnimation(context, android.R.anim.fade_out);
        mDismissAnim.setDuration(500);
        mDismissAnim.setAnimationListener(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mDismissAnim.reset();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mDismissAnim.cancel();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        findViewById(R.id.action_btn).setOnClickListener(this);

        updateStringFromCache();
    }

    @Override
    public void onClick(View view) {
        startAnimation(mDismissAnim);
        LauncherPrefs.get(getContext()).put(WORK_EDU_STEP, 1);
    }

    @Override
    public void onAnimationEnd(Animation animation) {
        removeCard();
    }

    @Override
    public void onAnimationRepeat(Animation animation) { }

    @Override
    public void onAnimationStart(Animation animation) { }

    private void removeCard() {
        if (mPosition == -1) {
            if (getParent() != null) ((ViewGroup) getParent()).removeView(WorkEduCard.this);
        } else {
            AllAppsRecyclerView rv = mActivityContext.getAppsView().mAH.get(
                    ActivityAllAppsContainerView.AdapterHolder.WORK).mRecyclerView;
            rv.getApps().getAdapterItems().remove(mPosition);
            // Remove the educard fast scroll section.
            rv.getApps().getFastScrollerSections().remove(0);
            rv.getAdapter().notifyItemRemoved(mPosition);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = MeasureSpec.getSize(widthMeasureSpec);
        findViewById(R.id.wrapper).getLayoutParams().width = getTabWidth(getContext(), size);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setPosition(int position) {
        mPosition = position;
    }

    public void updateStringFromCache() {
        StringCache cache = mActivityContext.getStringCache();
        if (cache != null) {
            TextView title = findViewById(R.id.work_apps_paused_title);
            title.setText(cache.workProfileEdu);
        }
    }
}
