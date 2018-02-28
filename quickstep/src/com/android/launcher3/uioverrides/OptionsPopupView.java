/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Outline;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.Toast;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherAnimUtils;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.anim.RevealOutlineAnimation;
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.graphics.ColorScrim;
import com.android.launcher3.widget.WidgetsFullSheet;

/**
 * Popup shown on long pressing an empty space in launcher
 */
public class OptionsPopupView extends AbstractFloatingView implements OnClickListener {

    private final float mOutlineRadius;
    private final Launcher mLauncher;
    private final PointF mTouchPoint = new PointF();

    private final ColorScrim mScrim;

    protected Animator mOpenCloseAnimator;

    public OptionsPopupView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public OptionsPopupView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mOutlineRadius = getResources().getDimension(R.dimen.bg_round_rect_radius);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), mOutlineRadius);
            }
        });

        mLauncher = Launcher.getLauncher(context);
        mScrim = ColorScrim.createExtractedColorScrim(this);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        findViewById(R.id.wallpaper_button).setOnClickListener(this);
        findViewById(R.id.widget_button).setOnClickListener(this);
        findViewById(R.id.settings_button).setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.wallpaper_button) {
            mLauncher.onClickWallpaperPicker(null);
            close(true);
        } else if (view.getId() == R.id.widget_button) {
            if (mLauncher.getPackageManager().isSafeMode()) {
                Toast.makeText(mLauncher, R.string.safemode_widget_error, Toast.LENGTH_SHORT).show();
            } else {
                WidgetsFullSheet.show(mLauncher, true /* animated */);
                close(true);
            }
        } else if (view.getId() == R.id.settings_button) {
            mLauncher.startActivity(new Intent(Intent.ACTION_APPLICATION_PREFERENCES)
                .setPackage(mLauncher.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            close(true);
        }
    }

    @Override
    public boolean onControllerInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() != MotionEvent.ACTION_DOWN) {
            return false;
        }
        if (mLauncher.getDragLayer().isEventOverView(this, ev)) {
            return false;
        }
        close(true);
        return true;
    }

    @Override
    protected void handleClose(boolean animate) {
        if (animate) {
            animateClose();
        } else {
            closeComplete();
        }
    }

    protected void animateClose() {
        if (!mIsOpen) {
            return;
        }
        mIsOpen = false;

        final AnimatorSet closeAnim = LauncherAnimUtils.createAnimatorSet();
        closeAnim.setDuration(getResources().getInteger(R.integer.config_popupOpenCloseDuration));

        // Rectangular reveal (reversed).
        final ValueAnimator revealAnim = createOpenCloseOutlineProvider()
                .createRevealAnimator(this, true);
        closeAnim.play(revealAnim);

        Animator fadeOut = ObjectAnimator.ofFloat(this, ALPHA, 0);
        fadeOut.setInterpolator(Interpolators.DEACCEL);
        closeAnim.play(fadeOut);

        Animator gradientAlpha = ObjectAnimator.ofFloat(mScrim, ColorScrim.PROGRESS, 0);
        gradientAlpha.setInterpolator(Interpolators.DEACCEL);
        closeAnim.play(gradientAlpha);

        closeAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenCloseAnimator = null;
                closeComplete();
            }
        });
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
        }
        mOpenCloseAnimator = closeAnim;
        closeAnim.start();
    }

    /**
     * Closes the popup without animation.
     */
    private void closeComplete() {
        if (mOpenCloseAnimator != null) {
            mOpenCloseAnimator.cancel();
            mOpenCloseAnimator = null;
        }
        mIsOpen = false;
        mLauncher.getDragLayer().removeView(this);
    }

    @Override
    public void logActionCommand(int command) {
        // TODO:
    }

    @Override
    protected boolean isOfType(int type) {
        return (type & TYPE_OPTIONS_POPUP) != 0;
    }

    private RoundedRectRevealOutlineProvider createOpenCloseOutlineProvider() {
        DragLayer.LayoutParams lp = (DragLayer.LayoutParams) getLayoutParams();
        Rect startRect = new Rect();
        startRect.offset((int) (mTouchPoint.x - lp.x), (int) (mTouchPoint.y - lp.y));

        Rect endRect = new Rect(0, 0, lp.width, lp.height);
        if (getOutlineProvider() instanceof RevealOutlineAnimation) {
            ((RevealOutlineAnimation) getOutlineProvider()).getOutline(endRect);
        }

        return new RoundedRectRevealOutlineProvider
                (mOutlineRadius, mOutlineRadius, startRect, endRect);
    }

    private void animateOpen() {
        mIsOpen = true;
        final AnimatorSet openAnim = LauncherAnimUtils.createAnimatorSet();
        openAnim.setDuration(getResources().getInteger(R.integer.config_popupOpenCloseDuration));

        final ValueAnimator revealAnim = createOpenCloseOutlineProvider()
                .createRevealAnimator(this, false);
        openAnim.play(revealAnim);

        Animator gradientAlpha = ObjectAnimator.ofFloat(mScrim, ColorScrim.PROGRESS, 1);
        gradientAlpha.setInterpolator(Interpolators.ACCEL);
        openAnim.play(gradientAlpha);

        mOpenCloseAnimator = openAnim;

        openAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mOpenCloseAnimator = null;
            }
        });
        openAnim.start();
    }

    public static void show(Launcher launcher, float x, float y) {
        DragLayer dl = launcher.getDragLayer();
        OptionsPopupView view = (OptionsPopupView) launcher.getLayoutInflater()
                .inflate(R.layout.longpress_options_menu, dl, false);
        DragLayer.LayoutParams lp = (DragLayer.LayoutParams) view.getLayoutParams();

        int maxWidth = dl.getWidth();
        int maxHeight = dl.getHeight();
        if (x <= 0 || y <= 0 || x >= maxWidth || y >= maxHeight) {
            x = maxWidth / 2;
            y = maxHeight / 2;
        }
        view.mTouchPoint.set(x, y);

        int height = lp.height;

        // Find a good width;
        int childCount = view.getChildCount();
        int heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST);
        int widthSpec = MeasureSpec.makeMeasureSpec(maxWidth / childCount, MeasureSpec.AT_MOST);
        int maxChildWidth = 0;

        for (int i = 0; i < childCount; i ++) {
            View child = ((ViewGroup) view.getChildAt(i)).getChildAt(0);
            child.measure(widthSpec, heightSpec);
            maxChildWidth = Math.max(maxChildWidth, child.getMeasuredWidth());
        }
        Rect insets = dl.getInsets();
        int margin = (int) (2 * view.getElevation());

        int width = Math.min(maxWidth - insets.left - insets.right - 2 * margin,
                maxChildWidth * childCount);
        lp.width = width;

        // Position is towards the finger
        lp.customPosition = true;
        lp.x = Utilities.boundToRange((int) (x - width / 2), insets.left + margin,
                maxWidth - insets.right - width - margin);
        lp.y = Utilities.boundToRange((int) (y - height / 2), insets.top + margin,
                maxHeight - insets.bottom - height - margin);

        launcher.getDragLayer().addView(view);
        view.animateOpen();
    }
}
