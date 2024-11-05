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

import android.annotation.DrawableRes;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.PathParser;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.IconNormalizer;
import com.android.wm.shell.R;
import com.android.wm.shell.animation.Interpolators;

import java.util.EnumSet;

/**
 * View that displays an adaptive icon with an app-badge and a dot.
 *
 * Dot = a small colored circle that indicates whether this bubble has an unread update.
 * Badge = the icon associated with the app that created this bubble, this will show work profile
 * badge if appropriate.
 */
public class BadgedImageView extends ConstraintLayout {

    /** Same value as Launcher3 dot code */
    public static final float WHITE_SCRIM_ALPHA = 0.54f;
    /** Same as value in Launcher3 IconShape */
    public static final int DEFAULT_PATH_SIZE = 100;

    /**
     * Flags that suppress the visibility of the 'new' dot, for one reason or another. If any of
     * these flags are set, the dot will not be shown even if {@link Bubble#showDot()} returns true.
     */
    enum SuppressionFlag {
        // Suppressed because the flyout is visible - it will morph into the dot via animation.
        FLYOUT_VISIBLE,
        // Suppressed because this bubble is behind others in the collapsed stack.
        BEHIND_STACK,
    }

    /**
     * Start by suppressing the dot because the flyout is visible - most bubbles are added with a
     * flyout, so this is a reasonable default.
     */
    private final EnumSet<SuppressionFlag> mDotSuppressionFlags =
            EnumSet.of(SuppressionFlag.FLYOUT_VISIBLE);

    private final ImageView mBubbleIcon;
    private final ImageView mAppIcon;

    private float mDotScale = 0f;
    private float mAnimatingToDotScale = 0f;
    private boolean mDotIsAnimating = false;

    private BubbleViewProvider mBubble;
    private BubblePositioner mPositioner;
    private boolean mBadgeOnLeft;
    private boolean mDotOnLeft;
    private DotRenderer mDotRenderer;
    private DotRenderer.DrawParams mDrawParams;
    private int mDotColor;

    private Rect mTempBounds = new Rect();

    public BadgedImageView(Context context) {
        this(context, null);
    }

    public BadgedImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BadgedImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public BadgedImageView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        // We manage positioning the badge ourselves
        setLayoutDirection(LAYOUT_DIRECTION_LTR);

        LayoutInflater.from(context).inflate(R.layout.badged_image_view, this);

        mBubbleIcon = findViewById(R.id.icon_view);
        mAppIcon = findViewById(R.id.app_icon_view);

        final TypedArray ta = mContext.obtainStyledAttributes(attrs, new int[]{android.R.attr.src},
                defStyleAttr, defStyleRes);
        mBubbleIcon.setImageResource(ta.getResourceId(0, 0));
        ta.recycle();

        mDrawParams = new DotRenderer.DrawParams();

