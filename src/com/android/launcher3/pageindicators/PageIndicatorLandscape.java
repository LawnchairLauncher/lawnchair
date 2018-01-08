/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.pageindicators;

import static com.android.launcher3.LauncherState.ALL_APPS;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.FrameLayout;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Insettable;
import com.android.launcher3.Launcher;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType;

/**
 * Simply draws the caret drawable bottom-right aligned in the view. This ensures that we can have
 * a view with as large an area as we want (for touching) while maintaining a caret of size
 * all_apps_caret_size.  Used only for the landscape layout.
 */
public class PageIndicatorLandscape extends PageIndicator implements OnClickListener, Insettable {
    // all apps pull up handle drawable.

    private final Launcher mLauncher;

    public PageIndicatorLandscape(Context context) {
        this(context, null);
    }

    public PageIndicatorLandscape(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageIndicatorLandscape(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setOnClickListener(this);
        mLauncher = Launcher.getLauncher(context);
        setOnFocusChangeListener(mLauncher.mFocusHandler);
    }

    @Override
    public void onClick(View view) {
        Launcher l = Launcher.getLauncher(getContext());
        if (!l.isInState(ALL_APPS)) {
            l.getUserEventDispatcher().logActionOnControl(
                    Action.Touch.TAP, ControlType.ALL_APPS_BUTTON);
            l.getStateManager().goToState(ALL_APPS);
        }
    }

    @Override
    public void setInsets(Rect insets) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mLauncher.getDeviceProfile();
        if (insets.left > insets.right) {
            lp.leftMargin = grid.hotseatBarSidePaddingPx;
            lp.rightMargin = insets.right;
            lp.gravity =  Gravity.RIGHT | Gravity.BOTTOM;
        } else {
            lp.leftMargin = insets.left;
            lp.rightMargin = grid.hotseatBarSidePaddingPx;
            lp.gravity = Gravity.LEFT | Gravity.BOTTOM;
        }
        lp.bottomMargin = grid.workspacePadding.bottom;
        setLayoutParams(lp);
    }
}
