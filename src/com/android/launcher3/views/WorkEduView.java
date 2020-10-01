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
package com.android.launcher3.views;


import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsPagedView;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.userevent.nano.LauncherLogProto;

/**
 * On boarding flow for users right after setting up work profile
 */
public class WorkEduView extends AbstractSlideInView
        implements Insettable, StateListener<LauncherState> {

    private static final int DEFAULT_CLOSE_DURATION = 200;
    public static final String KEY_WORK_EDU_STEP = "showed_work_profile_edu";
    public static final String KEY_LEGACY_WORK_EDU_SEEN = "showed_bottom_user_education";

    private static final int WORK_EDU_NOT_STARTED = 0;
    private static final int WORK_EDU_PERSONAL_APPS = 1;
    private static final int WORK_EDU_WORK_APPS = 2;

    protected static final int FINAL_SCRIM_BG_COLOR = 0x88000000;


    private Rect mInsets = new Rect();
    private View mViewWrapper;
    private Button mProceedButton;
    private TextView mContentText;
    private AllAppsPagedView mAllAppsPagedView;

    private int mNextWorkEduStep = WORK_EDU_PERSONAL_APPS;


    public WorkEduView(Context context, AttributeSet attr) {
        this(context, attr, 0);
    }

    public WorkEduView(Context context, AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContent = this;
    }

    @Override
    protected void handleClose(boolean animate) {
        mLauncher.getSharedPrefs().edit().putInt(KEY_WORK_EDU_STEP, mNextWorkEduStep).apply();
        handleClose(true, DEFAULT_CLOSE_DURATION);
    }

    @Override
    protected void onCloseComplete() {
        super.onCloseComplete();
        mLauncher.getStateManager().removeStateListener(this);
    }

    @Override
    public void logActionCommand(int command) {
        // Since this is on-boarding popup, it is not a user controlled action.
    }

    @Override
    public int getLogContainerType() {
        return LauncherLogProto.ContainerType.TIP;
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ON_BOARD_POPUP) != 0;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mViewWrapper = findViewById(R.id.view_wrapper);
        mProceedButton = findViewById(R.id.proceed);
        mContentText = findViewById(R.id.content_text);

        // make sure layout does not shrink when we change the text
        mContentText.post(() -> mContentText.setMinLines(mContentText.getLineCount()));
        if (mLauncher.getAppsView().getContentView() instanceof AllAppsPagedView) {
            mAllAppsPagedView = (AllAppsPagedView) mLauncher.getAppsView().getContentView();
        }

        mProceedButton.setOnClickListener(view -> {
            if (mAllAppsPagedView != null) {
                mAllAppsPagedView.snapToPage(AllAppsContainerView.AdapterHolder.WORK);
            }
            goToWorkTab(true);
        });
    }

    private void goToWorkTab(boolean animate) {
        mProceedButton.setText(R.string.work_profile_edu_accept);
        if (animate) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(mContentText, ALPHA, 0);
            animator.addListener(new AnimationSuccessListener() {
                @Override
                public void onAnimationSuccess(Animator animator) {
                    mContentText.setText(mLauncher.getString(R.string.work_profile_edu_work_apps));
                    ObjectAnimator.ofFloat(mContentText, ALPHA, 1).start();
                }
            });
            animator.start();
        } else {
            mContentText.setText(mLauncher.getString(R.string.work_profile_edu_work_apps));
        }
        mNextWorkEduStep = WORK_EDU_WORK_APPS;
        mProceedButton.setOnClickListener(v -> handleClose(true));
    }

    @Override
    public void setInsets(Rect insets) {
        int leftInset = insets.left - mInsets.left;
        int rightInset = insets.right - mInsets.right;
        int bottomInset = insets.bottom - mInsets.bottom;
        mInsets.set(insets);
        setPadding(leftInset, getPaddingTop(), rightInset, 0);
        mViewWrapper.setPaddingRelative(mViewWrapper.getPaddingStart(),
                mViewWrapper.getPaddingTop(), mViewWrapper.getPaddingEnd(), bottomInset);
    }

    private void show() {
        attachToContainer();
        animateOpen();
        mLauncher.getStateManager().addStateListener(this);
    }

    @Override
    protected int getScrimColor(Context context) {
        return FINAL_SCRIM_BG_COLOR;
    }

    private void goToFirstPage() {
        if (mAllAppsPagedView != null) {
            mAllAppsPagedView.snapToPageImmediately(AllAppsContainerView.AdapterHolder.MAIN);
        }
    }

    private void animateOpen() {
        if (mIsOpen || mOpenCloseAnimator.isRunning()) {
            return;
        }
        mIsOpen = true;
        mOpenCloseAnimator.setValues(
                PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED));
        mOpenCloseAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        mOpenCloseAnimator.start();
    }

    /**
     * Checks if user has not seen onboarding UI yet and shows it when user navigates to all apps
     */
    public static StateListener<LauncherState> showEduFlowIfNeeded(Launcher launcher,
            @Nullable StateListener<LauncherState> oldListener) {
        if (oldListener != null) {
            launcher.getStateManager().removeStateListener(oldListener);
        }
        if (hasSeenLegacyEdu(launcher) || launcher.getSharedPrefs().getInt(KEY_WORK_EDU_STEP,
                WORK_EDU_NOT_STARTED) != WORK_EDU_NOT_STARTED) {
            return null;
        }

        StateListener<LauncherState> listener = new StateListener<LauncherState>() {
            @Override
            public void onStateTransitionComplete(LauncherState finalState) {
                if (finalState != LauncherState.ALL_APPS) return;
                LayoutInflater layoutInflater = LayoutInflater.from(launcher);
                WorkEduView v = (WorkEduView) layoutInflater.inflate(
                        R.layout.work_profile_edu, launcher.getDragLayer(),
                        false);
                v.show();
                v.goToFirstPage();
                launcher.getStateManager().removeStateListener(this);
            }
        };
        launcher.getStateManager().addStateListener(listener);
        return listener;
    }

    /**
     * Shows work apps edu if user had dismissed full edu flow
     */
    public static void showWorkEduIfNeeded(Launcher launcher) {
        if (hasSeenLegacyEdu(launcher) || launcher.getSharedPrefs().getInt(KEY_WORK_EDU_STEP,
                WORK_EDU_NOT_STARTED) != WORK_EDU_PERSONAL_APPS) {
            return;
        }
        LayoutInflater layoutInflater = LayoutInflater.from(launcher);
        WorkEduView v = (WorkEduView) layoutInflater.inflate(
                R.layout.work_profile_edu, launcher.getDragLayer(), false);
        v.show();
        v.goToWorkTab(false);
    }

    private static boolean hasSeenLegacyEdu(Launcher launcher) {
        return launcher.getSharedPrefs().getBoolean(KEY_LEGACY_WORK_EDU_SEEN, false);
    }

    @Override
    public void onStateTransitionComplete(LauncherState finalState) {
        close(false);
    }
}
