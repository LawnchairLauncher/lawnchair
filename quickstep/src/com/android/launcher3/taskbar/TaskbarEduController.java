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
package com.android.launcher3.taskbar;

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.anim.Interpolators.ACCEL_2;
import static com.android.launcher3.anim.Interpolators.ACCEL_DEACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL;
import static com.android.launcher3.anim.Interpolators.FAST_OUT_SLOW_IN;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.TimeInterpolator;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.View;

import com.android.launcher3.R;
import com.android.launcher3.icons.BitmapInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.uioverrides.PredictedAppIcon;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Handles the Taskbar Education flow. */
public class TaskbarEduController implements TaskbarControllers.LoggableTaskbarController {

    private static final long WAVE_ANIM_DELAY = 250;
    private static final long WAVE_ANIM_STAGGER = 50;
    private static final long WAVE_ANIM_EACH_ICON_DURATION = 633;
    private static final long WAVE_ANIM_SLOT_MACHINE_DURATION = 1085;
    // The fraction of each icon's animation at which we reach the top point of the wave.
    private static final float WAVE_ANIM_FRACTION_TOP = 0.4f;
    // The fraction of each icon's animation at which we reach the bottom, before overshooting.
    private static final float WAVE_ANIM_FRACTION_BOTTOM = 0.9f;
    private static final TimeInterpolator WAVE_ANIM_TO_TOP_INTERPOLATOR = FAST_OUT_SLOW_IN;
    private static final TimeInterpolator WAVE_ANIM_TO_BOTTOM_INTERPOLATOR = ACCEL_2;
    private static final TimeInterpolator WAVE_ANIM_OVERSHOOT_INTERPOLATOR = DEACCEL;
    private static final TimeInterpolator WAVE_ANIM_OVERSHOOT_RETURN_INTERPOLATOR = ACCEL_DEACCEL;
    private static final float WAVE_ANIM_ICON_SCALE = 1.2f;
    // How many icons to cycle through in the slot machine (+ the original icon at each end).
    private static final int WAVE_ANIM_SLOT_MACHINE_NUM_ICONS = 3;

    private final TaskbarActivityContext mActivity;
    private final float mWaveAnimTranslationY;
    private final float mWaveAnimTranslationYReturnOvershoot;

    // Initialized in init.
    TaskbarControllers mControllers;

    private TaskbarEduView mTaskbarEduView;
    private Animator mAnim;

