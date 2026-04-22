/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.wm.shell.windowdecor;

import static android.view.InsetsSource.FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Handler;
import android.view.InsetsState;
import android.view.SurfaceControl;
import android.view.View;
import android.view.WindowInsets;
import android.window.DesktopModeFlags;
import android.window.WindowContainerTransaction;

import androidx.annotation.NonNull;

import com.android.wm.shell.R;
import com.android.wm.shell.ShellTaskOrganizer;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.ShellExecutor;
import com.android.wm.shell.shared.annotations.ShellBackgroundThread;
import com.android.wm.shell.shared.annotations.ShellMainThread;
import com.android.wm.shell.transition.Transitions;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost;
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier;

/**
 * {@link WindowDecoration} to show app controls for windows on automotive.
 */
public class CarWindowDecoration extends WindowDecoration<WindowDecorLinearLayout> {
    private WindowDecorLinearLayout mRootView;
    private @ShellBackgroundThread final ShellExecutor mBgExecutor;
    private final View.OnClickListener mClickListener;
    private final RelayoutParams mRelayoutParams = new RelayoutParams();
    private final RelayoutResult<WindowDecorLinearLayout> mResult = new RelayoutResult<>();

    CarWindowDecoration(
            Context context,
            @android.annotation.NonNull @ShellMainThread Handler handler,
            @android.annotation.NonNull Transitions transitions,
            @android.annotation.NonNull Context userContext,
            DisplayController displayController,
            ShellTaskOrganizer taskOrganizer,
            ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl taskSurface,
            @ShellBackgroundThread ShellExecutor bgExecutor,
            WindowDecorViewHostSupplier<WindowDecorViewHost> windowDecorViewHostSupplier,
            View.OnClickListener clickListener) {
        super(context, handler, transitions, userContext, displayController, taskOrganizer,
                taskInfo, taskSurface, windowDecorViewHostSupplier);
        mBgExecutor = bgExecutor;
        mClickListener = clickListener;
    }

    @Override
    void relayout(ActivityManager.RunningTaskInfo taskInfo, boolean hasGlobalFocus,
            @NonNull Region displayExclusionRegion) {
        final SurfaceControl.Transaction t = new SurfaceControl.Transaction();
        relayout(taskInfo, t, t);
    }

    @SuppressLint("MissingPermission")
    void relayout(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT) {
        relayout(taskInfo, startT, finishT,
                /* isCaptionVisible= */ mRelayoutParams.mIsCaptionVisible);
    }

    @SuppressLint("MissingPermission")
    void relayout(ActivityManager.RunningTaskInfo taskInfo,
            SurfaceControl.Transaction startT, SurfaceControl.Transaction finishT,
            boolean isCaptionVisible) {
        final WindowContainerTransaction wct = new WindowContainerTransaction();

        updateRelayoutParams(mRelayoutParams, taskInfo, isCaptionVisible);

        relayout(mRelayoutParams, startT, finishT, wct, mRootView, mResult);
        // After this line, mTaskInfo is up-to-date and should be used instead of taskInfo
        mBgExecutor.execute(() -> mTaskOrganizer.applyTransaction(wct));

        if (mResult.mRootView == null) {
            // This means something blocks the window decor from showing, e.g. the task is hidden.
            // Nothing is set up in this case including the decoration surface.
            return;
        }

        if (mRootView != mResult.mRootView) {
            mRootView = mResult.mRootView;
            setupRootView(mResult.mRootView, mClickListener);
        }

        if (DesktopModeFlags.ENABLE_DESKTOP_APP_HANDLE_ANIMATION.isTrue()) {
            setCaptionVisibility(mRootView, mRelayoutParams.mIsCaptionVisible);
        }
    }

    private void setCaptionVisibility(@NonNull View rootView, boolean visible) {
        final int v = visible ? View.VISIBLE : View.GONE;
        final View captionView = rootView.findViewById(getCaptionViewId());
        if (captionView != null) {
            captionView.setVisibility(v);
        }
    }

    @Override
    @NonNull
    Rect calculateValidDragArea() {
        return new Rect();
    }

    @Override
    int getCaptionViewId() {
        return R.id.caption;
    }

    private void updateRelayoutParams(
            RelayoutParams relayoutParams,
            ActivityManager.RunningTaskInfo taskInfo,
            boolean isCaptionVisible) {
        relayoutParams.reset();
        relayoutParams.mRunningTaskInfo = taskInfo;
        // todo(b/382071404): update to car specific UI
        relayoutParams.mLayoutResId = R.layout.caption_window_decor;
        relayoutParams.mCaptionHeightCalculator = (ctx, display) ->
                loadDimensionPixelSize(ctx.getResources(), R.dimen.freeform_decor_caption_height);
        relayoutParams.mIsCaptionVisible =
                isCaptionVisible && mIsStatusBarVisible && !mIsKeyguardVisibleAndOccluded;
        relayoutParams.mCaptionTopPadding = getTopPadding(taskInfo, relayoutParams);

        relayoutParams.mInsetSourceFlags |= FLAG_FORCE_CONSUMING_OPAQUE_CAPTION_BAR;
        relayoutParams.mApplyStartTransactionOnDraw = true;
    }

    private int getTopPadding(ActivityManager.RunningTaskInfo taskInfo,
            RelayoutParams relayoutParams) {
        Rect taskBounds = taskInfo.getConfiguration().windowConfiguration.getBounds();
        InsetsState insetsState = mDisplayController.getInsetsState(taskInfo.displayId);
        if (insetsState == null) {
            return relayoutParams.mCaptionTopPadding;
        }
        Insets systemDecor = insetsState.calculateInsets(taskBounds, taskBounds,
                WindowInsets.Type.systemBars() & ~WindowInsets.Type.captionBar(),
                false /* ignoreVisibility */);
        return systemDecor.top;
    }

    /**
     * Sets up listeners when a new root view is created.
     */
    private void setupRootView(View rootView, View.OnClickListener onClickListener) {
        final View caption = rootView.findViewById(R.id.caption);
        final View close = caption.findViewById(R.id.close_window);
        if (close != null) {
            close.setOnClickListener(onClickListener);
        }
        final View back = caption.findViewById(R.id.back_button);
        if (back != null) {
            back.setOnClickListener(onClickListener);
        }
    }
}
