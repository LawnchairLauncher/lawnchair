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
package com.android.quickstep.util;

import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_HORIZONTAL_OFFSET;

import android.animation.Animator;
import android.animation.ObjectAnimator;

import androidx.dynamicanimation.animation.DynamicAnimation;

import com.android.launcher3.anim.SpringAnimationBuilder;
import com.android.launcher3.statemanager.StateManager.AtomicAnimationFactory;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.quickstep.views.RecentsView;

public class RecentsAtomicAnimationFactory<ACTIVITY_TYPE extends StatefulActivity, STATE_TYPE>
        extends AtomicAnimationFactory<STATE_TYPE> {

    public static final int INDEX_RECENTS_FADE_ANIM = AtomicAnimationFactory.NEXT_INDEX + 0;
    public static final int INDEX_RECENTS_TRANSLATE_X_ANIM = AtomicAnimationFactory.NEXT_INDEX + 1;

    private static final int MY_ANIM_COUNT = 2;

    protected final ACTIVITY_TYPE mActivity;

    public RecentsAtomicAnimationFactory(ACTIVITY_TYPE activity) {
        super(MY_ANIM_COUNT);
        mActivity = activity;
    }

    @Override
    public Animator createStateElementAnimation(int index, float... values) {
        switch (index) {
            case INDEX_RECENTS_FADE_ANIM:
                return ObjectAnimator.ofFloat(mActivity.getOverviewPanel(),
                        RecentsView.CONTENT_ALPHA, values);
            case INDEX_RECENTS_TRANSLATE_X_ANIM: {
                RecentsView rv = mActivity.getOverviewPanel();
                return new SpringAnimationBuilder(mActivity)
                        .setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE)
                        .setDampingRatio(0.8f)
                        .setStiffness(250)
                        .setValues(values)
                        .build(rv, ADJACENT_PAGE_HORIZONTAL_OFFSET);
            }
            default:
                return super.createStateElementAnimation(index, values);
        }
    }
}
