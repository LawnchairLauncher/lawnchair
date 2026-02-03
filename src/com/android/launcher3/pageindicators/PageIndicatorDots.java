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

import static com.android.launcher3.Flags.enableLauncherVisualRefresh;
import static com.android.launcher3.config.FeatureFlags.FOLDABLE_SINGLE_PAGE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.FloatProperty;
import android.util.IntProperty;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.launcher3.Flags;
import com.android.launcher3.Insettable;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;

import java.util.function.Consumer;

import app.lawnchair.theme.color.tokens.ColorTokens;

/**
 * {@link PageIndicator} which shows dots per page. The active page is shown with the current
 * accent color.
 * <p>
 * TODO(b/402258632): Split PageIndicatorDots into 2 different classes: FolderPageIndicator &
 * WorkspacePageIndicator. A lot of the functionality in this class is only used by one UI purpose.
 */
public class PageIndicatorDots extends View implements Insettable, PageIndicator {
    private static final float SHIFT_PER_ANIMATION = 0.5f;
    private static final float SHIFT_THRESHOLD = (enableLauncherVisualRefresh() ? 0.5f : 0.2f);
    private static final long ANIMATION_DURATION = (enableLauncherVisualRefresh() ? 200 : 150);
    private static final int PAGINATION_FADE_DELAY = ViewConfiguration.getScrollDefaultDelay();
    private static final int PAGINATION_FADE_IN_DURATION = 83;
    private static final int PAGINATION_FADE_OUT_DURATION = 167;

    private static final int ENTER_ANIMATION_START_DELAY = 300;
    private static final int ENTER_ANIMATION_STAGGERED_DELAY = 150;
    private static final int ENTER_ANIMATION_DURATION = 400;

    private static final int HEIGHT_MULTIPLIER = 4;
    private static final int WIDTH_MULTIPLIER = 3;

    private static final int PAGE_INDICATOR_ALPHA = 255;
    private static final int DOT_ALPHA = 128;
    private static final float DOT_ALPHA_FRACTION = 0.5f;
    private static final int DOT_GAP_FACTOR = 4;
    private static final int VISIBLE_ALPHA = 255;
    private static final int INVISIBLE_ALPHA = 0;
    private Paint mPaginationPaint;

    // This value approximately overshoots to 1.5 times the original size.
    private static final float ENTER_ANIMATION_OVERSHOOT_TENSION = 4.9f;

    // This is used to optimize the onDraw method by not constructing a new RectF each draw.
    private static final RectF sTempRect = new RectF();
    private static final RectF sLastActiveRect = new RectF();

    private static final FloatProperty<PageIndicatorDots> CURRENT_POSITION =
            new FloatProperty<PageIndicatorDots>("current_position") {
                @Override
                public Float get(PageIndicatorDots obj) {
                    return obj.mCurrentPosition;
                }

                @Override
                public void setValue(PageIndicatorDots obj, float pos) {
                    obj.mCurrentPosition = pos;
                    obj.invalidate();
                    obj.invalidateOutline();
                }
            };

    private static final IntProperty<PageIndicatorDots> PAGINATION_ALPHA =
            new IntProperty<PageIndicatorDots>("pagination_alpha") {
                @Override
                public Integer get(PageIndicatorDots obj) {
                    return obj.mPaginationPaint.getAlpha();
                }

                @Override
                public void setValue(PageIndicatorDots obj, int alpha) {
                    obj.mPaginationPaint.setAlpha(alpha);
                    obj.invalidate();
                }
            };

    private final Handler mDelayedPaginationFadeHandler = new Handler(Looper.getMainLooper());
    private final float mDotRadius;
    private final float mGapWidth;
    private final float mCircleGap;
    private final boolean mIsRtl;

    private int mNumPages;
    private int mActivePage;
    private int mTotalScroll;
    private boolean mShouldAutoHide;
    private boolean mIsMoveAnimationQueued;
    private int mToAlpha;

    /**
     * The current position of the active dot including the animation progress.
     * For ex:
     * 0.0  => Active dot is at position 0
     * 0.33 => Active dot is at position 0 and is moving towards 1
     * 0.50 => Active dot is at position [0, 1]
     * 0.77 => Active dot has left position 0 and is collapsing towards position 1
     * 1.0  => Active dot is at position 1
     */
    private float mCurrentPosition;
    private int mLastPosition;
    private float mFinalPosition;
    private boolean mIsScrollPaused;
    @VisibleForTesting
    boolean mIsTwoPanels;
    private ObjectAnimator mAnimator;
    private @Nullable ObjectAnimator mAlphaAnimator;

