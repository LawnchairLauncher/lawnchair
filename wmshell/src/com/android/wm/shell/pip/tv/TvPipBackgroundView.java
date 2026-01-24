/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell.pip.tv;

import static com.android.wm.shell.pip.tv.TvPipMenuController.MODE_ALL_ACTIONS_MENU;
import static com.android.wm.shell.pip.tv.TvPipMenuController.MODE_MOVE_MENU;
import static com.android.wm.shell.pip.tv.TvPipMenuController.MODE_NO_MENU;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.protolog.ShellProtoLogGroup;

/**
 * This view is part of the Tv PiP menu. It is drawn behind the PiP surface and serves as a
 * background behind the PiP content. If the PiP content is translucent, this view is visible
 * behind it.
 * It is also used to draw the shadow behind the Tv PiP menu. The shadow intensity is determined
 * by the menu mode that the Tv PiP menu is in. See {@link TvPipMenuController.TvPipMenuMode}.
 */
class TvPipBackgroundView extends FrameLayout {
    private static final String TAG = "TvPipBackgroundView";

    private final View mBackgroundView;
    private final int mElevationNoMenu;
    private final int mElevationMoveMenu;
    private final int mElevationAllActionsMenu;
    private final int mPipMenuFadeAnimationDuration;

    private @TvPipMenuController.TvPipMenuMode int mCurrentMenuMode = MODE_NO_MENU;

    TvPipBackgroundView(@NonNull Context context) {
        super(context, null, 0, 0);
        inflate(context, R.layout.tv_pip_menu_background, this);

        mBackgroundView = findViewById(R.id.background_view);

        final Resources res = mContext.getResources();
        mElevationNoMenu = res.getDimensionPixelSize(R.dimen.pip_menu_elevation_no_menu);
        mElevationMoveMenu = res.getDimensionPixelSize(R.dimen.pip_menu_elevation_move_menu);
        mElevationAllActionsMenu =
                res.getDimensionPixelSize(R.dimen.pip_menu_elevation_all_actions_menu);
        mPipMenuFadeAnimationDuration =
                res.getInteger(R.integer.tv_window_menu_fade_animation_duration);
    }

    void transitionToMenuMode(@TvPipMenuController.TvPipMenuMode int pipMenuMode) {
        ProtoLog.d(ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE,
                "%s: transitionToMenuMode(), old menu mode = %s, new menu mode = %s",
                TAG, TvPipMenuController.getMenuModeString(mCurrentMenuMode),
                TvPipMenuController.getMenuModeString(pipMenuMode));

        if (mCurrentMenuMode == pipMenuMode) return;

        int elevation = mElevationNoMenu;
        Interpolator interpolator = TvPipInterpolators.ENTER;
        switch(pipMenuMode) {
            case MODE_NO_MENU:
                elevation = mElevationNoMenu;
                interpolator = TvPipInterpolators.EXIT;
                break;
            case MODE_MOVE_MENU:
                elevation = mElevationMoveMenu;
                break;
            case MODE_ALL_ACTIONS_MENU:
                elevation = mElevationAllActionsMenu;
                if (mCurrentMenuMode == MODE_MOVE_MENU) {
                    interpolator = TvPipInterpolators.EXIT;
                }
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown TV PiP menu mode: " + pipMenuMode);
        }

        mBackgroundView.animate()
            .translationZ(elevation)
            .setInterpolator(interpolator)
            .setDuration(mPipMenuFadeAnimationDuration)
            .start();

        mCurrentMenuMode = pipMenuMode;
    }

}
