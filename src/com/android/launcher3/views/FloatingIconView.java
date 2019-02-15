/*
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
package com.android.launcher3.views;

import android.animation.Animator;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.InsettableFrameLayout.LayoutParams;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.Launcher;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.graphics.DrawableFactory;

/**
 * A view that is created to look like another view with the purpose of creating fluid animations.
 */
public class FloatingIconView extends View implements Animator.AnimatorListener {

    private Runnable mStartRunnable;
    private Runnable mEndRunnable;

    public FloatingIconView(Context context) {
        super(context);
    }

    public void setRunnables(Runnable startRunnable, Runnable endRunnable) {
        mStartRunnable = startRunnable;
        mEndRunnable = endRunnable;
    }

    /**
     * Positions this view to match the size and location of {@param rect}.
     */
    public void update(RectF rect, float alpha) {
        setAlpha(alpha);

        LayoutParams lp = (LayoutParams) getLayoutParams();
        float dX = rect.left - lp.leftMargin;
        float dY = rect.top - lp.topMargin;
        setTranslationX(dX);
        setTranslationY(dY);

        float scaleX = rect.width() / (float) getWidth();
        float scaleY = rect.height() / (float) getHeight();
        float scale = Math.min(scaleX, scaleY);
        setPivotX(0);
        setPivotY(0);
        setScaleX(scale);
        setScaleY(scale);
    }

    @Override
    public void onAnimationStart(Animator animator) {
        if (mStartRunnable != null) {
            mStartRunnable.run();
        }
    }

    @Override
    public void onAnimationEnd(Animator animator) {
        if (mEndRunnable != null) {
            mEndRunnable.run();
        }
    }

    @Override
    public void onAnimationCancel(Animator animator) {
    }

    @Override
    public void onAnimationRepeat(Animator animator) {
    }

    /**
     * Sets the size and position of this view to match {@param v}.
     *
     * @param v The view to copy
     * @param hideOriginal If true, it will hide {@param v} while this view is visible.
     * @param positionOut Rect that will hold the size and position of v.
     */
    public void matchPositionOf(Launcher launcher, View v, boolean hideOriginal, Rect positionOut) {
        Utilities.getLocationBoundsForView(launcher, v, positionOut);
        final LayoutParams lp = new LayoutParams(positionOut.width(), positionOut.height());
        lp.ignoreInsets = true;

        // Position the floating view exactly on top of the original
        lp.leftMargin = positionOut.left;
        lp.topMargin = positionOut.top;
        setLayoutParams(lp);
        // Set the properties here already to make sure they are available when running the first
        // animation frame.
        layout(lp.leftMargin, lp.topMargin, lp.leftMargin + lp.width, lp.topMargin
                + lp.height);

        if (v instanceof BubbleTextView && v.getTag() instanceof ItemInfoWithIcon ) {
            // Create a copy of the app icon
            setBackground(DrawableFactory.INSTANCE.get(launcher)
                    .newIcon(v.getContext(), (ItemInfoWithIcon) v.getTag()));
        }

        // We need to add it to the overlay, but keep it invisible until animation starts..
        final DragLayer dragLayer = launcher.getDragLayer();
        setVisibility(INVISIBLE);
        ((ViewGroup) dragLayer.getParent()).getOverlay().add(this);

        setRunnables(() -> {
                    setVisibility(VISIBLE);
                    if (hideOriginal) {
                        v.setVisibility(INVISIBLE);
                    }
                },
                () -> {
                    ((ViewGroup) dragLayer.getParent()).getOverlay().remove(this);
                    if (hideOriginal) {
                        v.setVisibility(VISIBLE);
                    }
                });
    }
}
