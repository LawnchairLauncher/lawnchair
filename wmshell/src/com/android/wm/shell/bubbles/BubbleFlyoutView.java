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

package com.android.wm.shell.bubbles;

import static android.graphics.Paint.ANTI_ALIAS_FLAG;
import static android.graphics.Paint.FILTER_BITMAP_FLAG;

import static com.android.wm.shell.animation.Interpolators.ALPHA_IN;
import static com.android.wm.shell.animation.Interpolators.ALPHA_OUT;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.wm.shell.R;
import com.android.wm.shell.common.TriangleShape;

/**
 * Flyout view that appears as a 'chat bubble' alongside the bubble stack. The flyout can visually
 * transform into the 'new' dot, which is used during flyout dismiss animations/gestures.
 */
public class BubbleFlyoutView extends FrameLayout {
    /** Translation Y of fade animation. */
    private static final float FLYOUT_FADE_Y = 40f;

    private static final long FLYOUT_FADE_OUT_DURATION = 150L;
    private static final long FLYOUT_FADE_IN_DURATION = 250L;

    // Whether the flyout view should show a pointer to the bubble.
    private static final boolean SHOW_POINTER = false;

    private BubblePositioner mPositioner;

    private final int mFlyoutPadding;
    private final int mFlyoutSpaceFromBubble;
    private final int mPointerSize;
    private int mBubbleSize;

    private final int mFlyoutElevation;
    private final int mBubbleElevation;
    private int mFloatingBackgroundColor;
    private final float mCornerRadius;

    private final ViewGroup mFlyoutTextContainer;
    private final ImageView mSenderAvatar;
    private final TextView mSenderText;
    private final TextView mMessageText;

    /** Values related to the 'new' dot which we use to figure out where to collapse the flyout. */
    private float mNewDotRadius;
    private float mNewDotSize;
    private float mOriginalDotSize;

    /**
     * The paint used to draw the background, whose color changes as the flyout transitions to the
     * tinted 'new' dot.
     */
    private final Paint mBgPaint = new Paint(ANTI_ALIAS_FLAG | FILTER_BITMAP_FLAG);
    private final ArgbEvaluator mArgbEvaluator = new ArgbEvaluator();

    /**
     * Triangular ShapeDrawables used for the triangle that points from the flyout to the bubble
     * stack (a chat-bubble effect).
     */
    private final ShapeDrawable mLeftTriangleShape;
    private final ShapeDrawable mRightTriangleShape;

    /** Whether the flyout arrow is on the left (pointing left) or right (pointing right). */
    private boolean mArrowPointingLeft = true;

    /** Color of the 'new' dot that the flyout will transform into. */
    private int mDotColor;

    /** Keeps last used night mode flags **/
    private int mNightModeFlags;

    /** The outline of the triangle, used for elevation shadows. */
    private final Outline mTriangleOutline = new Outline();

    /** The bounds of the flyout background, kept up to date as it transitions to the 'new' dot. */
    private final RectF mBgRect = new RectF();

    /** The y position of the flyout, relative to the top of the screen. */
    private float mFlyoutY = 0f;

    /**
     * Percent progress in the transition from flyout to 'new' dot. These two values are the inverse
     * of each other (if we're 40% transitioned to the dot, we're 60% flyout), but it makes the code
     * much more readable.
     */
    private float mPercentTransitionedToDot = 1f;
    private float mPercentStillFlyout = 0f;

    /**
     * The difference in values between the flyout and the dot. These differences are gradually
     * added over the course of the animation to transform the flyout into the 'new' dot.
     */
    private float mFlyoutToDotWidthDelta = 0f;
    private float mFlyoutToDotHeightDelta = 0f;

    /** The translation values when the flyout is completely transitioned into the dot. */
    private float mTranslationXWhenDot = 0f;
    private float mTranslationYWhenDot = 0f;

    /**
     * The current translation values applied to the flyout background as it transitions into the
     * 'new' dot.
     */
    private float mBgTranslationX;
    private float mBgTranslationY;

    private float[] mDotCenter;

    /** The flyout's X translation when at rest (not animating or dragging). */
    private float mRestingTranslationX = 0f;

    /** The badge sizes are defined as percentages of the app icon size. Same value as Launcher3. */
    private static final float SIZE_PERCENTAGE = 0.228f;

    private static final float DOT_SCALE = 1f;

    /** Callback to run when the flyout is hidden. */
    @Nullable private Runnable mOnHide;