    private float[] mEntryAnimationRadiusFactors;

    private final Runnable mHidePaginationRunnable =
            () -> animatePaginationToAlpha(INVISIBLE_ALPHA);

    public PageIndicatorDots(Context context) {
        this(context, null);
    }

    public PageIndicatorDots(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PageIndicatorDots(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mPaginationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaginationPaint.setStyle(Style.FILL);
        mPaginationPaint.setColor(ColorTokens.pageIndicatorDotColor.resolveColor(context));
        mDotRadius = getResources().getDimension(R.dimen.page_indicator_dot_size) / 2;
        mGapWidth = getResources().getDimension(R.dimen.page_indicator_gap_width);
        mCircleGap = (enableLauncherVisualRefresh())
                ? mDotRadius * 2 + mGapWidth
                : DOT_GAP_FACTOR * mDotRadius;
        setOutlineProvider(new MyOutlineProver());
        mIsRtl = Utilities.isRtl(getResources());
    }

    @Override
    public void setScroll(int currentScroll, int totalScroll) {
        if (currentScroll == 0 && totalScroll == 0) {
            CURRENT_POSITION.set(this, (float) mActivePage);
            return;
        }

        if (mNumPages <= 1) {
            return;
        }

        // Skip scroll update during binding. We will update it when binding completes.
        if (mIsScrollPaused) {
            return;
        }

        if (mShouldAutoHide) {
            animatePaginationToAlpha(VISIBLE_ALPHA);
        }

        if (mIsRtl) {
            currentScroll = totalScroll - currentScroll;
        }

        mTotalScroll = totalScroll;

        if (enableLauncherVisualRefresh()) {
            float scrollPerPage = (float) totalScroll / (mNumPages - 1);
            float position = currentScroll / scrollPerPage;

            if (mIsMoveAnimationQueued) {
                mCurrentPosition = position;
                // For jump animations, wait until we have scrolled to within 1 page away from the
                // final destination to start the animation. This has the effect of a smooth delay.
                if (Math.abs(mActivePage - position) <= 1) {
                    animateToPosition(mActivePage);
                }
            } else if (mAnimator == null) {
                // mLastPosition is used to determine which dots should be growing / shrinking.
                // Update it when the scroll moves pages. Fling animations also update mLastPosition
                if (Math.abs(mLastPosition - position) > 1) {
                    mLastPosition = Math.round(position);
                }
                mFinalPosition = mLastPosition + (position > mLastPosition ? 1 : -1);

                // Just show current position if slow scroll. Otherwise, fling animation is going
                CURRENT_POSITION.set(this, position);
            }

            float delta = Math.abs((int) position - position);
            if (mShouldAutoHide && !mIsMoveAnimationQueued && (delta < 0.1 || delta > 0.9)) {
                hideAfterDelay();
            }
        } else {
            int scrollPerPage = totalScroll / (mNumPages - 1);
            int pageToLeft = scrollPerPage == 0 ? 0 : currentScroll / scrollPerPage;
            int pageToLeftScroll = pageToLeft * scrollPerPage;
            int pageToRightScroll = pageToLeftScroll + scrollPerPage;

            float scrollThreshold = SHIFT_THRESHOLD * scrollPerPage;
            if (currentScroll < pageToLeftScroll + scrollThreshold) {
                // scroll is within the left page's threshold
                animateToPosition(pageToLeft);
                if (mShouldAutoHide) {
                    hideAfterDelay();
                }
            } else if (currentScroll > pageToRightScroll - scrollThreshold) {
                // scroll is far enough from left page to go to the right page
                animateToPosition(pageToLeft + 1);
                if (mShouldAutoHide) {
                    hideAfterDelay();
                }
            } else {
                // scroll is between left and right page
                animateToPosition(pageToLeft + SHIFT_PER_ANIMATION);
                if (mShouldAutoHide) {
                    mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
                }
            }
        }
    }

    @Override
    public void setShouldAutoHide(boolean shouldAutoHide) {
        mShouldAutoHide = shouldAutoHide;
        if (shouldAutoHide && mPaginationPaint.getAlpha() > INVISIBLE_ALPHA) {
            hideAfterDelay();
        } else if (!shouldAutoHide) {
            mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void setPaintColor(int color) {
        mPaginationPaint.setColor(color);
    }

    private void hideAfterDelay() {
        mDelayedPaginationFadeHandler.removeCallbacksAndMessages(null);
        mDelayedPaginationFadeHandler.postDelayed(mHidePaginationRunnable, PAGINATION_FADE_DELAY);
    }

    private void animatePaginationToAlpha(int alpha) {
        if (alpha == mToAlpha) {
            // Ignore the new animation if it is going to the same alpha as the current animation.
            return;
        }

        if (mAlphaAnimator != null) {
            mAlphaAnimator.cancel();
        }
        mAlphaAnimator = ObjectAnimator.ofInt(this, PAGINATION_ALPHA,
                alpha);
        // If we are animating to decrease the alpha, then it's a fade out animation
        // whereas if we are animating to increase the alpha, it's a fade in animation.
        mAlphaAnimator.setDuration(alpha < mToAlpha
                ? PAGINATION_FADE_OUT_DURATION
                : PAGINATION_FADE_IN_DURATION);
        mAlphaAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAlphaAnimator = null;
            }
        });
        mAlphaAnimator.start();
        mToAlpha = alpha;
    }

