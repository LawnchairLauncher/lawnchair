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

package com.android.quickstep.views;

import static com.android.launcher3.config.FeatureFlags.ENABLE_OVERVIEW_ACTIONS;
import static com.android.launcher3.config.FeatureFlags.ENABLE_OVERVIEW_SHARE;
import static com.android.quickstep.SysUINavigationMode.removeShelfFromOverview;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.MultiValueAlpha.AlphaProperty;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SysUINavigationMode.Mode;
import com.android.quickstep.TaskOverlayFactory.OverlayUICallbacks;
import com.android.quickstep.util.LayoutUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * View for showing action buttons in Overview
 */
public class OverviewActionsView<T extends OverlayUICallbacks> extends FrameLayout
        implements OnClickListener, Insettable {

    private final Rect mInsets = new Rect();

    @IntDef(flag = true, value = {
            HIDDEN_UNSUPPORTED_NAVIGATION,
            HIDDEN_DISABLED_FEATURE,
            HIDDEN_NON_ZERO_ROTATION,
            HIDDEN_NO_TASKS,
            HIDDEN_GESTURE_RUNNING,
            HIDDEN_NO_RECENTS})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ActionsHiddenFlags { }

    public static final int HIDDEN_UNSUPPORTED_NAVIGATION = 1 << 0;
    public static final int HIDDEN_DISABLED_FEATURE = 1 << 1;
    public static final int HIDDEN_NON_ZERO_ROTATION = 1 << 2;
    public static final int HIDDEN_NO_TASKS = 1 << 3;
    public static final int HIDDEN_GESTURE_RUNNING = 1 << 4;
    public static final int HIDDEN_NO_RECENTS = 1 << 5;

    @IntDef(flag = true, value = {
            DISABLED_SCROLLING,
            DISABLED_ROTATED,
            DISABLED_NO_THUMBNAIL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ActionsDisabledFlags { }

    public static final int DISABLED_SCROLLING = 1 << 0;
    public static final int DISABLED_ROTATED = 1 << 1;
    public static final int DISABLED_NO_THUMBNAIL = 1 << 2;

    private static final int INDEX_CONTENT_ALPHA = 0;
    private static final int INDEX_VISIBILITY_ALPHA = 1;
    private static final int INDEX_FULLSCREEN_ALPHA = 2;
    private static final int INDEX_HIDDEN_FLAGS_ALPHA = 3;

    private final MultiValueAlpha mMultiValueAlpha;

    @ActionsHiddenFlags
    private int mHiddenFlags;

    @ActionsDisabledFlags
    protected int mDisabledFlags;

    protected T mCallbacks;

    public OverviewActionsView(Context context) {
        this(context, null);
    }

    public OverviewActionsView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OverviewActionsView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr, 0);
        mMultiValueAlpha = new MultiValueAlpha(this, 4);
        mMultiValueAlpha.setUpdateVisibility(true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View share = findViewById(R.id.action_share);
        share.setOnClickListener(this);
        findViewById(R.id.action_screenshot).setOnClickListener(this);
        if (ENABLE_OVERVIEW_SHARE.get()) {
            share.setVisibility(VISIBLE);
            findViewById(R.id.share_space).setVisibility(VISIBLE);
        }
    }

    /**
     * Set listener for callbacks on action button taps.
     *
     * @param callbacks for callbacks, or {@code null} to clear the listener.
     */
    public void setCallbacks(T callbacks) {
        mCallbacks = callbacks;
    }

    @Override
    public void onClick(View view) {
        if (mCallbacks == null) {
            return;
        }
        int id = view.getId();
        if (id == R.id.action_share) {
            mCallbacks.onShare();
        } else if (id == R.id.action_screenshot) {
            mCallbacks.onScreenshot();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateHiddenFlags(HIDDEN_DISABLED_FEATURE, !ENABLE_OVERVIEW_ACTIONS.get());
        updateHiddenFlags(HIDDEN_UNSUPPORTED_NAVIGATION, !removeShelfFromOverview(getContext()));
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateVerticalMargin(SysUINavigationMode.getMode(getContext()));
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        updateVerticalMargin(SysUINavigationMode.getMode(getContext()));
    }

    public void updateHiddenFlags(@ActionsHiddenFlags int visibilityFlags, boolean enable) {
        if (enable) {
            mHiddenFlags |= visibilityFlags;
        } else {
            mHiddenFlags &= ~visibilityFlags;
        }
        boolean isHidden = mHiddenFlags != 0;
        mMultiValueAlpha.getProperty(INDEX_HIDDEN_FLAGS_ALPHA).setValue(isHidden ? 0 : 1);
    }

    /**
     * Updates the proper disabled flag to indicate whether OverviewActionsView should be enabled.
     * Ignores DISABLED_ROTATED flag for determining enabled. Flag is used to enable/disable
     * buttons individually, currently done for select button in subclass.
     *
     * @param disabledFlags The flag to update.
     * @param enable        Whether to enable the disable flag: True will cause view to be disabled.
     */
    public void updateDisabledFlags(@ActionsDisabledFlags int disabledFlags, boolean enable) {
        if (enable) {
            mDisabledFlags |= disabledFlags;
        } else {
            mDisabledFlags &= ~disabledFlags;
        }
        //
        boolean isEnabled = (mDisabledFlags & ~DISABLED_ROTATED) == 0;
        LayoutUtils.setViewEnabled(this, isEnabled);
    }

    public AlphaProperty getContentAlpha() {
        return mMultiValueAlpha.getProperty(INDEX_CONTENT_ALPHA);
    }

    public AlphaProperty getVisibilityAlpha() {
        return mMultiValueAlpha.getProperty(INDEX_VISIBILITY_ALPHA);
    }

    public AlphaProperty getFullscreenAlpha() {
        return mMultiValueAlpha.getProperty(INDEX_FULLSCREEN_ALPHA);
    }

    /** Updates vertical margins for different navigation mode or configuration changes. */
    public void updateVerticalMargin(Mode mode) {
        int bottomMargin;
        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            bottomMargin = 0;
        } else if (mode == Mode.THREE_BUTTONS) {
            bottomMargin = getResources()
                    .getDimensionPixelSize(R.dimen.overview_actions_bottom_margin_three_button);
        } else {
            bottomMargin = getResources()
                    .getDimensionPixelSize(R.dimen.overview_actions_bottom_margin_gesture);
        }
        bottomMargin += mInsets.bottom;
        LayoutParams params = (LayoutParams) getLayoutParams();
        params.setMargins(
                params.leftMargin, params.topMargin, params.rightMargin, bottomMargin);
    }
}
