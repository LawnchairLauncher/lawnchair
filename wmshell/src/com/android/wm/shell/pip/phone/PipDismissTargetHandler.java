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

package com.android.wm.shell.pip.phone;

import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.android.wm.shell.R;
import com.android.wm.shell.bubbles.DismissViewUtils;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.common.pip.PipUiEventLogger;
import com.android.wm.shell.shared.bubbles.DismissCircleView;
import com.android.wm.shell.shared.bubbles.DismissView;
import com.android.wm.shell.shared.magnetictarget.MagnetizedObject;

import kotlin.Unit;

/**
 * Handler of all Magnetized Object related code for PiP.
 */
public class PipDismissTargetHandler implements ViewTreeObserver.OnPreDrawListener {

    /* The multiplier to apply scale the target size by when applying the magnetic field radius */
    private static final float MAGNETIC_FIELD_RADIUS_MULTIPLIER = 1.25f;

    /**
     * MagnetizedObject wrapper for PIP. This allows the magnetic target library to locate and move
     * PIP.
     */
    private MagnetizedObject<Rect> mMagnetizedPip;

    /**
     * Container for the dismiss circle, so that it can be animated within the container via
     * translation rather than within the WindowManager via slow layout animations.
     */
    private DismissView mTargetViewContainer;

    /** Circle view used to render the dismiss target. */
    private DismissCircleView mTargetView;

    /**
     * MagneticTarget instance wrapping the target view and allowing us to set its magnetic radius.
     */
    private MagnetizedObject.MagneticTarget mMagneticTarget;

    // Allow dragging the PIP to a location to close it
    private boolean mEnableDismissDragToEdge;

    private int mTargetSize;
    private int mDismissAreaHeight;
    private float mMagneticFieldRadiusPercent = 1f;
    private WindowInsets mWindowInsets;

    private SurfaceControl mTaskLeash;
    private boolean mHasDismissTargetSurface;

    private final Context mContext;
    private final PipMotionHelper mMotionHelper;
    private final PipUiEventLogger mPipUiEventLogger;
    private final WindowManager mWindowManager;
    private final ShellExecutor mMainExecutor;

    public PipDismissTargetHandler(Context context, PipUiEventLogger pipUiEventLogger,
            PipMotionHelper motionHelper, ShellExecutor mainExecutor) {
        mContext = context;
        mPipUiEventLogger = pipUiEventLogger;
        mMotionHelper = motionHelper;
        mMainExecutor = mainExecutor;
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
    }

