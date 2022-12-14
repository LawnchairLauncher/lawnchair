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
package com.android.launcher3.allapps;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.WindowInsets;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.ExtendedEditText;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;

/**
 * AllAppsContainerView with launcher specific callbacks
 */
public class LauncherAllAppsContainerView extends ActivityAllAppsContainerView<Launcher> {

    private final RecyclerView.OnScrollListener mActivityScrollListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    int scrolledOffset = recyclerView.computeVerticalScrollOffset();
                    ExtendedEditText input = mSearchUiManager.getEditText();
                    if (input != null) {
                        // Save the input box state on scroll down
                        if (dy > 0) {
                            input.saveFocusedStateAndUpdateToUnfocusedState();
                        }

                        // Scroll up and scroll to top
                        if (dy < 0 && scrolledOffset == 0) {
                            // Show keyboard
                            boolean isImeEnabledOnSwipeUp = Launcher.getLauncher(mActivityContext)
                                    .getSearchConfig().isImeEnabledOnSwipeUp();
                            if (isImeEnabledOnSwipeUp || !TextUtils.isEmpty(input.getText())) {
                                input.showKeyboard();
                            }

                            // Restore state in input box
                            input.restoreToFocusedState();
                        }
                    }
                }
            };

    @Override
    protected void onInitializeRecyclerView(RecyclerView rv) {
        super.onInitializeRecyclerView(rv);
        if (FeatureFlags.SCROLL_TOP_TO_RESET.get()) {
            rv.addOnScrollListener(mActivityScrollListener);
        }
    }

    public LauncherAllAppsContainerView(Context context) {
        this(context, null);
    }

    public LauncherAllAppsContainerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LauncherAllAppsContainerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // The AllAppsContainerView houses the QSB and is hence visible from the Workspace
        // Overview states. We shouldn't intercept for the scrubber in these cases.
        if (!mActivityContext.isInState(LauncherState.ALL_APPS)) {
            mTouchHandler = null;
            return false;
        }

        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mActivityContext.isInState(LauncherState.ALL_APPS)) {
            return false;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected int getNavBarScrimHeight(WindowInsets insets) {
        if (Utilities.ATLEAST_Q) {
            return insets.getTappableElementInsets().bottom;
        } else {
            return insets.getStableInsetBottom();
        }
    }
}
