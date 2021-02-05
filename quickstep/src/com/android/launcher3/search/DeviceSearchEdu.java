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
package com.android.launcher3.search;

import static com.android.launcher3.util.OnboardingPrefs.SEARCH_EDU_SEEN;

import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.AbstractSlideInView;

/**
 * Feature education for on-device Search. Shown the first time user opens AllApps Search
 */
public class DeviceSearchEdu extends AbstractSlideInView implements
        StateManager.StateListener<LauncherState>, TextWatcher, Insettable,
        TextView.OnEditorActionListener {

    private static final long ANIMATION_DURATION = 350;
    private static final int ANIMATION_CONTENT_TRANSLATION = 200;

    private EditText mEduInput;

    private View mInputWrapper;
    private EditText mSearchInput;

    private boolean mSwitchFocusOnDismiss;


    public DeviceSearchEdu(Context context) {
        this(context, null, 0);
    }

    public DeviceSearchEdu(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeviceSearchEdu(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }


    private void dismiss() {
        handleClose(true);
        mLauncher.getOnboardingPrefs().markChecked(SEARCH_EDU_SEEN);
    }

    @Override
    protected void handleClose(boolean animate) {
        handleClose(animate, ANIMATION_DURATION);
        mLauncher.getStateManager().removeStateListener(this);
    }

    @Override
    protected boolean isOfType(int type) {
        return false;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mSearchInput = mLauncher.getAppsView().getSearchUiManager().getEditText();
        mInputWrapper = findViewById(R.id.search_box_wrapper);
        mContent = findViewById(R.id.edu_wrapper);

        mEduInput = findViewById(R.id.mock_search_box);
        mEduInput.setHint(R.string.all_apps_on_device_search_bar_hint);
        mEduInput.addTextChangedListener(this);
        if (mSearchInput != null) {
            mEduInput.getLayoutParams().height = mSearchInput.getHeight();
            mEduInput.setOnEditorActionListener(this);
        } else {
            mEduInput.setVisibility(INVISIBLE);
        }

        findViewById(R.id.dismiss_edu).setOnClickListener((view) -> {
            mSwitchFocusOnDismiss = true;
            dismiss();
        });
    }

    private void showInternal() {
        mLauncher.getStateManager().addStateListener(this);
        AbstractFloatingView.closeAllOpenViews(mLauncher);
        attachToContainer();
        if (mSearchInput != null) {
            Rect r = mLauncher.getViewBounds(mSearchInput);
            mEduInput.requestFocus();
            InputMethodManager imm = mLauncher.getSystemService(InputMethodManager.class);
            imm.showSoftInput(mEduInput, InputMethodManager.SHOW_IMPLICIT);
            ((LayoutParams) mInputWrapper.getLayoutParams()).setMargins(0, r.top, 0, 0);
        }
        animateOpen();
    }

    @Override
    protected int getScrimColor(Context context) {
        return ColorUtils.setAlphaComponent(Themes.getAttrColor(context, R.attr.allAppsScrimColor),
                230);
    }

    protected void setTranslationShift(float translationShift) {
        mTranslationShift = translationShift;
        mContent.setAlpha(getBoxedProgress(1 - mTranslationShift, .25f, 1));
        mContent.setTranslationY(ANIMATION_CONTENT_TRANSLATION * translationShift);
        if (mColorScrim != null) {
            mColorScrim.setAlpha(getBoxedProgress(1 - mTranslationShift, 0, .75f));
        }
    }

    /**
     * Given input [0-1], returns progress within bounds [min,max] allowing for staged animations
     */
    private float getBoxedProgress(float input, float min, float max) {
        if (input < min) return 0;
        if (input > max) return 1;
        return (input - min) / (max - min);
    }

    private void animateOpen() {
        if (mIsOpen || mOpenCloseAnimator.isRunning()) {
            return;
        }
        mIsOpen = true;
        mOpenCloseAnimator.setValues(
                PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED));
        mOpenCloseAnimator.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        mOpenCloseAnimator.setDuration(ANIMATION_DURATION);
        mOpenCloseAnimator.start();
    }

    /**
     * Show On-device search education view.
     */
    public static void show(Launcher launcher) {
        LayoutInflater layoutInflater = LayoutInflater.from(launcher);
        ((DeviceSearchEdu) layoutInflater.inflate(
                R.layout.search_edu_view, launcher.getDragLayer(),
                false)).showInternal();
    }

    @Override
    public void onStateTransitionStart(LauncherState toState) {
        dismiss();
    }

    @Override
    protected void onCloseComplete() {
        super.onCloseComplete();
        if (mSearchInput != null && mSwitchFocusOnDismiss) {
            mSearchInput.requestFocus();
            mSearchInput.setSelection(mSearchInput.getText().length());
        }
    }

    @Override
    public void afterTextChanged(Editable editable) {
        //Does nothing
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        //Does nothing
    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        if (mSearchInput != null) {
            mSearchInput.setText(charSequence.toString());
            mSwitchFocusOnDismiss = true;
            dismiss();
        }
    }

    @Override
    public void setInsets(Rect insets) {

    }

    @Override
    public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
        mSearchInput.onEditorAction(i);
        dismiss();
        return true;
    }
}