    /**
     * Pauses all currently running animations.
     */
    @Override
    public void pauseAnimations() {
        if (mAlphaAnimator != null) {
            mAlphaAnimator.pause();
        }
    }

    /**
     * Force-ends all currently running or paused animations.
     */
    @Override
    public void skipAnimationsToEnd() {
        if (mAlphaAnimator != null) {
            mAlphaAnimator.end();
        }
    }

    private void animateToPosition(float position) {
        mFinalPosition = position;
        if (!enableLauncherVisualRefresh()
                && Math.abs(mCurrentPosition - mFinalPosition) < SHIFT_THRESHOLD) {
            mCurrentPosition = mFinalPosition;
        }
        if (mAnimator == null && Float.compare(mCurrentPosition, position) != 0) {
            float positionForThisAnim = enableLauncherVisualRefresh()
                    ? position
                    : (mCurrentPosition > mFinalPosition
                            ? mCurrentPosition - SHIFT_PER_ANIMATION
                            : mCurrentPosition + SHIFT_PER_ANIMATION);
            mAnimator = ObjectAnimator.ofFloat(this, CURRENT_POSITION, positionForThisAnim);
            mAnimator.addListener(new AnimationCycleListener());
            mAnimator.setDuration(ANIMATION_DURATION);
            if (enableLauncherVisualRefresh()) {
                // If user performs fling with only 11% of the distance to go, we don't want the
                // animation to be 150 ms long so we tether it to the scroll distance remaining.
                // Large jump scrolls can be more than 1, so we cap the animation time.
                float remainingScroll = Math.min(1, Math.abs(position - mCurrentPosition));
                mAnimator.setDuration((long) (ANIMATION_DURATION * remainingScroll));
                mAnimator.setInterpolator(new OvershootInterpolator());
                mIsMoveAnimationQueued = false;
            }
            mAnimator.start();
        }
    }

    public void stopAllAnimations() {
        if (mAnimator != null) {
            mAnimator.cancel();
            mAnimator = null;
        }
        mFinalPosition = mActivePage;
        CURRENT_POSITION.set(this, mFinalPosition);
    }

    /**
     * Sets up up the page indicator to play the entry animation.
     * {@link #playEntryAnimation()} must be called after this.
     */
    public void prepareEntryAnimation() {
        mEntryAnimationRadiusFactors = new float[mNumPages];
        invalidate();
    }