        setFocusable(true);
        setClickable(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                BadgedImageView.this.getOutline(outline);
            }
        });
    }

    private void getOutline(Outline outline) {
        final int bubbleSize = mPositioner.getBubbleSize();
        final int normalizedSize = IconNormalizer.getNormalizedCircleSize(bubbleSize);
        final int inset = (bubbleSize - normalizedSize) / 2;
        outline.setOval(inset, inset, inset + normalizedSize, inset + normalizedSize);
    }

    public void initialize(BubblePositioner positioner) {
        mPositioner = positioner;

        Path iconPath = PathParser.createPathFromPathData(
                getResources().getString(com.android.internal.R.string.config_icon_mask));
        mDotRenderer = new DotRenderer(mPositioner.getBubbleSize(),
                iconPath, DEFAULT_PATH_SIZE);
    }

    public void showDotAndBadge(boolean onLeft) {
        removeDotSuppressionFlag(BadgedImageView.SuppressionFlag.BEHIND_STACK);
        animateDotBadgePositions(onLeft);
    }

    public void hideDotAndBadge(boolean onLeft) {
        addDotSuppressionFlag(BadgedImageView.SuppressionFlag.BEHIND_STACK);
        mBadgeOnLeft = onLeft;
        mDotOnLeft = onLeft;
        hideBadge();
    }

    /**
     * Updates the view with provided info.
     */
    public void setRenderedBubble(BubbleViewProvider bubble) {
        mBubble = bubble;
        mBubbleIcon.setImageBitmap(bubble.getBubbleIcon());
        mAppIcon.setImageBitmap(bubble.getAppBadge());
        if (mDotSuppressionFlags.contains(SuppressionFlag.BEHIND_STACK)) {
            hideBadge();
        } else {
            showBadge();
        }
        mDotColor = bubble.getDotColor();
        drawDot(bubble.getDotPath());
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (!shouldDrawDot()) {
            return;
        }

        getDrawingRect(mTempBounds);

        mDrawParams.dotColor = mDotColor;
        mDrawParams.iconBounds = mTempBounds;
        mDrawParams.leftAlign = mDotOnLeft;
        mDrawParams.scale = mDotScale;

        mDotRenderer.draw(canvas, mDrawParams);
    }

    /**
     * Set drawable resource shown as the icon
     */
    public void setIconImageResource(@DrawableRes int drawable) {
        mBubbleIcon.setImageResource(drawable);
    }

    /**
     * Get icon drawable
     */
    public Drawable getIconDrawable() {
        return mBubbleIcon.getDrawable();
    }

    /** Adds a dot suppression flag, updating dot visibility if needed. */
    void addDotSuppressionFlag(SuppressionFlag flag) {
        if (mDotSuppressionFlags.add(flag)) {
            // Update dot visibility, and animate out if we're now behind the stack.
            updateDotVisibility(flag == SuppressionFlag.BEHIND_STACK /* animate */);
        }
    }

    /** Removes a dot suppression flag, updating dot visibility if needed. */
    void removeDotSuppressionFlag(SuppressionFlag flag) {
        if (mDotSuppressionFlags.remove(flag)) {
            // Update dot visibility, animating if we're no longer behind the stack.
            updateDotVisibility(flag == SuppressionFlag.BEHIND_STACK);
        }
    }

    /** Updates the visibility of the dot, animating if requested. */
    void updateDotVisibility(boolean animate) {
        final float targetScale = shouldDrawDot() ? 1f : 0f;

        if (animate) {
            animateDotScale(targetScale, null /* after */);
        } else {
            mDotScale = targetScale;
            mAnimatingToDotScale = targetScale;
            invalidate();
        }
    }

    /**
     * @param iconPath The new icon path to use when calculating dot position.
     */
    void drawDot(Path iconPath) {
        mDotRenderer = new DotRenderer(mPositioner.getBubbleSize(),
                iconPath, DEFAULT_PATH_SIZE);
        invalidate();
    }

    /**
     * How big the dot should be, fraction from 0 to 1.
     */
    void setDotScale(float fraction) {
        mDotScale = fraction;
        invalidate();
    }

    /**
     * Whether decorations (badges or dots) are on the left.
     */
    boolean getDotOnLeft() {
        return mDotOnLeft;
    }

    /**
     * Return dot position relative to bubble view container bounds.
     */
    float[] getDotCenter() {
        float[] dotPosition;
        if (mDotOnLeft) {
            dotPosition = mDotRenderer.getLeftDotPosition();
        } else {
            dotPosition = mDotRenderer.getRightDotPosition();
        }
        getDrawingRect(mTempBounds);
        float dotCenterX = mTempBounds.width() * dotPosition[0];
        float dotCenterY = mTempBounds.height() * dotPosition[1];
        return new float[]{dotCenterX, dotCenterY};
    }

    /**
     * The key for the {@link Bubble} associated with this view, if one exists.
     */
    @Nullable
    public String getKey() {
        return (mBubble != null) ? mBubble.getKey() : null;
    }

    int getDotColor() {
        return mDotColor;
    }

    /** Sets the position of the dot and badge, animating them out and back in if requested. */
    void animateDotBadgePositions(boolean onLeft) {
        if (onLeft != getDotOnLeft()) {
            if (shouldDrawDot()) {
                animateDotScale(0f /* showDot */, () -> {
                    mDotOnLeft = onLeft;
                    invalidate();
                    animateDotScale(1.0f, null /* after */);
                });
            } else {
                mDotOnLeft = onLeft;
            }
        }
        mBadgeOnLeft = onLeft;
        // TODO animate badge
        showBadge();
    }

    /** Sets the position of the dot and badge. */
    void setDotBadgeOnLeft(boolean onLeft) {
        mBadgeOnLeft = onLeft;
        mDotOnLeft = onLeft;
        invalidate();
        showBadge();
    }

    /** Whether to draw the dot in onDraw(). */
    private boolean shouldDrawDot() {
        // Always render the dot if it's animating, since it could be animating out. Otherwise, show
        // it if the bubble wants to show it, and we aren't suppressing it.
        return mDotIsAnimating || (mBubble.showDot() && mDotSuppressionFlags.isEmpty());
    }

    /**
     * Animates the dot to the given scale, running the optional callback when the animation ends.
     */
    public void animateDotScale(float toScale, @Nullable Runnable after) {
        mDotIsAnimating = true;

        // Don't restart the animation if we're already animating to the given value.
        if (mAnimatingToDotScale == toScale || !shouldDrawDot()) {
            mDotIsAnimating = false;
            return;
        }

        mAnimatingToDotScale = toScale;

        final boolean showDot = toScale > 0f;

        // Do NOT wait until after animation ends to setShowDot
        // to avoid overriding more recent showDot states.
        clearAnimation();
        animate()
                .setDuration(200)
                .setInterpolator(Interpolators.FAST_OUT_SLOW_IN)
                .setUpdateListener((valueAnimator) -> {
                    float fraction = valueAnimator.getAnimatedFraction();
                    fraction = showDot ? fraction : 1f - fraction;
                    setDotScale(fraction);
                }).withEndAction(() -> {
                    setDotScale(showDot ? 1f : 0f);
                    mDotIsAnimating = false;
                    if (after != null) {
                        after.run();
                    }
                }).start();
    }

    void showBadge() {
        Bitmap appBadgeBitmap = mBubble.getAppBadge();
        if (appBadgeBitmap == null) {
            mAppIcon.setVisibility(GONE);
            return;
        }

        int translationX;
        if (mBadgeOnLeft) {
            translationX = -(mBubble.getBubbleIcon().getWidth() - appBadgeBitmap.getWidth());
        } else {
            translationX = 0;
        }

        mAppIcon.setTranslationX(translationX);
        mAppIcon.setVisibility(VISIBLE);
    }

    void hideBadge() {
        mAppIcon.setVisibility(GONE);
    }

    @Override
    public String toString() {
        return "BadgedImageView{" + mBubble + "}";
    }
}