    public BubbleFlyoutView(Context context, BubblePositioner positioner) {
        super(context);
        mPositioner = positioner;

        LayoutInflater.from(context).inflate(R.layout.bubble_flyout, this, true);
        mFlyoutTextContainer = findViewById(R.id.bubble_flyout_text_container);
        mSenderText = findViewById(R.id.bubble_flyout_name);
        mSenderAvatar = findViewById(R.id.bubble_flyout_avatar);
        mMessageText = mFlyoutTextContainer.findViewById(R.id.bubble_flyout_text);

        final Resources res = getResources();
        mFlyoutPadding = res.getDimensionPixelSize(R.dimen.bubble_flyout_padding_x);
        mFlyoutSpaceFromBubble = res.getDimensionPixelSize(R.dimen.bubble_flyout_space_from_bubble);
        mPointerSize = SHOW_POINTER
                ? res.getDimensionPixelSize(R.dimen.bubble_flyout_pointer_size)
                : 0;

        mBubbleElevation = res.getDimensionPixelSize(R.dimen.bubble_elevation);
        mFlyoutElevation = res.getDimensionPixelSize(R.dimen.bubble_flyout_elevation);

        final TypedArray ta = mContext.obtainStyledAttributes(
                new int[] {android.R.attr.dialogCornerRadius});
        mCornerRadius = ta.getDimensionPixelSize(0, 0);
        ta.recycle();

        // Add padding for the pointer on either side, onDraw will draw it in this space.
        setPadding(mPointerSize, 0, mPointerSize, 0);
        setWillNotDraw(false);
        setClipChildren(!SHOW_POINTER);
        setTranslationZ(mFlyoutElevation);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                BubbleFlyoutView.this.getOutline(outline);
            }
        });

        // Use locale direction so the text is aligned correctly.
        setLayoutDirection(LAYOUT_DIRECTION_LOCALE);

        mLeftTriangleShape =
                new ShapeDrawable(TriangleShape.createHorizontal(
                        mPointerSize, mPointerSize, true /* isPointingLeft */));
        mLeftTriangleShape.setBounds(0, 0, mPointerSize, mPointerSize);

        mRightTriangleShape =
                new ShapeDrawable(TriangleShape.createHorizontal(
                        mPointerSize, mPointerSize, false /* isPointingLeft */));
        mRightTriangleShape.setBounds(0, 0, mPointerSize, mPointerSize);

        applyConfigurationColors(getResources().getConfiguration());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        renderBackground(canvas);
        invalidateOutline();
        super.onDraw(canvas);
    }

    void updateFontSize() {
        final float fontSize = mContext.getResources()
                .getDimensionPixelSize(com.android.internal.R.dimen.text_size_body_2_material);
        mMessageText.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
        mSenderText.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize);
    }

    /*
     * Fade animation for consecutive flyouts.
     */
    void animateUpdate(Bubble.FlyoutMessage flyoutMessage, PointF stackPos,
            boolean hideDot, float[] dotCenter, Runnable onHide) {
        mOnHide = onHide;
        mDotCenter = dotCenter;
        final Runnable afterFadeOut = () -> {
            updateFlyoutMessage(flyoutMessage);
            // Wait for TextViews to layout with updated height.
            post(() -> {
                fade(true /* in */, stackPos, hideDot, () -> {} /* after */);
            } /* after */ );
        };
        fade(false /* in */, stackPos, hideDot, afterFadeOut);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        if (applyColorsAccordingToConfiguration(newConfig)) {
            invalidate();
        }
    }

    /*
     * Fade-out above or fade-in from below.
     */
    private void fade(boolean in, PointF stackPos, boolean hideDot, Runnable afterFade) {
        mFlyoutY = stackPos.y + (mBubbleSize - mFlyoutTextContainer.getHeight()) / 2f;

        setAlpha(in ? 0f : 1f);
        setTranslationY(in ? mFlyoutY + FLYOUT_FADE_Y : mFlyoutY);
        updateFlyoutX(stackPos.x);
        setTranslationX(mRestingTranslationX);
        updateDot(stackPos, hideDot);

        animate()
                .alpha(in ? 1f : 0f)
                .setDuration(in ? FLYOUT_FADE_IN_DURATION : FLYOUT_FADE_OUT_DURATION)
                .setInterpolator(in ? ALPHA_IN : ALPHA_OUT);
        animate()
                .translationY(in ? mFlyoutY : mFlyoutY - FLYOUT_FADE_Y)
                .setDuration(in ? FLYOUT_FADE_IN_DURATION : FLYOUT_FADE_OUT_DURATION)
                .setInterpolator(in ? ALPHA_IN : ALPHA_OUT)
                .withEndAction(afterFade);
    }

    private void updateFlyoutMessage(Bubble.FlyoutMessage flyoutMessage) {
        final Drawable senderAvatar = flyoutMessage.senderAvatar;
        if (senderAvatar != null && flyoutMessage.isGroupChat) {
            mSenderAvatar.setVisibility(VISIBLE);
            mSenderAvatar.setImageDrawable(senderAvatar);
        } else {
            mSenderAvatar.setVisibility(GONE);
            mSenderAvatar.setTranslationX(0);
            mMessageText.setTranslationX(0);
            mSenderText.setTranslationX(0);
        }

        final int maxTextViewWidth = (int) mPositioner.getMaxFlyoutSize() - mFlyoutPadding * 2;

        // Name visibility
        if (!TextUtils.isEmpty(flyoutMessage.senderName)) {
            mSenderText.setMaxWidth(maxTextViewWidth);
            mSenderText.setText(flyoutMessage.senderName);
            mSenderText.setVisibility(VISIBLE);
        } else {
            mSenderText.setVisibility(GONE);
        }

        // Set the flyout TextView's max width in terms of percent, and then subtract out the
        // padding so that the entire flyout view will be the desired width (rather than the
        // TextView being the desired width + extra padding).
        mMessageText.setMaxWidth(maxTextViewWidth);
        mMessageText.setText(flyoutMessage.message);
    }

    void updateFlyoutX(float stackX) {
        // Calculate the translation required to position the flyout next to the bubble stack,
        // with the desired padding.
        mRestingTranslationX = mArrowPointingLeft
                ? stackX + mBubbleSize + mFlyoutSpaceFromBubble
                : stackX - getWidth() - mFlyoutSpaceFromBubble;
    }

    void updateDot(PointF stackPos, boolean hideDot) {
        // Calculate the difference in size between the flyout and the 'dot' so that we can
        // transform into the dot later.
        final float newDotSize = hideDot ? 0f : mNewDotSize;
        mFlyoutToDotWidthDelta = getWidth() - newDotSize;
        mFlyoutToDotHeightDelta = getHeight() - newDotSize;

        // Calculate the translation values needed to be in the correct 'new dot' position.
        final float adjustmentForScaleAway = hideDot ? 0f : (mOriginalDotSize / 2f);
        final float dotPositionX = stackPos.x + mDotCenter[0] - adjustmentForScaleAway;
        final float dotPositionY = stackPos.y + mDotCenter[1] - adjustmentForScaleAway;

        final float distanceFromFlyoutLeftToDotCenterX = mRestingTranslationX - dotPositionX;
        final float distanceFromLayoutTopToDotCenterY = mFlyoutY - dotPositionY;

        mTranslationXWhenDot = -distanceFromFlyoutLeftToDotCenterX;
        mTranslationYWhenDot = -distanceFromLayoutTopToDotCenterY;
    }

    /** Configures the flyout, collapsed into dot form. */
    void setupFlyoutStartingAsDot(
            Bubble.FlyoutMessage flyoutMessage,
            PointF stackPos,
            boolean arrowPointingLeft,
            int dotColor,
            @Nullable Runnable onLayoutComplete,
            @Nullable Runnable onHide,
            float[] dotCenter,
            boolean hideDot)  {

        mBubbleSize = mPositioner.getBubbleSize();

        mOriginalDotSize = SIZE_PERCENTAGE * mBubbleSize;
        mNewDotRadius = (DOT_SCALE * mOriginalDotSize) / 2f;
        mNewDotSize = mNewDotRadius * 2f;

        updateFlyoutMessage(flyoutMessage);

        mArrowPointingLeft = arrowPointingLeft;
        mDotColor = dotColor;
        mOnHide = onHide;
        mDotCenter = dotCenter;

        setCollapsePercent(1f);

        // Wait for TextViews to layout with updated height.
        post(() -> {
            // Flyout is vertically centered with respect to the bubble.
            mFlyoutY =
                    stackPos.y + (mBubbleSize - mFlyoutTextContainer.getHeight()) / 2f;
            setTranslationY(mFlyoutY);
            updateFlyoutX(stackPos.x);
            updateDot(stackPos, hideDot);
            if (onLayoutComplete != null) {
                onLayoutComplete.run();
            }
        });
    }

    /**
     * Hides the flyout and runs the optional callback passed into setupFlyoutStartingAsDot.
     * The flyout has been animated into the 'new' dot by the time we call this, so no animations
     * are needed.
     */
    void hideFlyout() {
        if (mOnHide != null) {
            mOnHide.run();
            mOnHide = null;
        }

        setVisibility(GONE);
    }

    /** Sets the percentage that the flyout should be collapsed into dot form. */
    void setCollapsePercent(float percentCollapsed) {
        // This is unlikely, but can happen in a race condition where the flyout view hasn't been
        // laid out and returns 0 for getWidth(). We check for this condition at the sites where
        // this method is called, but better safe than sorry.
        if (Float.isNaN(percentCollapsed)) {
            return;
        }

        mPercentTransitionedToDot = Math.max(0f, Math.min(percentCollapsed, 1f));
        mPercentStillFlyout = (1f - mPercentTransitionedToDot);

        // Move and fade out the text.
        final float translationX = mPercentTransitionedToDot
                * (mArrowPointingLeft ? -getWidth() : getWidth());
        final float alpha = clampPercentage(
                (mPercentStillFlyout - (1f - BubbleStackView.FLYOUT_DRAG_PERCENT_DISMISS))
                        / BubbleStackView.FLYOUT_DRAG_PERCENT_DISMISS);

        mMessageText.setTranslationX(translationX);
        mMessageText.setAlpha(alpha);

        mSenderText.setTranslationX(translationX);
        mSenderText.setAlpha(alpha);

        mSenderAvatar.setTranslationX(translationX);
        mSenderAvatar.setAlpha(alpha);

        // Reduce the elevation towards that of the topmost bubble.
        setTranslationZ(
                mFlyoutElevation
                        - (mFlyoutElevation - mBubbleElevation) * mPercentTransitionedToDot);
        invalidate();
    }

    /** Return the flyout's resting X translation (translation when not dragging or animating). */
    float getRestingTranslationX() {
        return mRestingTranslationX;
    }

    /** Clamps a float to between 0 and 1. */
    private float clampPercentage(float percent) {
        return Math.min(1f, Math.max(0f, percent));
    }

    /**
     * Resolving and applying colors according to the ui mode, remembering most recent mode.
     *
     * @return {@code true} if night mode setting has changed since the last invocation,
     * {@code false} otherwise
     */
    boolean applyColorsAccordingToConfiguration(Configuration configuration) {
        int nightModeFlags = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean flagsChanged = nightModeFlags != mNightModeFlags;
        if (flagsChanged) {
            mNightModeFlags = nightModeFlags;
            applyConfigurationColors(configuration);
        }
        return flagsChanged;
    }

    private void applyConfigurationColors(Configuration configuration) {
        int nightModeFlags = configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNightModeOn = nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
        try (TypedArray ta = mContext.obtainStyledAttributes(
                new int[]{
                        com.android.internal.R.attr.materialColorSurfaceContainer,
                        com.android.internal.R.attr.materialColorOnSurface,
                        com.android.internal.R.attr.materialColorOnSurfaceVariant})) {
            mFloatingBackgroundColor = ta.getColor(0,
                    isNightModeOn ? Color.BLACK : Color.WHITE);
            mSenderText.setTextColor(ta.getColor(1,
                    isNightModeOn ? Color.WHITE : Color.BLACK));
            mMessageText.setTextColor(ta.getColor(2,
                    isNightModeOn ? Color.WHITE : Color.BLACK));
            mBgPaint.setColor(mFloatingBackgroundColor);
            mLeftTriangleShape.getPaint().setColor(mFloatingBackgroundColor);
            mRightTriangleShape.getPaint().setColor(mFloatingBackgroundColor);
        }
    }

    /**
     * Renders the background, which is either the rounded 'chat bubble' flyout, or some state
     * between that and the 'new' dot over the bubbles.
     */
    private void renderBackground(Canvas canvas) {
        // Calculate the width, height, and corner radius of the flyout given the current collapsed
        // percentage.
        final float width = getWidth() - (mFlyoutToDotWidthDelta * mPercentTransitionedToDot);
        final float height = getHeight() - (mFlyoutToDotHeightDelta * mPercentTransitionedToDot);
        final float interpolatedRadius = getInterpolatedRadius();

        // Translate the flyout background towards the collapsed 'dot' state.
        mBgTranslationX = mTranslationXWhenDot * mPercentTransitionedToDot;
        mBgTranslationY = mTranslationYWhenDot * mPercentTransitionedToDot;

        // Set the bounds of the rounded rectangle that serves as either the flyout background or
        // the collapsed 'dot'. These bounds will also be used to provide the outline for elevation
        // shadows. In the expanded flyout state, the left and right bounds leave space for the
        // pointer triangle - as the flyout collapses, this space is reduced since the triangle
        // retracts into the flyout.
        mBgRect.set(
                mPointerSize * mPercentStillFlyout /* left */,
                0 /* top */,
                width - mPointerSize * mPercentStillFlyout /* right */,
                height /* bottom */);

        mBgPaint.setColor(
                (int) mArgbEvaluator.evaluate(
                        mPercentTransitionedToDot, mFloatingBackgroundColor, mDotColor));

        canvas.save();
        canvas.translate(mBgTranslationX, mBgTranslationY);
        renderPointerTriangle(canvas, width, height);
        canvas.drawRoundRect(mBgRect, interpolatedRadius, interpolatedRadius, mBgPaint);
        canvas.restore();
    }

    /** Renders the 'pointer' triangle that points from the flyout to the bubble stack. */
    private void renderPointerTriangle(
            Canvas canvas, float currentFlyoutWidth, float currentFlyoutHeight) {
        if (!SHOW_POINTER) return;
        canvas.save();

        // Translation to apply for the 'retraction' effect as the flyout collapses.
        final float retractionTranslationX =
                (mArrowPointingLeft ? 1 : -1) * (mPercentTransitionedToDot * mPointerSize * 2f);

        // Place the arrow either at the left side, or the far right, depending on whether the
        // flyout is on the left or right side.
        final float arrowTranslationX =
                mArrowPointingLeft
                        ? retractionTranslationX
                        : currentFlyoutWidth - mPointerSize + retractionTranslationX;

        // Vertically center the arrow at all times.
        final float arrowTranslationY = currentFlyoutHeight / 2f - mPointerSize / 2f;

        // Draw the appropriate direction of arrow.
        final ShapeDrawable relevantTriangle =
                mArrowPointingLeft ? mLeftTriangleShape : mRightTriangleShape;
        canvas.translate(arrowTranslationX, arrowTranslationY);
        relevantTriangle.setAlpha((int) (255f * mPercentStillFlyout));
        relevantTriangle.draw(canvas);

        // Save the triangle's outline for use in the outline provider, offsetting it to reflect its
        // current position.
        relevantTriangle.getOutline(mTriangleOutline);
        mTriangleOutline.offset((int) arrowTranslationX, (int) arrowTranslationY);
        canvas.restore();
    }

    /** Builds an outline that includes the transformed flyout background and triangle. */
    private void getOutline(Outline outline) {
        if (!mTriangleOutline.isEmpty() || !SHOW_POINTER) {
            // Draw the rect into the outline as a path so we can merge the triangle path into it.
            final Path rectPath = new Path();
            final float interpolatedRadius = getInterpolatedRadius();
            rectPath.addRoundRect(mBgRect, interpolatedRadius,
                    interpolatedRadius, Path.Direction.CW);
            outline.setPath(rectPath);

            // Get rid of the triangle path once it has disappeared behind the flyout.
            if (SHOW_POINTER && mPercentStillFlyout > 0.5f) {
                outline.mPath.addPath(mTriangleOutline.mPath);
            }

            // Translate the outline to match the background's position.
            final Matrix outlineMatrix = new Matrix();
            outlineMatrix.postTranslate(getLeft() + mBgTranslationX, getTop() + mBgTranslationY);

            // At the very end, retract the outline into the bubble so the shadow will be pulled
            // into the flyout-dot as it (visually) becomes part of the bubble. We can't do this by
            // animating translationZ to zero since then it'll go under the bubbles, which have
            // elevation.
            if (mPercentTransitionedToDot > 0.98f) {
                final float percentBetween99and100 = (mPercentTransitionedToDot - 0.98f) / .02f;
                final float percentShadowVisible = 1f - percentBetween99and100;

                // Keep it centered.
                outlineMatrix.postTranslate(
                        mNewDotRadius * percentBetween99and100,
                        mNewDotRadius * percentBetween99and100);
                outlineMatrix.preScale(percentShadowVisible, percentShadowVisible);
            }

            outline.mPath.transform(outlineMatrix);
        }
    }

    private float getInterpolatedRadius() {
        return mNewDotRadius * mPercentTransitionedToDot
                + mCornerRadius * (1 - mPercentTransitionedToDot);
    }
}