    // TODO(b/394355070): Verify Folder Entry Animation works correctly with visual updates
    public void playEntryAnimation() {
        int count = mEntryAnimationRadiusFactors.length;
        if (count == 0) {
            mEntryAnimationRadiusFactors = null;
            invalidate();
            return;
        }

        Interpolator interpolator = new OvershootInterpolator(ENTER_ANIMATION_OVERSHOOT_TENSION);
        AnimatorSet animSet = new AnimatorSet();
        for (int i = 0; i < count; i++) {
            ValueAnimator anim = ValueAnimator.ofFloat(0, 1).setDuration(ENTER_ANIMATION_DURATION);
            final int index = i;
            anim.addUpdateListener(new AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    mEntryAnimationRadiusFactors[index] = (Float) animation.getAnimatedValue();
                    invalidate();
                }
            });
            anim.setInterpolator(interpolator);
            anim.setStartDelay(ENTER_ANIMATION_START_DELAY + ENTER_ANIMATION_STAGGERED_DELAY * i);
            animSet.play(anim);
        }

        animSet.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(Animator animation) {
                mEntryAnimationRadiusFactors = null;
                invalidateOutline();
                invalidate();
            }
        });
        animSet.start();
    }

    /**
     * Performs a move-bounce animation upon fling. Flings happen in between 2 positions. Either we
     * are coming from the right one, and going to the left, or vice versa. Ex. 3.7 current pos with
     * left swipe means last=3, target=4. Slow scroll page-changes also update mLastPosition.
     */
    @Override
    public void setActiveMarker(int activePage) {
        // In unfolded foldables, every page has two CellLayouts, so we need to halve the active
        // page for it to be accurate.
        if (mIsTwoPanels && !FOLDABLE_SINGLE_PAGE.get()) {
            activePage = activePage / 2;
        }

        if (mActivePage != activePage) {
            mLastPosition = mActivePage;
            mActivePage = activePage;

            if (Flags.enableLauncherVisualRefresh()) {
                // If the animation is a local snap, then immediately perform it. Otherwise, delay
                // snap animation until current scroll is within 1 page of the target page.
                if (Math.abs(mActivePage - mCurrentPosition) <= 1) {
                    animateToPosition(activePage);
                } else {
                    mFinalPosition = activePage;
                    mIsMoveAnimationQueued = true;
                }
            }
        }
    }

    @Override
    public void setMarkersCount(int numMarkers) {
        mNumPages = numMarkers;

        // If the last page gets removed we want to go to the previous page.
        if (mNumPages > 0 && mNumPages == mActivePage) {
            mActivePage--;
            CURRENT_POSITION.set(this, (float) mActivePage);
        }

        requestLayout();
    }

    @Override
    public void setPauseScroll(boolean pause, boolean isTwoPanels) {
        mIsTwoPanels = isTwoPanels;

        // Reapply correct current position which was skipped during setScroll.
        if (mIsScrollPaused && !pause) {
            CURRENT_POSITION.set(this, (float) mActivePage);
        }

        mIsScrollPaused = pause;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Add extra spacing of mDotRadius on all sides so than entry animation could be run
        int width = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY ?
                MeasureSpec.getSize(widthMeasureSpec)
                : enableLauncherVisualRefresh()
                        // #ofGaps*gap_size + (#ofDots + 1 for double size dot)*dot_size
                        ? (int) (((mNumPages - 1) * mGapWidth) + ((1 + mNumPages) * mDotRadius * 2))
                        : (int) ((mNumPages * WIDTH_MULTIPLIER + 2) * mDotRadius);
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec)
                : (int) (HEIGHT_MULTIPLIER * mDotRadius);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mNumPages < 2) {
            return;
        }

        if (mShouldAutoHide && mTotalScroll == 0) {
            mPaginationPaint.setAlpha(INVISIBLE_ALPHA);
            return;
        }

        float circleGap = mCircleGap;
        float x = ((float) getWidth() / 2) - (mCircleGap * ((float) mNumPages - 1) / 2);
        float y = getHeight() / 2;

        // Save the current alpha value, so we can reset to it again after drawing the dots
        int alpha = mPaginationPaint.getAlpha();

        if (mEntryAnimationRadiusFactors != null) {
            if (enableLauncherVisualRefresh()) {
                x -= mDotRadius;
                if (mIsRtl) {
                    x = getWidth() - x;
                    circleGap = -circleGap;
                }
                sTempRect.top = y - mDotRadius;
                sTempRect.bottom = y + mDotRadius;

                for (int i = 0; i < mEntryAnimationRadiusFactors.length; i++) {
                    if (i == mActivePage) {
                        if (mIsRtl) {
                            sTempRect.left = x - (mDotRadius * 3);
                            sTempRect.right = x + mDotRadius;
                            x += circleGap - (mDotRadius * 2);
                        } else {
                            sTempRect.left = x - mDotRadius;
                            sTempRect.right = x + (mDotRadius * 3);
                            x += circleGap + (mDotRadius * 2);
                        }
                        scale(sTempRect, mEntryAnimationRadiusFactors[i]);
                        float scaledRadius = mDotRadius * mEntryAnimationRadiusFactors[i];
                        mPaginationPaint.setAlpha(PAGE_INDICATOR_ALPHA);
                        canvas.drawRoundRect(sTempRect, scaledRadius, scaledRadius,
                                mPaginationPaint);
                    } else {
                        mPaginationPaint.setAlpha(DOT_ALPHA);
                        canvas.drawCircle(x, y, mDotRadius * mEntryAnimationRadiusFactors[i],
                                mPaginationPaint);
                        x += circleGap;
                    }
                }
            } else {
                // During entry animation, only draw the circles

                if (mIsRtl) {
                    x = getWidth() - x;
                    circleGap = -circleGap;
                }
                for (int i = 0; i < mEntryAnimationRadiusFactors.length; i++) {
                    mPaginationPaint.setAlpha(i == mActivePage ? PAGE_INDICATOR_ALPHA : DOT_ALPHA);
                    canvas.drawCircle(x, y, mDotRadius * mEntryAnimationRadiusFactors[i],
                            mPaginationPaint);
                    x += circleGap;
                }
            }
        } else {

            if (enableLauncherVisualRefresh()) {
                int nonActiveAlpha = (int) (alpha * DOT_ALPHA_FRACTION);

                float diameter = 2 * mDotRadius;
                sTempRect.top = y - mDotRadius;
                sTempRect.bottom = y + mDotRadius;
                sTempRect.left = x - diameter;

                float currentPosition = mCurrentPosition;
                float lastPosition = mLastPosition;
                float finalPosition = mFinalPosition;

                if (mIsRtl) {
                    currentPosition = mNumPages - currentPosition - 1;
                    lastPosition = mNumPages - lastPosition - 1;
                    finalPosition = mNumPages - finalPosition - 1;
                }

                // Calculate the progress of the animation. The numerator is the distance from the
                // start while the denominator is the total distance from start to end. Progress can
                // be greater than 1 if performing a snap animation. In the case that the last and
                // final positions are equivalent, we divide by 1 rather than 0 to avoid NaN, which
                // screws up the math and has the effect of turning page indicator dots invisible.
                float progress = Math.abs(currentPosition - lastPosition)
                        / Math.max(1, Math.abs(finalPosition - lastPosition));
                float bounceAdjustment = Math.max(progress - 1, 0) * diameter;
                int alphaAdjustment = (int) (Math.min(progress, 1) * (alpha - nonActiveAlpha));

                // Here we draw the dots, one at a time from the left-most dot to the right-most dot
                // 1.0 => 000000 000000111111 000000
                // 1.3 => 000000 0000001111 11000000
                // 1.6 => 000000 00000011 1111000000
                // 2.0 => 000000 000000 111111000000
                for (int i = 0; i < mNumPages; i++) {
                    // Adjust alpha and width based on the progress of the animation. Smaller dots
                    // will also be more transparent, and larger dots more opaque.
                    mPaginationPaint.setAlpha(i == lastPosition ? alpha - alphaAdjustment
                            : i == finalPosition ? nonActiveAlpha + alphaAdjustment
                                    : nonActiveAlpha);
                    sTempRect.right = sTempRect.left + diameter + (diameter
                            * (i == lastPosition ? 1 - progress
                            : i == finalPosition ? progress : 0));

                    // Save x position before adjusting right edge for bounce animation. This keeps
                    // the dots in the same centered locations, avoiding an accordion appearance.
                    x = sTempRect.right + mGapWidth;

                    if (bounceAdjustment > 0) {
                        // While the animation is shifting the active pagination dots size from
                        // the previously active one, to the newly active dot, there is no bounce
                        // adjustment. The bounce happens in the "Overshoot" phase of the animation.
                        // lastPosition is used to determine when the currentPosition is just
                        // leaving the page, or if it is in the overshoot phase.
                        if (finalPosition == i) {
                            if (lastPosition < finalPosition) {
                                sTempRect.left -= bounceAdjustment;
                            } else {
                                sTempRect.right += bounceAdjustment;
                            }
                        }
                        if ((lastPosition <= i && i < finalPosition)
                                || (finalPosition < i && i <= lastPosition)) {
                            if (lastPosition > finalPosition) {
                                sTempRect.left += bounceAdjustment;
                            } else {
                                sTempRect.right -= bounceAdjustment;
                            }
                        }
                    }
                    if (Math.round(mCurrentPosition) == i) {
                        sLastActiveRect.set(sTempRect);
                        if (mCurrentPosition == 0) {
                            // The outline is calculated before onDraw is called. If the user has
                            // paginated, closed the folder, and opened the folder again, the
                            // first drawn outline will use stale bounds.
                            // Invalidation is cheap, and is only needed when scroll is 0.
                            invalidateOutline();
                        }
                    }
                    canvas.drawRoundRect(sTempRect, mDotRadius, mDotRadius, mPaginationPaint);

                    sTempRect.left = x;
                }

            } else {
                // Here we draw the dots
                mPaginationPaint.setAlpha((int) (alpha * DOT_ALPHA_FRACTION));
                for (int i = 0; i < mNumPages; i++) {
                    canvas.drawCircle(x, y, mDotRadius, mPaginationPaint);
                    x += circleGap;
                }

                // Here we draw the current page indicator
                mPaginationPaint.setAlpha(alpha);
                canvas.drawRoundRect(getActiveRect(), mDotRadius, mDotRadius, mPaginationPaint);
            }
        }
        // Reset the alpha so it doesn't become progressively more transparent each onDraw call
        mPaginationPaint.setAlpha(alpha);
    }

    private static void scale(RectF rect, float factor) {
        float horizontalAdjustment = rect.width() * (factor - 1) / 2;
        float verticalAdjustment = rect.height() * (factor - 1) / 2;

        rect.top -= verticalAdjustment;
        rect.bottom += verticalAdjustment;

        rect.left -= horizontalAdjustment;
        rect.right += horizontalAdjustment;
    }

    private RectF getActiveRect() {
        float startCircle = (int) mCurrentPosition;
        float delta = mCurrentPosition - startCircle;
        float diameter = 2 * mDotRadius;
        float startX = ((float) getWidth() / 2)
                - (mCircleGap * (((float) mNumPages - 1) / 2))
                - mDotRadius;
        sTempRect.top = (getHeight() * 0.5f) - mDotRadius;
        sTempRect.bottom = (getHeight() * 0.5f) + mDotRadius;
        sTempRect.left = startX + (startCircle * mCircleGap);
        sTempRect.right = sTempRect.left + diameter;

        if (delta < SHIFT_PER_ANIMATION) {
            // dot is capturing the right circle.
            sTempRect.right += delta * mCircleGap * 2;
        } else {
            // Dot is leaving the left circle.
            sTempRect.right += mCircleGap;

            delta -= SHIFT_PER_ANIMATION;
            sTempRect.left += delta * mCircleGap * 2;
        }

        if (mIsRtl) {
            float rectWidth = sTempRect.width();
            sTempRect.right = getWidth() - sTempRect.left;
            sTempRect.left = sTempRect.right - rectWidth;
        }

        return sTempRect;
    }

    @VisibleForTesting
    int getActivePage() {
        return mActivePage;
    }

    @VisibleForTesting
    int getNumPages() {
        return mNumPages;
    }

    @VisibleForTesting
    float getCurrentPosition() {
        return mCurrentPosition;
    }

    private class MyOutlineProver extends ViewOutlineProvider {

        @Override
        public void getOutline(View view, Outline outline) {
            if (mEntryAnimationRadiusFactors == null) {
                RectF activeRect = enableLauncherVisualRefresh()
                        ? sLastActiveRect : getActiveRect();
                outline.setRoundRect(
                        (int) activeRect.left,
                        (int) activeRect.top,
                        (int) activeRect.right,
                        (int) activeRect.bottom,
                        mDotRadius
                );
            }
        }
    }

    /**
     * Listener for keep running the animation until the final state is reached.
     */
    private class AnimationCycleListener extends AnimatorListenerAdapter {

        private boolean mCancelled = false;

        @Override
        public void onAnimationCancel(Animator animation) {
            mCancelled = true;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            if (!mCancelled) {
                if (mShouldAutoHide) {
                    hideAfterDelay();
                }
                mAnimator = null;
                animateToPosition(mFinalPosition);
            }
        }
    }

    /**
     * We need to override setInsets to prevent InsettableFrameLayout from applying different
     * margins on the pagination.
     */
    @Override
    public void setInsets(Rect insets) {
    }
}