    public void init() {
        Resources res = mContext.getResources();
        mEnableDismissDragToEdge = res.getBoolean(R.bool.config_pipEnableDismissDragToEdge);
        mDismissAreaHeight = res.getDimensionPixelSize(R.dimen.floating_dismiss_gradient_height);

        if (mTargetViewContainer != null) {
            // init can be called multiple times, remove the old one from view hierarchy first.
            cleanUpDismissTarget();
        }

        mTargetViewContainer = new DismissView(mContext);
        DismissViewUtils.setup(mTargetViewContainer);
        mTargetView = mTargetViewContainer.getCircle();
        mTargetViewContainer.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            if (!windowInsets.equals(mWindowInsets)) {
                mWindowInsets = windowInsets;
                updateMagneticTargetSize();
            }
            return windowInsets;
        });

        mMagnetizedPip = mMotionHelper.getMagnetizedPip();
        mMagnetizedPip.clearAllTargets();
        mMagneticTarget = mMagnetizedPip.addTarget(mTargetView, 0);
        updateMagneticTargetSize();

        mMagnetizedPip.setAnimateStuckToTarget(
                (target, velX, velY, flung, after) -> {
                    if (mEnableDismissDragToEdge) {
                        mMotionHelper.animateIntoDismissTarget(target, velX, velY, flung, after);
                    }
                    return Unit.INSTANCE;
                });
        mMagnetizedPip.setMagnetListener(new MagnetizedObject.MagnetListener() {
            @Override
            public void onStuckToTarget(@NonNull MagnetizedObject.MagneticTarget target,
                    @NonNull MagnetizedObject<?> draggedObject) {
                // Show the dismiss target, in case the initial touch event occurred within
                // the magnetic field radius.
                if (mEnableDismissDragToEdge) {
                    showDismissTargetMaybe();
                }
            }

            @Override
            public void onUnstuckFromTarget(@NonNull MagnetizedObject.MagneticTarget target,
                    @NonNull MagnetizedObject<?> draggedObject,
                    float velX, float velY, boolean wasFlungOut) {
                if (wasFlungOut) {
                    mMotionHelper.flingToSnapTarget(velX, velY, null /* endAction */);
                    hideDismissTargetMaybe();
                } else {
                    mMotionHelper.setSpringingToTouch(true);
                }
            }

            @Override
            public void onReleasedInTarget(@NonNull MagnetizedObject.MagneticTarget target,
                    @NonNull MagnetizedObject<?> draggedObject) {
                if (mEnableDismissDragToEdge) {
                    mMainExecutor.executeDelayed(() -> {
                        mMotionHelper.notifyDismissalPending();
                        mMotionHelper.animateDismiss();
                        hideDismissTargetMaybe();

                        mPipUiEventLogger.log(
                                PipUiEventLogger.PipUiEventEnum.PICTURE_IN_PICTURE_DRAG_TO_REMOVE);
                    }, 0);
                }
            }
        });

    }

    @Override
    public boolean onPreDraw() {
        mTargetViewContainer.getViewTreeObserver().removeOnPreDrawListener(this);
        mHasDismissTargetSurface = true;
        updateDismissTargetLayer();
        return true;
    }

    /**
     * Potentially start consuming future motion events if PiP is currently near the magnetized
     * object.
     */
    public boolean maybeConsumeMotionEvent(MotionEvent ev) {
        return mMagnetizedPip.maybeConsumeMotionEvent(ev);
    }

    /**
     * Update the magnet size.
     */
    public void updateMagneticTargetSize() {
        if (mTargetView == null) {
            return;
        }
        if (mTargetViewContainer != null) {
            mTargetViewContainer.updateResources();
        }

        final Resources res = mContext.getResources();
        mTargetSize = res.getDimensionPixelSize(R.dimen.dismiss_circle_size);
        mDismissAreaHeight = res.getDimensionPixelSize(R.dimen.floating_dismiss_gradient_height);

        // Set the magnetic field radius equal to the target size from the center of the target
        setMagneticFieldRadiusPercent(mMagneticFieldRadiusPercent);
    }

    /**
     * Increase or decrease the field radius of the magnet object, e.g. with larger percent,
     * PiP will magnetize to the field sooner.
     */
    public void setMagneticFieldRadiusPercent(float percent) {
        mMagneticFieldRadiusPercent = percent;
        mMagneticTarget.setMagneticFieldRadiusPx((int) (mMagneticFieldRadiusPercent * mTargetSize
                        * MAGNETIC_FIELD_RADIUS_MULTIPLIER));
    }

    public void setTaskLeash(SurfaceControl taskLeash) {
        mTaskLeash = taskLeash;
    }

    private void updateDismissTargetLayer() {
        if (!mHasDismissTargetSurface || mTaskLeash == null) {
            // No dismiss target surface, can just return
            return;
        }

        final SurfaceControl targetViewLeash =
                mTargetViewContainer.getViewRootImpl().getSurfaceControl();
        if (!targetViewLeash.isValid()) {
            // The surface of mTargetViewContainer is somehow not ready, bail early
            return;
        }

        // Put the dismiss target behind the task
        SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        t.setRelativeLayer(targetViewLeash, mTaskLeash, -1);
        t.apply();
    }

    /** Adds the magnetic target view to the WindowManager so it's ready to be animated in. */
    public void createOrUpdateDismissTarget() {
        if (mTargetViewContainer.getParent() == null) {
            mTargetViewContainer.cancelAnimators();

            mTargetViewContainer.setVisibility(View.INVISIBLE);
            mTargetViewContainer.getViewTreeObserver().removeOnPreDrawListener(this);
            mHasDismissTargetSurface = false;

            mWindowManager.addView(mTargetViewContainer, getDismissTargetLayoutParams());
        } else {
            mWindowManager.updateViewLayout(mTargetViewContainer, getDismissTargetLayoutParams());
        }
    }

    /** Returns layout params for the dismiss target, using the latest display metrics. */
    private WindowManager.LayoutParams getDismissTargetLayoutParams() {
        final Point windowSize = new Point();
        mWindowManager.getDefaultDisplay().getRealSize(windowSize);
        int height = Math.min(windowSize.y, mDismissAreaHeight);
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                height,
                0, windowSize.y - height,
                WindowManager.LayoutParams.TYPE_NAVIGATION_BAR_PANEL,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        lp.setTitle("pip-dismiss-overlay");
        lp.privateFlags |= WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        lp.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        lp.setFitInsetsTypes(0 /* types */);

        return lp;
    }

    /** Makes the dismiss target visible and animates it in, if it isn't already visible. */
    public void showDismissTargetMaybe() {
        if (!mEnableDismissDragToEdge) {
            return;
        }

        createOrUpdateDismissTarget();

        if (mTargetViewContainer.getVisibility() != View.VISIBLE) {
            mTargetViewContainer.getViewTreeObserver().addOnPreDrawListener(this);
        }
        // always invoke show, since the target might still be VISIBLE while playing hide animation,
        // so we want to ensure it will show back again
        mTargetViewContainer.show();
    }

    /** Animates the magnetic dismiss target out and then sets it to GONE. */
    public void hideDismissTargetMaybe() {
        if (!mEnableDismissDragToEdge) {
            return;
        }
        mTargetViewContainer.hide();
    }

    /**
     * Removes the dismiss target and cancels any pending callbacks to show it.
     */
    public void cleanUpDismissTarget() {
        if (mTargetViewContainer.getParent() != null) {
            mWindowManager.removeViewImmediate(mTargetViewContainer);
        }
    }
}
