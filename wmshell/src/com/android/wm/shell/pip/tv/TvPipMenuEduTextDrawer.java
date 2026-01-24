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

package com.android.wm.shell.pip.tv;

import static android.view.Gravity.BOTTOM;
import static android.view.Gravity.CENTER;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_PICTURE_IN_PICTURE;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.text.Annotation;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannedString;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.internal.protolog.ProtoLog;
import com.android.wm.shell.R;

import java.util.Arrays;

/**
 * The edu text drawer shows the user a hint for how to access the Picture-in-Picture menu.
 * It displays a text in a drawer below the Picture-in-Picture window. The drawer has the same
 * width as the Picture-in-Picture window. Depending on the Picture-in-Picture mode, there might
 * not be enough space to fit the whole educational text in the available space. In such cases we
 * apply a marquee animation to the TextView inside the drawer.
 *
 * The drawer is shown temporarily giving the user enough time to read it, after which it slides
 * shut. We show the text for a duration calculated based on whether the text is marqueed or not.
 */
class TvPipMenuEduTextDrawer extends FrameLayout {
    private static final String TAG = "TvPipMenuEduTextDrawer";

    private static final float MARQUEE_DP_PER_SECOND = 30; // Copy of TextView.MARQUEE_DP_PER_SECOND
    private static final int MARQUEE_RESTART_DELAY = 1200; // Copy of TextView.MARQUEE_DELAY
    private final float mMarqueeAnimSpeed; // pixels per ms

    private final Runnable mCloseDrawerRunnable = this::closeDrawer;
    private final Runnable mStartScrollEduTextRunnable = this::startScrollEduText;

    private final Handler mMainHandler;
    private final Listener mListener;
    private final TextView mEduTextView;

    TvPipMenuEduTextDrawer(@NonNull Context context, Handler mainHandler, Listener listener) {
        super(context, null, 0, 0);

        mListener = listener;
        mMainHandler = mainHandler;

        // Taken from TextView.Marquee calculation
        mMarqueeAnimSpeed =
            (MARQUEE_DP_PER_SECOND * context.getResources().getDisplayMetrics().density) / 1000f;

        mEduTextView = new TextView(mContext);
        setupDrawer();
    }

    private void setupDrawer() {
        final int eduTextHeight = mContext.getResources().getDimensionPixelSize(
                R.dimen.pip_menu_edu_text_view_height);
        final int marqueeRepeatLimit = mContext.getResources()
                .getInteger(R.integer.pip_edu_text_scroll_times);

        mEduTextView.setLayoutParams(
                new LayoutParams(MATCH_PARENT, eduTextHeight, BOTTOM | CENTER));
        mEduTextView.setGravity(CENTER);
        mEduTextView.setClickable(false);
        mEduTextView.setText(createEduTextString());
        mEduTextView.setSingleLine();
        mEduTextView.setTextAppearance(R.style.TvPipEduText);
        mEduTextView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        mEduTextView.setMarqueeRepeatLimit(marqueeRepeatLimit);
        mEduTextView.setHorizontallyScrolling(true);
        mEduTextView.setHorizontalFadingEdgeEnabled(true);
        mEduTextView.setSelected(false);
        addView(mEduTextView);

        setLayoutParams(new LayoutParams(MATCH_PARENT, eduTextHeight, CENTER));
        setClipChildren(true);
    }

    /**
     * Initializes the edu text. Should only be called once when the PiP is entered
     */
    void init() {
        ProtoLog.i(WM_SHELL_PICTURE_IN_PICTURE, "%s: init()", TAG);
        scheduleLifecycleEvents();
    }

    int getEduTextDrawerHeight() {
        return getVisibility() == GONE ? 0 : getHeight();
    }

    private void scheduleLifecycleEvents() {
        final int startScrollDelay = mContext.getResources().getInteger(
                R.integer.pip_edu_text_start_scroll_delay);
        if (isEduTextMarqueed()) {
            mMainHandler.postDelayed(mStartScrollEduTextRunnable, startScrollDelay);
        }
        mMainHandler.postDelayed(mCloseDrawerRunnable, startScrollDelay + getEduTextShowDuration());
        mEduTextView.getViewTreeObserver().addOnWindowAttachListener(
                    new ViewTreeObserver.OnWindowAttachListener() {
                @Override
                public void onWindowAttached() {
                }

                @Override
                public void onWindowDetached() {
                    mEduTextView.getViewTreeObserver().removeOnWindowAttachListener(this);
                    mMainHandler.removeCallbacks(mStartScrollEduTextRunnable);
                    mMainHandler.removeCallbacks(mCloseDrawerRunnable);
                }
            });
    }

