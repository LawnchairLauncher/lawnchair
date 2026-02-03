/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wm.shell.splitscreen.tv;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_NO_MOVE_ANIMATION;
import static android.view.WindowManager.LayoutParams.PRIVATE_FLAG_TRUSTED_OVERLAY;
import static android.view.WindowManager.LayoutParams.TYPE_DOCK_DIVIDER;
import static android.view.WindowManager.SHELL_ROOT_LAYER_DIVIDER;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;
import com.android.wm.shell.common.SystemWindows;
import com.android.wm.shell.protolog.ShellProtoLogGroup;
import com.android.wm.shell.shared.split.SplitScreenConstants;

/**
 * Handles the interaction logic with the {@link TvSplitMenuView}.
 * A bridge between {@link TvStageCoordinator} and {@link TvSplitMenuView}.
 */
public class TvSplitMenuController implements TvSplitMenuView.Listener {

    private static final String TAG = TvSplitMenuController.class.getSimpleName();
    private static final String ACTION_SHOW_MENU = "com.android.wm.shell.splitscreen.SHOW_MENU";
    private static final String SYSTEMUI_PERMISSION = "com.android.systemui.permission.SELF";

    private final Context mContext;
    private final StageController mStageController;
    private final SystemWindows mSystemWindows;
    private final Handler mMainHandler;

    private final TvSplitMenuView mSplitMenuView;

    private final ActionBroadcastReceiver mActionBroadcastReceiver;

    private final int mTvButtonFadeAnimationDuration;

    public TvSplitMenuController(Context context, StageController stageController,
            SystemWindows systemWindows, Handler mainHandler) {
        mContext = context;
        mMainHandler = mainHandler;
        mStageController = stageController;
        mSystemWindows = systemWindows;

        mTvButtonFadeAnimationDuration = context.getResources()
                .getInteger(R.integer.tv_window_menu_fade_animation_duration);

        mSplitMenuView = (TvSplitMenuView) LayoutInflater.from(context)
                .inflate(R.layout.tv_split_menu_view, null);
        mSplitMenuView.setListener(this);

        mActionBroadcastReceiver = new ActionBroadcastReceiver();
    }

    /**
     * Adds the menu view for the splitscreen to SystemWindows.
     */
    void addSplitMenuViewToSystemWindows() {
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                mContext.getResources().getDisplayMetrics().widthPixels,
                mContext.getResources().getDisplayMetrics().heightPixels,
                TYPE_DOCK_DIVIDER,
                FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);
        lp.privateFlags |= PRIVATE_FLAG_NO_MOVE_ANIMATION | PRIVATE_FLAG_TRUSTED_OVERLAY;
        mSplitMenuView.setAlpha(0);
        mSystemWindows.addView(mSplitMenuView, lp, DEFAULT_DISPLAY, SHELL_ROOT_LAYER_DIVIDER);
    }

    /**
     * Removes the menu view for the splitscreen from SystemWindows.
     */
    void removeSplitMenuViewFromSystemWindows() {
        mSystemWindows.removeView(mSplitMenuView);
    }

    /**
     * Registers BroadcastReceiver when split screen mode is entered.
     */
    void registerBroadcastReceiver() {
        mActionBroadcastReceiver.register();
    }

    /**
     * Unregisters BroadcastReceiver when split screen mode is entered.
     */
    void unregisterBroadcastReceiver() {
        mActionBroadcastReceiver.unregister();
    }

    @Override
    public void onBackPress() {
        setMenuVisibility(false);
    }

    @Override
    public void onFocusStage(@SplitScreenConstants.SplitPosition int stageToFocus) {
        setMenuVisibility(false);
        mStageController.grantFocusToStage(stageToFocus);
    }

    @Override
    public void onCloseStage(@SplitScreenConstants.SplitPosition int stageToClose) {
        setMenuVisibility(false);
        mStageController.exitStage(stageToClose);
    }

    @Override
    public void onSwapPress() {
        mStageController.swapStages();
    }

    private void setMenuVisibility(boolean visible) {
        applyMenuVisibility(visible);
        setMenuFocus(visible);
    }

    private void applyMenuVisibility(boolean visible) {
        float alphaTarget = visible ? 1F : 0F;

        if (mSplitMenuView.getAlpha() == alphaTarget) {
            return;
        }

        mSplitMenuView.animate()
                .alpha(alphaTarget)
                .setDuration(mTvButtonFadeAnimationDuration)
                .withStartAction(() -> {
                    if (alphaTarget != 0) {
                        mSplitMenuView.setVisibility(VISIBLE);
                    }
                })
                .withEndAction(() -> {
                    if (alphaTarget == 0) {
                        mSplitMenuView.setVisibility(INVISIBLE);
                    }
                });

    }

    private void setMenuFocus(boolean focused) {
        try {
            WindowManagerGlobal.getWindowSession().grantEmbeddedWindowFocus(null,
                    mSystemWindows.getFocusGrantToken(mSplitMenuView), focused);
        } catch (RemoteException e) {
            ProtoLog.e(ShellProtoLogGroup.WM_SHELL_SPLIT_SCREEN,
                    "%s: Unable to update focus, %s", TAG, e);
        }
    }

    interface StageController {
        void grantFocusToStage(@SplitScreenConstants.SplitPosition  int stageToFocus);
        void exitStage(@SplitScreenConstants.SplitPosition  int stageToClose);
        void swapStages();
    }

    private class ActionBroadcastReceiver extends BroadcastReceiver {

        final IntentFilter mIntentFilter;
        {
            mIntentFilter = new IntentFilter();
            mIntentFilter.addAction(ACTION_SHOW_MENU);
        }
        boolean mRegistered = false;

        void register() {
            if (mRegistered) return;

            mContext.registerReceiverForAllUsers(this, mIntentFilter, SYSTEMUI_PERMISSION,
                    mMainHandler);
            mRegistered = true;
        }

        void unregister() {
            if (!mRegistered) return;

            mContext.unregisterReceiver(this);
            mRegistered = false;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (ACTION_SHOW_MENU.equals(action)) {
                setMenuVisibility(true);
            }
        }
    }
}
