/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.wm.shell.bubbles.bar;

import static com.android.wm.shell.bubbles.bar.BubbleBarCaptionView.CAPTION_ELEVATION;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.app.animation.Interpolators;
import com.android.launcher3.icons.BitmapInfo;
import com.android.wm.shell.R;
import com.android.wm.shell.bubbles.Bubble;
import com.android.wm.shell.shared.bubbles.BubbleAnythingFlagHelper;

import java.util.ArrayList;

/**
 * Manages bubble bar expanded view menu presentation and animations
 */
class BubbleBarMenuViewController {

    private static final float WIDTH_SWAP_FRACTION = 0.4F;
    private static final long MENU_ANIMATION_DURATION = 600;

    private final Context mContext;
    private final ViewGroup mRootView;
    private final BubbleBarHandleView mHandleView;
    private @Nullable Listener mListener;
    private @Nullable Bubble mBubble;
    private @Nullable BubbleBarMenuView mMenuView;
    /** A transparent view used to intercept touches to collapse menu when presented */
    private @Nullable View mScrimView;
    private @Nullable ValueAnimator mMenuAnimator;


    BubbleBarMenuViewController(Context context, BubbleBarHandleView handleView,
            ViewGroup rootView) {
        mContext = context;
        mRootView = rootView;
        mHandleView = handleView;
    }

    /** Tells if the menu is visible or being animated */
    boolean isMenuVisible() {
        return mMenuView != null && mMenuView.getVisibility() == View.VISIBLE;
    }

    /** Sets menu actions listener */
    void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    /** Update menu with bubble */
    void updateMenu(@NonNull Bubble bubble) {
        mBubble = bubble;
    }

    /**
     * Show bubble bar expanded view menu
     * @param animated if should animate transition
     */
    void showMenu(boolean animated) {
        if (mMenuView == null || mScrimView == null) {
            setupMenu();
        }
        runOnMenuIsMeasured(() -> {
            mMenuView.setVisibility(View.VISIBLE);
            mScrimView.setVisibility(View.VISIBLE);
            if (mListener != null) {
                mListener.onMenuVisibilityChanged(true /* isShown */);
            }
            Runnable endActions = () -> mMenuView.getChildAt(0).requestAccessibilityFocus();
            if (animated) {
                animateTransition(true /* show */, endActions);
            } else {
                endActions.run();
            }
        });
    }

    /**
     * Hide bubble bar expanded view menu
     * @param animated if should animate transition
     */
    void hideMenu(boolean animated) {
        if (mMenuView == null || mScrimView == null) return;
        runOnMenuIsMeasured(() -> {
            Runnable endActions = () -> {
                mHandleView.restoreAnimationDefaults();
                mMenuView.setVisibility(View.GONE);
                mScrimView.setVisibility(View.GONE);
                mHandleView.setVisibility(View.VISIBLE);
                if (mListener != null) {
                    mListener.onMenuVisibilityChanged(false /* isShown */);
                }
            };
            if (animated) {
                animateTransition(false /* show */, endActions);
            } else {
                endActions.run();
            }
        });
    }

    private void runOnMenuIsMeasured(Runnable action) {
        if (mMenuView.getWidth() == 0 || mMenuView.getHeight() == 0) {
            // the menu view is not yet measured, postpone showing the animation
            mMenuView.post(() -> runOnMenuIsMeasured(action));
        } else {
            action.run();
        }
    }