    private int getEduTextShowDuration() {
        int eduTextShowDuration;
        if (isEduTextMarqueed()) {
            // Calculate the time it takes to fully scroll the text once: time = distance / speed
            final float singleMarqueeDuration =
                    getMarqueeAnimEduTextLineWidth() / mMarqueeAnimSpeed;
            // The TextView adds a delay between each marquee repetition. Take that into account
            final float durationFromStartToStart = singleMarqueeDuration + MARQUEE_RESTART_DELAY;
            // Finally, multiply by the number of times we repeat the marquee animation
            eduTextShowDuration =
                    (int) durationFromStartToStart * mEduTextView.getMarqueeRepeatLimit();
        } else {
            eduTextShowDuration = mContext.getResources()
                    .getInteger(R.integer.pip_edu_text_non_scroll_show_duration);
        }

        ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE, "%s: getEduTextShowDuration(), showDuration=%d",
                TAG, eduTextShowDuration);
        return eduTextShowDuration;
    }

    /**
     * Returns true if the edu text width is bigger than the width of the text view, which indicates
     * that the edu text will be marqueed
     */
    private boolean isEduTextMarqueed() {
        if (mEduTextView.getLayout() == null) {
            return false;
        }
        final int availableWidth = (int) mEduTextView.getWidth()
                - mEduTextView.getCompoundPaddingLeft()
                - mEduTextView.getCompoundPaddingRight();
        return availableWidth < getEduTextWidth();
    }

    /**
     * Returns the width of a single marquee repetition of the edu text in pixels.
     * This is the width from the start of the edu text to the start of the next edu
     * text when it is marqueed.
     *
     * This is calculated based on the TextView.Marquee#start calculations
     */
    private float getMarqueeAnimEduTextLineWidth() {
        // When the TextView has a marquee animation, it puts a gap between the text end and the
        // start of the next edu text repetition. The space is equal to a third of the TextView
        // width
        final float gap = mEduTextView.getWidth() / 3.0f;
        return getEduTextWidth() + gap;
    }

    private void startScrollEduText() {
        ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE, "%s: startScrollEduText(), repeat=%d",
                TAG, mEduTextView.getMarqueeRepeatLimit());
        mEduTextView.setSelected(true);
    }

    /**
     * Returns the width of the edu text irrespective of the TextView width
     */
    private int getEduTextWidth() {
        return (int) mEduTextView.getLayout().getLineWidth(0);
    }

    /**
     * Closes the edu text drawer if it hasn't been closed yet
     */
    void closeIfNeeded() {
        if (mMainHandler.hasCallbacks(mCloseDrawerRunnable)) {
            ProtoLog.d(WM_SHELL_PICTURE_IN_PICTURE,
                    "%s: close(), closing the edu text drawer because of user action", TAG);
            mMainHandler.removeCallbacks(mCloseDrawerRunnable);
            mCloseDrawerRunnable.run();
        } else {
            // Do nothing, the drawer has already been closed
        }
    }

    private void closeDrawer() {
        ProtoLog.i(WM_SHELL_PICTURE_IN_PICTURE, "%s: closeDrawer()", TAG);
        final int eduTextFadeExitAnimationDuration = mContext.getResources().getInteger(
                R.integer.pip_edu_text_view_exit_animation_duration);
        final int eduTextSlideExitAnimationDuration = mContext.getResources().getInteger(
                R.integer.pip_edu_text_window_exit_animation_duration);

        // Start fading out the edu text
        mEduTextView.animate()
                .alpha(0f)
                .setInterpolator(TvPipInterpolators.EXIT)
                .setDuration(eduTextFadeExitAnimationDuration)
                .start();

        // Start animation to close the drawer by animating its height to 0
        final ValueAnimator heightAnimator = ValueAnimator.ofInt(getHeight(), 0);
        heightAnimator.setDuration(eduTextSlideExitAnimationDuration);
        heightAnimator.setInterpolator(TvPipInterpolators.BROWSE);
        heightAnimator.addUpdateListener(animator -> {
            final ViewGroup.LayoutParams params = getLayoutParams();
            params.height = (int) animator.getAnimatedValue();
            setLayoutParams(params);
        });
        heightAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animator) {
            }

            @Override
            public void onAnimationEnd(@NonNull Animator animator) {
                onCloseEduTextAnimationEnd();
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animator) {
                onCloseEduTextAnimationEnd();
            }

            @Override
            public void onAnimationRepeat(@NonNull Animator animator) {
            }
        });
        heightAnimator.start();

        mListener.onCloseEduTextAnimationStart();
    }

    public void onCloseEduTextAnimationEnd() {
        mListener.onCloseEduTextAnimationEnd();
    }

    /**
     * Creates the educational text that will be displayed to the user. Here we replace the
     * HOME annotation in the String with an icon
     */
    private CharSequence createEduTextString() {
        final SpannedString eduText = (SpannedString) getResources().getText(R.string.pip_edu_text);
        final SpannableString spannableString = new SpannableString(eduText);
        Arrays.stream(eduText.getSpans(0, eduText.length(), Annotation.class)).findFirst()
                .ifPresent(annotation -> {
                    final Drawable icon =
                            getResources().getDrawable(R.drawable.home_icon, mContext.getTheme());
                    if (icon != null) {
                        icon.mutate();
                        icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                        spannableString.setSpan(new CenteredImageSpan(icon),
                                eduText.getSpanStart(annotation),
                                eduText.getSpanEnd(annotation),
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                });

        return spannableString;
    }

    /**
     * A listener for edu text drawer event states.
     */
    interface Listener {
        void onCloseEduTextAnimationStart();
        void onCloseEduTextAnimationEnd();
    }

}
