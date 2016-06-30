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

package com.android.launcher3.shortcuts;

import android.content.Context;
import android.support.annotation.IntDef;
import android.util.AttributeSet;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.R;

/**
 * A {@link BubbleTextView} that represents a deep shortcut within an app.
 */
public class DeepShortcutView extends BubbleTextView {

    private static final float HOVER_SCALE = 1.1f;
    // The direction this view should translate when animating the hover state.
    // This allows hovered shortcuts to "push" other shortcuts away.
    @IntDef({DIRECTION_UP, DIRECTION_NONE, DIRECTION_DOWN})
    public @interface TranslationDirection {}

    public static final int DIRECTION_UP = -1;
    public static final int DIRECTION_NONE = 0;
    public static final int DIRECTION_DOWN = 1;
    @TranslationDirection
    private int mTranslationDirection = DIRECTION_NONE;

    private int mSpacing;
    private int mTop;
    private boolean mIsHoveringOver = false;

    public DeepShortcutView(Context context) {
        this(context, null, 0);
    }

    public DeepShortcutView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeepShortcutView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mSpacing = getResources().getDimensionPixelSize(R.dimen.deep_shortcuts_spacing);
    }

    public int getSpacing() {
        return mSpacing;
    }

    /**
     * Updates the state of this view based on touches over the container before user lifts finger.
     *
     * @param containerContainsTouch whether the {@link DeepShortcutsContainer} this shortcut
     *                               is inside contains the current touch
     * @param isBelowHoveredShortcut whether a sibling shortcut before this one in the
     *                               view hierarchy is being hovered over
     * @param touchY the y coordinate of the touch, relative to the {@link DeepShortcutsContainer}
     *               this shortcut is inside
     * @return whether this shortcut is being hovered over
     */
    public boolean updateHoverState(boolean containerContainsTouch, boolean isBelowHoveredShortcut,
            float touchY) {
        if (!containerContainsTouch) {
            mIsHoveringOver = false;
            mTranslationDirection = DIRECTION_NONE;
        } else if (isBelowHoveredShortcut) {
            mIsHoveringOver = false;
            mTranslationDirection = DIRECTION_DOWN;
        } else {
            // Include space around the view when determining hover state to avoid gaps.
            mTop = (int) (getY() - getTranslationY());
            mIsHoveringOver = (touchY >= mTop - mSpacing / 2)
                    && (touchY < mTop + getHeight() + mSpacing / 2);
            mTranslationDirection = mIsHoveringOver ? DIRECTION_NONE : DIRECTION_UP;
        }
        animateHoverState();
        return mIsHoveringOver;
    }

    /**
     * If this shortcut is being hovered over, we scale it up. If another shortcut is being hovered
     * over, we translate this one away from it to account for its increased size.
     *
     * TODO: apply motion spec here
     */
    private void animateHoverState() {
        float scale = mIsHoveringOver ? HOVER_SCALE : 1f;
        setScaleX(scale);
        setScaleY(scale);

        float translation = (HOVER_SCALE - 1f) * getHeight();
        setTranslationY(translation * mTranslationDirection);
    }

    public boolean isHoveringOver() {
        return mIsHoveringOver;
    }
}
