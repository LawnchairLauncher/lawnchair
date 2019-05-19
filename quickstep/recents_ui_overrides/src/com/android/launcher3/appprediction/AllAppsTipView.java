/**
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.appprediction;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.quickstep.logging.UserEventDispatcherExtension.ALL_APPS_PREDICTION_TIPS;

import android.content.Context;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.graphics.TriangleShape;
import com.android.systemui.shared.system.LauncherEventUtil;

import androidx.core.content.ContextCompat;

/**
 * All apps tip view aligned just above prediction apps, shown to users that enter all apps for the
 * first time.
 */
public class AllAppsTipView extends AbstractFloatingView {

    private static final String ALL_APPS_TIP_SEEN = "launcher.all_apps_tip_seen";
    private static final long AUTO_CLOSE_TIMEOUT_MILLIS = 10 * 1000;
    private static final long SHOW_DELAY_MS = 200;
    private static final long SHOW_DURATION_MS = 300;
    private static final long HIDE_DURATION_MS = 100;

    private final Launcher mLauncher;
    private final Handler mHandler = new Handler();

    private AllAppsTipView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    private AllAppsTipView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(LinearLayout.VERTICAL);

        mLauncher = Launcher.getLauncher(context);

        init(context);
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            close(true);
        }
        return false;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (mIsOpen) {
            if (animate) {
                animate().alpha(0f)
                        .withLayer()
                        .setStartDelay(0)
                        .setDuration(HIDE_DURATION_MS)
                        .setInterpolator(Interpolators.ACCEL)
                        .withEndAction(() -> mLauncher.getDragLayer().removeView(this))
                        .start();
            } else {
                animate().cancel();
                mLauncher.getDragLayer().removeView(this);
            }
            mLauncher.getSharedPrefs().edit().putBoolean(ALL_APPS_TIP_SEEN, true).apply();
            mIsOpen = false;
        }
    }

    @Override
    public void logActionCommand(int command) {
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_ON_BOARD_POPUP) != 0;
    }

    private void init(Context context) {
        inflate(context, R.layout.arrow_toast, this);

        TextView textView = findViewById(R.id.text);
        textView.setText(R.string.all_apps_prediction_tip);

        View dismissButton = findViewById(R.id.dismiss);
        dismissButton.setOnClickListener(view -> {
            mLauncher.getUserEventDispatcher().logActionTip(
                    LauncherEventUtil.DISMISS, ALL_APPS_PREDICTION_TIPS);
            handleClose(true);
        });

        View arrowView = findViewById(R.id.arrow);
        ViewGroup.LayoutParams arrowLp = arrowView.getLayoutParams();
        ShapeDrawable arrowDrawable = new ShapeDrawable(TriangleShape.create(
                arrowLp.width, arrowLp.height, false));
        Paint arrowPaint = arrowDrawable.getPaint();
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        arrowPaint.setColor(ContextCompat.getColor(getContext(), typedValue.resourceId));
        // The corner path effect won't be reflected in the shadow, but shouldn't be noticeable.
        arrowPaint.setPathEffect(new CornerPathEffect(
                context.getResources().getDimension(R.dimen.arrow_toast_corner_radius)));
        arrowView.setBackground(arrowDrawable);

        mIsOpen = true;

        mHandler.postDelayed(() -> handleClose(true), AUTO_CLOSE_TIMEOUT_MILLIS);
    }

    private static boolean showAllAppsTipIfNecessary(Launcher launcher) {
        FloatingHeaderView floatingHeaderView = launcher.getAppsView().getFloatingHeaderView();
        if (!floatingHeaderView.hasVisibleContent()
                || AbstractFloatingView.getOpenView(launcher,
                TYPE_ON_BOARD_POPUP | TYPE_DISCOVERY_BOUNCE) != null
                || !launcher.isInState(ALL_APPS)
                || hasSeenAllAppsTip(launcher)
                || UserManagerCompat.getInstance(launcher).isDemoUser()
                || Utilities.IS_RUNNING_IN_TEST_HARNESS) {
            return false;
        }

        AllAppsTipView allAppsTipView = new AllAppsTipView(launcher.getAppsView().getContext(),
            null);
        launcher.getDragLayer().addView(allAppsTipView);

        DragLayer.LayoutParams params = (DragLayer.LayoutParams) allAppsTipView.getLayoutParams();
        params.gravity = Gravity.CENTER_HORIZONTAL;

        int top = floatingHeaderView.findFixedRowByType(PredictionRowView.class).getTop();
        allAppsTipView.setY(top - launcher.getResources().getDimensionPixelSize(
                R.dimen.all_apps_tip_bottom_margin));

        allAppsTipView.setAlpha(0);
        allAppsTipView.animate()
                .alpha(1f)
                .withLayer()
                .setStartDelay(SHOW_DELAY_MS)
                .setDuration(SHOW_DURATION_MS)
                .setInterpolator(Interpolators.DEACCEL)
                .start();

        launcher.getUserEventDispatcher().logActionTip(
                LauncherEventUtil.VISIBLE, ALL_APPS_PREDICTION_TIPS);
        return true;
    }

    private static boolean hasSeenAllAppsTip(Launcher launcher) {
        return launcher.getSharedPrefs().getBoolean(ALL_APPS_TIP_SEEN, false);
    }

    public static void scheduleShowIfNeeded(Launcher launcher) {
        if (!hasSeenAllAppsTip(launcher)) {
            launcher.getStateManager().addStateListener(
                    new LauncherStateManager.StateListener() {
                        @Override
                        public void onStateTransitionStart(LauncherState toState) {
                        }

                        @Override
                        public void onStateTransitionComplete(LauncherState finalState) {
                            if (finalState == ALL_APPS) {
                                if (showAllAppsTipIfNecessary(launcher)) {
                                    launcher.getStateManager().removeStateListener(this);
                                }
                            }
                        }
                    });
        }
    }
}