    /**
     * Animate show/hide menu transition
     * @param show if should show or hide the menu
     * @param endActions will be called when animation ends
     */
    private void animateTransition(boolean show, Runnable endActions) {
        if (mMenuView == null) return;
        float startValue = show ? 0 : 1;
        if (mMenuAnimator != null && mMenuAnimator.isRunning()) {
            startValue = (float) mMenuAnimator.getAnimatedValue();
            mMenuAnimator.cancel();
        }
        ValueAnimator showMenuAnimation = ValueAnimator.ofFloat(startValue, show ? 1 : 0);
        showMenuAnimation.setDuration(MENU_ANIMATION_DURATION);
        showMenuAnimation.setInterpolator(Interpolators.EMPHASIZED);
        showMenuAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mMenuAnimator = null;
                endActions.run();
            }
        });
        mMenuAnimator = showMenuAnimation;
        setupAnimatorListener(showMenuAnimation);
        showMenuAnimation.start();
    }

    /** Setup listener that orchestrates the animation. */
    private void setupAnimatorListener(ValueAnimator showMenuAnimation) {
        // Getting views properties start values
        int widthDiff = mMenuView.getWidth() - mHandleView.getHandleWidth();
        int handleHeight = mHandleView.getHandleHeight();
        float targetWidth = mHandleView.getHandleWidth() + widthDiff * WIDTH_SWAP_FRACTION;
        float targetHeight = targetWidth * mMenuView.getTitleItemHeight() / mMenuView.getWidth();
        int menuColor = mContext.getColor(com.android.internal.R.color.materialColorSurfaceBright);
        // Calculating deltas
        float swapScale = targetWidth / mMenuView.getWidth();
        float handleWidthDelta = targetWidth - mHandleView.getHandleWidth();
        float handleHeightDelta = targetHeight - handleHeight;
        // Setting update listener that will orchestrate the animation
        showMenuAnimation.addUpdateListener(animator -> {
            float animationProgress = (float) animator.getAnimatedValue();
            boolean showHandle = animationProgress <= WIDTH_SWAP_FRACTION;
            mHandleView.setVisibility(showHandle ? View.VISIBLE : View.GONE);
            mMenuView.setVisibility(showHandle ? View.GONE : View.VISIBLE);
            if (showHandle) {
                float handleAnimationProgress = animationProgress / WIDTH_SWAP_FRACTION;
                mHandleView.animateHandleForMenu(handleAnimationProgress, handleWidthDelta,
                        handleHeightDelta, menuColor);
            } else {
                mMenuView.setTranslationY(mHandleView.getHandlePaddingTop());
                mMenuView.setPivotY(0);
                mMenuView.setPivotX((float) mMenuView.getWidth() / 2);
                float menuAnimationProgress =
                        (animationProgress - WIDTH_SWAP_FRACTION) / (1 - WIDTH_SWAP_FRACTION);
                float currentMenuScale = swapScale + (1 - swapScale) * menuAnimationProgress;
                mMenuView.animateFromStartScale(currentMenuScale, menuAnimationProgress);
            }
        });
    }

    /** Sets up and inflate menu views */
    private void setupMenu() {
        // Menu view setup
        mMenuView = (BubbleBarMenuView) LayoutInflater.from(mContext).inflate(
                R.layout.bubble_bar_menu_view, mRootView, false);
        mMenuView.setOnCloseListener(() -> hideMenu(true  /* animated */));
        // Set the elevation to draw on top of the caption view.
        mMenuView.setElevation(CAPTION_ELEVATION + 1);
        if (mBubble != null) {
            mMenuView.updateInfo(mBubble);
            mMenuView.updateActions(createMenuActions(mBubble));
        }
        // Scrim view setup
        mScrimView = new View(mContext);
        mScrimView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mScrimView.setOnClickListener(view -> hideMenu(true  /* animated */));
        // Attach to root view
        mRootView.addView(mScrimView);
        mRootView.addView(mMenuView);
    }

    /**
     * Creates menu actions to populate menu view
     * @param bubble used to create actions depending on bubble type
     */
    private ArrayList<BubbleBarMenuView.MenuAction> createMenuActions(Bubble bubble) {
        ArrayList<BubbleBarMenuView.MenuAction> menuActions = new ArrayList<>();
        Resources resources = mContext.getResources();
        int tintColor = mContext.getColor(com.android.internal.R.color.materialColorOnSurface);

        if (bubble.isChat()) {
            // Don't bubble conversation action
            menuActions.add(new BubbleBarMenuView.MenuAction(
                    DrawableProvider.forResource(R.drawable.bubble_ic_stop_bubble),
                    resources.getString(R.string.bubbles_dont_bubble_conversation),
                    tintColor,
                    view -> {
                        hideMenu(true /* animated */);
                        if (mListener != null) {
                            mListener.onUnBubbleConversation(bubble);
                        }
                    }
            ));
            // Open settings action
            BitmapInfo rawAppBadge = bubble.getRawAppBadge();
            DrawableProvider appIcon = rawAppBadge != null
                    ? DrawableProvider.forInfo(rawAppBadge) : null;
            menuActions.add(new BubbleBarMenuView.MenuAction(
                    appIcon,
                    resources.getString(R.string.bubbles_app_settings, bubble.getAppName()),
                    view -> {
                        hideMenu(true /* animated */);
                        if (mListener != null) {
                            mListener.onOpenAppSettings(bubble);
                        }
                    }
            ));
        }

        // Dismiss bubble action
        menuActions.add(new BubbleBarMenuView.MenuAction(
                DrawableProvider.forResource(R.drawable.ic_remove_no_shadow),
                resources.getString(R.string.bubble_dismiss_text),
                tintColor,
                view -> {
                    hideMenu(true /* animated */);
                    if (mListener != null) {
                        mListener.onDismissBubble(bubble);
                    }
                }
        ));

        if (BubbleAnythingFlagHelper.enableBubbleToFullscreen()) {
            menuActions.add(new BubbleBarMenuView.MenuAction(
                    DrawableProvider.forResource(R.drawable.desktop_mode_ic_handle_menu_fullscreen),
                    resources.getString(R.string.bubble_fullscreen_text),
                    tintColor,
                    view -> {
                        hideMenu(true /* animated */);
                        if (mListener != null) {
                            mListener.onMoveToFullscreen(bubble);
                        }
                    }
            ));
        }

        return menuActions;
    }

    /**
     * Bubble bar expanded view menu actions listener
     */
    interface Listener {
        /**
         * Called when manage menu is shown/hidden
         * If animated will be called when animation ends
         */
        void onMenuVisibilityChanged(boolean visible);

        /**
         * Un-bubbles conversation and removes the bubble from the stack
         * This conversation will not be bubbled with new messages
         * @see com.android.wm.shell.bubbles.BubbleController
         */
        void onUnBubbleConversation(Bubble bubble);

        /**
         * Launches app notification bubble settings for the bubble with intent created in:
         * {@code Bubble.getSettingsIntent}
         */
        void onOpenAppSettings(Bubble bubble);

        /**
         * Dismiss bubble and remove it from the bubble stack
         */
        void onDismissBubble(Bubble bubble);

        /**
         * Move the bubble to fullscreen.
         */
        void onMoveToFullscreen(Bubble bubble);
    }
}
