/*
 * Copyright 2022 The Android Open Source Project
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

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.logging.StatsLogManager.LauncherEvent.LAUNCHER_SPLIT_SELECTION_EXIT_CANCEL_BUTTON;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.content.Context;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.app.animation.Interpolators;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.statemanager.BaseState;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StatefulActivity;
import com.android.launcher3.states.StateAnimationConfig;

import com.android.quickstep.util.SplitSelectStateController;

/**
 * A rounded rectangular component containing a single TextView.
 * Appears when a split is in progress, and tells the user to select a second app to initiate
 * splitscreen.
 *
 * Appears and disappears concurrently with a FloatingTaskView.
 */
public class SplitInstructionsView extends LinearLayout {
    private static final int BOUNCE_DURATION = 250;
    private static final float BOUNCE_HEIGHT = 20;
    private static final int DURATION_DEFAULT_SPLIT_DISMISS = 350;

    private final RecentsViewContainer mContainer;
    public boolean mIsCurrentlyAnimating = false;

    public static final FloatProperty<SplitInstructionsView> UNFOLD =
            new FloatProperty<>("SplitInstructionsUnfold") {
                @Override
                public void setValue(SplitInstructionsView splitInstructionsView, float v) {
                    splitInstructionsView.setScaleY(v);
                }

                @Override
                public Float get(SplitInstructionsView splitInstructionsView) {
                    return splitInstructionsView.getScaleY();
                }
            };

    public static final FloatProperty<SplitInstructionsView> TRANSLATE_Y =
            new FloatProperty<>("SplitInstructionsTranslateY") {
                @Override
                public void setValue(SplitInstructionsView splitInstructionsView, float v) {
                    splitInstructionsView.setTranslationY(v);
                }

                @Override
                public Float get(SplitInstructionsView splitInstructionsView) {
                    return splitInstructionsView.getTranslationY();
                }
            };

    public SplitInstructionsView(Context context) {
        this(context, null);
    }

    public SplitInstructionsView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SplitInstructionsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContainer = RecentsViewContainer.containerFromContext(context);
    }

    public static SplitInstructionsView getSplitInstructionsView(RecentsViewContainer container) {
        ViewGroup dragLayer = container.getDragLayer();
        final SplitInstructionsView splitInstructionsView =
                (SplitInstructionsView) container.getLayoutInflater().inflate(
                        R.layout.split_instructions_view,
                        dragLayer,
                        false
                );
        splitInstructionsView.init();

        // Since textview overlays base view, and we sometimes manipulate the alpha of each
        // simultaneously, force overlapping rendering to false prevents redrawing of pixels,
        // improving performance at the cost of some accuracy.
        splitInstructionsView.forceHasOverlappingRendering(false);

        dragLayer.addView(splitInstructionsView);
        return splitInstructionsView;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        ensureProperRotation();
    }

    private void init() {
        TextView cancelTextView = findViewById(R.id.split_instructions_text_cancel);
        TextView instructionTextView = findViewById(R.id.split_instructions_text);

        if (FeatureFlags.enableSplitContextually()) {
            cancelTextView.setVisibility(VISIBLE);
            cancelTextView.setOnClickListener((v) -> exitSplitSelection());
            instructionTextView.setText(R.string.toast_contextual_split_select_app);
        }

        // Set accessibility title, will be announced by a11y tools.
        instructionTextView.setAccessibilityPaneTitle(instructionTextView.getText());
    }

    private void exitSplitSelection() {
        RecentsView recentsView = mContainer.getOverviewPanel();
        SplitSelectStateController splitSelectController = recentsView.getSplitSelectController();

        StateManager stateManager = recentsView.getStateManager();
        BaseState startState = stateManager.getState();
        long duration = startState.getTransitionDuration(mContainer.asContext(), false);
        if (duration == 0) {
            // Case where we're in contextual on workspace (NORMAL), which by default has 0
            // transition duration
            duration = DURATION_DEFAULT_SPLIT_DISMISS;
        }
        StateAnimationConfig config = new StateAnimationConfig();
        config.duration = duration;
        AnimatorSet stateAnim = stateManager.createAtomicAnimation(
                startState, NORMAL, config);
        AnimatorSet dismissAnim = splitSelectController.getSplitAnimationController()
                .createPlaceholderDismissAnim(mContainer,
                        LAUNCHER_SPLIT_SELECTION_EXIT_CANCEL_BUTTON, duration);
        stateAnim.play(dismissAnim);
        stateManager.setCurrentAnimation(stateAnim, NORMAL);
        stateAnim.start();
    }

    void ensureProperRotation() {
        ((RecentsView) mContainer.getOverviewPanel()).getPagedOrientationHandler()
                .setSplitInstructionsParams(
                        this,
                        mContainer.getDeviceProfile(),
                        getMeasuredHeight(),
                        getMeasuredWidth()
                );
    }

    /**
     * Draws attention to the split instructions view by bouncing it up and down.
     */
    public void goBoing() {
        if (mIsCurrentlyAnimating) {
            return;
        }

        float restingY = getTranslationY();
        float bounceToY = restingY - Utilities.dpToPx(BOUNCE_HEIGHT);
        PendingAnimation anim = new PendingAnimation(BOUNCE_DURATION);
        // Animate the view lifting up to a higher position
        anim.addFloat(this, TRANSLATE_Y, restingY, bounceToY, Interpolators.STANDARD);

        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mIsCurrentlyAnimating = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // Create a low stiffness, medium bounce spring centering at the rest position
                SpringForce spring = new SpringForce(restingY)
                        .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                        .setStiffness(SpringForce.STIFFNESS_LOW);
                // Animate the view getting pulled back to rest position by the spring
                SpringAnimation springAnim = new SpringAnimation(SplitInstructionsView.this,
                        DynamicAnimation.TRANSLATION_Y).setSpring(spring).setStartValue(bounceToY);

                springAnim.addEndListener((a, b, c, d) -> mIsCurrentlyAnimating = false);
                springAnim.start();
            }
        });

        anim.buildAnim().start();
    }
}
