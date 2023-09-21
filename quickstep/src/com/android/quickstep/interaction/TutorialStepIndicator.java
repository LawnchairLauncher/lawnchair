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
package com.android.quickstep.interaction;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.content.res.AppCompatResources;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.icons.GraphicsUtils;

/** Indicator displaying the current progress through the gesture navigation tutorial. */
public class TutorialStepIndicator extends LinearLayout {

    private static final String LOG_TAG = "TutorialStepIndicator";

    private int mCurrentStep = -1;
    private int mTotalSteps = -1;

    public TutorialStepIndicator(Context context) {
        super(context);
    }

    public TutorialStepIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TutorialStepIndicator(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public TutorialStepIndicator(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * Updates this indicator to display totalSteps indicator pills, with the first currentStep
     * pills highlighted.
     */
    public void setTutorialProgress(int currentStep, int totalSteps) {
        if (currentStep <= 0) {
            Log.w(LOG_TAG, "Current step number invalid: " + currentStep + ". Assuming step 1.");
            currentStep = 1;
        }
        if (totalSteps <= 0) {
            Log.w(LOG_TAG, "Total number of steps invalid: " + totalSteps + ". Assuming 1 step.");
            totalSteps = 1;
        }
        if (currentStep > totalSteps) {
            Log.w(LOG_TAG, "Current step number greater than the total number of steps. Assuming"
                    + " final step.");
            currentStep = totalSteps;
        }
        if (totalSteps < 2) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        mCurrentStep = currentStep;
        mTotalSteps = totalSteps;

        initializeStepIndicators();
    }

    private void initializeStepIndicators() {
        for (int i = mTotalSteps; i < getChildCount(); i++) {
            removeViewAt(i);
        }
        int activeStepIndicatorColor = GraphicsUtils.getAttrColor(
                getContext(), android.R.attr.textColorPrimary);
        int inactiveStepIndicatorColor = GraphicsUtils.getAttrColor(
                getContext(), android.R.attr.textColorSecondaryInverse);
        for (int i = 0; i < mTotalSteps; i++) {
            Drawable pageIndicatorPillDrawable = AppCompatResources.getDrawable(
                    getContext(), R.drawable.tutorial_step_indicator_pill);

            if (i >= getChildCount()) {
                ImageView pageIndicatorPill = new ImageView(getContext());
                pageIndicatorPill.setImageDrawable(pageIndicatorPillDrawable);

                LinearLayout.LayoutParams lp = new LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

                lp.setMarginStart(Utilities.dpToPx(3));
                lp.setMarginEnd(Utilities.dpToPx(3));

                addView(pageIndicatorPill, lp);
            }
            if (pageIndicatorPillDrawable != null) {
                if (i < mCurrentStep) {
                    pageIndicatorPillDrawable.setTint(activeStepIndicatorColor);
                } else {
                    pageIndicatorPillDrawable.setTint(inactiveStepIndicatorColor);
                }
            }
        }
    }
}