    public TaskbarEduController(TaskbarActivityContext activity) {
        mActivity = activity;

        final Resources resources = activity.getResources();
        mWaveAnimTranslationY = resources.getDimension(R.dimen.taskbar_edu_wave_anim_trans_y);
        mWaveAnimTranslationYReturnOvershoot = resources.getDimension(
                R.dimen.taskbar_edu_wave_anim_trans_y_return_overshoot);
    }

    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
    }

    void showEdu() {
        mActivity.setTaskbarWindowFullscreen(true);
        mActivity.getDragLayer().post(() -> {
            mTaskbarEduView = (TaskbarEduView) mActivity.getLayoutInflater().inflate(
                    R.layout.taskbar_edu, mActivity.getDragLayer(), false);
            mTaskbarEduView.init(new TaskbarEduCallbacks());
            mControllers.navbarButtonsViewController.setSlideInViewVisible(true);
            mTaskbarEduView.setOnCloseBeginListener(
                    () -> mControllers.navbarButtonsViewController.setSlideInViewVisible(false));
            mTaskbarEduView.addOnCloseListener(() -> mTaskbarEduView = null);
            mTaskbarEduView.show();
            startAnim(createWaveAnim());
        });
    }

    void hideEdu() {
        if (mTaskbarEduView != null) {
            mTaskbarEduView.close(true /* animate */);
        }
    }

    /**
     * Starts the given animation, ending the previous animation first if it's still playing.
     */
    private void startAnim(Animator anim) {
        if (mAnim != null) {
            mAnim.end();
        }
        mAnim = anim;
        mAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnim = null;
            }
        });
        mAnim.start();
    }

    /**
     * Creates a staggered "wave" animation where each icon translates and scales up in succession.
     */
    private Animator createWaveAnim() {
        AnimatorSet waveAnim = new AnimatorSet();
        View[] icons = mControllers.taskbarViewController.getIconViews();
        for (int i = 0; i < icons.length; i++) {
            View icon = icons[i];
            AnimatorSet iconAnim = new AnimatorSet();

            Keyframe[] scaleKeyframes = new Keyframe[] {
                    Keyframe.ofFloat(0, 1f),
                    Keyframe.ofFloat(WAVE_ANIM_FRACTION_TOP, WAVE_ANIM_ICON_SCALE),
                    Keyframe.ofFloat(WAVE_ANIM_FRACTION_BOTTOM, 1f),
                    Keyframe.ofFloat(1f, 1f)
            };
            scaleKeyframes[1].setInterpolator(WAVE_ANIM_TO_TOP_INTERPOLATOR);
            scaleKeyframes[2].setInterpolator(WAVE_ANIM_TO_BOTTOM_INTERPOLATOR);

            Keyframe[] translationYKeyframes = new Keyframe[] {
                    Keyframe.ofFloat(0, 0f),
                    Keyframe.ofFloat(WAVE_ANIM_FRACTION_TOP, -mWaveAnimTranslationY),
                    Keyframe.ofFloat(WAVE_ANIM_FRACTION_BOTTOM, 0f),
                    // Half of the remaining fraction overshoots, then the other half returns to 0.
                    Keyframe.ofFloat(
                            WAVE_ANIM_FRACTION_BOTTOM + (1 - WAVE_ANIM_FRACTION_BOTTOM) / 2f,
                            mWaveAnimTranslationYReturnOvershoot),
                    Keyframe.ofFloat(1f, 0f)
            };
            translationYKeyframes[1].setInterpolator(WAVE_ANIM_TO_TOP_INTERPOLATOR);
            translationYKeyframes[2].setInterpolator(WAVE_ANIM_TO_BOTTOM_INTERPOLATOR);
            translationYKeyframes[3].setInterpolator(WAVE_ANIM_OVERSHOOT_INTERPOLATOR);
            translationYKeyframes[4].setInterpolator(WAVE_ANIM_OVERSHOOT_RETURN_INTERPOLATOR);

            iconAnim.play(ObjectAnimator.ofPropertyValuesHolder(icon,
                    PropertyValuesHolder.ofKeyframe(SCALE_PROPERTY, scaleKeyframes))
                    .setDuration(WAVE_ANIM_EACH_ICON_DURATION));
            iconAnim.play(ObjectAnimator.ofPropertyValuesHolder(icon,
                    PropertyValuesHolder.ofKeyframe(View.TRANSLATION_Y, translationYKeyframes))
                    .setDuration(WAVE_ANIM_EACH_ICON_DURATION));

            if (icon instanceof PredictedAppIcon) {
                // Play slot machine animation through random icons from AllAppsList.
                PredictedAppIcon predictedAppIcon = (PredictedAppIcon) icon;
                ItemInfo itemInfo = (ItemInfo) icon.getTag();
                List<BitmapInfo> iconsToAnimate = mControllers.uiController.getAppIconsForEdu()
                        .filter(appInfo -> !TextUtils.equals(appInfo.title, itemInfo.title))
                        .map(appInfo -> appInfo.bitmap)
                        .filter(bitmap -> !bitmap.isNullOrLowRes())
                        .collect(Collectors.toList());
                // Pick n icons at random.
                Collections.shuffle(iconsToAnimate);
                if (iconsToAnimate.size() > WAVE_ANIM_SLOT_MACHINE_NUM_ICONS) {
                    iconsToAnimate = iconsToAnimate.subList(0, WAVE_ANIM_SLOT_MACHINE_NUM_ICONS);
                }
                Animator slotMachineAnim = predictedAppIcon.createSlotMachineAnim(iconsToAnimate);
                if (slotMachineAnim != null) {
                    iconAnim.play(slotMachineAnim.setDuration(WAVE_ANIM_SLOT_MACHINE_DURATION));
                }
            }

            iconAnim.setStartDelay(WAVE_ANIM_STAGGER * i);
            waveAnim.play(iconAnim);
        }
        waveAnim.setStartDelay(WAVE_ANIM_DELAY);
        return waveAnim;
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarEduController:");

        pw.println(prefix + "\tisShowingEdu=" + (mTaskbarEduView != null));
        pw.println(prefix + "\tmWaveAnimTranslationY=" + mWaveAnimTranslationY);
        pw.println(prefix + "\tmWaveAnimTranslationYReturnOvershoot="
                + mWaveAnimTranslationYReturnOvershoot);
    }

    /**
     * Callbacks for {@link TaskbarEduView} to interact with its controller.
     */
    class TaskbarEduCallbacks {
        void onPageChanged(int currentPage, int pageCount) {
            if (currentPage == 0) {
                mTaskbarEduView.updateStartButton(R.string.taskbar_edu_close,
                        v -> mTaskbarEduView.close(true /* animate */));
            } else {
                mTaskbarEduView.updateStartButton(R.string.taskbar_edu_previous,
                        v -> mTaskbarEduView.snapToPage(currentPage - 1));
            }
            if (currentPage == pageCount - 1) {
                mTaskbarEduView.updateEndButton(R.string.taskbar_edu_done,
                        v -> mTaskbarEduView.close(true /* animate */));
            } else {
                mTaskbarEduView.updateEndButton(R.string.taskbar_edu_next,
                        v -> mTaskbarEduView.snapToPage(currentPage + 1));
            }
        }
    }
}
